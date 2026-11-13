package com.cdp.codpattern.app.zombies.scenario;

import com.cdp.codpattern.app.zombies.npc.ZombiesNpcConfig;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import java.util.*;

/** Map-wide cast, pre-wave dialogue, optional wave overrides and bounded one-shot events. */
public record ZombiesScenarioConfig(int version,List<JsonObject> waves,List<Speaker> speakers,List<Dialogue> dialogues,List<MapEvent> events,List<Task> tasks,List<AreaReward> areaRewards,SnapshotLimits snapshotLimits) {
    public ZombiesScenarioConfig(int version,List<JsonObject> waves,List<Speaker> speakers,List<Dialogue> dialogues,List<MapEvent> events,List<Task> tasks,List<AreaReward> areaRewards){this(version,waves,speakers,dialogues,events,tasks,areaRewards,SnapshotLimits.defaults());}
    public ZombiesScenarioConfig(int version,List<JsonObject> waves,List<Speaker> speakers,List<Dialogue> dialogues,List<MapEvent> events,List<Task> tasks){this(version,waves,speakers,dialogues,events,tasks,List.of());}
    public ZombiesScenarioConfig(int version,List<JsonObject> waves,List<Speaker> speakers,List<Dialogue> dialogues,List<MapEvent> events){this(version,waves,speakers,dialogues,events,List.of());}
    public record RewardItem(String item,int count){
        public RewardItem {if(item==null||item.isBlank()||item.length()>16384||count<1||count>2304)throw new IllegalArgumentException("区域奖励需要物品和 1 至 2304 的数量");}
        public ZombiesNpcConfig.Action action(){return new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.GIVE_ITEM,null,null,null,item,count,null,null,0);}
    }
    public record AreaReward(String id,String name,BlockPos from,BlockPos to,List<RewardItem> items,int minWave,String rule){
        public AreaReward {requireId(id);name=name==null?id:name;rule=rule==null?"":rule;items=List.copyOf(items==null?List.of():items);
            if(name.length()>128||from==null||to==null||items.isEmpty()||items.size()>16||minWave<0)throw new IllegalArgumentException("区域奖励需设置范围、物品和有效的触发条件");}
        public boolean contains(net.minecraft.world.phys.Vec3 point){return point.x>=Math.min(from.getX(),to.getX())&&point.x<Math.max(from.getX(),to.getX())+1.0&&point.y>=Math.min(from.getY(),to.getY())&&point.y<Math.max(from.getY(),to.getY())+1.0&&point.z>=Math.min(from.getZ(),to.getZ())&&point.z<Math.max(from.getZ(),to.getZ())+1.0;}
    }
    public record Task(String id,String name,boolean automatic,int minWave,String rule,String text){
        public Task{requireId(id);name=name==null?id:name;rule=rule==null?"":rule;text=text==null?"":text;if(name.length()>128||text.length()>1024||minWave<0)throw new IllegalArgumentException("任务名称、文本或波次无效");}
    }
    public record Speaker(String id,String name,String nameColor,String textColor,boolean bold,boolean italic) {
        public Speaker { requireId(id);if(name==null||name.isBlank()||name.length()>128)throw new IllegalArgumentException("人物名字需要 1 至 128 字");
            nameColor=color(nameColor);textColor=color(textColor); }
        public Component say(String text){return Component.literal("["+name+"] ").withStyle(s->s.withColor(TextColor.parseColor(nameColor)).withBold(bold).withItalic(italic))
                .append(Component.literal(text).withStyle(s->s.withColor(TextColor.parseColor(textColor)).withBold(bold).withItalic(italic)));}
    }
    public record Line(String speaker,String text,double seconds) {
        public Line {speaker=speaker==null?"":speaker;if(text==null||text.isBlank()||text.length()>8192)throw new IllegalArgumentException("台词需要 1 至 8192 字");ZombiesNpcConfig.intervalTicks(seconds);}
    }
    public record Dialogue(int wave,List<Line> lines,double delaySeconds) {
        public Dialogue(int wave,List<Line> lines){this(wave,lines,0); }
        public Dialogue {ZombiesNpcConfig.intervalTicks(delaySeconds);if(wave<1)throw new IllegalArgumentException("波次必须大于 0");lines=List.copyOf(lines);if(lines.size()>256)throw new IllegalArgumentException("每波最多 256 句台词");}
    }
    public enum Weather { CLEAR,RAIN,THUNDER }
    public enum RegionMode { FILL, SCENE }
    public record SnapshotLimits(int maxRegionBlocks,int maxEventBlocks) {
        public static final int MEMORY_PROTECTION_BLOCKS=1048576;
        public SnapshotLimits {if(maxRegionBlocks<1||maxEventBlocks<1)throw new IllegalArgumentException("场景容量上限必须是正整数，不能设为 0 或无限制");if(maxRegionBlocks>MEMORY_PROTECTION_BLOCKS||maxEventBlocks>MEMORY_PROTECTION_BLOCKS)throw new IllegalArgumentException("场景上限不能超过单次操作的内存保护范围 1048576 格，请拆分事件");}
        public static SnapshotLimits defaults(){return new SnapshotLimits(65536,131072);}
        public void checkRegion(long volume){if(volume>maxRegionBlocks)throw new IllegalArgumentException("选区体积 "+volume+" 格，超过本关卡的单区域上限 "+maxRegionBlocks+" 格；可在场景容量设置中调整（空气也计入体积）");}
    }
    public static int volume(long x,long y,long z){if(x<1||y<1||z<1||x>Integer.MAX_VALUE||y>Integer.MAX_VALUE/x||z>Integer.MAX_VALUE/(x*y))throw new IllegalArgumentException("区域体积过大，超出可存储范围");return (int)(x*y*z);}
    /** Palette data is relative to the destination's minimum corner; air is deliberately included. */
    public record SceneBlockEntity(int x,int y,int z,String nbt) { }
    public record SceneEntity(String nbt) { }
    public record Scene(int sizeX,int sizeY,int sizeZ,List<String> palette,List<Integer> blocks,List<SceneBlockEntity> blockEntities,List<SceneEntity> entities,boolean includesEntities) {
        public Scene(int x,int y,int z,List<String> palette,List<Integer> blocks){this(x,y,z,palette,blocks,List.of(),List.of(),false);}
        public Scene {
            int volume=volume(sizeX,sizeY,sizeZ);
            palette=List.copyOf(palette==null?List.of():palette);blocks=List.copyOf(blocks==null?List.of():blocks);
            if(palette.isEmpty()||palette.size()>volume||palette.stream().anyMatch(v->v==null||v.isBlank()||v.length()>1024)||blocks.size()!=volume)throw new IllegalArgumentException("场景方块数据不完整");
            int count=palette.size();if(blocks.stream().anyMatch(i->i==null||i<0||i>=count))throw new IllegalArgumentException("场景方块索引无效");
            blockEntities=List.copyOf(blockEntities==null?List.of():blockEntities);entities=List.copyOf(entities==null?List.of():entities);
            if(blockEntities.size()>blocks.size()||entities.size()>128||!includesEntities&&!entities.isEmpty())throw new IllegalArgumentException("场景附加数据无效，实体最多 128 个");
            var occupied=new HashSet<String>();long bytes=0;
            for(var value:blockEntities){if(value.x()<0||value.x()>=sizeX||value.y()<0||value.y()>=sizeY||value.z()<0||value.z()>=sizeZ||!occupied.add(value.x()+","+value.y()+","+value.z()))throw new IllegalArgumentException("场景方块附加数据坐标无效或重复");bytes+=nbtSize(value.nbt());}
            for(var value:entities)bytes+=nbtSize(value.nbt());if(bytes>1500000)throw new IllegalArgumentException("场景附加数据过大，请缩小框选区域");
        }
        private static int nbtSize(String value){if(value==null||value.isBlank()||value.length()>262144)throw new IllegalArgumentException("场景单个对象数据过大或为空");return value.length();}
        public BlockPos endAt(BlockPos origin){return origin.offset(sizeX-1,sizeY-1,sizeZ-1);}
    }
    public record Region(BlockPos from,BlockPos to,String block,RegionMode mode,Scene scene,BlockPos sourceFrom,BlockPos sourceTo) {
        public Region(BlockPos from,BlockPos to,String block){this(from,to,block,RegionMode.FILL,null,null,null);}
        public Region {
            mode=mode==null?(scene==null?RegionMode.FILL:RegionMode.SCENE):mode;
            if(from==null||to==null)throw new IllegalArgumentException("区域需要两个目标坐标");
            long a=Math.abs((long)from.getX()-to.getX())+1,b=Math.abs((long)from.getY()-to.getY())+1,c=Math.abs((long)from.getZ()-to.getZ())+1;
            volume(a,b,c);
            if(mode==RegionMode.FILL&&(block==null||block.isBlank()))throw new IllegalArgumentException("填充区域需要选择方块");
            if(mode==RegionMode.SCENE){if(scene==null)throw new IllegalArgumentException("请先读取部署者搭建的目标场景");if(a!=scene.sizeX()||b!=scene.sizeY()||c!=scene.sizeZ())throw new IllegalArgumentException("目标区域尺寸与已读取场景不一致");}
        }
        public int blockCount(){return volume(Math.abs((long)from.getX()-to.getX())+1,Math.abs((long)from.getY()-to.getY())+1,Math.abs((long)from.getZ()-to.getZ())+1);}
        public void forEachCell(java.util.function.BiConsumer<BlockPos,String> consumer){
            if(mode==RegionMode.FILL){for(var p:BlockPos.betweenClosed(from,to))consumer.accept(p.immutable(),block);}
            else{var origin=new BlockPos(Math.min(from.getX(),to.getX()),Math.min(from.getY(),to.getY()),Math.min(from.getZ(),to.getZ()));int i=0;
                for(int y=0;y<scene.sizeY();y++)for(int z=0;z<scene.sizeZ();z++)for(int x=0;x<scene.sizeX();x++)consumer.accept(origin.offset(x,y,z),scene.palette().get(scene.blocks().get(i++)));}
        }
        public List<ZombiesNpcConfig.BlockPlacement> placements(){var cells=new ArrayList<ZombiesNpcConfig.BlockPlacement>();forEachCell((pos,value)->cells.add(placement(pos,value)));return cells;}
        private static ZombiesNpcConfig.BlockPlacement placement(BlockPos p,String block){return new ZombiesNpcConfig.BlockPlacement(new ZombiesNpcConfig.Position(p.getX(),p.getY(),p.getZ(),0),block);}
    }
    public record MapEvent(String id,String name,boolean automatic,int minWave,String rule,List<Region> regions,Weather weather,Long time) {
        public MapEvent {requireId(id);if(name==null||name.length()>128||minWave<0||time!=null&&(time<0||time>23999))throw new IllegalArgumentException("地图事件名字、波次或时间无效");
            rule=rule==null?"":rule;regions=List.copyOf(regions==null?List.of():regions);if(regions.size()>16)throw new IllegalArgumentException("单事件最多 16 个区域");
            if(regions.isEmpty()&&weather==null&&time==null)throw new IllegalArgumentException("地图事件至少设置一种变化");}
    }
    public ZombiesScenarioConfig {
        snapshotLimits=snapshotLimits==null?SnapshotLimits.defaults():snapshotLimits;
        areaRewards=List.copyOf(areaRewards==null?List.of():areaRewards);if(areaRewards.size()>128||areaRewards.stream().map(AreaReward::id).distinct().count()!=areaRewards.size())throw new IllegalArgumentException("区域奖励过多或编号重复");
        tasks=List.copyOf(tasks==null?List.of():tasks);if(tasks.size()>256||tasks.stream().map(Task::id).distinct().count()!=tasks.size())throw new IllegalArgumentException("任务条目过多或编号重复");
        if(version!=1)throw new IllegalArgumentException("未知关卡剧情版本");
        waves=waves==null?null:waves.stream().map(JsonObject::deepCopy).toList();speakers=List.copyOf(speakers==null?List.of():speakers);dialogues=List.copyOf(dialogues==null?List.of():dialogues);events=List.copyOf(events==null?List.of():events);
        for(var event:events){long count=0;for(var region:event.regions()){snapshotLimits.checkRegion(region.blockCount());count+=region.blockCount();}if(count>snapshotLimits.maxEventBlocks())throw new IllegalArgumentException("事件「"+event.name()+"」合计 "+count+" 格，超过本关卡的单事件上限 "+snapshotLimits.maxEventBlocks()+" 格");}
        if(waves!=null&&waves.size()>512||speakers.size()>128||dialogues.size()>512||events.size()>128)throw new IllegalArgumentException("关卡配置条目过多");
        if(speakers.stream().map(Speaker::id).distinct().count()!=speakers.size()||dialogues.stream().map(Dialogue::wave).distinct().count()!=dialogues.size()||events.stream().map(MapEvent::id).distinct().count()!=events.size())throw new IllegalArgumentException("人物、波次台词或事件编号重复");
        var eventIds=new HashSet<String>();events.forEach(e->eventIds.add(e.id()));if(tasks.stream().anyMatch(t->eventIds.contains(t.id())))throw new IllegalArgumentException("任务与地图事件编号不能重复");
        var ids=new HashSet<String>();speakers.forEach(s->ids.add(s.id()));for(var d:dialogues)for(var l:d.lines())if(!l.speaker().isBlank()&&!ids.contains(l.speaker()))throw new IllegalArgumentException("台词引用未知人物："+l.speaker());
    }
    public static ZombiesScenarioConfig empty(){return new ZombiesScenarioConfig(1,null,List.of(),List.of(),List.of());}
    private static void requireId(String s){if(s==null||!s.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("编号使用字母、数字、下划线或短横线");}
    private static String color(String s){s=s==null||s.isBlank()?"white":s;if(TextColor.parseColor(s)==null)throw new IllegalArgumentException("颜色使用原版英文颜色名或 #RRGGBB");return s;}
}
