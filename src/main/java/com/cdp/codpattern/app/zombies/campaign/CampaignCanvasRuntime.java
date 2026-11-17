package com.cdp.codpattern.app.zombies.campaign;

import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.JsonObject;
import java.util.*;
import static com.cdp.codpattern.app.zombies.campaign.CampaignRuntime.*;

/** Persistent choice facts and prerequisites, scoped to the selected story history. */
public final class CampaignCanvasRuntime {
    private CampaignCanvasRuntime(){}
    public static boolean ready(ZombiesMap map,String target,ServerPlayer player){var s=session(map);return s==null||s.definition.canvas().links().stream().filter(l->l.kind()==CampaignCanvas.LinkKind.REQUIRE&&l.to().equals(target)).allMatch(l->satisfied(s,l,player));}
    private static boolean satisfied(Session s,CampaignCanvas.Link link,ServerPlayer player){var p=CampaignCanvas.endpoint(link.from());return switch(p[0]){
        case "node"->str(object(s.world,"signals"),link.from()).equals(link.port());
        case "state"->str(object(s.world,"states"),p[1]).equals(link.value().isBlank()?"true":link.value());
        case "actor"->str(object(s.world,"actors"),p[1]).equals(link.value().isBlank()?"alive":link.value());
        case "item"->CampaignRuntime.item(s.map,player,p[1],link.count());
        case "task"->str(object(s.world,"tasks"),p[1]).equals(link.value().isBlank()?"done":link.value());
        case "event"->{var e=s.definition.events().stream().filter(v->v.id().equals(p[1])).findFirst().orElseThrow();yield object(s.world,"events").has(e.chapter()+"."+e.id());}
        case "tag"->object(s.world,"milestones").has(p[1]);
        default->false;
    };}
    public static String key(ZombiesMap map,String npc,String rule,String node){var s=session(map);return s==null?"":CampaignCanvas.node(s.chapter,npc,rule,node);}
    public static void reached(ZombiesMap map,String npc,String rule,String node,String port,ServerPlayer player){var s=session(map);if(s==null||s.failed||s.settled)return;String key=key(map,npc,rule,node);
        var old=s.world.deepCopy();var inventory=new LinkedHashMap<UUID,JsonObject>();s.inventories.forEach((id,i)->inventory.put(id,i.deepCopy()));var rewards=new HashSet<>(s.rewards);var events=new HashSet<>(s.events);String pending=s.pendingCheckpoint;boolean finished=s.pendingFinished;
        try{
            object(s.world,"signals").addProperty(key,port);
            for(var link:s.definition.canvas().links())if(link.kind()==CampaignCanvas.LinkKind.EFFECT&&link.from().equals(key)&&link.port().equals(port)&&!object(s.world,"canvasEffects").has(s.chapter+":"+link.id())){
                var target=CampaignCanvas.endpoint(link.to());if(target[0].equals("event"))CampaignRuntime.event(map,target[1],player);
                else {var op=switch(target[0]){case "state"->CampaignDefinition.Operation.STATE;case "actor"->CampaignDefinition.Operation.ACTOR;case "task"->CampaignDefinition.Operation.TASK;case "collection"->CampaignDefinition.Operation.COLLECTION;case "item"->link.value().equals("take")?CampaignDefinition.Operation.TAKE:CampaignDefinition.Operation.GIVE;default->throw new IllegalArgumentException("不支持的效果目标");};String value=link.value().isBlank()?switch(op){case ACTOR->"alive";case TASK->"active";default->"true";}:link.value();CampaignRuntime.canvasEffect(s,new CampaignDefinition.Effect(op,target[1],value,link.count(),link.everyone()),player);}
                object(s.world,"canvasEffects").addProperty(s.chapter+":"+link.id(),true);
            }
            for(var tag:s.definition.canvas().tags())if(tag.target().equals(key)&&tag.port().equals(port)&&!object(s.world,"milestones").has(tag.id())){object(s.world,"milestones").addProperty(tag.id(),true);if(tag.kind()==CampaignCanvas.TagKind.CHECKPOINT)s.pendingCheckpoint="标签："+tag.name();}
            CampaignRuntime.canvasActors(s);
        }catch(RuntimeException e){s.world=old;s.inventories.clear();s.inventories.putAll(inventory);s.rewards.clear();s.rewards.addAll(rewards);s.events.clear();s.events.addAll(events);s.pendingCheckpoint=pending;s.pendingFinished=finished;CampaignRuntime.canvasActors(s);throw e;}
    }
}
