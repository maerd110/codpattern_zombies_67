package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ZombiesNpcCommands {
    private ZombiesNpcCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var map = Commands.argument("map", StringArgumentType.string()).suggests((context, builder) -> {
            for (var room : FpsMatchMapRegistry.listMaps(BuiltInGameModes.ZOMBIES)) builder.suggest(room.getMapName());
            return builder.buildFuture();
        });
        for (String operation : List.of("list", "reload", "preview", "clear")) {
            map.then(Commands.literal(operation).executes(context -> execute(context, operation, "", "")));
        }
        var npcId = Commands.argument("npc", StringArgumentType.word()).suggests((context, builder) -> {
            try { for (var npc : load(context).npcs()) builder.suggest(npc.id()); }
            catch (Exception ignored) { }
            return builder.buildFuture();
        });
        for (String operation : List.of("create", "remove", "show", "move")) {
            map.then(Commands.literal(operation).then(Commands.argument("npc", StringArgumentType.word())
                    .executes(context -> execute(context, operation, StringArgumentType.getString(context, "npc"), ""))));
        }
        npcId.then(Commands.literal("set").then(Commands.argument("json", StringArgumentType.greedyString())
                .executes(context -> execute(context, "set", StringArgumentType.getString(context, "npc"), StringArgumentType.getString(context, "json")))));
        npcId.then(Commands.literal("rule").then(Commands.argument("json", StringArgumentType.greedyString())
                .executes(context -> execute(context, "rule", StringArgumentType.getString(context, "npc"), StringArgumentType.getString(context, "json")))));
        map.then(npcId);
        dispatcher.register(Commands.literal("zombiesnpc").requires(source -> source.hasPermission(2)).then(map));
    }

    private static ZombiesMap room(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "map");
        return FpsMatchMapRegistry.listMaps(BuiltInGameModes.ZOMBIES).stream()
                .filter(map -> map.getMapName().equals(name) && map instanceof ZombiesMap)
                .map(map -> (ZombiesMap) map).findFirst().orElseThrow(() -> new IllegalArgumentException("找不到僵尸关卡 " + name));
    }

    private static ZombiesNpcConfig load(CommandContext<CommandSourceStack> context) {
        return ZombiesNpcRepository.load(ZombiesNpcRepository.path(context.getSource().getServer(), room(context).getMapName()));
    }

    private static ZombiesNpcConfig.Position playerPosition(CommandContext<CommandSourceStack> context, ZombiesMap room) throws Exception {
        var player = context.getSource().getPlayerOrException();
        if (player.level() != room.getServerLevel()) throw new IllegalArgumentException("请进入关卡所在维度");
        return new ZombiesNpcConfig.Position(player.getX(), player.getY(), player.getZ(), player.getYRot());
    }

    private static int execute(CommandContext<CommandSourceStack> context, String operation, String id, String json)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            ZombiesMap room = room(context);
            var path = ZombiesNpcRepository.path(context.getSource().getServer(), room.getMapName());
            ZombiesNpcConfig config = ZombiesNpcRepository.load(path);
            if (operation.equals("list")) {
                String message = "NPC " + config.npcs().stream().map(ZombiesNpcConfig.Npc::id).toList() + "\n" + path;
                context.getSource().sendSuccess(() -> Component.literal(message), false);
                return config.npcs().size();
            }
            if (operation.equals("show")) {
                var npc = find(config, id);
                context.getSource().sendSuccess(() -> Component.literal(ZombiesNpcRepository.GSON.toJson(npc)), false);
                return 1;
            }
            if (room.currentPhase() != com.cdp.codpattern.app.zombies.model.ZombiesGamePhase.WAITING) {
                throw new IllegalArgumentException("请先结束对局，再编辑或预览 NPC");
            }
            if (operation.equals("clear")) {
                room.npcRuntime().reset();
                context.getSource().sendSuccess(() -> Component.literal("已清除 NPC 预览，配置仍保存在 " + path), false);
                return 1;
            }
            if (operation.equals("reload") || operation.equals("preview")) {
                room.npcRuntime().validate(config);
                room.npcRuntime().reset();
                if (operation.equals("preview")) room.npcRuntime().start(config, true);
            } else {
                List<ZombiesNpcConfig.Npc> npcs = new ArrayList<>(config.npcs());
                if (operation.equals("create")) {
                    if (npcs.stream().anyMatch(npc -> npc.id().equals(id))) throw new IllegalArgumentException("NPC ID 已存在");
                    npcs.add(new ZombiesNpcConfig.Npc(id, new ZombiesNpcConfig.Appearance(id,
                            "minecraft:textures/entity/player/wide/steve.png", ZombiesNpcConfig.Pose.STANDING, true,
                            playerPosition(context, room)), "你好。", List.of()));
                } else {
                    var previous = find(config, id);
                    int index = npcs.indexOf(previous);
                    switch (operation) {
                        case "remove" -> npcs.remove(index);
                        case "move" -> {
                            var a = previous.appearance();
                            npcs.set(index, new ZombiesNpcConfig.Npc(id, new ZombiesNpcConfig.Appearance(a.name(), a.skin(), a.pose(), a.alive(), playerPosition(context, room), a.invulnerable(), a.chatName(), a.nameColor(), a.textColor(), a.combat(), a.playerSkin()), previous.dialogue(), previous.rules(), previous.dialogueIntervalSeconds()));
                        }
                        case "set" -> {
                            JsonObject merged = ZombiesNpcRepository.GSON.toJsonTree(previous).getAsJsonObject();
                            merge(merged, JsonParser.parseString(json).getAsJsonObject());
                            if (!merged.get("id").getAsString().equals(id)) throw new IllegalArgumentException("set 不能修改 NPC ID");
                            npcs.set(index, ZombiesNpcRepository.GSON.fromJson(merged, ZombiesNpcConfig.Npc.class));
                        }
                        case "rule" -> {
                            var rule = ZombiesNpcRepository.GSON.fromJson(json, ZombiesNpcConfig.Rule.class);
                            List<ZombiesNpcConfig.Rule> rules = new ArrayList<>(previous.rules());
                            rules.removeIf(existing -> existing.id().equals(rule.id()));
                            rules.add(rule);
                            npcs.set(index, new ZombiesNpcConfig.Npc(id, previous.appearance(), previous.dialogue(), rules, previous.dialogueIntervalSeconds()));
                        }
                        default -> throw new IllegalArgumentException("Unknown NPC operation");
                    }
                }
                config = new ZombiesNpcConfig(1, npcs, config.looseNodes(), config.debug());
                room.npcRuntime().validate(config);
                ZombiesNpcRepository.save(path, config);
                room.npcRuntime().reset();
            }
            context.getSource().sendSuccess(() -> Component.literal("NPC 配置已验证。" + path), true);
            return 1;
        } catch (Exception failure) {
            throw new SimpleCommandExceptionType(Component.literal("NPC 操作失败 " + failure.getMessage())).create();
        }
    }

    private static ZombiesNpcConfig.Npc find(ZombiesNpcConfig config, String id) {
        return config.npcs().stream().filter(npc -> npc.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("找不到 NPC " + id));
    }

    private static void merge(JsonObject target, JsonObject patch) {
        patch.entrySet().forEach(entry -> {
            if (entry.getValue().isJsonObject() && target.has(entry.getKey()) && target.get(entry.getKey()).isJsonObject()) {
                merge(target.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject());
            } else target.add(entry.getKey(), entry.getValue());
        });
    }
}
