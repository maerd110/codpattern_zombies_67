package com.cdp.codpattern.app.zombies.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/** A local working copy shared by the NPC, rule and action pages. */
public final class ZombiesNpcEditorDraft {
    private JsonObject data;
    private String saved;
    public ZombiesNpcEditorDraft(ZombiesNpcConfig config) { accept(config); }
    public void accept(ZombiesNpcConfig config) {
        data = ZombiesNpcRepository.GSON.toJsonTree(config).getAsJsonObject();
        saved = data.toString();
    }
    public boolean dirty() { return !saved.equals(data.toString()); }
    public String snapshot() { return data.toString(); }
    public void restoreWorking(String snapshot) { data = com.google.gson.JsonParser.parseString(snapshot).getAsJsonObject(); }
    public JsonArray npcs() { return data.getAsJsonArray("npcs"); }
    public ZombiesNpcConfig validated() { return ZombiesNpcRepository.GSON.fromJson(data, ZombiesNpcConfig.class); }
    public String request(String revision) {
        JsonObject result = new JsonObject();
        result.addProperty("revision", revision);
        result.add("config", ZombiesNpcRepository.GSON.toJsonTree(validated()));
        return result.toString();
    }
    public JsonObject addNpc(ZombiesNpcConfig.Position position) {
        String id = unique(npcs(), "npc_");
        var npc = new ZombiesNpcConfig.Npc(id, new ZombiesNpcConfig.Appearance(id,
                "minecraft:textures/entity/player/wide/steve.png", ZombiesNpcConfig.Pose.STANDING, true, position), "", List.of());
        JsonObject object = ZombiesNpcRepository.GSON.toJsonTree(npc).getAsJsonObject();
        npcs().add(object);
        return object;
    }
    public JsonObject addRule(JsonObject npc) {
        JsonArray rules = npc.getAsJsonArray("rules");
        JsonObject rule = new JsonObject();
        rule.addProperty("id", unique(rules, "rule_"));
        rule.addProperty("trigger", "INTERACT");
        rule.addProperty("minWave", 0);
        rule.addProperty("actionIntervalSeconds", ZombiesNpcConfig.DEFAULT_INTERVAL_SECONDS);
        rule.addProperty("anyCondition", false);
        rule.add("openedBarriers", new JsonArray());
        rule.add("requires", new JsonArray());
        rule.add("actions", new JsonArray());
        addAction(rule, ZombiesNpcConfig.Kind.DIALOGUE, new ZombiesNpcConfig.Position(0, 64, 0, 0));
        rules.add(rule);
        return rule;
    }
    public JsonObject addAction(JsonObject rule, ZombiesNpcConfig.Kind kind, ZombiesNpcConfig.Position position) {
        JsonObject action = new JsonObject();
        setKind(action, kind, position);
        rule.getAsJsonArray("actions").add(action);
        return action;
    }
    public static void setKind(JsonObject action, ZombiesNpcConfig.Kind kind, ZombiesNpcConfig.Position position) {
        action.addProperty("kind", kind.name());
        switch (kind) {
            case NPC -> { if (!action.has("appearance")) action.add("appearance", new JsonObject()); }
            case DIALOGUE -> defaultValue(action, "text", "");
            case TAKE_ITEM, GIVE_ITEM -> { defaultValue(action, "item", "minecraft:emerald"); if (!action.has("count")) action.addProperty("count", 1); }
            case SET_BLOCK -> {
                if (!action.has("position")) action.add("position", position(position));
                defaultValue(action, "block", "minecraft:stone");
            }
            case OPEN_BARRIER -> { if (!action.has("barrierGroup")) action.addProperty("barrierGroup", 1); }
        }
    }
    public static JsonObject position(ZombiesNpcConfig.Position position) {
        return ZombiesNpcRepository.GSON.toJsonTree(position).getAsJsonObject();
    }
    private static void defaultValue(JsonObject object, String key, String value) { if (!object.has(key)) object.addProperty(key, value); }
    private static String unique(JsonArray objects, String prefix) {
        var ids = new java.util.HashSet<String>();
        for (JsonElement element : objects) ids.add(element.getAsJsonObject().get("id").getAsString());
        int index = 1;
        while (ids.contains(prefix + index)) index++;
        return prefix + index;
    }
    public void renameNpc(JsonObject npc, String name) {
        String old = npc.get("id").getAsString();
        npc.addProperty("id", name);
        for (JsonElement element : npcs()) for (JsonElement rule : element.getAsJsonObject().getAsJsonArray("rules")) {
            var object = rule.getAsJsonObject();
            JsonArray requirements = object.getAsJsonArray("requires");
            for (int i = 0; i < requirements.size(); i++) {
                String value = requirements.get(i).getAsString();
                if (value.startsWith(old + ".")) requirements.set(i, new com.google.gson.JsonPrimitive(name + value.substring(old.length())));
            }
            ZombiesNpcGraphDraft.references(object, value -> value.equals(old) ? name : value,
                    value -> value.startsWith(old + ".") ? name + value.substring(old.length()) : value);
        }
    }
    public void renameRule(JsonObject npc, JsonObject rule, String name) {
        String prefix = npc.get("id").getAsString() + ".";
        String previous = prefix + rule.get("id").getAsString();
        rule.addProperty("id", name);
        for (JsonElement element : npcs()) for (JsonElement candidate : element.getAsJsonObject().getAsJsonArray("rules")) {
            JsonArray requirements = candidate.getAsJsonObject().getAsJsonArray("requires");
            for (int i = 0; i < requirements.size(); i++) if (requirements.get(i).getAsString().equals(previous)) {
                requirements.set(i, new com.google.gson.JsonPrimitive(prefix + name));
            }
            ZombiesNpcGraphDraft.references(candidate.getAsJsonObject(), value -> value, value -> value.equals(previous) ? prefix + name : value);
        }
    }
}
