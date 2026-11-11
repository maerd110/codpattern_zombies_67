package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.cdp.codpattern.app.zombies.npc.ZombiesNpcGraph.*;

@GameTestHolder("codpattern_npc")
@PrefixGameTestTemplate(false)
public final class ZombiesNpcGraphGameTests {
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void allFalseConditionsRouteDuringOpening(GameTestHelper h){
        for(int wave:List.of(0,2))for(boolean open:List.of(false,true)){var f=new Fixture(h);try{
            f.map.phase=wave==0?ZombiesGamePhase.OPENING_COUNTDOWN:ZombiesGamePhase.WAVE_ACTIVE;f.map.wave=wave;f.map.barrierOpen=open;
            var pos=f.origin.offset(6,0,6);var barrier=new com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData("door","door",1,0,true,h.getLevel().dimension(),pos,pos,pos).withEntryId(1);
            f.map.applyObjects(new com.cdp.codpattern.app.zombies.map.ZombiesMapObjects(List.of(),List.of(),List.of(barrier),List.of(),List.of(),List.of(),Optional.empty(),List.of(),List.of(),List.of(),List.of()));
            var c=new Condition(Match.ALL,List.of(new Test(TestKind.WAVE,2,"",1,"",false),new Test(TestKind.BARRIER,1,"",1,"",false)));
            f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"check"),condition("check",Type.BRANCH,c,"yes","no"),action("yes",say("yes"),"end"),action("no",say("no"),"end"),node("end",Type.END,""))));
            f.interact();h.assertTrue(f.map.lines.equals(List.of(wave>=2&&open?"[guide] yes":"[guide] no")),"all-false and partly-false AND conditions execute false branch from opening onward");
        }finally{f.close();}}h.succeed();
    }
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void missingSingleItemRoutesDuringOpening(GameTestHelper h){var f=new Fixture(h);try{
        f.map.phase=ZombiesGamePhase.OPENING_COUNTDOWN;f.map.wave=0;
        var c=new Condition(Match.ALL,List.of(new Test(TestKind.ITEM_ACTOR,0,"minecraft:emerald",1,"",false)));
        f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"check"),condition("check",Type.BRANCH,c,"yes","no"),action("yes",say("has"),"end"),action("no",say("missing"),"end"),node("end",Type.END,""))));
        f.interact();h.assertTrue(f.map.lines.equals(List.of("[guide] missing")),"empty inventory takes false branch before wave one");
    }finally{f.close();}h.succeed();}
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void returnToEntryRearmsInteractionAndReportsTransfers(GameTestHelper h){var f=new Fixture(h);
        f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"give"),action("give",item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:emerald"),"take"),action("take",item(ZombiesNpcConfig.Kind.TAKE_ITEM,"minecraft:emerald"),"say"),action("say",say("once"),"entry"))));
        f.interact();h.assertTrue(f.map.lines.size()==1,"return-to-entry does not spin in same interaction");
        h.runAtTickTime(20,()->{try{f.interact();h.assertTrue(f.map.lines.size()==2&&f.actor.getInventory().countItem(Items.EMERALD)==0,"next click reruns flow in order");
            h.assertTrue(f.map.notices.stream().filter(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().startsWith("npc.item.")).count()==4,"each successful give/take sends exactly one item-name notification");
        }finally{f.close();}h.succeed();});
    }
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void automaticReturnToEntryYieldsBetweenIterations(GameTestHelper h){var f=new Fixture(h);
        f.start(ZombiesNpcConfig.Trigger.AUTO,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"say"),action("say",say("tick"),"entry"))));f.map.npcRuntime().tick(1);
        h.assertTrue(f.map.lines.size()==1,"automatic loop yields instead of exhausting graph budget");
        h.runAtTickTime(30,()->{try{f.map.npcRuntime().tick(1);h.assertTrue(f.map.lines.size()>=2&&f.map.lines.size()<=3,"automatic loops are paced at least one second apart");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void looseChainsAdoptTheConnectedFlow(GameTestHelper h){
        var d=new ZombiesNpcEditorDraft(ZombiesNpcConfig.empty());var npc=d.addNpc(new ZombiesNpcConfig.Position(1,64,1,0));d.addRule(npc);d.addRule(npc);ZombiesNpcSharedCanvas.ensure(d);
        var pool=ZombiesNpcSharedCanvas.looseRule(d);var a=ZombiesNpcGraphDraft.add(pool,Type.WAIT,900,300,null);var b=ZombiesNpcGraphDraft.add(pool,Type.WAIT,1100,300,null);
        ZombiesNpcGraphDraft.connect(pool,a.get("id").getAsString(),"next",b.get("id").getAsString());
        h.assertTrue(d.validated().looseNodes().size()==2,"unassigned nodes can be saved without executing or acquiring a rule");
        var flow=ZombiesNpcSharedCanvas.find(d,"npc_1.rule_2");var source=ZombiesNpcGraphDraft.find(flow.rule(),"node_2");
        ZombiesNpcSharedCanvas.connectExecution(d,new ZombiesNpcSharedCanvas.Ref(flow,source),"next",new ZombiesNpcSharedCanvas.Ref(null,a));
        h.assertTrue(d.looseNodes().isEmpty()&&flow.nodes().size()==5&&ZombiesNpcSharedCanvas.find(d,"npc_1.rule_1").nodes().size()==3,"whole loose chain inherits the connected rule rather than the selected/default rule");
        ZombiesNpcSharedCanvas.connectExecution(d,new ZombiesNpcSharedCanvas.Ref(flow,b),"next",new ZombiesNpcSharedCanvas.Ref(flow,ZombiesNpcGraphDraft.find(flow.rule(),"node_3")));
        h.assertTrue(a.get("x").getAsDouble()==900&&d.validated().npcs().get(0).rules().get(1).graph().nodes().size()==5,"adoption preserves layout and remaps colliding node IDs");h.succeed();
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void ruleCompletionIsCheckedAfterEarlierActions(GameTestHelper h){
        var draft=new ZombiesNpcEditorDraft(ZombiesNpcConfig.empty());var fake=ZombiesNpcSharedCanvas.looseRule(draft);
        var check=ZombiesNpcGraphDraft.add(fake,Type.BRANCH,0,0,null);var checks=check.getAsJsonObject("condition").getAsJsonArray("tests");
        checks.get(0).getAsJsonObject().addProperty("kind","RULE_DONE");
        ZombiesNpcSharedCanvas.connectCondition("guide.first",check);
        h.assertTrue(checks.size()==1&&checks.get(0).getAsJsonObject().get("reference").getAsString().equals("guide.first"),"wiring fills an unfinished rule-completed condition instead of leaving an invalid blank target");
        for(boolean done:List.of(false,true)){var f=new Fixture(h);try{
            var first=new ZombiesNpcConfig.Rule("first",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of(),List.of(),0,"",1,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"end"),node("end",Type.END,""))));
            var c=new Condition(Match.ALL,List.of(new Test(TestKind.RULE_DONE,0,"",1,"guide.first",false)));
            var second=new ZombiesNpcConfig.Rule("second",ZombiesNpcConfig.Trigger.AUTO,0,List.of(),false,List.of(),List.of(),0,"",1,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"intro"),action("intro",say("intro"),"check"),condition("check",Type.BRANCH,c,"yes","no"),action("yes",say("done"),"end"),action("no",say("pending"),"end"),node("end",Type.END,""))));
            f.start(List.of(first,second),0);if(done)f.interact();f.map.npcRuntime().tick(1);
            h.assertTrue(f.map.lines.equals(List.of("[guide] intro",done?"[guide] done":"[guide] pending")),"mid-flow completion check does not gate or skip earlier dialogue");
        }finally{f.close();}}h.succeed();
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void retryBranchNeverFallsBackToLegacyDialogue(GameTestHelper h) {
        var f=new Fixture(h);
        var c=new Condition(Match.ALL,List.of(new Test(TestKind.WAVE,14,"",1,"",false)));
        var graph=new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"check"),condition("check",Type.BRANCH,c,"yes","no"),
                action("yes",say("success"),"done"),node("done",Type.END,""),action("no",say("try again"),"retry"),
                new Node("retry",Type.END,0,0,null,null,0,"","",false)));
        var r=new ZombiesNpcConfig.Rule("flow",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of(),List.of(),0,"",1,graph);
        var npc=new ZombiesNpcConfig.Npc("guide",new ZombiesNpcConfig.Appearance("guide",null,ZombiesNpcConfig.Pose.STANDING,true,position(f.origin)),"legacy fallback",List.of(r),0);
        f.map.npcRuntime().start(new ZombiesNpcConfig(1,List.of(npc)),false);f.interact();
        h.assertTrue(f.map.lines.equals(List.of("[guide] try again")),"false branch uses its own dialogue");
        h.runAtTickTime(20,()->{f.map.wave=14;f.interact();h.assertTrue(f.map.lines.equals(List.of("[guide] try again","[guide] success")),"retry end permits later re-evaluation");});
        h.runAtTickTime(40,()->{try{f.interact();h.assertTrue(f.map.lines.size()==2,"completed graph never falls back to hidden basic dialogue");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void actorMissingItemChecksOnlyInteractorAndCombinedSlots(GameTestHelper h) {
        for(int count=0;count<=2;count++){
            var f=new Fixture(h);try{
                var other=f.player();other.getInventory().setItem(15,new ItemStack(Items.EMERALD,64));
                if(count>0)f.actor.getInventory().setItem(15,new ItemStack(Items.EMERALD));
                if(count>1)f.actor.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Items.EMERALD));
                var c=new Condition(Match.ALL,List.of(new Test(TestKind.ITEM_ACTOR,0,"minecraft:emerald",2,"",true)));
                f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"check"),condition("check",Type.BRANCH,c,"yes","no"),action("yes",say("missing"),"end"),action("no",say("held"),"end"),node("end",Type.END,""))));
                f.interact();h.assertTrue(f.map.lines.equals(List.of(count<2?"[guide] missing":"[guide] held")),"missing means fewer than required count, other player's inventory is ignored");
                h.assertTrue(f.actor.getInventory().countItem(Items.EMERALD)==count,"condition does not consume items");
            }finally{f.close();}
        }h.succeed();
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void legacyDialogueImportsAsVisibleRetryGraph(GameTestHelper h) {
        var draft=new ZombiesNpcEditorDraft(ZombiesNpcConfig.empty());var npc=draft.addNpc(new ZombiesNpcConfig.Position(1,64,1,0));npc.addProperty("dialogue","old one\nold two");draft.importDialogue(npc);
        var created=draft.validated().npcs().get(0);h.assertTrue(created.dialogue().isEmpty()&&created.rules().get(0).graph().actions().get(0).text().equals("old one\nold two"),"import preserves legacy text in a visible node");
        h.assertTrue(created.rules().get(0).graph().nodes().stream().anyMatch(n->n.type()==Type.END&&!n.complete()),"imported default dialogue can repeat on interaction");
        var bad=created.rules().get(0);rejects(()->new ZombiesNpcConfig.Rule("bad",ZombiesNpcConfig.Trigger.AUTO,0,List.of(),false,List.of(),List.of(),0,"",1,bad.graph()));h.succeed();
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void sharedCanvasLayoutDependenciesAndSnapshots(GameTestHelper h) {
        var draft=new ZombiesNpcEditorDraft(ZombiesNpcConfig.empty());
        var a=draft.addNpc(new ZombiesNpcConfig.Position(1,64,1,0));var a1=draft.addRule(a);var a2=draft.addRule(a);
        var b=draft.addNpc(new ZombiesNpcConfig.Position(2,64,1,0));var b1=draft.addRule(b);
        ZombiesNpcSharedCanvas.ensure(draft);String initial=draft.snapshot();ZombiesNpcSharedCanvas.ensure(draft);
        h.assertTrue(initial.equals(draft.snapshot()),"shared layout migration is idempotent");
        var flows=ZombiesNpcSharedCanvas.flows(draft);
        h.assertTrue(flows.size()==3 && flows.stream().allMatch(f->f.rule().getAsJsonObject("graph").get("canvasVersion").getAsInt()==1),"all NPCs and rules share the saved layout");
        double bottom=Double.NEGATIVE_INFINITY;
        for(var flow:flows){double top=flow.nodes().stream().mapToDouble(n->n.get("y").getAsDouble()).min().orElseThrow();h.assertTrue(top>bottom,"old independent layouts become non-overlapping groups");bottom=flow.nodes().stream().mapToDouble(n->n.get("y").getAsDouble()+88).max().orElseThrow();}
        ZombiesNpcSharedCanvas.connect(draft,"npc_1.rule_1","npc_1.rule_2");
        ZombiesNpcSharedCanvas.connect(draft,"npc_1.rule_2","npc_2.rule_1");
        ZombiesNpcSharedCanvas.connect(draft,"npc_1.rule_2","npc_2.rule_1");
        h.assertTrue(b1.getAsJsonArray("requires").size()==1,"duplicate wires do not duplicate prerequisites");
        rejects(()->ZombiesNpcSharedCanvas.connect(draft,"npc_2.rule_1","npc_1.rule_1"));
        String saved=draft.snapshot();draft.renameNpc(a,"doctor");draft.renameRule(a,a2,"followup");
        h.assertTrue(b1.getAsJsonArray("requires").get(0).getAsString().equals("doctor.followup"),"renaming updates cross-NPC wires");
        draft.restoreWorking(saved);h.assertTrue(draft.snapshot().equals(saved),"global undo restores every NPC and rule together");
        ZombiesNpcSharedCanvas.delete(draft,"npc_1.rule_2");
        h.assertTrue(draft.npcs().get(1).getAsJsonObject().getAsJsonArray("rules").get(0).getAsJsonObject().getAsJsonArray("requires").isEmpty(),"deleting a rule removes its outgoing dependency wires");
        var config=draft.validated();h.assertTrue(config.equals(ZombiesNpcRepository.GSON.fromJson(ZombiesNpcRepository.GSON.toJson(config),ZombiesNpcConfig.class)),"shared positions and dependency wires round trip");
        h.succeed();
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void crossNpcCompletionUnlocksAutomaticAndInteractionRules(GameTestHelper h) {
        var f=new Fixture(h);
        var sourceGraph=new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"take"),action("take",item(ZombiesNpcConfig.Kind.TAKE_ITEM,"minecraft:emerald"),"say"),
                action("say",new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.DIALOGUE,"guard",null,"delivered",null,0,null,null,0),"end"),node("end",Type.END,"")));
        var autoGraph=new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"say"),action("say",say("ready"),"end"),node("end",Type.END,"")));
        var interactionGraph=new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"say"),action("say",say("next interaction"),"end"),node("end",Type.END,"")));
        var source=new ZombiesNpcConfig.Rule("delivery",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of(),List.of(),0,"",1,sourceGraph);
        var automatic=new ZombiesNpcConfig.Rule("ready",ZombiesNpcConfig.Trigger.AUTO,0,List.of(),false,List.of("guide.delivery"),List.of(),0,"",1,autoGraph);
        var interaction=new ZombiesNpcConfig.Rule("followup",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of("guard.ready"),List.of(),0,"",1,interactionGraph);
        var guide=new ZombiesNpcConfig.Npc("guide",new ZombiesNpcConfig.Appearance("guide",null,ZombiesNpcConfig.Pose.STANDING,true,position(f.origin)),"",List.of(source),0);
        var guard=new ZombiesNpcConfig.Npc("guard",new ZombiesNpcConfig.Appearance("guard",null,ZombiesNpcConfig.Pose.STANDING,true,position(f.origin.offset(4,0,0))),"",List.of(automatic,interaction),0);
        var config=new ZombiesNpcConfig(1,List.of(guide,guard));f.actor.getInventory().setItem(12,new ItemStack(Items.EMERALD));
        f.map.npcRuntime().start(config,false);f.map.npcRuntime().tick(1);
        h.assertTrue(f.map.lines.isEmpty(),"target cannot start before source completes");
        f.interact();f.map.npcRuntime().tick(1);f.map.npcRuntime().tick(1);
        h.assertTrue(f.map.lines.equals(List.of("[guard] delivered","[guard] ready")),"cross-NPC target action and automatic dependency execute in order, interaction rule waits");
        h.runAtTickTime(20,()->{try{
            var entity=h.getLevel().getEntitiesOfClass(ZombiesNpcEntity.class,new AABB(f.origin.offset(4,0,0)).inflate(1)).stream().findFirst().orElseThrow();
            f.map.npcRuntime().interact(f.actor,"guard",entity);
            h.assertTrue(f.map.lines.size()==3 && f.map.lines.get(2).equals("[guard] next interaction"),"linked interaction rule still requires interaction with its own NPC");
            f.map.npcRuntime().start(config,false);f.map.npcRuntime().tick(1);
            h.assertTrue(f.map.lines.size()==3,"a new round clears completion dependencies");
        }finally{f.close();}h.succeed();});
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void graphDialogueTimingAndSeparateInteractions(GameTestHelper h) {
        var f = new Fixture(h); var other = f.player();
        var firstGraph = new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"talk"),action("talk",say("one\ntwo\nthree"),"give"),
                action("give",item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:diamond"),"end"),node("end",Type.END,"")));
        var nextGraph = new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"talk"),action("talk",say("next rule"),"end"),node("end",Type.END,"")));
        var first = new ZombiesNpcConfig.Rule("first",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of(),List.of(),.15,"",1,firstGraph);
        var next = new ZombiesNpcConfig.Rule("next",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of("guide.first"),List.of(),0,"",1,nextGraph);
        f.start(List.of(first,next),.1); f.interact();
        for(int tick=1;tick<=40;tick++){int current=tick;h.runAtTickTime(tick,()->{
            f.map.npcRuntime().tick(1);
            if(current==2)f.interact(other);
            if(current==40){try{
                h.assertTrue(f.map.lines.equals(List.of("[guide] one","[guide] two","[guide] three")),"one click runs dialogue graph, repeated click does not launch another rule");
                h.assertTrue(f.map.times.get(1)-f.map.times.get(0)>=2 && f.map.times.get(2)-f.map.times.get(1)>=2,"graph dialogue waits between lines");
                h.assertTrue(f.actor.getInventory().getItem(9).is(Items.DIAMOND) && other.getInventory().countItem(Items.DIAMOND)==0,"delayed graph reward stays with first actor");
                f.interact(other); h.assertTrue(f.map.lines.size()==4 && f.map.lines.get(3).equals("[guide] next rule"),"another graph rule requires its own interaction");
            }finally{f.close();}h.succeed();}
        });}
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void roomItemsCompletedRulesAndNpcLifeCombine(GameTestHelper h) {
        var f=new Fixture(h);
        try{
            f.actor.getInventory().setItem(15,new ItemStack(Items.DIAMOND));
            f.actor.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Items.DIAMOND));
            var firstGraph=new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"talk"),action("talk",say("first"),"end"),node("end",Type.END,"")));
            var c=new Condition(Match.ALL,List.of(new Test(TestKind.ITEM_ROOM,0,"minecraft:diamond",2,"",false),new Test(TestKind.RULE_DONE,0,"",1,"guide.first",false),
                    new Test(TestKind.NPC_ALIVE,0,"",1,"guide",false),new Test(TestKind.WAVE,14,"",1,"",true)));
            var secondGraph=new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"branch"),condition("branch",Type.BRANCH,c,"yes","no"),action("yes",say("ready"),"end"),action("no",say("wrong"),"end"),node("end",Type.END,"")));
            var first=new ZombiesNpcConfig.Rule("first",ZombiesNpcConfig.Trigger.AUTO,0,List.of(),false,List.of(),List.of(),0,"",1,firstGraph);
            var second=new ZombiesNpcConfig.Rule("second",ZombiesNpcConfig.Trigger.AUTO,0,List.of(),false,List.of(),List.of(),0,"",1,secondGraph);
            f.start(List.of(first,second),0);f.map.npcRuntime().tick(1);f.map.npcRuntime().tick(1);f.map.npcRuntime().tick(1);
            h.assertTrue(f.map.lines.equals(List.of("[guide] first","[guide] ready")),"combined graph checks room inventory, completed rule, NPC life and inverted wave without repeating completed graphs");
        }finally{f.close();}h.succeed();
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=120)
    public static void branchChoosesOnePathAndKeepsInteractionOwner(GameTestHelper h) {
        for (boolean hasItem : List.of(false, true)) {
            var f = new Fixture(h);
            try {
                if (hasItem) f.actor.getInventory().setItem(12, new ItemStack(Items.EMERALD));
                // Another room player having an item must not satisfy ITEM_ACTOR.
                var other = f.player(); other.getInventory().setItem(12, new ItemStack(Items.EMERALD));
                var c = new Condition(Match.ALL, List.of(new Test(TestKind.ITEM_ACTOR, 0, "minecraft:emerald", 1, "", false)));
                f.start(ZombiesNpcConfig.Trigger.INTERACT, new ZombiesNpcGraph(List.of(
                        node("entry", Type.ENTRY, "branch"), condition("branch", Type.BRANCH, c, "take", "no"),
                        action("take", item(ZombiesNpcConfig.Kind.TAKE_ITEM, "minecraft:emerald"), "give"),
                        action("give", item(ZombiesNpcConfig.Kind.GIVE_ITEM, "minecraft:diamond"), "yes"),
                        action("yes", say("yes"), "end"), action("no", say("no"), "end"), node("end", Type.END, ""))));
                f.interact();
                h.assertTrue(f.map.lines.equals(List.of(hasItem ? "[guide] yes" : "[guide] no")), "only selected branch executes");
                h.assertTrue(f.actor.getInventory().countItem(Items.DIAMOND) == (hasItem ? 1 : 0), "reward belongs to triggering player");
                h.assertTrue(other.getInventory().countItem(Items.EMERALD) == 1 && f.actor.getInventory().countItem(Items.EMERALD) == 0, "branch consumes only owner's item");
            } finally { f.close(); }
        }
        h.succeed();
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=120)
    public static void awaitAndDelayModifyMapThenReset(GameTestHelper h) {
        var f = new Fixture(h); BlockPos block = f.origin.offset(2, 0, 0);
        h.getLevel().setBlock(block, Blocks.AIR.defaultBlockState(), 18);
        var place = new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.SET_BLOCK, null, null, null, null, 0, position(block), "minecraft:stone", 0);
        var c = new Condition(Match.ALL, List.of(new Test(TestKind.WAVE, 14, "", 1, "", false)));
        f.start(ZombiesNpcConfig.Trigger.AUTO, new ZombiesNpcGraph(List.of(node("entry", Type.ENTRY, "intro"), action("intro", say("waiting"), "await"),
                condition("await", Type.AWAIT, c, "delay", ""), new Node("delay", Type.WAIT, 0, 0, null, null, .1, "place", ""), action("place", place, "end"), node("end", Type.END, ""))));
        f.map.npcRuntime().tick(1);
        h.assertTrue(h.getLevel().getBlockState(block).isAir() && f.map.lines.size() == 1, "await pauses after initial dialogue");
        for (int tick=1; tick<=32; tick++) { int current=tick; h.runAtTickTime(tick, () -> {
            if (current == 5) f.map.wave=14;
            f.map.npcRuntime().tick(f.map.wave);
            if (current == 6) h.assertTrue(h.getLevel().getBlockState(block).isAir(), "no premature placement while waiting");
            if (current == 32) {
                try {
                    h.assertTrue(h.getLevel().getBlockState(block).is(Blocks.STONE) && f.map.lines.size()==1, "condition resumes through delay without replay");
                    f.map.npcRuntime().reset(); h.assertTrue(h.getLevel().getBlockState(block).isAir(), "graph map effects roll back");
                } finally { f.close(); } h.succeed();
            }
        }); }
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void graphItemPauseResumesWithoutAnotherClick(GameTestHelper h) {
        var f = new Fixture(h);
        f.start(ZombiesNpcConfig.Trigger.INTERACT, new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"hello"), action("hello",say("hello"),"take"),
                action("take",item(ZombiesNpcConfig.Kind.TAKE_ITEM,"minecraft:emerald"),"reward"), action("reward",item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:diamond"),"end"),node("end",Type.END,""))));
        f.interact();
        for(int tick=1;tick<=30;tick++){ int current=tick;h.runAtTickTime(tick,()->{
            if(current==5) f.actor.getInventory().setItem(15,new ItemStack(Items.EMERALD));
            f.map.npcRuntime().tick(1);
            if(current==30){try{h.assertTrue(f.map.lines.size()==1 && f.actor.getInventory().getItem(9).is(Items.DIAMOND) && f.actor.getInventory().countItem(Items.EMERALD)==0,"paused graph resumes once into backpack");}finally{f.close();}h.succeed();}
        });}
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void resetCancelsGraphTimers(GameTestHelper h) {
        var f = new Fixture(h);
        f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"delay"),new Node("delay",Type.WAIT,0,0,null,null,1,"reward",""),
                action("reward",item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:diamond"),"end"),node("end",Type.END,""))));
        f.interact();f.map.npcRuntime().reset();
        h.runAtTickTime(40,()->{try{f.map.npcRuntime().tick(1);h.assertTrue(f.actor.getInventory().countItem(Items.DIAMOND)==0,"round end cancels graph timers");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void migrationValidationAndGraphReferences(GameTestHelper h) {
        var draft = new ZombiesNpcEditorDraft(ZombiesNpcConfig.empty());
        var npc=draft.addNpc(new ZombiesNpcConfig.Position(1,64,1,0));var r=draft.addRule(npc);
        r.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("text","original");
        ZombiesNpcGraphDraft.ensure(r);
        var migrated=draft.validated().npcs().get(0).rules().get(0);
        h.assertTrue(migrated.actions().isEmpty() && migrated.graph().actions().get(0).text().equals("original") && migrated.actionIntervalSeconds()==1,"legacy actions and timing migrate without dual source of truth");
        String before=r.toString();ZombiesNpcGraphDraft.ensure(r);h.assertTrue(r.toString().equals(before),"migration is idempotent");
        var branch=ZombiesNpcGraphDraft.add(r,Type.BRANCH,50,50,null);String bid=branch.get("id").getAsString();
        ZombiesNpcGraphDraft.connect(r,"node_1","next",bid);ZombiesNpcGraphDraft.connect(r,bid,"next","node_2");ZombiesNpcGraphDraft.connect(r,bid,"onFalse","node_3");
        var test=branch.getAsJsonObject("condition").getAsJsonArray("tests").get(0).getAsJsonObject();
        test.addProperty("kind","NPC_ALIVE");test.addProperty("reference","npc_1");
        draft.renameNpc(npc,"guide");h.assertTrue(test.get("reference").getAsString().equals("guide"),"graph NPC condition rename follows target");
        var second=draft.addRule(npc);ZombiesNpcGraphDraft.ensure(second);
        test.addProperty("kind","RULE_DONE");test.addProperty("reference","guide.rule_2");draft.renameRule(npc,second,"later");
        h.assertTrue(test.get("reference").getAsString().equals("guide.later"),"graph rule references follow rename");
        var config=draft.validated();var json=ZombiesNpcRepository.GSON.toJson(config);
        h.assertTrue(config.equals(ZombiesNpcRepository.GSON.fromJson(json,ZombiesNpcConfig.class)),"graph round trip preserves geometry, branches and actions");
        rejects(()->ZombiesNpcGraphDraft.connect(r,"node_2","next",bid));
        r.getAsJsonObject("graph").getAsJsonArray("nodes").add(ZombiesNpcRepository.GSON.toJsonTree(node("orphan",Type.END,"")));
        rejects(draft::validated);
        h.succeed();
    }
    @GameTest(template="empty", batch="npc_graph", timeoutTicks=100)
    public static void graphRejectsCyclesAndAutomaticTransfers(GameTestHelper h) {
        rejects(()->new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"a"),node("a",Type.WAIT,"b"),node("b",Type.WAIT,"a"))));
        rejects(()->new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"missing"))));
        var graph=new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"give"),action("give",item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:diamond"),"end"),node("end",Type.END,"")));
        rejects(()->new ZombiesNpcConfig.Rule("bad",ZombiesNpcConfig.Trigger.AUTO,0,List.of(),false,List.of(),List.of(),0,"",1,graph));
        var wave=new Test(TestKind.WAVE,14,"",1,"",false);var notWave=new Test(TestKind.WAVE,14,"",1,"",true);
        h.assertTrue(new Condition(Match.ANY,List.of(wave,notWave)).evaluate(t->true) && !new Condition(Match.ALL,List.of(wave,notWave)).evaluate(t->true),"ALL/ANY and per-test negation");
        h.succeed();
    }
    private static Node node(String id,Type type,String next){return new Node(id,type,0,0,null,null,0,next,"");}
    private static Node action(String id,ZombiesNpcConfig.Action a,String next){return new Node(id,Type.ACTION,0,0,a,null,0,next,"");}
    private static Node condition(String id,Type type,Condition c,String next,String no){return new Node(id,type,0,0,null,c,0,next,no);}
    private static ZombiesNpcConfig.Action say(String text){return new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.DIALOGUE,null,null,text,null,0,null,null,0);}
    private static ZombiesNpcConfig.Action item(ZombiesNpcConfig.Kind kind,String item){return new ZombiesNpcConfig.Action(kind,null,null,null,item,1,null,null,0);}
    private static ZombiesNpcConfig.Position position(BlockPos p){return new ZombiesNpcConfig.Position(p.getX()+.5,p.getY(),p.getZ()+.5,0);}
    private static void rejects(Runnable r){
        try { r.run(); } catch (RuntimeException error) {
            Throwable cause=error; while(cause.getCause()!=null)cause=cause.getCause();
            if(cause instanceof IllegalArgumentException)return; throw error;
        }
        throw new AssertionError("invalid graph accepted");
    }
    private static final class Fixture{
        final TestMap map;final BlockPos origin;final ServerPlayer actor;
        Fixture(GameTestHelper h){origin=h.absolutePos(new BlockPos(1,2,1));map=new TestMap(h.getLevel(),"graph-"+UUID.randomUUID().toString().substring(0,8),new AreaData(origin.offset(-1,-1,-1),origin.offset(8,5,8)));FPSMCore.getInstance().registerMap(map.getGameType(),map);actor=player();}
        ServerPlayer player(){var p=new FakePlayer(map.getServerLevel(),new GameProfile(UUID.randomUUID(),"graph-actor")){@Override public void sendSystemMessage(Component message){map.notices.add(message);}};p.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5);map.actors.add(p);return p;}
        void start(ZombiesNpcConfig.Trigger trigger,ZombiesNpcGraph graph){var r=new ZombiesNpcConfig.Rule("flow",trigger,0,List.of(),false,List.of(),List.of(),0,"",1,graph);start(List.of(r),0);}
        void start(List<ZombiesNpcConfig.Rule> rules,double interval){var npc=new ZombiesNpcConfig.Npc("guide",new ZombiesNpcConfig.Appearance("guide",null,ZombiesNpcConfig.Pose.STANDING,true,position(origin)),"",rules,interval);map.npcRuntime().start(new ZombiesNpcConfig(1,List.of(npc)),false);}
        void interact(){interact(actor);}
        void interact(ServerPlayer player){var npc=map.getServerLevel().getEntitiesOfClass(ZombiesNpcEntity.class,new AABB(origin).inflate(1)).stream().findFirst().orElseThrow();map.npcRuntime().interact(player,"guide",npc);}
        void close(){map.npcRuntime().reset();FPSMCore.getInstance().unregisterMap(map);}
    }
    private static final class TestMap extends ZombiesMap{
        final List<ServerPlayer> actors=new ArrayList<>();final List<String> lines=new ArrayList<>();final List<Long> times=new ArrayList<>();final List<Component> notices=new ArrayList<>();int wave=1;boolean barrierOpen;ZombiesGamePhase phase=ZombiesGamePhase.WAVE_ACTIVE;
        TestMap(net.minecraft.server.level.ServerLevel l,String n,AreaData a){super(l,n,a);}
        @Override public ZombiesGamePhase currentPhase(){return phase;}
        @Override public boolean npcBarrierOpen(int group){return barrierOpen;}
        @Override public int npcWave(){return wave;}
        @Override public boolean isAliveSurvivor(UUID id){return actors.stream().anyMatch(p->p.getUUID().equals(id));}
        @Override public List<ServerPlayer> npcPlayers(){return List.copyOf(actors);}
        @Override public void tick(){npcRuntime().tick(wave);}
        @Override public void sendNpcDialogue(Component c){lines.add(c.getString());times.add(getServerLevel().getGameTime());}
    }
}
