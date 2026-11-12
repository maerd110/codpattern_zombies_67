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
public final class ZombiesNpcStackPickerScreen extends Screen {
    public record Entry(ItemStack stack, String value, String caption, int count) { }
    private record Tile(Entry entry, int x, int y, int width) { }
    private final Screen parent;
    private final List<Entry> entries;
    private final Consumer<Entry> choose;
    private final List<Button> buttons = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private String search = "";
    private int scroll;
    public ZombiesNpcStackPickerScreen(Screen parent, String title, List<Entry> entries, Consumer<Entry> choose) {
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
        int columns = Math.max(1, (width - 32) / 96), rows = Math.max(1, (height - 104) / 64);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, (filtered.size() + columns - 1) / columns - rows)));
        int cell = (width - 32) / columns;
        for (int i = scroll * columns; i < Math.min(filtered.size(), (scroll + rows) * columns); i++) {
            Entry entry = filtered.get(i);
            int x = 16 + (i % columns) * cell, y = 62 + (i / columns - scroll) * 64;
            Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                minecraft.setScreen(parent); choose.accept(entry);
            }).bounds(x, y, cell - 4, 60).build());
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
            var stats=com.cdp.codpattern.app.zombies.npc.ZombiesCoopRuntime.tags(tile.entry.stack());
            var rarity=stats==null?java.util.Optional.<com.cdp.codpattern.client.zombies.ZombiesRarityDisplay.Entry>empty():com.cdp.codpattern.client.zombies.ZombiesRarityDisplay.fromRarityId(stats.rarityId());
            int color=rarity.map(com.cdp.codpattern.client.zombies.ZombiesRarityDisplay.Entry::color).orElse(0xFF9CB6C6);
            if(stats!=null){graphics.fill(tile.x+1,tile.y+1,tile.x+tile.width-1,tile.y+25,(color&0xFFFFFF)|0x40000000);graphics.fill(tile.x,tile.y,tile.x+2,tile.y+60,color);graphics.fill(tile.x,tile.y,tile.x+tile.width,tile.y+2,color);graphics.fill(tile.x,tile.y+58,tile.x+tile.width,tile.y+60,color);graphics.fill(tile.x+tile.width-2,tile.y,tile.x+tile.width,tile.y+60,color);}
            if (!tile.entry.stack().isEmpty()) {
                graphics.renderItem(tile.entry.stack(),tile.x+8,tile.y+6);
                graphics.renderItemDecorations(font,tile.entry.stack(),tile.x+8,tile.y+6);
            }
            graphics.drawString(font,font.plainSubstrByWidth(tile.entry.stack().getHoverName().getString(),tile.width-8),tile.x+4,tile.y+30,stats==null?0xFFFFFFFF:color,false);
            if(stats!=null){String upgrade="+"+stats.upgradeLevel();graphics.drawString(font,upgrade,tile.x+tile.width-font.width(upgrade)-6,tile.y+9,0xFFFFE2A1,false);graphics.drawString(font,font.plainSubstrByWidth(rarity.map(com.cdp.codpattern.client.zombies.ZombiesRarityDisplay.Entry::label).orElse("预设")+" · Lv"+stats.weaponLevel(),tile.width-8),tile.x+4,tile.y+45,color,false);}
            else graphics.drawString(font,font.plainSubstrByWidth(tile.entry.caption(),tile.width-8),tile.x+4,tile.y+45,0xFF9CB6C6,false);
        }
    }
}
