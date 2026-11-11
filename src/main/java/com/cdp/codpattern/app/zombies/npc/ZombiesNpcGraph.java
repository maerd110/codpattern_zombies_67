package com.cdp.codpattern.app.zombies.npc;

import java.util.*;

/** Saved execution graph. The same edges drawn by the editor drive the server interpreter. */
public record ZombiesNpcGraph(List<Node> nodes, Integer canvasVersion) {
    public ZombiesNpcGraph(List<Node> nodes) { this(nodes, 0); }
    public enum Type { ENTRY, ACTION, BRANCH, WAIT, AWAIT, INTERACT, CHOICE, END }
    public enum TestKind { WAVE, BARRIER, ITEM_ROOM, ITEM_ACTOR, RULE_DONE, NPC_ALIVE }
    public enum Match { ALL, ANY }
    public record Test(TestKind kind, int value, String item, Integer count, String reference, boolean negated) {
        public Test {
            if (kind == null) throw new IllegalArgumentException("条件缺少类型");
            item = item == null ? "" : item;
            reference = reference == null ? "" : reference;
            count = count == null ? 1 : count;
            if (count < 1 || count > 2304 || value < 0) throw new IllegalArgumentException("条件数量或数值无效");
            if (kind == TestKind.BARRIER && value < 1) throw new IllegalArgumentException("屏障分组必须大于 0");
            if ((kind == TestKind.ITEM_ROOM || kind == TestKind.ITEM_ACTOR) && item.isBlank()) throw new IllegalArgumentException("物品条件需要选择物品");
            if ((kind == TestKind.RULE_DONE || kind == TestKind.NPC_ALIVE) && reference.isBlank()) throw new IllegalArgumentException("条件需要选择目标");
        }
    }
    public record Condition(Match match, List<Test> tests) {
        public Condition {
            if (match == null || tests == null || tests.isEmpty() || tests.size() > 16) throw new IllegalArgumentException("条件节点需要 1 至 16 个条件");
            tests = List.copyOf(tests);
        }
        public boolean evaluate(java.util.function.Predicate<Test> evaluator) {
            var stream = tests.stream();
            java.util.function.Predicate<Test> check = test -> evaluator.test(test) != test.negated();
            return match == Match.ALL ? stream.allMatch(check) : stream.anyMatch(check);
        }
    }
    public record Option(String id, String text, Condition condition, String next) {
        public Option {
            if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}") || text == null || text.isBlank() || text.length() > 256)
                throw new IllegalArgumentException("对话选项需要编号和 1 至 256 字的按钮文字");
            next = next == null ? "" : next;
        }
    }
    public record Node(String id, Type type, double x, double y, ZombiesNpcConfig.Action action,
                       Condition condition, double seconds, String next, String onFalse, Boolean complete,
                       String text, String speaker, List<Option> options) {
        public Node(String id, Type type, double x, double y, ZombiesNpcConfig.Action action, Condition condition, double seconds, String next, String onFalse, Boolean complete) {
            this(id,type,x,y,action,condition,seconds,next,onFalse,complete,"","",List.of());
        }
        public Node(String id, Type type, double x, double y, ZombiesNpcConfig.Action action, Condition condition, double seconds, String next, String onFalse) {
            this(id,type,x,y,action,condition,seconds,next,onFalse,true);
        }
        public Node {
            text = text == null ? "" : text;
            speaker = speaker == null ? "" : speaker;
            options = options == null ? List.of() : List.copyOf(options);
            if (text.length() > 8192) throw new IllegalArgumentException("对话正文最多 8192 字");
            if (type == Type.CHOICE) {
                if (options.isEmpty() || options.size() > 12 || options.stream().map(Option::id).distinct().count() != options.size())
                    throw new IllegalArgumentException("玩家选择节点需要 1 至 12 个编号不同的选项");
            } else if (!options.isEmpty() || !text.isEmpty() || !speaker.isEmpty()) throw new IllegalArgumentException("此节点不支持对话选项");
            complete = complete == null ? true : complete;
            if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}") || type == null) throw new IllegalArgumentException("节点编号或类型无效");
            if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 100000 || Math.abs(y) > 100000) throw new IllegalArgumentException("节点位置无效");
            ZombiesNpcConfig.intervalTicks(seconds);
            next = next == null ? "" : next;
            onFalse = onFalse == null ? "" : onFalse;
            if (type == Type.ACTION && action == null) throw new IllegalArgumentException("动作节点缺少动作参数");
            if ((type == Type.BRANCH || type == Type.AWAIT) && condition == null) throw new IllegalArgumentException("判断节点缺少条件");
            if (type != Type.ACTION && action != null || type != Type.BRANCH && type != Type.AWAIT && condition != null) throw new IllegalArgumentException("节点参数与类型不符");
        }
        public List<String> outputs() {
            return type == Type.CHOICE ? options.stream().map(Option::next).toList() : type == Type.END ? List.of() : type == Type.BRANCH ? List.of(next, onFalse) : List.of(next);
        }
        public List<Condition> conditions() {
            var result = new ArrayList<Condition>();
            if (condition != null) result.add(condition);
            for (var option : options) if (option.condition() != null) result.add(option.condition());
            return List.copyOf(result);
        }
    }
    public ZombiesNpcGraph {
        canvasVersion = canvasVersion == null ? 0 : canvasVersion;
        if (canvasVersion < 0 || canvasVersion > 1) throw new IllegalArgumentException("未知画布布局版本");
        if (nodes == null || nodes.isEmpty() || nodes.size() > 128) throw new IllegalArgumentException("每张流程图需要 1 至 128 个节点");
        nodes = List.copyOf(nodes);
        Map<String, Node> byId = new LinkedHashMap<>();
        for (var node : nodes) if (byId.put(node.id(), node) != null) throw new IllegalArgumentException("节点编号重复：" + node.id());
        var entries = nodes.stream().filter(n -> n.type() == Type.ENTRY).toList();
        if (entries.size() != 1) throw new IllegalArgumentException("每条规则需要且只能有一个事件入口");
        if (nodes.stream().filter(n -> n.type() == Type.ACTION).count() > 64) throw new IllegalArgumentException("每条规则最多 64 个动作节点");
        for (var node : nodes) {
            if ((node.type() == Type.END || node.type() == Type.CHOICE) && !node.next().isEmpty() || node.type() != Type.BRANCH && !node.onFalse().isEmpty()) throw new IllegalArgumentException("节点存在不支持的出口：" + node.id());
            for (String target : node.outputs()) {
                if (!byId.containsKey(target)) throw new IllegalArgumentException("请连接节点的所有出口：" + node.id());

            }
        }
        Map<String, Integer> colors = new HashMap<>();
        visit(entries.get(0).id(), byId, colors);
        if (colors.size() != nodes.size()) throw new IllegalArgumentException("存在未连接到事件入口的节点，请连接或删除后保存");
    }
    private static void visit(String id, Map<String, Node> nodes, Map<String, Integer> colors) {
        int color = colors.getOrDefault(id, 0);
        if (color == 1) throw new IllegalArgumentException("连线形成循环，请使用等待条件节点等待事件");
        if (color == 2) return;
        colors.put(id, 1);
        for (String next : nodes.get(id).outputs()) if(nodes.get(next).type()!=Type.ENTRY) visit(next, nodes, colors);
        colors.put(id, 2);
    }
    public Node entry() { return nodes.stream().filter(n -> n.type() == Type.ENTRY).findFirst().orElseThrow(); }
    public List<ZombiesNpcConfig.Action> actions() { return nodes.stream().filter(n -> n.type() == Type.ACTION).map(Node::action).toList(); }
}
