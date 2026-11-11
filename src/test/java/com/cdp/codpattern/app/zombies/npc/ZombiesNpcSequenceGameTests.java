package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("codpattern_npc")
@PrefixGameTestTemplate(false)
public final class ZombiesNpcSequenceGameTests {
    @GameTest(template="empty", batch="npc_sequences", timeoutTicks=100)
    public static void defaultDialogueUsesConfiguredInterval(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        var actor = fixture.player();
        var npc = new ZombiesNpcConfig.Npc("guide", new ZombiesNpcConfig.Appearance("guide", null, ZombiesNpcConfig.Pose.STANDING, true, position(fixture.origin)), "first\nsecond", List.of(), 1);
        fixture.map.npcRuntime().start(new ZombiesNpcConfig(1, List.of(npc)), false);
        fixture.interact(actor);
        helper.assertTrue(fixture.map.lines.size() == 1, "default dialogue sends first line immediately");
        helper.runAtTickTime(30, () -> {
            try {
                fixture.map.npcRuntime().tick(1);
                helper.assertTrue(fixture.map.lines.size() == 2 && fixture.map.times.get(1) - fixture.map.times.get(0) >= 20, "default dialogue automatically sends second line after configured interval");
            } finally { fixture.close(); }
            helper.succeed();
        });
    }
    @GameTest(template="empty", batch="npc_sequences", timeoutTicks=100)
    public static void oneInteractionRunsTimedActionsInOrder(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        var actor = fixture.player();
        var other = fixture.player();
        var first = new ZombiesNpcConfig.Rule("first", ZombiesNpcConfig.Trigger.INTERACT, 0, List.of(), false, List.of(),
                List.of(dialogue("one\ntwo\nthree"), item(ZombiesNpcConfig.Kind.GIVE_ITEM, "minecraft:emerald"), item(ZombiesNpcConfig.Kind.TAKE_ITEM, "minecraft:emerald")), 0.15, "", 1);
        var second = new ZombiesNpcConfig.Rule("second", ZombiesNpcConfig.Trigger.INTERACT, 0, List.of(), false, List.of("guide.first"), List.of(dialogue("next rule")));
        fixture.start(List.of(first, second), 0.1);
        fixture.interact(actor);
        helper.assertTrue(fixture.map.lines.size() == 1 && actor.getInventory().countItem(Items.EMERALD) == 0, "first line is immediate, later actions wait");
        boolean[] sawReward = {false};
        for (int tick = 1; tick <= 30; tick++) {
            int current = tick;
            helper.runAtTickTime(tick, () -> {
                fixture.map.npcRuntime().tick(1);
                if (actor.getInventory().countItem(Items.EMERALD) == 1) sawReward[0] = true;
                if (current == 3) fixture.interact(other);
                if (current == 30) {
                    try {
                        helper.assertTrue(fixture.map.lines.equals(List.of("[guide] one", "[guide] two", "[guide] three")), "one click runs all dialogue, repeated clicks do not duplicate or start another rule");
                        helper.assertTrue(sawReward[0] && actor.getInventory().countItem(Items.EMERALD) == 0, "give happens before take");
                        helper.assertTrue(fixture.map.times.get(1) - fixture.map.times.get(0) >= 2 && fixture.map.times.get(2) - fixture.map.times.get(1) >= 2, "dialogue interval enforced");
                        fixture.interact(actor);
                        helper.assertTrue(fixture.map.lines.size() == 4 && fixture.map.lines.get(3).endsWith("next rule"), "different rule needs another interaction");
                    } finally { fixture.close(); }
                    helper.succeed();
                }
            });
        }
    }

