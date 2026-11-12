package com.cdp.codpattern.app.zombies.npc;
import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.UUID;
public final class ZombiesCoopNetwork {
    private static SimpleChannel channel;
    public static final UUID ZERO=new UUID(0,0);
    public record Action(String operation,UUID session,UUID token,int amount,int entity){
        void encode(FriendlyByteBuf b){b.writeUtf(operation,16);b.writeUUID(session);b.writeUUID(token);b.writeVarInt(amount);b.writeVarInt(entity);}
        static Action decode(FriendlyByteBuf b){return new Action(b.readUtf(16),b.readUUID(),b.readUUID(),b.readVarInt(),b.readVarInt());}
    }
    public record State(CompoundTag data){void encode(FriendlyByteBuf b){b.writeNbt(data);}static State decode(FriendlyByteBuf b){var d=b.readNbt();return new State(d==null?new CompoundTag():d);}}
    public static void install(){if(channel!=null)return;channel=NetworkRegistry.newSimpleChannel(new net.minecraft.resources.ResourceLocation(ZombiesAddonConstants.MOD_ID,"coop"),()->"1","1"::equals,"1"::equals);
        channel.messageBuilder(Action.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Action::encode).decoder(Action::decode).consumerMainThread((p,c)->{var sender=c.get().getSender();if(sender!=null)handle(sender,p);c.get().setPacketHandled(true);}).add();
        channel.messageBuilder(State.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(State::encode).decoder(State::decode).consumerMainThread((p,c)->{com.cdp.codpattern.client.runtime.ModeClientActionHandlers.dispatch("codpattern:zombies_coop",p);c.get().setPacketHandled(true);}).add();
    }
    public static void request(Action p){if(channel!=null)channel.sendToServer(p);}
    public static void send(ServerPlayer p,State state){if(channel!=null&&p.connection!=null)channel.send(PacketDistributor.PLAYER.with(()->p),state);}
    public static void openNpc(ServerPlayer p,ZombiesNpcEntity npc){var map=ZombiesCoopRuntime.room(p);if(map==null)return;try{var token=map.coopRuntime().offer(p,npc.getId());send(p,new State(map.coopRuntime().snapshot(p,"npc","",npc.getId(),token)));}catch(IllegalArgumentException e){p.sendSystemMessage(net.minecraft.network.chat.Component.literal(e.getMessage()));}}
    static void handle(ServerPlayer player,Action request){var map=ZombiesCoopRuntime.room(player);if(map==null){send(player,new State(new CompoundTag()));return;}var runtime=map.coopRuntime();String page="",notice="";UUID token=null;int focus=request.entity();
        try{
            if(!request.operation().equals("open")&&!runtime.session.equals(request.session()))throw new IllegalArgumentException("对局已变化，请重新打开页面");
            switch(request.operation()){
                case "open"->page="bank";
                case "deposit","withdraw"->{runtime.transfer(player,request.amount(),request.operation().equals("deposit"));notice=(request.operation().equals("deposit")?"已存入 ":"已取出 ")+request.amount()+" 点";}
                case "swap"->{runtime.exchange(player,request.token());notice="NPC 枪械已更换";page="npc";token=runtime.offer(player,focus);}
                case "resume"->{runtime.resume(player,focus);notice="NPC 已恢复原任务";}
                case "call"->{long tick=player.level().getGameTime();var data=player.getPersistentData();if(data.contains("coop.call.tick")&&tick-data.getLong("coop.call.tick")<10)return;data.putLong("coop.call.tick",tick);runtime.call(player,focus);notice="已呼叫附近 NPC；靠近后右键可换枪";}
                default->throw new IllegalArgumentException("未知操作");
            }
        }catch(RuntimeException failure){notice=failure.getMessage()==null?"操作未完成":failure.getMessage();}
        send(player,new State(runtime.snapshot(player,page,notice,focus,token)));
    }
}
