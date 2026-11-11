package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.app.zombies.item.ZombiesRequiredItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Plans every transfer against copies before committing any inventory slot. */
final class ZombiesNpcInventory {
    private ZombiesNpcInventory() { }
    static boolean contains(ServerPlayer player, String specification, int count) {
        ItemStack required = parse(specification);
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameTags(required, candidate)) found += candidate.getCount();
        }
        return found >= count;
    }
    static List<ItemStack> plan(ServerPlayer player, List<ZombiesNpcConfig.Action> actions) {
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) slots.add(player.getInventory().getItem(i).copy());
        // Requirements always come from the original inventory, never from this rule's rewards.
        for (var action : actions) if (action.kind() == ZombiesNpcConfig.Kind.TAKE_ITEM) {
            ItemStack requested = parse(action.item());
            int remaining = action.count();
            for (ItemStack stack : slots) {
                if (!ItemStack.isSameItemSameTags(stack, requested)) continue;
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
            if (remaining > 0) throw new IllegalArgumentException("缺少任务物品 " + requested.getHoverName().getString() + " × " + action.count());
        }
        for (var action : actions) if (action.kind() == ZombiesNpcConfig.Kind.GIVE_ITEM) {
            ItemStack reward = parse(action.item());
            int remaining = action.count();
            for (int i = 0; i < 36; i++) {
                ItemStack slot = slots.get(i);
                if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, reward)) continue;
                int inserted = Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
                slot.grow(inserted);
                remaining -= inserted;
            }
            for (int i = 0; i < 36 && remaining > 0; i++) if (slots.get(i).isEmpty()) {
                ItemStack stack = reward.copy();
                stack.setCount(Math.min(remaining, stack.getMaxStackSize()));
                remaining -= stack.getCount();
                slots.set(i, stack);
            }
            if (remaining > 0) throw new IllegalArgumentException("背包空间不足，无法领取任务奖励");
        }
        return slots;
    }
    static ItemStack parse(String specification) {
        return ZombiesRequiredItem.parse(specification).stack().orElseThrow(() -> new IllegalArgumentException("Invalid NPC item " + specification)).copy();
    }
    static void commit(ServerPlayer player, List<ItemStack> slots) {
        for (int i = 0; i < slots.size(); i++) player.getInventory().setItem(i, slots.get(i));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
