package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployDraft;
import com.cdp.codpattern.app.zombies.npc.*;
import com.cdp.codpattern.client.zombies.ZombiesDeployClientActionHandler;
import com.google.gson.*;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.zombies.OpenZombiesDeployToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.zombies.ZombiesDeployToolActionC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;

/** NPC authoring pages inside the deployment tool. The working copy survives page switches and collapse. */
public final class ZombiesNpcDeployScreen extends Screen {
    private static final AtomicLong REQUESTS = new AtomicLong();
    private static final int ROW = 25;
    private final ZombiesDeployToolScreen parent;
    private final ZombiesDeployDraft deployment;
    private ZombiesNpcEditorDraft draft;
    private String revision = "";
    private List<Integer> barriers = List.of();
    private List<String> events=List.of();
    private boolean editable;
    private boolean suspended;
    private long pending = -1;
    private long deadline;
    private Runnable afterReply;
    private String message = "";
    private int npcIndex = -1, ruleIndex = -1, actionIndex = -1;
    private int page, scroll, npcScroll;
    private int placementIndex;
    private ZombiesNpcGraphScreen graphScreen, graphReturn;
    private JsonObject graphAction;
    private boolean looseActionEditing;
    private int top, bottom, leftWidth, formX, formWidth;
    private final List<FormRow> rows = new ArrayList<>();
    private final List<EditBox> textBoxes = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();

    public ZombiesNpcDeployScreen(ZombiesDeployToolScreen parent, ZombiesDeployDraft deployment) {
        super(Component.translatable("gui.codpattern.zombies.npc.title"));
        this.parent = parent;
        this.deployment = deployment.withFields(java.util.Map.of());
    }
    private static String tr(String key, Object... values) { return Component.translatable("gui.codpattern.zombies.npc." + key, values).getString(); }
    private boolean busy() { return pending >= 0; }
    private boolean canEdit() { return editable && !busy() && draft != null; }
    public boolean suspended() { return suspended; }
    public String mapName() { return deployment.selectedMap(); }

    public void load() { request("load", "", null); }
    public void reopen() { suspended = false; minecraft.setScreen(this); }

    private void request(String operation, String payload, Runnable next) {
        if (busy()) return;
        try {
            ZombiesNpcEditorWire.checkRequest(payload);
            pending = REQUESTS.incrementAndGet();
            deadline = System.currentTimeMillis() + 20_000;
            afterReply = next;
            message = tr("waiting");
            FPSMatch.sendToServer(ZombiesDeployToolActionC2SPacket.npcEditor(deployment, operation, payload, pending));
        } catch (RuntimeException failure) {
            pending = -1;
            message = detail(failure);
        }
        rebuildWidgets();
    }

    public void accept(OpenZombiesDeployToolScreenS2CPacket packet) {
        if (packet.requestId() != pending || pending < 0) return;
        pending = -1;
        Runnable next = afterReply;
        afterReply = null;
        try {
            var response = ZombiesNpcRepository.GSON.fromJson(packet.npcEditorData(), ZombiesNpcEditorService.Snapshot.class);
            if (!response.mapName().equals(mapName())) return;
            editable = response.editable();
            if (!response.error().isBlank()) {
                message = response.error().startsWith("gui.") ? Component.translatable(response.error()).getString() : response.error();
                rebuildWidgets();
                if (graphScreen != null) graphScreen.refresh();
                return;
            }
            if (draft == null) draft = new ZombiesNpcEditorDraft(response.config());
            else draft.accept(response.config());
            revision = response.revision();
            barriers = response.barriers();
            events=response.events()==null?List.of():response.events();
            npcIndex = Math.min(Math.max(0, npcIndex), draft.npcs().size() - 1);
            clampSelection();
            message = tr("status." + response.status());
            rebuildWidgets();
            if (graphScreen != null) graphScreen.refresh();
            if (next != null) next.run();
        } catch (RuntimeException failure) {
            message = detail(failure);
            rebuildWidgets();
            if (graphScreen != null) graphScreen.refresh();
        }
    }

