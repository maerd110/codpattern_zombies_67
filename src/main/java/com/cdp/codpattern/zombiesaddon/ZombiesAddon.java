package com.cdp.codpattern.zombiesaddon;

import com.cdp.codpattern.app.zombies.bootstrap.ZombiesBootstrap;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ZombiesAddonConstants.MOD_ID)
public final class ZombiesAddon {
    public ZombiesAddon() {
        var loadingContext = ModLoadingContext.get();
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        var localVersion = loadingContext.getActiveContainer().getModInfo().getVersion().toString();
        loadingContext.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,ZombiesClientConfig.SPEC);
        ZombiesAddonCompatibility.install(localVersion);
        ZombiesBootstrap.install(modEventBus);
    }
}
