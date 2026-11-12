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
                if (rule.graph() != null) for (var node : rule.graph().nodes()) for (var condition : node.conditions()) for (var test : condition.tests()) {
                    switch (test.kind()) {
                        case BARRIER -> requireBarrier(test.value());
                        case ITEM_ROOM, ITEM_ACTOR -> ZombiesNpcInventory.parse(test.item());
                        default -> { }
                    }
                }
                for (var action : rule.executableActions()) switch (action.kind()) {
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
                    case MAP_EVENT -> map.scenarioRuntime().requireEvent(action.text());
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
        if (preview || !map.currentPhase().isRoundRunning()) return;
        for (var sequence : List.copyOf(sequences.values())) advance(sequence);
        if (!map.currentPhase().isRoundRunning()) return;
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
        if (preview || !map.currentPhase().isRoundRunning() || !map.isAliveSurvivor(player.getUUID())
                || player.level() != level() || entities.get(id) != entity || entity.isRemoved()
                || !entity.storyAlive() || player.distanceToSqr(entity) > 36) return;
        long now = level().getGameTime();
        if (now - lastInteraction.getOrDefault(player.getUUID(), now - 10) < 10) return;
        lastInteraction.put(player.getUUID(), now);
        if (sequences.containsKey(id)) {
            var sequence=sequences.get(id);
            if(sequence.actor!=null && sequence.actor.getUUID().equals(player.getUUID()) && sequence.graphNodes!=null) {
                var node=sequence.graphNodes.get(sequence.cursor);
                if(node.type()==ZombiesNpcGraph.Type.INTERACT && sequence.nodeStarted && now>sequence.armedAt) {
                    sequence.interactionReceived=true;sequence.due=now;advance(sequence);return;
                }
                if(node.type()==ZombiesNpcGraph.Type.CHOICE && sequence.nodeStarted) {openDialogue(sequence,node);return;}
            }
            reportPending(player,sequence); return;
        }
        var npc = config.npcs().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        for (var rule : npc.rules()) {
            if (rule.trigger() == ZombiesNpcConfig.Trigger.INTERACT && eligible(npc, rule, map.npcWave())) {
                startSequence(npc, rule, player);
                return;
            }
        }
        if (npc.rules().stream().anyMatch(rule -> rule.graph() != null)) {
            if(!config.debug() || !player.hasPermissions(2))return;
            for(var rule:npc.rules())if(rule.trigger()==ZombiesNpcConfig.Trigger.INTERACT&&!completed.contains(id+"."+rule.id())) {
                player.sendSystemMessage(Component.literal("规则 "+rule.id()+" 的入口条件未满足：最低波次 "+rule.minWave()+"，屏障 "+rule.openedBarriers()+"，前置规则 "+rule.requires()+(rule.requiredItem().isBlank()?"":"，所需物品 "+rule.requiredItem()+" × "+rule.requiredCount())));return;
            }
            player.sendSystemMessage(Component.literal("该 NPC 的交互规则本局已完成。"));return;
        }
        String dialogue = dialogues.getOrDefault(id, "");
        if (!dialogue.isBlank()) startSequence(npc, null, player);
    }

    private void startSequence(ZombiesNpcConfig.Npc npc, ZombiesNpcConfig.Rule rule, ServerPlayer player) {
        List<ZombiesNpcConfig.Action> actions = rule == null
                ? List.of(new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.DIALOGUE, null, null,
                        dialogues.getOrDefault(npc.id(), ""), null, 0, null, null, 0)) : rule.actions();
        var sequence = new Sequence(npc, rule, player, actions);
        sequence.due = level().getGameTime() + (sequence.graphNodes == null ? delay(sequence, 0) : 0);
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
        if(sequence.view!=null && !canConverse(sequence)) closeDialogue(sequence);
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
            if (sequence.graphNodes != null) { advanceGraph(sequence, now); return; }
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

    private void reportPending(ServerPlayer actor,Sequence sequence){
        if(actor==null || !config.debug() || !actor.hasPermissions(2))return;String text="剧情正在按设置的间隔执行，请稍候。";
        if(sequence.graphNodes!=null){var node=sequence.graphNodes.get(sequence.cursor);if(node.type()==ZombiesNpcGraph.Type.AWAIT){
            var details=new java.util.ArrayList<String>();for(var test:node.condition().tests())details.add((test.negated()?"不满足 ":"")+switch(test.kind()){
                case WAVE->"波次达到 "+test.value();case BARRIER->"屏障已开启 "+test.value();case RULE_DONE->"规则已完成 "+test.reference();case NPC_ALIVE->"NPC 存活 "+test.reference();case ITEM_ROOM,ITEM_ACTOR->"持有 "+test.item()+" × "+test.count();});
            text="等待条件（"+(node.condition().match()==ZombiesNpcGraph.Match.ALL?"全部满足":"任一满足")+"）："+String.join("；",details);
        }}
        long now=level().getGameTime();if(now-sequence.lastNotice>=40||!text.equals(sequence.notice)){actor.sendSystemMessage(Component.literal(text));sequence.lastNotice=now;sequence.notice=text;}
    }
    private boolean condition(ZombiesNpcGraph.Condition condition, ServerPlayer actor) {
        return condition.evaluate(test -> switch (test.kind()) {
            case WAVE -> map.npcWave() >= test.value();
            case BARRIER -> map.npcBarrierOpen(test.value());
            case ITEM_ROOM -> map.npcPlayers().stream().anyMatch(p -> ZombiesNpcInventory.contains(p, test.item(), test.count()));
            case ITEM_ACTOR -> actor != null && ZombiesNpcInventory.contains(actor, test.item(), test.count());
            case RULE_DONE -> completed.contains(test.reference());
            case NPC_ALIVE -> entities.containsKey(test.reference()) && entities.get(test.reference()).storyAlive();
        });
    }

    private void advanceGraph(Sequence sequence, long now) {
        // Graphs are acyclic and bounded on load. This budget also guards future interpreter changes.
        for (int steps = 0; steps < 256; steps++) {
            var node = sequence.graphNodes.get(sequence.cursor);
            String next = node.next();
            switch (node.type()) {
                case END -> {
                    if (node.complete()) completed.add(sequence.npc.id() + "." + sequence.rule.id());
                    sequences.remove(sequence.npc.id()); releaseUnusedChunks(); return;
                }
                case INTERACT -> {
                    if(!sequence.nodeStarted){sequence.nodeStarted=true;sequence.armedAt=now;sequence.interactionReceived=false;}
                    if(!sequence.interactionReceived)return;
                }
                case CHOICE -> {
                    if(!sequence.nodeStarted){sequence.nodeStarted=true;openDialogue(sequence,node);}
                    else if(sequence.view!=null && now>=sequence.refreshAt){
                        sequence.refreshAt=now+20;
                        if(!visibleOptions(sequence,node).equals(sequence.view.options()))openDialogue(sequence,node);
                    }
                    return;
                }
                case ENTRY -> { }
                case BRANCH -> next = condition(node.condition(), sequence.actor) ? node.next() : node.onFalse();
                case AWAIT -> {
                    if (!condition(node.condition(), sequence.actor)) { sequence.due = now + 20; if(sequence.notice.isEmpty())reportPending(sequence.actor,sequence); return; }
                }
                case WAIT -> {
                    if (!sequence.nodeStarted) {
                        sequence.nodeStarted = true;
                        sequence.due = now + ZombiesNpcConfig.intervalTicks(node.seconds());
                        if (sequence.due > now) return;
                    }
                }
                case ACTION -> {
                    var action = node.action();
                    if (!sequence.nodeStarted) {
                        sequence.nodeStarted = true;
                        double seconds = action.delaySeconds() != null ? action.delaySeconds()
                                : sequence.executedAction ? sequence.rule.actionIntervalSeconds() : 0;
                        sequence.due = now + ZombiesNpcConfig.intervalTicks(seconds);
                        if (sequence.due > now) return;
                    }
                    String target = action.target() == null ? sequence.npc.id() : action.target();
                    if (action.kind() == ZombiesNpcConfig.Kind.DIALOGUE) {
                        String[] lines = action.text().split("\\n", -1);
                        int interval = ZombiesNpcConfig.intervalTicks(action.dialogueIntervalSeconds() == null
                                ? sequence.npc.dialogueIntervalSeconds() : action.dialogueIntervalSeconds());
                        while (sequence.line < lines.length) {
                            say(target, lines[sequence.line++]);
                            if (sequence.line < lines.length && interval > 0) { sequence.due = now + interval; return; }
                        }
                    } else executeAction(action, target, sequence.actor);
                    sequence.executedAction = true; sequence.blockedMessage = "";
                }
            }
            if(next.equals(sequence.rule.graph().entry().id())) {
                sequence.cursor=next;sequence.nodeStarted=false;sequence.line=0;sequence.executedAction=false;
                if(sequence.rule.trigger()==ZombiesNpcConfig.Trigger.INTERACT){sequences.remove(sequence.npc.id());releaseUnusedChunks();}
                else sequence.due=now+20;
                return;
            }
            sequence.cursor = next; sequence.nodeStarted = false; sequence.line = 0; sequence.notice="";
        }
        throw new IllegalStateException("NPC graph execution budget exceeded");
    }

    private boolean canConverse(Sequence s) {
        var entity=entities.get(s.npc.id());
        return !preview && map.currentPhase().isRoundRunning() && s.actor!=null && !s.actor.isRemoved()
                && s.actor.level()==level() && map.isAliveSurvivor(s.actor.getUUID())
                && entity!=null && !entity.isRemoved() && entity.storyAlive() && s.actor.distanceToSqr(entity)<=36;
    }
    private void closeDialogue(Sequence s) {
        if(s.view==null)return;
        ZombiesNpcDialogueNetwork.send(s.actor,new ZombiesNpcDialogueNetwork.View(s.view.token(),false,0,Component.empty(),Component.empty(),List.of()));
        s.view=null;
    }
    private void openDialogue(Sequence s,ZombiesNpcGraph.Node node) {
        if(!canConverse(s))return;
        for(var runtime:ACTIVE)for(var other:runtime.sequences.values())if(other!=s && other.actor!=null && other.actor.getUUID().equals(s.actor.getUUID()))runtime.closeDialogue(other);
        String speaker=node.speaker().isBlank()?s.npc.id():node.speaker();var entity=entities.get(speaker);
        var name=Component.literal(entity.chatName()).withStyle(style->style.withColor(net.minecraft.network.chat.TextColor.parseColor(entity.nameColor())));
        var text=Component.literal(node.text()).withStyle(style->style.withColor(net.minecraft.network.chat.TextColor.parseColor(entity.textColor())));
        var options=visibleOptions(s,node);
        s.refreshAt=level().getGameTime()+20;
        s.view=new ZombiesNpcDialogueNetwork.View(UUID.randomUUID(),true,entity.getId(),name,text,options);
        ZombiesNpcDialogueNetwork.send(s.actor,s.view);
    }
    private List<ZombiesNpcDialogueNetwork.Answer> visibleOptions(Sequence s,ZombiesNpcGraph.Node node) {
        return node.options().stream().filter(o->o.condition()==null||condition(o.condition(),s.actor))
                .map(o->new ZombiesNpcDialogueNetwork.Answer(o.id(),o.text())).toList();
    }
    /** Snapshot for diagnostics and tests; no mutable sequence state is exposed. */
    public ZombiesNpcDialogueNetwork.View dialogue(ServerPlayer player) {
        return sequences.values().stream().filter(s->s.actor!=null&&s.actor.getUUID().equals(player.getUUID())&&s.view!=null).map(s->s.view).findFirst().orElse(null);
    }
    public static boolean selectDialogue(ServerPlayer player,UUID token,String option) {
        for(var runtime:List.copyOf(ACTIVE))for(var s:List.copyOf(runtime.sequences.values())) {
            if(s.view==null||!s.view.token().equals(token)||s.actor==null||!s.actor.getUUID().equals(player.getUUID()))continue;
            if(!runtime.canConverse(s)){runtime.closeDialogue(s);return false;}
            if(option.isEmpty()){runtime.closeDialogue(s);return true;}
            var node=s.graphNodes.get(s.cursor);
            var selected=node.options().stream().filter(o->o.id().equals(option)).findFirst().orElse(null);
            // The option must have been offered AND still be eligible; old or forged packets cannot run a branch.
            if(selected==null||s.view.options().stream().noneMatch(o->o.id().equals(option))
                    || selected.condition()!=null&&!runtime.condition(selected.condition(),player)) {
                runtime.openDialogue(s,node);return false;
            }
            runtime.closeDialogue(s);
            if(selected.next().equals(s.rule.graph().entry().id())) {
                runtime.sequences.remove(s.npc.id());runtime.releaseUnusedChunks();return true;
            }
            s.cursor=selected.next();s.nodeStarted=false;s.interactionReceived=false;s.line=0;s.notice="";s.due=runtime.level().getGameTime();
            runtime.advance(s);return true;
        }
        return false;
    }

    private void executeAction(ZombiesNpcConfig.Action action, String target, ServerPlayer actor) {
        switch (action.kind()) {
            case TAKE_ITEM, GIVE_ITEM -> {
                if (actor == null) throw new IllegalStateException("Item action has no interaction owner");
                ZombiesNpcInventory.commit(actor, ZombiesNpcInventory.plan(actor, List.of(action)));
                actor.sendSystemMessage(Component.translatable(action.kind()==ZombiesNpcConfig.Kind.GIVE_ITEM?"npc.item.received":"npc.item.delivered",ZombiesNpcInventory.parse(action.item()).getHoverName(),action.count()));
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
            case MAP_EVENT -> map.scenarioRuntime().trigger(action.text());
            default -> throw new IllegalStateException("Unexpected NPC action");
        }
    }

    public boolean isRuleComplete(String key){return completed.contains(key);}
    public void validateMapBlocks(List<ZombiesNpcConfig.BlockPlacement> placements){
        // Reuse the same map/device boundaries and static-block policy as NPC changes, in bounded batches.
        for(int i=0;i<placements.size();i+=256){var batch=placements.subList(i,Math.min(i+256,placements.size()));if(batch.isEmpty())continue;
            var action=new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.SET_BLOCK,null,null,null,null,0,null,null,0,null,null,null,batch);
            var npc=new ZombiesNpcConfig.Npc("event_validation",new ZombiesNpcConfig.Appearance("",null,null,true,batch.get(0).position()),"",List.of(new ZombiesNpcConfig.Rule("event",ZombiesNpcConfig.Trigger.AUTO,0,List.of(),false,List.of(),List.of(action))));
            validate(new ZombiesNpcConfig(1,List.of(npc)));
            for(var cell:batch){var pos=blockPos(cell.position());requireReplaceableStoryCell(pos);}
        }
    }
    private void requireReplaceableStoryCell(BlockPos pos){
        try {requireStableBlock(level().getBlockState(pos));}
        catch(IllegalArgumentException e){throw new IllegalArgumentException("目标区域含暂不支持替换的方块 " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level().getBlockState(pos).getBlock()) + "，位置 " + pos.toShortString(),e);}
    }
    public boolean debugEnabled(){return config.debug();}
    public void applyMapBlocks(List<ZombiesNpcConfig.BlockPlacement> placements){validateMapBlocks(placements);placeBlocks(placements);}
    private void placeBlocks(List<ZombiesNpcConfig.BlockPlacement> placements) {
        Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
        for (var placement : placements) {
            BlockPos pos = blockPos(placement.position());
            level().getChunk(pos);
            requireReplaceableStoryCell(pos);
            changes.put(pos, parseBlock(placement.block()));
        }
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        ZombiesNpcBlockJournal.get(level()).captureAll(level(), map.getMapName(), changes.keySet());
        try {
            for (var change : changes.entrySet()) {
                BlockPos pos = change.getKey();
                before.put(pos, level().getBlockState(pos));
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
        String notice="";long lastNotice;
        Map<String, ZombiesNpcGraph.Node> graphNodes;
        String cursor;
        boolean nodeStarted, executedAction, interactionReceived;
        long armedAt, refreshAt;
        ZombiesNpcDialogueNetwork.View view;
        Sequence(ZombiesNpcConfig.Npc npc, ZombiesNpcConfig.Rule rule, ServerPlayer actor, List<ZombiesNpcConfig.Action> actions) {
            this.npc = npc; this.rule = rule; this.actor = actor; this.actions = actions;
            if (rule != null && rule.graph() != null) {
                graphNodes = new HashMap<>(); rule.graph().nodes().forEach(node -> graphNodes.put(node.id(), node));
                cursor = rule.graph().entry().id();
            }
        }
    }

    private void say(String id, String text) {
        var entity = entities.get(id);
        String name = entity == null ? id : entity.chatName();
        var nameColor = net.minecraft.network.chat.TextColor.parseColor(entity == null ? "#FFFFFF" : entity.nameColor());
        var textColor = net.minecraft.network.chat.TextColor.parseColor(entity == null ? "#FFFFFF" : entity.textColor());
        map.sendNpcDialogue(Component.literal("[" + name + "] ").withStyle(style -> style.withColor(nameColor))
                .append(Component.literal(text).withStyle(style -> style.withColor(textColor))));
    }

    public void reset() {
        ACTIVE.remove(this);
        for(var sequence:sequences.values())closeDialogue(sequence);
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
