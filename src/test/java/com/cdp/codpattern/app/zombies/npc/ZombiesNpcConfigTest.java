package com.cdp.codpattern.app.zombies.npc;

import com.google.gson.Gson;
import java.util.List;
import java.util.Set;

public final class ZombiesNpcConfigTest {
    public static void main(String[] args) {
        var dialogue = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.DIALOGUE, null, null, "到了第十四波。", null, 0, null, null, 0);
        var either = new ZombiesNpcConfig.Rule("relocate", ZombiesNpcConfig.Trigger.AUTO, 14, List.of(3), true, List.of(), List.of(dialogue));
        require(!either.eligible(13, group -> false, Set.of()), "early trigger");
        require(either.eligible(14, group -> false, Set.of()), "wave 14");
        require(either.eligible(2, group -> group == 3, Set.of()), "barrier alternative");
        var both = new ZombiesNpcConfig.Rule("both", ZombiesNpcConfig.Trigger.AUTO, 14, List.of(3), false, List.of("guide.talk"), List.of(dialogue));
        require(!both.eligible(14, group -> true, Set.of()), "prerequisite required");
        require(!both.eligible(13, group -> true, Set.of("guide.talk")), "wave and barrier");
        require(!both.eligible(14, group -> false, Set.of("guide.talk")), "barrier and wave");
        require(both.eligible(14, group -> true, Set.of("guide.talk")), "all conditions satisfied");
        var initial = new ZombiesNpcConfig.Appearance("向导", "minecraft:textures/entity/player/wide/steve.png", ZombiesNpcConfig.Pose.SITTING, true, new ZombiesNpcConfig.Position(1, 2, 3, 90));
        var config = new ZombiesNpcConfig(1, List.of(new ZombiesNpcConfig.Npc("guide", initial, "你好", List.of(either))));
        Gson gson = new Gson();
        require(config.equals(gson.fromJson(gson.toJson(config), ZombiesNpcConfig.class)), "round trip");
        rejects(() -> new ZombiesNpcConfig(2, List.of()));
        rejects(() -> new ZombiesNpcConfig(1, List.of(config.npcs().get(0), config.npcs().get(0))));
        rejects(() -> new ZombiesNpcConfig.Position(Double.NaN, 0, 0, 0));
        rejects(() -> ZombiesNpcConfig.intervalTicks(Double.NaN));
        rejects(() -> ZombiesNpcConfig.intervalTicks(-1));
        rejects(() -> new ZombiesNpcConfig.Rule("zero_count", ZombiesNpcConfig.Trigger.AUTO, 0, List.of(), false, List.of(), List.of(dialogue), 0, "minecraft:emerald", 0));
        rejects(() -> new ZombiesNpcConfig(1, List.of(new ZombiesNpcConfig.Npc("guide", initial, "", List.of(both)))));
        var circular = new ZombiesNpcConfig.Rule("cycle", ZombiesNpcConfig.Trigger.AUTO, 0, List.of(), false, List.of("guide.cycle"), List.of(dialogue));
        rejects(() -> new ZombiesNpcConfig(1, List.of(new ZombiesNpcConfig.Npc("guide", initial, "", List.of(circular)))));
        rejects(() -> new ZombiesNpcConfig.Rule("invalid", ZombiesNpcConfig.Trigger.AUTO, 0, List.of(), false, List.of(),
                List.of(new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.GIVE_ITEM, null, null, null, "minecraft:diamond", 1, null, null, 0))));
        System.out.println("PASS NPC conditions, prerequisites, JSON round trip, invalid data and transfer trigger validation");
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid configuration to be rejected");
    }
}
