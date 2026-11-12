package com.cdp.codpattern.app.zombies.npc;

import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/** Immutable authoring data. Runtime progress is deliberately kept outside this file. */
public record ZombiesNpcConfig(int version, List<Npc> npcs, List<ZombiesNpcGraph.Node> looseNodes, Boolean debug) {
    public ZombiesNpcConfig(int version,List<Npc> npcs){this(version,npcs,List.of(),false);}
    public ZombiesNpcConfig(int version,List<Npc> npcs,List<ZombiesNpcGraph.Node> looseNodes){this(version,npcs,looseNodes,false);}
    public static final double DEFAULT_INTERVAL_SECONDS = 1;
    public ZombiesNpcConfig {
        debug=debug==null?false:debug;
        if (version != 1) throw new IllegalArgumentException("NPC version must be 1");
        npcs = List.copyOf(npcs);
        looseNodes=looseNodes==null?List.of():List.copyOf(looseNodes);
        if(looseNodes.size()>128 || looseNodes.stream().anyMatch(n->n.type()==ZombiesNpcGraph.Type.ENTRY))throw new IllegalArgumentException("未归属节点最多 128 个，不能包含事件入口");
        Set<String> looseIds=new java.util.HashSet<>();
        for(var node:looseNodes)if(!looseIds.add(node.id()))throw new IllegalArgumentException("未归属节点编号重复");
        for(var node:looseNodes)for(var next:node.outputs())if(!next.isBlank()&&!looseIds.contains(next))throw new IllegalArgumentException("未归属连线目标不存在");
        if (npcs.size() > 128) throw new IllegalArgumentException("At most 128 NPCs per map");
        Set<String> keys = new java.util.HashSet<>();
        for (Npc npc : npcs) {
            requireId(npc.id());
            if (!keys.add(npc.id())) throw new IllegalArgumentException("Duplicate NPC " + npc.id());
            Set<String> rules = new java.util.HashSet<>();
            for (Rule rule : npc.rules()) {
                requireId(rule.id());
                if (!rules.add(rule.id())) throw new IllegalArgumentException("Duplicate rule " + rule.id());
            }
        }
        Set<String> ruleKeys = new java.util.HashSet<>();
        for (Npc npc : npcs) for (Rule rule : npc.rules()) ruleKeys.add(npc.id() + "." + rule.id());
        for (Npc npc : npcs) for (Rule rule : npc.rules()) {
            if (!ruleKeys.containsAll(rule.requires())) throw new IllegalArgumentException("Unknown prerequisite in " + rule.id());
            if (rule.graph() != null) for (var node : rule.graph().nodes()) if (!node.speaker().isBlank() && !keys.contains(node.speaker())) throw new IllegalArgumentException("对话引用未知 NPC：" + node.speaker());
            for (Action action : rule.executableActions()) {
                if (action.target() != null && !keys.contains(action.target())) throw new IllegalArgumentException("Unknown NPC target");
            }
            if (rule.graph() != null) for (var node : rule.graph().nodes()) for (var condition : node.conditions()) for (var test : condition.tests()) {
                if (test.kind() == ZombiesNpcGraph.TestKind.RULE_DONE && !ruleKeys.contains(test.reference())) throw new IllegalArgumentException("条件引用未知规则：" + test.reference());
                if (test.kind() == ZombiesNpcGraph.TestKind.NPC_ALIVE && !keys.contains(test.reference())) throw new IllegalArgumentException("条件引用未知 NPC：" + test.reference());
                if (rule.trigger() == Trigger.AUTO && test.kind() == ZombiesNpcGraph.TestKind.ITEM_ACTOR) throw new IllegalArgumentException("交互玩家物品条件只能用于玩家交互入口");
            }
        }
        Set<String> reachable = new java.util.HashSet<>();
        int previousSize;
        do {
            previousSize = reachable.size();
            for (Npc npc : npcs) for (Rule rule : npc.rules()) {
                if (reachable.containsAll(rule.requires())) reachable.add(npc.id() + "." + rule.id());
            }
        } while (reachable.size() > previousSize);
        if (!reachable.containsAll(ruleKeys)) throw new IllegalArgumentException("NPC rules contain circular prerequisites");
    }

    public static ZombiesNpcConfig empty() { return new ZombiesNpcConfig(1, List.of()); }

    private static void requireId(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("IDs use letters, digits, _ and - (1-64 characters)");
    }

