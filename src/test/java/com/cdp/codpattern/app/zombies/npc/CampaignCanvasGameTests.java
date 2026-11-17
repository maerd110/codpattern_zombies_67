package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.app.zombies.campaign.*;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.scenario.*;
import com.cdp.codpattern.app.zombies.service.*;
import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.nio.file.*;
import java.util.*;
import static com.cdp.codpattern.app.zombies.npc.ZombiesNpcGraph.*;

@GameTestHolder("codpattern_npc")
@PrefixGameTestTemplate(false)
public final class CampaignCanvasGameTests {
    private static Node node(String id,Type type,String next){return new Node(id,type,0,0,null,null,0,next,"");}
    private static ZombiesNpcConfig program(BlockPos pos,boolean source){
        List<Node> nodes;
        if(source){var choice=new Node("choose",Type.CHOICE,210,0,null,null,0,"","",true,"要救下老人吗？","",List.of(new Option("save","救下老人",null,"grant"),new Option("leave","离开",null,"end")));
            var grant=new Node("grant",Type.ACTION,420,0,new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.GIVE_ITEM,null,null,null,"minecraft:emerald",1,null,null,0),null,0,"wait","");
            var wait=new Node("wait",Type.AWAIT,630,0,null,new Condition(Match.ALL,List.of(new Test(TestKind.WAVE,10,"",1,"",false))),0,"end","");nodes=List.of(node("entry",Type.ENTRY,"choose"),choice,grant,wait,node("end",Type.END,""));
        }else{var say=new Node("say",Type.ACTION,210,0,new ZombiesNpcConfig.Action(ZombiesNpcConfig.Kind.DIALOGUE,null,null,"老人还活着",null,0,null,null,0),null,0,"end","");nodes=List.of(node("entry",Type.ENTRY,"say"),say,node("end",Type.END,""));}
        var rule=new ZombiesNpcConfig.Rule("flow",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of(),List.of(),0,"",1,new ZombiesNpcGraph(nodes));
        return new ZombiesNpcConfig(1,List.of(new ZombiesNpcConfig.Npc("guide",new ZombiesNpcConfig.Appearance("Guide",null,ZombiesNpcConfig.Pose.STANDING,true,new ZombiesNpcConfig.Position(pos.getX(),pos.getY(),pos.getZ(),0)),"",List.of(rule),0)));
    }
    private static CampaignDefinition install(CampaignGameTests.Fixture f)throws Exception{
        ZombiesNpcRepository.save(ZombiesNpcRepository.path(f.server,f.a.getMapName()),program(f.a.getMapArea().pos1().offset(2,1,2),true));
        ZombiesNpcRepository.save(ZombiesNpcRepository.path(f.server,f.b.getMapName()),program(f.b.getMapArea().pos1().offset(2,1,2),false));
        var d=CampaignStore.GSON.toJsonTree(CampaignStore.definition(f.server)).getAsJsonObject();
        d.getAsJsonArray("tasks").add(CampaignStore.GSON.toJsonTree(new CampaignDefinition.Entry("witness","询问老人","找到被救下的老人")));d.getAsJsonArray("events").add(CampaignStore.GSON.toJsonTree(new CampaignDefinition.Event("arrival","进入老人存活路线","b",0,"","","",List.of(new CampaignDefinition.Effect(CampaignDefinition.Operation.TASK,"witness","active",1,true)))));
        var links=new JsonArray();links.add(CampaignStore.GSON.toJsonTree(new CampaignCanvas.Link("route",CampaignCanvas.node("a","guide","flow","choose"),"save",CampaignCanvas.node("b","guide","flow","say"),CampaignCanvas.LinkKind.REQUIRE,"",1,false)));
        links.add(CampaignStore.GSON.toJsonTree(new CampaignCanvas.Link("letter",CampaignCanvas.node("a","guide","flow","choose"),"save","item:letter",CampaignCanvas.LinkKind.EFFECT,"give",1,true)));
        links.add(CampaignStore.GSON.toJsonTree(new CampaignCanvas.Link("elder",CampaignCanvas.node("a","guide","flow","choose"),"save","state:elder",CampaignCanvas.LinkKind.EFFECT,"saved",1,true)));
        links.add(CampaignStore.GSON.toJsonTree(new CampaignCanvas.Link("arrival_gate",CampaignCanvas.node("a","guide","flow","choose"),"save","event:arrival",CampaignCanvas.LinkKind.REQUIRE,"",1,true)));
        var canvas=new JsonObject();canvas.add("links",links);canvas.add("tags",CampaignStore.GSON.toJsonTree(List.of(new CampaignCanvas.Tag("safety",CampaignCanvas.node("a","guide","flow","grant"),"done","安全屋",CampaignCanvas.TagKind.CHECKPOINT))));d.add("canvas",canvas);
        var definition=CampaignStore.GSON.fromJson(d,CampaignDefinition.class);CampaignService.validate(f.owner,definition);CampaignStore.atomic(f.definition,CampaignStore.GSON.toJson(definition));return definition;
    }
    private static void choose(CampaignGameTests.Fixture f,String option){var npc=f.a.getServerLevel().getEntitiesOfClass(ZombiesNpcEntity.class,new AABB(f.a.getMapArea().pos1(),f.a.getMapArea().pos2()).inflate(8)).stream().filter(n->n.getName().getString().equals("Guide")).findFirst().orElseThrow();f.owner.moveTo(npc.getX(),npc.getY(),npc.getZ());f.a.npcRuntime().interact(f.owner,"guide",npc);var view=f.a.npcRuntime().dialogue(f.owner);if(view==null)throw new AssertionError("choice should open");if(!ZombiesNpcRuntime.selectDialogue(f.owner,view.token(),option))throw new AssertionError("offered choice should resolve");}

    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void actualChoiceGatesNextChapterAndReplayHasNoFutureFacts(GameTestHelper h)throws Exception{try(var f=new CampaignGameTests.Fixture(h)){
        install(f);f.join(f.a,f.owner);CampaignRuntime.launch(f.owner,"story","",-1);h.assertTrue(!CampaignCanvasRuntime.ready(f.a,CampaignCanvas.node("b","guide","flow","say"),f.owner),"future NPC gate is closed before actual choice");
        f.a.phase=ZombiesGamePhase.WAVE_ACTIVE;choose(f,"save");var s=CampaignRuntime.session(f.a);h.assertTrue(CampaignCanvasRuntime.ready(f.a,CampaignCanvas.node("b","guide","flow","say"),f.owner)&&CampaignRuntime.item(f.a,f.owner,"letter",1),"selecting the actual option records its fact and grants named warehouse item");
        CampaignRuntime.tick(f.a);h.assertTrue(!s.failed&&!s.pendingCheckpoint.isBlank(),"tag during combat defers safely without freezing or failing story");f.a.phase=ZombiesGamePhase.INTERMISSION;CampaignRuntime.tick(f.a);h.assertTrue(s.pendingCheckpoint.isBlank(),"next safe intermission saves tag");String save=CampaignRuntime.str(s.saves.get(f.owner.getUUID()),"id");
        f.a.phase=ZombiesGamePhase.VICTORY;CampaignRuntime.victory(f.a);CampaignRuntime.next(f.owner);CampaignRuntime.drain(f.server);h.assertTrue(CampaignRuntime.session(f.b)!=null&&CampaignCanvasRuntime.ready(f.b,CampaignCanvas.node("b","guide","flow","say"),f.owner),"exact choice prerequisite survives actual chapter transition");CampaignRuntime.tick(f.b);h.assertTrue(CampaignRuntime.task(f.b).equals("找到被救下的老人"),"condition-wired event automatically activates its named task in the later chapter");
        CampaignRuntime.end(f.owner);CampaignRuntime.launch(f.owner,"story",save,0);h.assertTrue(!CampaignCanvasRuntime.ready(f.a,CampaignCanvas.node("b","guide","flow","say"),f.owner)&&!CampaignRuntime.item(f.a,f.owner,"letter",1),"old chapter branch restores history before choice and reward, leaving old route intact");
    }h.succeed();}

    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void checkpointResumesNodeCursorWithoutRepeatingPhysicalOrStoryRewards(GameTestHelper h)throws Exception{try(var f=new CampaignGameTests.Fixture(h)){
        install(f);f.join(f.a,f.owner);CampaignRuntime.launch(f.owner,"story","",-1);choose(f,"save");CampaignRuntime.tick(f.a);var s=CampaignRuntime.session(f.a);String save=CampaignRuntime.str(s.saves.get(f.owner.getUUID()),"id");var local=CampaignStore.tip(CampaignStore.get(f.server).save(f.owner.getUUID(),save)).getAsJsonObject("local");h.assertTrue(local.getAsJsonArray("npcPrograms").get(0).getAsJsonObject().get("cursor").getAsString().equals("wait"),"safety snapshot captures exact post-reward cursor");
        int physical=count(f.owner,Items.EMERALD);CampaignRuntime.end(f.owner);CampaignRuntime.launch(f.owner,"story",save,-1);f.a.npcRuntime().tick(1);h.assertTrue(count(f.owner,Items.EMERALD)==physical&&CampaignRuntime.item(f.a,f.owner,"letter",1)&&!CampaignRuntime.item(f.a,f.owner,"letter",2),"resuming at wait does not replay physical grant or story effect");
        CampaignCanvasRuntime.reached(f.a,"guide","flow","choose","save",f.owner);h.assertTrue(!CampaignRuntime.item(f.a,f.owner,"letter",2),"revisiting the same story effect wire cannot farm another warehouse reward");
    }h.succeed();}
    private static int count(net.minecraft.server.level.ServerPlayer p,Item item){int n=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(item))n+=p.getInventory().getItem(i).getCount();return n;}

    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void effectsRollbackAllFactsAndInventoriesWhenOneTransferFails(GameTestHelper h)throws Exception{try(var f=new CampaignGameTests.Fixture(h)){
        var definition=install(f);var d=CampaignStore.GSON.toJsonTree(definition).getAsJsonObject();d.getAsJsonObject("canvas").getAsJsonArray("links").add(CampaignStore.GSON.toJsonTree(new CampaignCanvas.Link("cannot",CampaignCanvas.node("a","guide","flow","choose"),"save","item:letter",CampaignCanvas.LinkKind.EFFECT,"take",5,true)));CampaignStore.atomic(f.definition,CampaignStore.GSON.toJson(CampaignStore.GSON.fromJson(d,CampaignDefinition.class)));f.join(f.a,f.owner);CampaignRuntime.launch(f.owner,"story","",-1);var s=CampaignRuntime.session(f.a);String before=s.world.toString();
        try{CampaignCanvasRuntime.reached(f.a,"guide","flow","choose","save",f.owner);throw new AssertionError("insufficient quest items must fail");}catch(IllegalArgumentException expected){}
        h.assertTrue(s.world.toString().equals(before)&&!CampaignRuntime.item(f.a,f.owner,"letter",1),"failed link bundle cannot leak a choice, state change, account reward or partial item grant");
    }h.succeed();}

    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void workspaceRequiresToolRevisionsAndTemplatesDoNotPlaceNpcs(GameTestHelper h)throws Exception{try(var f=new CampaignGameTests.Fixture(h)){
        install(f);h.assertTrue(CampaignCanvasService.perform(f.owner,"canvas_load","{}").has("error"),"admin must actually hold deployment tool");f.owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.cdp.codpattern.app.zombies.bootstrap.ZombiesItemRegister.ZOMBIES_DEPLOY_TOOL.get()));var reply=CampaignCanvasService.perform(f.owner,"canvas_load","{}");h.assertTrue(!reply.has("error"),"workspace opens: "+reply);
        var config=reply.getAsJsonObject("config");var actor=new CampaignDefinition.Entry("elder_actor","老人","跨关人物");config.getAsJsonArray("actors").add(CampaignStore.GSON.toJsonTree(actor));var original=program(f.a.getMapArea().pos1().offset(2,1,2),true).npcs().get(0);var profile=new CampaignCanvas.Profile("elder_actor",original.appearance(),"默认台词");h.assertTrue(profile.appearance().position()==null,"reusable identity cannot retain source map coordinates");config.getAsJsonObject("canvas").getAsJsonArray("profiles").add(CampaignStore.GSON.toJsonTree(profile));config.getAsJsonObject("canvas").getAsJsonArray("references").add(CampaignStore.GSON.toJsonTree(new CampaignCanvas.Reference("later_elder","b","elder_actor","")));
        int before=ZombiesNpcRepository.load(ZombiesNpcRepository.path(f.server,f.b.getMapName())).npcs().size();var q=new JsonObject();q.add("config",config);q.add("revision",reply.get("revision"));q.add("maps",reply.get("maps"));var saved=CampaignCanvasService.perform(f.owner,"canvas_save",q.toString());h.assertTrue(!saved.has("error"),"template and unresolved reference save: "+saved);h.assertTrue(ZombiesNpcRepository.load(ZombiesNpcRepository.path(f.server,f.b.getMapName())).npcs().size()==before,"reference alone never adds NPC placement or physical entity");
        var stale=CampaignCanvasService.perform(f.owner,"canvas_save",q.toString());h.assertTrue(stale.has("error")&&stale.get("error").getAsString().contains("修改"),"stale workspace cannot overwrite newer campaign definition");
        var invalid=saved.getAsJsonObject("config");invalid.getAsJsonObject("canvas").getAsJsonArray("links").get(0).getAsJsonObject().addProperty("port","deleted_choice");q.add("config",invalid);q.add("revision",saved.get("revision"));q.add("maps",saved.get("maps"));String previous=Files.readString(f.definition);h.assertTrue(CampaignCanvasService.perform(f.owner,"canvas_save",q.toString()).has("error")&&Files.readString(f.definition).equals(previous),"deleted option reference is rejected before persistence");
    }h.succeed();}

    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void authoringJournalCompletesInterruptedBundleAndRejectsEscapingPaths(GameTestHelper h)throws Exception{
        var root=Files.createTempDirectory("campaign-authoring-");var a=root.resolve("serverconfig/maps/a/npcs.json");var b=root.resolve("serverconfig/codpattern_zombies/campaigns.json");var writes=new LinkedHashMap<Path,String>();writes.put(a,"map-new");writes.put(b,"campaign-new");CampaignAuthoringTransaction.commit(root,writes);h.assertTrue(Files.readString(a).equals("map-new")&&Files.readString(b).equals("campaign-new"),"all members of authoring save are persisted");
        var journal=root.resolve("serverconfig/codpattern_zombies/authoring-pending.json");var pending=new JsonObject();pending.addProperty("serverconfig/maps/a/npcs.json","map-recovered");pending.addProperty("serverconfig/codpattern_zombies/campaigns.json","campaign-recovered");Files.writeString(journal,pending.toString());Files.writeString(a,"map-recovered");CampaignAuthoringTransaction.recover(root);h.assertTrue(Files.readString(b).equals("campaign-recovered")&&!Files.exists(journal),"restart completes whole bundle after interruption between writes");
        try{CampaignAuthoringTransaction.commit(root,Map.of(root.resolve("../outside.json"),"bad"));throw new AssertionError("unsafe path accepted");}catch(IllegalArgumentException expected){}h.succeed();
    }

    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void missingSceneEntityRetainsCauseAndEventLocationWithoutChangingBlocks(GameTestHelper h){
        var origin=h.absolutePos(new BlockPos(1,2,1));var map=new com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap(h.getLevel(),"missing-entity-test",new com.phasetranscrystal.fpsmatch.core.data.AreaData(origin.offset(-1,-1,-1),origin.offset(5,5,5)));
        var scene=new ZombiesScenarioConfig.Scene(1,1,1,List.of("minecraft:stone"),List.of(0),List.of(),List.of(new ZombiesScenarioConfig.SceneEntity("{id:\"absent_scene:helicopter\",Pos:[0.5d,0.0d,0.5d]}")),true);var region=new ZombiesScenarioConfig.Region(origin,origin,"minecraft:stone",ZombiesScenarioConfig.RegionMode.SCENE,scene,null,null);var event=new ZombiesScenarioConfig.MapEvent("event_1","停机坪",false,0,"",List.of(region),null,null);
        var state=h.getLevel().getBlockState(origin);try{map.scenarioRuntime().validate(new ZombiesScenarioConfig(1,null,List.of(),List.of(),List.of(event)),ZombiesNpcConfig.empty());throw new AssertionError("missing entity must block startup");}catch(IllegalArgumentException e){String reason=ZombiesStartupDiagnostics.reason(e);h.assertTrue(reason.contains("event_1")&&reason.contains("absent_scene:helicopter"),"diagnostics identify event and missing mod entity");var result=ZombiesServiceResult.failure(ZombiesErrorCode.STARTUP_PREFLIGHT_FAILED,Map.of("reason",ModePlayerValue.ofString(reason)),"test");var translated=(net.minecraft.network.chat.contents.TranslatableContents)ZombiesStartupDiagnostics.message(result).getContents();h.assertTrue(Arrays.toString(translated.getArgs()).contains("absent_scene:helicopter"),"chat receives real cause instead of only generic error code");}h.assertTrue(h.getLevel().getBlockState(origin).equals(state),"preflight does not write destination blocks");h.succeed();
    }
}
