package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.cdp.codpattern.config.zombies.ZombiesConfigPaths;
import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

@GameTestHolder("codpattern_npc")
@PrefixGameTestTemplate(false)
public final class ZombiesNpcGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void editorCommandsSaveAndValidate(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        String name = "npc-edit-" + UUID.randomUUID().toString().substring(0, 8);
        var map = new ZombiesMap(level, name, new AreaData(origin.offset(-1, -1, -1), origin.offset(5, 5, 5)));
        var core = com.phasetranscrystal.fpsmatch.core.FPSMCore.getInstance();
        core.registerMap(map.getGameType(), map);
        var player = player(level, "npc-editor");
        player.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        var source = level.getServer().createCommandSourceStack().withEntity(player).withPermission(2);
        String prefix = "zombiesnpc \"" + name + "\" ";
        var commands = level.getServer().getCommands();
        var path = ZombiesNpcRepository.path(level.getServer(), name);
        try {
            helper.assertTrue(commands.performPrefixedCommand(source, prefix + "create guide") == 1, "create command");
            helper.assertTrue(commands.performPrefixedCommand(source, prefix + "guide set {\"appearance\":{\"name\":\"向导\",\"pose\":\"SITTING\"}}") == 1, "edit command");
            var saved = ZombiesNpcRepository.load(path).npcs().get(0);
            helper.assertTrue(saved.appearance().name().equals("向导") && saved.appearance().pose() == ZombiesNpcConfig.Pose.SITTING, "saved edits");
            String before = Files.readString(path);
            helper.assertTrue(commands.performPrefixedCommand(source, prefix + "guide rule {\"id\":\"bad\",\"trigger\":\"AUTO\",\"actions\":[{\"kind\":\"GIVE_ITEM\",\"item\":\"minecraft:diamond\",\"count\":1}]}") == 0, "invalid command rejected");
            helper.assertTrue(before.equals(Files.readString(path)), "invalid edit preserves original file");
            helper.assertTrue(commands.performPrefixedCommand(source.withPermission(0), prefix + "remove guide") == 0, "editing needs operator permission");
            helper.assertTrue(commands.performPrefixedCommand(source, prefix + "preview") == 1, "preview command");
            var entity = level.getEntitiesOfClass(ZombiesNpcEntity.class, new AABB(origin).inflate(1)).stream().findFirst().orElseThrow();
            helper.assertTrue(entity.posture() == 1 && entity.getName().getString().equals("向导"), "preview reflects saved edits");
            helper.assertTrue(commands.performPrefixedCommand(source, prefix + "clear") == 1 && entity.isRemoved(), "clear command releases preview");
        } finally {
            map.npcRuntime().reset();
            core.unregisterMap(map);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void configuration(GameTestHelper helper) {
        ZombiesNpcConfigTest.main(new String[0]);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void exchangesAreAtomic(GameTestHelper helper) {
        FakePlayer player = player(helper.getLevel(), "npc-inventory");
        var take = action(ZombiesNpcConfig.Kind.TAKE_ITEM, "minecraft:emerald", 3);
        var give = action(ZombiesNpcConfig.Kind.GIVE_ITEM, "minecraft:diamond", 2);
        player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 2));
        rejects(() -> ZombiesNpcInventory.plan(player, List.of(take, give)));
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 2, "missing items must not consume inventory");
        player.getInventory().setItem(1, new ItemStack(Items.EMERALD, 2));
        var planned = ZombiesNpcInventory.plan(player, List.of(take, give));
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 2, "planning must be read only");
        ZombiesNpcInventory.commit(player, planned);
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 1 && player.getInventory().countItem(Items.DIAMOND) == 2, "count across slots and reward");
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        rejects(() -> ZombiesNpcInventory.plan(player, List.of(give)));
        helper.assertTrue(player.getInventory().countItem(Items.STONE) == 2304, "full inventory must remain intact");
        player.getInventory().clearContent();
        var tagged = new ItemStack(Items.EMERALD, 4);
        tagged.getOrCreateTag().putString("quest", "wrong");
        player.getInventory().setItem(0, tagged);
        rejects(() -> ZombiesNpcInventory.plan(player, List.of(take)));
        helper.assertTrue(player.getInventory().getItem(0).getTag().getString("quest").equals("wrong"), "NBT must match exactly");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void storyTriggersOnceAndResets(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        String name = "npc-test-" + UUID.randomUUID().toString().substring(0, 8);
        var rulesPath = ZombiesConfigPaths.zombiesMapBarrierGroups(level.getServer(), name);
        Files.createDirectories(rulesPath.getParent());
        Files.writeString(rulesPath, "{\"schemaVersion\":2,\"groups\":{\"1\":{\"entries\":{\"1\":{\"cost\":0,\"requiredItem\":\"\"}}}}}");
        var map = new StoryMap(level, name, new AreaData(origin.offset(-1, -1, -1), origin.offset(8, 5, 8)));
        var barrierPos = origin.offset(6, 0, 6);
        var barrier = new ZombiesBarrierData("door", "门", 1, 0, true, level.dimension(), barrierPos, barrierPos, barrierPos).withEntryId(1);
        map.applyObjects(new ZombiesMapObjects(List.of(), List.of(), List.of(barrier), List.of(), List.of(), List.of(), java.util.Optional.empty(), List.of(), List.of(), List.of(), List.of()));
        var initial = new ZombiesNpcConfig.Appearance("向导", "minecraft:textures/entity/player/wide/steve.png", ZombiesNpcConfig.Pose.SITTING, true, position(origin));
        var blockPos = origin.offset(3, 0, 0);
        var original = Blocks.GOLD_BLOCK.defaultBlockState();
        level.setBlock(blockPos, original, 2);
        var relocate = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.NPC, null,
                new ZombiesNpcConfig.Appearance("幸存者", null, ZombiesNpcConfig.Pose.STANDING, true, position(origin.offset(1, 0, 0))), null, null, 0, null, null, 0);
        var dialogue = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.DIALOGUE, null, null, "十四波，出发！", null, 0, null, null, 0);
        var place = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.SET_BLOCK, null, null, null, null, 0, position(blockPos), "minecraft:stone", 0);
        var open = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.OPEN_BARRIER, null, null, null, null, 0, null, null, 1);
        var trade = new ZombiesNpcConfig.Rule("trade", ZombiesNpcConfig.Trigger.INTERACT, 0, List.of(), false, List.of(), List.of(action(ZombiesNpcConfig.Kind.TAKE_ITEM, "minecraft:emerald", 3), action(ZombiesNpcConfig.Kind.GIVE_ITEM, "minecraft:diamond", 2), place, open), 0, "", 1);
        var wave = new ZombiesNpcConfig.Rule("move", ZombiesNpcConfig.Trigger.AUTO, 14, List.of(1), true, List.of(), List.of(relocate, dialogue), 0, "", 1);
        var death = new ZombiesNpcConfig.Rule("death", ZombiesNpcConfig.Trigger.AUTO, 15, List.of(), false, List.of("guide.move"),
                List.of(new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.NPC, null,
                        new ZombiesNpcConfig.Appearance(null, null, ZombiesNpcConfig.Pose.LYING, false, null), null, null, 0, null, null, 0)), 0, "", 1);
        var config = new ZombiesNpcConfig(1, List.of(new ZombiesNpcConfig.Npc("guide", initial, "", List.of(trade, wave, death))));
        try {
            map.npcRuntime().start(config, false);
            var entity = level.getEntitiesOfClass(ZombiesNpcEntity.class, new AABB(origin).inflate(3)).stream().findFirst().orElseThrow();
            var first = player(level, "npc-first");
            var second = player(level, "npc-second");
            first.moveTo(entity.position()); second.moveTo(entity.position());
            first.getInventory().setItem(0, new ItemStack(Items.EMERALD, 3));
            second.getInventory().setItem(0, new ItemStack(Items.EMERALD, 3));
            map.npcRuntime().tick(13);
            helper.assertTrue(map.lines == 0 && entity.posture() == 1, "no early wave trigger");
            map.npcRuntime().interact(first, "guide", entity);
            map.npcRuntime().interact(second, "guide", entity);
            helper.assertTrue(first.getInventory().countItem(Items.DIAMOND) == 2 && second.getInventory().countItem(Items.DIAMOND) == 0, "shared completion prevents duplicate rewards");
            helper.assertTrue(second.getInventory().countItem(Items.EMERALD) == 3, "second player is not charged");
            helper.assertTrue(map.npcBarrierOpen(1) && level.getBlockState(blockPos).is(Blocks.STONE), "trade opens actual barrier state and places block");
            map.npcRuntime().tick(13); map.npcRuntime().tick(14);
            helper.assertTrue(map.lines == 1 && entity.blockPosition().equals(origin.offset(1, 0, 0)) && entity.posture() == 0, "barrier alternative relocates and speaks once");
            map.npcRuntime().tick(15);
            helper.assertTrue(!entity.storyAlive() && entity.posture() == 2, "death is a persistent story posture");
            map.npcRuntime().reset();
            helper.assertTrue(entity.isRemoved() && level.getBlockState(blockPos).equals(original), "end restores entity and block state");
            map.applyObjects(map.objects());
            helper.assertTrue(!map.npcBarrierOpen(1), "map reset clears opened barriers");
            map.npcRuntime().start(config, false);
            var next = level.getEntitiesOfClass(ZombiesNpcEntity.class, new AABB(origin).inflate(3)).stream().filter(npc -> !npc.isRemoved()).findFirst().orElseThrow();
            helper.assertTrue(next.posture() == 1 && next.storyAlive() && next.blockPosition().equals(origin), "next round uses authored appearance");
            map.npcRuntime().tick(14);
            helper.assertTrue(map.lines == 2, "wave alternative works in next round");
        } finally { map.npcRuntime().reset(); }
        helper.succeed();
    }

    private static ZombiesNpcConfig.Action action(ZombiesNpcConfig.Kind kind, String item, int count) {
        return new ZombiesNpcConfig.Action(kind, null, null, null, item, count, null, null, 0);
    }
    private static ZombiesNpcConfig.Position position(BlockPos pos) { return new ZombiesNpcConfig.Position(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 90); }
    private static FakePlayer player(ServerLevel level, String name) { return new FakePlayer(level, new GameProfile(UUID.randomUUID(), name)); }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Expected rejection"); }
    private static final class StoryMap extends ZombiesMap {
        int lines;
        StoryMap(ServerLevel level, String name, AreaData area) { super(level, name, area); }
        @Override public ZombiesGamePhase currentPhase() { return ZombiesGamePhase.WAVE_ACTIVE; }
        @Override public boolean isAliveSurvivor(UUID id) { return true; }
        @Override public int npcWave() { return 1; }
        @Override public void sendNpcDialogue(Component message) { lines++; }
    }
}
