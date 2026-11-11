package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import com.cdp.codpattern.app.zombies.npc.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.*;

/** Pan/zoom canvas; execution ports, not visual proximity, define the saved program. */
public final class ZombiesNpcGraphScreen extends Screen {
    private static final int NW = 170, NH = 88;
    private final ZombiesNpcDeployScreen owner;
    private String selected = "", source = "", port = "", message = "";
    private double panX = 15, panY = 25, zoom = .8;
    private int canvasRight, canvasBottom, inspectorX, inspectorWidth, inspectorScroll, testIndex;
    private boolean overlay, needsFit = true, panning;
    private String dragging = "";
    private double lastX, lastY;
    private long lastClick;
    private record History(String data, String npc, String rule, String selected) { }
    private final Deque<History> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final Map<String, ZombiesNpcSharedCanvas.Ref> index = new LinkedHashMap<>();
    private final Map<JsonObject, ZombiesNpcSharedCanvas.Ref> contexts = new IdentityHashMap<>();
    private List<ZombiesNpcSharedCanvas.Flow> flows = List.of();
    private String draggingGroup = "";
    private final List<Consumer<Integer>> inspector = new ArrayList<>();
    private final List<Runnable> decorations = new ArrayList<>();
    private GuiGraphics graphics;
    private Button undoButton, redoButton;
    private int previousWidth, previousHeight;

    public ZombiesNpcGraphScreen(ZombiesNpcDeployScreen owner) { super(Component.translatable("gui.codpattern.zombies.npc.graph.title")); this.owner = owner; }
    private static String tr(String key, Object... args) { return Component.translatable("gui.codpattern.zombies.npc." + key, args).getString(); }
    private static String value(JsonObject obj, String key, String fallback) { return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : fallback; }
    private static double number(JsonObject obj, String key) { return obj.get(key).getAsDouble(); }
    private JsonObject rule() { return owner.graphRule(); }
    private JsonArray nodes() { return ZombiesNpcGraphDraft.nodes(rule()); }
    private String activeKey() { return rule() == null ? "" : value(owner.graphNpc(), "id", "") + "." + value(rule(), "id", ""); }
    private String qualify(String id) { return id.contains("/") ? id : activeKey() + "/" + id; }
    private JsonObject node(String id) { var ref = index.get(qualify(id)); return ref == null ? null : ref.node(); }
    private String key(JsonObject n) { return contexts.get(n).key(); }
    private ZombiesNpcSharedCanvas.Flow flow(JsonObject n) { return contexts.get(n).flow(); }
    private void activate(JsonObject n) { var f = flow(n); if(f!=null)owner.graphSelect(f.npcIndex(), f.ruleIndex()); }
    private List<String> ports(JsonObject n) { return value(n,"type","").equals("END") && Boolean.parseBoolean(value(n,"complete","true")) && flow(n)!=null ? List.of("completed") : ZombiesNpcGraphDraft.ports(n); }
    private boolean editingAutomatic(){var n=node(selected);return n!=null&&flow(n)!=null&&value(flow(n).rule(),"trigger","INTERACT").equals("AUTO");}
    private boolean editable() { return owner.graphEditable(); }
    private List<JsonObject> nodeList() { return index.values().stream().map(ZombiesNpcSharedCanvas.Ref::node).toList(); }
    void refresh() { if (minecraft != null) rebuildWidgets(); }

