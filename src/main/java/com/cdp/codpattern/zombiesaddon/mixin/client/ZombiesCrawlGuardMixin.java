package com.cdp.codpattern.zombiesaddon.mixin.client;

import com.cdp.codpattern.client.zombies.ZombiesCoopClient;
import com.tacz.guns.client.gameplay.LocalPlayerCrawl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject crawling before TaCZ starts its animation, packet or ten-tick cooldown. */
@Mixin(value = LocalPlayerCrawl.class, remap = false)
public abstract class ZombiesCrawlGuardMixin {
    @Shadow @Final private LocalPlayer player;
    @Shadow private boolean isCrawling;
    @Shadow private int crawCooldownTicks;

    @Inject(method = "crawl", at = @At("HEAD"), cancellable = true, remap = false)
    private void zombies$guardCrawl(boolean crawling, CallbackInfo callback) {
        if (!ZombiesCoopClient.noProne()) return;
        // Also clear a crawl carried into the room, even during TaCZ's cooldown.
        isCrawling = false;
        crawCooldownTicks = 0;
        if (player != null && !player.isInWaterOrBubble() && player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
        if (crawling) callback.cancel();
    }
}
