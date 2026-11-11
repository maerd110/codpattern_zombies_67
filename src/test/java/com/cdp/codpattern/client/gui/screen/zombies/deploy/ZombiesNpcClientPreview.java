package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployDraft;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeploySnapshot;
import com.cdp.codpattern.app.zombies.npc.*;
import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.phasetranscrystal.fpsmatch.common.packet.zombies.OpenZombiesDeployToolScreenS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Test-only client render checks, staged exclusively by -PnpcClientPreview. */
@Mod.EventBusSubscriber(modid = ZombiesAddonConstants.MOD_ID, value = Dist.CLIENT)
public final class ZombiesNpcClientPreview {
    private static boolean changingLanguage;
    private static boolean finished;
    private static java.util.concurrent.CompletableFuture<Void> languageReload;
    @Mod.EventBusSubscriber(modid = ZombiesAddonConstants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            if (Boolean.getBoolean("codpattern.npc.clientPreview")) event.enqueueWork(() ->
                    GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow()));
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (!Boolean.getBoolean("codpattern.npc.clientPreview") || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (languageReload != null && languageReload.isDone()) {
            languageReload.join();
            // Hidden windows need no splash-screen fade-out before off-screen capture.
            client.setOverlay(null);
        }
        if (client.getOverlay() == null && client.screen != null
                && client.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
            client.setScreen(new TitleScreen());
        }
        if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
        if (!changingLanguage) {
            changingLanguage = true;
            client.options.languageCode = "zh_cn";
            client.getLanguageManager().setSelected("zh_cn");
            languageReload = client.reloadResourcePacks();
            return;
        }
        finished = true;
        Path output = Path.of(System.getProperty("codpattern.npc.clientPreviewOutput"));
        Files.createDirectories(output);
        try {
            var snapshot = ZombiesNpcRepository.GSON.fromJson("{\"selectedMap\":\"剧情测试关卡\",\"workspaceStage\":\"object_marking\"}", ZombiesDeploySnapshot.class);
            var parent = new ZombiesDeployToolScreen(new OpenZombiesDeployToolScreenS2CPacket(snapshot));
            render(parent, 640, 360, output.resolve("deployment-entry.png"));
            var screen = new ZombiesNpcDeployScreen(parent, new ZombiesDeployDraft("剧情测试关卡", "INITIAL", -1, "MVP1_MINIMAL", java.util.Map.of()));
            var working = new ZombiesNpcEditorDraft(fixture());
            set(screen, "draft", working);
            set(screen, "editable", true); set(screen, "npcIndex", 0); set(screen, "ruleIndex", 0); set(screen, "actionIndex", 0);
            set(screen, "barriers", List.of(1, 3, 5));
            for (int page = 0; page < 3; page++) {
                set(screen, "page", page); set(screen, "scroll", 0);
                render(screen, 640, 360, output.resolve("page-" + page + "-640.png"));
                if (page == 1 && button(screen, "新增触发规则").getY() >= button(screen, "delivery ▾").getY())
                    throw new AssertionError("Add rule must precede rule selection");
                if (page == 2 && (button(screen, "新增执行动作").getY() >= button(screen, "delivery ▾").getY()
                        || button(screen, "delivery ▾").getY() >= button(screen, "1. 玩家交出物品 ▾").getY()))
                    throw new AssertionError("Action page order must be add action, current rule, current action");
                render(screen, 320, 240, output.resolve("page-" + page + "-320.png"));
            }
            set(screen, "page", 0); set(screen, "scroll", 5);
            render(screen, 640, 360, output.resolve("dialogue-640.png"));
            set(screen, "page", 2); set(screen, "actionIndex", 3); set(screen, "scroll", 5);
            render(screen, 640, 360, output.resolve("state-patch-640.png"));
            var choices = new ZombiesNpcChoiceScreen(screen, "动作类型", List.of("给予物品", "交出物品", "打开屏障", "改变 NPC 状态"), "打开屏障", value -> value, ignored -> { });
            render(choices, 320, 240, output.resolve("choices-320.png"));
            var selected = new java.util.concurrent.atomic.AtomicReference<ZombiesNpcStackPickerScreen.Entry>();
            var inventory = new ZombiesNpcStackPickerScreen(screen, "从背包中选择物品", List.of(
                    new ZombiesNpcStackPickerScreen.Entry(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 3), "minecraft:emerald", "绿宝石", 3),
                    new ZombiesNpcStackPickerScreen.Entry(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 2), "minecraft:diamond", "钻石", 2)), selected::set);
            render(inventory, 320, 240, output.resolve("inventory-320.png"));
            inventory.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                    .map(child -> (net.minecraft.client.gui.components.Button) child).filter(button -> button.getMessage().getString().isEmpty()).findFirst().orElseThrow().onPress();
            if (selected.get() == null || selected.get().count() != 3) throw new AssertionError("Inventory icon selection callback");
            var blocks = new ZombiesNpcStackPickerScreen(screen, "选择部署方块", List.of(
                    new ZombiesNpcStackPickerScreen.Entry(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE), "minecraft:stone", "石头", 1),
                    new ZombiesNpcStackPickerScreen.Entry(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS), "minecraft:oak_planks", "橡木木板", 1)), selected::set);
            render(blocks, 320, 240, output.resolve("blocks-320.png"));
            set(screen, "page", 0); set(screen, "scroll", 0); screen.init(client, 640, 360);
            var name = screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox)
                    .map(child -> (net.minecraft.client.gui.components.EditBox) child)
                    .filter(box -> box.getMessage().getString().equals("显示名称")).findFirst().orElseThrow();
            name.setValue("新的向导");
            if (!working.validated().npcs().get(0).appearance().name().equals("新的向导")) throw new AssertionError("Text editor is detached from draft");
            for (var edit : java.util.Map.of("聊天名字（留空用显示名称）", "电台向导", "聊天名字颜色（#RRGGBB）", "gold", "对话正文颜色（#RRGGBB）", "#00ffaa").entrySet()) {
                screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox)
                        .map(child -> (net.minecraft.client.gui.components.EditBox) child)
                        .filter(box -> box.getMessage().getString().equals(edit.getKey())).findFirst().orElseThrow().setValue(edit.getValue());
            }
            screen.init(client, 640, 360);
            button(screen, "金色 ▾").onPress();
            var palette = client.screen;
            if (!(palette instanceof ZombiesNpcChoiceScreen)) throw new AssertionError("Color presets open graphical choices");
            render(palette, 320, 240, output.resolve("chat-colors-320.png"));
            palette.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox)
                    .map(child -> (net.minecraft.client.gui.components.EditBox) child).findFirst().orElseThrow().setValue("aqua");
            button(palette, "青色").onPress();
            var styled = working.validated().npcs().get(0).appearance();
            if (!styled.chatName().equals("电台向导") || !styled.nameColor().equals("aqua") || !styled.textColor().equals("#00ffaa"))
                throw new AssertionError("Chat name, hex color and preset selection persist in draft");
            render(screen, 640, 360, output.resolve("chat-settings-640.png"));
            press(screen, "新增 NPC");
            if (working.npcs().size() != 3) throw new AssertionError("Add NPC button");
            if (working.validated().npcs().get(2).dialogueIntervalSeconds() != 1) throw new AssertionError("New NPC defaults to one second");
            set(screen, "page", 1); screen.init(client, 640, 360); press(screen, "新增触发规则");
            if (working.validated().npcs().get(2).rules().get(0).actionIntervalSeconds() != 1) throw new AssertionError("New rule defaults to one second");
            set(screen, "page", 2); screen.init(client, 640, 360); press(screen, "新增执行动作");
            if (working.validated().npcs().get(2).rules().get(0).actions().size() != 2) throw new AssertionError("Add rule/action buttons");
            var firstAction = working.npcs().get(2).getAsJsonObject().getAsJsonArray("rules").get(0).getAsJsonObject().getAsJsonArray("actions").get(0).getAsJsonObject();
            ZombiesNpcEditorDraft.setKind(firstAction, ZombiesNpcConfig.Kind.SET_BLOCK, new ZombiesNpcConfig.Position(1, 64, 2, 0));
            set(screen, "actionIndex", 0); set(screen, "scroll", 0); screen.init(client, 640, 480);
            press(screen, "新增方块放置位置");
            if (working.validated().npcs().get(2).rules().get(0).actions().get(0).blockPlacements().size() != 2) throw new AssertionError("Multiple block GUI placements");
            render(screen, 640, 480, output.resolve("multi-blocks-640.png"));
            render(screen,1280,1300,output.resolve("all-placements-1280.png"));
            if(screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox b && b.getMessage().getString().equals("方块及状态")).count()!=2)
                throw new AssertionError("All block placements must expose their own fields");
            graphChecks(screen, working, client, output);
            sharedGraphChecks(screen,client,output);
            Files.writeString(output.resolve("result.txt"), "PASS 30 client renders, expanded condition/placement lists, explicit actor possession choices, retry-end controls and shared graph editing\n");
        } catch (Throwable failure) {
            Files.writeString(output.resolve("result.txt"), "FAIL " + failure + "\n");
            throw failure;
        } finally { client.stop(); }
    }

    private static void graphChecks(ZombiesNpcDeployScreen owner, ZombiesNpcEditorDraft working, Minecraft client, Path output) throws Exception {
        owner.graphSelect(0, 0); owner.init(client, 960, 540); press(owner, "逻辑画布");
        if (!(client.screen instanceof ZombiesNpcGraphScreen graph)) throw new AssertionError("NPC tool opens native graph canvas");
        if (owner.graphRule().getAsJsonObject("graph").getAsJsonArray("nodes").size() != 6) throw new AssertionError("Legacy actions migrate into entry, actions and end");
        render(graph, 960, 540, output.resolve("graph-migrated-960.png"));
        press(graph, "新增触发规则");
        render(graph, 960, 540, output.resolve("graph-new-rule-960.png"));
        var rule = owner.graphRule();
        press(graph, "+ 条件分支"); bindLatest(graph,owner); press(graph, "+ 等待时间"); bindLatest(graph,owner);
        graph.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.EditBox)
                .map(c -> (net.minecraft.client.gui.components.EditBox)c).filter(b -> b.getMessage().getString().equals("等待时间（秒）")).findFirst().orElseThrow().setValue("0.5");
        if (ZombiesNpcGraphDraft.find(rule,"node_5").get("seconds").getAsDouble()!=.5) throw new AssertionError("Wait inspector edits duration");
        press(graph,"删除节点");press(graph,"撤销");
        rule=owner.graphRule();
        if(ZombiesNpcGraphDraft.nodes(rule).size()!=5) throw new AssertionError("Undo restores deleted node");
        // History restores rule objects; read through the owner after undo/redo.
        rule=owner.graphRule();press(graph,"重做");rule=owner.graphRule();
        if(ZombiesNpcGraphDraft.nodes(rule).size()!=4) throw new AssertionError("Redo deletes restored node");
        press(graph,"+ 动作");
        var picker=client.screen;
        picker.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).map(c->(net.minecraft.client.gui.components.EditBox)c).findFirst().orElseThrow().setValue("DIALOGUE");
        button(picker,"● " + net.minecraft.network.chat.Component.translatable("gui.codpattern.zombies.npc.kind.DIALOGUE").getString()).onPress();
        bindLatest(graph,owner);rule=owner.graphRule();
        double baseY=ZombiesNpcGraphDraft.find(rule,"node_1").get("y").getAsDouble();
        String[][] positions={{"node_1","20","160"},{"node_4","230","160"},{"node_2","440","60"},{"node_5","440","280"},{"node_3","650","160"}};
        for(var p:positions){var n=ZombiesNpcGraphDraft.find(rule,p[0]);n.addProperty("x",Double.parseDouble(p[1]));n.addProperty("y",baseY+Double.parseDouble(p[2]));}
        set(graph,"selected","node_2");graph.init(client,960,540);
        graph.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).map(c->(net.minecraft.client.gui.components.EditBox)c).filter(b->b.getMessage().getString().equals("对话内容")).findFirst().orElseThrow().setValue("准备好，通道即将开启。");
        ZombiesNpcGraphDraft.find(rule,"node_5").getAsJsonObject("action").addProperty("text","请先找来绿宝石。");
        set(graph,"selected","node_4");graph.init(client,960,540);press(graph,"波次达到 ▾");picker=client.screen;
        picker.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).map(c->(net.minecraft.client.gui.components.EditBox)c).findFirst().orElseThrow().setValue("ITEM_ACTOR");
        button(picker,"交互玩家持有物品").onPress();
        render(graph,1280,720,output.resolve("graph-branch-before-wiring.png"));press(graph,"适应画布");
        connect(graph,rule,"node_1","next","node_4");connect(graph,rule,"node_4","next","node_2");connect(graph,rule,"node_4","onFalse","node_5");
        connect(graph,rule,"node_2","next","node_3");connect(graph,rule,"node_5","next","node_3");
        working.validated();
        connect(graph,rule,"node_2","next","node_4");
        if(!ZombiesNpcGraphDraft.find(rule,"node_2").get("next").getAsString().equals("node_3"))throw new AssertionError("Canvas rejects cyclic wire without replacing existing output");
        var entry=ZombiesNpcGraphDraft.find(rule,"node_1");double z=(double)get(graph,"zoom"),px=(double)get(graph,"panX"),py=(double)get(graph,"panY");
        double ex=8+px+(entry.get("x").getAsDouble()+170)*z,ey=94+py+(entry.get("y").getAsDouble()+43)*z;
        graph.mouseClicked(ex,ey,1);
        if(!entry.get("next").getAsString().isEmpty())throw new AssertionError("Right-click disconnects output");
        connect(graph,rule,"node_1","next","node_4");
        var action=ZombiesNpcGraphDraft.find(rule,"node_2");double ax=8+px+(action.get("x").getAsDouble()+50)*z,ay=94+py+(action.get("y").getAsDouble()+10)*z;
        double oldX=action.get("x").getAsDouble();graph.mouseClicked(ax,ay,0);graph.mouseDragged(ax+24,ay+12,0,24,12);graph.mouseReleased(ax+24,ay+12,0);
        if(action.get("x").getAsDouble()<=oldX)throw new AssertionError("Node drag persists canvas coordinates");
        double oldPan=(double)get(graph,"panX");graph.mouseClicked(180,620,2);graph.mouseDragged(200,620,2,20,0);graph.mouseReleased(200,620,2);
        if((double)get(graph,"panX")<=oldPan)throw new AssertionError("Background pan");
        double oldZoom=(double)get(graph,"zoom");graph.mouseScrolled(300,300,1);if((double)get(graph,"zoom")<=oldZoom)throw new AssertionError("Wheel zoom");
        set(graph,"selected","node_2");graph.init(client,960,540);press(graph,"节点属性");
        if(client.screen!=owner)throw new AssertionError("Action property editor opens from node");
        render(owner,640,360,output.resolve("graph-action-properties-640.png"));press(owner,"返回逻辑画布");
        if(client.screen!=graph || !ZombiesNpcGraphDraft.find(owner.graphRule(),"node_2").getAsJsonObject("action").get("text").getAsString().equals("准备好，通道即将开启。"))throw new AssertionError("Node editor returns to same graph and retains inline dialogue");
        set(graph,"selected","node_4");graph.init(client,1280,720);press(graph,"适应画布");
        render(graph,1280,720,output.resolve("graph-final-1280.png"));render(graph,320,240,output.resolve("graph-small-320.png"));
        press(graph,"节点属性");render(graph,320,240,output.resolve("graph-inspector-320.png"));
        set(graph,"overlay",false);set(graph,"selected","node_1");graph.init(client,960,540);press(graph,"节点属性");
        set(owner,"scroll",100);render(owner,640,360,output.resolve("graph-rule-properties-640.png"));
        String currentRule=owner.graphRule().get("id").getAsString();press(owner,"上移");press(owner,"返回逻辑画布");
        if(owner.graphRuleIndex()!=0 || !owner.graphRule().get("id").getAsString().equals(currentRule))throw new AssertionError("Entry properties retain rule-priority ordering");
        working.validated();
    }
    private static void sharedGraphChecks(ZombiesNpcDeployScreen previous,Minecraft client,Path output)throws Exception{
        var owner=new ZombiesNpcDeployScreen((ZombiesDeployToolScreen)get(previous,"parent"),new ZombiesDeployDraft("协同剧情关卡","INITIAL",-1,"MVP1_MINIMAL",java.util.Map.of()));
        var draft=new ZombiesNpcEditorDraft(ZombiesNpcConfig.empty());
        var doctor=draft.addNpc(new ZombiesNpcConfig.Position(1,64,1,0));draft.renameNpc(doctor,"doctor");doctor.getAsJsonObject("appearance").addProperty("name","医生");
        var trade=draft.addRule(doctor);draft.renameRule(doctor,trade,"trade");trade.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("text","请帮我找到药品。");
        var follow=draft.addRule(doctor);draft.renameRule(doctor,follow,"followup");follow.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("text","药品收到了，去找守卫吧。");
        var guard=draft.addNpc(new ZombiesNpcConfig.Position(5,64,1,0));draft.renameNpc(guard,"guard");guard.getAsJsonObject("appearance").addProperty("name","守卫");
        var welcome=draft.addRule(guard);draft.renameRule(guard,welcome,"welcome");welcome.addProperty("trigger","AUTO");welcome.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("text","通道已开启，请继续前进。");
        set(owner,"draft",draft);set(owner,"editable",true);owner.graphSelect(0,0);owner.init(client,1280,720);press(owner,"逻辑画布");
        var graph=(ZombiesNpcGraphScreen)client.screen;graph.init(client,1280,720);press(graph,"适应画布");
        if(((java.util.Map<?,?>)get(graph,"index")).size()!=9)throw new AssertionError("All rules and both NPCs are visible on one canvas");
        crossConnect(graph,trade,"node_1","completed",follow,"node_1");crossConnect(graph,follow,"node_1","completed",welcome,"node_1");
        if(!follow.getAsJsonArray("requires").get(0).getAsString().equals("doctor.trade")||!welcome.getAsJsonArray("requires").get(0).getAsString().equals("doctor.followup"))throw new AssertionError("Cross-rule wires compile into completion dependencies");
        crossConnect(graph,welcome,"node_1","completed",trade,"node_1");
        if(!trade.getAsJsonArray("requires").isEmpty())throw new AssertionError("Cross-rule cycle must not alter prerequisites");
        // Pick the guard node by mouse, despite all flows using the same local node_2 ID.
        var n=ZombiesNpcGraphDraft.find(welcome,"node_2");double z=(double)get(graph,"zoom"),px=(double)get(graph,"panX"),py=(double)get(graph,"panY");
        double x=8+px+(n.get("x").getAsDouble()+50)*z,y=94+py+(n.get("y").getAsDouble()+10)*z;
        graph.mouseClicked(x,y,0);graph.mouseReleased(x,y,0);
        if(!owner.graphNpc().get("id").getAsString().equals("guard"))throw new AssertionError("Selecting another NPC activates its own property context");
        graph.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).map(c->(net.minecraft.client.gui.components.EditBox)c).filter(b->b.getMessage().getString().equals("对话内容")).findFirst().orElseThrow().setValue("医生让我为你开启通道。");
        if(!ZombiesNpcGraphDraft.find(welcome,"node_2").getAsJsonObject("action").get("text").getAsString().equals("医生让我为你开启通道。")||ZombiesNpcGraphDraft.find(trade,"node_2").getAsJsonObject("action").get("text").getAsString().equals("医生让我为你开启通道。"))throw new AssertionError("Repeated local node IDs do not mix NPC data");
        render(graph,1280,720,output.resolve("shared-canvas-1280.png"));
        var entry=ZombiesNpcSharedCanvas.completion(ZombiesNpcSharedCanvas.find(draft,"doctor.followup"));z=(double)get(graph,"zoom");px=(double)get(graph,"panX");py=(double)get(graph,"panY");
        graph.mouseClicked(8+px+(entry.get("x").getAsDouble()+170)*z,94+py+(entry.get("y").getAsDouble()+43)*z,1);
        if(!welcome.getAsJsonArray("requires").isEmpty())throw new AssertionError("Completion output disconnects outgoing rule dependencies");
        press(graph,"撤销");welcome=ZombiesNpcSharedCanvas.find(draft,"guard.welcome").rule();trade=ZombiesNpcSharedCanvas.find(draft,"doctor.trade").rule();
        if(welcome.getAsJsonArray("requires").size()!=1)throw new AssertionError("Global undo restores another NPC's dependency");
        var f=ZombiesNpcSharedCanvas.find(draft,"doctor.trade");entry=f.entry();z=(double)get(graph,"zoom");px=(double)get(graph,"panX");py=(double)get(graph,"panY");
        double oldX=entry.get("x").getAsDouble(),oldOther=ZombiesNpcGraphDraft.find(welcome,"node_1").get("x").getAsDouble();
        x=8+px+(entry.get("x").getAsDouble()+20)*z;y=94+py+(entry.get("y").getAsDouble()-20)*z;
        graph.mouseClicked(x,y,0);graph.mouseDragged(x+35,y+10,0,35,10);graph.mouseReleased(x+35,y+10,0);
        double moved=f.entry().get("x").getAsDouble()-oldX;
        if(moved<=0 || Math.abs(f.nodes().get(1).get("x").getAsDouble()-250-moved)>.001 || ZombiesNpcGraphDraft.find(welcome,"node_1").get("x").getAsDouble()!=oldOther)throw new AssertionError("Header drag moves exactly one complete rule group");
        press(graph,"撤销");press(graph,"重做");
        f=ZombiesNpcSharedCanvas.find(draft,"doctor.trade");if(f.entry().get("x").getAsDouble()<=oldX)throw new AssertionError("Global redo restores group movement");
        press(graph,"定位当前规则");if(((java.util.Map<?,?>)get(graph,"index")).size()!=9)throw new AssertionError("Focus moves the viewport without hiding other NPCs");
        render(graph,960,540,output.resolve("shared-focused-960.png"));render(graph,320,240,output.resolve("shared-small-320.png"));
        draft.validated();
        graph.init(client,1280,1000);press(graph,"+ 条件分支");bindLatest(graph,owner);
        var r=owner.graphRule();var branch=ZombiesNpcGraphDraft.find(r,"node_4");
        var tests=branch.getAsJsonObject("condition").getAsJsonArray("tests");
        tests.get(0).getAsJsonObject().addProperty("kind","ITEM_ACTOR");tests.get(0).getAsJsonObject().addProperty("negated",true);
        tests.add(ZombiesNpcGraphDraft.test());
        graph.init(client,1280,1000);
        button(graph,"交互玩家未持有物品 ▾");button(graph,"波次达到 ▾");
        press(graph,"新增条件");if(tests.size()!=3)throw new AssertionError("Add condition keeps all existing cards");
        press(graph,"删除条件 2");if(tests.size()!=2)throw new AssertionError("Condition cards delete their own indexed entry");
        press(graph,"交互玩家未持有物品 ▾");var choice=client.screen;
        choice.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).map(c->(net.minecraft.client.gui.components.EditBox)c).findFirst().orElseThrow().setValue("ITEM_ACTOR");
        button(choice,"交互玩家持有物品").onPress();
        if(tests.get(0).getAsJsonObject().get("negated").getAsBoolean())throw new AssertionError("Explicit has-item choice clears missing-item predicate");
        graph.init(client,1280,1000);render(graph,1280,1000,output.resolve("all-conditions-1280.png"));
        ZombiesNpcGraphDraft.connect(r,"node_1","next","node_4");ZombiesNpcGraphDraft.connect(r,"node_4","next","node_2");ZombiesNpcGraphDraft.connect(r,"node_4","onFalse","node_3");draft.validated();
        set(graph,"selected",owner.graphNpc().get("id").getAsString()+"."+r.get("id").getAsString()+"/node_3");graph.init(client,1280,1000);
        press(graph,"结束并标记本规则完成");
        if(ZombiesNpcGraphDraft.find(r,"node_3").get("complete").getAsBoolean())throw new AssertionError("Retry end can be enabled in node inspector");
        press(graph,"只结束本次，下次交互重新判断");draft.validated();
        press(graph,"+ 等待时间");press(graph,"+ 结束");
        if(draft.looseNodes().size()!=2)throw new AssertionError("Palette nodes must remain unassigned until execution wiring");
        graph.init(client,1280,1000);
        var pool=ZombiesNpcSharedCanvas.looseRule(draft);var wait=draft.looseNodes().get(0).getAsJsonObject();var end=draft.looseNodes().get(1).getAsJsonObject();
        wait.addProperty("x",950);wait.addProperty("y",100);end.addProperty("x",950);end.addProperty("y",250);
        graph.init(client,1280,1000);press(graph,"适应画布");
        connect(graph,pool,wait.get("id").getAsString(),"next",end.get("id").getAsString());
        if(draft.looseNodes().size()!=2)throw new AssertionError("Loose-to-loose wiring must not assign the current rule");
        crossConnect(graph,r,"node_4","onFalse",pool,wait.get("id").getAsString());
        if(!draft.looseNodes().isEmpty())throw new AssertionError("Connection to owned node adopts entire loose chain");
        welcome=ZombiesNpcSharedCanvas.find(draft,"guard.welcome").rule();
        int testCount=branch.getAsJsonObject("condition").getAsJsonArray("tests").size();
        crossConnect(graph,welcome,"node_3","completed",r,"node_4");
        if(branch.getAsJsonObject("condition").getAsJsonArray("tests").size()!=testCount+1||!r.getAsJsonArray("requires").isEmpty())throw new AssertionError("End-to-branch wire must add a mid-flow rule check without creating entry prerequisites");
        render(graph,1280,1000,output.resolve("mid-rule-reference-1280.png"));draft.validated();
        connect(graph,r,"node_5","next","node_1");ZombiesNpcGraphDraft.delete(r,"node_6");draft.validated();
        if(!ZombiesNpcGraphDraft.find(r,"node_5").get("next").getAsString().equals("node_1"))throw new AssertionError("Execution wire returns directly to the entry input");
        render(graph,1280,1000,output.resolve("return-wire-1280.png"));
    }
    private static void bindLatest(ZombiesNpcGraphScreen graph,ZombiesNpcDeployScreen owner)throws Exception{
        var pool=owner.graphDraft().looseNodes();if(pool.isEmpty())return;var ref=ZombiesNpcSharedCanvas.adopt(owner.graphDraft(),pool.get(pool.size()-1).getAsJsonObject(),ZombiesNpcSharedCanvas.find(owner.graphDraft(),owner.graphNpc().get("id").getAsString()+"."+owner.graphRule().get("id").getAsString()));
        set(graph,"selected",ref.key());graph.init(Minecraft.getInstance(),960,540);
    }
    private static Object get(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void connect(ZombiesNpcGraphScreen graph,com.google.gson.JsonObject rule,String from,String port,String to)throws Exception{
        crossConnect(graph,rule,from,port,rule,to);
    }
    private static void crossConnect(ZombiesNpcGraphScreen graph,com.google.gson.JsonObject fromRule,String from,String port,com.google.gson.JsonObject toRule,String to)throws Exception{
        var a=ZombiesNpcGraphDraft.find(fromRule,from);var b=ZombiesNpcGraphDraft.find(toRule,to);
        if(port.equals("completed"))a=ZombiesNpcGraphDraft.nodes(fromRule).asList().stream().map(com.google.gson.JsonElement::getAsJsonObject).filter(n->n.get("type").getAsString().equals("END")&&(!n.has("complete")||n.get("complete").getAsBoolean())).findFirst().orElseThrow();
        double z=(double)get(graph,"zoom"),px=(double)get(graph,"panX"),py=(double)get(graph,"panY");
        double ax=8+px+(a.get("x").getAsDouble()+170)*z,ay=94+py+(a.get("y").getAsDouble()+(port.equals("onFalse")?68:43))*z;
        double bx=8+px+b.get("x").getAsDouble()*z,by=94+py+(b.get("y").getAsDouble()+(port.equals("completed")&&java.util.Set.of("ENTRY","BRANCH","AWAIT").contains(b.get("type").getAsString())?68:43))*z;
        graph.mouseClicked(ax,ay,0);graph.mouseReleased(ax,ay,0);graph.mouseClicked(bx,by,0);graph.mouseReleased(bx,by,0);
    }

    private static ZombiesNpcConfig fixture() {
        var position = new ZombiesNpcConfig.Position(10.5, 64, 20.5, 90);
        var take = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.TAKE_ITEM, null, null, null, "minecraft:emerald", 3, null, null, 0);
        var give = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.GIVE_ITEM, null, null, null, "minecraft:diamond", 2, null, null, 0);
        var open = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.OPEN_BARRIER, null, null, null, null, 0, null, null, 3);
        var change = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.NPC, null,
                new ZombiesNpcConfig.Appearance(null, null, ZombiesNpcConfig.Pose.SITTING, false, position), null, null, 0, null, null, 0, "通道已开启，去出口见我。");
        var rule = new ZombiesNpcConfig.Rule("delivery", ZombiesNpcConfig.Trigger.INTERACT, 14, List.of(3), true, List.of(), List.of(take, give, open, change), 0, "", 1);
        var guide = new ZombiesNpcConfig.Npc("guide", new ZombiesNpcConfig.Appearance("向导", "minecraft:textures/entity/player/wide/steve.png", ZombiesNpcConfig.Pose.SITTING, true, position), "请替我找来三颗绿宝石。\n找到后可以打开通道。", List.of(rule));
        var guard = new ZombiesNpcConfig.Npc("guard", new ZombiesNpcConfig.Appearance("守卫", null, ZombiesNpcConfig.Pose.STANDING, true, position), "保持警惕。", List.of());
        return new ZombiesNpcConfig(1, List.of(guide, guard));
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void press(Screen screen, String caption) {
        button(screen, caption).onPress();
    }
    private static net.minecraft.client.gui.components.Button button(Screen screen, String caption) {
        return screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                .map(child -> (net.minecraft.client.gui.components.Button) child)
                .filter(button -> button.getTooltip() != null && (button.getMessage().getString().equals(caption)
                        || button.getMessage().getString().equals(Minecraft.getInstance().font.plainSubstrByWidth(caption, Math.max(8, button.getWidth() - 8)))))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button " + caption));
    }
    private static void render(Screen screen, int width, int height, Path file) throws Exception {
        Minecraft client = Minecraft.getInstance();
        screen.init(client, width, height);
        for (var child : screen.children()) if (child instanceof AbstractWidget widget) {
            if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > width || widget.getY() + widget.getHeight() > height) {
                throw new AssertionError("Widget outside viewport " + widget.getMessage().getString());
            }
        }
        var target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            target.setClearColor(0, 0, 0, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width, height, 0, 1000, 3000), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.setIdentity(); modelView.translate(0, 0, -2000); RenderSystem.applyModelViewMatrix();
            // Off-screen targets use logical pixels; the real window's GUI scale must not scale scissor rectangles.
            var graphics = new GuiGraphics(client, client.renderBuffers().bufferSource()) {
                @Override public void enableScissor(int left, int top, int right, int bottom) {
                    flush(); RenderSystem.enableScissor(left, height - bottom, right - left, bottom - top);
                }
                @Override public void disableScissor() { flush(); RenderSystem.disableScissor(); }
            };
            screen.render(graphics, 0, 0, 0); graphics.flush();
            try (var image = Screenshot.takeScreenshot(target)) {
                if(file.getFileName().toString().equals("shared-canvas-1280.png")){
                    boolean visible=false;
                    for(int x=432;x<=438;x++)for(int y=216;y<=222;y++){int pixel=image.getPixelRGBA(x,y);if(((pixel>>8)&255)>150&&(pixel&255)>50)visible=true;}
                    if(!visible)throw new AssertionError("Execution curve must render visibly between entry and action ports");
                }
                image.writeToFile(file);
            }
        } finally {
            modelView.popPose(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(projection, sorting);
            target.destroyBuffers(); client.getMainRenderTarget().bindWrite(true);
        }
    }
}