    public enum Pose { STANDING, SITTING, LYING, GROUND_SITTING, SUPINE, KNEELING }
    public enum Trigger { INTERACT, AUTO }
    public enum Kind { NPC, DIALOGUE, TAKE_ITEM, GIVE_ITEM, SET_BLOCK, OPEN_BARRIER, MAP_EVENT }

    public record Position(double x, double y, double z, float yaw) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
                throw new IllegalArgumentException("NPC coordinates must be finite");
            }
        }
    }

    public enum CombatMode { OFF, GUARD, FOLLOW }
    public record Combat(CombatMode mode,String gun,double range){
        public Combat { mode=mode==null?CombatMode.OFF:mode;gun=gun==null?"":gun;
            if(!Double.isFinite(range)||range<4||range>64)throw new IllegalArgumentException("作战射程为 4 至 64 格");
            if(mode==CombatMode.GUARD&&gun.isBlank())throw new IllegalArgumentException("请先读取 NPC 使用的 TaCZ 枪械");
        }
    }
    public record Appearance(String name, String skin, Pose pose, Boolean alive, Position position, Boolean invulnerable,
                             String chatName, String nameColor, String textColor, Combat combat) {
        public Appearance(String name,String skin,Pose pose,Boolean alive,Position position,Boolean invulnerable,String chatName,String nameColor,String textColor){this(name,skin,pose,alive,position,invulnerable,chatName,nameColor,textColor,null);}
        public Appearance(String name, String skin, Pose pose, Boolean alive, Position position, Boolean invulnerable) {
            this(name, skin, pose, alive, position, invulnerable, null, null, null);
        }
        public Appearance(String name, String skin, Pose pose, Boolean alive, Position position) {
            this(name, skin, pose, alive, position, null, null, null, null);
        }
        public Appearance {
            nameColor = normalizeColor(nameColor);
            textColor = normalizeColor(textColor);
        }
    }

    public record Npc(String id, Appearance appearance, String dialogue, List<Rule> rules, double dialogueIntervalSeconds) {
        public Npc(String id, Appearance appearance, String dialogue, List<Rule> rules) { this(id, appearance, dialogue, rules, DEFAULT_INTERVAL_SECONDS); }
        public Npc {
            intervalTicks(dialogueIntervalSeconds);
            if (appearance == null || appearance.position() == null) throw new IllegalArgumentException("NPC needs appearance.position");
            dialogue = dialogue == null ? "" : dialogue;
            rules = rules == null ? List.of() : List.copyOf(rules);
            if (rules.size() > 128) throw new IllegalArgumentException("At most 128 rules per NPC");
        }
    }

    public record Rule(String id, Trigger trigger, int minWave, List<Integer> openedBarriers,
                       boolean anyCondition, List<String> requires, List<Action> actions,
                       double actionIntervalSeconds, String requiredItem, Integer requiredCount, ZombiesNpcGraph graph) {
        public Rule(String id, Trigger trigger, int minWave, List<Integer> openedBarriers,
                    boolean anyCondition, List<String> requires, List<Action> actions,
                    double actionIntervalSeconds, String requiredItem, Integer requiredCount) {
            this(id, trigger, minWave, openedBarriers, anyCondition, requires, actions, actionIntervalSeconds, requiredItem, requiredCount, null);
        }
        public Rule(String id, Trigger trigger, int minWave, List<Integer> openedBarriers,
                    boolean anyCondition, List<String> requires, List<Action> actions) {
            this(id, trigger, minWave, openedBarriers, anyCondition, requires, actions, DEFAULT_INTERVAL_SECONDS, "", 1);
        }
        public Rule {
            intervalTicks(actionIntervalSeconds);
            requiredItem = requiredItem == null ? "" : requiredItem;
            if (requiredCount == null) requiredCount = 1;
            if (requiredCount < 1 || requiredCount > 2304) throw new IllegalArgumentException("Invalid item condition count");
            if (trigger == null || minWave < 0) throw new IllegalArgumentException("Invalid NPC trigger");
            openedBarriers = openedBarriers == null ? List.of() : List.copyOf(openedBarriers);
            requires = requires == null ? List.of() : List.copyOf(requires);
            actions = actions == null ? List.of() : List.copyOf(actions);
            if (openedBarriers.stream().anyMatch(group -> group < 1) || (graph == null && actions.isEmpty()) || actions.size() > 64) {
                throw new IllegalArgumentException("Invalid barriers or actions");
            }
            if (graph != null && trigger == Trigger.AUTO && graph.nodes().stream().anyMatch(n -> n.type() == ZombiesNpcGraph.Type.END && !n.complete()))
                throw new IllegalArgumentException("自动规则的结束节点必须标记完成，避免每刻重复触发");
            if (graph != null && trigger == Trigger.AUTO && graph.nodes().stream().anyMatch(n -> n.type() == ZombiesNpcGraph.Type.INTERACT || n.type() == ZombiesNpcGraph.Type.CHOICE))
                throw new IllegalArgumentException("等待交互和玩家选择节点必须接入玩家交互规则");
            if (graph != null && !actions.isEmpty()) throw new IllegalArgumentException("节点流程与旧动作列表不能同时存在");
            if (trigger == Trigger.AUTO && (graph == null ? actions : graph.actions()).stream().anyMatch(action -> action.kind() == Kind.TAKE_ITEM || action.kind() == Kind.GIVE_ITEM)) {
                throw new IllegalArgumentException("Item transfers require INTERACT");
            }
        }

        public List<Action> executableActions() { return graph == null ? actions : graph.actions(); }

        public boolean eligible(int wave, IntPredicate barrierOpen, Set<String> completed) {
            if (!completed.containsAll(requires)) return false;
            boolean hasWave = minWave > 0;
            if (anyCondition) {
                return (!hasWave && openedBarriers.isEmpty()) || (hasWave && wave >= minWave)
                        || openedBarriers.stream().anyMatch(barrierOpen::test);
            }
            return (!hasWave || wave >= minWave) && openedBarriers.stream().allMatch(barrierOpen::test);
        }
    }

    /** Only the fields used by kind are required. NPC patches retain unspecified properties. */
    public record Action(Kind kind, String target, Appearance appearance, String text,
                         String item, int count, Position position, String block, int barrierGroup, String dialogue,
                         Double delaySeconds, Double dialogueIntervalSeconds, List<BlockPlacement> placements) {
        public Action(Kind kind, String target, Appearance appearance, String text,
                      String item, int count, Position position, String block, int barrierGroup, String dialogue) {
            this(kind, target, appearance, text, item, count, position, block, barrierGroup, dialogue, null, null, List.of());
        }
        public Action(Kind kind, String target, Appearance appearance, String text,
                      String item, int count, Position position, String block, int barrierGroup) {
            this(kind, target, appearance, text, item, count, position, block, barrierGroup, null, null, null, List.of());
        }
        public Action {
            if (delaySeconds != null) intervalTicks(delaySeconds);
            if (dialogueIntervalSeconds != null) intervalTicks(dialogueIntervalSeconds);
            placements = placements == null ? List.of() : List.copyOf(placements);
            if (placements.size() > 256) throw new IllegalArgumentException("At most 256 blocks per action");
            if (kind == null) throw new IllegalArgumentException("Action needs kind");
            switch (kind) {
                case MAP_EVENT -> { if(text==null||!text.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("请选择地图事件"); }
                case NPC -> { if (appearance == null && dialogue == null) throw new IllegalArgumentException("NPC action needs appearance or dialogue"); }
                case DIALOGUE -> { if (text == null) throw new IllegalArgumentException("DIALOGUE needs text"); }
                case TAKE_ITEM, GIVE_ITEM -> {
                    if (item == null || item.isBlank() || count < 1 || count > 2304) throw new IllegalArgumentException("Item action needs item and count (1-2304)");
                }
                case SET_BLOCK -> { if (placements.isEmpty() && (position == null || block == null)) throw new IllegalArgumentException("SET_BLOCK needs block placements"); }
                case OPEN_BARRIER -> { if (barrierGroup < 1) throw new IllegalArgumentException("OPEN_BARRIER needs barrierGroup"); }
            }
        }

        public List<BlockPlacement> blockPlacements() {
            return placements.isEmpty() ? List.of(new BlockPlacement(position, block)) : placements;
        }
    }

    public record BlockPlacement(Position position, String block) {
        public BlockPlacement {
            if (position == null || block == null || block.isBlank()) throw new IllegalArgumentException("Invalid block placement");
        }
    }

    public static int intervalTicks(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || seconds > 3600) throw new IllegalArgumentException("Interval must be between 0 and 3600 seconds");
        return (int) Math.ceil(seconds * 20);
    }

    public static String normalizeColor(String color) {
        if (color == null) return null;
        String normalized = color.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) return "#ffffff";
        if (!normalized.matches("#[0-9a-f]{6}") && !Set.of("black", "dark_blue", "dark_green", "dark_aqua",
                "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white").contains(normalized)) {
            throw new IllegalArgumentException("Chat color must be a named color or #RRGGBB");
        }
        return normalized;
    }
}
