package com.cdp.codpattern.app.zombies.npc;

import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/** Immutable authoring data. Runtime progress is deliberately kept outside this file. */
public record ZombiesNpcConfig(int version, List<Npc> npcs) {
    public ZombiesNpcConfig {
        if (version != 1) throw new IllegalArgumentException("NPC version must be 1");
        npcs = List.copyOf(npcs);
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
            for (Action action : rule.actions()) {
                if (action.target() != null && !keys.contains(action.target())) throw new IllegalArgumentException("Unknown NPC target");
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

    public enum Pose { STANDING, SITTING, LYING }
    public enum Trigger { INTERACT, AUTO }
    public enum Kind { NPC, DIALOGUE, TAKE_ITEM, GIVE_ITEM, SET_BLOCK, OPEN_BARRIER }

    public record Position(double x, double y, double z, float yaw) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
                throw new IllegalArgumentException("NPC coordinates must be finite");
            }
        }
    }

    public record Appearance(String name, String skin, Pose pose, Boolean alive, Position position) { }

    public record Npc(String id, Appearance appearance, String dialogue, List<Rule> rules) {
        public Npc {
            if (appearance == null || appearance.position() == null) throw new IllegalArgumentException("NPC needs appearance.position");
            dialogue = dialogue == null ? "" : dialogue;
            rules = rules == null ? List.of() : List.copyOf(rules);
            if (rules.size() > 128) throw new IllegalArgumentException("At most 128 rules per NPC");
        }
    }

    public record Rule(String id, Trigger trigger, int minWave, List<Integer> openedBarriers,
                       boolean anyCondition, List<String> requires, List<Action> actions) {
        public Rule {
            if (trigger == null || minWave < 0) throw new IllegalArgumentException("Invalid NPC trigger");
            openedBarriers = openedBarriers == null ? List.of() : List.copyOf(openedBarriers);
            requires = requires == null ? List.of() : List.copyOf(requires);
            actions = actions == null ? List.of() : List.copyOf(actions);
            if (openedBarriers.stream().anyMatch(group -> group < 1) || actions.isEmpty() || actions.size() > 64) {
                throw new IllegalArgumentException("Invalid barriers or actions");
            }
            if (trigger == Trigger.AUTO && actions.stream().anyMatch(action -> action.kind() == Kind.TAKE_ITEM || action.kind() == Kind.GIVE_ITEM)) {
                throw new IllegalArgumentException("Item transfers require INTERACT");
            }
        }

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
                         String item, int count, Position position, String block, int barrierGroup, String dialogue) {
        public Action(Kind kind, String target, Appearance appearance, String text,
                      String item, int count, Position position, String block, int barrierGroup) {
            this(kind, target, appearance, text, item, count, position, block, barrierGroup, null);
        }
        public Action {
            if (kind == null) throw new IllegalArgumentException("Action needs kind");
            switch (kind) {
                case NPC -> { if (appearance == null && dialogue == null) throw new IllegalArgumentException("NPC action needs appearance or dialogue"); }
                case DIALOGUE -> { if (text == null) throw new IllegalArgumentException("DIALOGUE needs text"); }
                case TAKE_ITEM, GIVE_ITEM -> {
                    if (item == null || item.isBlank() || count < 1 || count > 2304) throw new IllegalArgumentException("Item action needs item and count (1-2304)");
                }
                case SET_BLOCK -> { if (position == null || block == null) throw new IllegalArgumentException("SET_BLOCK needs position and block"); }
                case OPEN_BARRIER -> { if (barrierGroup < 1) throw new IllegalArgumentException("OPEN_BARRIER needs barrierGroup"); }
            }
        }
    }
}
