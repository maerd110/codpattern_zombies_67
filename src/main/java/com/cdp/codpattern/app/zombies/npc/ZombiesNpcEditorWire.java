package com.cdp.codpattern.app.zombies.npc;

import net.minecraft.network.FriendlyByteBuf;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Bounded transport for nested editor data, including long dialogue and item NBT. */
public final class ZombiesNpcEditorWire {
    private static final int MAX_TEXT = 2 * 1024 * 1024;
    private static final int MAX_REQUEST = 28_000;
    private static final int MAX_RESPONSE = 900_000;
    private ZombiesNpcEditorWire() { }

    private static byte[] compress(String text, int limit) {
        byte[] source = text.getBytes(StandardCharsets.UTF_8);
        if (source.length > MAX_TEXT) throw new IllegalArgumentException("NPC editor data exceeds 2 MiB");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(source); }
            if (bytes.size() > limit) throw new IllegalArgumentException("NPC editor data is too large to send");
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }

    public static void checkRequest(String text) { compress(text, MAX_REQUEST); }
    public static void checkResponse(String text) { compress(text, MAX_RESPONSE); }
    public static void write(FriendlyByteBuf buffer, String text, boolean request) {
        buffer.writeByteArray(compress(text, request ? MAX_REQUEST : MAX_RESPONSE));
    }
    public static String read(FriendlyByteBuf buffer, boolean request) {
        byte[] compressed = buffer.readByteArray(request ? MAX_REQUEST : MAX_RESPONSE);
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] decoded = gzip.readNBytes(MAX_TEXT + 1);
            if (decoded.length > MAX_TEXT) throw new IllegalArgumentException("NPC editor data exceeds 2 MiB");
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new IllegalArgumentException("Invalid NPC editor data", failure); }
    }
}
