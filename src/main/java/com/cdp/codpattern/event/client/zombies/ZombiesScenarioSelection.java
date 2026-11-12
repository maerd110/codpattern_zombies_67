package com.cdp.codpattern.event.client.zombies;

import com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesScenarioScreen;
import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import com.phasetranscrystal.fpsmatch.common.item.zombies.ZombiesDeployTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Explicit deployment selection mode consumes tool clicks and draws the selected bounding box. */
@Mod.EventBusSubscriber(modid=ZombiesAddonConstants.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombiesScenarioSelection {
    private ZombiesScenarioSelection() { }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event){ZombiesScenarioScreen.disconnected();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void input(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();if(!ZombiesScenarioScreen.selecting()||mc.screen!=null||mc.player==null||!(mc.player.getMainHandItem().getItem() instanceof ZombiesDeployTool))return;
        if(event.isAttack()||event.isUseItem()){
            event.setCanceled(true);event.setSwingHand(false);event.getKeyMapping().setDown(false);
            ZombiesScenarioScreen.selectionInput(event.isAttack(),event.isUseItem(),event.isUseItem()&&(Screen.hasShiftDown()||Screen.hasControlDown()),mc.hitResult instanceof BlockHitResult hit&&hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getBlockPos():null);
        }
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||Minecraft.getInstance().screen!=null)return;
        var bounds=ZombiesScenarioScreen.selectionBounds();if(bounds==null)return;var camera=event.getCamera().getPosition();
        renderSelectionBox(event.getPoseStack(),camera,bounds);
    }
    public static void renderSelectionBox(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.world.phys.Vec3 camera,net.minecraft.world.phys.AABB bounds){
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        // AFTER_PARTICLES already installs the camera view in RenderSystem. The event pose
        // also contains it: neutralise the global matrix while drawing so it is applied once.
        var modelView=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();modelView.pushPose();modelView.setIdentity();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {var buffers=net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(256));LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),bounds,0.35f,0.95f,0.8f,1f);buffers.endBatch(RenderType.lines());}
        finally {modelView.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();pose.popPose();}
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.screen!=null||!ZombiesScenarioScreen.selecting())return;renderSelectionOverlay(event.getGuiGraphics(),mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
    }
    public static void renderSelectionOverlay(net.minecraft.client.gui.GuiGraphics g,int w,int h){
        if(!ZombiesScenarioScreen.selecting())return;var mc=Minecraft.getInstance();int y=h-77;
        g.fill(8,y-5,w-8,y+27,0xD010202B);g.drawString(mc.font,mc.font.plainSubstrByWidth(ZombiesScenarioScreen.selectionHint(),w-28),14,y,0xFF83D9BA,false);
        var box=ZombiesScenarioScreen.selectionBounds();if(box!=null){String text="范围 "+(int)box.minX+", "+(int)box.minY+", "+(int)box.minZ+" → "+(int)(box.maxX-1)+", "+(int)(box.maxY-1)+", "+(int)(box.maxZ-1);g.drawString(mc.font,mc.font.plainSubstrByWidth(text,w-28),14,y+14,0xFFCBDCE5,false);}
    }
}
