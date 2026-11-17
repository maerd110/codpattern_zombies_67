package com.cdp.codpattern.app.zombies.campaign;

import com.cdp.codpattern.app.zombies.npc.*;
import java.util.*;

/** Qualified endpoints are story identities. Canvas wires never masquerade as local execution edges. */
public record CampaignCanvas(List<Link> links,List<Tag> tags,Map<String,Point> layout,List<Profile> profiles,List<Reference> references) {
    public enum LinkKind { REQUIRE, EFFECT }
    public enum TagKind { MILESTONE, CHECKPOINT }
    public record Point(double x,double y){public Point{if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>1000000||Math.abs(y)>1000000)throw new IllegalArgumentException("画布坐标无效");}}
    public record Link(String id,String from,String port,String to,LinkKind kind,String value,int count,boolean everyone){
        public Link{CampaignDefinition.id(id);endpoint(from);endpoint(to);Objects.requireNonNull(kind);port=port==null?"done":port;value=value==null?"":value;if(port.length()>64||value.length()>1024||count<1||count>2304)throw new IllegalArgumentException("连线参数无效");}
    }
    public record Tag(String id,String target,String port,String name,TagKind kind){
        public Tag{CampaignDefinition.id(id);endpoint(target);port=port==null?"done":port;name=name==null?id:name;Objects.requireNonNull(kind);if(!target.startsWith("node:")||port.length()>64||name.length()>128)throw new IllegalArgumentException("标签需要绑定 NPC 流程节点");}
    }
    /** Position and per-map rule programs are intentionally not part of a reusable character identity. */
    public record Profile(String actor,ZombiesNpcConfig.Appearance appearance,String dialogue,String sourceMap,String sourceNpc){
        public Profile(String actor,ZombiesNpcConfig.Appearance appearance,String dialogue){this(actor,appearance,dialogue,"","");}
        public Profile{CampaignDefinition.id(actor);Objects.requireNonNull(appearance);appearance=new ZombiesNpcConfig.Appearance(appearance.name(),appearance.skin(),appearance.pose(),appearance.alive(),null,appearance.invulnerable(),appearance.chatName(),appearance.nameColor(),appearance.textColor(),appearance.combat(),appearance.playerSkin());dialogue=dialogue==null?"":dialogue;sourceMap=sourceMap==null?"":sourceMap;sourceNpc=sourceNpc==null?"":sourceNpc;if(dialogue.length()>8192||sourceMap.length()>256)throw new IllegalArgumentException("人物默认台词或来源名称过长");if(!sourceNpc.isBlank())CampaignDefinition.id(sourceNpc);}
    }
    public record Reference(String id,String chapter,String actor,String localNpc){
        public Reference{CampaignDefinition.id(id);CampaignDefinition.id(chapter);CampaignDefinition.id(actor);localNpc=localNpc==null?"":localNpc;if(!localNpc.isBlank())CampaignDefinition.id(localNpc);}
    }
    public CampaignCanvas{
        links=List.copyOf(links==null?List.of():links);tags=List.copyOf(tags==null?List.of():tags);layout=Map.copyOf(layout==null?Map.of():layout);profiles=List.copyOf(profiles==null?List.of():profiles);references=List.copyOf(references==null?List.of():references);
        if(links.size()>2048||tags.size()>1024||layout.size()>16384||profiles.size()>256||references.size()>1024)throw new IllegalArgumentException("战役画布内容过多，请拆分战役");
        unique(links.stream().map(Link::id).toList());unique(tags.stream().map(Tag::id).toList());unique(profiles.stream().map(Profile::actor).toList());unique(references.stream().map(Reference::id).toList());
    }
    public static CampaignCanvas empty(){return new CampaignCanvas(List.of(),List.of(),Map.of(),List.of(),List.of());}
    public static String node(String chapter,String npc,String rule,String node){return "node:"+chapter+":"+npc+":"+rule+":"+node;}
    public static String[] endpoint(String key){if(key==null||key.length()>300)throw new IllegalArgumentException("连线目标无效");var p=key.split(":",-1);if(p.length!=2&&p.length!=5)throw new IllegalArgumentException("连线目标无效："+key);if(p.length==5&&!p[0].equals("node")||p.length==2&&!List.of("state","actor","item","task","collection","event","tag").contains(p[0]))throw new IllegalArgumentException("未知连线类别");for(int i=1;i<p.length;i++)CampaignDefinition.id(p[i]);return p;}
    private static void unique(List<String> ids){if(ids.stream().distinct().count()!=ids.size())throw new IllegalArgumentException("画布编号重复");}
    public void validate(List<CampaignDefinition.Campaign> campaigns,List<CampaignDefinition.Entry> states,List<CampaignDefinition.Entry> actors,List<CampaignDefinition.Entry> items,List<CampaignDefinition.Entry> tasks,List<CampaignDefinition.Entry> collections,List<CampaignDefinition.Event> events){
        var assets=new HashSet<String>();for(var c:campaigns)for(var ch:c.chapters())assets.add("chapter:"+ch.id());
        var lists=List.of(states,actors,items,tasks,collections);var types=List.of("state","actor","item","task","collection");for(int i=0;i<types.size();i++)for(var e:lists.get(i))assets.add(types.get(i)+":"+e.id());for(var e:events)assets.add("event:"+e.id());for(var t:tags)assets.add("tag:"+t.id());
        for(var l:links){require(assets,l.from());require(assets,l.to());if(!l.to().startsWith("node:")&&!l.to().startsWith("event:")&&l.kind()==LinkKind.REQUIRE)throw new IllegalArgumentException("前置条件连线需要指向流程节点或战役事件");if(l.kind()==LinkKind.EFFECT&&(!l.from().startsWith("node:")||l.to().startsWith("node:")||l.to().startsWith("tag:")))throw new IllegalArgumentException("效果连线需要从流程节点连接到道具、状态、人物、任务、收藏或事件");if(l.from().equals(l.to()))throw new IllegalArgumentException("前置连线不能连接到自身");}
        for(var t:tags)require(assets,t.target());for(var p:profiles)require(assets,"actor:"+p.actor());for(var r:references){if(!assets.contains("chapter:"+r.chapter()))throw new IllegalArgumentException("人物引用的章节不存在");require(assets,"actor:"+r.actor());if(profiles.stream().noneMatch(p->p.actor().equals(r.actor())))throw new IllegalArgumentException("人物尚未保存为可复用模板");}
        // A dependency cycle has no satisfiable starting point. Local dialogue loops remain legal.
        var edges=new HashMap<String,List<String>>();for(var l:links)if(l.kind()==LinkKind.REQUIRE)edges.computeIfAbsent(l.from(),k->new ArrayList<>()).add(l.to());var visiting=new HashSet<String>();var done=new HashSet<String>();for(var key:edges.keySet())visit(key,edges,visiting,done);
    }
    private static void require(Set<String> assets,String key){var p=endpoint(key);if(p[0].equals("node")?!assets.contains("chapter:"+p[1]):!assets.contains(key))throw new IllegalArgumentException("引用不存在："+key);}
    private static void visit(String key,Map<String,List<String>> edges,Set<String> visiting,Set<String> done){if(done.contains(key))return;if(!visiting.add(key))throw new IllegalArgumentException("跨关前置连线形成循环");for(var next:edges.getOrDefault(key,List.of()))visit(next,edges,visiting,done);visiting.remove(key);done.add(key);}
    public void validateNodes(CampaignDefinition def,Map<String,ZombiesNpcConfig> maps){
        var owner=new HashMap<String,String>();var order=new HashMap<String,Integer>();for(var c:def.campaigns())for(int i=0;i<c.chapters().size();i++){owner.put(c.chapters().get(i).id(),c.id());order.put(c.chapters().get(i).id(),i);}
        for(var l:links){var from=endpoint(l.from());var to=endpoint(l.to());if(from[0].equals("node")&&to[0].equals("node")&&(!owner.get(from[1]).equals(owner.get(to[1]))||order.get(from[1])>order.get(to[1])))throw new IllegalArgumentException("跨关前置条件需沿同一战役从前面的章节指向后面的章节");if(l.kind()==LinkKind.REQUIRE&&from[0].equals("collection"))throw new IllegalArgumentException("账号收藏不作为剧情存档的前置条件");if(l.kind()==LinkKind.EFFECT&&to[0].equals("actor")&&!l.value().isBlank()&&!List.of("alive","dead","hidden").contains(l.value()))throw new IllegalArgumentException("人物状态使用 alive、dead 或 hidden");
            if(from[0].equals("item")&&to[0].equals("node")){var rule=maps.get(def.chapter(to[1]).map()).npcs().stream().filter(n->n.id().equals(to[2])).flatMap(n->n.rules().stream()).filter(r->r.id().equals(to[3])).findFirst().orElseThrow();if(rule.trigger()==ZombiesNpcConfig.Trigger.AUTO)throw new IllegalArgumentException("个人剧情仓库条件需要连接到玩家交互规则");}
        }
        for(var l:links){checkNode(def,maps,l.from(),l.port());checkNode(def,maps,l.to(),null);if(l.kind()==LinkKind.EFFECT&&l.to().startsWith("event:")){var e=def.events().stream().filter(v->v.id().equals(l.to().substring(6))).findFirst().orElseThrow();if(!e.chapter().equals(endpoint(l.from())[1]))throw new IllegalArgumentException("跨关事件使用前置条件连线，不能在前一关直接执行后一关事件");}}
        for(var t:tags)checkNode(def,maps,t.target(),t.port());
        for(var ref:references)if(!ref.localNpc().isBlank()){var chapter=def.chapter(ref.chapter());var npcs=maps.get(chapter.map());if(npcs==null||npcs.npcs().stream().noneMatch(n->n.id().equals(ref.localNpc()))||!ref.actor().equals(chapter.actors().get(ref.localNpc())))throw new IllegalArgumentException("人物尚未在目标关卡实际放置或绑定："+ref.id());}
    }
    private static void checkNode(CampaignDefinition def,Map<String,ZombiesNpcConfig> maps,String key,String port){var p=endpoint(key);if(!p[0].equals("node"))return;var map=maps.get(def.chapter(p[1]).map());var node=map==null?null:map.npcs().stream().filter(n->n.id().equals(p[2])).flatMap(n->n.rules().stream()).filter(r->r.id().equals(p[3])&&r.graph()!=null).flatMap(r->r.graph().nodes().stream()).filter(n->n.id().equals(p[4])).findFirst().orElse(null);if(node==null)throw new IllegalArgumentException("连线或标签引用了已删除的流程节点："+key);if(port!=null&&!ports(node).contains(port))throw new IllegalArgumentException("选项出口不存在："+key+" / "+port);}
    public static List<String> ports(ZombiesNpcGraph.Node node){return node.type()==ZombiesNpcGraph.Type.CHOICE?node.options().stream().map(ZombiesNpcGraph.Option::id).toList():node.type()==ZombiesNpcGraph.Type.BRANCH?List.of("true","false"):List.of("done");}
}
