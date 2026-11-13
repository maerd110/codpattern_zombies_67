package com.cdp.codpattern.app.zombies.scenario;

import com.cdp.codpattern.app.zombies.npc.*;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.*;

/** Registry-based structures retain arbitrary mod block state, block entity and entity save data. */
public final class ZombiesSceneSnapshots {
    private ZombiesSceneSnapshots(){}
    public static Scene capture(ServerLevel level,BlockPos min,int sx,int sy,int sz,boolean includeEntities){
        if(ZombiesScenarioConfig.volume(sx,sy,sz)>SnapshotLimits.MEMORY_PROTECTION_BLOCKS)throw new IllegalArgumentException("场景超出内存保护范围，请拆分区域");var palette=new ArrayList<String>();var stateNames=new HashMap<BlockState,String>();var indexes=new HashMap<String,Integer>();var blocks=new ArrayList<Integer>();var data=new ArrayList<SceneBlockEntity>();
        for(int y=0;y<sy;y++)for(int z=0;z<sz;z++)for(int x=0;x<sx;x++){
            var pos=min.offset(x,y,z);var state=level.getBlockState(pos);String spec=stateNames.computeIfAbsent(state,BlockStateParser::serialize);Integer index=indexes.get(spec);if(index==null){index=palette.size();indexes.put(spec,index);palette.add(spec);}blocks.add(index);
            var entity=level.getBlockEntity(pos);if(entity!=null){var tag=entity.saveWithFullMetadata();tag.putInt("x",x);tag.putInt("y",y);tag.putInt("z",z);data.add(new SceneBlockEntity(x,y,z,tag.toString()));}
        }
        var entities=new ArrayList<SceneEntity>();int entityCount=0;if(includeEntities){var bounds=new AABB(min,min.offset(sx,sy,sz));for(var entity:entities(level,bounds,"",true)){
            entityCount+=entity.getSelfAndPassengers().count();if(entityCount>128)throw new IllegalArgumentException("场景最多 128 个实体（含骑乘实体）");var tag=new CompoundTag();if(!entity.save(tag))throw new IllegalArgumentException("实体无法保存："+entity.getType());translate(tag,new Vec3(-min.getX(),-min.getY(),-min.getZ()));entities.add(new SceneEntity(tag.toString()));if(entities.size()>128)throw new IllegalArgumentException("场景最多 128 个实体，请缩小区域");
        }}
        return new Scene(sx,sy,sz,palette,blocks,data,entities,includeEntities);
    }
    private static boolean allowed(Entity entity){
        return !(entity instanceof Player||entity instanceof ZombiesNpcEntity||entity instanceof ItemEntity||entity instanceof ExperienceOrb||entity instanceof Projectile||entity instanceof net.minecraft.world.entity.LightningBolt)
            &&!entity.getPersistentData().contains(com.cdp.codpattern.app.zombies.service.ZombiesMobSpawnService.WAVE_MOB_ID_TAG)&&com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry.instance().entryOf(entity).isEmpty();
    }
    private static List<Entity> entities(ServerLevel level,AABB bounds,String room,boolean source){
        var result=new ArrayList<Entity>();for(var entity:level.getAllEntities()){
            if(entity.isRemoved()||entity.isPassenger()||!bounds.contains(entity.position()))continue;
            var tree=entity.getSelfAndPassengers().toList();if(tree.stream().anyMatch(e->!allowed(e)))continue;
            String owner=entity.getPersistentData().getString(ZombiesSceneEntityJournal.MARKER);if(!owner.isBlank()&&(source||!owner.equals(room)))continue;
            if(tree.stream().anyMatch(e->!bounds.contains(e.position())))throw new IllegalArgumentException("骑乘实体跨出场景范围，请扩大框选区域");
            result.add(entity);if(result.size()>128)throw new IllegalArgumentException("目标区域实体过多，请缩小区域");
        }return result;
    }
    private static CompoundTag nbt(String text){try{return TagParser.parseTag(text);}catch(Exception error){throw new IllegalArgumentException("场景附加数据格式错误",error);}}
    private static BlockState state(ServerLevel level,String text){try{return BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK),text,false).blockState();}catch(Exception error){throw new IllegalArgumentException("场景方块未加载或状态无效："+text,error);}}
    private record Plan(Map<BlockPos,CompoundTag> cells,List<Entity> removed,List<Entity> spawned){}
    private static Plan plan(ZombiesMap map,List<Region> regions){
        if(regions.stream().mapToLong(Region::blockCount).sum()>SnapshotLimits.MEMORY_PROTECTION_BLOCKS)throw new IllegalArgumentException("事件超出内存保护范围，请拆分区域");
        var level=map.getServerLevel();var states=new HashMap<String,CompoundTag>();var cells=new LinkedHashMap<BlockPos,CompoundTag>();var removed=new LinkedHashMap<UUID,Entity>();var spawned=new ArrayList<Entity>();
        for(var region:regions){
            var origin=new BlockPos(Math.min(region.from().getX(),region.to().getX()),Math.min(region.from().getY(),region.to().getY()),Math.min(region.from().getZ(),region.to().getZ()));
            map.npcRuntime().validateSceneRegion(region.from(),region.to());
            int maxX=Math.max(region.from().getX(),region.to().getX())>>4,maxZ=Math.max(region.from().getZ(),region.to().getZ())>>4;
            for(int cx=origin.getX()>>4;cx<=maxX;cx++)for(int cz=origin.getZ()>>4;cz<=maxZ;cz++)level.getChunk(cx,cz);
            region.forEachCell((pos,spec)->{var cell=new CompoundTag();cell.put("state",states.computeIfAbsent(spec,key->NbtUtils.writeBlockState(state(level,key))));if(cells.putIfAbsent(pos,cell)!=null)throw new IllegalArgumentException("同一地图事件的区域不能互相重叠："+pos.toShortString());});
            if(region.mode()!=RegionMode.SCENE)continue;var scene=region.scene();
            for(var value:scene.blockEntities()){
                var pos=origin.offset(value.x(),value.y(),value.z());var cell=cells.get(pos);var block=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),cell.getCompound("state"));var tag=nbt(value.nbt());tag.putInt("x",pos.getX());tag.putInt("y",pos.getY());tag.putInt("z",pos.getZ());
                var entity=BlockEntity.loadStatic(pos,block,tag);if(entity==null||!entity.getType().isValid(block))throw new IllegalArgumentException("场景方块附加数据与方块类型不匹配："+pos.toShortString());cell.put("entity",tag);
            }
            if(!scene.includesEntities())continue;var bounds=new AABB(origin,scene.endAt(origin).offset(1,1,1));
            for(var entity:entities(level,bounds,map.getMapName(),false))removed.put(entity.getUUID(),entity);
            for(var value:scene.entities()){
                var tag=nbt(value.nbt());int expected=validateEntityTree(tag,0);translate(tag,Vec3.atLowerCornerOf(origin));freshUuids(tag);var entity=ZombiesSceneEntityJournal.loadEntity(level,tag);
                var tree=entity.getSelfAndPassengers().toList();if(tree.size()!=expected||tree.size()>128||tree.stream().anyMatch(e->!allowed(e)||!bounds.contains(e.position())))throw new IllegalArgumentException("场景实体类型不支持或位置超出场景范围");
                for(var child:tree){map.npcRuntime().validateSceneCell(child.blockPosition());if(child instanceof Mob mob)mob.setPersistenceRequired();}
                spawned.add(entity);
            }
        }
        if(spawned.stream().mapToLong(e->e.getSelfAndPassengers().count()).sum()>256)throw new IllegalArgumentException("单个地图事件最多放置 256 个实体");
        return new Plan(cells,List.copyOf(removed.values()),spawned);
    }
    public static void validate(ZombiesMap map,List<Region> regions){plan(map,regions);}
    public static void apply(ZombiesMap map,List<Region> regions){
        var plan=plan(map,regions);if(plan.cells().isEmpty())return;var level=map.getServerLevel();String room=map.getMapName();
        var before=new LinkedHashMap<BlockPos,CompoundTag>();for(var pos:plan.cells().keySet())before.put(pos,ZombiesNpcBlockJournal.snapshotCell(level,pos));
        var removed=new ArrayList<CompoundTag>();for(var entity:plan.removed()){var tag=new CompoundTag();if(!entity.save(tag))throw new IllegalArgumentException("目标实体无法保存");removed.add(tag);}
        var blocks=ZombiesNpcBlockJournal.get(level);var entities=ZombiesSceneEntityJournal.get(level);var checkpoint=entities.checkpoint(room);var blockCheckpoint=blocks.checkpoint(room);
        try{
            blocks.captureAll(level,room,plan.cells().keySet());entities.capture(level,room,plan.removed(),plan.spawned());
            plan.removed().forEach(ZombiesSceneEntityJournal::discard);
            ZombiesNpcBlockJournal.restoreCells(level,plan.cells());
            for(var entity:plan.spawned())if(!ZombiesSceneEntityJournal.add(level,entity))throw new IllegalStateException("场景实体生成被其他模组拒绝："+entity.getType());
        }catch(RuntimeException failure){
            try{plan.spawned().forEach(ZombiesSceneEntityJournal::discard);ZombiesNpcBlockJournal.restoreCells(level,before);for(var entity:removed)ZombiesSceneEntityJournal.restoreEntity(level,entity);entities.rollbackRecord(level,room,checkpoint);blocks.rollbackRecord(level,room,blockCheckpoint);}catch(RuntimeException rollback){failure.addSuppressed(rollback);}throw failure;
        }
    }
    /** Standard entity positions, hanging anchors and leash anchors follow the scene translation. */
    private static void translate(CompoundTag tag,Vec3 offset){
        var pos=tag.getList("Pos",Tag.TAG_DOUBLE);if(pos.size()!=3)throw new IllegalArgumentException("场景实体缺少位置数据");var moved=new ListTag();for(int i=0;i<3;i++){double value=pos.getDouble(i)+(i==0?offset.x:i==1?offset.y:offset.z);if(!Double.isFinite(value))throw new IllegalArgumentException("场景实体位置无效");moved.add(DoubleTag.valueOf(value));}tag.put("Pos",moved);
        if(tag.contains("TileX")){tag.putInt("TileX",tag.getInt("TileX")+(int)offset.x);tag.putInt("TileY",tag.getInt("TileY")+(int)offset.y);tag.putInt("TileZ",tag.getInt("TileZ")+(int)offset.z);}
        var leash=tag.getCompound("Leash");if(leash.contains("X")){leash.putInt("X",leash.getInt("X")+(int)offset.x);leash.putInt("Y",leash.getInt("Y")+(int)offset.y);leash.putInt("Z",leash.getInt("Z")+(int)offset.z);}
        for(var child:tag.getList("Passengers",Tag.TAG_COMPOUND))translate((CompoundTag)child,offset);
    }
    private static int validateEntityTree(CompoundTag tag,int depth){
        var id=net.minecraft.resources.ResourceLocation.tryParse(tag.getString("id"));if(depth>16||id==null||!BuiltInRegistries.ENTITY_TYPE.containsKey(id))throw new IllegalArgumentException("场景实体类型未加载或骑乘层级过深："+tag.getString("id"));
        int count=1;for(var child:tag.getList("Passengers",Tag.TAG_COMPOUND)){count+=validateEntityTree((CompoundTag)child,depth+1);if(count>128)throw new IllegalArgumentException("场景骑乘实体过多");}return count;
    }
    private static void freshUuids(CompoundTag tag){tag.putUUID("UUID",UUID.randomUUID());tag.getCompound("ForgeData").remove(ZombiesSceneEntityJournal.MARKER);for(var child:tag.getList("Passengers",Tag.TAG_COMPOUND))freshUuids((CompoundTag)child);}
}
