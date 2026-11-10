package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.config.zombies.ZombiesConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ZombiesNpcRepository {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private ZombiesNpcRepository() { }

    public static Path path(MinecraftServer server, String map) {
        return ZombiesConfigPaths.zombiesMapRulesRoot(server, map).resolve("npcs.json");
    }

    public static ZombiesNpcConfig load(Path path) {
        if (!Files.exists(path)) return ZombiesNpcConfig.empty();
        ZombiesNpcConfig config;
        try { config = GSON.fromJson(Files.readString(path), ZombiesNpcConfig.class); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        if (config == null) throw new IllegalArgumentException("Empty NPC config " + path);
        return config;
    }

    public static void save(Path path, ZombiesNpcConfig config) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "npcs-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(config));
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
