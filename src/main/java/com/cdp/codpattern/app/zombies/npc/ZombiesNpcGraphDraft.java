package com.cdp.codpattern.app.zombies.npc;

import com.google.gson.*;
import java.util.*;

/** Mutable editor operations allow incomplete wiring; saving uses the strict graph validator. */
public final class ZombiesNpcGraphDraft {
    private ZombiesNpcGraphDraft() { }
    public static JsonArray nodes(JsonObject rule) { return rule.getAsJsonObject("graph").getAsJsonArray("nodes"); }
    public static JsonObject find(JsonObject rule, String id) {
        for (var node : nodes(rule)) if (node.getAsJsonObject().get("id").getAsString().equals(id)) return node.getAsJsonObject();
        return null;
    }
    public static void ensure(JsonObject rule) {
        if (rule.has("graph") && !rule.get("graph").isJsonNull()) return;
        JsonObject graph = new JsonObject(); graph.add("nodes", new JsonArray()); rule.add("graph", graph);
        var entry = add(rule, ZombiesNpcGraph.Type.ENTRY, 20, 80, null);
        JsonObject previous = entry;
        int column = 1;
        for (var action : rule.getAsJsonArray("actions")) {
            var node = add(rule, ZombiesNpcGraph.Type.ACTION, 20 + column++ * 210, 80, action.getAsJsonObject().deepCopy());
            previous.addProperty("next", node.get("id").getAsString()); previous = node;
        }
        var end = add(rule, ZombiesNpcGraph.Type.END, 20 + column * 210, 80, null);
        previous.addProperty("next", end.get("id").getAsString());
        rule.add("actions", new JsonArray());
    }
    public static JsonObject add(JsonObject rule, ZombiesNpcGraph.Type type, double x, double y, JsonObject action) {
        if (nodes(rule).size() >= 128) throw new IllegalArgumentException("每条规则最多 128 个节点");
        int index = 1; while (find(rule, "node_" + index) != null) index++;
        JsonObject node = new JsonObject();
        node.addProperty("id", "node_" + index); node.addProperty("type", type.name());
        node.addProperty("x", x); node.addProperty("y", y); node.addProperty("seconds", 1);
        node.addProperty("next", ""); node.addProperty("onFalse", "");
        if (type == ZombiesNpcGraph.Type.ACTION) node.add("action", Objects.requireNonNull(action));
        if (type == ZombiesNpcGraph.Type.BRANCH || type == ZombiesNpcGraph.Type.AWAIT) {
            JsonObject condition = new JsonObject(); condition.addProperty("match", "ALL");
            JsonArray tests = new JsonArray(); tests.add(test()); condition.add("tests", tests); node.add("condition", condition);
        }
        if (type == ZombiesNpcGraph.Type.CHOICE) {
            node.add("segments",new JsonArray());
            node.addProperty("text", ""); node.addProperty("speaker", "");
            var options = new JsonArray(); options.add(option(1)); node.add("options", options);
        }
        nodes(rule).add(node); return node;
    }
    public static JsonObject test() {
        JsonObject test = new JsonObject(); test.addProperty("kind", "WAVE"); test.addProperty("value", 1);
        test.addProperty("item", "minecraft:emerald"); test.addProperty("count", 1); test.addProperty("reference", ""); test.addProperty("negated", false);
        return test;
    }
    public static void connect(JsonObject rule, String from, String port, String to) {
        var source = find(rule, from); var target = find(rule, to);
        if (source == null || target == null || from.equals(to)&&!source.get("type").getAsString().equals("CHOICE")) throw new IllegalArgumentException("这两个节点不能相连");
        if (!ports(source).contains(port)) throw new IllegalArgumentException("请选择有效出口");
        if (!source.get("type").getAsString().equals("CHOICE")&&!target.get("type").getAsString().equals("ENTRY") && reaches(rule, to, from, new HashSet<>())) throw new IllegalArgumentException("自动执行连线不能形成循环；返回对话请使用玩家选择出口");
        target(source, port, to);
        if(port.startsWith("option:")){var option=portObject(source,port);if(from.equals(to))option.addProperty("closeOnSelect",true);else if(!target.get("type").getAsString().equals("CHOICE"))option.addProperty("closeOnSelect",false);}
    }
    private static boolean reaches(JsonObject rule, String start, String target, Set<String> visited) {
        if (start.equals(target)) return true;
        if (!visited.add(start)) return false;
        var node = find(rule, start); if (node == null) return false;
        if(node.get("type").getAsString().equals("CHOICE"))return false;
        for (String port : ports(node)) {var next=find(rule,target(node,port));if(next!=null && !next.get("type").getAsString().equals("ENTRY") && reaches(rule,target(node,port),target,visited))return true;}
        return false;
    }
    public static List<String> ports(JsonObject node) {
        return switch (node.get("type").getAsString()) { case "CHOICE" -> node.getAsJsonArray("options").asList().stream().map(o -> "option:"+o.getAsJsonObject().get("id").getAsString()).toList(); case "END" -> List.of(); case "BRANCH" -> List.of("next", "onFalse"); default -> List.of("next"); };
    }
    public static JsonObject option(int index) {
        var o = new JsonObject(); o.addProperty("id", "choice_"+index); o.addProperty("text", "选项 "+index); o.addProperty("next", ""); return o;
    }
    public static JsonObject portObject(JsonObject node, String port) {
        if (!port.startsWith("option:")) return node;
        return node.getAsJsonArray("options").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(o -> o.get("id").getAsString().equals(port.substring(7))).findFirst().orElseThrow();
    }
    public static String target(JsonObject node, String port) {
        var o=portObject(node,port); String k=port.startsWith("option:")?"next":port;
        return o.has(k)?o.get(k).getAsString():"";
    }
    public static void target(JsonObject node, String port, String next) { portObject(node,port).addProperty(port.startsWith("option:")?"next":port,next); }
    public static List<JsonObject> conditions(JsonObject node) {
        var result=new ArrayList<JsonObject>();
        if (node.has("condition") && !node.get("condition").isJsonNull()) result.add(node.getAsJsonObject("condition"));
        if (node.has("options")) for(var o:node.getAsJsonArray("options")) if(o.getAsJsonObject().has("condition") && !o.getAsJsonObject().get("condition").isJsonNull()) result.add(o.getAsJsonObject().getAsJsonObject("condition"));
        return result;
    }
    public static void delete(JsonObject rule, String id) {
        var node = find(rule, id); if (node == null || node.get("type").getAsString().equals("ENTRY")) return;
        nodes(rule).remove(node);
        for (var element : nodes(rule)) for (String port : ports(element.getAsJsonObject())) {
            if (target(element.getAsJsonObject(),port).equals(id)) target(element.getAsJsonObject(),port, "");
        }
    }
    public static void layout(JsonObject rule) {
        Map<String, Integer> depth = new HashMap<>();
        var entries = nodes(rule).asList().stream().map(JsonElement::getAsJsonObject).filter(n -> n.get("type").getAsString().equals("ENTRY")).toList();
        if (entries.isEmpty()) return;
        Set<String> returns=new HashSet<>();layoutReturns(rule,entries.get(0).get("id").getAsString(),new HashMap<>(),returns);
        depth.put(entries.get(0).get("id").getAsString(), 0);
        for (int pass = 0; pass < nodes(rule).size(); pass++) {
            boolean changed = false;
            for (var element : nodes(rule)) {
                var node = element.getAsJsonObject(); var d = depth.get(node.get("id").getAsString()); if (d == null) continue;
                for (String port : ports(node)) {
                    String target = target(node,port);
                    if (!returns.contains(node.get("id").getAsString()+"/"+port) && !target.isBlank() && find(rule,target)!=null && !find(rule,target).get("type").getAsString().equals("ENTRY") && depth.getOrDefault(target, -1) < d + 1) { depth.put(target, d + 1); changed = true; }
                }
            }
            if (!changed) break;
        }
        Map<Integer, Integer> rows = new HashMap<>();
        for (var element : nodes(rule)) {
            var node = element.getAsJsonObject(); int d = depth.getOrDefault(node.get("id").getAsString(), 0);
            int row = rows.getOrDefault(d, 0);
            int height=node.get("type").getAsString().equals("CHOICE")?Math.max(88,61+node.getAsJsonArray("options").size()*22):88;
            rows.put(d,row+height+37);
            node.addProperty("x", 20 + d * 210); node.addProperty("y", 30 + row);
        }
    }
    private static void layoutReturns(JsonObject rule,String id,Map<String,Integer> colors,Set<String> returns){
        if(colors.containsKey(id))return;var node=find(rule,id);if(node==null)return;colors.put(id,1);
        for(String port:ports(node)){String target=target(node,port);if(colors.getOrDefault(target,0)==1)returns.add(id+"/"+port);else layoutReturns(rule,target,colors,returns);}
        colors.put(id,2);
    }
    public static List<JsonObject> actions(JsonObject rule) {
        if (rule.has("graph") && !rule.get("graph").isJsonNull()) return nodes(rule).asList().stream().map(JsonElement::getAsJsonObject)
                .filter(n -> n.get("type").getAsString().equals("ACTION")).map(n -> n.getAsJsonObject("action")).toList();
        return rule.getAsJsonArray("actions").asList().stream().map(JsonElement::getAsJsonObject).toList();
    }
    public static boolean usesActor(JsonObject rule) {
        if (actions(rule).stream().anyMatch(a -> Set.of("GIVE_ITEM", "TAKE_ITEM").contains(a.get("kind").getAsString()))) return true;
        if (rule.has("graph") && !rule.get("graph").isJsonNull()) for (var element : nodes(rule)) {
            var n = element.getAsJsonObject(); if (Set.of("INTERACT","CHOICE").contains(n.get("type").getAsString())) return true;
            for (var c : conditions(n)) for (var t : c.getAsJsonArray("tests")) if (t.getAsJsonObject().get("kind").getAsString().equals("ITEM_ACTOR")) return true;
        }
        return false;
    }
    public static void references(JsonObject rule, java.util.function.UnaryOperator<String> npc, java.util.function.UnaryOperator<String> key) {
        for (var action : actions(rule)) if (action.has("target")) action.addProperty("target", npc.apply(action.get("target").getAsString()));
        if (!rule.has("graph") || rule.get("graph").isJsonNull()) return;
        for (var element : nodes(rule)) {
            var node = element.getAsJsonObject(); if (node.has("speaker") && !node.get("speaker").getAsString().isBlank()) node.addProperty("speaker",npc.apply(node.get("speaker").getAsString()));
            if(node.has("segments"))for(var segment:node.getAsJsonArray("segments")){var s=segment.getAsJsonObject();if(s.has("speaker")&&!s.get("speaker").getAsString().isBlank())s.addProperty("speaker",npc.apply(s.get("speaker").getAsString()));}
            for (var c : conditions(node)) for (var value : c.getAsJsonArray("tests")) {
                var test = value.getAsJsonObject(); String kind = test.get("kind").getAsString();
                if (kind.equals("NPC_ALIVE")) test.addProperty("reference", npc.apply(test.get("reference").getAsString()));
                if (kind.equals("RULE_DONE")) test.addProperty("reference", key.apply(test.get("reference").getAsString()));
            }
        }
    }
}
