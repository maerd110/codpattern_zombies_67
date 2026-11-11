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
    public static void debugDiagnosticsDefaultOffAndAdminOnly(GameTestHelper h){var f=new Fixture(h);try{
        var c=new Condition(Match.ALL,List.of(new Test(TestKind.WAVE,3,"",1,"",false)));
        var r=new ZombiesNpcConfig.Rule("wait",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of(),List.of(),0,"",1,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"wait"),condition("wait",Type.AWAIT,c,"end",""),node("end",Type.END,""))));
        var npc=new ZombiesNpcConfig.Npc("guide",new ZombiesNpcConfig.Appearance("guide",null,ZombiesNpcConfig.Pose.STANDING,true,position(f.origin)),"",List.of(r),0);
        f.map.npcRuntime().start(new ZombiesNpcConfig(1,List.of(npc)),false);f.interact();h.assertTrue(f.map.notices.isEmpty(),"normal play has no waiting diagnostics by default");
        f.map.npcRuntime().start(new ZombiesNpcConfig(1,List.of(npc),List.of(),true),false);f.interact();h.assertTrue(f.map.notices.stream().anyMatch(m->m.getString().contains("3")),"enabled debug mode explains the wait to an administrator");
        f.map.notices.clear();f.map.admin=false;f.map.npcRuntime().start(new ZombiesNpcConfig(1,List.of(npc),List.of(),true),false);f.interact();h.assertTrue(f.map.notices.isEmpty(),"debug does not leak to non-admin players");
    }finally{f.close();}h.succeed();}
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void mismatchedItemGateExplainsQueuedCrossNpcResume(GameTestHelper h){var f=new Fixture(h);
        String key="minecraft:tripwire_hook{display:{Name:'{\"text\":\"品C229钥匙\"}'}}";
        var c=new Condition(Match.ALL,List.of(new Test(TestKind.ITEM_ACTOR,0,"minecraft:emerald",1,"",false)));
        var firstGraph=new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"check"),condition("check",Type.BRANCH,c,"take","no"),action("take",item(ZombiesNpcConfig.Kind.TAKE_ITEM,key),"say"),action("say",say("queued followup"),"end"),action("no",say("missing"),"entry"),node("end",Type.END,"")));
        var wait=new Condition(Match.ALL,List.of(new Test(TestKind.WAVE,2,"",1,"",false)));
        var secondGraph=new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"intro"),action("intro",say("intro"),"wait"),condition("wait",Type.AWAIT,wait,"give",""),action("give",item(ZombiesNpcConfig.Kind.GIVE_ITEM,key),"end"),node("end",Type.END,"")));
        var a=new ZombiesNpcConfig.Rule("first",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of(),List.of(),0,"",1,firstGraph);
        var b=new ZombiesNpcConfig.Rule("second",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of(),List.of(),0,"",1,secondGraph);
        var n1=new ZombiesNpcConfig.Npc("guide",new ZombiesNpcConfig.Appearance("guide",null,ZombiesNpcConfig.Pose.STANDING,true,position(f.origin)),"",List.of(a),0);
        var n2=new ZombiesNpcConfig.Npc("guard",new ZombiesNpcConfig.Appearance("guard",null,ZombiesNpcConfig.Pose.STANDING,true,position(f.origin.offset(4,0,0))),"",List.of(b),0);
        f.actor.getInventory().setItem(15,new ItemStack(Items.EMERALD));f.map.npcRuntime().start(new ZombiesNpcConfig(1,List.of(n1,n2)),false);f.interact();
        h.assertTrue(f.map.lines.isEmpty(),"emerald gate incorrectly permits entering a missing-key transfer and pauses there");
        for(int tick=1;tick<=60;tick++){int t=tick;h.runAtTickTime(tick,()->{
            if(t==10){var npc=h.getLevel().getEntitiesOfClass(ZombiesNpcEntity.class,new AABB(f.origin.offset(4,0,0)).inflate(1)).stream().filter(n->n.getName().getString().equals("guard")).findFirst().orElseThrow();f.map.npcRuntime().interact(f.actor,"guard",npc);}
            if(t==15)f.map.wave=2;f.map.npcRuntime().tick(f.map.wave);
            if(t==60){try{h.assertTrue(f.map.lines.equals(List.of("[guard] intro","[guide] queued followup")),"reward unblocks the already-started first sequence without another click; no new rule is implicitly started");h.assertTrue(f.actor.getInventory().countItem(Items.TRIPWIRE_HOOK)==0,"queued transfer consumes the key as configured");}finally{f.close();}h.succeed();}
        });}
    }
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void matchingKeyGateDoesNotQueueAnUnstartedTrade(GameTestHelper h){var f=new Fixture(h);try{
        var c=new Condition(Match.ALL,List.of(new Test(TestKind.ITEM_ACTOR,0,"minecraft:tripwire_hook",1,"",false)));
        f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"check"),condition("check",Type.BRANCH,c,"take","no"),action("take",item(ZombiesNpcConfig.Kind.TAKE_ITEM,"minecraft:tripwire_hook"),"end"),action("no",say("missing key"),"entry"),node("end",Type.END,""))));
        f.interact();f.actor.getInventory().setItem(15,new ItemStack(Items.TRIPWIRE_HOOK));f.map.npcRuntime().tick(1);
        h.assertTrue(f.actor.getInventory().countItem(Items.TRIPWIRE_HOOK)==1&&f.map.lines.equals(List.of("[guide] missing key")),"a correct failed gate returns to entry and receiving an item alone cannot restart the rule");
    }finally{f.close();}h.succeed();}
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
            var entity=h.getLevel().getEntitiesOfClass(ZombiesNpcEntity.class,new AABB(f.origin.offset(4,0,0)).inflate(1)).stream().filter(n->n.getName().getString().equals("guard")).findFirst().orElseThrow();
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
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void newInteractionGateDoesNotConsumeOpeningClick(GameTestHelper h){var f=new Fixture(h);
        f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"intro"),action("intro",say("hello"),"click"),node("click",Type.INTERACT,"reward"),action("reward",item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:diamond"),"end"),node("end",Type.END,""))));
        f.interact();h.assertTrue(f.map.lines.equals(List.of("[guide] hello"))&&f.actor.getInventory().countItem(Items.DIAMOND)==0,"opening click arms the gate but cannot satisfy it");
        var stranger=f.player();
        h.runAtTickTime(15,()->{f.interact(stranger);f.map.npcRuntime().tick(1);h.assertTrue(f.actor.getInventory().countItem(Items.DIAMOND)==0,"another player cannot advance the owner gate");});
        h.runAtTickTime(30,()->{try{f.interact();h.assertTrue(f.actor.getInventory().countItem(Items.DIAMOND)==1&&stranger.getInventory().countItem(Items.DIAMOND)==0,"new owner interaction advances once");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void crossNpcCompletionStillRequiresNewInteraction(GameTestHelper h){var f=new Fixture(h);
        var done=new Condition(Match.ALL,List.of(new Test(TestKind.RULE_DONE,0,"",1,"guard.quest",false)));
        var g=new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"intro"),action("intro",say("hello"),"wait"),condition("wait",Type.AWAIT,done,"click",""),node("click",Type.INTERACT,"reward"),action("reward",item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:diamond"),"end"),node("end",Type.END,"")));
        var quest=new ZombiesNpcConfig.Rule("quest",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of(),List.of(item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:emerald")),0,"",1);
        var guide=new ZombiesNpcConfig.Npc("guide",new ZombiesNpcConfig.Appearance("guide",null,ZombiesNpcConfig.Pose.STANDING,true,position(f.origin)),"",List.of(new ZombiesNpcConfig.Rule("story",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of(),List.of(),0,"",1,g)),0);
        var guard=new ZombiesNpcConfig.Npc("guard",new ZombiesNpcConfig.Appearance("guard",null,ZombiesNpcConfig.Pose.STANDING,true,position(f.origin.offset(4,0,0))),"",List.of(quest),0);
        f.map.npcRuntime().start(new ZombiesNpcConfig(1,List.of(guide,guard)),false);f.interact();
        h.assertTrue(f.map.lines.equals(List.of("[guide] hello")),"early conversation runs before the other quest completes");
        h.runAtTickTime(15,()->{var entity=h.getLevel().getEntitiesOfClass(ZombiesNpcEntity.class,new AABB(f.origin.offset(4,0,0)).inflate(1)).stream().filter(n->n.getName().getString().equals("guard")).findFirst().orElseThrow();f.map.npcRuntime().interact(f.actor,"guard",entity);});
        h.runAtTickTime(40,()->{f.map.npcRuntime().tick(1);h.assertTrue(f.actor.getInventory().countItem(Items.EMERALD)==1&&f.actor.getInventory().countItem(Items.DIAMOND)==0,"other NPC reward and completion do not advance fresh interaction gate");});
        h.runAtTickTime(55,()->{try{f.interact();h.assertTrue(f.actor.getInventory().countItem(Items.DIAMOND)==1,"right clicking original NPC after completion continues the story");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void dialogueVisibilityOwnershipAndReplay(GameTestHelper h){var f=new Fixture(h);try{
        var owns=new Condition(Match.ALL,List.of(new Test(TestKind.ITEM_ACTOR,0,"minecraft:emerald",1,"",false)));
        var missing=new Condition(Match.ALL,List.of(new Test(TestKind.ITEM_ACTOR,0,"minecraft:emerald",1,"",true)));
        f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"choose"),choice(List.of(new Option("trade","Trade",owns,"give"),new Option("ask","Ask",missing,"say"))),action("give",item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:diamond"),"end"),action("say",say("come back"),"end"),node("end",Type.END,""))));
        f.interact();var runtime=f.map.npcRuntime();var view=runtime.dialogue(f.actor);var other=f.player();
        h.assertTrue(view.options().size()==1&&view.options().get(0).id().equals("ask")&&!view.text().getString().isEmpty(),"only eligible answers and configured speech are sent");
        h.assertTrue(!ZombiesNpcRuntime.selectDialogue(other,view.token(),"ask"),"another player cannot answer this conversation");
        h.assertTrue(!ZombiesNpcRuntime.selectDialogue(f.actor,UUID.randomUUID(),"ask"),"forged tokens are rejected");
        h.assertTrue(!ZombiesNpcRuntime.selectDialogue(f.actor,view.token(),"trade"),"hidden answers cannot be selected");
        view=runtime.dialogue(f.actor);
        h.assertTrue(ZombiesNpcRuntime.selectDialogue(f.actor,view.token(),"ask")&&f.map.lines.equals(List.of("[guide] come back")),"selected branch runs exactly once");
        h.assertTrue(!ZombiesNpcRuntime.selectDialogue(f.actor,view.token(),"ask")&&f.map.lines.size()==1&&f.actor.getInventory().countItem(Items.DIAMOND)==0,"duplicate packets cannot duplicate consequences");
    }finally{f.close();}h.succeed();}
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void dialogueRechecksConditionsAndCanReopen(GameTestHelper h){var f=new Fixture(h);
        var owns=new Condition(Match.ALL,List.of(new Test(TestKind.ITEM_ACTOR,0,"minecraft:emerald",1,"",false)));
        f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"choose"),choice(List.of(new Option("trade","Trade",owns,"take"))),action("take",item(ZombiesNpcConfig.Kind.TAKE_ITEM,"minecraft:emerald"),"give"),action("give",item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:diamond"),"end"),node("end",Type.END,""))));
        f.actor.getInventory().setItem(15,new ItemStack(Items.EMERALD));f.interact();var runtime=f.map.npcRuntime();var first=runtime.dialogue(f.actor);
        f.actor.getInventory().clearContent();h.assertTrue(!ZombiesNpcRuntime.selectDialogue(f.actor,first.token(),"trade")&&runtime.dialogue(f.actor).options().isEmpty(),"a displayed answer that becomes invalid is rechecked and hidden");
        var refreshed=runtime.dialogue(f.actor);ZombiesNpcRuntime.selectDialogue(f.actor,refreshed.token(),"");
        f.actor.getInventory().setItem(15,new ItemStack(Items.EMERALD));runtime.tick(1);h.assertTrue(runtime.dialogue(f.actor)==null&&f.actor.getInventory().countItem(Items.DIAMOND)==0,"closing does not choose or automatically reopen");
        h.runAtTickTime(20,()->{try{f.interact();var reopened=runtime.dialogue(f.actor);h.assertTrue(reopened!=null&&!first.token().equals(reopened.token()),"new click reopens with new session");h.assertTrue(ZombiesNpcRuntime.selectDialogue(f.actor,reopened.token(),"trade")&&f.actor.getInventory().countItem(Items.DIAMOND)==1&&f.actor.getInventory().countItem(Items.EMERALD)==0,"trade follows answer output into inventory actions");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void dialogueDistanceAndResetInvalidateAnswers(GameTestHelper h){var f=new Fixture(h);try{
        var g=new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"choose"),choice(List.of(new Option("yes","Yes",null,"give"))),action("give",item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:diamond"),"end"),node("end",Type.END,"")));
        f.start(ZombiesNpcConfig.Trigger.INTERACT,g);f.interact();var view=f.map.npcRuntime().dialogue(f.actor);
        f.actor.moveTo(f.origin.getX()+30,f.origin.getY(),f.origin.getZ());
        h.assertTrue(!ZombiesNpcRuntime.selectDialogue(f.actor,view.token(),"yes")&&f.actor.getInventory().countItem(Items.DIAMOND)==0,"distant players cannot execute choices");
        f.actor.moveTo(f.origin.getX()+.5,f.origin.getY(),f.origin.getZ()+.5);f.start(ZombiesNpcConfig.Trigger.INTERACT,g);f.interact();view=f.map.npcRuntime().dialogue(f.actor);f.map.npcRuntime().reset();
        h.assertTrue(!ZombiesNpcRuntime.selectDialogue(f.actor,view.token(),"yes")&&f.actor.getInventory().countItem(Items.DIAMOND)==0,"round reset invalidates outstanding answers");
    }finally{f.close();}h.succeed();}
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void dialoguePortsDraftMigrationAndCodec(GameTestHelper h){
        var d=new ZombiesNpcEditorDraft(ZombiesNpcConfig.empty());var npc=d.addNpc(new ZombiesNpcConfig.Position(1,64,1,0));d.addRule(npc);ZombiesNpcSharedCanvas.ensure(d);var f=ZombiesNpcSharedCanvas.flows(d).get(0);
        var c=ZombiesNpcGraphDraft.add(ZombiesNpcSharedCanvas.looseRule(d),Type.CHOICE,20,20,null);c.getAsJsonArray("options").add(ZombiesNpcGraphDraft.option(2));
        var wait=ZombiesNpcGraphDraft.add(ZombiesNpcSharedCanvas.looseRule(d),Type.INTERACT,40,20,null);var end=ZombiesNpcGraphDraft.add(ZombiesNpcSharedCanvas.looseRule(d),Type.END,60,20,null);
        ZombiesNpcGraphDraft.connect(ZombiesNpcSharedCanvas.looseRule(d),c.get("id").getAsString(),"option:choice_1",wait.get("id").getAsString());ZombiesNpcGraphDraft.connect(ZombiesNpcSharedCanvas.looseRule(d),c.get("id").getAsString(),"option:choice_2",end.get("id").getAsString());ZombiesNpcGraphDraft.connect(ZombiesNpcSharedCanvas.looseRule(d),wait.get("id").getAsString(),"next",end.get("id").getAsString());
        ZombiesNpcSharedCanvas.connectExecution(d,new ZombiesNpcSharedCanvas.Ref(f,ZombiesNpcGraphDraft.find(f.rule(),"node_2")),"next",new ZombiesNpcSharedCanvas.Ref(null,c));
        ZombiesNpcGraphDraft.delete(f.rule(),"node_3");
        var cond=new com.google.gson.JsonObject();cond.addProperty("match","ALL");var tests=new com.google.gson.JsonArray();var test=ZombiesNpcGraphDraft.test();test.addProperty("kind","RULE_DONE");test.addProperty("reference","npc_1.rule_1");tests.add(test);cond.add("tests",tests);c.getAsJsonArray("options").get(0).getAsJsonObject().add("condition",cond);c.addProperty("speaker","npc_1");
        d.renameNpc(npc,"doctor");d.renameRule(npc,f.rule(),"quest");var validated=d.validated();
        var choice=validated.npcs().get(0).rules().get(0).graph().nodes().stream().filter(n->n.type()==Type.CHOICE).findFirst().orElseThrow();
        h.assertTrue(d.looseNodes().isEmpty()&&choice.speaker().equals("doctor")&&choice.options().get(0).condition().tests().get(0).reference().equals("doctor.quest"),"choice edges adopt as a group, remap IDs and follow NPC/rule renames");
        h.assertTrue(validated.equals(ZombiesNpcRepository.GSON.fromJson(ZombiesNpcRepository.GSON.toJson(validated),ZombiesNpcConfig.class)),"choice data survives JSON roundtrip");
        rejects(()->new ZombiesNpcConfig.Rule("auto",ZombiesNpcConfig.Trigger.AUTO,0,List.of(),false,List.of(),List.of(),0,"",1,validated.npcs().get(0).rules().get(0).graph()));
        var packet=new ZombiesNpcDialogueNetwork.View(UUID.randomUUID(),true,42,Component.literal("Guide"),Component.literal("Story"),List.of(new ZombiesNpcDialogueNetwork.Answer("yes","Yes")));
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{packet.encode(buf);h.assertTrue(packet.equals(ZombiesNpcDialogueNetwork.View.decode(buf)),"dialogue packet roundtrip");}finally{buf.release();}
        var response=new ZombiesNpcDialogueNetwork.Selection(packet.token(),"yes");buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{response.encode(buf);h.assertTrue(response.equals(ZombiesNpcDialogueNetwork.Selection.decode(buf)),"selection packet roundtrip");}finally{buf.release();}
        h.succeed();
    }
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void openDialogueRefreshesVisibilityWithoutExecuting(GameTestHelper h){var f=new Fixture(h);
        var owns=new Condition(Match.ALL,List.of(new Test(TestKind.ITEM_ACTOR,0,"minecraft:emerald",1,"",false)));
        f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"choose"),choice(List.of(new Option("yes","Yes",owns,"give"))),action("give",item(ZombiesNpcConfig.Kind.GIVE_ITEM,"minecraft:diamond"),"end"),node("end",Type.END,""))));
        f.interact();var first=f.map.npcRuntime().dialogue(f.actor);h.assertTrue(first.options().isEmpty(),"initially no visible options");
        f.actor.getInventory().setItem(15,new ItemStack(Items.EMERALD));
        h.runAtTickTime(30,()->{try{f.map.npcRuntime().tick(1);var refreshed=f.map.npcRuntime().dialogue(f.actor);h.assertTrue(refreshed!=null&&refreshed.options().size()==1&&!refreshed.token().equals(first.token()),"open dialogue refreshes newly unlocked choices");h.assertTrue(f.actor.getInventory().countItem(Items.DIAMOND)==0&&!ZombiesNpcRuntime.selectDialogue(f.actor,first.token(),"yes"),"unlocking updates buttons without executing a consequence or accepting old session");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=160)
    public static void scenarioSpeechHoldsSpawnPhaseThroughLastLine(GameTestHelper h){var f=new Fixture(h);try{
        var speaker=new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Speaker("guide","向导","gold","aqua",true,false);
        var config=new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig(1,null,List.of(speaker),List.of(new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Dialogue(1,List.of(new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Line("guide","准备",1),new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Line("guide","出发",2)),.5)),List.of());
        var rt=f.map.scenarioRuntime();rt.start(config);rt.startWaveText(1);
        var state=new com.cdp.codpattern.app.zombies.runtime.ZombiesRoomRuntimeState(com.cdp.codpattern.app.match.model.RoomId.of("zombies","scenario-text"));state.configureMaxWave(2);state.transitionTo(ZombiesGamePhase.INTERMISSION);
        var lifecycle=new com.cdp.codpattern.app.zombies.runtime.ZombiesLifecycleRuntime(state,new com.cdp.codpattern.app.zombies.runtime.ZombiesPhaseStateMachine.Config(0,1,0),null,s0->false,List.of());lifecycle.setIntermissionReady(()->!rt.dialogueActive());
        for(int tick=0;tick<69;tick++){rt.tick();lifecycle.tick();h.assertTrue(state.phase()==ZombiesGamePhase.INTERMISSION,"spawn phase is held through initial delay, both lines and final reading duration");}
        h.assertTrue(f.map.lines.equals(List.of("[向导] 准备","[向导] 出发")),"speaker order and content are retained");rt.tick();lifecycle.tick();h.assertTrue(state.phase()==ZombiesGamePhase.WAVE_ACTIVE&&!rt.dialogueActive(),"spawn begins after final line duration, rather than while text is playing");rt.reset();
    }finally{f.map.scenarioRuntime().reset();f.close();}h.succeed();}
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void scenarioRegionWaveNpcTriggerAndReset(GameTestHelper h){var f=new Fixture(h);try{
        var at=f.origin.offset(2,0,0);h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),18);h.getLevel().setBlock(at.east(),Blocks.AIR.defaultBlockState(),18);
        var event=new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.MapEvent("door","通道",true,2,"guide.flow",List.of(new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Region(at,at.east(),"minecraft:stone")),com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Weather.RAIN,18000L);
        f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"end"),node("end",Type.END,""))));
        var rt=f.map.scenarioRuntime();rt.start(new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig(1,null,List.of(),List.of(),List.of(event)));rt.tick();h.assertTrue(h.getLevel().getBlockState(at).isAir(),"NPC and wave gate both required");
        f.map.wave=2;rt.tick();h.assertTrue(!rt.completed("door"),"wave alone does not trigger NPC gated event");f.interact();long daytime=h.getLevel().getDayTime();boolean rain=h.getLevel().isRaining();rt.tick();
        h.assertTrue(rt.completed("door")&&h.getLevel().getBlockState(at).is(Blocks.STONE)&&h.getLevel().getBlockState(at.east()).is(Blocks.STONE),"completed NPC plus wave applies full region");
        h.assertTrue(h.getLevel().getDayTime()==daytime&&h.getLevel().isRaining()==rain,"room environment never mutates dimension-wide weather or time");
        h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),18);rt.tick();h.assertTrue(h.getLevel().getBlockState(at).isAir(),"event runs only once per round");
        f.map.npcRuntime().reset();rt.reset();h.assertTrue(h.getLevel().getBlockState(at.east()).isAir(),"map region changes restore through the persistent journal");
    }finally{f.map.scenarioRuntime().reset();f.close();}h.succeed();}
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void npcActionCallsManualMapEvent(GameTestHelper h){var f=new Fixture(h);try{
        var at=f.origin.offset(2,0,0);h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),18);
        var event=new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.MapEvent("bridge","桥梁",false,0,"",List.of(new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Region(at,at,"minecraft:oak_log")),null,null);
        var config=new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig(1,null,List.of(),List.of(),List.of(event));f.map.scenarioRuntime().start(config);
        var a=new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.MAP_EVENT,null,null,"bridge",null,0,null,null,0);
        f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"event"),action("event",a,"end"),node("end",Type.END,""))));
        f.map.scenarioRuntime().tick();h.assertTrue(h.getLevel().getBlockState(at).isAir(),"manual event is not automatically fired");f.interact();h.assertTrue(h.getLevel().getBlockState(at).is(Blocks.OAK_LOG),"NPC action invokes selected event");
    }finally{f.map.scenarioRuntime().reset();f.close();}h.succeed();}
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void scenarioValidationPreventsUnboundedAndOutsideChanges(GameTestHelper h){var f=new Fixture(h);try{
        rejects(()->new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Region(BlockPos.ZERO,new BlockPos(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE),"minecraft:stone"));
        var outside=new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.MapEvent("outside","outside",true,1,"",List.of(new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Region(f.origin.offset(20,0,0),f.origin.offset(20,0,0),"minecraft:stone")),null,null);
        rejects(()->f.map.scenarioRuntime().validate(new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig(1,null,List.of(),List.of(),List.of(outside)),ZombiesNpcConfig.empty()));
        rejects(()->new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig(1,null,List.of(),List.of(new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Dialogue(1,List.of(new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Line("unknown","text",1)))),List.of()));
        var invalid=com.google.gson.JsonParser.parseString("{\"wave\":1,\"maxAlive\":0,\"mobs\":[]}").getAsJsonObject();
        var repo=new com.cdp.codpattern.app.zombies.service.ZombiesWaveConfigRepository(java.nio.file.Path.of("build/verification/wave-validation"));h.assertTrue(!repo.loadConfigured(List.of(invalid)).isValid(),"GUI saves use the same wave validator as file loading");
    }finally{f.close();}h.succeed();}
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void sceneSchemaRejectsCorruptionAndReadsLegacyFill(GameTestHelper h){
        rejects(()->new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Scene(2,1,1,List.of("minecraft:air"),List.of(0)));
        rejects(()->new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Scene(2,1,1,List.of("minecraft:air"),List.of(0,2)));
        rejects(()->new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Scene(4096,4096,4096,List.of("minecraft:air"),List.of(0)));
        var scene=new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Scene(2,1,1,List.of("minecraft:stone"),List.of(0,0));
        rejects(()->new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Region(BlockPos.ZERO,BlockPos.ZERO,null,com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.RegionMode.SCENE,scene,null,null));
        var legacy=ZombiesNpcRepository.GSON.fromJson("{\"from\":{\"x\":0,\"y\":0,\"z\":0},\"to\":{\"x\":1,\"y\":0,\"z\":0},\"block\":\"minecraft:stone\"}",com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Region.class);
        h.assertTrue(legacy.mode()==com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.RegionMode.FILL&&legacy.placements().size()==2,"old single-block fill regions retain their original behavior");h.succeed();
    }
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void coordinateCodecIsStableAcrossProductionFieldNames(GameTestHelper h){
        var expected=new BlockPos(3,-52,-39);
        var typed=ZombiesNpcRepository.GSON.fromJson("{\"x\":\"3\",\"y\":\"-52\",\"z\":\"-39\"}",BlockPos.class);
        var legacy=ZombiesNpcRepository.GSON.fromJson("{\"f_123285_\":3,\"f_123286_\":-52,\"f_123289_\":-39}",BlockPos.class);
        h.assertTrue(expected.equals(typed)&&expected.equals(legacy),"manual GUI coordinates and production legacy coordinates decode identically");
        var json=ZombiesNpcRepository.GSON.toJsonTree(expected.mutable()).getAsJsonObject();
        h.assertTrue(json.keySet().equals(Set.of("x","y","z"))&&json.get("y").getAsInt()==-52,"both mutable and immutable positions always write stable x/y/z keys");
        for(String invalid:List.of("{}","{\"x\":3,\"y\":-52}","{\"x\":3.5,\"y\":-52,\"z\":-39}","{\"x\":2147483648,\"y\":0,\"z\":0}")){
            boolean rejected=false;try{ZombiesNpcRepository.GSON.fromJson(invalid,BlockPos.class);}catch(com.google.gson.JsonParseException e){rejected=true;}h.assertTrue(rejected,"invalid positions fail explicitly instead of silently becoming zero");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="npc_graph",timeoutTicks=100)
    public static void livingLyingNpcCanBePickedAndInteractedAtEveryYaw(GameTestHelper h){var f=new Fixture(h);try{
        for(float yaw:new float[]{0,90,180,270}){
            f.start(ZombiesNpcConfig.Trigger.INTERACT,new ZombiesNpcGraph(List.of(node("entry",Type.ENTRY,"say"),action("say",say("lying dialogue"),"end"),node("end",Type.END,""))));
            var npc=h.getLevel().getEntitiesOfClass(ZombiesNpcEntity.class,new AABB(f.origin).inflate(1)).stream().filter(n->n.getName().getString().equals("guide")).findFirst().orElseThrow();
            npc.apply(new ZombiesNpcConfig.Appearance(null,null,ZombiesNpcConfig.Pose.LYING,true,new ZombiesNpcConfig.Position(npc.getX(),npc.getY(),npc.getZ(),yaw)));
            var box=npc.getBoundingBox();double angle=Math.toRadians(yaw),dx=Math.cos(angle),dz=Math.sin(angle);
            var foot=new net.minecraft.world.phys.Vec3(npc.getX()+dx*.9,npc.getY()+.5,npc.getZ()+dz*.9);var head=new net.minecraft.world.phys.Vec3(npc.getX()-dx*.9,npc.getY()+.5,npc.getZ()-dz*.9);
            h.assertTrue(box.contains(foot)&&box.contains(head)&&box.clip(foot.add(0,2,0),foot.add(0,-1,0)).isPresent(),"ray reaches visible horizontal body at yaw "+yaw);
            h.assertTrue(npc.isPickable()&&box.getYsize()<1.1,"lying NPC is pickable with a horizontal interaction shape");
            npc.interact(f.actor,net.minecraft.world.InteractionHand.MAIN_HAND);h.assertTrue(f.map.lines.get(f.map.lines.size()-1).equals("[guide] lying dialogue"),"normal entity interaction dispatch reaches the NPC graph");
            int count=f.map.lines.size();npc.apply(new ZombiesNpcConfig.Appearance(null,null,null,false,null));npc.interact(f.actor,net.minecraft.world.InteractionHand.MAIN_HAND);h.assertTrue(f.map.lines.size()==count,"dead story NPC remains non-interactive");
            npc.apply(new ZombiesNpcConfig.Appearance(null,null,ZombiesNpcConfig.Pose.STANDING,true,null));h.assertTrue(npc.getBoundingBox().getYsize()>1.7&&npc.getBoundingBox().getXsize()<.7,"standing after revival restores upright dimensions");
        }
    }finally{f.close();}h.succeed();}
    private static Node choice(List<Option> options){return new Node("choose",Type.CHOICE,0,0,null,null,0,"","",true,"What will you do?","",options);}
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
        ServerPlayer player(){var p=new FakePlayer(map.getServerLevel(),new GameProfile(UUID.randomUUID(),"graph-actor")){@Override public boolean hasPermissions(int level){return map.admin&&level<=2;}@Override public void sendSystemMessage(Component message){map.notices.add(message);}};p.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5);map.actors.add(p);return p;}
        void start(ZombiesNpcConfig.Trigger trigger,ZombiesNpcGraph graph){var r=new ZombiesNpcConfig.Rule("flow",trigger,0,List.of(),false,List.of(),List.of(),0,"",1,graph);start(List.of(r),0);}
        void start(List<ZombiesNpcConfig.Rule> rules,double interval){var npc=new ZombiesNpcConfig.Npc("guide",new ZombiesNpcConfig.Appearance("guide",null,ZombiesNpcConfig.Pose.STANDING,true,position(origin)),"",rules,interval);map.npcRuntime().start(new ZombiesNpcConfig(1,List.of(npc)),false);}
        void interact(){interact(actor);}
        void interact(ServerPlayer player){var npc=map.getServerLevel().getEntitiesOfClass(ZombiesNpcEntity.class,new AABB(origin).inflate(1)).stream().filter(n->n.getName().getString().equals("guide")).findFirst().orElseThrow();map.npcRuntime().interact(player,"guide",npc);}
        void close(){map.npcRuntime().reset();FPSMCore.getInstance().unregisterMap(map);}
    }
    private static final class TestMap extends ZombiesMap{
        final List<ServerPlayer> actors=new ArrayList<>();final List<String> lines=new ArrayList<>();final List<Long> times=new ArrayList<>();final List<Component> notices=new ArrayList<>();int wave=1;boolean barrierOpen,admin=true;ZombiesGamePhase phase=ZombiesGamePhase.WAVE_ACTIVE;
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
