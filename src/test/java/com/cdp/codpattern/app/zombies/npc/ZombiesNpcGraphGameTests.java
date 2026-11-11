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
        ServerPlayer player(){var p=new FakePlayer(map.getServerLevel(),new GameProfile(UUID.randomUUID(),"graph-actor"));p.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5);map.actors.add(p);return p;}
        void start(ZombiesNpcConfig.Trigger trigger,ZombiesNpcGraph graph){var r=new ZombiesNpcConfig.Rule("flow",trigger,0,List.of(),false,List.of(),List.of(),0,"",1,graph);start(List.of(r),0);}
        void start(List<ZombiesNpcConfig.Rule> rules,double interval){var npc=new ZombiesNpcConfig.Npc("guide",new ZombiesNpcConfig.Appearance("guide",null,ZombiesNpcConfig.Pose.STANDING,true,position(origin)),"",rules,interval);map.npcRuntime().start(new ZombiesNpcConfig(1,List.of(npc)),false);}
        void interact(){interact(actor);}
        void interact(ServerPlayer player){var npc=map.getServerLevel().getEntitiesOfClass(ZombiesNpcEntity.class,new AABB(origin).inflate(1)).stream().findFirst().orElseThrow();map.npcRuntime().interact(player,"guide",npc);}
        void close(){map.npcRuntime().reset();FPSMCore.getInstance().unregisterMap(map);}
    }
    private static final class TestMap extends ZombiesMap{
        final List<ServerPlayer> actors=new ArrayList<>();final List<String> lines=new ArrayList<>();final List<Long> times=new ArrayList<>();int wave=1;
        TestMap(net.minecraft.server.level.ServerLevel l,String n,AreaData a){super(l,n,a);}
        @Override public ZombiesGamePhase currentPhase(){return ZombiesGamePhase.WAVE_ACTIVE;}
        @Override public int npcWave(){return wave;}
        @Override public boolean isAliveSurvivor(UUID id){return actors.stream().anyMatch(p->p.getUUID().equals(id));}
        @Override public List<ServerPlayer> npcPlayers(){return List.copyOf(actors);}
        @Override public void tick(){npcRuntime().tick(wave);}
        @Override public void sendNpcDialogue(Component c){lines.add(c.getString());times.add(getServerLevel().getGameTime());}
    }
}
