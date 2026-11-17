package com.cdp.codpattern.app.zombies.campaign;

import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import com.cdp.codpattern.app.zombies.npc.*;
import java.util.*;

/** Every request is authorized server-side. Player requests can never submit story state or reward contents. */
public final class CampaignService {
    private CampaignService(){}
    private static final Map<UUID,Long> LAST=new HashMap<>();
    public static JsonObject perform(ServerPlayer p,String operation,String payload){
        var response=new JsonObject();response.addProperty("campaignPage",true);
        try{
            if(payload.length()>2*1024*1024)throw new IllegalArgumentException("请求过大");
            var q=payload.isBlank()?new JsonObject():JsonParser.parseString(payload).getAsJsonObject();
            var store=CampaignStore.get(p.getServer());
            if(List.of("camp_keep","camp_next","camp_end","camp_retry").contains(operation)){var s=CampaignRuntime.room(p);if(s==null||!s.instance.toString().equals(CampaignRuntime.str(q,"instance")))throw new IllegalArgumentException("房间已变化，请重新打开个人主页");}
            if(!operation.equals("camp_poll")){long now=p.getServer().getTickCount();long last=LAST.getOrDefault(p.getUUID(),-100L);if(now-last<2)throw new IllegalArgumentException("操作过快，请稍后重试");LAST.put(p.getUUID(),now);}
            switch(operation){
                case "camp_edit","camp_config"->{
                    if(!(p.getMainHandItem().getItem() instanceof com.phasetranscrystal.fpsmatch.common.item.zombies.ZombiesDeployTool)||!com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper.hasAdminAccess(p))throw new IllegalArgumentException("需要管理员权限并手持部署工具");
                    var def=CampaignStore.definition(p.getServer());
                    if(operation.equals("camp_config")){
                        if(CampaignRuntime.maps().stream().anyMatch(m->m.getServerLevel().getServer()==p.getServer()&&m.currentPhase()!=com.cdp.codpattern.app.zombies.model.ZombiesGamePhase.WAITING))throw new IllegalArgumentException("请先结束僵尸对局再修改战役配置");
                        if(!CampaignStore.revision(def).equals(CampaignRuntime.str(q,"revision")))throw new IllegalArgumentException("配置已被其他部署者修改，请重新读取");
                        var candidate=CampaignStore.GSON.fromJson(q.get("config"),CampaignDefinition.class);validate(p,candidate);
                        var test=new JsonObject();test.add("config",CampaignStore.GSON.toJsonTree(candidate));ZombiesNpcEditorWire.checkResponse(test.toString());
                        CampaignStore.atomic(CampaignStore.definitions(p.getServer()),CampaignStore.GSON.toJson(candidate));def=candidate;response.addProperty("notice","战役配置已保存；新启动的对局使用新配置");
                    }
                    response.addProperty("view","editor");response.add("config",CampaignStore.GSON.toJsonTree(def));response.addProperty("revision",CampaignStore.revision(def));var maps=new JsonArray();CampaignRuntime.maps().forEach(m->maps.add(m.getMapName()));response.add("maps",maps);
                }
                case "camp_launch"->{CampaignRuntime.launch(p,CampaignRuntime.str(q,"campaign"),CampaignRuntime.str(q,"save"),q.has("index")?q.get("index").getAsInt():-1);home(p,response);}
                case "camp_keep"->{CampaignRuntime.keep(p,q.get("keep").getAsBoolean());home(p,response);}
                case "camp_next"->{CampaignRuntime.next(p);home(p,response);}
                case "camp_end"->{CampaignRuntime.end(p);home(p,response);}
                case "camp_retry"->{CampaignRuntime.retry(p);home(p,response);}
                case "camp_delete"->{String id=CampaignRuntime.str(q,"save");var save=store.save(p.getUUID(),id);if(!CampaignStore.revision(save).equals(CampaignRuntime.str(q,"revision")))throw new IllegalArgumentException("存档已更新，请重新查看后确认删除");if(CampaignRuntime.maps().stream().map(CampaignRuntime::session).filter(Objects::nonNull).anyMatch(s->s.saves.containsKey(p.getUUID())&&id.equals(CampaignRuntime.str(s.saves.get(p.getUUID()),"id"))))throw new IllegalArgumentException("存档正在房间中使用，不能删除");store.transaction(root->CampaignStore.account(root,p.getUUID()).getAsJsonObject("saves").remove(id));home(p,response);}
                case "camp_detail"->{var save=store.save(p.getUUID(),CampaignRuntime.str(q,"save"));response.addProperty("view","save");response.add("save",save);response.addProperty("revision",CampaignStore.revision(save));publicDefinition(p,response);}
                case "camp_rename"->{String id=CampaignRuntime.str(q,"save"),name=CampaignRuntime.str(q,"name").trim();var save=store.save(p.getUUID(),id);if(name.isBlank()||name.length()>64)throw new IllegalArgumentException("存档名称需要 1 至 64 字");if(!CampaignStore.revision(save).equals(CampaignRuntime.str(q,"revision")))throw new IllegalArgumentException("存档已更新，请重新读取");store.transaction(root->CampaignRuntime.object(CampaignStore.account(root,p.getUUID()),"saves").getAsJsonObject(id).addProperty("name",name));for(var map:CampaignRuntime.maps()){var s=CampaignRuntime.session(map);if(s!=null&&s.saves.containsKey(p.getUUID())&&id.equals(CampaignRuntime.str(s.saves.get(p.getUUID()),"id")))s.saves.get(p.getUUID()).addProperty("name",name);}home(p,response);}
                case "camp_profile"->{UUID id=q.has("owner")?UUID.fromString(q.get("owner").getAsString()):p.getUUID();var a=store.account(id);if(!id.equals(p.getUUID())&&!a.get("public").getAsBoolean())throw new IllegalArgumentException("该玩家未公开收藏");response.addProperty("view","profile");response.addProperty("name",CampaignRuntime.str(a,"name"));response.add("collections",a.getAsJsonObject("collections").deepCopy());response.add("completed",a.getAsJsonObject("completed").deepCopy());publicDefinition(p,response);}
                case "camp_public"->{store.transaction(root->CampaignStore.account(root,p.getUUID()).addProperty("public",q.get("public").getAsBoolean()));home(p,response);}
                case "camp_vote"->{CampaignVotes.cast(p,CampaignRuntime.str(q,"token"),CampaignRuntime.str(q,"option"));response.addProperty("view","vote");response.add("vote",CampaignVotes.view(p));}
                case "camp_poll"->{response.addProperty("view","poll");response.add("vote",CampaignVotes.view(p));status(p,response);}
                case "camp_home"->home(p,response);
                default->throw new IllegalArgumentException("未知战役操作");
            }
            ZombiesNpcEditorWire.checkResponse(response.toString());
        }catch(Exception e){response.addProperty("error",e.getMessage()==null?"操作未完成":e.getMessage());}
        return response;
    }
    public static void validate(ServerPlayer p,CampaignDefinition def){
        for(var c:def.campaigns())for(var chapter:c.chapters()){
            var map=CampaignRuntime.maps().stream().filter(m->m.getMapName().equals(chapter.map())&&m.getServerLevel().getServer()==p.getServer()).findFirst().orElseThrow(()->new IllegalArgumentException("未部署章节地图："+chapter.map()));
            var npcs=ZombiesNpcRepository.load(ZombiesNpcRepository.path(p.getServer(),map.getMapName()));var keys=new HashSet<String>();var rules=new HashSet<String>();for(var n:npcs.npcs()){keys.add(n.id());for(var r:n.rules())rules.add(n.id()+"."+r.id());}
            if(!keys.containsAll(chapter.actors().keySet()))throw new IllegalArgumentException("人物绑定引用不存在的 NPC："+chapter.id());
            for(var event:def.events())if(event.chapter().equals(chapter.id())&&!event.rule().isBlank()&&!rules.contains(event.rule()))throw new IllegalArgumentException("事件引用不存在的 NPC 规则："+event.rule());
            for(var n:npcs.npcs())for(var r:n.rules())for(var action:r.executableActions())if(action.kind()==ZombiesNpcConfig.Kind.CAMPAIGN&&def.events().stream().noneMatch(e->e.id().equals(action.text())&&e.chapter().equals(chapter.id())))throw new IllegalArgumentException("NPC 动作引用未知战役事件："+action.text());
        }
    }
    private static void publicDefinition(ServerPlayer p,JsonObject out){var d=CampaignStore.definition(p.getServer());out.add("campaigns",CampaignStore.GSON.toJsonTree(d.campaigns()));out.add("itemNames",CampaignStore.GSON.toJsonTree(d.items()));out.add("collectionNames",CampaignStore.GSON.toJsonTree(d.collections()));}
    private static void home(ServerPlayer p,JsonObject out){out.addProperty("view","home");publicDefinition(p,out);var a=CampaignStore.get(p.getServer()).account(p.getUUID());out.addProperty("public",a.get("public").getAsBoolean());var saves=new JsonArray();for(var entry:a.getAsJsonObject("saves").entrySet()){var save=entry.getValue().getAsJsonObject();var row=new JsonObject();row.addProperty("id",entry.getKey());row.addProperty("name",CampaignRuntime.str(save,"name"));row.addProperty("campaign",CampaignRuntime.str(save,"campaign"));row.add("point",CampaignStore.tip(save).deepCopy());row.getAsJsonObject("point").remove("world");row.getAsJsonObject("point").remove("local");row.getAsJsonObject("point").remove("items");saves.add(row);}out.add("saves",saves);
        out.add("people",CampaignStore.get(p.getServer()).publicProfiles());status(p,out);
    }
    private static void status(ServerPlayer p,JsonObject out){var s=CampaignRuntime.room(p);if(s==null)return;var r=new JsonObject();r.addProperty("instance",s.instance.toString());r.addProperty("chapter",s.definition.chapter(s.chapter).name());r.addProperty("host",s.host.equals(p.getUUID()));r.addProperty("keep",s.keep.contains(p.getUUID()));r.addProperty("finished",s.settled);r.addProperty("error",s.saveError);r.add("items",s.inventories.getOrDefault(p.getUUID(),new JsonObject()).deepCopy());out.add("room",r);}
    public static void clear(){LAST.clear();}
}
