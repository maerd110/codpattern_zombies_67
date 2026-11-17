package com.cdp.codpattern.app.zombies.campaign;

import java.util.*;

/** Stable authoring identities are independent of entity UUIDs, graph node IDs and account rewards. */
public record CampaignDefinition(int version, List<Campaign> campaigns, List<Entry> states,
        List<Entry> actors, List<Entry> items, List<Entry> tasks, List<Entry> collections, List<Event> events) {
    public record Entry(String id, String name, String description) {
        public Entry { id = CampaignDefinition.id(id); name = text(name, 128); description = text(description, 2048); }
    }
    public record Campaign(String id, String name, List<Chapter> chapters) {
        public Campaign { id = CampaignDefinition.id(id); name = text(name, 128); chapters = copy(chapters, 128); unique(chapters.stream().map(Chapter::id).toList()); }
    }
    public record Chapter(String id, String name, String map, String reward, Map<String,String> actors) {
        public Chapter { id=CampaignDefinition.id(id);name=text(name,128);map=text(map,256);if(map.isBlank())throw new IllegalArgumentException("请选择章节地图");reward=text(reward,64);actors=actors==null?Map.of():Map.copyOf(actors); }
    }
    public enum Operation { STATE, TASK, ACTOR, GIVE, TAKE, COLLECTION, CHECKPOINT, COMPLETE }
    public record Effect(Operation operation, String key, String value, int count, boolean everyone) {
        public Effect { Objects.requireNonNull(operation); key=id(key);value=text(value,1024);if(count<0||count>2304)throw new IllegalArgumentException("数量需要在 0 至 2304 之间"); }
    }
    /** A blank rule and minWave=0 is explicit-only; automatic effects are once per chapter checkpoint history. */
    public record Event(String id, String name, String chapter, int minWave, String rule,
                        String state, String equalsValue, List<Effect> effects) {
        public Event { id=CampaignDefinition.id(id);name=text(name,128);chapter=CampaignDefinition.id(chapter);rule=text(rule,129);state=text(state,64);equalsValue=text(equalsValue,1024);effects=copy(effects,32);if(minWave<0)throw new IllegalArgumentException("波次不能小于 0"); }
    }
    public CampaignDefinition {
        if(version!=1)throw new IllegalArgumentException("未知战役配置版本");
        campaigns=copy(campaigns,32);states=copy(states,256);actors=copy(actors,256);items=copy(items,256);tasks=copy(tasks,256);collections=copy(collections,256);events=copy(events,512);
        unique(campaigns.stream().map(Campaign::id).toList());
        for(var list:List.of(states,actors,items,tasks,collections))unique(list.stream().map(Entry::id).toList());
        unique(events.stream().map(Event::id).toList());
        var chapters=new HashSet<String>();for(var c:campaigns)for(var ch:c.chapters()) {
            if(!chapters.add(ch.id()))throw new IllegalArgumentException("章节编号重复："+ch.id());
            if(!ch.reward().isBlank())require(collections,ch.reward());
            for(var a:ch.actors().values())require(actors,a);
        }
        for(var e:events){if(!chapters.contains(e.chapter()))throw new IllegalArgumentException("事件引用未知章节："+e.chapter());if(!e.state().isBlank())require(states,e.state());
            for(var f:e.effects())switch(f.operation()){
                case STATE->require(states,f.key());case TASK->require(tasks,f.key());case ACTOR->{require(actors,f.key());if(!List.of("alive","dead","hidden").contains(f.value()))throw new IllegalArgumentException("人物状态为 alive、dead 或 hidden");}
                case GIVE,TAKE->{require(items,f.key());if(f.count()<1)throw new IllegalArgumentException("剧情物品数量需要大于 0");}
                case COLLECTION->require(collections,f.key());default->{}
            }
        }
    }
    public static CampaignDefinition empty(){return new CampaignDefinition(1,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());}
    public Chapter chapter(String id){return campaigns.stream().flatMap(c->c.chapters().stream()).filter(c->c.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("章节不存在："+id));}
    public Campaign campaign(String id){return campaigns.stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("战役不存在："+id));}
    public String next(String campaign,String chapter){var chapters=campaign(campaign).chapters();for(int i=0;i<chapters.size();i++)if(chapters.get(i).id().equals(chapter))return i+1<chapters.size()?chapters.get(i+1).id():"";throw new IllegalArgumentException("章节不属于战役");}
    public static String label(List<Entry> entries,String id){return entries.stream().filter(e->e.id().equals(id)).map(Entry::name).findFirst().orElse(id);}
    public static String id(String id){if(id==null||!id.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("内部编号使用 1 至 64 位字母、数字、下划线或短横线");return id;}
    private static String text(String s,int max){s=s==null?"":s;if(s.length()>max)throw new IllegalArgumentException("文字过长，最多 "+max+" 字");return s;}
    private static <T> List<T> copy(List<T> l,int max){l=l==null?List.of():List.copyOf(l);if(l.size()>max)throw new IllegalArgumentException("条目超过上限 "+max);return l;}
    private static void unique(List<String> ids){if(ids.stream().distinct().count()!=ids.size())throw new IllegalArgumentException("内部编号重复");}
    private static void require(List<Entry> list,String id){if(list.stream().noneMatch(e->e.id().equals(id)))throw new IllegalArgumentException("引用未定义的条目："+id);}
}
