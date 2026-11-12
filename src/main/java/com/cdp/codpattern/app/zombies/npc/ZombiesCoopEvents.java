package com.cdp.codpattern.app.zombies.npc;
import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
@Mod.EventBusSubscriber(modid=ZombiesAddonConstants.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombiesCoopEvents {
    private ZombiesCoopEvents(){}
    private static final java.util.Set<java.util.UUID> recipients=new java.util.HashSet<>();
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void toss(net.minecraftforge.event.entity.item.ItemTossEvent event){
        if(!(event.getPlayer() instanceof ServerPlayer player))return;player.getPersistentData().remove(ZombiesCoopRuntime.PASS);
        var room=ZombiesCoopRuntime.room(player);if(room!=null)room.coopRuntime().markToss(player,event.getEntity());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void spawn(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide)return;
        if(event.getEntity() instanceof ItemEntity item&&item.getPersistentData().hasUUID(ZombiesCoopRuntime.DROP)&&!ZombiesCoopRuntime.dropAllowed(item)){event.setCanceled(true);return;}
        if(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile bullet&&bullet.getOwner() instanceof ZombiesNpcEntity npc){double multiplier=1;var owner=npc.owner();if(owner!=null){var map=ZombiesCoopRuntime.roomIdOf(npc);if(map!=null)multiplier=new com.cdp.codpattern.app.zombies.service.ZombiesWeaponItemStackService().sameRoomDamageMultiplier(npc.getMainHandItem(),map);}bullet.getPersistentData().putDouble("coop.npc.damage",multiplier);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void pickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event){
        var item=event.getItem();if(!item.getPersistentData().hasUUID(ZombiesCoopRuntime.DROP))return;event.setCanceled(true);
        if(event.getEntity() instanceof ServerPlayer player){var room=ZombiesCoopRuntime.room(player);if(room!=null)room.coopRuntime().pickup(player,item);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void noProne(net.minecraftforge.event.TickEvent.PlayerTickEvent e){if(e.phase==net.minecraftforge.event.TickEvent.Phase.END&&e.player instanceof ServerPlayer player&&(!com.tacz.guns.config.sync.SyncConfig.ENABLE_CRAWL.get()||ZombiesCoopRuntime.room(player)!=null))enforceNoProne(player);}
    public static void enforceNoProne(ServerPlayer p){var op=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(p);if(op.getDataHolder().isCrawling){op.crawl(false);if(!p.isInWaterOrBubble()&&p.getForcedPose()==net.minecraft.world.entity.Pose.SWIMMING){p.setForcedPose(null);p.setPose(p.isShiftKeyDown()?net.minecraft.world.entity.Pose.CROUCHING:net.minecraft.world.entity.Pose.STANDING);}}}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||event.getServer().getTickCount()%5!=0)return;
        var online=new java.util.HashSet<java.util.UUID>();for(var player:event.getServer().getPlayerList().getPlayers()){online.add(player.getUUID());var room=ZombiesCoopRuntime.room(player);if(room!=null){recipients.add(player.getUUID());ZombiesCoopNetwork.send(player,new ZombiesCoopNetwork.State(room.coopRuntime().snapshot(player,"","",0,null)));}else if(recipients.remove(player.getUUID()))ZombiesCoopNetwork.send(player,new ZombiesCoopNetwork.State(new net.minecraft.nbt.CompoundTag()));}recipients.retainAll(online);
    }
}
