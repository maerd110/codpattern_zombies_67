package com.cdp.codpattern.client.zombies;

import com.cdp.codpattern.app.zombies.npc.*;
import com.cdp.codpattern.client.gui.screen.zombies.deploy.CampaignScreen;
import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@Mod.EventBusSubscriber(modid=ZombiesAddonConstants.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class CampaignClient {
    public static final KeyMapping HOME=new KeyMapping("key.codpattern.zombies.campaign",GLFW.GLFW_KEY_H,"key.categories.codpattern.zombies");
    public static JsonObject vote=new JsonObject(),room=new JsonObject();
    private static int ticks;
    private static UUID polling;
    private static String dismissedVote="";
    private static String voteKey(){return vote.has("token")?vote.get("token").getAsString()+":"+vote.get("round").getAsInt():"";}
    public static void dismissVote(){dismissedVote=voteKey();}
    @SubscribeEvent public static void keys(net.minecraftforge.client.event.RegisterKeyMappingsEvent e){e.register(HOME);}
    public static UUID request(String operation,JsonObject payload){if(List.of("camp_keep","camp_next","camp_end","camp_retry").contains(operation)&&room.has("instance"))payload.add("instance",room.get("instance"));var token=UUID.randomUUID();ZombiesNpcDialogueNetwork.edit(new ZombiesNpcDialogueNetwork.EditorRequest(token,"",operation,payload.toString()));return token;}
    public static void accept(ZombiesNpcDialogueNetwork.EditorReply packet){
        JsonObject data;try{data=JsonParser.parseString(packet.payload()).getAsJsonObject();}catch(Exception e){return;}if(!data.has("campaignPage"))return;
        if(packet.token().equals(polling)){polling=null;if(data.has("error"))return;vote=data.has("vote")?data.getAsJsonObject("vote"):new JsonObject();room=data.has("room")?data.getAsJsonObject("room"):new JsonObject();var mc=Minecraft.getInstance();if(vote.size()>0&&!voteKey().equals(dismissedVote)&&!(mc.screen instanceof CampaignScreen)&&!(mc.screen instanceof com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesDeployToolScreen)&&mc.screen==null){var screen=new CampaignScreen(null,false);screen.showVote();mc.setScreen(screen);}if(mc.screen instanceof CampaignScreen s)s.pollUpdated();return;}
        CampaignScreen.accept(packet.token(),data);
    }
    @Mod.EventBusSubscriber(modid=ZombiesAddonConstants.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.getConnection()==null)return;if(++ticks%20==0)polling=request("camp_poll",new JsonObject());while(HOME.consumeClick())if(mc.screen==null){var screen=new CampaignScreen(null,false);mc.setScreen(screen);screen.load();}}
        @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){vote=new JsonObject();room=new JsonObject();polling=null;dismissedVote="";CampaignScreen.disconnected();}
    }
}
