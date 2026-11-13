package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.client.runtime.ModeClientActionHandlers;
import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;

/** A separate, direction-checked channel keeps story conversations independent of deployment permissions. */
public final class ZombiesNpcDialogueNetwork {
    public static final String CLIENT_ACTION = "codpattern:zombies_npc_dialogue";
    private static SimpleChannel channel;
    private ZombiesNpcDialogueNetwork() { }
    public record Answer(String id, String text) { }
    public record View(UUID token, boolean open, int entityId, Component name, Component text, List<Answer> options) {
        public View { options=List.copyOf(options); }
        public void encode(FriendlyByteBuf b) {
            b.writeUUID(token); b.writeBoolean(open); b.writeVarInt(entityId); b.writeComponent(name); b.writeComponent(text);
            b.writeVarInt(options.size()); for(var o:options){b.writeUtf(o.id(),64);b.writeUtf(o.text(),256);}
        }
        public static View decode(FriendlyByteBuf b) {
            var id=b.readUUID(); boolean open=b.readBoolean(); int entity=b.readVarInt(); var name=b.readComponent();var text=b.readComponent();
            int size=b.readVarInt();if(size<0||size>12)throw new IllegalArgumentException("Invalid dialogue options");
            var options=new ArrayList<Answer>();for(int i=0;i<size;i++)options.add(new Answer(b.readUtf(64),b.readUtf(256)));
            return new View(id,open,entity,name,text,options);
        }
    }
    public record Selection(UUID token, String option) {
        public void encode(FriendlyByteBuf b){b.writeUUID(token);b.writeUtf(option,64);}
        public static Selection decode(FriendlyByteBuf b){return new Selection(b.readUUID(),b.readUtf(64));}
    }
    public record EditorRequest(UUID token,String map,String operation,String payload){
        public void encode(FriendlyByteBuf b){b.writeUUID(token);b.writeUtf(map,256);b.writeUtf(operation,16);ZombiesNpcEditorWire.write(b,payload,true);}
        public static EditorRequest decode(FriendlyByteBuf b){return new EditorRequest(b.readUUID(),b.readUtf(256),b.readUtf(16),ZombiesNpcEditorWire.read(b,true));}
    }
    public record EditorFragment(UUID token,String map,String operation,int index,int total,byte[] bytes){
        public void encode(FriendlyByteBuf b){b.writeUUID(token);b.writeUtf(map,256);b.writeUtf(operation,16);b.writeVarInt(index);b.writeVarInt(total);b.writeByteArray(bytes);}
        public static EditorFragment decode(FriendlyByteBuf b){return new EditorFragment(b.readUUID(),b.readUtf(256),b.readUtf(16),b.readVarInt(),b.readVarInt(),b.readByteArray(28000));}
    }
    public record EditorReply(UUID token,String payload){
        public void encode(FriendlyByteBuf b){b.writeUUID(token);ZombiesNpcEditorWire.write(b,payload,false);}
        public static EditorReply decode(FriendlyByteBuf b){return new EditorReply(b.readUUID(),ZombiesNpcEditorWire.read(b,false));}
    }
    public static void edit(EditorRequest request){var parts=ZombiesNpcEditorWire.requestParts(request.payload());if(parts.size()==1)channel.sendToServer(request);else for(int i=0;i<parts.size();i++)channel.sendToServer(new EditorFragment(request.token(),request.map(),request.operation(),i,parts.size(),parts.get(i)));}
    private static void reply(ServerPlayer player,EditorRequest packet){Object result=packet.operation().startsWith("config_")?com.cdp.codpattern.app.zombies.deploy.ZombiesRulesEditorService.perform(player,packet.map(),packet.operation(),packet.payload()):com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioEditorService.perform(player,packet.map(),packet.operation(),packet.payload());if(player.connection!=null)channel.send(PacketDistributor.PLAYER.with(()->player),new EditorReply(packet.token(),ZombiesNpcRepository.GSON.toJson(result)));}
    public static void install() {
        if(channel!=null)return;
        channel=NetworkRegistry.newSimpleChannel(new ResourceLocation(ZombiesAddonConstants.MOD_ID,"npc_dialogue"),()->"10","10"::equals,"10"::equals);
        channel.messageBuilder(View.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder(View::encode).decoder(View::decode)
                .consumerMainThread((packet,ctx)->{ModeClientActionHandlers.dispatch(CLIENT_ACTION,packet);ctx.get().setPacketHandled(true);}).add();
        channel.messageBuilder(Selection.class,1,NetworkDirection.PLAY_TO_SERVER).encoder(Selection::encode).decoder(Selection::decode)
                .consumerMainThread((packet,ctx)->{var player=ctx.get().getSender();if(player!=null)ZombiesNpcRuntime.selectDialogue(player,packet.token(),packet.option());ctx.get().setPacketHandled(true);}).add();
        channel.messageBuilder(EditorRequest.class,2,NetworkDirection.PLAY_TO_SERVER).encoder(EditorRequest::encode).decoder(EditorRequest::decode)
                .consumerMainThread((packet,ctx)->{var player=ctx.get().getSender();if(player!=null)reply(player,packet);ctx.get().setPacketHandled(true);}).add();
        channel.messageBuilder(EditorFragment.class,4,NetworkDirection.PLAY_TO_SERVER).encoder(EditorFragment::encode).decoder(EditorFragment::decode)
                .consumerMainThread((packet,ctx)->{var player=ctx.get().getSender();if(player!=null)try{var request=ZombiesNpcEditorUpload.accept(player,packet);if(request!=null)reply(player,request);}catch(RuntimeException error){player.sendSystemMessage(Component.literal("部署数据上传失败："+error.getMessage()));}ctx.get().setPacketHandled(true);}).add();

        channel.messageBuilder(EditorReply.class,3,NetworkDirection.PLAY_TO_CLIENT).encoder(EditorReply::encode).decoder(EditorReply::decode)
                .consumerMainThread((packet,ctx)->{ModeClientActionHandlers.dispatch("codpattern:zombies_scenario_editor",packet);ctx.get().setPacketHandled(true);}).add();
    }
    public static void send(ServerPlayer player,View view){if(channel!=null && player.connection!=null)channel.send(PacketDistributor.PLAYER.with(()->player),view);}
    public static void select(UUID token,String option){channel.sendToServer(new Selection(token,option));}
}
