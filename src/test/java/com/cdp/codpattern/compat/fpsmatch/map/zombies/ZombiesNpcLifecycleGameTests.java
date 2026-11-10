package com.cdp.codpattern.compat.fpsmatch.map.zombies;

import com.cdp.codpattern.app.match.runtime.termination.ForceEndCoordinator;
import com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.npc.ZombiesNpcConfig;
import com.cdp.codpattern.app.zombies.npc.ZombiesNpcEntity;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("codpattern_npc")
@PrefixGameTestTemplate(false)
public final class ZombiesNpcLifecycleGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void normalAndForcedEndRestoreStory(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var pos = new ZombiesNpcConfig.Position(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0);
        var name = "npc-end-" + UUID.randomUUID().toString().substring(0, 8);
        var map = new ZombiesMap(level, name, new AreaData(origin.offset(-1, -1, -1), origin.offset(4, 4, 4)));
        FPSMCore.getInstance().registerMap(map.getGameType(), map);
        var service = RoomTerminationService.get(level.getServer());
        var blockPos = origin.offset(2, 0, 0);
        var block = new ZombiesNpcConfig.Position(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 0);
        var place = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.SET_BLOCK, null, null, null, null, 0, block, "minecraft:stone", 0);
        var rule = new ZombiesNpcConfig.Rule("place", ZombiesNpcConfig.Trigger.AUTO, 1, List.of(), false, List.of(), List.of(place));
        var config = new ZombiesNpcConfig(1, List.of(new ZombiesNpcConfig.Npc("guide",
                new ZombiesNpcConfig.Appearance("NPC", null, ZombiesNpcConfig.Pose.STANDING, true, pos), "", List.of(rule))));
        try {
            for (boolean forced : List.of(true, false)) {
                level.setBlock(blockPos, Blocks.GOLD_BLOCK.defaultBlockState(), 18);
                service.begin(map.roomId(), List.of(), null);
                map.runtimeState().transitionTo(ZombiesGamePhase.WAVE_ACTIVE);
                map.isStart = true;
                map.npcRuntime().start(config, false);
                map.npcRuntime().tick(1);
                var entity = level.getEntitiesOfClass(ZombiesNpcEntity.class, new AABB(origin).inflate(1)).stream().findFirst().orElseThrow();
                helper.assertTrue(level.getBlockState(blockPos).is(Blocks.STONE), "story action executes");
                if (forced) {
                    var result = service.forceEnd(level.getServer().createCommandSourceStack(), map.roomId(), service.generation(map.roomId()));
                    helper.assertTrue(result.outcome() == ForceEndCoordinator.Outcome.COMPLETED, "forced termination completes " + result.failures());
                } else map.resetGame();
                helper.assertTrue(entity.isRemoved(), "termination removes NPC");
                helper.assertTrue(level.getBlockState(blockPos).is(Blocks.GOLD_BLOCK), "termination restores map cell");
                helper.assertTrue(map.currentPhase() == ZombiesGamePhase.WAITING && !service.hasLease(map.roomId()), "termination releases room");
            }
        } finally {
            map.npcRuntime().reset();
            FPSMCore.getInstance().unregisterMap(map);
        }
        helper.succeed();
    }
}
