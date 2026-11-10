package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZombiesAddonConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombiesNpcEvents {
    private ZombiesNpcEvents() { }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) { ZombiesNpcCommands.register(event.getDispatcher()); }
    @SubscribeEvent public static void loaded(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.loadedFromDisk() && event.getEntity() instanceof ZombiesNpcEntity) {
            event.setCanceled(true);
            event.getEntity().discard();
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void started(ServerStartedEvent event) {
        for (var level : event.getServer().getAllLevels()) ZombiesNpcBlockJournal.get(level).restoreAll(level);
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0
                && com.phasetranscrystal.fpsmatch.core.FPSMCore.initialized()) {
            ZombiesNpcRuntime.cleanupDetached(event.getServer(), false);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        ZombiesNpcRuntime.cleanupDetached(event.getServer(), true);
    }
}
