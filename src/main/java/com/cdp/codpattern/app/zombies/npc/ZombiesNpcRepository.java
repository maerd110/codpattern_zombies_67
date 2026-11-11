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
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .registerTypeAdapterFactory(new IntervalDefaults()).create();
    private ZombiesNpcRepository() { }

    private static final class IntervalDefaults implements com.google.gson.TypeAdapterFactory {
        @Override public <T> com.google.gson.TypeAdapter<T> create(Gson gson, com.google.gson.reflect.TypeToken<T> type) {
            String field;
            if (type.getRawType() == ZombiesNpcConfig.Npc.class) field = "dialogueIntervalSeconds";
            else if (type.getRawType() == ZombiesNpcConfig.Rule.class) field = "actionIntervalSeconds";
            else return null;
            var delegate = gson.getDelegateAdapter(this, type);
            return new com.google.gson.TypeAdapter<T>() {
                @Override public T read(com.google.gson.stream.JsonReader reader) throws IOException {
                    var object = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    if (!object.has(field) || object.get(field).isJsonNull()) object.addProperty(field, ZombiesNpcConfig.DEFAULT_INTERVAL_SECONDS);
                    return delegate.fromJsonTree(object);
                }
                @Override public void write(com.google.gson.stream.JsonWriter writer, T value) throws IOException { delegate.write(writer, value); }
            }.nullSafe();
        }
    }

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