    @Override protected void init() {
        rows.clear(); textBoxes.clear(); labels.clear();
        top = 74; bottom = height - 92;
        leftWidth = Math.max(84, Math.min(144, width / 4));
        formX = graphReturn == null ? leftWidth + 24 : 12; formWidth = width - formX - 12;
        int tabWidth = (width - 28) / 2;
        if (graphReturn != null) {
            button(tr("graph.back"), 12, 42, width - 24, this::returnToGraph, true);
        } else for (int i = 0; i < 2; i++) {
            int target = i;
            button(tr(i == 0 ? "page.0" : "graph.title"), 12 + i * (tabWidth + 4), 42, tabWidth, () -> {
                if (target == 1) openGraph(); else { page = 0; scroll = 0; rebuildWidgets(); }
            }, !busy() && draft != null);
        }
        if (draft != null) {
            if (graphReturn == null) buildNpcList();
            if (npc() != null) {
                if (graphAction != null) actionProperties(graphAction);
                else switch (page) { case 0 -> basic(); case 1 -> rules(); case 2 -> actions(); }
            } else heading("empty");
            int visible = visibleRows();
            scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
            for (int i = scroll; i < Math.min(rows.size(), scroll + visible); i++) rows.get(i).draw(formX, top + (i - scroll) * ROW, formWidth);
        }
        footer();
    }

    private int visibleRows() { return Math.max(1, (bottom - top) / ROW); }
    private JsonObject npc() { return object(draft == null ? null : draft.npcs(), npcIndex); }
    private JsonObject rule() { return object(npc() == null ? null : npc().getAsJsonArray("rules"), ruleIndex); }
    private JsonObject action() { return object(rule() == null ? null : rule().getAsJsonArray("actions"), actionIndex); }
    private static JsonObject object(JsonArray array, int index) { return array != null && index >= 0 && index < array.size() ? array.get(index).getAsJsonObject() : null; }
    private static String value(JsonObject object, String key, String fallback) { return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback; }
    private void clampSelection() {
        if (npc() == null) { ruleIndex = -1; actionIndex = -1; return; }
        ruleIndex = Math.min(Math.max(0, ruleIndex), npc().getAsJsonArray("rules").size() - 1);
        actionIndex = rule() == null ? -1 : Math.min(Math.max(0, actionIndex), rule().getAsJsonArray("actions").size() - 1);
    }

    private void buildNpcList() {
        int count = Math.max(1, (bottom - top - 48) / ROW);
        npcScroll = Math.max(0, Math.min(npcScroll, Math.max(0, draft.npcs().size() - count)));
        for (int row = 0; row < count && row + npcScroll < draft.npcs().size(); row++) {
            int index = row + npcScroll;
            JsonObject npc = draft.npcs().get(index).getAsJsonObject();
            String caption = value(npc.getAsJsonObject("appearance"), "name", value(npc, "id", ""));
            button((index == npcIndex ? "● " : "") + caption, 12, top + row * ROW, leftWidth, () -> {
                npcIndex = index; ruleIndex = 0; actionIndex = 0; scroll = 0; clampSelection(); rebuildWidgets();
            }, !busy());
        }
        button(tr("add_npc"), 12, bottom - 44, leftWidth, () -> {
            draft.addNpc(currentPosition()); npcIndex = draft.npcs().size() - 1; ruleIndex = -1; actionIndex = -1;
            page = 0; scroll = 0; npcScroll = npcIndex; rebuildWidgets();
        }, canEdit() && draft.npcs().size() < 128);
        button(tr("delete_npc"), 12, bottom - 20, leftWidth, () -> {
            draft.npcs().remove(npcIndex); npcIndex = Math.min(Math.max(0, npcIndex - 1), draft.npcs().size() - 1);
            clampSelection(); scroll = 0; rebuildWidgets();
        }, canEdit() && npc() != null);
    }