    @GameTest(template="empty", batch="npc_sequences", timeoutTicks=100)
    public static void missingItemsPauseWithoutReplayingEarlierActions(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        var actor = fixture.player();
        fixture.start(List.of(new ZombiesNpcConfig.Rule("trade", ZombiesNpcConfig.Trigger.INTERACT, 0, List.of(), false, List.of(),
                List.of(dialogue("intro"), item(ZombiesNpcConfig.Kind.TAKE_ITEM, "minecraft:emerald"), item(ZombiesNpcConfig.Kind.GIVE_ITEM, "minecraft:diamond")))), 0);
        fixture.interact(actor);
        helper.assertTrue(fixture.map.lines.size() == 1, "dialogue before missing item executes");
        for (int tick = 1; tick <= 30; tick++) {
            int current = tick;
            helper.runAtTickTime(tick, () -> {
                if (current == 5) actor.getInventory().setItem(12, new ItemStack(Items.EMERALD));
                fixture.map.npcRuntime().tick(1);
                if (current == 30) {
                    try {
                        helper.assertTrue(actor.getInventory().countItem(Items.EMERALD) == 0 && actor.getInventory().countItem(Items.DIAMOND) == 1, "same interaction resumes after inventory becomes sufficient");
                        helper.assertTrue(fixture.map.lines.size() == 1, "intro not replayed");
                    } finally { fixture.close(); }
                    helper.succeed();
                }
            });
        }
    }

    @GameTest(template="empty", batch="npc_sequences", timeoutTicks=100)
    public static void resetCancelsDelayedRewards(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        var actor = fixture.player();
        fixture.start(List.of(new ZombiesNpcConfig.Rule("later", ZombiesNpcConfig.Trigger.INTERACT, 0, List.of(), false, List.of(),
                List.of(dialogue("start"), item(ZombiesNpcConfig.Kind.GIVE_ITEM, "minecraft:emerald")), 1, "", 1)), 0);
        fixture.interact(actor);
        fixture.map.npcRuntime().reset();
        helper.runAtTickTime(40, () -> {
            try {
                fixture.map.npcRuntime().tick(1);
                helper.assertTrue(actor.getInventory().countItem(Items.EMERALD) == 0, "ending cancels timers and delayed rewards");
            } finally { fixture.close(); }
            helper.succeed();
        });
    }

