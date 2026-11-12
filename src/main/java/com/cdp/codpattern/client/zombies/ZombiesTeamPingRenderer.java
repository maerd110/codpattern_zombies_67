package com.cdp.codpattern.client.zombies;

import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.*;

/** Project the room's world markers, then draw them in the HUD so terrain cannot hide them. */
@Mod.EventBusSubscriber(modid=ZombiesAddonConstants.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombiesTeamPingRenderer {
    public record Label(float x,float y,long meters,String text,boolean gun,String rarity,int level,int upgrade){}
    private static List<Label> frame=List.of();
    @SubscribeEvent public static void world(net.minecraftforge.client.event.RenderLevelStageEvent event){
        if(event.getStage()!=net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        frame=ZombiesCoopClient.active()?project(event.getPoseStack().last().pose(),RenderSystem.getProjectionMatrix(),event.getCamera().getPosition(),ZombiesCoopClient.state().getList("pings",Tag.TAG_COMPOUND)):List.of();
    }
    public static List<Label> project(Matrix4f view,Matrix4f projection,Vec3 eye,ListTag pings){
        var labels=new ArrayList<Label>();var matrix=new Matrix4f(projection).mul(view);
        for(var raw:pings){var p=(CompoundTag)raw;var at=new Vec3(p.getDouble("x"),p.getDouble("y"),p.getDouble("z"));var clip=matrix.transform(new Vector4f((float)(at.x-eye.x),(float)(at.y-eye.y+.35),(float)(at.z-eye.z),1));
            if(!Float.isFinite(clip.w)||clip.w<=0)continue;float x=clip.x/clip.w,y=clip.y/clip.w,z=clip.z/clip.w;
            if(!Float.isFinite(x)||!Float.isFinite(y)||z< -1||z>1||Math.abs(x)>1||Math.abs(y)>1)continue;
            labels.add(new Label((x+1)/2,(1-y)/2,Math.round(eye.distanceTo(at)),p.contains("weapon",Tag.TAG_COMPOUND)?p.getString("sender")+" · "+ZombiesGunNames.display(net.minecraft.world.item.ItemStack.of(p.getCompound("weapon"))).getString():p.getString("text"),p.getBoolean("gun"),p.getString("rarity"),p.getInt("level"),p.getInt("upgrade")));
        }return List.copyOf(labels);
    }
    @SubscribeEvent public static void hud(net.minecraftforge.client.event.RenderGuiEvent.Post event){var mc=Minecraft.getInstance();if(!ZombiesCoopClient.active()||mc.options.hideGui||mc.screen!=null)return;render(event.getGuiGraphics(),mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),frame);}
    public static void render(GuiGraphics g,int width,int height,List<Label> labels){var font=Minecraft.getInstance().font;
        for(var label:labels){String title="◆ "+font.plainSubstrByWidth(label.text(),Math.min(160,width-20)),distance=label.meters()+" m";String quality=label.gun()?ZombiesRarityDisplay.fromRarityId(label.rarity()).map(ZombiesRarityDisplay.Entry::label).orElse("预设")+" · Lv"+label.level()+" · 强化 +"+label.upgrade():"";int color=ZombiesRarityDisplay.fromRarityId(label.rarity()).map(ZombiesRarityDisplay.Entry::color).orElse(label.gun()?0xFFFFD27A:0xFF83D9BA);int span=Math.max(Math.max(font.width(title),font.width(distance)),font.width(quality)),x=Math.max(span/2+4,Math.min(width-span/2-4,Math.round(label.x()*width))),y=Math.max(28,Math.min(height-38,Math.round(label.y()*height)));
            g.fill(x-span/2-4,y-13,x+span/2+5,y+(label.gun()?24:12),0xB0101F29);g.drawCenteredString(font,title,x,y-10,color);if(label.gun())g.drawCenteredString(font,quality,x,y+1,color);g.drawCenteredString(font,distance,x,y+(label.gun()?13:1),0xFFE8F1F4);
        }
    }
}
