package com.cdp.codpattern.client.zombies;

import com.cdp.codpattern.zombiesaddon.*;
import net.minecraft.client.gui.screens.controls.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keep the wheel behaviour preference beside Minecraft's key bindings. */
@Mod.EventBusSubscriber(modid=ZombiesAddonConstants.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombiesCoopControls {
    @SubscribeEvent public static void init(net.minecraftforge.client.event.ScreenEvent.Init.Post event){if(!(event.getScreen() instanceof KeyBindsScreen screen))return;
        for(var child:screen.children())if(child instanceof KeyBindsList list)list.updateSize(screen.width,screen.height,43,screen.height-62);
        event.addListener(Button.builder(caption(),button->{ZombiesClientConfig.setHoldWheel(!ZombiesClientConfig.holdWheel());button.setMessage(caption());}).bounds(screen.width/2-155,screen.height-54,310,20).build());
    }
    private static Component caption(){return Component.literal("协作轮盘："+(ZombiesClientConfig.holdWheel()?"按住显示，松开选择":"单击切换，再按关闭"));}
}
