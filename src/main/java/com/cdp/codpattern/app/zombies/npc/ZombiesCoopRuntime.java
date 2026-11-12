package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.cdp.codpattern.app.zombies.service.*;
import com.cdp.codpattern.app.zombies.model.*;
import com.cdp.codpattern.app.match.model.RoomId;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Server authoritative bank, weapon handoffs and short-lived support interactions. */
public final class ZombiesCoopRuntime {
    public static final String DROP="codpattern.coop.drop",PASS="codpattern.coop.toss";
    private final ZombiesMap map;
    public final ZombiesSharedBank bank=new ZombiesSharedBank();
    public UUID session=UUID.randomUUID();
    private final Set<UUID> drops=new HashSet<>();
    private final Map<UUID,Offer> offers=new HashMap<>();
    private record Offer(UUID token,UUID npc,UUID weapon,ItemStack hand,long expires){}
    public ZombiesCoopRuntime(ZombiesMap map){this.map=map;}
    public static ZombiesMap room(ServerPlayer player){return com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry.listMaps(com.cdp.codpattern.app.match.BuiltInGameModes.ZOMBIES).stream().filter(m->m instanceof ZombiesMap).map(m->(ZombiesMap)m).filter(m->m.getServerLevel()==player.serverLevel()&&m.currentPhase().isRoundRunning()&&m.isAliveSurvivor(player.getUUID())).findFirst().orElse(null);}
    public static RoomId roomIdOf(ZombiesNpcEntity npc){return com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry.listMaps(com.cdp.codpattern.app.match.BuiltInGameModes.ZOMBIES).stream().filter(m->m instanceof ZombiesMap).map(m->(ZombiesMap)m).filter(m->m.npcRuntime().supportNpcs().contains(npc)).map(m->m.coopRuntime().roomId()).findFirst().orElse(null);}
    public boolean allowed(ServerPlayer player){return player!=null&&player.serverLevel()==map.getServerLevel()&&map.currentPhase().isRoundRunning()&&map.isAliveSurvivor(player.getUUID());}
    public RoomId roomId(){return new RoomId(map.getGameType(),map.getMapName());}
    public static ZombiesWeaponItemStackService.ZombiesWeaponTagData tags(ItemStack stack){return new ZombiesWeaponItemStackService().readWeaponTags(stack).value().orElse(null);}
    public boolean roomGun(ItemStack stack){var tag=tags(stack);return stack.getCount()==1&&com.tacz.guns.api.item.IGun.getIGunOrNull(stack)!=null&&tag!=null&&tag.roomId().equals(roomId().encode());}
    public void transfer(ServerPlayer player,int amount,boolean deposit){if(!allowed(player))throw new IllegalArgumentException("当前不能使用银行");bank.transfer(map.coopPlayerState(player.getUUID()),amount,deposit);}
    public void detach(ServerPlayer player,ItemStack stack){var tag=tags(stack);if(tag!=null)setWeapon(map.coopPlayerState(player.getUUID()),tag.slot(),null);}
    private static void setWeapon(ZombiesPlayerRuntimeState state,ZombiesEquipmentSlot slot,ZombiesWeaponInstanceState gun){switch(slot){case STARTER->state.setStarterWeapon(gun);case PRIMARY->state.setPrimaryWeapon(gun);case MYSTERY_BOX->state.setMysteryBoxWeapon(gun);}}
    private ZombiesEquipmentSlot freeSlot(ServerPlayer player,ItemStack incoming){var first=tags(incoming).slot();var order=new ArrayList<ZombiesEquipmentSlot>();order.add(first);for(var s:ZombiesEquipmentSlot.values())if(s!=first)order.add(s);
        for(var slot:order){boolean occupied=false;for(int i=0;i<36;i++){var t=tags(player.getInventory().getItem(i));if(t!=null&&t.roomId().equals(roomId().encode())&&t.slot()==slot){occupied=true;break;}}if(!occupied)return slot;}return null;}
    public void receive(ServerPlayer player,ItemStack item,int inventorySlot){var slot=freeSlot(player,item);if(slot==null||inventorySlot<0)throw new IllegalArgumentException("武器栏位或背包已满");var tag=tags(item);var copy=item.copy();copy.getOrCreateTag().putString(ZombiesWeaponItemStackService.TAG_SLOT,slot.key());player.getInventory().setItem(inventorySlot,copy);setWeapon(map.coopPlayerState(player.getUUID()),slot,tag.toWeaponState());syncInventory(player);}
    public void markToss(ServerPlayer player,ItemEntity item){if(!allowed(player)||!roomGun(item.getItem()))return;var tag=item.getPersistentData();tag.putUUID(DROP,session);tag.putString(DROP+".room",map.getMapName());drops.add(item.getUUID());player.getPersistentData().putUUID(PASS,session);player.getPersistentData().putLong(PASS+".tick",player.level().getGameTime());detach(player,item.getItem());}
    public static boolean tossAllowed(net.minecraft.world.entity.Entity entity){if(!(entity instanceof ServerPlayer p))return false;var map=room(p);return map!=null&&p.getPersistentData().hasUUID(PASS)&&p.getPersistentData().getUUID(PASS).equals(map.coopRuntime().session)&&p.getPersistentData().getLong(PASS+".tick")==p.level().getGameTime();}
    public static boolean dropAllowed(ItemEntity item){if(!item.getPersistentData().hasUUID(DROP))return false;return com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry.listMaps(com.cdp.codpattern.app.match.BuiltInGameModes.ZOMBIES).stream().filter(m->m instanceof ZombiesMap).map(m->(ZombiesMap)m).anyMatch(m->m.getServerLevel()==item.level()&&m.currentPhase().isRoundRunning()&&m.coopRuntime().session.equals(item.getPersistentData().getUUID(DROP))&&m.coopRuntime().drops.contains(item.getUUID())&&m.coopRuntime().roomGun(item.getItem()));}
    public boolean pickup(ServerPlayer player,ItemEntity entity){if(!allowed(player)||!dropAllowed(entity)||!session.equals(entity.getPersistentData().getUUID(DROP)))return false;int slot=player.getInventory().getFreeSlot();if(slot<0||freeSlot(player,entity.getItem())==null){long now=player.level().getGameTime();if(!player.getPersistentData().contains("coop.pickup.warn")||now-player.getPersistentData().getLong("coop.pickup.warn")>=40){player.getPersistentData().putLong("coop.pickup.warn",now);player.sendSystemMessage(Component.literal(slot<0?"背包已满，无法拾取枪械":"武器栏位已满，请先丢出一把枪"));}return false;}receive(player,entity.getItem(),slot);drops.remove(entity.getUUID());player.take(entity,1);entity.discard();return true;}
    private ZombiesNpcEntity npc(ServerPlayer player,int id){var n=map.getServerLevel().getEntity(id);if(!allowed(player)||!(n instanceof ZombiesNpcEntity npc)||!map.npcRuntime().supportNpcs().contains(npc)||npc.distanceToSqr(player)>36||npc.posture()!=0)throw new IllegalArgumentException("请靠近本房间存活的站立作战 NPC");return npc;}
    public UUID offer(ServerPlayer player,int entity){var npc=npc(player,entity);var token=UUID.randomUUID();offers.put(player.getUUID(),new Offer(token,npc.getUUID(),npc.combat.weaponRevision,player.getMainHandItem().copy(),player.level().getGameTime()+200));return token;}
    public void exchange(ServerPlayer player,UUID token){var offer=offers.remove(player.getUUID());if(offer==null||!offer.token().equals(token)||offer.expires()<player.level().getGameTime())throw new IllegalArgumentException("换枪页面已失效，请重新交互");var entity=map.getServerLevel().getEntity(offer.npc());if(!(entity instanceof ZombiesNpcEntity candidate))throw new IllegalArgumentException("NPC 已离开");var npc=npc(player,candidate.getId());var hand=player.getMainHandItem();if(!npc.combat.weaponRevision.equals(offer.weapon())||!ItemStack.matches(hand,offer.hand())||!roomGun(hand))throw new IllegalArgumentException("枪械已变化，请重新交互；只能交付本局枪械");ZombiesNpcCombat.gun(hand.save(new CompoundTag()).toString());
        var old=npc.combat.loan.copy();var original=hand.copy();var selected=player.getInventory().selected;
        var state=map.coopPlayerState(player.getUUID());var beforeStarter=state.starterWeapon().orElse(null);var beforePrimary=state.primaryWeapon().orElse(null);var beforeBox=state.mysteryBoxWeapon().orElse(null);
        detach(player,hand);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        try{if(!old.isEmpty())receive(player,old,selected);npc.combat.equipLoan(original);syncInventory(player);}catch(RuntimeException e){player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,original);state.setStarterWeapon(beforeStarter);state.setPrimaryWeapon(beforePrimary);state.setMysteryBoxWeapon(beforeBox);syncInventory(player);throw e;}
    }
    public void call(ServerPlayer player,int preferred){if(!allowed(player))throw new IllegalArgumentException("当前不能呼叫 NPC");var npc=map.npcRuntime().supportNpcs().stream().filter(n->n.posture()==0&&n.distanceToSqr(player)<=4096).min(Comparator.comparingDouble(n->n.getId()==preferred?-1:n.distanceToSqr(player))).orElseThrow(()->new IllegalArgumentException("附近没有可呼叫的作战 NPC"));npc.combat.call(player);}
    public void resume(ServerPlayer player,int entity){npc(player,entity).combat.resume();}
    public CompoundTag snapshot(ServerPlayer player,String page,String notice,int focus,UUID token){var data=new CompoundTag();data.putBoolean("active",allowed(player));data.putUUID("session",session);data.putString("room",map.getMapName());data.putLong("bank",bank.balance());data.putDouble("points",map.coopPlayerState(player.getUUID()).points());data.putString("page",page);data.putString("notice",notice);data.putInt("focus",focus);if(token!=null)data.putUUID("token",token);var list=new ListTag();for(var npc:map.npcRuntime().supportNpcs().stream().sorted(Comparator.comparingDouble(player::distanceToSqr)).limit(128).toList()){var n=new CompoundTag();n.putInt("id",npc.getId());n.putString("name",npc.getName().getString());n.putString("gun",Component.Serializer.toJson(npc.getMainHandItem().getHoverName()));var tags=tags(npc.getMainHandItem());n.putString("rarity",tags==null?"":tags.rarityId());n.putInt("level",tags==null?0:tags.weaponLevel());n.putInt("upgrade",tags==null?0:tags.upgradeLevel());n.putFloat("hp",npc.getHealth());n.putBoolean("called",npc.combat.calledFor(player));list.add(n);}data.put("npcs",list);return data;}
    public static void syncInventory(ServerPlayer p){p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();}
    public void reset(){for(var id:drops){var e=map.getServerLevel().getEntity(id);if(e!=null)e.discard();}drops.clear();offers.clear();bank.reset();session=UUID.randomUUID();}
}
