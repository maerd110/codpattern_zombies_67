package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper;
import com.phasetranscrystal.fpsmatch.common.item.zombies.ZombiesDeployTool;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** All GUI mutations execute on the server thread and compare the last read file revision. */
public final class ZombiesNpcEditorService {
    public record Snapshot(String mapName, String revision, ZombiesNpcConfig config, List<Integer> barriers,
                           boolean editable, String status, String error) { }
    private ZombiesNpcEditorService() { }

    public static Snapshot perform(ServerPlayer player, String mapName, String operation, String payload) {
        ZombiesMap map = null;
        try {
            if (!(player.getMainHandItem().getItem() instanceof ZombiesDeployTool) || !ToolAccessHelper.hasAdminAccess(player)) {
                throw new IllegalArgumentException("gui.codpattern.zombies.npc.error.access");
            }
            map = FpsMatchMapRegistry.listMaps(BuiltInGameModes.ZOMBIES).stream()
                    .filter(candidate -> candidate.getMapName().equals(mapName) && candidate instanceof ZombiesMap)
                    .map(candidate -> (ZombiesMap) candidate).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("gui.codpattern.zombies.npc.error.map"));
            var path = ZombiesNpcRepository.path(player.getServer(), mapName);
            var current = ZombiesNpcRepository.load(path);
            boolean editable = map.currentPhase() == ZombiesGamePhase.WAITING;
            if (!operation.equals("load") && !editable) throw new IllegalArgumentException("gui.codpattern.zombies.npc.error.running");
            switch (operation) {
                case "save" -> {
                    JsonObject request = JsonParser.parseString(payload).getAsJsonObject();
                    if (!revision(current).equals(request.get("revision").getAsString())) {
                        throw new IllegalArgumentException("gui.codpattern.zombies.npc.error.conflict");
                    }
                    var candidate = ZombiesNpcRepository.GSON.fromJson(request.get("config"), ZombiesNpcConfig.class);
                    if (candidate == null) throw new IllegalArgumentException("Empty NPC editor request");
                    map.npcRuntime().validate(candidate);
                    // Prepare the response before writing so oversized edits fail without changing the file.
                    ZombiesNpcEditorWire.checkResponse(ZombiesNpcRepository.GSON.toJson(snapshot(map, candidate, "saved", "")));
                    ZombiesNpcRepository.save(path, candidate);
                    map.npcRuntime().reset();
                    current = candidate;
                }
                case "preview" -> map.npcRuntime().start(current, true);
                case "clear" -> map.npcRuntime().reset();
                case "load" -> { }
                default -> throw new IllegalArgumentException("Unknown NPC editor operation");
            }
            Snapshot response = snapshot(map, current, operation.equals("save") ? "saved" : operation, "");
            ZombiesNpcEditorWire.checkResponse(ZombiesNpcRepository.GSON.toJson(response));
            return response;
        } catch (Exception failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            // Errors preserve the client's working copy. Refresh is an explicit operation.
            return new Snapshot(mapName, "", null, List.of(), map != null && map.currentPhase() == ZombiesGamePhase.WAITING, "", message);
        }
    }

    private static Snapshot snapshot(ZombiesMap map, ZombiesNpcConfig config, String status, String error) {
        return new Snapshot(map.getMapName(), revision(config), config,
                map.barriers().stream().map(barrier -> barrier.group()).distinct().sorted().toList(),
                map.currentPhase() == ZombiesGamePhase.WAITING, status, error);
    }

    public static String revision(ZombiesNpcConfig config) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(ZombiesNpcRepository.GSON.toJson(config).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
