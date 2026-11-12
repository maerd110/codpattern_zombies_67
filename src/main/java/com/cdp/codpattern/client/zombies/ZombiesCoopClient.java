package com.cdp.codpattern.client.zombies;
import com.cdp.codpattern.app.zombies.npc.ZombiesCoopNetwork;
import com.cdp.codpattern.client.gui.screen.zombies.ZombiesCoopScreen;
import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import net.minecraft.client.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;
@Mod.EventBusSubscriber(modid=ZombiesAddonConstants.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ZombiesCoopClient {
    public static final KeyMapping BANK=new KeyMapping("key.codpattern.zombies.bank",GLFW.GLFW_KEY_K,"key.categories.codpattern.zombies");
    public static final KeyMapping CALL=new KeyMapping("key.codpattern.zombies.call_npc",GLFW.GLFW_KEY_N,"key.categories.codpattern.zombies");
    public static final KeyMapping PING=new KeyMapping("key.codpattern.zombies.ping",com.mojang.blaze3d.platform.InputConstants.Type.MOUSE,2,"key.categories.codpattern.zombies");
    private static CompoundTag state=new CompoundTag();private static long updated;private static String notice="";private static long noticeUntil;
    @SubscribeEvent public static void keys(net.minecraftforge.client.event.RegisterKeyMappingsEvent e){e.register(BANK);e.register(CALL);e.register(PING);}
    public static CompoundTag state(){return state;}
    public static boolean active(){return state.getBoolean("active")&&System.currentTimeMillis()-updated<2500;}
    public static Component gun(CompoundTag npc){try{var c=Component.Serializer.fromJson(npc.getString("gun"));return c==null?Component.literal("未持枪"):c;}catch(Exception ignored){return Component.literal("未持枪");}}
    public static String quality(CompoundTag npc){return ZombiesRarityDisplay.fromRarityId(npc.getString("rarity")).map(ZombiesRarityDisplay.Entry::label).orElse("预设")+" · Lv"+npc.getInt("level")+" +"+npc.getInt("upgrade");}
    public static void accept(ZombiesCoopNetwork.State packet){var mc=Minecraft.getInstance();var next=packet.data();
        if(mc.screen instanceof ZombiesCoopScreen screen&&(!next.getBoolean("active")||!next.hasUUID("session")||!screen.session.equals(next.getUUID("session"))))mc.setScreen(null);
        state=next;updated=System.currentTimeMillis();if(!next.getString("notice").isBlank()){notice=next.getString("notice");noticeUntil=updated+4000;}
        if(next.getBoolean("active")&&!next.getString("page").isBlank())mc.setScreen(new ZombiesCoopScreen(next.getString("page").equals("npc"),next.getInt("focus"),next.hasUUID("token")?next.getUUID("token"):ZombiesCoopNetwork.ZERO));
    }
    public static String notice(){return System.currentTimeMillis()<noticeUntil?notice:"";}
    public static void request(String op,int amount,int entity,UUID token,UUID session){ZombiesCoopNetwork.request(new ZombiesCoopNetwork.Action(op,session,token,amount,entity));}
    public static void clear(){state=new CompoundTag();notice="";updated=0;}
    @Mod.EventBusSubscriber(modid=ZombiesAddonConstants.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void mouse(net.minecraftforge.client.event.InputEvent.MouseButton.Pre e){var mc=Minecraft.getInstance();if(mc.screen!=null||mc.player==null||!active()||!state.getBoolean("interactive"))return;if(e.getAction()==GLFW.GLFW_PRESS&&PING.matchesMouse(e.getButton())){e.setCanceled(true);request("ping",0,0,ZombiesCoopNetwork.ZERO,state.getUUID("session"));}else if(e.getAction()==GLFW.GLFW_PRESS&&mc.options.keyUse.matchesMouse(e.getButton())&&interactNpc()){e.setCanceled(true);}}
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void use(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e){if(e.isUseItem()&&interactNpc()){e.setCanceled(true);e.setSwingHand(false);e.getKeyMapping().setDown(false);}}
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void crawlKey(net.minecraftforge.client.event.InputEvent.Key e){if(noProne())clearClientProne();}
        @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){clear();}
        @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(noProne())clearClientProne();while(PING.consumeClick())if(mc.screen==null&&active()&&state.getBoolean("interactive"))request("ping",0,0,ZombiesCoopNetwork.ZERO,state.getUUID("session"));while(BANK.consumeClick())if(mc.screen==null&&mc.player!=null)request("open",0,0,ZombiesCoopNetwork.ZERO,ZombiesCoopNetwork.ZERO);while(CALL.consumeClick())if(mc.screen==null&&active()&&state.getBoolean("interactive")){int preferred=mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit?hit.getEntity().getId():-1;request("call",0,preferred,ZombiesCoopNetwork.ZERO,state.getUUID("session"));}}
        @SubscribeEvent public static void hud(net.minecraftforge.client.event.RenderGuiEvent.Post e){if(!active()||Minecraft.getInstance().screen!=null)return;renderHud(e.getGuiGraphics(),Minecraft.getInstance().getWindow().getGuiScaledWidth(),Minecraft.getInstance().getWindow().getGuiScaledHeight());}
    }
    private static long lastInteraction;
    private static boolean noProne(){return Minecraft.getInstance().player!=null&&(active()||!com.tacz.guns.config.sync.SyncConfig.ENABLE_CRAWL.get());}
    private static void clearClientProne(){var p=Minecraft.getInstance().player;if(p!=null){var op=com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(p);if(op.isCrawl())op.crawl(false);}}
    private static boolean interactNpc(){var mc=Minecraft.getInstance();if(mc.screen!=null||mc.player==null||mc.level==null||!active()||!state.getBoolean("interactive")||mc.player.getMainHandItem().getItem() instanceof com.phasetranscrystal.fpsmatch.common.item.zombies.ZombiesDeployTool)return false;var eye=mc.player.getEyePosition();var end=eye.add(mc.player.getViewVector(1).scale(6));var block=mc.level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));double closest=eye.distanceToSqr(block.getLocation())+.05;int id=-1;
        for(var n:mc.level.getEntitiesOfClass(com.cdp.codpattern.app.zombies.npc.ZombiesNpcEntity.class,new net.minecraft.world.phys.AABB(eye,end).inflate(1),n->n.storyAlive())){var hit=n.getBoundingBox().inflate(.1).clip(eye,end);if(hit.isPresent()&&eye.distanceToSqr(hit.get())<closest){closest=eye.distanceToSqr(hit.get());id=n.getId();}}
        if(id<0)return false;long now=System.currentTimeMillis();if(now-lastInteraction>200){lastInteraction=now;request("interact",0,id,ZombiesCoopNetwork.ZERO,state.getUUID("session"));}return true;
    }
    public static void renderTask(net.minecraft.client.gui.GuiGraphics g,int width){var text=state.getString("task");if(text.isBlank())return;var font=Minecraft.getInstance().font;var lines=font.split(Component.literal(text),Math.min(360,width-62));int count=Math.min(3,lines.size()),span=0;for(int i=0;i<count;i++)span=Math.max(span,font.width(lines.get(i)));int left=(width-span-24)/2,top=10;g.fill(left-7,top-5,left+span+29,top+count*11+5,0xBC101F29);
        // A small folded task sheet and an amber objective tab, drawn at native GUI resolution.
        g.fill(left,top+1,left+12,top+15,0xFFB8D9DC);g.fill(left+1,top+2,left+11,top+14,0xFF294654);g.fill(left+3,top+4,left+9,top+5,0xFFE5EFF2);g.fill(left+3,top+7,left+9,top+8,0xFFE5EFF2);g.fill(left+3,top+10,left+7,top+11,0xFFE5EFF2);g.fill(left+8,top,left+12,top+4,0xFFFFCB70);for(int i=0;i<count;i++)g.drawString(font,lines.get(i),left+22,top+i*11,0xFFE4EFF3,false);
    }
    public static void renderHud(net.minecraft.client.gui.GuiGraphics g,int width,int height){if(!active())return;var font=Minecraft.getInstance().font;renderTask(g,width);int x=Math.max(4,width-178);var list=state.getList("npcs",Tag.TAG_COMPOUND);int count=Math.min(3,list.size());int panelHeight=16+count*25+(count>0?13:0);int weaponTop=com.cdp.codpattern.client.gui.overlay.zombies.ZombiesWeaponPanelLayout.reservedTop(width,height,(float)(2/Math.max(1,Minecraft.getInstance().getWindow().getGuiScale())),font.lineHeight,font.width("0"));int y=Math.max(50,weaponTop-panelHeight-10);g.fill(x-5,y-5,width-12,y+16+count*25+(count>0?13:0),0xA8101B24);g.drawString(font,font.plainSubstrByWidth("共享银行 "+state.getLong("bank")+" ["+BANK.getTranslatedKeyMessage().getString()+"]",164),x,y,0xFFFFD27A,false);
        for(int i=0;i<count;i++){var npc=list.getCompound(i);int row=y+16+i*25;g.drawString(font,font.plainSubstrByWidth(npc.getString("name")+"  ♥"+(int)npc.getFloat("hp")+(npc.getBoolean("called")?" · 靠近中":""),164),x,row,0xFFCFDFE5,false);int color=ZombiesRarityDisplay.fromRarityId(npc.getString("rarity")).map(ZombiesRarityDisplay.Entry::color).orElse(0xFF9CB6C6);g.drawString(font,font.plainSubstrByWidth(gun(npc).getString()+" · "+quality(npc),164),x,row+10,color,false);}
        if(count>0)g.drawString(font,font.plainSubstrByWidth("呼叫 ["+CALL.getTranslatedKeyMessage().getString()+"] · 靠近后右键换枪",164),x,y+17+count*25,0xFF83D9BA,false);
        if(!notice().isBlank())g.drawCenteredString(font,font.plainSubstrByWidth(notice(),width-20),width/2,height-65,0xFFFFD27A);
    }
}
