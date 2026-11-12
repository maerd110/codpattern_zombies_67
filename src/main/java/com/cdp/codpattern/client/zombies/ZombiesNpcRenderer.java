package com.cdp.codpattern.client.zombies;

import com.cdp.codpattern.app.zombies.npc.ZombiesNpcEntity;
import com.cdp.codpattern.app.zombies.npc.ZombiesNpcRegister;
import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZombiesAddonConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ZombiesNpcRenderer extends MobRenderer<ZombiesNpcEntity, PlayerModel<ZombiesNpcEntity>> {
    public ZombiesNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombiesNpcPoseModel(context.bakeLayer(ModelLayers.PLAYER)), 0.4F);
        addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<>(this,context.getItemInHandRenderer()));
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ZombiesNpcRegister.NPC.get(), ZombiesNpcRenderer::new);
    }
    @Override public ResourceLocation getTextureLocation(ZombiesNpcEntity npc) { return ResourceLocation.tryParse(npc.skin()); }
    @Override protected void setupRotations(ZombiesNpcEntity npc, PoseStack stack, float age, float yaw, float partialTick) {
        super.setupRotations(npc, stack, age, yaw, partialTick);
        if (npc.posture()==4) {
            stack.translate(0,.26,-1);stack.mulPose(Axis.XP.rotationDegrees(90));
        } else if (!npc.storyAlive() || npc.posture() == 2) {
            // Centre the sideways model above its position, inside the matching horizontal hit box.
            stack.translate(.9, .26, 0);
            stack.mulPose(Axis.ZP.rotationDegrees(90));
        } else if(npc.posture()==3){stack.translate(0,-.54,0);
        } else if (npc.posture() == 1) {
            stack.translate(0, -0.55, 0);
        }
    }
}