    @GameTest(template="empty", batch="npc_sequences", timeoutTicks=100)
    public static void automaticItemConditionFindsBackpackAndOffhand(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        try {
            var player = fixture.player();
            var outside = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "outside"));
            outside.getInventory().setItem(15, new ItemStack(Items.EMERALD, 64));
            var rule = new ZombiesNpcConfig.Rule("has_item", ZombiesNpcConfig.Trigger.AUTO, 0, List.of(), false, List.of(), List.of(dialogue("found")), 0, "minecraft:emerald", 2);
            fixture.start(List.of(rule), 0);
            fixture.map.npcRuntime().tick(1);
            helper.assertTrue(fixture.map.lines.isEmpty(), "outside players do not satisfy room condition");
            player.getInventory().setItem(15, new ItemStack(Items.EMERALD));
            fixture.map.npcRuntime().tick(1);
            helper.assertTrue(fixture.map.lines.isEmpty(), "required count enforced");
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.EMERALD));
            fixture.map.npcRuntime().tick(1); fixture.map.npcRuntime().tick(1);
            helper.assertTrue(fixture.map.lines.size() == 1 && player.getInventory().countItem(Items.EMERALD) == 2, "backpack and offhand combine, checking does not consume, trigger once");
        } finally { fixture.close(); }
        helper.succeed();
    }

    @GameTest(template="empty", batch="npc_sequences", timeoutTicks=100)
    public static void vulnerabilityAndMultipleBlocksRestore(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        try {
            BlockPos a = fixture.origin.offset(2, 0, 0), b = fixture.origin.offset(3, 0, 0);
            helper.getLevel().setBlock(a, Blocks.AIR.defaultBlockState(), 18);
            helper.getLevel().setBlock(b, Blocks.GOLD_BLOCK.defaultBlockState(), 18);
            var placements = List.of(new ZombiesNpcConfig.BlockPlacement(position(a), "minecraft:stone"), new ZombiesNpcConfig.BlockPlacement(position(b), "minecraft:oak_planks"));
            var action = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.SET_BLOCK, null, null, null, null, 0, null, null, 0, null, null, null, placements);
            fixture.start(List.of(new ZombiesNpcConfig.Rule("blocks", ZombiesNpcConfig.Trigger.AUTO, 0, List.of(), false, List.of(), List.of(action))), 0);
            var npc = fixture.entity();
            helper.assertTrue(!npc.hurt(helper.getLevel().damageSources().generic(), 100), "old NPCs default to invulnerable");
            npc.apply(new ZombiesNpcConfig.Appearance(null, null, null, null, null, false));
            helper.assertTrue(npc.hurt(helper.getLevel().damageSources().playerAttack(fixture.player()), 100)
                    && !npc.storyAlive() && !npc.isAlive() && npc.posture() == 2, "player can kill vulnerable NPC and the game recognizes its corpse");
            npc.apply(new ZombiesNpcConfig.Appearance(null, null, ZombiesNpcConfig.Pose.STANDING, true, null, true));
            helper.assertTrue(npc.storyAlive() && npc.getHealth() == npc.getMaxHealth(), "scripted revival restores health");
            fixture.map.npcRuntime().tick(1);
            helper.assertTrue(helper.getLevel().getBlockState(a).is(Blocks.STONE) && helper.getLevel().getBlockState(b).is(Blocks.OAK_PLANKS), "one action places multiple selected blocks");
            fixture.map.npcRuntime().reset();
            helper.assertTrue(helper.getLevel().getBlockState(a).isAir() && helper.getLevel().getBlockState(b).is(Blocks.GOLD_BLOCK), "both original states restore");
        } finally { fixture.close(); }
        helper.succeed();
    }

    private static ZombiesNpcConfig.Action dialogue(String text) { return new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.DIALOGUE, null, null, text, null, 0, null, null, 0); }
    private static ZombiesNpcConfig.Action item(ZombiesNpcConfig.Kind kind, String item) { return new ZombiesNpcConfig.Action(kind, null, null, null, item, 1, null, null, 0); }
    private static ZombiesNpcConfig.Position position(BlockPos pos) { return new ZombiesNpcConfig.Position(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0); }
    private static final class Fixture {
        final TestMap map;
        final BlockPos origin;
        Fixture(GameTestHelper helper) {
            origin = helper.absolutePos(new BlockPos(1, 2, 1));
            map = new TestMap(helper.getLevel(), "sequence-" + UUID.randomUUID().toString().substring(0, 8), new AreaData(origin.offset(-1, -1, -1), origin.offset(8, 5, 8)));
            FPSMCore.getInstance().registerMap(map.getGameType(), map);
        }
        ServerPlayer player() {
            var player = new FakePlayer(map.getServerLevel(), new GameProfile(UUID.randomUUID(), "actor-" + map.actors.size()));
            player.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5); map.actors.add(player); return player;
        }
        void start(List<ZombiesNpcConfig.Rule> rules, double interval) {
            var npc = new ZombiesNpcConfig.Npc("guide", new ZombiesNpcConfig.Appearance("guide", null, ZombiesNpcConfig.Pose.STANDING, true, position(origin)), "", rules, interval);
            map.npcRuntime().start(new ZombiesNpcConfig(1, List.of(npc)), false);
        }
        ZombiesNpcEntity entity() { return map.getServerLevel().getEntitiesOfClass(ZombiesNpcEntity.class, new AABB(origin).inflate(1)).stream().findFirst().orElseThrow(); }
        void interact(ServerPlayer player) { map.npcRuntime().interact(player, "guide", entity()); }
        void close() { map.npcRuntime().reset(); FPSMCore.getInstance().unregisterMap(map); }
    }
    private static final class TestMap extends ZombiesMap {
        final List<ServerPlayer> actors = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        final List<Long> times = new ArrayList<>();
        TestMap(net.minecraft.server.level.ServerLevel level, String name, AreaData area) { super(level, name, area); }
        @Override public ZombiesGamePhase currentPhase() { return ZombiesGamePhase.WAVE_ACTIVE; }
        @Override public int npcWave() { return 1; }
        @Override public boolean isAliveSurvivor(UUID id) { return actors.stream().anyMatch(player -> player.getUUID().equals(id)); }
        @Override public List<ServerPlayer> npcPlayers() { return List.copyOf(actors); }
        @Override public void tick() { npcRuntime().tick(1); }
        @Override public void sendNpcDialogue(Component message) { lines.add(message.getString()); times.add(getServerLevel().getGameTime()); }
    }
}
