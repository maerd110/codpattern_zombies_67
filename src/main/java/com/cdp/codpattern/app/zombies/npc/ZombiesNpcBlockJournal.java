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
        CompoundTag before = snapshotCell(level,pos);
        cells.put(pos.immutable(), before);
        setDirty();
        // Persist undo information before changing a chunk, including block-entity contents.
    }

    public Map<BlockPos,CompoundTag> checkpoint(String room){var copy=new LinkedHashMap<BlockPos,CompoundTag>();var values=rooms.get(room);if(values!=null)values.forEach((pos,value)->copy.put(pos,value.copy()));return copy;}
    public void rollbackRecord(ServerLevel level,String room,Map<BlockPos,CompoundTag> checkpoint){if(checkpoint.isEmpty())rooms.remove(room);else rooms.put(room,checkpoint);setDirty();level.getDataStorage().save();}
    public static CompoundTag snapshotCell(ServerLevel level,BlockPos pos) {
        CompoundTag before = new CompoundTag();
        before.put("state", NbtUtils.writeBlockState(level.getBlockState(pos)));
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity != null) before.put("entity", entity.saveWithFullMetadata());
        return before;
    }
    public static void restoreCells(ServerLevel level,Map<BlockPos,CompoundTag> cells) {
        var states=new java.util.HashMap<CompoundTag,BlockState>();
        // Remove old block entities first so replacing containers cannot drop their contents.
        for(var entry:cells.entrySet()){
            var pos=entry.getKey();level.getChunk(pos);level.removeBlockEntity(pos);
            var state=states.computeIfAbsent(entry.getValue().getCompound("state"),value->NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),value));
            if(!level.setBlock(pos,state,18)&&!level.getBlockState(pos).equals(state))throw new IllegalStateException("Cannot restore scene block at "+pos);
        }
        for(var entry:cells.entrySet()){
            var pos=entry.getKey();var state=level.getBlockState(pos);
            if(entry.getValue().contains("entity",Tag.TAG_COMPOUND)){
                var entity=BlockEntity.loadStatic(pos,state,entry.getValue().getCompound("entity").copy());
                if(entity==null||!entity.getType().isValid(state))throw new IllegalStateException("Cannot restore scene block data at "+pos);
                level.setBlockEntity(entity);entity.setChanged();
            }else if(state.hasBlockEntity()&&level.getBlockEntity(pos)==null&&state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock factory){var entity=factory.newBlockEntity(pos,state);if(entity!=null)level.setBlockEntity(entity);}
            level.sendBlockUpdated(pos,state,state,3);
            com.cdp.codpattern.app.zombies.service.navigation.NavigationWorldChanges.blockChanged(level,pos,"scene-restore");
        }
    }

    public void restore(ServerLevel level, String room) {
        Map<BlockPos, CompoundTag> cells = rooms.get(room);
        if (cells == null) return;
        restoreCells(level,cells);
        // Save the restored chunks before discarding recovery evidence.
        level.save(null, true, false);
        rooms.remove(room);
        setDirty();
    }

    public void restoreAll(ServerLevel level) {
        for (String room : java.util.List.copyOf(rooms.keySet())) restore(level, room);
    }
}
