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
                if (page == 2 && button(screen, "新增执行动作").getY() >= button(screen, "1. 玩家交出物品 ▾").getY())
                    throw new AssertionError("Add action must precede action selection");
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
            Files.writeString(output.resolve("result.txt"), "PASS 15 client renders, widget bounds, add button ordering, 1s defaults, chat name/colors/presets, inventory icons and multi-block placement editor\n");
        } catch (Throwable failure) {
            Files.writeString(output.resolve("result.txt"), "FAIL " + failure + "\n");
            throw failure;
        } finally { client.stop(); }
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
                .filter(button -> button.getTooltip() != null && button.getMessage().getString().equals(caption))
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
            var graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
            screen.render(graphics, 0, 0, 0); graphics.flush();
            try (var image = Screenshot.takeScreenshot(target)) { image.writeToFile(file); }
        } finally {
            modelView.popPose(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(projection, sorting);
            target.destroyBuffers(); client.getMainRenderTarget().bindWrite(true);
        }
    }
}
