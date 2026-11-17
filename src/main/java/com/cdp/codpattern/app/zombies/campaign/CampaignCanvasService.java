package com.cdp.codpattern.app.zombies.campaign;

import com.cdp.codpattern.app.zombies.npc.*;
import com.cdp.codpattern.app.zombies.scenario.*;
import com.cdp.codpattern.app.zombies.validation.*;
import com.cdp.codpattern.app.zombies.map.*;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.nio.file.Path;

/** One authorized workspace bundles campaign assets and the physical NPC programs of deployed maps. */
public final class CampaignCanvasService {
    private CampaignCanvasService(){}
    public static JsonObject perform(ServerPlayer player,String operation,String payload){
        var out=new JsonObject();out.addProperty("campaignCanvas",true);
        try{
            if(!com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper.hasAdminAccess(player)||!(player.getMainHandItem().getItem() instanceof com.phasetranscrystal.fpsmatch.common.item.zombies.ZombiesDeployTool))throw new IllegalArgumentException("需要管理员权限并手持部署工具");
            if(payload.length()>2*1024*1024)throw new IllegalArgumentException("画布内容过大");
            CampaignAuthoringTransaction.recover(player.getServer());var request=payload.isBlank()?new JsonObject():JsonParser.parseString(payload).getAsJsonObject();var current=CampaignStore.definition(player.getServer());
            var maps=CampaignRuntime.maps().stream().filter(m->m.getServerLevel().getServer()==player.getServer()).sorted(Comparator.comparing(m->m.getMapName())).toList();
            if(operation.equals("canvas_icon")){var stack=player.getOffhandItem();if(stack.isEmpty())throw new IllegalArgumentException("请先把用于展示的物品放入副手，再手持部署工具读取");var copy=stack.copy();copy.setCount(1);out.addProperty("icon",copy.save(new net.minecraft.nbt.CompoundTag()).toString());out.addProperty("name",copy.getHoverName().getString());return out;}
            if(operation.equals("canvas_save")){
                if(maps.stream().anyMatch(m->m.currentPhase()!=ZombiesGamePhase.WAITING))throw new IllegalArgumentException("请先结束僵尸对局再保存战役工作区");
                if(!CampaignStore.revision(current).equals(CampaignRuntime.str(request,"revision")))throw new IllegalArgumentException("战役配置已被其他部署者修改，请重新读取");
                var def=CampaignStore.GSON.fromJson(request.get("config"),CampaignDefinition.class);var changes=new LinkedHashMap<Path,String>();var candidates=new HashMap<String,ZombiesNpcConfig>();
                for(var entry:request.getAsJsonObject("maps").entrySet()){
                    var map=maps.stream().filter(m->m.getMapName().equals(entry.getKey())).findFirst().orElseThrow(()->new IllegalArgumentException("关卡已被删除："+entry.getKey()));
                    var q=entry.getValue().getAsJsonObject();var path=ZombiesNpcRepository.path(player.getServer(),entry.getKey());var old=ZombiesNpcRepository.load(path);
                    if(!CampaignStore.revision(old).equals(CampaignRuntime.str(q,"revision")))throw new IllegalArgumentException("NPC 配置已被其他部署者修改："+entry.getKey());
                    var candidate=CampaignStore.GSON.fromJson(q.get("config"),ZombiesNpcConfig.class);if(!CampaignStore.revision(old).equals(CampaignStore.revision(candidate))){map.npcRuntime().validate(candidate);var scenario=ZombiesScenarioRepository.load(ZombiesScenarioRepository.path(player.getServer(),entry.getKey()));map.scenarioRuntime().validate(scenario,candidate);}candidates.put(entry.getKey(),candidate);
                    if(!CampaignStore.revision(old).equals(CampaignStore.revision(candidate)))changes.put(path,CampaignStore.GSON.toJson(candidate));
                }
                CampaignService.validate(player,def,candidates);
                var oldChapters=new HashSet<String>();for(var c:current.campaigns())for(var ch:c.chapters())oldChapters.add(ch.id());
                for(var c:def.campaigns())for(var ch:c.chapters())if(!oldChapters.contains(ch.id())){var map=maps.stream().filter(m->m.getMapName().equals(ch.map())).findFirst().orElseThrow();String error=readiness(map);if(!error.isBlank())throw new IllegalArgumentException("请先完成地图部署「"+ch.map()+"」："+error);}
                changes.put(CampaignStore.definitions(player.getServer()),CampaignStore.GSON.toJson(def));
                // Build and bound the complete response before the first persistent mutation.
                populate(player,out,def,candidates);ZombiesNpcEditorWire.checkResponse(out.toString());CampaignAuthoringTransaction.commit(player.getServer(),changes);maps.forEach(m->m.npcRuntime().reset());out.addProperty("notice","战役画布与各关 NPC 配置已一起保存");return out;
            }
            if(!operation.equals("canvas_load"))throw new IllegalArgumentException("未知画布操作");populate(player,out,current,Map.of());ZombiesNpcEditorWire.checkResponse(out.toString());
        }catch(Exception error){out=new JsonObject();out.addProperty("campaignCanvas",true);out.addProperty("error",com.cdp.codpattern.app.zombies.service.ZombiesStartupDiagnostics.reason(error));}
        return out;
    }
    private static String readiness(com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap map){try{var report=new ZombiesMapValidator(ZombiesMapValidationProfile.MVP3_FULL_INITIAL).validate(ZombiesMapSnapshot.fromMapObjects(com.cdp.codpattern.app.match.model.RoomId.of("zombies",map.getMapName()),map.getMapName(),false,map.getServerLevel().dimension().location().toString(),ZombiesMapSnapshot.BoundsSnapshot.fromAreaData(map.getMapArea()),map.barrierGroupRulesForPreview().resolveObjects(map.objects())));return report.errors().stream().map(e->e.code().key()+" · "+e.subject()+" · "+e.message()).findFirst().orElse("");}catch(RuntimeException e){return e.getMessage();}}
    private static void populate(ServerPlayer p,JsonObject out,CampaignDefinition def,Map<String,ZombiesNpcConfig> candidates){out.add("config",CampaignStore.GSON.toJsonTree(def));out.addProperty("revision",CampaignStore.revision(def));var data=new JsonObject();for(var map:CampaignRuntime.maps())if(map.getServerLevel().getServer()==p.getServer()){
        var row=new JsonObject();var c=candidates.containsKey(map.getMapName())?candidates.get(map.getMapName()):ZombiesNpcRepository.load(ZombiesNpcRepository.path(p.getServer(),map.getMapName()));row.add("config",CampaignStore.GSON.toJsonTree(c));row.addProperty("revision",CampaignStore.revision(c));row.add("barriers",CampaignStore.GSON.toJsonTree(map.barriers().stream().map(b->b.group()).distinct().sorted().toList()));var s=ZombiesScenarioRepository.load(ZombiesScenarioRepository.path(p.getServer(),map.getMapName()));row.add("events",CampaignStore.GSON.toJsonTree(java.util.stream.Stream.concat(s.events().stream().map(ZombiesScenarioConfig.MapEvent::id),s.tasks().stream().map(ZombiesScenarioConfig.Task::id)).toList()));row.addProperty("issue",readiness(map));row.addProperty("editable",map.currentPhase()==ZombiesGamePhase.WAITING);row.addProperty("dimension",map.getServerLevel().dimension().location().toString());data.add(map.getMapName(),row);
    }out.add("maps",data);out.add("position",ZombiesNpcRepository.GSON.toJsonTree(new ZombiesNpcConfig.Position(p.getX(),p.getY(),p.getZ(),p.getYRot())));out.addProperty("dimension",p.serverLevel().dimension().location().toString());}
}
