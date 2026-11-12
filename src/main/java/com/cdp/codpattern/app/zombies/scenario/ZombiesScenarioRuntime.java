package com.cdp.codpattern.app.zombies.scenario;

import com.cdp.codpattern.app.zombies.npc.*;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.cdp.codpattern.config.zombies.ZombiesConfigPaths;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Frozen per-round scenario state; environment packets are scoped to room members, never the dimension. */
public final class ZombiesScenarioRuntime {
    private static final Set<ZombiesScenarioRuntime> ACTIVE=new HashSet<>();
    private final ZombiesMap map;
    private ZombiesScenarioConfig config=ZombiesScenarioConfig.empty();
    private final Set<String> completed=new HashSet<>();
    private final Set<String> taskDone=new HashSet<>();
    private final Map<String,Set<UUID>> areaClaimed=new HashMap<>(),areaBlocked=new HashMap<>();
    private String taskText="";
    public String taskText(){return taskText;}
    private final Set<String> failed=new HashSet<>();
    private final Map<UUID,ServerPlayer> environmentPlayers=new HashMap<>();
    private ZombiesScenarioConfig.Weather weather;
    private Long time;
    private List<ZombiesScenarioConfig.Line> lines=List.of();
    private int index,remaining,areaCheckTicks;
    private boolean active;
    public ZombiesScenarioRuntime(ZombiesMap map){this.map=map;}
    public void validate(ZombiesScenarioConfig candidate,ZombiesNpcConfig npcs){
        var keys=new HashSet<String>();for(var npc:npcs.npcs())for(var rule:npc.rules())keys.add(npc.id()+"."+rule.id());
        for(var e:candidate.events()){
            if(!e.rule().isBlank()&&!keys.contains(e.rule()))throw new IllegalArgumentException("事件引用未知 NPC 规则："+e.rule());
            map.npcRuntime().validateMapBlocks(e.regions().stream().flatMap(r->r.placements().stream()).toList());
        }
        for(var task:candidate.tasks())if(!task.rule().isBlank()&&!keys.contains(task.rule()))throw new IllegalArgumentException("任务引用未知 NPC 规则："+task.rule());
        for(var area:candidate.areaRewards()){
            if(!area.rule().isBlank()&&!keys.contains(area.rule()))throw new IllegalArgumentException("区域奖励引用未知 NPC 规则："+area.rule());
            for(var point:List.of(area.from(),area.to())){var box=map.getMapArea();if(point.getX()<Math.min(box.pos1().getX(),box.pos2().getX())||point.getX()>Math.max(box.pos1().getX(),box.pos2().getX())||point.getY()<Math.min(box.pos1().getY(),box.pos2().getY())||point.getY()>Math.max(box.pos1().getY(),box.pos2().getY())||point.getZ()<Math.min(box.pos1().getZ(),box.pos2().getZ())||point.getZ()>Math.max(box.pos1().getZ(),box.pos2().getZ()))throw new IllegalArgumentException("奖励触发区域必须位于本关卡内");}
            for(var item:area.items())if(com.cdp.codpattern.app.zombies.item.ZombiesRequiredItem.parse(item.item()).stack().isEmpty())throw new IllegalArgumentException("区域奖励物品无效："+item.item());
        }
        if(candidate.waves()!=null){var loaded=new com.cdp.codpattern.app.zombies.service.ZombiesWaveConfigRepository(ZombiesConfigPaths.zombiesMapWaves(map.getServerLevel().getServer(),map.getMapName())).loadConfigured(candidate.waves());
            if(!loaded.isValid())throw new IllegalArgumentException(loaded.getIssues().toString());}
    }
    public void start(ZombiesScenarioConfig candidate){reset();config=candidate;active=true;ACTIVE.add(this);}
    public boolean ownsDialogue(int wave){return config.waves()!=null||config.dialogues().stream().anyMatch(d->d.wave()==wave);}
    public boolean dialogueActive(){return !lines.isEmpty();}
    public void startWaveText(int wave){cancelDialogue();var dialogue=config.dialogues().stream().filter(d->d.wave()==wave).findFirst().orElse(null);if(dialogue==null)return;lines=dialogue.lines();remaining=ZombiesNpcConfig.intervalTicks(dialogue.delaySeconds());if(!lines.isEmpty()&&remaining==0)advanceDialogue();}
    private void advanceDialogue(){
        // Zero-duration lines may be consecutive, but the last nonzero duration is also observed before spawning.
        while(index<lines.size()){
            var line=lines.get(index++);var speaker=config.speakers().stream().filter(s->s.id().equals(line.speaker())).findFirst().orElse(null);
            map.sendNpcDialogue(speaker==null?com.cdp.codpattern.app.zombies.service.ZombiesLegacyTextFormatter.parse(line.text()):speaker.say(line.text()));
            remaining=ZombiesNpcConfig.intervalTicks(line.seconds());if(remaining>0)return;
        }
        cancelDialogue();
    }
    private void cancelDialogue(){lines=List.of();index=remaining=0;}
    public void tick(){
        if(!active||!map.currentPhase().isRoundRunning())return;
        if(++areaCheckTicks>=5){areaCheckTicks=0;checkAreaRewards();}
        for(var task:config.tasks())if(task.automatic()&&!taskDone.contains(task.id())&&map.npcWave()>=task.minWave()&&(task.rule().isBlank()||map.npcRuntime().isRuleComplete(task.rule())))trigger(task.id());
        if(dialogueActive()&&--remaining<=0)advanceDialogue();
        for(var e:config.events())if(e.automatic()&&!completed.contains(e.id())&&!failed.contains(e.id())&&map.npcWave()>=e.minWave()&&(e.rule().isBlank()||map.npcRuntime().isRuleComplete(e.rule())))trigger(e.id());
        if(map.getServerLevel().getGameTime()%5==0){
            var players=map.npcPlayers();var ids=new HashSet<UUID>();for(var player:players){ids.add(player.getUUID());if(weather!=null||time!=null){environmentPlayers.put(player.getUUID(),player);sendEnvironment(player,false);}}
            for(var id:List.copyOf(environmentPlayers.keySet()))if(!ids.contains(id)){sendEnvironment(environmentPlayers.remove(id),true);}
        }
    }
    private void checkAreaRewards(){
        for(var area:config.areaRewards()){
            if(map.npcWave()<area.minWave()||!area.rule().isBlank()&&!map.npcRuntime().isRuleComplete(area.rule()))continue;
            var claimed=areaClaimed.computeIfAbsent(area.id(),k->new HashSet<>());var blocked=areaBlocked.computeIfAbsent(area.id(),k->new HashSet<>());
            for(var player:map.npcPlayers()){
                UUID id=player.getUUID();if(player.level()!=map.getServerLevel()||!player.isAlive()||player.isSpectator()||!map.isAliveSurvivor(id))continue;
                if(!area.contains(player.position())){blocked.remove(id);continue;}if(claimed.contains(id))continue;
                try{ZombiesNpcInventory.grant(player,area.items().stream().map(ZombiesScenarioConfig.RewardItem::action).toList());claimed.add(id);blocked.remove(id);
                    for(var item:area.items())player.sendSystemMessage(ZombiesNpcInventory.receivedMessage(item.item(),item.count()));
                }catch(IllegalArgumentException full){if(blocked.add(id))player.sendSystemMessage(Component.literal("区域道具暂未领取："+full.getMessage()+"；整理背包后在区域内自动重试"));}
            }
        }
    }
    public List<String> eventIds(){return java.util.stream.Stream.concat(config.events().stream().map(ZombiesScenarioConfig.MapEvent::id),config.tasks().stream().map(ZombiesScenarioConfig.Task::id)).toList();}
    public void requireEvent(String id){var source=active?config:ZombiesScenarioRepository.load(ZombiesScenarioRepository.path(map.getServerLevel().getServer(),map.getMapName()));if(source.events().stream().noneMatch(e->e.id().equals(id))&&source.tasks().stream().noneMatch(t->t.id().equals(id)))throw new IllegalArgumentException("未知地图事件："+id);}
    public void trigger(String id){
        if(!active||!map.currentPhase().isRoundRunning())throw new IllegalArgumentException("地图事件只能在对局中执行");
        var task=config.tasks().stream().filter(t->t.id().equals(id)).findFirst().orElse(null);if(task!=null){taskText=task.text();taskDone.add(id);return;}
        var event=config.events().stream().filter(e->e.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("未知地图事件："+id));
        if(completed.contains(id)||failed.contains(id))return;
        try {
        map.npcRuntime().applyMapBlocks(event.regions().stream().flatMap(r->r.placements().stream()).toList());
        if(event.weather()!=null)weather=event.weather();if(event.time()!=null)time=event.time();completed.add(id);
        for(var player:map.npcPlayers()){environmentPlayers.put(player.getUUID(),player);sendEnvironment(player,false);}
        }catch(RuntimeException failure){
            failed.add(id);
            com.mojang.logging.LogUtils.getLogger().error("Map event {} ({}) failed in {}; disabled until next round",id,event.name(),map.getMapName(),failure);
            if(map.npcRuntime().debugEnabled())for(var player:map.npcPlayers())if(player.hasPermissions(2))player.sendSystemMessage(Component.literal("地图事件 "+id+" 执行失败："+failure.getMessage()));
        }
    }
    public boolean completed(String id){return completed.contains(id);}
    private void sendEnvironment(ServerPlayer player,boolean restore){
        if(player==null||player.connection==null)return;var level=player.serverLevel();
        if(weather!=null){boolean rain=restore?level.isRaining():weather!=ZombiesScenarioConfig.Weather.CLEAR;float strength=restore?level.getRainLevel(1):rain?1:0;float thunder=restore?level.getThunderLevel(1):weather==ZombiesScenarioConfig.Weather.THUNDER?1:0;
            player.connection.send(new ClientboundGameEventPacket(rain?ClientboundGameEventPacket.START_RAINING:ClientboundGameEventPacket.STOP_RAINING,0));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE,strength));player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE,thunder));}
        if(time!=null)player.connection.send(new ClientboundSetTimePacket(level.getGameTime(),restore?level.getDayTime():time,restore&&level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)));
    }
    public void reset(){if(active)ZombiesNpcBlockJournal.get(map.getServerLevel()).restore(map.getServerLevel(),map.getMapName());for(var player:environmentPlayers.values())sendEnvironment(player,true);environmentPlayers.clear();weather=null;time=null;completed.clear();failed.clear();taskDone.clear();areaClaimed.clear();areaBlocked.clear();areaCheckTicks=0;taskText="";cancelDialogue();config=ZombiesScenarioConfig.empty();active=false;ACTIVE.remove(this);}
    public static void cleanupDetached(net.minecraft.server.MinecraftServer server,boolean stopping){var rooms=stopping?List.of():com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry.listMaps(com.cdp.codpattern.app.match.BuiltInGameModes.ZOMBIES);for(var r:List.copyOf(ACTIVE))if(r.map.getServerLevel().getServer()==server&&(stopping||rooms.stream().noneMatch(m->m==r.map))){ZombiesNpcBlockJournal.get(r.map.getServerLevel()).restore(r.map.getServerLevel(),r.map.getMapName());r.reset();}}
}
