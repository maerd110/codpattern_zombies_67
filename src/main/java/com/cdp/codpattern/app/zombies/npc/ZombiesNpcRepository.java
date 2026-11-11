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
            .registerTypeHierarchyAdapter(net.minecraft.core.BlockPos.class, new PositionAdapter())
            .registerTypeAdapterFactory(new IntervalDefaults()).create();
    private ZombiesNpcRepository() { }

    /** Minecraft field names change after reobfuscation. On-disk and wire coordinates must not. */
    private static final class PositionAdapter implements com.google.gson.JsonSerializer<net.minecraft.core.BlockPos>, com.google.gson.JsonDeserializer<net.minecraft.core.BlockPos> {
        @Override public com.google.gson.JsonElement serialize(net.minecraft.core.BlockPos value, java.lang.reflect.Type type, com.google.gson.JsonSerializationContext context) {
            var object = new com.google.gson.JsonObject();
            object.addProperty("x", value.getX()); object.addProperty("y", value.getY()); object.addProperty("z", value.getZ());
            return object;
        }
        @Override public net.minecraft.core.BlockPos deserialize(com.google.gson.JsonElement value, java.lang.reflect.Type type, com.google.gson.JsonDeserializationContext context) {
            if (!value.isJsonObject()) throw new com.google.gson.JsonParseException("坐标需要 x、y、z");
            var object = value.getAsJsonObject();
            return new net.minecraft.core.BlockPos(coordinate(object,"x","f_123285_"), coordinate(object,"y","f_123286_"), coordinate(object,"z","f_123289_"));
        }
        private int coordinate(com.google.gson.JsonObject object, String name, String legacy) {
            var value = object.has(name) ? object.get(name) : object.get(legacy);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) throw new com.google.gson.JsonParseException("缺少坐标 " + name);
            try { return new java.math.BigDecimal(value.getAsString()).intValueExact(); }
            catch (RuntimeException invalid) { throw new com.google.gson.JsonParseException("坐标 " + name + " 必须是有效整数", invalid); }
        }
    }

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
