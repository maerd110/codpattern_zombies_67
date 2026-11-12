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
    private static CompoundTag state=new CompoundTag();private static long updated;private static String notice="";private static long noticeUntil;
    @SubscribeEvent public static void keys(net.minecraftforge.client.event.RegisterKeyMappingsEvent e){e.register(BANK);e.register(CALL);}
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
        @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){clear();}
        @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();while(BANK.consumeClick())if(mc.screen==null&&mc.player!=null)request("open",0,0,ZombiesCoopNetwork.ZERO,ZombiesCoopNetwork.ZERO);while(CALL.consumeClick())if(mc.screen==null&&active()){int preferred=mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit?hit.getEntity().getId():-1;request("call",0,preferred,ZombiesCoopNetwork.ZERO,state.getUUID("session"));}}
        @SubscribeEvent public static void hud(net.minecraftforge.client.event.RenderGuiEvent.Post e){if(!active()||Minecraft.getInstance().screen!=null)return;renderHud(e.getGuiGraphics(),Minecraft.getInstance().getWindow().getGuiScaledWidth(),Minecraft.getInstance().getWindow().getGuiScaledHeight());}
    }
    public static void renderHud(net.minecraft.client.gui.GuiGraphics g,int width,int height){if(!active())return;var font=Minecraft.getInstance().font;int x=Math.max(4,width-178),y=Math.max(66,height/2-24);var list=state.getList("npcs",Tag.TAG_COMPOUND);int count=Math.min(3,list.size());g.fill(x-5,y-5,width-4,y+16+count*25+(count>0?13:0),0xA8101B24);g.drawString(font,font.plainSubstrByWidth("共享银行 "+state.getLong("bank")+" ["+BANK.getTranslatedKeyMessage().getString()+"]",164),x,y,0xFFFFD27A,false);
        for(int i=0;i<count;i++){var npc=list.getCompound(i);int row=y+16+i*25;g.drawString(font,font.plainSubstrByWidth(npc.getString("name")+"  ♥"+(int)npc.getFloat("hp")+(npc.getBoolean("called")?" · 靠近中":""),164),x,row,0xFFCFDFE5,false);int color=ZombiesRarityDisplay.fromRarityId(npc.getString("rarity")).map(ZombiesRarityDisplay.Entry::color).orElse(0xFF9CB6C6);g.drawString(font,font.plainSubstrByWidth(gun(npc).getString()+" · "+quality(npc),164),x,row+10,color,false);}
        if(count>0)g.drawString(font,font.plainSubstrByWidth("呼叫 ["+CALL.getTranslatedKeyMessage().getString()+"] · 蹲下右键换枪",164),x,y+17+count*25,0xFF83D9BA,false);
        if(!notice().isBlank())g.drawCenteredString(font,font.plainSubstrByWidth(notice(),width-20),width/2,height-65,0xFFFFD27A);
    }
}
