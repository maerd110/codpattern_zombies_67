package com.cdp.codpattern.app.zombies.scenario;

import com.cdp.codpattern.app.zombies.npc.ZombiesNpcConfig;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import java.util.*;

/** Map-wide cast, pre-wave dialogue, optional wave overrides and bounded one-shot events. */
public record ZombiesScenarioConfig(int version,List<JsonObject> waves,List<Speaker> speakers,List<Dialogue> dialogues,List<MapEvent> events) {
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
    public record Region(BlockPos from,BlockPos to,String block) {
        public Region {if(from==null||to==null||block==null||block.isBlank())throw new IllegalArgumentException("区域需要两个坐标和方块");
            long a=Math.abs((long)from.getX()-to.getX())+1,b=Math.abs((long)from.getY()-to.getY())+1,c=Math.abs((long)from.getZ()-to.getZ())+1;
            if(a>4096||b>4096||c>4096)throw new IllegalArgumentException("区域过大");long cells=a*b*c;
            if(cells<1||cells>4096)throw new IllegalArgumentException("单个区域最多 4096 个方块");}
        public List<ZombiesNpcConfig.BlockPlacement> placements(){var cells=new ArrayList<ZombiesNpcConfig.BlockPlacement>();for(var p:BlockPos.betweenClosed(from,to))cells.add(new ZombiesNpcConfig.BlockPlacement(new ZombiesNpcConfig.Position(p.getX(),p.getY(),p.getZ(),0),block));return cells;}
    }
    public record MapEvent(String id,String name,boolean automatic,int minWave,String rule,List<Region> regions,Weather weather,Long time) {
        public MapEvent {requireId(id);if(name==null||name.length()>128||minWave<0||time!=null&&(time<0||time>23999))throw new IllegalArgumentException("地图事件名字、波次或时间无效");
            rule=rule==null?"":rule;regions=List.copyOf(regions==null?List.of():regions);if(regions.size()>16||regions.stream().mapToInt(r->r.placements().size()).sum()>8192)throw new IllegalArgumentException("单事件最多 16 个区域、8192 个方块");
            if(regions.isEmpty()&&weather==null&&time==null)throw new IllegalArgumentException("地图事件至少设置一种变化");}
    }
    public ZombiesScenarioConfig {
        if(version!=1)throw new IllegalArgumentException("未知关卡剧情版本");
        waves=waves==null?null:waves.stream().map(JsonObject::deepCopy).toList();speakers=List.copyOf(speakers==null?List.of():speakers);dialogues=List.copyOf(dialogues==null?List.of():dialogues);events=List.copyOf(events==null?List.of():events);
        if(waves!=null&&waves.size()>512||speakers.size()>128||dialogues.size()>512||events.size()>128)throw new IllegalArgumentException("关卡配置条目过多");
        if(speakers.stream().map(Speaker::id).distinct().count()!=speakers.size()||dialogues.stream().map(Dialogue::wave).distinct().count()!=dialogues.size()||events.stream().map(MapEvent::id).distinct().count()!=events.size())throw new IllegalArgumentException("人物、波次台词或事件编号重复");
        var ids=new HashSet<String>();speakers.forEach(s->ids.add(s.id()));for(var d:dialogues)for(var l:d.lines())if(!l.speaker().isBlank()&&!ids.contains(l.speaker()))throw new IllegalArgumentException("台词引用未知人物："+l.speaker());
    }
    public static ZombiesScenarioConfig empty(){return new ZombiesScenarioConfig(1,null,List.of(),List.of(),List.of());}
    private static void requireId(String s){if(s==null||!s.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("编号使用字母、数字、下划线或短横线");}
    private static String color(String s){s=s==null||s.isBlank()?"white":s;if(TextColor.parseColor(s)==null)throw new IllegalArgumentException("颜色使用原版英文颜色名或 #RRGGBB");return s;}
}
