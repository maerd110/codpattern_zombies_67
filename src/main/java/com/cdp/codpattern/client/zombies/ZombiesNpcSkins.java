package com.cdp.codpattern.client.zombies;

import com.cdp.codpattern.app.zombies.npc.*;
import com.mojang.authlib.*;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Lookup happens only in the editor. Rendering downloads the saved texture through MC's skin cache. */
public final class ZombiesNpcSkins {
    public record Resolved(ResourceLocation texture, boolean slim) { }
    private static final Map<String, Resolved> TEXTURES = new ConcurrentHashMap<>();
    public static CompletableFuture<ZombiesNpcPlayerSkin> lookup(String input) {
        var client = Minecraft.getInstance();
        String query = input.trim();
        UUID id = parseId(query);
        GameProfile online = client.getConnection() == null ? null : client.getConnection().getOnlinePlayers().stream()
                .map(info -> info.getProfile()).filter(p -> p.getName().equalsIgnoreCase(query) || p.getId().equals(id)).findFirst().orElse(null);
        if (online != null && online.getProperties().containsKey("textures")) {
            try { return CompletableFuture.completedFuture(ZombiesNpcPlayerSkin.fromProfile(online)); }
            catch (RuntimeException ignored) { }
        }
        if (id == null && !query.matches("[A-Za-z0-9_]{1,16}")) return CompletableFuture.failedFuture(new IllegalArgumentException("请输入在线玩家名、正版账号名或完整 UUID"));
        return CompletableFuture.supplyAsync(() -> {
            var authentication = new YggdrasilAuthenticationService(client.getProxy());
            GameProfile profile;
            if (id != null) profile = new GameProfile(id, null);
            else {
                var found = new AtomicReference<GameProfile>();
                authentication.createProfileRepository().findProfilesByNames(new String[]{query}, Agent.MINECRAFT, new ProfileLookupCallback() {
                    @Override public void onProfileLookupSucceeded(GameProfile result) { found.set(result); }
                    @Override public void onProfileLookupFailed(GameProfile result, Exception failure) { }
                });
                profile = found.get();
                if (profile == null) throw new IllegalArgumentException("未查到此玩家，请检查玩家名和网络连接");
            }
            profile = client.getMinecraftSessionService().fillProfileProperties(profile, true);
            if (profile == null) throw new IllegalArgumentException("无法读取玩家皮肤，请稍后重试");
            return ZombiesNpcPlayerSkin.fromProfile(profile);
        }, net.minecraft.Util.backgroundExecutor()).orTimeout(20, TimeUnit.SECONDS);
    }
    private static UUID parseId(String value) {
        try { if (value.matches("[a-fA-F0-9]{32}")) value = value.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");if(!value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))return null; return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    public static Resolved resolve(String value) {
        if (!value.startsWith(ZombiesNpcPlayerSkin.PREFIX)) {
            var path = ResourceLocation.tryParse(value);
            return new Resolved(path == null ? DefaultPlayerSkin.getDefaultSkin() : path, false);
        }
        var cached = TEXTURES.get(value); if (cached != null) return cached;
        try {
            var skin = ZombiesNpcRepository.GSON.fromJson(value.substring(ZombiesNpcPlayerSkin.PREFIX.length()), ZombiesNpcPlayerSkin.class);
            var id = UUID.fromString(skin.uuid());
            var fallback = new Resolved(DefaultPlayerSkin.getDefaultSkin(id), DefaultPlayerSkin.getSkinModelName(id).equals("slim"));
            if (TEXTURES.size() >= 256) TEXTURES.clear();
            TEXTURES.put(value, fallback);
            Minecraft.getInstance().getSkinManager().registerSkins(skin.profile(), (type, path, texture) -> {
                if (type == MinecraftProfileTexture.Type.SKIN) TEXTURES.put(value, new Resolved(path, "slim".equals(texture.getMetadata("model"))));
            }, false);
            return TEXTURES.get(value);
        } catch (RuntimeException invalid) { return new Resolved(DefaultPlayerSkin.getDefaultSkin(), false); }
    }
    private ZombiesNpcSkins() { }
}
