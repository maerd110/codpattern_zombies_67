package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Searchable choices retain full labels even on a small GUI scale. */
final class ZombiesNpcChoiceScreen extends Screen {
    private final Screen parent;
    private final List<String> options;
    private final String selected;
    private final Function<String, String> label;
    private final Consumer<String> choose;
    private final List<Button> entries = new ArrayList<>();
    private String search = "";
    private int offset;
    ZombiesNpcChoiceScreen(Screen parent, String title, List<String> options, String selected,
                           Function<String, String> label, Consumer<String> choose) {
        super(Component.literal(title));
        this.parent = parent; this.options = List.copyOf(options); this.selected = selected;
        this.label = label; this.choose = choose;
    }
    @Override protected void init() {
        var filter = addRenderableWidget(new EditBox(font, 20, 35, width - 40, 20,
                Component.translatable("gui.codpattern.zombies.npc.search")));
        filter.setValue(search);
        filter.setHint(Component.translatable("gui.codpattern.zombies.npc.search"));
        filter.setResponder(text -> { search = text; offset = 0; buildEntries(); });
        addRenderableWidget(Button.builder(Component.translatable("gui.codpattern.zombies.npc.continue_editing"), ignored -> onClose())
                .bounds(width / 2 - 70, height - 30, 140, 20).build());
        setInitialFocus(filter);
        buildEntries();
    }
    private void buildEntries() {
        entries.forEach(this::removeWidget); entries.clear();
        String query = search.toLowerCase(java.util.Locale.ROOT);
        List<String> filtered = options.stream().filter(value -> label.apply(value).toLowerCase(java.util.Locale.ROOT).contains(query)
                || value.toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
        int visible = Math.max(1, (height - 105) / 24);
        offset = Math.max(0, Math.min(offset, Math.max(0, filtered.size() - visible)));
        for (int i = offset; i < Math.min(filtered.size(), offset + visible); i++) {
            String value = filtered.get(i);
            String caption = (value.equals(selected) ? "● " : "") + label.apply(value);
            Button button = addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(caption, width - 60)), ignored -> {
                minecraft.setScreen(parent); choose.accept(value);
            }).bounds(20, 65 + (i - offset) * 24, width - 40, 20).build());
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label.apply(value))));
            entries.add(button);
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        offset += delta > 0 ? -1 : 1; buildEntries(); return true;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF012191D);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFE8EFED);
        super.render(graphics, x, y, partialTick);
    }
}