    @Override protected void init() {
        ZombiesNpcSharedCanvas.ensure(owner.graphDraft());
        flows = ZombiesNpcSharedCanvas.flows(owner.graphDraft()); index.clear(); contexts.clear();
        for (var f : flows) for (var n : f.nodes()) { var ref = new ZombiesNpcSharedCanvas.Ref(f,n); index.put(ref.key(),ref); contexts.put(n,ref); }
        for(var e:owner.graphDraft().looseNodes()){var n=e.getAsJsonObject();var ref=new ZombiesNpcSharedCanvas.Ref(null,n);index.put(ref.key(),ref);contexts.put(n,ref);}
        canvasRight = width >= 620 && !overlay ? width - 208 : width - 8; canvasBottom = height - 36;
        inspectorX = overlay ? 8 : canvasRight + 8; inspectorWidth = overlay ? width - 16 : 192;
        int w = (width - 26) / 6;
        String npcName = owner.graphNpc() == null ? "" : value(owner.graphNpc(), "id", "");
        String ruleName = rule() == null ? tr("no_rules") : value(rule(), "id", "");
        toolbar(0, 20, w, tr("graph.npc", npcName), this::chooseNpc, true);
        toolbar(1, 20, w, tr("graph.rule", ruleName), this::chooseRule, rule() != null);
        toolbar(2, 20, w, tr("add_rule"), () -> {
            remember(); owner.graphDraft().addRule(owner.graphNpc());
            owner.graphSelect(owner.graphNpcIndex(), owner.graphNpc().getAsJsonArray("rules").size() - 1); switchGraph();
        }, editable());
        toolbar(3, 20, w, tr("delete_rule"), () -> minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) { remember(); ZombiesNpcSharedCanvas.delete(owner.graphDraft(), activeKey()); owner.graphSelect(owner.graphNpcIndex(), 0); switchGraph(); }
            minecraft.setScreen(this);
        }, Component.literal(tr("delete_rule")), Component.literal(ruleName))), editable() && rule() != null);
        toolbar(4, 20, w, tr("save"), () -> { validate(); owner.graphSave(); }, editable());
        toolbar(5, 20, w, tr("graph.basic"), this::onClose, true);
        String[] types = {"ACTION", "BRANCH", "WAIT", "AWAIT", "END", "ENTRY"};
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            toolbar(i, 44, w, tr(type.equals("ENTRY") ? "graph.entry_settings" : "graph.add." + type),
                    () -> { if (type.equals("ENTRY")) { remember(); owner.graphEdit(this, null); } else add(type); }, editable() && (!type.equals("ENTRY") || rule() != null));
        }
        int tw = (width - 30) / 8;
        toolbar(0, 68, tw, tr("graph.fit"), this::fit, !index.isEmpty());
        toolbar(1, 68, tw, tr("shared.focus"), this::focus, rule() != null);
        toolbar(2, 68, tw, tr("graph.layout"), () -> mutate(() -> { ZombiesNpcSharedCanvas.layout(owner.graphDraft()); fit(); }), editable() && !index.isEmpty());
        toolbar(3, 68, tw, tr("graph.unlink"), () -> mutate(this::unlinkSelected), editable() && node(selected) != null);
        toolbar(4, 68, tw, tr("graph.delete"), () -> mutate(this::deleteSelected), editable() && node(selected) != null && !value(node(selected), "type", "").equals("ENTRY"));
        toolbar(5, 68, tw, tr("graph.properties"), this::properties, node(selected) != null);
        undoButton = toolbar(6, 68, tw, tr("graph.undo"), () -> history(undo, redo), editable() && !undo.isEmpty());
        redoButton = toolbar(7, 68, tw, tr("graph.redo"), () -> history(redo, undo), editable() && !redo.isEmpty());
        buildInspector();
        if ((needsFit || previousWidth != width || previousHeight != height) && !index.isEmpty()) { fit(); needsFit = false; }
        previousWidth = width; previousHeight = height;
    }
    private Button toolbar(int index, int y, int w, String text, Runnable click, boolean active) {
        return button(text, 8 + index * (w + 2), y, w, click, active);
    }
    private Button button(String text, int x, int y, int w, Runnable click, boolean active) {
        var b = addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(text, Math.max(8, w - 8))), ignored -> {
            try { click.run(); } catch (RuntimeException e) { message = detail(e); }
        }).bounds(x, y, w, 20).build()); b.active = active; b.setTooltip(Tooltip.create(Component.literal(text))); return b;
    }
    private void chooseNpc() {
        var npcs = owner.graphDraft().npcs().asList().stream().map(JsonElement::getAsJsonObject).toList();
        var ids = npcs.stream().map(n -> value(n, "id", "")).toList();
        choose(tr("graph.choose_npc"), ids, value(owner.graphNpc(), "id", ""), Function.identity(), id -> {
            owner.graphSelect(ids.indexOf(id), 0); switchGraph();
        });
    }
    private void chooseRule() {
        var ids = owner.graphNpc().getAsJsonArray("rules").asList().stream().map(n -> value(n.getAsJsonObject(), "id", "")).toList();
        choose(tr("selected_rule"), ids, value(rule(), "id", ""), Function.identity(), id -> { owner.graphSelect(owner.graphNpcIndex(), ids.indexOf(id)); switchGraph(); });
    }
    private void choose(String title, List<String> options, String selected, Function<String, String> label, Consumer<String> change) {
        minecraft.setScreen(new ZombiesNpcChoiceScreen(this, title, options, selected, label, v -> {
            try { change.accept(v); } catch (RuntimeException e) { message = detail(e); refresh(); }
        }));
    }
    private void switchGraph() {
        selected = source = dragging = ""; inspectorScroll = 0; overlay = false; message = ""; refresh();
        if (rule() != null) { selected = activeKey() + "/" + value(ZombiesNpcSharedCanvas.find(owner.graphDraft(),activeKey()).entry(),"id",""); focus(); refresh(); }
    }
    private History state() { return new History(owner.graphDraft().snapshot(), value(owner.graphNpc(),"id",""), rule() == null ? "" : value(rule(),"id",""), qualify(selected)); }
    private void remember() {
        var state = state(); if (undo.isEmpty() || !undo.peekLast().data().equals(state.data())) undo.addLast(state);
        while (undo.size() > 40) undo.removeFirst(); redo.clear();
        if (undoButton != null) undoButton.active = editable() && !undo.isEmpty();
        if (redoButton != null) redoButton.active = false;
    }
    private void mutate(Runnable change) { if (!editable()) return; remember(); change.run(); message = ""; refresh(); }
    private void history(Deque<History> from, Deque<History> to) {
        if (from.isEmpty()) return;
        to.addLast(state()); var previous = from.removeLast(); owner.graphDraft().restoreWorking(previous.data());
        int ni=0,ri=0;
        for(int i=0;i<owner.graphDraft().npcs().size();i++) if(value(owner.graphDraft().npcs().get(i).getAsJsonObject(),"id","").equals(previous.npc()))ni=i;
        var rules=owner.graphDraft().npcs().get(ni).getAsJsonObject().getAsJsonArray("rules");
        for(int i=0;i<rules.size();i++)if(value(rules.get(i).getAsJsonObject(),"id","").equals(previous.rule()))ri=i;
        owner.graphSelect(ni,ri); selected=previous.selected(); source=dragging=draggingGroup=""; refresh();
    }
    private void deleteSelected() { var n=node(selected); if(n!=null){ZombiesNpcGraphDraft.delete(flow(n)==null?ZombiesNpcSharedCanvas.looseRule(owner.graphDraft()):flow(n).rule(),value(n,"id",""));selected="";} }
    private void unlinkSelected() {
        var n=node(selected); if(n==null)return;
        for(String p:ZombiesNpcGraphDraft.ports(n))n.addProperty(p,"");
        if(value(n,"type","").equals("END")&&flow(n)!=null)ZombiesNpcSharedCanvas.unlinkOutgoing(owner.graphDraft(),flow(n).key());
    }
    private void add(String type) {
        if (type.equals("ACTION")) {
            var kinds = Arrays.stream(ZombiesNpcConfig.Kind.values()).map(Enum::name)
                    .toList();
            choose(tr("kind"), kinds, "DIALOGUE", k -> tr("kind." + k), kind -> {
                JsonObject action = new JsonObject(); var player = minecraft.player;
                var position = player == null ? new ZombiesNpcConfig.Position(0, 64, 0, 0) : new ZombiesNpcConfig.Position(player.getX(), player.getY(), player.getZ(), player.getYRot());
                ZombiesNpcEditorDraft.setKind(action, ZombiesNpcConfig.Kind.valueOf(kind), position); create(type, action);
            });
        } else create(type, null);
    }
    private void create(String type, JsonObject action) {
        mutate(() -> {
            double x = ((canvasRight - 8) / 2.0 - panX) / zoom, y = ((canvasBottom - 94) / 2.0 - panY) / zoom;
            var n = ZombiesNpcGraphDraft.add(ZombiesNpcSharedCanvas.looseRule(owner.graphDraft()), ZombiesNpcGraph.Type.valueOf(type), x, y, action);
            selected = "_loose/" + value(n, "id", ""); inspectorScroll = testIndex = 0;
        });
    }
    private void properties() {
        var n = node(selected); if (n == null) return;
        if (value(n, "type", "").equals("ACTION")) { remember(); owner.graphEdit(this, n.getAsJsonObject("action")); }
        else if (value(n, "type", "").equals("ENTRY")) { remember(); owner.graphEdit(this, null); }
        else if (width < 620) { overlay = !overlay; inspectorScroll = 0; refresh(); }
    }
    private void validate() { owner.graphDraft().validated(); message = ""; }

    private void buildInspector() {
        inspector.clear(); decorations.clear();
        if (width < 620 && !overlay) return;
        var n = node(selected);
        if (overlay) rowButton(tr("graph.canvas"), () -> { overlay = false; refresh(); }, true);
        if (n == null) { line(tr("graph.select_hint")); line(tr("graph.connect_hint")); returnDrawRows(); return; }
        activate(n);
        line(flow(n)==null?tr("loose.label"):value(flow(n).npc().getAsJsonObject("appearance"),"name",value(flow(n).npc(),"id",""))+" · "+value(flow(n).rule(),"id",""));
        line(tr("graph.type." + value(n, "type", "")) + " · " + value(n,"id",""));
        switch (value(n, "type", "")) {
            case "ENTRY" -> {
                select(tr("trigger"), List.of("INTERACT", "AUTO"), value(rule(), "trigger", "INTERACT"), v -> tr("trigger." + v), v -> {
                    if (v.equals("AUTO") && ZombiesNpcGraphDraft.usesActor(rule())) { message = tr("graph.error.actor"); return; }
                    mutate(() -> rule().addProperty("trigger", v));
                });
                field(tr("action_interval"), rule(), "actionIntervalSeconds", "1");
                rowButton(tr("graph.entry_settings"), () -> { remember(); owner.graphEdit(this, null); }, editable());
                line(tr("shared.dependencies"));
                for (var requirement : rule().getAsJsonArray("requires")) {
                    String predecessor=requirement.getAsString();
                    rowButton(tr("shared.remove_dependency",predecessor), () -> mutate(() -> ZombiesNpcSharedCanvas.unlink(owner.graphDraft(),predecessor,activeKey())), editable());
                }
            }
            case "ACTION" -> {
                actionInspector(n.getAsJsonObject("action"));
                rowButton(tr("graph.edit_action"), this::properties, true);
            }
            case "WAIT" -> field(tr("graph.seconds"), n, "seconds", "1");
            case "BRANCH", "AWAIT" -> conditionInspector(n.getAsJsonObject("condition"));
            case "END" -> rowButton(tr(Boolean.parseBoolean(value(n,"complete","true"))?"end.complete":"end.retry"), () -> mutate(() -> {
                boolean next=!Boolean.parseBoolean(value(n,"complete","true"));
                if(!next && flow(n)!=null && value(rule(),"trigger","INTERACT").equals("AUTO"))throw new IllegalArgumentException(tr("end.auto"));
                n.addProperty("complete",next);
            }),editable());
        }
        if(value(n,"type","").equals("END"))line(tr("shared.link_hint"));
        for (String p : ZombiesNpcGraphDraft.ports(n)) {
            line(tr(p.equals("onFalse") ? "graph.false" : value(n, "type", "").equals("BRANCH") ? "graph.true" : "graph.next") + " → " + value(n, p, ""));
        }
        returnDrawRows();
    }
    private void conditionInspector(JsonObject c) {
        select(tr("graph.match"), List.of("ALL", "ANY"), value(c, "match", "ALL"), v -> tr("graph.match." + v), v -> mutate(() -> c.addProperty("match", v)));
        var tests = c.getAsJsonArray("tests");
        for(int i=0;i<tests.size();i++) line((i+1)+". "+testSummary(tests.get(i).getAsJsonObject()));
        for(int i=0;i<tests.size();i++) {
        final int index=i; line(tr("list.condition",i+1));
        var t = tests.get(i).getAsJsonObject();
        List<String> types=new ArrayList<>(Arrays.stream(ZombiesNpcGraph.TestKind.values()).map(Enum::name).filter(k -> !k.equals("ITEM_ACTOR") || !editingAutomatic()).toList());
        if(!editingAutomatic())types.add("ITEM_ACTOR_MISSING");
        String selectedType=value(t,"kind","WAVE");
        if(selectedType.equals("ITEM_ACTOR") && Boolean.parseBoolean(value(t,"negated","false")))selectedType="ITEM_ACTOR_MISSING";
        select(tr("graph.test_kind"),types,selectedType,v -> tr("graph.test."+v),v -> mutate(() -> {
            t.addProperty("kind",v.equals("ITEM_ACTOR_MISSING")?"ITEM_ACTOR":v); t.addProperty("negated",v.equals("ITEM_ACTOR_MISSING"));
        }));
        if(!value(t,"kind","").equals("ITEM_ACTOR"))rowButton(tr("graph.negate")+": "+tr("bool."+value(t,"negated","false")),()->mutate(()->t.addProperty("negated",!Boolean.parseBoolean(value(t,"negated","false")))),editable());
        switch (value(t, "kind", "WAVE")) {
            case "WAVE" -> field(tr("min_wave"), t, "value", "1");
            case "BARRIER" -> select(tr("barrier_group"), owner.graphBarriers().stream().map(String::valueOf).toList(), value(t, "value", "1"), Function.identity(), v -> mutate(() -> t.addProperty("value", Integer.parseInt(v))));
            case "ITEM_ROOM", "ITEM_ACTOR" -> {
                field(tr("item"), t, "item", "minecraft:emerald"); field(tr("count"), t, "count", "1");
                rowButton(tr("inventory_picker"), () -> {
                    if (minecraft.player == null) return;
                    List<ZombiesNpcStackPickerScreen.Entry> entries = new ArrayList<>();
                    var inventory = minecraft.player.getInventory();
                    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                        var stack = inventory.getItem(slot);
                        if (!stack.isEmpty()) entries.add(new ZombiesNpcStackPickerScreen.Entry(stack.copy(), stack.save(new net.minecraft.nbt.CompoundTag()).toString(), stack.getHoverName().getString(), stack.getCount()));
                    }
                    minecraft.setScreen(new ZombiesNpcStackPickerScreen(this, tr("inventory_picker"), entries, e -> mutate(() -> { t.addProperty("item", e.value()); t.addProperty("count", e.count()); })));
                }, editable());
            }
            case "RULE_DONE", "NPC_ALIVE" -> {
                List<String> references = new ArrayList<>();
                for (var element : owner.graphDraft().npcs()) {
                    var npc = element.getAsJsonObject(); String id = value(npc, "id", "");
                    if (value(t, "kind", "").equals("NPC_ALIVE")) references.add(id);
                    else for (var r : npc.getAsJsonArray("rules")) references.add(id + "." + value(r.getAsJsonObject(), "id", ""));
                }
                select(tr("target"), references, value(t, "reference", ""), Function.identity(), v -> mutate(() -> t.addProperty("reference", v)));
            }
        }
        rowButton(tr("list.remove_condition",i+1), () -> mutate(() -> tests.remove(index)), editable() && tests.size()>1);
        }
        rowButton(tr("graph.add_test"), () -> mutate(() -> tests.add(ZombiesNpcGraphDraft.test())), editable() && tests.size()<16);
    }
    private String testSummary(JsonObject t) {
        String kind=value(t,"kind","WAVE");boolean negated=Boolean.parseBoolean(value(t,"negated","false"));
        String name=tr("graph.test."+(kind.equals("ITEM_ACTOR")&&negated?"ITEM_ACTOR_MISSING":kind));
        if(negated&&!kind.equals("ITEM_ACTOR"))name=tr("list.not")+name;
        return name+switch(kind){case "ITEM_ROOM","ITEM_ACTOR" -> " · "+value(t,"item","")+" × "+value(t,"count","1");case "WAVE","BARRIER" -> " · "+value(t,"value","1");default -> " · "+value(t,"reference","");};
    }
    private void actionInspector(JsonObject a) {
        var kinds = Arrays.stream(ZombiesNpcConfig.Kind.values()).map(Enum::name)
                .filter(k -> !editingAutomatic() || !List.of("GIVE_ITEM", "TAKE_ITEM").contains(k)).toList();
        select(tr("kind"), kinds, value(a, "kind", "DIALOGUE"), v -> tr("kind." + v), v -> mutate(() -> {
            var p = minecraft.player;
            var pos = p == null ? new ZombiesNpcConfig.Position(0,64,0,0) : new ZombiesNpcConfig.Position(p.getX(),p.getY(),p.getZ(),p.getYRot());
            ZombiesNpcEditorDraft.setKind(a, ZombiesNpcConfig.Kind.valueOf(v), pos);
        }));
        if(List.of("NPC","DIALOGUE").contains(value(a,"kind","DIALOGUE"))) {
            List<String> targets=new ArrayList<>(List.of(""));
            for(var n:owner.graphDraft().npcs())targets.add(value(n.getAsJsonObject(),"id",""));
            select(tr("target"),targets,value(a,"target",""),v -> v.isBlank()?tr("self"):v,v -> mutate(() -> {if(v.isBlank())a.remove("target");else a.addProperty("target",v);}));
        }
        switch (value(a, "kind", "DIALOGUE")) {
            case "DIALOGUE" -> {
                List<String> lines = new ArrayList<>(Arrays.asList(value(a, "text", "").split("\\n", -1)));
                for (int i = 0; i < lines.size(); i++) {
                    int index = i; line(tr("dialogue") + " " + (i + 1));
                    inspector.add(y -> {
                        var box = addRenderableWidget(new EditBox(font, inspectorX + 6, y, inspectorWidth - 12, 20, Component.literal(tr("dialogue"))));
                        box.setMaxLength(32767); box.setValue(lines.get(index)); box.setEditable(editable());
                        box.setResponder(v -> { remember(); lines.set(index, v); a.addProperty("text", String.join("\n", lines)); message = ""; });
                    });
                }
                rowButton(tr("add_line"), () -> mutate(() -> a.addProperty("text", value(a,"text","") + "\n")), editable());
            }
            case "TAKE_ITEM", "GIVE_ITEM" -> { field(tr("item"), a, "item", "minecraft:emerald"); field(tr("count"), a, "count", "1"); }
            case "OPEN_BARRIER" -> select(tr("barrier_group"), owner.graphBarriers().stream().map(String::valueOf).toList(), value(a,"barrierGroup","1"), Function.identity(), v -> mutate(() -> a.addProperty("barrierGroup", Integer.parseInt(v))));
            default -> line(tr("graph.full_properties"));
        }
    }
    private void line(String text) { inspector.add(y -> decorations.add(() -> graphics.drawString(font, font.plainSubstrByWidth(text, inspectorWidth - 12), inspectorX + 6, y + 6, 0xFFBFCED7, false))); }
    private void rowButton(String text, Runnable click, boolean active) { inspector.add(y -> button(text, inspectorX + 6, y, inspectorWidth - 12, click, active)); }
    private void field(String label, JsonObject obj, String key, String fallback) {
        line(label);
        inspector.add(y -> {
            var box = addRenderableWidget(new EditBox(font, inspectorX + 6, y, inspectorWidth - 12, 20, Component.literal(label)));
            box.setMaxLength(32767); box.setValue(value(obj, key, fallback)); box.setEditable(editable());
            box.setResponder(v -> { remember(); obj.addProperty(key, v); message = ""; });
        });
    }
    private void select(String label, List<String> options, String selected, Function<String, String> caption, Consumer<String> change) {
        line(label); rowButton(selected.isBlank() ? tr("graph.choose") : caption.apply(selected) + " ▾", () -> choose(label, options, selected, caption, change), editable() && !options.isEmpty());
    }
    private void returnDrawRows() {
        int count = Math.max(1, (canvasBottom - 100) / 24);
        inspectorScroll = Math.max(0, Math.min(inspectorScroll, inspector.size() - count));
        for (int i = inspectorScroll; i < Math.min(inspector.size(), inspectorScroll + count); i++) inspector.get(i).accept(100 + (i - inspectorScroll) * 24);
    }

    private double sx(JsonObject n) { return 8 + panX + number(n, "x") * zoom; }
    private double sy(JsonObject n) { return 94 + panY + number(n, "y") * zoom; }
    private double outputY(JsonObject n, String p) { return sy(n) + (p.equals("onFalse") ? 68 : 43) * zoom; }
    private double inputY(JsonObject n) { return sy(n)+(value(n,"type","").equals("ENTRY")||port.equals("completed")&&Set.of("BRANCH","AWAIT").contains(value(n,"type",""))?68:43)*zoom; }
    private boolean inCanvas(double x, double y) { return !overlay && x >= 8 && x < canvasRight && y >= 94 && y < canvasBottom; }
    private boolean near(double x, double y, double a, double b) { return Math.abs(x - a) < 8 && Math.abs(y - b) < 8; }
    private JsonObject inputAt(double x,double y) {
        JsonObject best=null;double distance=129;
        for(var n:nodeList()){double d=Math.pow(x-sx(n),2)+Math.pow(y-inputY(n),2);if(near(x,y,sx(n),inputY(n))&&d<distance){best=n;distance=d;}}
        return best;
    }
    private record Output(JsonObject node,String port) { }
    private Output outputAt(double x,double y) {
        Output best=null;double distance=129;
        for(var n:nodeList())for(String p:ports(n)){
            double d=Math.pow(x-sx(n)-NW*zoom,2)+Math.pow(y-outputY(n,p),2);
            if(near(x,y,sx(n)+NW*zoom,outputY(n,p))&&d<distance){best=new Output(n,p);distance=d;}
        }
        return best;
    }
    private void wire(JsonObject target) {
        var from=node(source);if(target==null||from==null)return;
        try{mutate(()->{
            if(port.equals("completed")){
                if(flow(from)==null)throw new IllegalArgumentException(tr("loose.attach_first"));
                if(value(target,"type","").equals("ENTRY"))ZombiesNpcSharedCanvas.connect(owner.graphDraft(),flow(from).key(),flow(target).key());
                else ZombiesNpcSharedCanvas.connectCondition(flow(from).key(),target);
            }else{
                var result=ZombiesNpcSharedCanvas.connectExecution(owner.graphDraft(),contexts.get(from),port,contexts.get(target));
                selected=result.key();if(result.flow()!=null)owner.graphSelect(result.flow().npcIndex(),result.flow().ruleIndex());
            }
        });}catch(RuntimeException e){message=detail(e);}source="";
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(super.mouseClicked(x,y,button))return true;if(!inCanvas(x,y))return false;
        lastX=x;lastY=y;
        if(button==1){
            source="";var output=outputAt(x,y);
            if(output!=null){mutate(()->{if(output.port().equals("completed"))ZombiesNpcSharedCanvas.unlinkOutgoing(owner.graphDraft(),flow(output.node()).key());else output.node().addProperty(output.port(),"");});return true;}
            var input=inputAt(x,y);if(input!=null&&value(input,"type","").equals("ENTRY"))mutate(()->flow(input).rule().add("requires",new JsonArray()));
            return true;
        }
        if(button==2){panning=true;return true;}if(button!=0)return false;
        if(!source.isBlank()){var target=inputAt(x,y);if(target!=null){wire(target);return true;}}
        var output=outputAt(x,y);if(output!=null&&editable()){source=key(output.node());port=output.port();return true;}
        var all=nodeList();
        for(int i=all.size()-1;i>=0;i--){var n=all.get(i);if(x>=sx(n)&&x<=sx(n)+NW*zoom&&y>=sy(n)&&y<=sy(n)+NH*zoom){
            String id=key(n);long now=System.currentTimeMillis();boolean twice=qualify(selected).equals(id)&&now-lastClick<300;lastClick=now;
            activate(n);selected=id;inspectorScroll=testIndex=0;
            if(twice){properties();return true;}if(editable()){remember();dragging=id;}refresh();return true;
        }}
        for(var f:flows){var b=bounds(f);double gx=8+panX+(b[0]-12)*zoom,gy=94+panY+(b[1]-30)*zoom;
            if(x>=gx&&x<=8+panX+(b[2]+12)*zoom&&y>=gy&&y<=gy+24*zoom){
                owner.graphSelect(f.npcIndex(),f.ruleIndex());selected=f.key()+"/"+value(f.entry(),"id","");
                if(editable()){remember();draggingGroup=f.key();}inspectorScroll=0;refresh();return true;
            }
        }
        panning=true;return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (panning) { panX += x - lastX; panY += y - lastY; lastX = x; lastY = y; return true; }
        if(!draggingGroup.isBlank()) {
            var f=ZombiesNpcSharedCanvas.find(owner.graphDraft(),draggingGroup);
            if(f!=null)try{ZombiesNpcSharedCanvas.translate(f,(x-lastX)/zoom,(y-lastY)/zoom);}catch(IllegalArgumentException e){message=e.getMessage();}
            lastX=x;lastY=y;return true;
        }
        var n = node(dragging);
        if (n != null) {
            n.addProperty("x", Math.max(-99900, Math.min(99900, number(n, "x") + (x - lastX) / zoom)));
            n.addProperty("y", Math.max(-99900, Math.min(99900, number(n, "y") + (y - lastY) / zoom)));
            lastX = x; lastY = y; return true;
        }
        return !source.isBlank() || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        panning = false; dragging = draggingGroup = "";
        if (button == 0 && !source.isBlank() && inCanvas(x, y)) { var target = inputAt(x, y); if (target != null) { wire(target); return true; } }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (overlay || x >= inspectorX && width >= 620) { inspectorScroll += delta > 0 ? -1 : 1; refresh(); return true; }
        if (inCanvas(x, y)) {
            double next = Math.max(.03, Math.min(1.5, zoom * Math.pow(1.15, delta)));
            panX = (x - 8) - (x - 8 - panX) * next / zoom;
            panY = (y - 94) - (y - 94 - panY) * next / zoom; zoom = next; return true;
        }
        return false;
    }
    private double[] bounds(ZombiesNpcSharedCanvas.Flow flow){
        var all=flow.nodes();return new double[]{all.stream().mapToDouble(n->number(n,"x")).min().orElse(0),all.stream().mapToDouble(n->number(n,"y")).min().orElse(0),all.stream().mapToDouble(n->number(n,"x")+NW).max().orElse(NW),all.stream().mapToDouble(n->number(n,"y")+NH).max().orElse(NH)};
    }
    private void fit(){fitNodes(nodeList());}
    private void focus(){var f=ZombiesNpcSharedCanvas.find(owner.graphDraft(),activeKey());if(f!=null)fitNodes(f.nodes());}
    private void fitNodes(List<JsonObject> all){
        if(all.isEmpty())return;
        double minX=all.stream().mapToDouble(n->number(n,"x")-16).min().orElse(0),minY=all.stream().mapToDouble(n->number(n,"y")-34).min().orElse(0);
        double maxX=all.stream().mapToDouble(n->number(n,"x")+NW+16).max().orElse(NW),maxY=all.stream().mapToDouble(n->number(n,"y")+NH+16).max().orElse(NH);
        zoom=Math.max(.03,Math.min(1,Math.min((canvasRight-32)/(maxX-minX),(canvasBottom-118)/(maxY-minY))));
        panX=(canvasRight-8-(maxX-minX)*zoom)/2-minX*zoom;panY=(canvasBottom-94-(maxY-minY)*zoom)/2-minY*zoom;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (hasControlDown() && key == GLFW.GLFW_KEY_S) { saveGraph(); return true; }
        if (getFocused() instanceof EditBox && key != GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, mods);
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!source.isBlank()) source = ""; else if (overlay) { overlay = false; refresh(); } else onClose(); return true;
        }
        if (editable() && hasControlDown() && key == GLFW.GLFW_KEY_Z) { history(undo, redo); return true; }
        if (editable() && hasControlDown() && key == GLFW.GLFW_KEY_Y) { history(redo, undo); return true; }
        if (editable() && key == GLFW.GLFW_KEY_DELETE && node(selected) != null) { mutate(this::deleteSelected); return true; }
        return super.keyPressed(key, scan, mods);
    }
    void saveGraph() { try { validate(); owner.graphSave(); } catch (RuntimeException e) { message = detail(e); } }
    @Override public void onClose() { source = ""; owner.graphBack(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { boolean before = editable(); owner.tick(); if (before != editable()) refresh(); for (var child : children()) if (child instanceof EditBox box) box.tick(); }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        graphics = g;
        g.fill(0, 0, width, height, 0xFF0E151E);
        g.drawString(font, title, 8, 6, 0xFFE0EAF0, false);
        String info = Math.round(zoom * 100) + "%" + (owner.graphDraft().dirty() ? "  * " + tr("unsaved") : "");
        g.drawString(font, info, width - font.width(info) - 8, 6, 0xFF6ED6B0, false);
        if (!overlay) {
            g.enableScissor(8, 94, canvasRight, canvasBottom);
            g.fill(8, 94, canvasRight, canvasBottom, 0xFF101923);
            int spacing = Math.max(8, (int) (24 * zoom));
            int ox = (int) ((panX % spacing + spacing) % spacing), oy = (int) ((panY % spacing + spacing) % spacing);
            for (int x = 8 + ox; x < canvasRight; x += spacing) g.fill(x, 94, x + 1, canvasBottom, 0xFF1C2A37);
            for (int y = 94 + oy; y < canvasBottom; y += spacing) g.fill(8, y, canvasRight, y + 1, 0xFF1C2A37);
            for(var f:flows)drawGroup(g,f);
            for(var f:flows)for(var requirement:f.rule().getAsJsonArray("requires")) {
                var predecessor=flows.stream().filter(v->v.key().equals(requirement.getAsString())).findFirst().orElse(null);
                if(predecessor!=null){var from=ZombiesNpcSharedCanvas.completion(predecessor);var to=f.entry();if(from!=null)curve(g,sx(from)+NW*zoom,outputY(from,"completed"),sx(to),sy(to)+68*zoom,0xFFB895F5);}
            }
            for (var n : nodeList()) for (String p : ZombiesNpcGraphDraft.ports(n)) {
                String prefix=flow(n)==null?"_loose":flow(n).key(); var target = index.containsKey(prefix+"/"+value(n,p,""))?index.get(prefix+"/"+value(n,p,"")).node():null;
                if (target != null) curve(g, sx(n) + NW * zoom, outputY(n, p), sx(target), sy(target) + 43 * zoom, p.equals("completed") ? 0xFFB895F5 : p.equals("onFalse") ? 0xFFED927A : 0xFF83D9BA);
            }
            for(var target:nodeList())if(target.has("condition"))for(var e:target.getAsJsonObject("condition").getAsJsonArray("tests")){
                var t=e.getAsJsonObject();if(!value(t,"kind","").equals("RULE_DONE"))continue;
                var f=ZombiesNpcSharedCanvas.find(owner.graphDraft(),value(t,"reference",""));if(f==null)continue;var from=ZombiesNpcSharedCanvas.completion(f);
                if(from!=null)curve(g,sx(from)+NW*zoom,outputY(from,"completed"),sx(target),sy(target)+68*zoom,0xFFB895F5);
            }
            if (!source.isBlank() && node(source) != null) curve(g, sx(node(source)) + NW * zoom, outputY(node(source), port), mx, my, 0xFFFFDB80);
            for (var n : nodeList()) drawNode(g, n);
            if (index.isEmpty()) g.drawCenteredString(font, tr("graph.empty"), (canvasRight + 8) / 2, 120, 0xFFA7BBC9);
            miniMap(g);
            g.disableScissor();
        }
        if (overlay || width >= 620) {
            g.fill(inspectorX, 94, inspectorX + inspectorWidth, canvasBottom, 0xFF192430);
            for (var draw : decorations) draw.run();
            if (inspector.size() > (canvasBottom - 100) / 24) g.fill(inspectorX + inspectorWidth - 3, 100, inspectorX + inspectorWidth - 1, 110, 0xFF78CDB1);
        }
        String status = !message.isBlank() ? message : owner.graphMessage();
        g.drawString(font, font.plainSubstrByWidth(status, width - 16), 8, height - 30, 0xFFFFCE7E, false);
        g.drawString(font, font.plainSubstrByWidth(tr("graph.help"), width - 16), 8, height - 16, 0xFF92A8B9, false);
        super.render(g, mx, my, partial);
    }
    private int groupColor(ZombiesNpcSharedCanvas.Flow f) {int[] colors={0xFF4F91B5,0xFFB59053,0xFF67A682,0xFF9B79B5,0xFFB5767F};return colors[Math.floorMod(value(f.npc(),"id","").hashCode(),colors.length)];}
    private void drawGroup(GuiGraphics g,ZombiesNpcSharedCanvas.Flow f){
        var b=bounds(f);double x=8+panX+(b[0]-12)*zoom,y=94+panY+(b[1]-30)*zoom,w=(b[2]-b[0]+24)*zoom,h=(b[3]-b[1]+44)*zoom;
        if(x>canvasRight||x+w<8||y>canvasBottom||y+h<94)return;
        int color=groupColor(f);g.fill((int)x,(int)y,(int)(x+w),(int)(y+h),0x182B4255);
        g.fill((int)x,(int)y,(int)(x+w),(int)(y+24*zoom),color);
        g.pose().pushPose();g.pose().translate(x+5*zoom,y+7*zoom,0);g.pose().scale((float)zoom,(float)zoom,1);
        String name=value(f.npc().getAsJsonObject("appearance"),"name",value(f.npc(),"id",""));
        g.drawString(font,name+" ["+value(f.npc(),"id","")+"]  /  "+value(f.rule(),"id",""),0,0,0xFFFFFFFF,false);g.pose().popPose();
    }
    private void drawNode(GuiGraphics g, JsonObject n) {
        double x = sx(n), y = sy(n);
        if (x > canvasRight || x + NW * zoom < 8 || y > canvasBottom || y + NH * zoom < 94) return;
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale((float) zoom, (float) zoom, 1);
        int color = switch (value(n, "type", "")) { case "ENTRY" -> 0xFF367AA4; case "BRANCH" -> 0xFF7D9A48; case "WAIT", "AWAIT" -> 0xFF8A6AB0; case "END" -> 0xFF667582; default -> 0xFFAC6776; };
        g.fill(-1, -1, NW + 1, NH + 1, key(n).equals(qualify(selected)) ? 0xFFFFD36F : 0xFF3D5264);
        g.fill(0, 0, NW, NH, 0xFF1B2936); g.fill(0, 0, NW, 21, color);
        String title = value(n, "type", "").equals("ACTION") ? tr("kind." + value(n.getAsJsonObject("action"), "kind", "DIALOGUE")) : tr("graph.type." + value(n, "type", ""));
        g.drawString(font, font.plainSubstrByWidth(title, NW - 12), 6, 6, 0xFFFFFFFF, false);
        g.drawString(font, font.plainSubstrByWidth(summary(n).replace('\n', ' '), NW - 24), 10, 29, 0xFFCDDCE5, false);
        if (!value(n, "type", "").equals("ENTRY")) { g.fill(-4, 39, 4, 47, 0xFF8CD6F0); g.drawString(font, tr("graph.in"), 8, 42, 0xFF93ACBD, false); }
        if(Set.of("ENTRY","BRANCH","AWAIT").contains(value(n,"type",""))){g.fill(-4,64,4,72,0xFFB895F5);g.drawString(font,tr("shared.dependency_in"),8,65,0xFFD2B9FA,false);}
        for (String p : ports(n)) {
            int py = p.equals("onFalse") ? 68 : 43;
            String label = tr(p.equals("completed")?"shared.completed":p.equals("onFalse") ? "graph.false" : value(n, "type", "").equals("BRANCH") ? "graph.true" : "graph.next");
            g.drawString(font, label, NW - font.width(label) - 10, py - 3, 0xFFCADCE5, false);
            g.fill(NW - 4, py - 4, NW + 4, py + 4, p.equals("completed") ? 0xFFB895F5 : p.equals("onFalse") ? 0xFFED927A : 0xFF83D9BA);
        }
        g.drawString(font, font.plainSubstrByWidth((flow(n)==null?tr("loose.label"):flow(n).key())+" / "+value(n,"id",""),NW-12), 8, NH - 12, 0xFF91B2C8, false);
        g.pose().popPose();
    }
    private String summary(JsonObject n) {
        return switch (value(n, "type", "")) {
            case "ENTRY" -> tr("trigger." + value(flow(n)==null?null:flow(n).rule(), "trigger", "INTERACT"));
            case "WAIT" -> value(n, "seconds", "1") + " s";
            case "BRANCH", "AWAIT" -> {
                var c = n.getAsJsonObject("condition"); var tests = c.getAsJsonArray("tests");
                var t = tests.get(0).getAsJsonObject();
                yield tr("graph.match." + value(c, "match", "ALL")) + " · " + tr("graph.test." + value(t, "kind", "WAVE")) + (value(t, "kind", "").equals("WAVE") ? " ≥ " + value(t, "value", "1") : "") + (tests.size() > 1 ? " +" + (tests.size() - 1) : "");
            }
            case "ACTION" -> {
                var a = n.getAsJsonObject("action");
                yield switch (value(a, "kind", "")) {
                    case "DIALOGUE" -> value(a, "text", "");
                    case "TAKE_ITEM", "GIVE_ITEM" -> value(a, "item", "") + " × " + value(a, "count", "1");
                    case "OPEN_BARRIER" -> tr("barrier", value(a, "barrierGroup", "1"));
                    case "SET_BLOCK" -> value(a, "block", "") + (a.has("placements") ? " × " + a.getAsJsonArray("placements").size() : "");
                    default -> value(a, "target", tr("self"));
                };
            }
            default -> tr(Boolean.parseBoolean(value(n,"complete","true"))?"end.complete":"end.retry");
        };
    }
    private void curve(GuiGraphics g, double x0, double y0, double x1, double y1, int color) {
        double bend = Math.max(36, Math.abs(x1 - x0) * .5), px = x0, py = y0;
        for (int i = 1; i <= 40; i++) {
            double t = i / 40.0, u = 1 - t;
            double x = u*u*u*x0 + 3*u*u*t*(x0+bend) + 3*u*t*t*(x1-bend) + t*t*t*x1;
            double y = u*u*u*y0 + 3*u*u*t*y0 + 3*u*t*t*y1 + t*t*t*y1;
            segment(g, px, py, x, y, color); px = x; py = y;
        }
    }
    private void segment(GuiGraphics g, double ax, double ay, double bx, double by, int color) {
        if (Math.max(ax,bx) < 8 || Math.min(ax,bx) > canvasRight || Math.max(ay,by) < 94 || Math.min(ay,by) > canvasBottom) return;
        int steps = Math.min(2048, Math.max(1, (int) Math.ceil(Math.max(Math.abs(bx-ax), Math.abs(by-ay)))));
        for (int i = 0; i <= steps; i++) { int x = (int) Math.round(ax + (bx-ax) * i/steps), y = (int) Math.round(ay + (by-ay) * i/steps); g.fill(x, y, x+2, y+2, color); }
    }
    private void miniMap(GuiGraphics g) {
        var all = nodeList(); if (all.isEmpty() || canvasBottom - 94 < 180 || canvasRight < 400) return;
        int x = 14, y = canvasBottom - 64, w = 104, h = 58;
        double minX = all.stream().mapToDouble(n -> number(n, "x")).min().orElse(0), minY = all.stream().mapToDouble(n -> number(n, "y")).min().orElse(0);
        double bw = all.stream().mapToDouble(n -> number(n, "x") + NW).max().orElse(NW) - minX;
        double bh = all.stream().mapToDouble(n -> number(n, "y") + NH).max().orElse(NH) - minY;
        double s = Math.min((w - 12) / bw, (h - 12) / bh);
        g.fill(x, y, x+w, y+h, 0xDD293847);
        for (var n : all) {
            int nx = x+6+(int)((number(n,"x")-minX)*s), ny = y+6+(int)((number(n,"y")-minY)*s);
            g.fill(nx, ny, nx+Math.max(3,(int)(NW*s)), ny+Math.max(2,(int)(NH*s)), key(n).equals(qualify(selected)) ? 0xFFFFD36F : 0xFF88B3C7);
        }
    }
    private static String detail(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error.getMessage() == null ? error.toString() : error.getMessage(); }
}
