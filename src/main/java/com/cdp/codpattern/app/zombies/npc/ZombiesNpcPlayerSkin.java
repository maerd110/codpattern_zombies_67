package com.cdp.codpattern.app.zombies.npc;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** A saved skin snapshot: playing a map never needs to look up the account again. */
public record ZombiesNpcPlayerSkin(String name, String uuid, String texture, String signature) {
    public static final String PREFIX = "player:";
    public ZombiesNpcPlayerSkin {
        UUID.fromString(uuid);
        if (name == null || name.isBlank() || name.length()>128) throw new IllegalArgumentException("玩家名无效");
        if (texture == null || texture.length() > 8192 || signature != null && signature.length() > 2048)
            throw new IllegalArgumentException("玩家皮肤数据过大");
        try {
            var json = JsonParser.parseString(new String(Base64.getDecoder().decode(texture), StandardCharsets.UTF_8)).getAsJsonObject();
            String url = json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            if (!url.matches("https?://textures\\.minecraft\\.net/texture/[a-fA-F0-9]{32,128}")) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("没有有效的 Minecraft 玩家皮肤", invalid); }
        signature = signature == null ? "" : signature;
    }
    public GameProfile profile() {
        var profile = new GameProfile(UUID.fromString(uuid), name);
        profile.getProperties().put("textures", signature.isEmpty() ? new Property("textures", texture) : new Property("textures", texture, signature));
        return profile;
    }
    public String synced() { return PREFIX + ZombiesNpcRepository.GSON.toJson(this); }
    public static ZombiesNpcPlayerSkin fromProfile(GameProfile profile) {
        var property = profile.getProperties().get("textures").stream().findFirst().orElseThrow(() -> new IllegalArgumentException("此玩家尚无可读取的皮肤"));
        return new ZombiesNpcPlayerSkin(profile.getName(), profile.getId().toString(), property.getValue(), property.getSignature());
    }
}
