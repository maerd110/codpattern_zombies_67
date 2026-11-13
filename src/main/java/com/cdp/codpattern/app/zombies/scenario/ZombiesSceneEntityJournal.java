package com.cdp.codpattern.app.zombies.scenario;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Durable originals plus a live-session allowlist for temporary scene entity UUIDs. */
public final class ZombiesSceneEntityJournal extends SavedData {
    public static final String MARKER="codpattern.scene.room";
    private final Map<String,CompoundTag> rooms=new LinkedHashMap<>();
    private final Set<UUID> live=new HashSet<>();
    public static ZombiesSceneEntityJournal get(ServerLevel level){return level.getDataStorage().computeIfAbsent(ZombiesSceneEntityJournal::load,ZombiesSceneEntityJournal::new,"codpattern_zombies_scene_entities");}
    private static ZombiesSceneEntityJournal load(CompoundTag tag){var journal=new ZombiesSceneEntityJournal();var entries=tag.getList("rooms",Tag.TAG_COMPOUND);for(int i=0;i<entries.size();i++){var entry=entries.getCompound(i);journal.rooms.put(entry.getString("room"),entry.copy());}return journal;}
    @Override public CompoundTag save(CompoundTag tag){var values=new ListTag();rooms.values().forEach(value->values.add(value.copy()));tag.put("rooms",values);return tag;}
    public CompoundTag checkpoint(String room){return rooms.containsKey(room)?rooms.get(room).copy():null;}
    private CompoundTag room(String room){return rooms.computeIfAbsent(room,key->{var value=new CompoundTag();value.putString("room",key);value.put("before",new ListTag());value.put("spawned",new ListTag());return value;});}
    public void capture(ServerLevel level,String room,List<Entity> before,List<Entity> spawned){
        var record=room(room);var originals=record.getList("before",Tag.TAG_COMPOUND);var ids=new HashSet<UUID>();for(var value:originals)ids.add(((CompoundTag)value).getUUID("UUID"));
        for(var entity:before)if(entity.getPersistentData().getString(MARKER).isBlank()&&ids.add(entity.getUUID())){var nbt=new CompoundTag();if(!entity.save(nbt))throw new IllegalArgumentException("实体无法保存："+entity.getType());originals.add(nbt);}
        var created=record.getList("spawned",Tag.TAG_COMPOUND);for(var root:spawned)root.getSelfAndPassengers().forEach(entity->{var value=new CompoundTag();value.putUUID("id",entity.getUUID());created.add(value);live.add(entity.getUUID());entity.getPersistentData().putString(MARKER,room);});
        setDirty();level.getDataStorage().save();
    }
    public boolean isLive(UUID entity){return live.contains(entity);}
    public void rollbackRecord(ServerLevel level,String room,CompoundTag checkpoint){
        var current=rooms.remove(room);if(current!=null)for(var value:current.getList("spawned",Tag.TAG_COMPOUND))live.remove(((CompoundTag)value).getUUID("id"));
        if(checkpoint!=null){rooms.put(room,checkpoint);for(var value:checkpoint.getList("spawned",Tag.TAG_COMPOUND))live.add(((CompoundTag)value).getUUID("id"));}setDirty();level.getDataStorage().save();
    }
    static void discard(Entity root){root.getSelfAndPassengers().toList().forEach(Entity::discard);}
    static Entity loadEntity(ServerLevel level,CompoundTag nbt){var entity=EntityType.loadEntityRecursive(nbt.copy(),level,e->e);if(entity==null)throw new IllegalArgumentException("实体类型未加载或实体数据无效："+nbt.getString("id"));return entity;}
    static boolean add(ServerLevel level,Entity root){
        // Vanilla's with-passengers helper ignores Forge join cancellation; check each result.
        for(var entity:root.getSelfAndPassengers().toList())if(!level.addFreshEntity(entity)){discard(root);return false;}return true;
    }
    static void restoreEntity(ServerLevel level,CompoundTag nbt){
        var entity=loadEntity(level,nbt);level.getChunk(entity.blockPosition());var existing=level.getEntity(entity.getUUID());if(existing!=null)discard(existing);
        if(!add(level,entity))throw new IllegalStateException("无法还原场景实体："+entity.getUUID());
    }
    public void restore(ServerLevel level,String room){
        var record=rooms.get(room);if(record==null)return;
        for(var value:record.getList("spawned",Tag.TAG_COMPOUND)){UUID id=((CompoundTag)value).getUUID("id");live.remove(id);for(var world:level.getServer().getAllLevels()){var entity=world.getEntity(id);if(entity!=null)discard(entity);}}
        for(var value:record.getList("before",Tag.TAG_COMPOUND))restoreEntity(level,(CompoundTag)value);
        level.save(null,true,false);rooms.remove(room);setDirty();level.getDataStorage().save();
    }
    public void restoreAll(ServerLevel level){for(var room:List.copyOf(rooms.keySet()))restore(level,room);}
}
