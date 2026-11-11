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
        public String key() { return flow.key() + "/" + node.get("id").getAsString(); }
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
        double bottom = flows.stream().filter(f -> version(f) == 1).flatMap(f -> f.nodes().stream()).mapToDouble(n -> n.get("y").getAsDouble() + 88).max().orElse(0);
        for (var flow : flows) if (version(flow) == 0) {
            var nodes = flow.nodes();
            double left = nodes.stream().mapToDouble(n -> n.get("x").getAsDouble()).min().orElse(0);
            double top = nodes.stream().mapToDouble(n -> n.get("y").getAsDouble()).min().orElse(0);
            translate(flow, 40 - left, bottom + 90 - top);
            flow.rule().getAsJsonObject("graph").addProperty("canvasVersion", 1);
            bottom = nodes.stream().mapToDouble(n -> n.get("y").getAsDouble() + 88).max().orElse(bottom);
        }
    }
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
            bottom = flow.nodes().stream().mapToDouble(n -> n.get("y").getAsDouble() + 88).max().orElse(bottom);
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
