package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** A room-scoped, expiring two-step order: choose an NPC, then point at a server-resolved target. */
public final class ZombiesNpcOrders {
    private final ZombiesCoopRuntime room;private final ZombiesMap map;
    private record Pending(UUID npc,boolean guard,long expires){}
    private final Map<UUID,Pending> pending=new HashMap<>();
    ZombiesNpcOrders(ZombiesCoopRuntime room,ZombiesMap map){this.room=room;this.map=map;}
    public void arm(ServerPlayer player,int preferred,boolean guard){var npc=room.support(player,preferred);pending.put(player.getUUID(),new Pending(npc.getUUID(),guard,player.level().getGameTime()+600));}
    private Pending pending(ServerPlayer player){var p=pending.get(player.getUUID());if(p!=null&&(p.expires()<player.level().getGameTime()||!room.allowed(player)||!(map.getServerLevel().getEntity(p.npc()) instanceof ZombiesNpcEntity npc)||!map.npcRuntime().supportNpcs().contains(npc)||npc.posture()!=0)){pending.remove(player.getUUID());return null;}return p;}
    public void snapshot(ServerPlayer player,CompoundTag out){var p=pending(player);if(p!=null){out.putString("orderMode",p.guard()?"guard":"protect");out.putString("orderName",map.getServerLevel().getEntity(p.npc()).getName().getString());}}
    public void point(ServerPlayer player){var p=pending(player);if(p==null){room.pings.mark(player);return;}var npc=(ZombiesNpcEntity)map.getServerLevel().getEntity(p.npc());
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getViewVector(1).scale(96));var hit=player.level().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(p.guard()){
            if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP)throw new IllegalArgumentException("请用中键指定地面上表面");
            var feet=hit.getBlockPos().above();var at=new Vec3(feet.getX()+.5,hit.getLocation().y,feet.getZ()+.5);
            if(!room.contains(at)||!room.contains(at.add(0,1.8,0))||!player.level().hasChunkAt(feet)||!player.level().getWorldBorder().isWithinBounds(feet)||!player.level().noCollision(npc,npc.getBoundingBox().move(at.subtract(npc.position()))))throw new IllegalArgumentException("驻守点需要在本房间内且留有站立空间");
            npc.combat.guard(at);
        }else{
            double nearest=eye.distanceToSqr(hit.getLocation())+.05;ServerPlayer target=null;
            for(var teammate:map.npcPlayers()){if(teammate==player||!room.allowed(teammate))continue;var crossing=teammate.getBoundingBox().inflate(.25).clip(eye,end);if(crossing.isPresent()&&eye.distanceToSqr(crossing.get())<nearest){nearest=eye.distanceToSqr(crossing.get());target=teammate;}}
            if(target==null)throw new IllegalArgumentException("请用中键指定视线内存活的本房间队友");npc.combat.protect(target.getUUID());
        }pending.remove(player.getUUID());
    }
    public void cancel(ServerPlayer player){pending.remove(player.getUUID());}
    public void clear(){pending.clear();}
}
