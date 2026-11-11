package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.app.zombies.service.navigation.NavigationWorldChanges;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ZombiesNpcRuntime {
    private static final Set<ZombiesNpcRuntime> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ZombiesMap map;
    private ZombiesNpcConfig config = ZombiesNpcConfig.empty();
    private final Map<String, ZombiesNpcEntity> entities = new LinkedHashMap<>();
    private final Map<String, String> dialogues = new HashMap<>();
    private final Set<String> completed = new HashSet<>();
    private final Map<UUID, Long> lastInteraction = new HashMap<>();
    private final Map<String, Sequence> sequences = new LinkedHashMap<>();
    private boolean preview;
    private final Set<net.minecraft.world.level.ChunkPos> chunks = new HashSet<>();
    private static final net.minecraft.server.level.TicketType<String> NPC_TICKET =
            net.minecraft.server.level.TicketType.create("zombies-story-npc", String::compareTo);

    public ZombiesNpcRuntime(ZombiesMap map) { this.map = map; }
    private ServerLevel level() { return map.getServerLevel(); }

    public void validate(ZombiesNpcConfig candidate) {
        for (var npc : candidate.npcs()) {
            validateAppearance(npc.appearance());
            for (var rule : npc.rules()) {
                if (!rule.requiredItem().isBlank()) ZombiesNpcInventory.parse(rule.requiredItem());
                for (int group : rule.openedBarriers()) requireBarrier(group);
                for (var action : rule.actions()) switch (action.kind()) {
                    case NPC -> { if (action.appearance() != null) validateAppearance(action.appearance()); }
                    case SET_BLOCK -> {
                      for (var placement : action.blockPlacements()) {
                        validatePosition(placement.position());
                        parseBlock(placement.block());
                        BlockPos pos = blockPos(placement.position());
                        // Barrier blocks and configured interactive objects have their own reset owners.
                        for (var barrier : map.barriers()) {
                            if (inBox(pos, barrier.areaFrom(), barrier.areaTo())) throw new IllegalArgumentException("NPC block overlaps barrier " + barrier.objectId());
                        }
                        var objects = map.objects();
                        boolean occupied = java.util.stream.Stream.of(
                                objects.weaponWalls().stream().map(value -> value.pos()),
                                objects.ammoBoxes().stream().map(value -> value.pos()),
                                objects.armorStations().stream().map(value -> value.pos()),
                                objects.powerSwitch().stream().map(value -> value.pos()),
                                objects.sodaMachines().stream().map(value -> value.pos()),
                                objects.ultimateMachines().stream().map(value -> value.pos()),
                                objects.mysteryBoxes().stream().map(value -> value.pos()))
                                .flatMap(stream -> stream).anyMatch(pos::equals);
                        if (occupied || objects.windows().stream().anyMatch(window -> inBox(pos, window.areaFrom(), window.areaTo()))) {
                            throw new IllegalArgumentException("NPC block overlaps a configured map object " + pos);
                        }
                      }
                    }
                    case OPEN_BARRIER -> requireBarrier(action.barrierGroup());
                    case TAKE_ITEM, GIVE_ITEM -> ZombiesNpcInventory.parse(action.item());
                    default -> { }
                }
            }
        }
    }

    private void requireBarrier(int group) {
        if (map.barriers().stream().noneMatch(barrier -> barrier.group() == group)) throw new IllegalArgumentException("Unknown barrier group " + group);
    }

    private void validateAppearance(ZombiesNpcConfig.Appearance appearance) {
        if (appearance.skin() != null && (ResourceLocation.tryParse(appearance.skin()) == null || !appearance.skin().endsWith(".png"))) {
            throw new IllegalArgumentException("Skin must be a resource location ending in .png");
        }
        if (appearance.position() != null) validatePosition(appearance.position());
    }

    private void validatePosition(ZombiesNpcConfig.Position position) {
        BlockPos pos = blockPos(position);
        if (!inBox(pos, map.getMapArea().pos1(), map.getMapArea().pos2())
                || level().isOutsideBuildHeight(pos) || !level().getWorldBorder().isWithinBounds(pos)) {
            throw new IllegalArgumentException("NPC position outside map " + pos);
        }
    }

    private static boolean inBox(BlockPos pos, BlockPos from, BlockPos to) {
        return pos.getX() >= Math.min(from.getX(), to.getX()) && pos.getX() <= Math.max(from.getX(), to.getX())
                && pos.getY() >= Math.min(from.getY(), to.getY()) && pos.getY() <= Math.max(from.getY(), to.getY())
                && pos.getZ() >= Math.min(from.getZ(), to.getZ()) && pos.getZ() <= Math.max(from.getZ(), to.getZ());
    }

    private BlockState parseBlock(String specification) {
        try {
            var result = BlockStateParser.parseForBlock(level().holderLookup(Registries.BLOCK), specification, false);
            BlockState state = result.blockState();
            requireStableBlock(state);
            return state;
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException("Invalid NPC block " + specification, exception);
        }
    }

    public static boolean supportsStoryBlock(BlockState state) {
        try { requireStableBlock(state); return true; }
        catch (IllegalArgumentException unsupported) { return false; }
    }

    private static void requireStableBlock(BlockState state) {
        // These vanilla implementations have no on-place/on-remove world mutations. Shape and
        // neighbour updates are suppressed so a story cell has a complete, bounded undo record.
        Set<Class<?>> supported = Set.of(
                net.minecraft.world.level.block.Block.class,
                net.minecraft.world.level.block.AirBlock.class,
                net.minecraft.world.level.block.StairBlock.class,
                net.minecraft.world.level.block.SlabBlock.class,
                net.minecraft.world.level.block.WallBlock.class,
                net.minecraft.world.level.block.FenceBlock.class,
                net.minecraft.world.level.block.FenceGateBlock.class,
                net.minecraft.world.level.block.DoorBlock.class,
                net.minecraft.world.level.block.TrapDoorBlock.class,
                net.minecraft.world.level.block.IronBarsBlock.class,
                net.minecraft.world.level.block.GlassBlock.class,
                net.minecraft.world.level.block.HalfTransparentBlock.class,
                net.minecraft.world.level.block.RotatedPillarBlock.class);
        if (!supported.contains(state.getBlock().getClass()) || !state.getFluidState().isEmpty()
                || state.isRandomlyTicking() || state.hasBlockEntity()) {
            throw new IllegalArgumentException("NPC 方块动作支持静态建筑方块、门和空气，不能操作会扩散、掉落或保存数据的方块");
        }
    }

    private static BlockPos blockPos(ZombiesNpcConfig.Position position) { return BlockPos.containing(position.x(), position.y(), position.z()); }

    void keepLoaded(BlockPos pos) {
        var chunk = new net.minecraft.world.level.ChunkPos(pos);
        if (chunks.add(chunk)) level().getChunkSource().addRegionTicket(NPC_TICKET, chunk, 2, map.getMapName());
        level().getChunk(pos);
    }

    public void start(ZombiesNpcConfig next, boolean isPreview) {
        validate(next);
        reset();
        config = next;
        preview = isPreview;
        if (!next.npcs().isEmpty()) ACTIVE.add(this);
        try {
            for (var npc : config.npcs()) {
                ZombiesNpcEntity entity = ZombiesNpcRegister.NPC.get().create(level());
                if (entity == null) throw new IllegalStateException("Cannot create NPC");
                entity.bind(this, npc.id());
                entity.apply(npc.appearance());
                if (isPreview) entity.setInvulnerable(true);
                keepLoaded(entity.blockPosition());
                if (!level().addFreshEntity(entity)) throw new IllegalStateException("Cannot spawn NPC " + npc.id());
                entities.put(npc.id(), entity);
                dialogues.put(npc.id(), npc.dialogue());
            }
        } catch (RuntimeException failure) {
            reset();
            throw failure;
        }
        releaseUnusedChunks();
    }

    private void releaseUnusedChunks() {
        var occupied = entities.values().stream().map(entity -> new net.minecraft.world.level.ChunkPos(entity.blockPosition()))
                .collect(java.util.stream.Collectors.toSet());
        chunks.removeIf(chunk -> {
            if (occupied.contains(chunk)) return false;
            level().getChunkSource().removeRegionTicket(NPC_TICKET, chunk, 2, map.getMapName());
            return true;
        });
    }

    public void tick(int wave) {
        if (preview || !map.currentPhase().allowsPurchases()) return;
        for (var sequence : List.copyOf(sequences.values())) advance(sequence);
        if (!map.currentPhase().allowsPurchases()) return;
        for (var npc : config.npcs()) {
            if (sequences.containsKey(npc.id())) continue;
            for (var rule : npc.rules()) {
                if (rule.trigger() == ZombiesNpcConfig.Trigger.AUTO && eligible(npc, rule, wave)) {
                    startSequence(npc, rule, null);
                    break;
                }
            }
        }
    }

    private boolean eligible(ZombiesNpcConfig.Npc npc, ZombiesNpcConfig.Rule rule, int wave) {
        return !completed.contains(npc.id() + "." + rule.id())
                && rule.eligible(wave, map::npcBarrierOpen, completed)
                && (rule.requiredItem().isBlank() || map.npcPlayers().stream()
                        .anyMatch(player -> ZombiesNpcInventory.contains(player, rule.requiredItem(), rule.requiredCount())));
    }

    public void interact(ServerPlayer player, String id, ZombiesNpcEntity entity) {
        if (preview || !map.currentPhase().allowsPurchases() || !map.isAliveSurvivor(player.getUUID())
                || player.level() != level() || entities.get(id) != entity || entity.isRemoved()
                || !entity.storyAlive() || player.distanceToSqr(entity) > 36) return;
        long now = level().getGameTime();
        if (now - lastInteraction.getOrDefault(player.getUUID(), now - 10) < 10) return;
        lastInteraction.put(player.getUUID(), now);
        if (sequences.containsKey(id)) return;
        var npc = config.npcs().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        for (var rule : npc.rules()) {
            if (rule.trigger() == ZombiesNpcConfig.Trigger.INTERACT && eligible(npc, rule, map.npcWave())) {
                startSequence(npc, rule, player);
                return;
            }
        }
        String dialogue = dialogues.getOrDefault(id, "");
        if (!dialogue.isBlank()) startSequence(npc, null, player);
    }

    private void startSequence(ZombiesNpcConfig.Npc npc, ZombiesNpcConfig.Rule rule, ServerPlayer player) {
        List<ZombiesNpcConfig.Action> actions = rule == null
                ? List.of(new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.DIALOGUE, null, null,
                        dialogues.getOrDefault(npc.id(), ""), null, 0, null, null, 0)) : rule.actions();
        var sequence = new Sequence(npc, rule, player, actions);
        sequence.due = level().getGameTime() + delay(sequence, 0);
        sequences.put(npc.id(), sequence);
        advance(sequence);
    }

    private int delay(Sequence sequence, int index) {
        var action = sequence.actions.get(index);
        double seconds = action.delaySeconds() != null ? action.delaySeconds()
                : index == 0 || sequence.rule == null ? 0 : sequence.rule.actionIntervalSeconds();
        return ZombiesNpcConfig.intervalTicks(seconds);
    }

    private void advance(Sequence sequence) {
        long now = level().getGameTime();
        if (now < sequence.due) return;
        ServerPlayer actor = sequence.actor;
        if (actor != null) {
            ServerPlayer online = level().getServer().getPlayerList().getPlayer(actor.getUUID());
            if (online != null) sequence.actor = actor = online;
            if (actor.isRemoved() || actor.level() != level() || !map.isAliveSurvivor(actor.getUUID())) {
                sequence.due = now + 20;
                return;
            }
        }
        try {
            while (sequence.index < sequence.actions.size()) {
                var action = sequence.actions.get(sequence.index);
                String target = action.target() == null ? sequence.npc.id() : action.target();
                if (action.kind() == ZombiesNpcConfig.Kind.DIALOGUE) {
                    String[] lines = action.text().split("\\n", -1);
                    int interval = ZombiesNpcConfig.intervalTicks(action.dialogueIntervalSeconds() == null
                            ? sequence.npc.dialogueIntervalSeconds() : action.dialogueIntervalSeconds());
                    while (sequence.line < lines.length) {
                        say(target, lines[sequence.line++]);
                        if (sequence.line < lines.length && interval > 0) {
                            sequence.due = now + interval;
                            return;
                        }
                    }
                } else executeAction(action, target, actor);
                sequence.blockedMessage = "";
                sequence.line = 0;
                sequence.index++;
                if (sequence.index < sequence.actions.size()) {
                    sequence.due = now + delay(sequence, sequence.index);
                    if (sequence.due > now) return;
                }
            }
            if (sequence.rule != null) completed.add(sequence.npc.id() + "." + sequence.rule.id());
            sequences.remove(sequence.npc.id());
            releaseUnusedChunks();
        } catch (IllegalArgumentException blocked) {
            if (actor == null) {
                com.mojang.logging.LogUtils.getLogger().error("Automatic NPC sequence failed in {}", map.getMapName(), blocked);
                map.failNpcRound();
                return;
            }
            if (!blocked.getMessage().equals(sequence.blockedMessage)) {
                actor.sendSystemMessage(Component.literal(blocked.getMessage()));
                sequence.blockedMessage = blocked.getMessage();
            }
            sequence.due = now + 20;
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("NPC sequence failed in {}", map.getMapName(), failure);
            map.failNpcRound();
        }
    }

    private void executeAction(ZombiesNpcConfig.Action action, String target, ServerPlayer actor) {
        switch (action.kind()) {
            case TAKE_ITEM, GIVE_ITEM -> {
                if (actor == null) throw new IllegalStateException("Item action has no interaction owner");
                ZombiesNpcInventory.commit(actor, ZombiesNpcInventory.plan(actor, List.of(action)));
            }
            case NPC -> {
                if (action.appearance() != null) entities.get(target).apply(action.appearance());
                if (action.dialogue() != null) dialogues.put(target, action.dialogue());
            }
            case OPEN_BARRIER -> {
                map.validateNpcBarrierOpens(List.of(action.barrierGroup()));
                map.openNpcBarrier(action.barrierGroup());
            }
            case SET_BLOCK -> placeBlocks(action.blockPlacements());
            default -> throw new IllegalStateException("Unexpected NPC action");
        }
    }

    private void placeBlocks(List<ZombiesNpcConfig.BlockPlacement> placements) {
        Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
        for (var placement : placements) {
            BlockPos pos = blockPos(placement.position());
            level().getChunk(pos);
            requireStableBlock(level().getBlockState(pos));
            changes.put(pos, parseBlock(placement.block()));
        }
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        try {
            for (var change : changes.entrySet()) {
                BlockPos pos = change.getKey();
                before.put(pos, level().getBlockState(pos));
                ZombiesNpcBlockJournal.get(level()).capture(level(), map.getMapName(), pos);
                if (!level().setBlock(pos, change.getValue(), 18) && !level().getBlockState(pos).equals(change.getValue())) {
                    throw new IllegalStateException("Cannot place NPC block " + pos);
                }
                NavigationWorldChanges.blockChanged(level(), pos, "npc-action");
            }
        } catch (RuntimeException failure) {
            before.forEach((pos, state) -> { level().removeBlockEntity(pos); level().setBlock(pos, state, 18); });
            throw failure;
        }
    }

    private static final class Sequence {
        final ZombiesNpcConfig.Npc npc;
        final ZombiesNpcConfig.Rule rule;
        final List<ZombiesNpcConfig.Action> actions;
        ServerPlayer actor;
        int index;
        int line;
        long due;
        String blockedMessage = "";
        Sequence(ZombiesNpcConfig.Npc npc, ZombiesNpcConfig.Rule rule, ServerPlayer actor, List<ZombiesNpcConfig.Action> actions) {
            this.npc = npc; this.rule = rule; this.actor = actor; this.actions = actions;
        }
    }

    private void say(String id, String text) {
        var entity = entities.get(id);
        String name = entity != null && entity.hasCustomName() ? entity.getName().getString() : id;
        map.sendNpcDialogue(Component.literal("[" + name + "] " + text));
    }

    public void reset() {
        ACTIVE.remove(this);
        entities.values().forEach(net.minecraft.world.entity.Entity::discard);
        entities.clear();
        dialogues.clear();
        sequences.clear();
        if (level() != null) {
            ZombiesNpcBlockJournal.get(level()).restore(level(), map.getMapName());
            for (var chunk : chunks) level().getChunkSource().removeRegionTicket(NPC_TICKET, chunk, 2, map.getMapName());
        }
        chunks.clear();
        completed.clear();
        lastInteraction.clear();
        config = ZombiesNpcConfig.empty();
        preview = false;
    }

    static void cleanupDetached(net.minecraft.server.MinecraftServer server, boolean stopping) {
        if (ACTIVE.isEmpty()) return;
        var registered = stopping ? List.of() : com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry.listMaps(
                com.cdp.codpattern.app.match.BuiltInGameModes.ZOMBIES);
        for (var runtime : List.copyOf(ACTIVE)) {
            if (runtime.level().getServer() == server
                    && (stopping || registered.stream().noneMatch(room -> room == runtime.map))) runtime.reset();
        }
    }
}
