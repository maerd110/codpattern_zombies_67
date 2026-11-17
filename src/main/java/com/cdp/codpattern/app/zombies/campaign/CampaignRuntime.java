package com.cdp.codpattern.app.zombies.campaign;

import com.google.gson.*;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry;
import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.*;

/** All mutable story data belongs to a unique room attempt, never to the current host identity. */
public final class CampaignRuntime {
    private static final Map<ZombiesMap,Session> SESSIONS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,List<Runnable>> QUEUE=new WeakHashMap<>();
    public static final class Session {
        public final UUID instance=UUID.randomUUID();
        public UUID host;
        public final CampaignDefinition definition;
        public final String campaign,chapter;
        public JsonObject world,base;
        public final Map<UUID,JsonObject> inventories=new LinkedHashMap<>();
        public final Map<UUID,JsonObject> saves=new LinkedHashMap<>();
        public final Set<UUID> keep=new HashSet<>(),members=new HashSet<>(),seen=new HashSet<>();
        public final Set<String> events=new HashSet<>();
        public final Set<String> rewards=new HashSet<>();
        public final ZombiesMap map;
        public boolean started,settled,failed;
        public String saveError="";
        public String pendingCheckpoint="";
        public boolean pendingFinished;
        public long emptySince=-1;
        public Session(ZombiesMap map,UUID host,CampaignDefinition definition,String campaign,String chapter,JsonObject base,JsonObject source){
            this.map=map;this.host=host;this.definition=definition;this.campaign=campaign;this.chapter=chapter;this.base=base.deepCopy();this.world=object(base,"world").deepCopy();
            keep.add(host);saves.put(host,source.deepCopy());
            for(var pair:object(world,"events").entrySet())if(pair.getKey().startsWith(chapter+"."))events.add(pair.getKey().substring(chapter.length()+1));
            for(var pair:object(world,"rewards").entrySet())if(pair.getKey().startsWith(chapter+"."))rewards.add(pair.getKey().substring(chapter.length()+1));
        }
    }
    private CampaignRuntime(){}
    public static Session session(ZombiesMap map){return SESSIONS.get(map);}
    public static boolean canAdvance(ZombiesMap map){var s=session(map);return s==null||s.pendingCheckpoint.isBlank()&&!s.failed&&!s.settled;}
    public static boolean hold(ZombiesMap map){var s=session(map);return s!=null&&s.started&&(s.settled||s.failed);}
    public static void prepare(ZombiesMap map){var old=session(map);if(old!=null&&old.started)SESSIONS.remove(map);}
    public static List<ZombiesMap> maps(){return FpsMatchMapRegistry.listMaps(BuiltInGameModes.ZOMBIES).stream().filter(m->m instanceof ZombiesMap).map(m->(ZombiesMap)m).toList();}
    public static Session room(ServerPlayer p){return SESSIONS.values().stream().filter(s->s.map.getServerLevel().getServer()==p.getServer()&&s.members.contains(p.getUUID())&&s.map.getMapTeams().getJoinedPlayersWithSpec().contains(p.getUUID())).findFirst().orElse(null);}
    public static JsonObject object(JsonObject o,String key){if(!o.has(key)||!o.get(key).isJsonObject())o.add(key,new JsonObject());return o.getAsJsonObject(key);}
    public static String str(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
    public static JsonObject freshSave(String campaign,String chapter){var save=new JsonObject();save.addProperty("id",UUID.randomUUID().toString());save.addProperty("name","新的故事路线");save.addProperty("campaign",campaign);var h=new JsonArray();var point=new JsonObject();point.addProperty("id",UUID.randomUUID().toString());point.addProperty("chapter",chapter);point.addProperty("checkpoint","entry");point.addProperty("finished",false);point.addProperty("wave",1);point.add("world",new JsonObject());point.add("items",new JsonObject());h.add(point);save.add("history",h);return save;}
    public static List<ServerPlayer> online(Session s){return s.map.npcPlayers().stream().filter(p->p.connection!=null&&!p.hasDisconnected()&&p.getServer().getPlayerList().getPlayer(p.getUUID())==p&&s.map.getMapTeams().getJoinedPlayersWithSpec().contains(p.getUUID())).toList();}
    private static void addMember(Session s,ServerPlayer p){
        UUID id=p.getUUID();s.members.add(id);s.seen.add(id);if(s.inventories.containsKey(id))return;
        JsonObject matched=null;for(var entry:CampaignStore.get(p.getServer()).account(id).getAsJsonObject("saves").entrySet()){
            var candidate=entry.getValue().getAsJsonObject();if(str(candidate,"campaign").equals(s.campaign)&&str(CampaignStore.tip(candidate),"id").equals(str(s.base,"id"))){matched=candidate.deepCopy();break;}}
        if(!s.saves.containsKey(id)){var fork=matched==null?s.saves.get(s.host).deepCopy():matched;fork.addProperty("id",matched==null?UUID.randomUUID().toString():str(fork,"id"));if(matched==null)fork.addProperty("name",s.definition.campaign(s.campaign).name()+" · 合作路线");s.saves.put(id,fork);}
        s.inventories.put(id,object(CampaignStore.tip(s.saves.get(id)),"items").deepCopy());
        p.sendSystemMessage(Component.literal("本局采用统一剧情历史；打开个人主页可选择保留这条路线，原存档不会被覆盖。"));
    }
    public static void launch(ServerPlayer owner,String campaign,String saveId,int replayIndex){
        if(room(owner)!=null&&room(owner).map.currentPhase().isRoundRunning())throw new IllegalArgumentException("请结束当前对局后再选择存档");
        var def=CampaignStore.definition(owner.getServer());var c=def.campaign(campaign);if(c.chapters().isEmpty())throw new IllegalArgumentException("战役还没有章节");
        JsonObject save=saveId.isBlank()?freshSave(campaign,c.chapters().get(0).id()):CampaignStore.get(owner.getServer()).save(owner.getUUID(),saveId);
        if(!str(save,"campaign").equals(campaign))throw new IllegalArgumentException("存档不属于所选战役");
        if(replayIndex>=0){var history=save.getAsJsonArray("history");if(replayIndex>=history.size())throw new IllegalArgumentException("检查点不存在");var chosen=history.get(replayIndex).getAsJsonObject();if(str(chosen,"checkpoint").equals("chapter_start")){var trimmed=new JsonArray();for(int i=0;i<=replayIndex;i++)trimmed.add(history.get(i).deepCopy());save.add("history",trimmed);save.addProperty("origin",str(save,"id"));save.addProperty("id",UUID.randomUUID().toString());save.addProperty("name",str(save,"name")+" · 回玩分支");}else throw new IllegalArgumentException("回玩请选择章节起点");}
        var point=CampaignStore.tip(save);String chapter=str(point,"chapter");if(point.has("finished")&&point.get("finished").getAsBoolean()){chapter=def.next(campaign,chapter);if(chapter.isBlank())throw new IllegalArgumentException("此路线已完成；可以选择旧章节起点重新游玩");}
        var target=target(owner.getServer(),def.chapter(chapter).map());var previous=maps().stream().filter(m->m.npcPlayers().stream().anyMatch(p->p.getUUID().equals(owner.getUUID()))).findFirst().orElse(null);
        if(previous!=null&&previous.currentPhase()!=ZombiesGamePhase.WAITING)throw new IllegalArgumentException("请先结束当前对局");
        var party=previous==null?List.of(owner):previous.npcPlayers();if(party.size()>4)throw new IllegalArgumentException("队伍超过四人");
        var s=new Session(target,owner.getUUID(),def,campaign,chapter,point,save);
        transfer(previous,target,party);SESSIONS.put(target,s);for(var p:party)addMember(s,p);
        target.campaignStartGame();if(!s.started||session(target)!=s||!target.currentPhase().isRoundRunning()){SESSIONS.remove(target);throw new IllegalArgumentException("关卡未能启动，请检查部署校验；存档未改变");}
    }
    private static ZombiesMap target(MinecraftServer server,String map){var m=maps().stream().filter(v->v.getServerLevel().getServer()==server&&v.getMapName().equals(map)).findFirst().orElseThrow(()->new IllegalArgumentException("章节地图尚未部署："+map));if(m.currentPhase()!=ZombiesGamePhase.WAITING)throw new IllegalArgumentException("该地图正在使用，请稍后再试");return m;}
    private static void transfer(ZombiesMap previous,ZombiesMap next,List<ServerPlayer> party){if(previous!=next&& !next.npcPlayers().isEmpty())throw new IllegalArgumentException("目标地图已有其他队伍");for(var p:party){if(previous!=null&&previous!=next)previous.leave(p);if(previous!=next)next.campaignJoin(p);}}
    /** Called after starter equipment has been applied. Story warehouses never enter vanilla inventory. */
    public static void started(ZombiesMap map){var s=session(map);if(s==null)return;s.started=true;for(var p:online(s))addMember(s,p);
        if(str(s.base,"chapter").equals(s.chapter)&&!s.base.get("finished").getAsBoolean()){
            if(s.base.has("local")){var local=s.base.getAsJsonObject("local");if(local.has("barriers"))for(var group:local.getAsJsonArray("barriers"))map.openNpcBarrier(group.getAsInt());map.npcRuntime().restoreCampaign(local);map.scenarioRuntime().restoreCampaign(local);map.campaignWave(s.base.has("wave")?s.base.get("wave").getAsInt():1);}
        }
        applyActors(s);checkpoint(s,"chapter_start",false,true);
    }
    public static void tick(ZombiesMap map){var s=session(map);if(s==null||!s.started)return;
        var players=online(s);for(var p:players)addMember(s,p);
        if(players.isEmpty()){
            long now=map.getServerLevel().getGameTime();if(s.emptySince<0)s.emptySince=now;
            if(map.getMapTeams().getJoinedPlayersWithSpec().isEmpty()||(s.settled||s.failed)&&now-s.emptySince>=1200){map.resetGame();SESSIONS.remove(map);return;}
        }else s.emptySince=-1;
        if(players.stream().noneMatch(p->p.getUUID().equals(s.host))){players.stream().min(Comparator.comparingInt((ServerPlayer p)->p.latency).thenComparing(p->p.getUUID().toString())).ifPresent(p->{s.host=p.getUUID();s.keep.add(s.host);map.sendNpcDialogue(Component.literal("房主已移交给 "+p.getName().getString()+"，剧情路线保持不变。"));});}
        if(s.settled||s.failed)return;
        if(!map.currentPhase().isRoundRunning())return;
        if(!s.pendingCheckpoint.isBlank()){commitPending(s);if(s.failed||s.settled)return;}
        captureActors(s);
        for(var e:s.definition.events())if(e.chapter().equals(s.chapter)&&(e.minWave()>0||!e.rule().isBlank()||s.definition.canvas().links().stream().anyMatch(l->l.kind()==CampaignCanvas.LinkKind.REQUIRE&&l.to().equals("event:"+e.id())))&&!s.events.contains(e.id())&&e.effects().stream().noneMatch(f->(f.operation()==CampaignDefinition.Operation.GIVE||f.operation()==CampaignDefinition.Operation.TAKE)&&!f.everyone())&&map.npcWave()>=e.minWave()&&(e.rule().isBlank()||map.npcRuntime().isRuleComplete(e.rule()))&&eligible(s,e)){
            try{event(map,e.id(),null);}catch(RuntimeException error){s.failed=true;s.saveError=error.getMessage();map.sendNpcDialogue(Component.literal("战役事件已暂停："+error.getMessage()+"；原安全检查点仍保留。"));}if(s.settled||s.failed)break;
        }
        if(!s.pendingCheckpoint.isBlank())commitPending(s);
    }
    private static void commitPending(Session s){if(s.map.currentPhase()!=ZombiesGamePhase.INTERMISSION&&s.map.currentPhase()!=ZombiesGamePhase.OPENING_COUNTDOWN&&s.map.currentPhase()!=ZombiesGamePhase.VICTORY)return;try{checkpoint(s,s.pendingCheckpoint,s.pendingFinished,false);s.pendingCheckpoint="";if(s.pendingFinished)complete(s);s.pendingFinished=false;}catch(RuntimeException e){s.failed=true;s.saveError=e.getMessage();s.map.sendNpcDialogue(Component.literal("安全检查点未保存："+e.getMessage()+"；可在个人主页重试。"));}}
    private static boolean eligible(Session s,CampaignDefinition.Event e){return (e.state().isBlank()||str(object(s.world,"states"),e.state()).equals(e.equalsValue()))&&CampaignCanvasRuntime.ready(s.map,"event:"+e.id(),null);}
    public static boolean state(ZombiesMap map,String key,String expected){var s=session(map);return s!=null&&str(object(s.world,"states"),key).equals(expected);}
    public static boolean item(ZombiesMap map,ServerPlayer actor,String key,int amount){var s=session(map);return s!=null&&actor!=null&&s.inventories.containsKey(actor.getUUID())&&count(s.inventories.get(actor.getUUID()),key)>=amount;}
    private static int count(JsonObject items,String key){return items.has(key)?items.get(key).getAsInt():0;}
    public static void canvasActors(Session s){applyActors(s);}
    public static void canvasEffect(Session s,CampaignDefinition.Effect f,ServerPlayer actor){
        switch(f.operation()){
            case STATE->object(s.world,"states").addProperty(f.key(),f.value());
            case TASK->{object(s.world,"tasks").addProperty(f.key(),f.value());if(f.value().equals("active"))s.world.addProperty("activeTask",f.key());else if(str(s.world,"activeTask").equals(f.key()))s.world.addProperty("activeTask","");}
            case ACTOR->{if(!List.of("alive","dead","hidden").contains(f.value()))throw new IllegalArgumentException("人物状态为 alive、dead 或 hidden");object(s.world,"actors").addProperty(f.key(),f.value());}
            case COLLECTION->{s.rewards.add(f.key());object(s.world,"rewards").addProperty(s.chapter+"."+f.key(),true);}
            case GIVE,TAKE->{var ids=f.everyone()?online(s).stream().map(ServerPlayer::getUUID).toList():actor==null?List.<UUID>of():List.of(actor.getUUID());if(ids.isEmpty())throw new IllegalArgumentException("个人剧情物品效果需要玩家交互，或选择发给全队");for(var id:ids){var items=s.inventories.get(id);if(items==null)throw new IllegalArgumentException("玩家不在当前战役");int value=count(items,f.key())+(f.operation()==CampaignDefinition.Operation.GIVE?f.count():-f.count());if(value<0||value>2304)throw new IllegalArgumentException("剧情仓库数量不足或超出上限");items.addProperty(f.key(),value);}}
            default->throw new IllegalArgumentException("此效果请使用检查点标签或战役事件");
        }
    }
    public static String task(ZombiesMap map){var s=session(map);if(s==null)return "";String id=str(s.world,"activeTask");var entry=s.definition.tasks().stream().filter(t->t.id().equals(id)).findFirst().orElse(null);return entry==null?"":entry.description().isBlank()?entry.name():entry.description();}
    public static void event(ZombiesMap map,String id,ServerPlayer actor){var s=session(map);if(s==null)throw new IllegalArgumentException("请先从战役主页启动对局");if(s.settled||s.failed)throw new IllegalArgumentException("当前战役已结束或暂停");
        var e=s.definition.events().stream().filter(v->v.id().equals(id)&&v.chapter().equals(s.chapter)).findFirst().orElseThrow(()->new IllegalArgumentException("当前章节没有该战役事件："+id));if(s.events.contains(id))return;if(!(e.state().isBlank()||str(object(s.world,"states"),e.state()).equals(e.equalsValue()))||!CampaignCanvasRuntime.ready(map,"event:"+e.id(),actor))throw new IllegalArgumentException("战役事件前置条件尚未满足");
        captureActors(s);var oldWorld=s.world.deepCopy();var oldInventories=new LinkedHashMap<UUID,JsonObject>();s.inventories.forEach((p,i)->oldInventories.put(p,i.deepCopy()));var oldRewards=new HashSet<>(s.rewards);
        String checkpoint="";boolean finish=false;
        try{for(var f:e.effects())switch(f.operation()){
            case STATE->object(s.world,"states").addProperty(f.key(),f.value());
            case TASK->{object(s.world,"tasks").addProperty(f.key(),f.value());if(f.value().equals("active"))s.world.addProperty("activeTask",f.key());else if(str(s.world,"activeTask").equals(f.key()))s.world.addProperty("activeTask","");}
            case ACTOR->object(s.world,"actors").addProperty(f.key(),f.value());
            case COLLECTION->{s.rewards.add(f.key());object(s.world,"rewards").addProperty(s.chapter+"."+f.key(),true);}
            case GIVE,TAKE->{var ids=f.everyone()?online(s).stream().map(ServerPlayer::getUUID).toList():actor==null?List.<UUID>of():List.of(actor.getUUID());if(ids.isEmpty())throw new IllegalArgumentException("个人剧情物品操作需要玩家交互，或启用发给全队");for(var p:ids){var items=s.inventories.get(p);if(items==null)throw new IllegalArgumentException("玩家不在本战役");int n=count(items,f.key())+(f.operation()==CampaignDefinition.Operation.GIVE?f.count():-f.count());if(n<0||n>2304)throw new IllegalArgumentException("剧情道具数量不足或超出仓库上限");items.addProperty(f.key(),n);}}
            case CHECKPOINT->checkpoint=f.key();case COMPLETE->{finish=true;checkpoint=f.key();}
        }
        s.events.add(id);object(s.world,"events").addProperty(s.chapter+"."+id,true);applyActors(s);
        if(!checkpoint.isBlank()){
            if(map.currentPhase()!=ZombiesGamePhase.INTERMISSION&&map.currentPhase()!=ZombiesGamePhase.OPENING_COUNTDOWN&&map.currentPhase()!=ZombiesGamePhase.VICTORY)throw new IllegalArgumentException("请在安全休整阶段触发检查点或章节完成事件");
            s.pendingCheckpoint=checkpoint;s.pendingFinished|=finish;
        }
        }catch(RuntimeException error){s.world=oldWorld;s.inventories.clear();s.inventories.putAll(oldInventories);s.rewards.clear();s.rewards.addAll(oldRewards);s.events.remove(id);applyActors(s);throw error;}
    }
    private static void captureActors(Session s){var bindings=s.definition.chapter(s.chapter).actors();var alive=s.map.npcRuntime().campaignActors();for(var pair:bindings.entrySet())if(alive.containsKey(pair.getKey())&&!str(object(s.world,"actors"),pair.getValue()).equals("hidden"))object(s.world,"actors").addProperty(pair.getValue(),alive.get(pair.getKey())?"alive":"dead");}
    private static void applyActors(Session s){var bindings=s.definition.chapter(s.chapter).actors();for(var pair:bindings.entrySet()){String value=str(object(s.world,"actors"),pair.getValue());if(!value.isBlank())s.map.npcRuntime().campaignActor(pair.getKey(),value);}}
    public static void checkpoint(Session s,String name,boolean finished,boolean entry){
        for(var player:online(s))addMember(s,player);
        if(!entry&&s.map.currentPhase()!=ZombiesGamePhase.INTERMISSION&&s.map.currentPhase()!=ZombiesGamePhase.OPENING_COUNTDOWN&&s.map.currentPhase()!=ZombiesGamePhase.VICTORY)throw new IllegalArgumentException("安全检查点只能在开局、波间休整或通关时保存");
        captureActors(s);var local=new JsonObject();s.map.npcRuntime().captureCampaign(local);s.map.scenarioRuntime().captureCampaign(local);var barriers=new JsonArray();s.map.runtimeBarrierSnapshot().stream().map(b->b.group()).distinct().filter(s.map::npcBarrierOpen).forEach(barriers::add);local.add("barriers",barriers);
        String checkpointId=UUID.randomUUID().toString();var planned=new LinkedHashMap<UUID,JsonObject>();var online=online(s);var eligible=online.stream().map(ServerPlayer::getUUID).toList();
        for(var id:eligible){
            var save=s.saves.get(id).deepCopy();var point=new JsonObject();point.addProperty("id",checkpointId);point.addProperty("chapter",s.chapter);point.addProperty("checkpoint",name);point.addProperty("finished",finished);point.addProperty("wave",Math.max(1,s.map.npcWave()));point.addProperty("time",System.currentTimeMillis());point.add("world",s.world.deepCopy());point.add("items",s.inventories.get(id).deepCopy());point.add("local",local.deepCopy());
            var history=save.getAsJsonArray("history");if(entry&&!history.isEmpty()&&str(CampaignStore.tip(save),"chapter").equals(s.chapter)){
                // Resuming a mid-chapter checkpoint must retain the original chapter start for replays.
                if(!str(CampaignStore.tip(save),"checkpoint").equals("entry")){
                    if(!CampaignStore.get(s.map.getServerLevel().getServer()).account(id).getAsJsonObject("saves").has(str(save,"id")))planned.put(id,save);
                    continue;
                }history.remove(history.size()-1);
            }
            history.add(point);planned.put(id,save);
        }
        try{CampaignStore.get(s.map.getServerLevel().getServer()).transaction(root->{for(var p:online){var a=CampaignStore.account(root,p.getUUID());a.addProperty("name",p.getName().getString());if(s.keep.contains(p.getUUID())&&planned.containsKey(p.getUUID()))object(a,"saves").add(str(planned.get(p.getUUID()),"id"),planned.get(p.getUUID()));if(finished){object(a,"completed").addProperty(s.chapter,true);var reward=s.definition.chapter(s.chapter).reward();if(!reward.isBlank()&&!object(a,"collections").has(reward))object(a,"collections").addProperty(reward,System.currentTimeMillis());}if(finished)for(var reward:s.rewards)if(!object(a,"collections").has(reward))object(a,"collections").addProperty(reward,System.currentTimeMillis());}});
            s.saves.putAll(planned);s.saveError="";if(finished)s.rewards.clear();if(!planned.isEmpty())s.base=CampaignStore.tip(planned.getOrDefault(s.host,planned.values().iterator().next())).deepCopy();
        }catch(RuntimeException error){s.saveError="保存失败：已保留原安全检查点";throw error;}
    }
    public static void victory(ZombiesMap map){var s=session(map);if(s==null||s.settled||s.failed)return;try{checkpoint(s,"chapter_end",true,false);complete(s);}catch(RuntimeException e){s.failed=true;map.sendNpcDialogue(Component.literal("通关结果暂未保存："+e.getMessage()+"。请保留房间并在个人主页重试保存。"));}}
    private static void complete(Session s){s.settled=true;s.map.sendNpcDialogue(Component.literal("章节已完成，在线队员已获得结算收益。房主可从战役主页带队前往下一关。"));}
    public static void next(ServerPlayer p){var s=room(p);if(s==null||!s.host.equals(p.getUUID())||!s.settled)throw new IllegalArgumentException("需要房主在章节通关后继续");String next=s.definition.next(s.campaign,s.chapter);if(next.isBlank())throw new IllegalArgumentException("已经完成本战役");var target=s.definition.chapter(next).map().equals(s.map.getMapName())?s.map:target(p.getServer(),s.definition.chapter(next).map());var party=online(s);var source=s.saves.get(s.host);if(source==null||!CampaignStore.tip(source).get("finished").getAsBoolean())throw new IllegalArgumentException("请先保留并保存当前路线");
        var successor=new Session(target,s.host,s.definition,s.campaign,next,s.base,source);successor.keep.addAll(s.keep);successor.saves.putAll(s.saves);s.inventories.forEach((id,i)->successor.inventories.put(id,i.deepCopy()));
        QUEUE.computeIfAbsent(p.getServer(),k->new ArrayList<>()).add(()->{try{if(session(s.map)!=s)throw new IllegalArgumentException("原战役房间已变化");if(target!=s.map)target(p.getServer(),s.definition.chapter(next).map());if(target!=s.map&&!target.npcPlayers().isEmpty())throw new IllegalArgumentException("下一关已被其他队伍占用，请稍后重试");s.map.resetGame();transfer(s.map,target,party);SESSIONS.remove(s.map);SESSIONS.put(target,successor);for(var player:party)addMember(successor,player);target.campaignStartGame();if(!successor.started)throw new IllegalArgumentException("下一关启动失败，可从已保存的章节结算继续");}catch(RuntimeException e){party.forEach(v->v.sendSystemMessage(Component.literal(e.getMessage())));}});
    }
    public static void keep(ServerPlayer p,boolean yes){var s=room(p);if(s==null)throw new IllegalArgumentException("当前没有战役");if(!yes&&s.host.equals(p.getUUID()))throw new IllegalArgumentException("房主必须保留当前故事路线");
        if(yes){var save=s.saves.get(p.getUUID());if(save==null)throw new IllegalArgumentException("请等待入场初始化");CampaignStore.get(p.getServer()).transaction(root->object(CampaignStore.account(root,p.getUUID()),"saves").add(str(save,"id"),save.deepCopy()));s.keep.add(p.getUUID());}else s.keep.remove(p.getUUID());}
    public static void end(ServerPlayer p){var s=room(p);if(s==null||!s.host.equals(p.getUUID()))throw new IllegalArgumentException("只有房主可以结束战役房间");s.map.resetGame();SESSIONS.remove(s.map);}
    public static void retry(ServerPlayer p){var s=room(p);if(s==null||!s.host.equals(p.getUUID()))throw new IllegalArgumentException("只有房主可以重试保存");boolean finished=s.pendingFinished||s.map.currentPhase()==ZombiesGamePhase.VICTORY||s.settled;checkpoint(s,s.pendingCheckpoint.isBlank()?"retry":s.pendingCheckpoint,finished,false);s.pendingCheckpoint="";s.pendingFinished=false;s.failed=false;if(finished)complete(s);}
    public static void drain(MinecraftServer server){for(var r:List.copyOf(QUEUE.getOrDefault(server,List.of())))r.run();QUEUE.remove(server);}
    public static void cleanup(ZombiesMap map){CampaignVotes.clear(map);var s=session(map);if(s!=null&&!s.settled)SESSIONS.remove(map);}
    public static void stop(MinecraftServer server){SESSIONS.entrySet().removeIf(e->e.getKey().getServerLevel().getServer()==server);QUEUE.remove(server);CampaignStore.forget(server);}
}
