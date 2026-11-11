package com.cdp.codpattern.app.zombies.npc;

import com.google.gson.*;
import java.util.*;

/** Map-wide authoring view. Local execution edges and rule-completion dependencies stay distinct. */
public final class ZombiesNpcSharedCanvas {
    private ZombiesNpcSharedCanvas() { }
    public record Flow(int npcIndex, int ruleIndex, JsonObject npc, JsonObject rule) {
        public String key() { return npc.get("id").getAsString() + "." + rule.get("id").getAsString(); }
        public List<JsonObject> nodes() { return ZombiesNpcGraphDraft.nodes(rule).asList().stream().map(JsonElement::getAsJsonObject).toList(); }
        public JsonObject entry() { return nodes().stream().filter(n -> n.get("type").getAsString().equals("ENTRY")).findFirst().orElseThrow(); }
    }
    public record Ref(Flow flow, JsonObject node) {
        public String key() { return (flow==null?"_loose":flow.key()) + "/" + node.get("id").getAsString(); }
    }
    public static JsonObject looseRule(ZombiesNpcEditorDraft draft){var r=new JsonObject();var g=new JsonObject();g.add("nodes",draft.looseNodes());r.add("graph",g);return r;}
    public static JsonObject completion(Flow f){return f.nodes().stream().filter(n->n.get("type").getAsString().equals("END")&&(!n.has("complete")||n.get("complete").getAsBoolean())).findFirst().orElse(null);}
    public static Ref adopt(ZombiesNpcEditorDraft draft,JsonObject seed,Flow target){
        var pool=draft.looseNodes();Set<String> ids=new HashSet<>();ids.add(seed.get("id").getAsString());boolean changed;
        do{changed=false;for(var e:pool){var n=e.getAsJsonObject();for(var port:ZombiesNpcGraphDraft.ports(n)){String next=ZombiesNpcGraphDraft.target(n,port);if(next.isBlank())continue;if(ids.contains(n.get("id").getAsString())||ids.contains(next)){changed|=ids.add(next);changed|=ids.add(n.get("id").getAsString());}}}}while(changed);
        var moving=pool.asList().stream().map(JsonElement::getAsJsonObject).filter(n->ids.contains(n.get("id").getAsString())).toList();
        if(target.nodes().size()+moving.size()>128)throw new IllegalArgumentException("规则节点数量超出限制");
        if(target.rule().get("trigger").getAsString().equals("AUTO"))for(var n:moving){
            if(Set.of("INTERACT","CHOICE").contains(n.get("type").getAsString()))throw new IllegalArgumentException("此节点必须接入玩家交互规则");
            if(n.has("action")&&Set.of("GIVE_ITEM","TAKE_ITEM").contains(n.getAsJsonObject("action").get("kind").getAsString()))throw new IllegalArgumentException("物品交收必须接入交互规则");
            for(var c:ZombiesNpcGraphDraft.conditions(n))for(var t:c.getAsJsonArray("tests"))if(t.getAsJsonObject().get("kind").getAsString().equals("ITEM_ACTOR"))throw new IllegalArgumentException("交互玩家条件必须接入交互规则");
            if(n.get("type").getAsString().equals("END")&&n.has("complete")&&!n.get("complete").getAsBoolean())throw new IllegalArgumentException("自动规则必须标记完成");
        }
        Set<String> used=new HashSet<>();target.nodes().forEach(n->used.add(n.get("id").getAsString()));Map<String,String> names=new HashMap<>();
        for(var n:moving){int k=1;while(used.contains("node_"+k))k++;String id="node_"+k;used.add(id);names.put(n.get("id").getAsString(),id);}
        for(var n:moving){pool.remove(n);n.addProperty("id",names.get(n.get("id").getAsString()));for(var port:ZombiesNpcGraphDraft.ports(n)){String next=ZombiesNpcGraphDraft.target(n,port);if(names.containsKey(next))ZombiesNpcGraphDraft.target(n,port,names.get(next));}ZombiesNpcGraphDraft.nodes(target.rule()).add(n);}
        return new Ref(target,seed);
    }
    public static Ref connectExecution(ZombiesNpcEditorDraft draft,Ref from,String port,Ref to){
        if(from.node()==to.node())throw new IllegalArgumentException("无法连接到此节点");
        if(from.flow()!=null&&to.flow()!=null&&!from.flow().key().equals(to.flow().key()))throw new IllegalArgumentException("不同规则之间请使用完成状态连线");
        if(from.flow()==null&&to.flow()!=null)from=adopt(draft,from.node(),to.flow());
        if(to.flow()==null&&from.flow()!=null)to=adopt(draft,to.node(),from.flow());
        ZombiesNpcGraphDraft.connect(from.flow()==null?looseRule(draft):from.flow().rule(),from.node().get("id").getAsString(),port,to.node().get("id").getAsString());return to;
    }
    public static void connectCondition(String source,JsonObject target){
        String type=target.get("type").getAsString();if(!Set.of("BRANCH","AWAIT").contains(type))throw new IllegalArgumentException("请选择条件分支或等待条件节点");
        var tests=target.getAsJsonObject("condition").getAsJsonArray("tests");
        for(var e:tests){var t=e.getAsJsonObject();if(t.get("kind").getAsString().equals("RULE_DONE")&&t.get("reference").getAsString().equals(source))return;}
        for(var e:tests){var t=e.getAsJsonObject();if(t.get("kind").getAsString().equals("RULE_DONE")&&t.get("reference").getAsString().isBlank()){t.addProperty("reference",source);return;}}
        if(tests.size()>=16)throw new IllegalArgumentException("条件最多 16 项");var t=ZombiesNpcGraphDraft.test();t.addProperty("kind","RULE_DONE");t.addProperty("reference",source);tests.add(t);
    }
    public static List<Flow> flows(ZombiesNpcEditorDraft draft) {
        List<Flow> flows = new ArrayList<>();
        for (int n = 0; n < draft.npcs().size(); n++) {
            var npc = draft.npcs().get(n).getAsJsonObject(); var rules = npc.getAsJsonArray("rules");
            for (int r = 0; r < rules.size(); r++) flows.add(new Flow(n, r, npc, rules.get(r).getAsJsonObject()));
        }
        return flows;
    }
    public static void ensure(ZombiesNpcEditorDraft draft) {
        var flows = flows(draft);
        for (var flow : flows) ZombiesNpcGraphDraft.ensure(flow.rule());
        double bottom = flows.stream().filter(f -> version(f) == 1).flatMap(f -> f.nodes().stream()).mapToDouble(n -> n.get("y").getAsDouble() + height(n)).max().orElse(0);
        for (var flow : flows) if (version(flow) == 0) {
            var nodes = flow.nodes();
            double left = nodes.stream().mapToDouble(n -> n.get("x").getAsDouble()).min().orElse(0);
            double top = nodes.stream().mapToDouble(n -> n.get("y").getAsDouble()).min().orElse(0);
            translate(flow, 40 - left, bottom + 90 - top);
            flow.rule().getAsJsonObject("graph").addProperty("canvasVersion", 1);
            bottom = nodes.stream().mapToDouble(n -> n.get("y").getAsDouble() + height(n)).max().orElse(bottom);
        }
    }
    private static int height(JsonObject n){return n.get("type").getAsString().equals("CHOICE")?Math.max(88,61+n.getAsJsonArray("options").size()*22):88;}
    private static int version(Flow f) { var g = f.rule().getAsJsonObject("graph"); return g.has("canvasVersion") ? g.get("canvasVersion").getAsInt() : 0; }
    public static void translate(Flow flow, double dx, double dy) {
        for (var n : flow.nodes()) {
            double x = n.get("x").getAsDouble() + dx, y = n.get("y").getAsDouble() + dy;
            if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 99900 || Math.abs(y) > 99900) throw new IllegalArgumentException("画布位置超出范围，请使用自动整理");
        }
        for (var n : flow.nodes()) { n.addProperty("x", n.get("x").getAsDouble() + dx); n.addProperty("y", n.get("y").getAsDouble() + dy); }
    }
    public static void layout(ZombiesNpcEditorDraft draft) {
        double bottom = 0;
        for (var flow : flows(draft)) {
            ZombiesNpcGraphDraft.layout(flow.rule());
            translate(flow, 20, bottom + 60);
            flow.rule().getAsJsonObject("graph").addProperty("canvasVersion", 1);
            bottom = flow.nodes().stream().mapToDouble(n -> n.get("y").getAsDouble() + height(n)).max().orElse(bottom);
        }
    }
    public static Flow find(ZombiesNpcEditorDraft draft, String key) { return flows(draft).stream().filter(f -> f.key().equals(key)).findFirst().orElse(null); }
    public static void connect(ZombiesNpcEditorDraft draft, String predecessor, String successor) {
        var from = find(draft, predecessor); var to = find(draft, successor);
        if (from == null || to == null) throw new IllegalArgumentException("找不到要连接的规则");
        Map<String, Flow> byKey = new HashMap<>(); flows(draft).forEach(f -> byKey.put(f.key(), f));
        if (dependsOn(predecessor, successor, byKey, new HashSet<>())) throw new IllegalArgumentException("跨规则连线会形成循环依赖");
        var requirements = to.rule().getAsJsonArray("requires"); var key = new JsonPrimitive(predecessor);
        if (!requirements.contains(key)) requirements.add(key);
    }
    private static boolean dependsOn(String start, String goal, Map<String, Flow> flows, Set<String> visited) {
        if (start.equals(goal)) return true;
        if (!visited.add(start) || !flows.containsKey(start)) return false;
        for (var requirement : flows.get(start).rule().getAsJsonArray("requires")) if (dependsOn(requirement.getAsString(), goal, flows, visited)) return true;
        return false;
    }
    public static void unlink(ZombiesNpcEditorDraft draft, String predecessor, String successor) {
        var to = find(draft, successor); if (to != null) to.rule().getAsJsonArray("requires").remove(new JsonPrimitive(predecessor));
    }
    public static void unlinkOutgoing(ZombiesNpcEditorDraft draft, String predecessor) {
        for (var flow : flows(draft)) flow.rule().getAsJsonArray("requires").remove(new JsonPrimitive(predecessor));
    }
    public static void delete(ZombiesNpcEditorDraft draft, String key) {
        var flow = find(draft, key); if (flow == null) return;
        unlinkOutgoing(draft, key); flow.npc().getAsJsonArray("rules").remove(flow.ruleIndex());
    }
}
