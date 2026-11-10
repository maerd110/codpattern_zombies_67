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
                for (int group : rule.openedBarriers()) requireBarrier(group);
                for (var action : rule.actions()) switch (action.kind()) {
                    case NPC -> { if (action.appearance() != null) validateAppearance(action.appearance()); }
                    case SET_BLOCK -> {
                        validatePosition(action.position());
                        parseBlock(action.block());
                        BlockPos pos = blockPos(action.position());
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
        for (var npc : config.npcs()) {
            for (var rule : npc.rules()) {
                if (rule.trigger() == ZombiesNpcConfig.Trigger.AUTO && eligible(npc, rule, wave)) {
                    try { execute(npc, rule, null); }
                    catch (RuntimeException failure) {
                        com.mojang.logging.LogUtils.getLogger().error("NPC rule failed in map {}: {}.{}", map.getMapName(), npc.id(), rule.id(), failure);
                        map.failNpcRound();
                        return;
                    }
                }
            }
        }
    }

    private boolean eligible(ZombiesNpcConfig.Npc npc, ZombiesNpcConfig.Rule rule, int wave) {
        return !completed.contains(npc.id() + "." + rule.id()) && rule.eligible(wave, map::npcBarrierOpen, completed);
    }

    public void interact(ServerPlayer player, String id, ZombiesNpcEntity entity) {
        if (preview || !map.currentPhase().allowsPurchases() || !map.isAliveSurvivor(player.getUUID())
                || player.level() != level() || entities.get(id) != entity || entity.isRemoved()
                || !entity.storyAlive() || player.distanceToSqr(entity) > 36) return;
        long tick = level().getGameTime();
        if (tick - lastInteraction.getOrDefault(player.getUUID(), tick - 10) < 10) return;
        lastInteraction.put(player.getUUID(), tick);
        var npc = config.npcs().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        for (var rule : npc.rules()) {
            if (rule.trigger() == ZombiesNpcConfig.Trigger.INTERACT && eligible(npc, rule, map.npcWave())) {
                try { execute(npc, rule, player); }
                catch (IllegalArgumentException failure) { player.sendSystemMessage(Component.literal(failure.getMessage())); }
                catch (RuntimeException failure) {
                    com.mojang.logging.LogUtils.getLogger().error("NPC interaction failed in map {}", map.getMapName(), failure);
                    map.failNpcRound();
                }
                return;
            }
        }
        String dialogue = dialogues.getOrDefault(npc.id(), "");
        if (!dialogue.isBlank()) say(npc.id(), dialogue);
    }

    private void execute(ZombiesNpcConfig.Npc npc, ZombiesNpcConfig.Rule rule, ServerPlayer player) {
        // Every expected failure is checked before changing blocks, inventory or progress.
        List<ItemStack> inventory = player == null ? List.of() : ZombiesNpcInventory.plan(player, rule.actions());
        Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
        for (var action : rule.actions()) {
            if (action.kind() == ZombiesNpcConfig.Kind.SET_BLOCK) {
                BlockPos pos = blockPos(action.position());
                level().getChunk(pos);
                // Reject block entities at mutation time as their contents may have changed since validation.
                if (level().getBlockEntity(pos) != null) throw new IllegalArgumentException("NPC 不能替换存有数据的方块 " + pos);
                requireStableBlock(level().getBlockState(pos));
                changes.put(pos, parseBlock(action.block()));
            }
        }
        map.validateNpcBarrierOpens(rule.actions().stream()
                .filter(action -> action.kind() == ZombiesNpcConfig.Kind.OPEN_BARRIER)
                .map(ZombiesNpcConfig.Action::barrierGroup).toList());
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        try {
            for (var change : changes.entrySet()) {
                BlockPos pos = change.getKey();
                before.put(pos, level().getBlockState(pos));
                ZombiesNpcBlockJournal.get(level()).capture(level(), map.getMapName(), pos);
                level().removeBlockEntity(pos);
                if (!level().setBlock(pos, change.getValue(), 18) && !level().getBlockState(pos).equals(change.getValue())) {
                    throw new IllegalStateException("Cannot place NPC block " + pos);
                }
                NavigationWorldChanges.blockChanged(level(), pos, "npc-action");
            }
        } catch (RuntimeException failure) {
            before.forEach((pos, state) -> { level().removeBlockEntity(pos); level().setBlock(pos, state, 18); });
            throw failure;
        }
        for (var action : rule.actions()) if (action.kind() == ZombiesNpcConfig.Kind.OPEN_BARRIER) map.openNpcBarrier(action.barrierGroup());
        if (player != null) ZombiesNpcInventory.commit(player, inventory);
        completed.add(npc.id() + "." + rule.id());
        for (var action : rule.actions()) {
            String target = action.target() == null ? npc.id() : action.target();
            if (action.kind() == ZombiesNpcConfig.Kind.NPC) {
                if (action.appearance() != null) entities.get(target).apply(action.appearance());
                if (action.dialogue() != null) dialogues.put(target, action.dialogue());
            }
            if (action.kind() == ZombiesNpcConfig.Kind.DIALOGUE) say(target, action.text());
        }
        releaseUnusedChunks();
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