    private void skin(JsonObject appearance){
        boolean player=appearance.has("playerSkin")&&!appearance.get("playerSkin").isJsonNull();
        if(player){
            String name=value(appearance.getAsJsonObject("playerSkin"),"name","");
            rows.add((x,y,w)->labels.add(new Label(tr("player_skin.current")+name,x,y+6,w)));
            rowButton("player_skin.resource",()->{appearance.remove("playerSkin");rebuildWidgets();});
        }else text("skin",appearance,"skin","minecraft:textures/entity/player/wide/steve.png");
        rowButton("player_skin.open",()->minecraft.setScreen(new ZombiesNpcSkinScreen(this,result->{appearance.add("playerSkin",ZombiesNpcRepository.GSON.toJsonTree(result));rebuildWidgets();})));
    }
    private void basic() {
        JsonObject npc = npc();
        text("id", value(npc, "id", ""), next -> draft.renameNpc(npc, next));
        JsonObject appearance = npc.getAsJsonObject("appearance");
        text("name", appearance, "name", "");
        text("chat_name", appearance, "chatName", "");
        color("name_color", appearance, "nameColor");
        color("text_color", appearance, "textColor");
        skin(appearance);
        choice("pose", appearance, "pose", List.of("STANDING", "SITTING", "LYING", "GROUND_SITTING", "SUPINE", "KNEELING"), "STANDING", "pose.");
        bool("alive", appearance, "alive", true);
        bool("invulnerable", appearance, "invulnerable", true);
        combat(appearance);
        bool("debug.mode",draft.options(),"debug",false);
        text("dialogue_interval", npc, "dialogueIntervalSeconds", "1");
        position(appearance, "position");
        if (!value(npc,"dialogue","").isBlank()) rowButton("dialogue.import", () -> {
            draft.importDialogue(npc); rebuildWidgets();
        });
        rowButton("duplicate_npc", () -> {
            JsonObject clone = npc.deepCopy();
            String newId = draft.addNpc(currentPosition()).get("id").getAsString();
            clone.addProperty("id", newId);
            // Internal prerequisites of the copy follow its new ID.
            for (JsonElement rule : clone.getAsJsonArray("rules")) {
                JsonArray prerequisites = rule.getAsJsonObject().getAsJsonArray("requires");
                for (int i = 0; i < prerequisites.size(); i++) {
                    String prerequisite = prerequisites.get(i).getAsString();
                    String old = value(npc, "id", "") + ".";
                    if (prerequisite.startsWith(old)) prerequisites.set(i, new JsonPrimitive(newId + "." + prerequisite.substring(old.length())));
                }
                if (rule.getAsJsonObject().has("graph")) rule.getAsJsonObject().getAsJsonObject("graph").addProperty("canvasVersion",0);
                String oldId = value(npc, "id", "");
                ZombiesNpcGraphDraft.references(rule.getAsJsonObject(), v -> v.equals(oldId) ? newId : v,
                        v -> v.startsWith(oldId + ".") ? newId + v.substring(oldId.length()) : v);
            }
            draft.npcs().set(draft.npcs().size() - 1, clone); npcIndex = draft.npcs().size() - 1; scroll = 0; rebuildWidgets();
        });
    }

    private void rules() {
        if (graphReturn == null) rowButton("add_rule", () -> { draft.addRule(npc()); ruleIndex = npc().getAsJsonArray("rules").size() - 1; actionIndex = 0; scroll = 0; rebuildWidgets(); });
        if (graphReturn == null) ruleSelector();
        if (rule() == null) { heading("no_rules"); return; }
        JsonObject rule = rule();
        text("rule_id", value(rule, "id", ""), next -> draft.renameRule(npc(), rule, next));
        selection("trigger", value(rule, "trigger", "INTERACT"), List.of("INTERACT", "AUTO"), "trigger.", selected -> {
            if (selected.equals("AUTO") && ZombiesNpcGraphDraft.usesActor(rule)) {
                message = tr("graph.error.actor"); return;
            }
            rule.addProperty("trigger", selected); rebuildWidgets();
        });
        text("min_wave", rule, "minWave", "0");
        text("action_interval", rule, "actionIntervalSeconds", "1");
        boolean hasItemCondition = !value(rule, "requiredItem", "").isBlank();
        rowButton(hasItemCondition ? "disable_item_condition" : "enable_item_condition", () -> {
            rule.addProperty("requiredItem", hasItemCondition ? "" : "minecraft:emerald");
            rule.addProperty("requiredCount", 1); rebuildWidgets();
        });
        if (hasItemCondition) {
            text("required_item", rule, "requiredItem", "minecraft:emerald");
            text("count", rule, "requiredCount", "1");
            inventoryPicker(rule, "requiredItem", "requiredCount");
        }
        bool("any_condition", rule, "anyCondition", false);
        heading("barriers");
        var groups = new java.util.TreeSet<>(barriers);
        for (JsonElement group : rule.getAsJsonArray("openedBarriers")) groups.add(group.getAsInt());
        if (groups.isEmpty()) heading("no_barriers");
        for (int group : groups) toggleMember(tr("barrier", group), rule.getAsJsonArray("openedBarriers"), new JsonPrimitive(group));
        heading("prerequisites");
        var prerequisites = new java.util.TreeSet<String>();
        for (JsonElement requirement : rule.getAsJsonArray("requires")) prerequisites.add(requirement.getAsString());
        for (JsonElement element : draft.npcs()) {
            JsonObject candidate = element.getAsJsonObject();
            for (JsonElement candidateRule : candidate.getAsJsonArray("rules")) if (candidateRule != rule) {
                String key = value(candidate, "id", "") + "." + value(candidateRule.getAsJsonObject(), "id", "");
                prerequisites.add(key);
            }
        }
        for (String key : prerequisites) toggleMember(key, rule.getAsJsonArray("requires"), new JsonPrimitive(key));
        reorder(npc().getAsJsonArray("rules"), ruleIndex, index -> ruleIndex = index);
        if (graphReturn == null) {
            rowButton("delete_rule", () -> { npc().getAsJsonArray("rules").remove(ruleIndex); ruleIndex--; clampSelection(); scroll = 0; rebuildWidgets(); });
        }
    }

