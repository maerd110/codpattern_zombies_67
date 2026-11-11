package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Icon-based inventory and building-block selection with search. */
final class ZombiesNpcStackPickerScreen extends Screen {
    record Entry(ItemStack stack, String value, String caption, int count) { }
    private record Tile(Entry entry, int x, int y, int width) { }
    private final Screen parent;
    private final List<Entry> entries;
    private final Consumer<Entry> choose;
    private final List<Button> buttons = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private String search = "";
    private int scroll;
    ZombiesNpcStackPickerScreen(Screen parent, String title, List<Entry> entries, Consumer<Entry> choose) {
        super(Component.literal(title)); this.parent = parent; this.entries = List.copyOf(entries); this.choose = choose;
    }
    @Override protected void init() {
        EditBox filter = addRenderableWidget(new EditBox(font, 16, 34, width - 32, 20, Component.translatable("gui.codpattern.zombies.npc.search")));
        filter.setValue(search);
        filter.setResponder(value -> { search = value; scroll = 0; buildTiles(); });
        addRenderableWidget(Button.builder(Component.translatable("gui.codpattern.zombies.npc.continue_editing"), ignored -> onClose())
                .bounds(width / 2 - 70, height - 30, 140, 20).build());
        setInitialFocus(filter); buildTiles();
    }
    private void buildTiles() {
        buttons.forEach(this::removeWidget); buttons.clear(); tiles.clear();
        String query = search.toLowerCase(java.util.Locale.ROOT);
        List<Entry> filtered = entries.stream().filter(entry -> (entry.caption() + " " + entry.value()).toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
        int columns = Math.max(1, (width - 32) / 72), rows = Math.max(1, (height - 104) / 48);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, (filtered.size() + columns - 1) / columns - rows)));
        int cell = (width - 32) / columns;
        for (int i = scroll * columns; i < Math.min(filtered.size(), (scroll + rows) * columns); i++) {
            Entry entry = filtered.get(i);
            int x = 16 + (i % columns) * cell, y = 62 + (i / columns - scroll) * 48;
            Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                minecraft.setScreen(parent); choose.accept(entry);
            }).bounds(x, y, cell - 4, 44).build());
            button.setTooltip(Tooltip.create(Component.literal(entry.caption() + " × " + entry.count() + "\n" + entry.value())));
            buttons.add(button); tiles.add(new Tile(entry, x, y, cell - 4));
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) { scroll += delta > 0 ? -1 : 1; buildTiles(); return true; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF012191D);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFE8EFED);
        super.render(graphics, x, y, partialTick);
        for (Tile tile : tiles) {
            if (!tile.entry.stack().isEmpty()) {
                graphics.renderItem(tile.entry.stack(), tile.x + tile.width / 2 - 8, tile.y + 4);
                graphics.renderItemDecorations(font, tile.entry.stack(), tile.x + tile.width / 2 - 8, tile.y + 4);
            }
            graphics.drawString(font, font.plainSubstrByWidth(tile.entry.caption(), tile.width - 8), tile.x + 4, tile.y + 29, 0xFFFFFFFF, false);
        }
    }
}
