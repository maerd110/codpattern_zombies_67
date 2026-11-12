package com.cdp.codpattern.app.zombies.npc;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.nbt.*;
import java.util.*;
/** Positions are resolved from the server's ray, never accepted from client coordinates. */
public final class ZombiesTeamPings {
    private final ZombiesCoopRuntime room;
    private final Map<UUID,Ping> marks=new LinkedHashMap<>();
    private record Ping(ServerPlayer sender,Vec3 point,ItemEntity gun,long until,long created){}
    ZombiesTeamPings(ZombiesCoopRuntime room){this.room=room;}
    public void mark(ServerPlayer player){
        if(!room.allowed(player))throw new IllegalArgumentException("当前不能标记");long now=player.level().getGameTime();var old=marks.get(player.getUUID());
        if(old!=null&&now<old.until()&&(old.gun()==null||!old.gun().isRemoved())){var at=old.gun()==null?old.point():old.gun().position().add(0,.2,0);var eye=player.getEyePosition();var look=player.getViewVector(1);if(aimsAt(eye,look,at)||aimsAt(eye,look,at.add(0,.35,0))){marks.remove(player.getUUID());return;}}
        if(old!=null&&now-old.created()<10)return;
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getViewVector(1).scale(96));var hit=player.level().clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));double nearest=eye.distanceToSqr(hit.getLocation())+.5;ItemEntity chosen=null;
        for(var item:player.level().getEntitiesOfClass(ItemEntity.class,new AABB(eye,end).inflate(1),i->ZombiesCoopRuntime.dropAllowed(i)&&room.session.equals(i.getPersistentData().getUUID(ZombiesCoopRuntime.DROP)))){var crossing=item.getBoundingBox().inflate(.35).clip(eye,end);if(crossing.isPresent()&&eye.distanceToSqr(crossing.get())<nearest){nearest=eye.distanceToSqr(crossing.get());chosen=item;}}
        var point=chosen==null?hit.getLocation():chosen.position().add(0,.2,0);if(chosen==null&&(hit.getType()==HitResult.Type.MISS||!room.contains(point)))throw new IllegalArgumentException("请看向本房间内的位置或掉落枪械");marks.put(player.getUUID(),new Ping(player,point,chosen,now+300,now));
    }
    public ListTag snapshot(){var out=new ListTag();marks.values().removeIf(p->p.sender().level().getGameTime()>=p.until()||(p.gun()!=null&&p.gun().isRemoved()));for(var p:marks.values()){var pos=p.gun()==null?p.point():p.gun().position().add(0,.2,0);var tag=new CompoundTag();tag.putDouble("x",pos.x);tag.putDouble("y",pos.y);tag.putDouble("z",pos.z);tag.putString("text",p.sender().getName().getString()+" · "+(p.gun()==null?"位置":p.gun().getItem().getHoverName().getString()));tag.putBoolean("gun",p.gun()!=null);out.add(tag);}return out;}
    private static boolean aimsAt(Vec3 eye,Vec3 look,Vec3 point){var delta=point.subtract(eye);return delta.lengthSqr()<=96*96&&delta.lengthSqr()>0&&look.dot(delta.normalize())>=Math.cos(Math.toRadians(3));}
    public void clear(ServerPlayer player){marks.remove(player.getUUID());}
    public void clear(){marks.clear();}
}
