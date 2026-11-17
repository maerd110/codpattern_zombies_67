package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.app.zombies.campaign.*;
import com.cdp.codpattern.app.zombies.model.*;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.nio.file.*;

@GameTestHolder("codpattern_npc")
@PrefixGameTestTemplate(false)
public final class CampaignGameTests {
    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void accountAndMultiPlayerCheckpointAreAtomicAndDurable(GameTestHelper h)throws Exception{
        var dir=Files.createTempDirectory("campaign-transaction-");var file=dir.resolve("state.json");var store=new CampaignStore(file);UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        store.transaction(root->{CampaignStore.account(root,a).getAsJsonObject("collections").addProperty("museum",1);CampaignStore.account(root,b).getAsJsonObject("completed").addProperty("chapter_a",true);});
        var disk=new CampaignStore(file);h.assertTrue(disk.account(a).getAsJsonObject("collections").has("museum")&&disk.account(b).getAsJsonObject("completed").has("chapter_a"),"both accounts survive fresh store load");String previous=Files.readString(file);
        try{store.transaction(root->{CampaignStore.account(root,a).getAsJsonObject("collections").addProperty("invalid",1);throw new IllegalStateException("interrupted");});throw new AssertionError("transaction should fail");}catch(IllegalStateException expected){}
        h.assertTrue(Files.readString(file).equals(previous)&&!store.account(a).getAsJsonObject("collections").has("invalid"),"failed transaction changes neither disk nor live state");
        var blocked=dir.resolve("directory.json");Files.createDirectory(blocked);try{CampaignStore.atomic(blocked,"{}");throw new AssertionError("cannot replace directory");}catch(java.io.UncheckedIOException expected){}h.assertTrue(Files.isDirectory(blocked),"failed atomic replace preserves previous target");h.succeed();
    }
    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void partyAdoptionChapterTransitionAndReplayKeepIndependentHistories(GameTestHelper h)throws Exception{try(var f=new Fixture(h)){
        f.join(f.a,f.owner);f.join(f.a,f.guest);CampaignRuntime.launch(f.owner,"story","",-1);var first=CampaignRuntime.session(f.a);CampaignRuntime.keep(f.guest,true);
        f.a.phase=ZombiesGamePhase.INTERMISSION;CampaignRuntime.event(f.a,"rescue",f.owner);CampaignRuntime.tick(f.a);
        String ownerSave=CampaignRuntime.str(first.saves.get(f.owner.getUUID()),"id"),guestSave=CampaignRuntime.str(first.saves.get(f.guest.getUUID()),"id");
        h.assertTrue(!ownerSave.equals(guestSave),"each participant owns an independent save");h.assertTrue(first.inventories.get(f.guest.getUUID()).get("letter").getAsInt()==1,"every player receives their own warehouse item");
        var store=CampaignStore.get(h.getLevel().getServer());h.assertTrue(store.account(f.guest.getUUID()).getAsJsonObject("collections").size()==0,"checkpoint does not award settlement collection early");
        f.a.phase=ZombiesGamePhase.VICTORY;CampaignRuntime.victory(f.a);h.assertTrue(store.account(f.guest.getUUID()).getAsJsonObject("completed").has("a"),"guest gets real personal completion");
        CampaignRuntime.next(f.owner);CampaignRuntime.drain(h.getLevel().getServer());var second=CampaignRuntime.session(f.b);h.assertTrue(second!=null&&second.chapter.equals("b")&&CampaignRuntime.state(f.b,"elder","saved"),"next chapter uses coherent rescued elder history");
        h.assertTrue(CampaignRuntime.item(f.b,f.owner,"letter",1)&&CampaignRuntime.item(f.b,f.guest,"letter",1),"personal story warehouses survive chapter transition");
        h.assertTrue(CampaignRuntime.str(second.saves.get(f.guest.getUUID()),"id").equals(guestSave),"continuous party updates existing guest route");
        f.b.phase=ZombiesGamePhase.INTERMISSION;CampaignRuntime.event(f.b,"spend",f.owner);CampaignRuntime.tick(f.b);h.assertTrue(!CampaignRuntime.item(f.b,f.owner,"letter",1)&&CampaignRuntime.item(f.b,f.guest,"letter",1),"personal consumption cannot consume teammate warehouse");
        CampaignRuntime.end(f.owner);CampaignRuntime.launch(f.owner,"story",ownerSave,0);var replay=CampaignRuntime.session(f.a);h.assertTrue(replay!=null&&!CampaignRuntime.str(replay.saves.get(f.owner.getUUID()),"id").equals(ownerSave),"old chapter creates independent branch");
        h.assertTrue(!CampaignRuntime.state(f.a,"elder","saved")&&!CampaignRuntime.item(f.a,f.owner,"letter",1),"replay restores chapter start without future choices or items");
        h.assertTrue(store.save(f.owner.getUUID(),ownerSave).getAsJsonArray("history").size()>1&&store.account(f.owner.getUUID()).getAsJsonObject("collections").has("trophy"),"old route and account trophy remain untouched by replay");
    }h.succeed();}
    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void settlementExcludesOfflineAndHostTransferNeverReplacesWorld(GameTestHelper h)throws Exception{try(var f=new Fixture(h)){
        f.join(f.a,f.owner);f.join(f.a,f.guest);CampaignRuntime.launch(f.owner,"story","",-1);CampaignRuntime.keep(f.guest,true);var s=CampaignRuntime.session(f.a);f.a.phase=ZombiesGamePhase.INTERMISSION;CampaignRuntime.event(f.a,"rescue",f.owner);CampaignRuntime.tick(f.a);
        f.setOnline(f.owner,false);CampaignRuntime.tick(f.a);h.assertTrue(s.host.equals(f.guest.getUUID())&&CampaignRuntime.state(f.a,"elder","saved"),"lowest latency remaining player gets management with same world history");f.a.phase=ZombiesGamePhase.VICTORY;CampaignRuntime.victory(f.a);
        var store=CampaignStore.get(h.getLevel().getServer());h.assertTrue(!store.account(f.owner.getUUID()).getAsJsonObject("completed").has("a")&&store.account(f.guest.getUUID()).getAsJsonObject("completed").has("a"),"only online room participants earn settlement progress");
        h.assertTrue(!store.account(f.owner.getUUID()).getAsJsonObject("collections").has("trophy")&&store.account(f.guest.getUUID()).getAsJsonObject("collections").has("trophy"),"offline player cannot earn deferred account trophy");
    }h.succeed();}
    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void invalidEventsRollbackAndSavedEventsDoNotDuplicateItemsOnResume(GameTestHelper h)throws Exception{try(var f=new Fixture(h)){
        f.join(f.a,f.owner);CampaignRuntime.launch(f.owner,"story","",-1);f.a.phase=ZombiesGamePhase.INTERMISSION;CampaignRuntime.event(f.a,"rescue",f.owner);CampaignRuntime.tick(f.a);var s=CampaignRuntime.session(f.a);String id=CampaignRuntime.str(s.saves.get(f.owner.getUUID()),"id");
        var old=s.world.toString();try{CampaignRuntime.event(f.a,"impossible",f.owner);throw new AssertionError("insufficient item accepted");}catch(IllegalArgumentException expected){}h.assertTrue(s.world.toString().equals(old)&&CampaignRuntime.item(f.a,f.owner,"letter",1),"failed multi effect restores flags and inventories");
        CampaignRuntime.end(f.owner);CampaignRuntime.launch(f.owner,"story",id,-1);CampaignRuntime.event(f.a,"rescue",f.owner);h.assertTrue(CampaignRuntime.session(f.a).inventories.get(f.owner.getUUID()).get("letter").getAsInt()==1,"resuming a saved event never grants its item again");
        var q=new JsonObject();q.addProperty("campaign","story");q.addProperty("save",id);h.assertTrue(CampaignService.perform(f.guest,"camp_detail","{\"save\":\""+id+"\"}").has("error"),"one account cannot access another account's story save");
    }h.succeed();}
    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void votesUseFrozenElectorateRevoteThenHostTieBreak(GameTestHelper h)throws Exception{try(var f=new Fixture(h)){
        f.join(f.a,f.owner);f.join(f.a,f.guest);CampaignRuntime.launch(f.owner,"story","",-1);var result=new ArrayList<String>();CampaignVotes.open(f.a,"老人怎么办？",List.of(new CampaignVotes.Option("save","救人"),new CampaignVotes.Option("leave","离开")),result::add);
        var token=CampaignVotes.view(f.owner).get("token").getAsString();CampaignVotes.cast(f.owner,token,"save");h.assertTrue(result.isEmpty(),"one vote cannot prematurely run the next story node");CampaignVotes.cast(f.guest,token,"leave");h.assertTrue(CampaignVotes.view(f.owner).get("round").getAsInt()==1,"first tie starts a second vote");CampaignVotes.cast(f.owner,token,"save");CampaignVotes.cast(f.guest,token,"leave");h.assertTrue(CampaignVotes.view(f.owner).get("round").getAsInt()==2,"second tie delegates only tied options to host");try{CampaignVotes.cast(f.guest,token,"leave");throw new AssertionError("guest broke tie");}catch(IllegalArgumentException expected){}CampaignVotes.cast(f.owner,token,"save");h.assertTrue(result.equals(List.of("save"))&&!CampaignVotes.active(f.a),"single final result commits exactly once");
    }h.succeed();}
    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void adoptingMoreAdvancedWorldKeepsOldRouteAndCanBeAcceptedAfterSettlement(GameTestHelper h)throws Exception{try(var f=new Fixture(h)){
        f.join(f.a,f.owner);CampaignRuntime.launch(f.owner,"story","",-1);var old=CampaignRuntime.session(f.a);String oldId=CampaignRuntime.str(old.saves.get(f.owner.getUUID()),"id");f.a.phase=ZombiesGamePhase.VICTORY;CampaignRuntime.victory(f.a);CampaignRuntime.end(f.owner);f.a.leave(f.owner);
        f.join(f.a,f.guest);CampaignRuntime.launch(f.guest,"story","",-1);f.a.phase=ZombiesGamePhase.INTERMISSION;CampaignRuntime.event(f.a,"rescue",f.guest);CampaignRuntime.tick(f.a);var host=CampaignRuntime.session(f.a);String hostId=CampaignRuntime.str(host.saves.get(f.guest.getUUID()),"id");f.a.phase=ZombiesGamePhase.VICTORY;CampaignRuntime.victory(f.a);CampaignRuntime.end(f.guest);
        f.join(f.a,f.owner);CampaignRuntime.launch(f.guest,"story",hostId,-1);var adopted=CampaignRuntime.session(f.b);h.assertTrue(CampaignRuntime.state(f.b,"elder","saved")&&CampaignRuntime.item(f.b,f.owner,"letter",1),"joining advanced route inherits its coherent choices and currently owned quest items");
        String newId=CampaignRuntime.str(adopted.saves.get(f.owner.getUUID()),"id");h.assertTrue(!newId.equals(oldId),"incompatible history cannot overwrite original route");f.b.phase=ZombiesGamePhase.VICTORY;CampaignRuntime.victory(f.b);CampaignRuntime.keep(f.owner,true);
        var store=CampaignStore.get(f.server);h.assertTrue(CampaignStore.tip(store.save(f.owner.getUUID(),newId)).get("finished").getAsBoolean(),"accepting after victory retains the just completed result");h.assertTrue(!CampaignRuntime.object(CampaignStore.tip(store.save(f.owner.getUUID(),oldId)),"world").toString().contains("saved"),"original elder-dead route remains unchanged");
    }h.succeed();}
    @GameTest(template="empty",batch="campaign",timeoutTicks=200)
    public static void realStartupAndLateJoinUseFreshEquipmentAndRoundBank(GameTestHelper h)throws Exception{try(var f=new Fixture(h)){
        var pos=h.absolutePos(new BlockPos(1,2,1));var real=new ZombiesMap(h.getLevel(),"real-campaign-"+UUID.randomUUID().toString().substring(0,8),new AreaData(pos.offset(-1,-1,-1),pos.offset(8,5,8)));FPSMCore.getInstance().registerMap(real.getGameType(),real);
        try{var objects=new com.cdp.codpattern.app.zombies.map.ZombiesMapObjects(List.of(new com.cdp.codpattern.app.zombies.map.object.ZombiesInitialSpawnData(h.getLevel().dimension(),pos,0,0)),List.of(new com.cdp.codpattern.app.zombies.map.object.ZombiesZombieSpawnData("zombies",0,1,h.getLevel().dimension(),pos.offset(3,0,0),0,0)),List.of(),List.of(),List.of(),List.of(),Optional.empty(),List.of(),List.of(),List.of(),List.of());real.applyObjects(objects);
            var wall=com.cdp.codpattern.config.zombies.ZombiesWeaponWallConfig.defaults();wall.setRarityPools(List.of(wall.getRarityPools().get(0)));CampaignStore.atomic(com.cdp.codpattern.config.zombies.ZombiesConfigPaths.zombiesMapWeaponWall(f.server,real.getMapName()),CampaignStore.GSON.toJson(wall));
            var def=CampaignStore.GSON.toJsonTree(CampaignStore.definition(f.server)).getAsJsonObject();def.getAsJsonArray("campaigns").get(0).getAsJsonObject().getAsJsonArray("chapters").get(0).getAsJsonObject().addProperty("map",real.getMapName());CampaignStore.atomic(f.definition,def.toString());
            try{CampaignRuntime.launch(f.owner,"story","",-1);}catch(RuntimeException e){throw new AssertionError("real startup: "+f.messages,e);}h.assertTrue(real.currentPhase().isRoundRunning()&&com.tacz.guns.api.item.IGun.getIGunOrNull(f.owner.getInventory().getItem(0))!=null,"real full startup issues TaCZ starter kit and enters round");
            var session=CampaignRuntime.session(real);String id=CampaignRuntime.str(session.saves.get(f.owner.getUUID()),"id");real.coopRuntime().bank.creditReward(900);real.coopPlayerState(f.owner.getUUID()).addPoints(500);f.owner.getInventory().setItem(7,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,12));
            real.campaignLateJoin(f.guest);CampaignRuntime.tick(real);h.assertTrue(real.checkGameHasPlayer(f.guest.getUUID())&&com.tacz.guns.api.item.IGun.getIGunOrNull(f.guest.getInventory().getItem(0))!=null&&session.inventories.containsKey(f.guest.getUUID()),"mid-round join receives actual starter equipment and personal story warehouse");
            CampaignRuntime.end(f.owner);real.leave(f.guest);CampaignRuntime.launch(f.owner,"story",id,-1);h.assertTrue(real.coopRuntime().bank.balance()==0&&real.coopPlayerState(f.owner.getUUID()).points()==0&&!f.owner.getInventory().contains(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)),"resuming starts with fresh round equipment and points; bank and spare items never persist");
            CampaignRuntime.end(f.owner);
        }finally{System.out.println("REAL CAMPAIGN STARTUP MESSAGES "+f.messages);real.resetGame();FPSMCore.getInstance().unregisterMap(real);}
    }h.succeed();}
    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void failedCheckpointPreservesPreviousSaveAndRetryCommitsAllItems(GameTestHelper h)throws Exception{try(var f=new Fixture(h)){
        f.join(f.a,f.owner);CampaignRuntime.launch(f.owner,"story","",-1);var s=CampaignRuntime.session(f.a);var store=CampaignStore.get(f.server);String save=CampaignRuntime.str(s.saves.get(f.owner.getUUID()),"id");String old=store.snapshot().toString();
        var path=f.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/codpattern_zombies/campaign-state.json");var backup=path.resolveSibling("campaign-write-failure-backup.json");Files.move(path,backup);Files.createDirectory(path);
        try{f.a.phase=ZombiesGamePhase.INTERMISSION;CampaignRuntime.event(f.a,"rescue",f.owner);CampaignRuntime.tick(f.a);h.assertTrue(s.failed&&store.snapshot().toString().equals(old),"real write failure freezes story and leaves every committed account unchanged");}finally{Files.delete(path);Files.move(backup,path);}
        CampaignRuntime.retry(f.owner);var loaded=new CampaignStore(path);h.assertTrue(!s.failed&&CampaignStore.tip(loaded.save(f.owner.getUUID(),save)).getAsJsonObject("items").get("letter").getAsInt()==1,"retry commits inventory and story together and survives fresh disk load");
    }h.succeed();}
    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void segmentedDialogueHandsOffToVoteAndSelfReturnWaitsForInteraction(GameTestHelper h)throws Exception{try(var f=new Fixture(h)){
        f.join(f.a,f.owner);f.join(f.a,f.guest);CampaignRuntime.launch(f.owner,"story","",-1);var pos=h.absolutePos(new BlockPos(2,2,2));f.owner.moveTo(pos.getX(),pos.getY(),pos.getZ());f.guest.moveTo(pos.getX()+1,pos.getY(),pos.getZ());
        var entry=new ZombiesNpcGraph.Node("entry",ZombiesNpcGraph.Type.ENTRY,0,0,null,null,0,"choose","");var end=new ZombiesNpcGraph.Node("end",ZombiesNpcGraph.Type.END,0,0,null,null,0,"","");
        var choose=new ZombiesNpcGraph.Node("choose",ZombiesNpcGraph.Type.CHOICE,0,0,null,null,0,"","",true,"接下来怎么办？","",List.of(new ZombiesNpcGraph.Option("wait","先等等",null,"choose",true),new ZombiesNpcGraph.Option("go","出发",null,"end")),List.of(new ZombiesNpcGraph.Segment("先告诉你们一件事。","继续说。","")),true);
        var rule=new ZombiesNpcConfig.Rule("flow",ZombiesNpcConfig.Trigger.INTERACT,0,List.of(),false,List.of(),List.of(),0,"",1,new ZombiesNpcGraph(List.of(entry,choose,end)));var npc=new ZombiesNpcConfig.Npc("guide",new ZombiesNpcConfig.Appearance("Guide",null,ZombiesNpcConfig.Pose.STANDING,true,new ZombiesNpcConfig.Position(pos.getX(),pos.getY(),pos.getZ(),0)),"",List.of(rule),0);f.a.npcRuntime().start(new ZombiesNpcConfig(1,List.of(npc)),false);
        var entity=h.getLevel().getEntitiesOfClass(ZombiesNpcEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2)).stream().filter(n->n.getName().getString().equals("Guide")).findFirst().orElseThrow();f.a.npcRuntime().interact(f.owner,"guide",entity);var view=f.a.npcRuntime().dialogue(f.owner);h.assertTrue(view!=null,"initial personal segment opens");ZombiesNpcRuntime.selectDialogue(f.owner,view.token(),"segment_next");h.assertTrue(CampaignVotes.active(f.a)&&f.a.npcRuntime().dialogue(f.owner)==null,"last segment closes old personal view and starts shared vote");var token=CampaignVotes.view(f.owner).get("token").getAsString();CampaignVotes.cast(f.owner,token,"wait");CampaignVotes.cast(f.guest,token,"wait");f.a.npcRuntime().tick(1);h.assertTrue(!CampaignVotes.active(f.a),"self return consumes a new interaction rather than reopening voting automatically");
    }h.succeed();}
    @GameTest(template="empty",batch="campaign",timeoutTicks=100)
    public static void campaignDefinitionRejectsDanglingReferencesAndUnknownVersions(GameTestHelper h){
        try{CampaignStore.GSON.fromJson("{\"version\":2}",CampaignDefinition.class);throw new AssertionError("unknown version accepted");}catch(RuntimeException expected){}
        try{CampaignStore.GSON.fromJson("{\"version\":1,\"events\":[{\"id\":\"bad\",\"chapter\":\"missing\"}]}",CampaignDefinition.class);throw new AssertionError("dangling chapter accepted");}catch(RuntimeException expected){}
        h.assertTrue(CampaignStore.GSON.fromJson("{\"version\":1}",CampaignDefinition.class).campaigns().isEmpty(),"missing optional catalog lists normalize safely");h.succeed();
    }
    private static final class Fixture implements AutoCloseable {
        final List<String> messages=new ArrayList<>();final MinecraftMap a,b;final ServerPlayer owner,guest;final net.minecraft.server.MinecraftServer server;final Path definition;final String previous;final Map<UUID,ServerPlayer> registry;final List<ServerPlayer> players;
        @SuppressWarnings("unchecked") Fixture(GameTestHelper h)throws Exception{
            server=h.getLevel().getServer();definition=CampaignStore.definitions(server);previous=Files.exists(definition)?Files.readString(definition):null;
            var origin=h.absolutePos(new BlockPos(1,2,1));a=new MinecraftMap(h.getLevel(),"campaign-a-"+UUID.randomUUID().toString().substring(0,8),origin);b=new MinecraftMap(h.getLevel(),"campaign-b-"+UUID.randomUUID().toString().substring(0,8),origin.offset(32,0,0));
            FPSMCore.getInstance().registerMap(a.getGameType(),a);FPSMCore.getInstance().registerMap(b.getGameType(),b);
            Map<UUID,ServerPlayer> found=null;for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(field.getGenericType().getTypeName().contains("java.util.UUID")&&field.getGenericType().getTypeName().contains("ServerPlayer")){field.setAccessible(true);found=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());break;}registry=Objects.requireNonNull(found,"player UUID registry");
            List<ServerPlayer> list=null;for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(field.getGenericType().getTypeName().startsWith("java.util.List<")&&field.getGenericType().getTypeName().contains("ServerPlayer")){field.setAccessible(true);list=(List<ServerPlayer>)field.get(server.getPlayerList());break;}players=Objects.requireNonNull(list,"mutable player roster");
            owner=player(h,"CampOwner");guest=player(h,"CampGuest");
            String json="{\"version\":1,\"campaigns\":[{\"id\":\"story\",\"name\":\"测试战役\",\"chapters\":[{\"id\":\"a\",\"name\":\"第一关\",\"map\":\""+a.getMapName()+"\",\"reward\":\"trophy\"},{\"id\":\"b\",\"name\":\"第二关\",\"map\":\""+b.getMapName()+"\"}]}],\"states\":[{\"id\":\"elder\",\"name\":\"老人命运\"}],\"items\":[{\"id\":\"letter\",\"name\":\"信件\"}],\"collections\":[{\"id\":\"trophy\",\"name\":\"通关纪念\"}],\"events\":[{\"id\":\"rescue\",\"chapter\":\"a\",\"effects\":[{\"operation\":\"STATE\",\"key\":\"elder\",\"value\":\"saved\"},{\"operation\":\"GIVE\",\"key\":\"letter\",\"count\":1,\"everyone\":true},{\"operation\":\"CHECKPOINT\",\"key\":\"shelter\"}]},{\"id\":\"impossible\",\"chapter\":\"a\",\"effects\":[{\"operation\":\"STATE\",\"key\":\"elder\",\"value\":\"dead\"},{\"operation\":\"TAKE\",\"key\":\"letter\",\"count\":2,\"everyone\":true}]},{\"id\":\"spend\",\"chapter\":\"b\",\"effects\":[{\"operation\":\"TAKE\",\"key\":\"letter\",\"count\":1},{\"operation\":\"CHECKPOINT\",\"key\":\"spent\"}]}]}";
            CampaignStore.atomic(definition,CampaignStore.GSON.toJson(CampaignStore.GSON.fromJson(json,CampaignDefinition.class)));
        }
        ServerPlayer player(GameTestHelper h,String name){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),name)){@Override public void sendSystemMessage(net.minecraft.network.chat.Component c){messages.add(c.getString());}@Override public boolean hasPermissions(int level){return level<=2;}};p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p){@Override public void send(Packet<?> packet){}@Override public void send(Packet<?> packet,net.minecraft.network.PacketSendListener listener){}};registry.put(p.getUUID(),p);players.add(p);return p;}
        void setOnline(ServerPlayer p,boolean online){if(online){registry.put(p.getUUID(),p);if(!players.contains(p))players.add(p);}else{registry.remove(p.getUUID());players.remove(p);}}
        void join(MinecraftMap m,ServerPlayer p){m.campaignJoin(p);}
        public void close()throws Exception{CampaignVotes.clear(a);CampaignVotes.clear(b);a.resetGame();b.resetGame();setOnline(owner,false);setOnline(guest,false);FPSMCore.getInstance().unregisterMap(a);FPSMCore.getInstance().unregisterMap(b);if(previous==null)Files.deleteIfExists(definition);else CampaignStore.atomic(definition,previous);CampaignRuntime.stop(server);}
    }
    private static final class MinecraftMap extends ZombiesMap {
        ZombiesGamePhase phase=ZombiesGamePhase.WAITING;int wave=1;
        MinecraftMap(net.minecraft.server.level.ServerLevel level,String name,BlockPos origin){super(level,name,new AreaData(origin.offset(-1,-1,-1),origin.offset(8,5,8)));}
        @Override public ZombiesGamePhase currentPhase(){return phase;}
        @Override public int npcWave(){return wave;}
        @Override public void campaignWave(int value){wave=value;}
        @Override public void campaignStartGame(){startGame();}
        @Override public void startGame(){CampaignRuntime.prepare(this);phase=ZombiesGamePhase.OPENING_COUNTDOWN;npcRuntime().start(ZombiesNpcConfig.empty(),false);scenarioRuntime().start(com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.empty());CampaignRuntime.started(this);}
        @Override public void resetGame(){CampaignRuntime.cleanup(this);scenarioRuntime().reset();npcRuntime().reset();phase=ZombiesGamePhase.WAITING;}
        @Override public void tick(){}
        @Override public void sendNpcDialogue(net.minecraft.network.chat.Component text){}
    }
}