    private void ruleSelector() {
        JsonArray rules = npc().getAsJsonArray("rules");
        if (rules.isEmpty()) return;
        List<String> ids = rules.asList().stream().map(element -> value(element.getAsJsonObject(), "id", "")).toList();
        selection("selected_rule", rule() == null ? ids.get(0) : value(rule(), "id", ""), ids, "", selected -> {
            ruleIndex = ids.indexOf(selected); actionIndex = 0; scroll = 0; clampSelection(); rebuildWidgets();
        });
    }

    private void actions() {
        if (rule() != null) rowButton("add_action", () -> {
            draft.addAction(rule(), ZombiesNpcConfig.Kind.DIALOGUE, currentPosition());
            actionIndex = rule().getAsJsonArray("actions").size() - 1; rebuildWidgets();
        });
        ruleSelector();
        if (rule() == null) { heading("no_rules"); return; }
        JsonArray actions = rule().getAsJsonArray("actions");
        if (!actions.isEmpty()) {
            List<String> options = new ArrayList<>();
            for (int i = 0; i < actions.size(); i++) options.add((i + 1) + ". " + tr("kind." + value(actions.get(i).getAsJsonObject(), "kind", "DIALOGUE")));
            selection("selected_action", options.get(Math.max(0, actionIndex)), options, "", selected -> {
                actionIndex = options.indexOf(selected); rebuildWidgets();
            });
        }
        if (action() == null) return;
        actionProperties(action());
        reorder(actions, actionIndex, index -> actionIndex = index);
        rowButton("delete_action", () -> { actions.remove(actionIndex); actionIndex--; clampSelection(); rebuildWidgets(); });
    }

    private void actionProperties(JsonObject action) {
        List<String> kinds = Arrays.stream(ZombiesNpcConfig.Kind.values()).map(Enum::name)
                .filter(kind -> looseActionEditing || !value(rule(), "trigger", "INTERACT").equals("AUTO") || (!kind.equals("TAKE_ITEM") && !kind.equals("GIVE_ITEM"))).toList();
        selection("kind", value(action, "kind", "DIALOGUE"), kinds, "kind.", selected -> {
            ZombiesNpcEditorDraft.setKind(action, ZombiesNpcConfig.Kind.valueOf(selected), currentPosition()); rebuildWidgets();
        });
        String kind = value(action, "kind", "DIALOGUE");
        optional(action, "delaySeconds", "action_delay", new JsonPrimitive(1));
        if (action.has("delaySeconds")) text("action_delay", action, "delaySeconds", "1");
        if (kind.equals("NPC") || kind.equals("DIALOGUE")) {
            List<String> targets = new ArrayList<>(List.of(""));
            for (JsonElement npc : draft.npcs()) targets.add(value(npc.getAsJsonObject(), "id", ""));
            selection("target", value(action, "target", ""), targets, "", selected -> {
                if (selected.isEmpty()) action.remove("target"); else action.addProperty("target", selected);
                rebuildWidgets();
            });
        }
        switch (kind) {
            case "NPC" -> appearancePatch(action);
            case "DIALOGUE" -> {
                optional(action, "dialogueIntervalSeconds", "dialogue_interval", new JsonPrimitive(1));
                if (action.has("dialogueIntervalSeconds")) text("dialogue_interval", action, "dialogueIntervalSeconds", "1");
                multiline("dialogue", action, "text");
            }
            case "TAKE_ITEM", "GIVE_ITEM" -> {
                text("item", action, "item", "minecraft:emerald"); text("count", action, "count", "1");
                inventoryPicker(action, "item", "count");
                rowButton("held_item", () -> {
                    var stack = minecraft.player.getMainHandItem();
                    if (stack.isEmpty() || stack.getItem() instanceof com.phasetranscrystal.fpsmatch.common.item.zombies.ZombiesDeployTool) stack = minecraft.player.getOffhandItem();
                    if (stack.isEmpty()) { message = tr("error.no_item"); return; }
                    action.addProperty("item", stack.save(new CompoundTag()).toString()); action.addProperty("count", stack.getCount()); rebuildWidgets();
                });
            }
            case "SET_BLOCK" -> {
                blockPlacements(action);
            }
            case "MAP_EVENT" -> selection("map_event",value(action,"text",""),events,"",selected->{action.addProperty("text",selected);rebuildWidgets();});
            case "OPEN_BARRIER" -> {
                List<String> groups = barriers.stream().map(String::valueOf).toList();
                if (groups.isEmpty()) heading("no_barriers");
                else selection("barrier_group", value(action, "barrierGroup", groups.get(0)), groups, "", selected -> {
                    action.addProperty("barrierGroup", Integer.parseInt(selected)); rebuildWidgets();
                });
            }
        }
    }

