package com.cdp.codpattern.app.zombies.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/** World-local undo data survives normal saves and is recovered before rooms start. */
public final class ZombiesNpcBlockJournal extends SavedData {
    private final Map<String, Map<BlockPos, CompoundTag>> rooms = new LinkedHashMap<>();

    public static ZombiesNpcBlockJournal get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ZombiesNpcBlockJournal::load, ZombiesNpcBlockJournal::new, "codpattern_zombies_npc_blocks");
    }

    private static ZombiesNpcBlockJournal load(CompoundTag tag) {
        var journal = new ZombiesNpcBlockJournal();
        ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            journal.rooms.computeIfAbsent(entry.getString("room"), ignored -> new LinkedHashMap<>())
                    .put(BlockPos.of(entry.getLong("pos")), entry.getCompound("before"));
        }
        return journal;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        rooms.forEach((room, cells) -> cells.forEach((pos, before) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("room", room);
            entry.putLong("pos", pos.asLong());
            entry.put("before", before.copy());
            entries.add(entry);
        }));
        tag.put("entries", entries);
        return tag;
    }

    public void capture(ServerLevel level, String room, BlockPos pos) {captureAll(level,room,java.util.List.of(pos));}
    public void captureAll(ServerLevel level,String room,java.util.Collection<BlockPos> positions){
        for(var pos:positions)captureCell(level,room,pos);
        level.getDataStorage().save();
    }
    private void captureCell(ServerLevel level, String room, BlockPos pos) {
        Map<BlockPos, CompoundTag> cells = rooms.computeIfAbsent(room, ignored -> new LinkedHashMap<>());
        if (cells.containsKey(pos)) return;
        CompoundTag before = new CompoundTag();
        before.put("state", NbtUtils.writeBlockState(level.getBlockState(pos)));
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity != null) before.put("entity", entity.saveWithFullMetadata());
        cells.put(pos.immutable(), before);
        setDirty();
        // Persist undo information before changing a chunk, including block-entity contents.
    }

    public void restore(ServerLevel level, String room) {
        Map<BlockPos, CompoundTag> cells = rooms.get(room);
        if (cells == null) return;
        // Two passes avoid block entities dropping inventory during replacement.
        for (var entry : cells.entrySet()) {
            BlockPos pos = entry.getKey();
            level.removeBlockEntity(pos);
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getValue().getCompound("state"));
            if (!level.setBlock(pos, state, 18) && !level.getBlockState(pos).equals(state)) {
                throw new IllegalStateException("Cannot restore NPC block at " + pos);
            }
        }
        for (var entry : cells.entrySet()) {
            BlockEntity entity = level.getBlockEntity(entry.getKey());
            if (entity != null && entry.getValue().contains("entity")) {
                entity.load(entry.getValue().getCompound("entity"));
                entity.setChanged();
            }
            level.sendBlockUpdated(entry.getKey(), level.getBlockState(entry.getKey()), level.getBlockState(entry.getKey()), 3);
            com.cdp.codpattern.app.zombies.service.navigation.NavigationWorldChanges.blockChanged(level, entry.getKey(), "npc-reset");
        }
        // Save the restored chunks before discarding recovery evidence.
        level.save(null, true, false);
        rooms.remove(room);
        setDirty();
    }

    public void restoreAll(ServerLevel level) {
        for (String room : java.util.List.copyOf(rooms.keySet())) restore(level, room);
    }
}
