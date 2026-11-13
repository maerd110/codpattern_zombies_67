package com.cdp.codpattern.client.zombies;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ZombiesRarityDisplay {
    private static java.util.Map<String, Entry> roomEntries = java.util.Map.of();

    /** Replace, never merge: the same tier ID can have a different color in another room. */
    public static void receive(net.minecraft.nbt.ListTag rarities) {
        var entries = new java.util.HashMap<String, Entry>();
        for (int i = 0; i < Math.min(64, rarities.size()); i++) {
            var value = rarities.getCompound(i); String id = normalize(value.getString("id"));
            String name = value.getString("displayName").trim();
            if (!id.isBlank()) entries.put(id, new Entry(id, name.isBlank() ? defaultLabel(id) : name, value.getInt("color") | 0xFF000000));
        }
        roomEntries = java.util.Map.copyOf(entries);
    }
    public static void clear() { roomEntries = java.util.Map.of(); }
    public static String defaultLabel(String id) { return com.cdp.codpattern.config.zombies.ZombiesWeaponRulesConfig.defaultDisplayName(id); }
    private static final int COMMON_COLOR = 0xFF22C55E;
    private static final int RARE_COLOR = 0xFF3B82F6;
    private static final int EPIC_COLOR = 0xFFA855F7;
    private static final int LEGENDARY_COLOR = 0xFFFF9800;

    private ZombiesRarityDisplay() {
    }

    public static Optional<Entry> fromRarityId(String rarityId) {
        Entry configured = roomEntries.get(normalize(rarityId));
        if (configured != null) return Optional.of(configured);
        return switch (normalize(rarityId)) {
            case "common" -> Optional.of(new Entry("common", "普通", COMMON_COLOR));
            case "rare" -> Optional.of(new Entry("rare", "稀有", RARE_COLOR));
            case "epic" -> Optional.of(new Entry("epic", "史诗", EPIC_COLOR));
            case "legendary" -> Optional.of(new Entry("legendary", "传奇", LEGENDARY_COLOR));
            default -> Optional.empty();
        };
    }

    private static String normalize(String rarityId) {
        return Objects.requireNonNullElse(rarityId, "").trim().toLowerCase(Locale.ROOT);
    }

    public record Entry(String id, String label, int color) {
    }
}