    private void inventoryPicker(JsonObject target, String itemKey, String countKey) {
        rowButton("inventory_picker", () -> {
            if (minecraft.player == null) return;
            List<ZombiesNpcStackPickerScreen.Entry> entries = new ArrayList<>();
            var inventory = minecraft.player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var stack = inventory.getItem(slot);
                if (!stack.isEmpty()) entries.add(new ZombiesNpcStackPickerScreen.Entry(stack.copy(), stack.save(new CompoundTag()).toString(), com.cdp.codpattern.client.zombies.ZombiesGunNames.display(stack).getString(), stack.getCount()));
            }
            minecraft.setScreen(new ZombiesNpcStackPickerScreen(this, tr("inventory_picker"), entries, selected -> {
                target.addProperty(itemKey, selected.value()); target.addProperty(countKey, selected.count()); rebuildWidgets();
            }));
        });
    }

    private void blockPlacements(JsonObject action) {
        JsonArray placements = action.has("placements") ? action.getAsJsonArray("placements") : new JsonArray();
        rowButton("add_placement", () -> {
            if (placements.size() >= 256) return;
            if (placements.isEmpty()) {
                JsonObject original = new JsonObject(); original.add("position", action.get("position").deepCopy());
                original.addProperty("block", value(action, "block", "minecraft:stone")); placements.add(original);
                action.add("placements", placements);
            }
            JsonObject next = new JsonObject(); next.add("position", ZombiesNpcEditorDraft.position(currentPosition()));
            next.addProperty("block", value(placements.isEmpty()?action:placements.get(placements.size()-1).getAsJsonObject(), "block", "minecraft:stone")); placements.add(next);
            placementIndex = placements.size() - 1; rebuildWidgets();
        });
        int count = placements.isEmpty()?1:placements.size();
        for(int i=0;i<count;i++) {
        final int index=i;
        JsonObject placement=placements.isEmpty()?action:placements.get(i).getAsJsonObject();
        final int label=i+1;
        rows.add((x,y,w)->labels.add(new Label(tr("list.placement",label),x,y+6,w)));
        position(placement, "position"); text("block", placement, "block", "minecraft:stone");
        rowButton("block_picker", () -> {
            List<ZombiesNpcStackPickerScreen.Entry> entries = new ArrayList<>();
            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (!ZombiesNpcRuntime.supportsStoryBlock(block.defaultBlockState())) continue;
                entries.add(new ZombiesNpcStackPickerScreen.Entry(new net.minecraft.world.item.ItemStack(block.asItem()),
                        BlockStateParser.serialize(block.defaultBlockState()), block.getName().getString(), 1));
            }
            minecraft.setScreen(new ZombiesNpcStackPickerScreen(this, tr("block_picker"), entries, selected -> {
                placement.addProperty("block", selected.value()); rebuildWidgets();
            }));
        });
                rowButton("looked_block", () -> {
                    if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                        placement.add("position", ZombiesNpcEditorDraft.position(new ZombiesNpcConfig.Position(hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ(), 0)));
                        placement.addProperty("block", BlockStateParser.serialize(minecraft.level.getBlockState(hit.getBlockPos()))); rebuildWidgets();
                    } else message = tr("error.no_block");
                });
        rows.add((x, y, w) -> button(tr("delete_placement"), x, y, w, () -> {
            placements.remove(index); rebuildWidgets();
        }, canEdit() && placements.size() > 1));
        }
    }

    private void appearancePatch(JsonObject action) {
        JsonObject patch = action.getAsJsonObject("appearance");
        if (patch == null) { patch = new JsonObject(); action.add("appearance", patch); }
        optional(patch, "name", "name", new JsonPrimitive(""));
        if (patch.has("name")) text("name", patch, "name", "");
        optional(patch, "chatName", "chat_name", new JsonPrimitive(""));
        if (patch.has("chatName")) text("chat_name", patch, "chatName", "");
        optional(patch, "nameColor", "name_color", new JsonPrimitive("#FFFFFF"));
        if (patch.has("nameColor")) color("name_color", patch, "nameColor");
        optional(patch, "textColor", "text_color", new JsonPrimitive("#FFFFFF"));
        if (patch.has("textColor")) color("text_color", patch, "textColor");
        optional(patch, "skin", "skin", new JsonPrimitive("minecraft:textures/entity/player/wide/steve.png"));
        if (patch.has("skin")) skin(patch);
        optional(patch, "pose", "pose", new JsonPrimitive("STANDING"));
        if (patch.has("pose")) choice("pose", patch, "pose", List.of("STANDING", "SITTING", "LYING", "GROUND_SITTING", "SUPINE", "KNEELING"), "STANDING", "pose.");
        optional(patch, "alive", "alive", new JsonPrimitive(true));
        if (patch.has("alive")) bool("alive", patch, "alive", true);
        optional(patch, "invulnerable", "invulnerable", new JsonPrimitive(true));
        if (patch.has("invulnerable")) bool("invulnerable", patch, "invulnerable", true);
        optional(patch,"combat","combat",com.google.gson.JsonParser.parseString("{\"mode\":\"OFF\",\"gun\":\"\",\"range\":24}"));
        if(patch.has("combat"))combat(patch);
        optional(patch, "position", "position", ZombiesNpcEditorDraft.position(currentPosition()));
        if (patch.has("position")) position(patch, "position");
    }

    private void combat(JsonObject appearance){
        if(!appearance.has("combat"))appearance.add("combat",com.google.gson.JsonParser.parseString("{\"mode\":\"OFF\",\"gun\":\"\",\"range\":24}"));
        var config=appearance.getAsJsonObject("combat");
        choice("combat",config,"mode",List.of("OFF","GUARD","FOLLOW"),"OFF","combat.");
        text("combat.range",config,"range","24");
        String selected=tr("combat.none");
        try{selected=net.minecraft.world.item.ItemStack.of(net.minecraft.nbt.TagParser.parseTag(value(config,"gun",""))).getHoverName().getString();}catch(Exception ignored){}
        final String caption=selected;
        rows.add((x,y,w)->labels.add(new Label(tr("combat.gun",caption),x,y+6,w)));
        rowButton("combat.read",()->{
            if(minecraft.player==null)return;
            var entries=new ArrayList<ZombiesNpcStackPickerScreen.Entry>();var inv=minecraft.player.getInventory();
            for(int i=0;i<inv.getContainerSize();i++){var stack=inv.getItem(i);if(com.tacz.guns.api.item.IGun.getIGunOrNull(stack)!=null)entries.add(new ZombiesNpcStackPickerScreen.Entry(stack.copy(),stack.save(new CompoundTag()).toString(),com.cdp.codpattern.client.zombies.ZombiesGunNames.display(stack).getString(),1));}
            minecraft.setScreen(new ZombiesNpcStackPickerScreen(this,tr("combat.read"),entries,chosenGun->{config.addProperty("gun",chosenGun.value());rebuildWidgets();}));
        });
        if(value(config,"mode","OFF").equals("FOLLOW"))rowButton("combat.clear",()->{config.addProperty("gun","");rebuildWidgets();});
    }
    private void optional(JsonObject object, String key, String label, JsonElement defaultValue) {
        rows.add((x, y, w) -> button(tr(object.has(key) ? "change" : "keep", tr(label)), x, y, w, () -> {
            if (object.has(key)) {object.remove(key);if(key.equals("skin"))object.remove("playerSkin");} else object.add(key, defaultValue.deepCopy());
            rebuildWidgets();
        }, canEdit()));
    }
    private void position(JsonObject object, String key) {
        JsonObject position = object.getAsJsonObject(key);
        rowButton("current_position", () -> { object.add(key, ZombiesNpcEditorDraft.position(currentPosition())); rebuildWidgets(); });
        for (String axis : List.of("x", "y", "z", "yaw")) text(axis, position, axis, "0");
    }
    private ZombiesNpcConfig.Position currentPosition() {
        var player = minecraft.player;
        return player == null ? new ZombiesNpcConfig.Position(0, 64, 0, 0)
                : new ZombiesNpcConfig.Position(player.getX(), player.getY(), player.getZ(), player.getYRot());
    }
    private void multiline(String label, JsonObject object, String key) {
        List<String> lines = new ArrayList<>(Arrays.asList(value(object, key, "").split("\n", -1)));
        for (int i = 0; i < lines.size(); i++) {
            int index = i;
            rows.add((x, y, w) -> {
                input(label, lines.get(index), next -> { lines.set(index, next); object.addProperty(key, String.join("\n", lines)); }, x, y, w - 26);
                button("−", x + w - 22, y, 22, () -> {
                    lines.remove(index); object.addProperty(key, String.join("\n", lines)); rebuildWidgets();
                }, canEdit());
            });
        }
        rowButton("add_line", () -> { object.addProperty(key, value(object, key, "") + "\n"); rebuildWidgets(); });
    }
    private void text(String label, JsonObject object, String key, String fallback) {
        text(label, value(object, key, fallback), next -> object.addProperty(key, next));
    }
    private void color(String label, JsonObject object, String key) {
        text(label, object, key, "#FFFFFF");
        choice("color_preset", object, key, List.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
                "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"), "white", "color.");
    }
    private void text(String label, String value, Consumer<String> change) {
        rows.add((x, y, w) -> input(label, value, change, x, y, w));
    }
    private void input(String label, String value, Consumer<String> change, int x, int y, int w) {
        int labelWidth = Math.min(100, w / 3);
        labels.add(new Label(tr(label), x, y + 6, labelWidth - 5));
        EditBox box = addRenderableWidget(new EditBox(font, x + labelWidth, y, w - labelWidth, 20, Component.literal(tr(label))));
        box.setMaxLength(32767); box.setValue(value); box.setResponder(change); box.setEditable(canEdit());
        box.setTooltip(Tooltip.create(Component.literal(tr(label)))); textBoxes.add(box);
    }
    private void bool(String label, JsonObject object, String key, boolean fallback) {
        selection(label, value(object, key, Boolean.toString(fallback)), List.of("false", "true"), "bool.", selected -> {
            object.addProperty(key, Boolean.parseBoolean(selected)); rebuildWidgets();
        });
    }
    private void choice(String label, JsonObject object, String key, List<String> options, String fallback, String translation) {
        selection(label, value(object, key, fallback), options, translation, selected -> { object.addProperty(key, selected); rebuildWidgets(); });
    }
    private void selection(String label, String selected, List<String> choices, String translation, Consumer<String> change) {
        rows.add((x, y, w) -> {
            int labelWidth = Math.min(100, w / 3);
            labels.add(new Label(tr(label), x, y + 6, labelWidth - 5));
            String caption = selected.isEmpty() ? tr("self") : translation.isEmpty() || selected.startsWith("#") ? selected : tr(translation + selected);
            button(caption + " ▾", x + labelWidth, y, w - labelWidth, () -> {
                if (choices.size() > 2) {
                    minecraft.setScreen(new ZombiesNpcChoiceScreen(this, tr(label), choices, selected,
                            option -> option.isEmpty() ? tr("self") : translation.isEmpty() ? option : tr(translation + option), change));
                } else {
                    int next = (choices.indexOf(selected) + 1) % choices.size(); change.accept(choices.get(next));
                }
            }, canEdit() && !choices.isEmpty());
        });
    }
    private void toggleMember(String caption, JsonArray values, JsonElement member) {
        rows.add((x, y, w) -> button((values.contains(member) ? "☑ " : "☐ ") + caption, x, y, w, () -> {
            if (values.contains(member)) values.remove(member); else values.add(member);
            rebuildWidgets();
        }, canEdit()));
    }
    private void reorder(JsonArray array, int index, Consumer<Integer> change) {
        rows.add((x, y, w) -> {
            for (int direction : List.of(-1, 1)) {
                int destination = index + direction;
                button(tr(direction < 0 ? "up" : "down"), x + (direction < 0 ? 0 : w / 2 + 2), y, w / 2 - 2, () -> {
                    JsonElement swapped = array.get(destination); array.set(destination, array.get(index)); array.set(index, swapped);
                    change.accept(destination); rebuildWidgets();
                }, canEdit() && destination >= 0 && destination < array.size());
            }
        });
    }
    private void heading(String key) { rows.add((x, y, w) -> labels.add(new Label(tr(key), x, y + 6, w))); }
    private void rowButton(String key, Runnable action) { rows.add((x, y, w) -> button(tr(key), x, y, w, action, canEdit())); }
    private Button button(String caption, int x, int y, int w, Runnable action, boolean enabled) {
        var button = addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(caption, Math.max(8, w - 8))), ignored -> {
            try { action.run(); } catch (RuntimeException failure) { message = detail(failure); }
        }).bounds(x, y, Math.max(18, w), 20).build());
        button.active = enabled; button.setTooltip(Tooltip.create(Component.literal(caption))); return button;
    }

    private void save(Runnable next) {
        if (!canEdit()) return;
        try { request("save", draft.request(revision), next); }
        catch (RuntimeException failure) { message = detail(failure); }
    }
    private void footer() {
        if (graphReturn != null) {
            button(tr("graph.back"), 12, height - 34, width - 24, this::returnToGraph, true);
            return;
        }
        int w = (width - 32) / 3;
        button(tr("save"), 12, height - 60, w, () -> save(null), canEdit());
        button(tr("preview"), 16 + w, height - 60, w, () -> save(() -> request("preview", "", this::collapse)), canEdit());
        button(tr("clear_preview"), 20 + w * 2, height - 60, w, () -> request("clear", "", null), canEdit());
        button(tr("back"), 12, height - 34, w, this::onClose, !busy());
        button(tr("collapse"), 16 + w, height - 34, w, this::collapse, !busy());
        button(tr("reload"), 20 + w * 2, height - 34, w, () -> confirmDiscard(this::load), !busy());
    }
    private void collapse() { suspended = true; minecraft.setScreen(null); }
    private void confirmDiscard(Runnable next) {
        if (draft == null || !draft.dirty()) { next.run(); return; }
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this); if (confirmed) next.run();
        }, Component.literal(tr("unsaved")), Component.literal(tr("discard_warning")), Component.literal(tr("discard")), Component.literal(tr("continue_editing"))));
    }
    @Override public void onClose() {
        if (graphReturn != null) { returnToGraph(); return; }
        if (busy()) return;
        confirmDiscard(() -> { ZombiesDeployClientActionHandler.clearNpcEditor(this); minecraft.setScreen(parent); });
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (busy() || delta == 0 || draft == null) return true;
        int direction = delta > 0 ? -1 : 1;
        if (x < formX) npcScroll += direction; else scroll += direction;
        rebuildWidgets(); return true;
    }
    @Override public void tick() {
        textBoxes.forEach(EditBox::tick);
        if (busy() && System.currentTimeMillis() > deadline) {
            pending = -1; afterReply = null; message = tr("error.timeout"); rebuildWidgets();
        }
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (hasControlDown() && key == GLFW.GLFW_KEY_S) {
            if (graphReturn != null) { var graph = graphReturn; returnToGraph(); graph.saveGraph(); }
            else save(null);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE512191D);
        graphics.drawString(font, title, 12, 10, 0xFFE8EFED, false);
        String subtitle = mapName() + (draft != null && draft.dirty() ? "  * " + tr("unsaved") : "") + (!editable && draft != null ? "  " + tr("read_only") : "");
        graphics.drawString(font, font.plainSubstrByWidth(subtitle, width - 24), 12, 26, 0xFF7FD6A0, false);
        if (graphReturn == null) graphics.fill(formX - 7, top, formX - 6, bottom, 0x605B796D);
        for (Label label : labels) graphics.drawString(font, font.plainSubstrByWidth(label.text, label.width), label.x, label.y, 0xFFE8EFED, false);
        graphics.drawString(font, font.plainSubstrByWidth(message, width - 24), 12, height - 77, 0xFFFFCD78, false);
        if (rows.size() > visibleRows()) {
            int total = Math.max(1, bottom - top);
            int thumb = Math.max(6, total * visibleRows() / rows.size());
            int offset = (total - thumb) * scroll / Math.max(1, rows.size() - visibleRows());
            graphics.fill(width - 5, top + offset, width - 3, top + offset + thumb, 0xFF7FD6A0);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (mouseY >= height - 82 && mouseY < height - 65 && !message.isBlank()) graphics.renderTooltip(font, Component.literal(message), mouseX, mouseY);
    }
    void openGraph() {
        if (npc() == null) { message = tr("empty"); return; }
        if (graphScreen == null) graphScreen = new ZombiesNpcGraphScreen(this);
        minecraft.setScreen(graphScreen);
    }
    ZombiesNpcEditorDraft graphDraft() { return draft; }
    JsonObject graphNpc() { return npc(); }
    JsonObject graphRule() { return rule(); }
    boolean graphEditable() { return canEdit(); }
    List<String> graphEvents(){return events;}
    List<Integer> graphBarriers() { return barriers; }
    String graphMessage() { return message; }
    int graphNpcIndex() { return npcIndex; }
    int graphRuleIndex() { return ruleIndex; }
    void graphSelect(int npc, int rule) { npcIndex = npc; ruleIndex = rule; actionIndex = 0; clampSelection(); }
    void graphSave() { save(null); if (graphScreen != null) graphScreen.refresh(); }
    void graphEdit(ZombiesNpcGraphScreen graph, JsonObject action) {
        looseActionEditing = action != null && draft.looseNodes().asList().stream().anyMatch(e -> e.getAsJsonObject().get("action") == action);
        graphReturn = graph; graphAction = action; page = action == null ? 1 : 2; scroll = 0;
        minecraft.setScreen(this);
    }
    private void returnToGraph() {
        var graph = graphReturn; graphReturn = null; graphAction = null; looseActionEditing = false; page = 0; scroll = 0;
        minecraft.setScreen(graph);
    }
    void graphBack() { page = 0; scroll = 0; minecraft.setScreen(this); }
    private static String detail(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
    private interface FormRow { void draw(int x, int y, int width); }
    private record Label(String text, int x, int y, int width) { }
}
