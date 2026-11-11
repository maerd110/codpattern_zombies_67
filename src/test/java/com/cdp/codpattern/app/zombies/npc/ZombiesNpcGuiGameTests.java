package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.app.zombies.bootstrap.ZombiesItemRegister;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployDraft;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployToolService;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.common.packet.zombies.OpenZombiesDeployToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.zombies.ZombiesDeployToolActionC2SPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

@GameTestHolder("codpattern_npc")
@PrefixGameTestTemplate(false)
public final class ZombiesNpcGuiGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void scenarioEditorImportsSavesValidatesAndRejectsStaleWrites(GameTestHelper h)throws Exception{
        var origin=h.absolutePos(new BlockPos(1,2,1));String name="scenario-gui-"+UUID.randomUUID().toString().substring(0,8);
        var map=new ZombiesMap(h.getLevel(),name,new AreaData(origin.offset(-1,-1,-1),origin.offset(5,5,5))){boolean running;@Override public com.cdp.codpattern.app.zombies.model.ZombiesGamePhase currentPhase(){return running?com.cdp.codpattern.app.zombies.model.ZombiesGamePhase.WAVE_ACTIVE:super.currentPhase();}};
        var core=FPSMCore.getInstance();core.registerMap(map.getGameType(),map);var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"scenario-admin")){@Override public boolean hasPermissions(int level){return level<=2;}};player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ZombiesItemRegister.ZOMBIES_DEPLOY_TOOL.get()));
        var path=com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioRepository.path(h.getLevel().getServer(),name);
        try{
            var loaded=com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioEditorService.perform(player,name,"load","");h.assertTrue(loaded.error().isBlank()&&!loaded.config().waves().isEmpty(),"deployment editor imports existing wave configuration");
            var waveDir=com.cdp.codpattern.config.zombies.ZombiesConfigPaths.zombiesMapWaves(h.getLevel().getServer(),name);var original=Files.readString(waveDir.resolve("wave_001.json"));
            var working=ZombiesNpcRepository.GSON.toJsonTree(loaded.config()).getAsJsonObject();working.getAsJsonArray("waves").get(0).getAsJsonObject().addProperty("maxAlive",9);
            var speaker=new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Speaker("narrator","旁白","gold","white",true,false);working.getAsJsonArray("speakers").add(ZombiesNpcRepository.GSON.toJsonTree(speaker));
            var dialogue=new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Dialogue(1,List.of(new com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioConfig.Line("narrator","前方危险",3)),1);working.getAsJsonArray("dialogues").set(0,ZombiesNpcRepository.GSON.toJsonTree(dialogue));
            var req=new JsonObject();req.addProperty("revision",loaded.revision());req.add("config",working);
            var saved=com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioEditorService.perform(player,name,"save",req.toString());h.assertTrue(saved.error().isBlank()&&Files.exists(path),"graphical scenario save succeeds");
            var runtimeWaves=new com.cdp.codpattern.app.zombies.service.ZombiesWaveConfigRepository(waveDir).load();h.assertTrue(runtimeWaves.isValid()&&runtimeWaves.getWaves().get(0).getMaxAlive()==9,"startup consumes GUI wave overrides");h.assertTrue(Files.readString(waveDir.resolve("wave_001.json")).equals(original),"import keeps original wave files intact");
            var disk=com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioRepository.load(path);map.scenarioRuntime().start(disk);h.assertTrue(map.scenarioRuntime().ownsDialogue(2),"GUI scenario owns the full wave dialogue list so deleting an entry cannot resurrect legacy text");h.assertTrue(disk.dialogues().get(0).delaySeconds()==1&&disk.dialogues().get(0).lines().get(0).seconds()==3&&disk.speakers().get(0).bold(),"cast, format and relative dialogue timing persist");
            h.assertTrue(!com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioEditorService.perform(player,name,"save",req.toString()).error().isBlank(),"stale concurrent saves rejected");req.addProperty("revision",saved.revision());map.running=true;h.assertTrue(!com.cdp.codpattern.app.zombies.scenario.ZombiesScenarioEditorService.perform(player,name,"save",req.toString()).error().isBlank(),"running map rejects edits");map.running=false;
            var packet=new ZombiesNpcDialogueNetwork.EditorRequest(UUID.randomUUID(),name,"save",req.toString());var buf=new FriendlyByteBuf(Unpooled.buffer());try{packet.encode(buf);h.assertTrue(packet.equals(ZombiesNpcDialogueNetwork.EditorRequest.decode(buf)),"bounded editor request codec");}finally{buf.release();}
            var reply=new ZombiesNpcDialogueNetwork.EditorReply(packet.token(),ZombiesNpcRepository.GSON.toJson(saved));buf=new FriendlyByteBuf(Unpooled.buffer());try{reply.encode(buf);h.assertTrue(reply.equals(ZombiesNpcDialogueNetwork.EditorReply.decode(buf)),"bounded editor response codec");}finally{buf.release();}
        }finally{map.scenarioRuntime().reset();map.npcRuntime().reset();core.unregisterMap(map);Files.deleteIfExists(path);}h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void graphicalDraftPreservesRulesAndReferences(GameTestHelper helper) {
        var working = new ZombiesNpcEditorDraft(ZombiesNpcConfig.empty());
        var position = new ZombiesNpcConfig.Position(2.5, 64, 3.5, 90);
        JsonObject first = working.addNpc(position);
        JsonObject firstRule = working.addRule(first);
        JsonObject second = working.addNpc(position);
        JsonObject secondRule = working.addRule(second);
        secondRule.getAsJsonArray("requires").add("npc_1.rule_1");
        secondRule.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("target", "npc_1");
        working.renameNpc(first, "guide"); working.renameRule(first, firstRule, "delivery");
        helper.assertTrue(secondRule.getAsJsonArray("requires").get(0).getAsString().equals("guide.delivery"), "GUI rename updates prerequisites");
        helper.assertTrue(secondRule.getAsJsonArray("actions").get(0).getAsJsonObject().get("target").getAsString().equals("guide"), "GUI rename updates target NPC");
        helper.assertTrue(working.dirty(), "edits are local and marked unsaved");
        var config = working.validated();
        working.accept(config);
        helper.assertTrue(!working.dirty() && config.npcs().size() == 2, "save acceptance preserves both NPCs");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void graphicalSaveRejectsConflictsAndRoundTripsPackets(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        String name = "npc-gui-" + UUID.randomUUID().toString().substring(0, 8);
        var map = new ZombiesMap(level, name, new AreaData(origin.offset(-1, -1, -1), origin.offset(5, 5, 5)));
        var core = FPSMCore.getInstance(); core.registerMap(map.getGameType(), map);
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "npc-gui-admin")) {
            @Override public boolean hasPermissions(int level) { return level <= 2; }
        };
        var tool = new ItemStack(ZombiesItemRegister.ZOMBIES_DEPLOY_TOOL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        var deployment = new ZombiesDeployDraft(name, "INITIAL", -1, "MVP1_MINIMAL", java.util.Map.of());
        var path = ZombiesNpcRepository.path(level.getServer(), name);
        try {
            var initial = ZombiesNpcEditorService.perform(player, name, "load", "");
            helper.assertTrue(initial.error().isBlank() && initial.editable(), "GUI opens on the real tool");
            var working = new ZombiesNpcEditorDraft(initial.config());
            JsonObject npc = working.addNpc(new ZombiesNpcConfig.Position(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 90));
            var rule = working.addRule(npc);
            String dialogue = "任务对话\n".repeat(4000);
            rule.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("text", dialogue);
            ZombiesNpcGraphDraft.ensure(rule);
            var secondNpc=working.addNpc(new ZombiesNpcConfig.Position(origin.getX()+1.5,origin.getY(),origin.getZ()+.5,0));
            working.addRule(secondNpc);ZombiesNpcSharedCanvas.ensure(working);
            ZombiesNpcSharedCanvas.connect(working,"npc_1.rule_1","npc_2.rule_1");
            var gateNode=ZombiesNpcGraphDraft.add(rule,ZombiesNpcGraph.Type.BRANCH,250,150,null);
            var tests=gateNode.getAsJsonObject("condition").getAsJsonArray("tests");tests.get(0).getAsJsonObject().addProperty("value",3);
            tests.get(0).getAsJsonObject().addProperty("value",0);
            var done=ZombiesNpcGraphDraft.test();done.addProperty("kind","RULE_DONE");done.addProperty("reference","npc_2.rule_1");tests.add(done);
            ZombiesNpcGraphDraft.connect(rule,"node_1","next",gateNode.get("id").getAsString());ZombiesNpcGraphDraft.connect(rule,gateNode.get("id").getAsString(),"next","node_2");ZombiesNpcGraphDraft.connect(rule,gateNode.get("id").getAsString(),"onFalse","node_3");
            working.options().addProperty("debug",true);
            String request = working.request(initial.revision());
            var c2sBuffer = new FriendlyByteBuf(Unpooled.buffer());
            var originalPacket = ZombiesDeployToolActionC2SPacket.npcEditor(deployment, "save", request, 71);
            originalPacket.encode(c2sBuffer);
            var decoded = ZombiesDeployToolActionC2SPacket.decode(c2sBuffer);
            var check = new FriendlyByteBuf(Unpooled.buffer()); decoded.encode(check);
            helper.assertTrue(c2sBuffer.readerIndex() == c2sBuffer.writerIndex() && check.readableBytes() < 28000
                    && decoded.requestId() == 71 && io.netty.buffer.ByteBufUtil.equals(c2sBuffer, 0, check, 0, c2sBuffer.writerIndex()), "large dialogue survives bounded C2S transport");
            var saved = ZombiesNpcEditorService.perform(player, name, "save", request);
            helper.assertTrue(saved.error().isBlank() && Files.exists(path), "GUI saves actual NPC configuration " + saved.error());
            helper.assertTrue(saved.config().npcs().get(0).rules().get(0).graph().actions().get(0).text().equals(dialogue), "long graph dialogue preserved");
            helper.assertTrue(saved.config().npcs().get(1).rules().get(0).requires().equals(List.of("npc_1.rule_1")) && saved.config().npcs().get(0).rules().get(0).graph().canvasVersion()==1,"shared layout and cross-NPC dependency survive actual save packets");
            var persisted=ZombiesNpcRepository.load(path).npcs().get(0).rules().get(0).graph().nodes().stream().filter(n->n.type()==ZombiesNpcGraph.Type.BRANCH).findFirst().orElseThrow().condition();
            helper.assertTrue(persisted.tests().get(0).value()==0&&persisted.tests().get(1).reference().equals("npc_2.rule_1"),"GUI edit from wave three to zero reaches actual JSON without deleting the other rule condition");
            helper.assertTrue(saved.config().debug()&&ZombiesNpcRepository.load(path).debug(),"debug toggle survives actual GUI save and file reload");
            String before = Files.readString(path);
            var stale = ZombiesNpcEditorService.perform(player, name, "save", request);
            helper.assertTrue(stale.error().endsWith("conflict") && Files.readString(path).equals(before), "stale GUI must not overwrite newer data");
            var snapshot = ZombiesDeployToolService.instance().snapshot(player, tool, deployment, "", "ok", "").value().orElseThrow();
            var s2c = new OpenZombiesDeployToolScreenS2CPacket(snapshot, false, ZombiesDeployToolActionC2SPacket.Action.NPC_EDITOR, 71,
                    ZombiesNpcRepository.GSON.toJson(saved));
            var responseBuffer = new FriendlyByteBuf(Unpooled.buffer()); s2c.encode(responseBuffer);
            var response = OpenZombiesDeployToolScreenS2CPacket.decode(responseBuffer);
            helper.assertTrue(response.requestId() == 71 && response.npcEditorData().equals(s2c.npcEditorData()), "S2C snapshot and request ID round trip");
            var preview = ZombiesNpcEditorService.perform(player, name, "preview", "");
            helper.assertTrue(preview.error().isBlank(), "saved GUI configuration supports world preview");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var rejected = ZombiesNpcEditorService.perform(player, name, "save", working.request(saved.revision()));
            helper.assertTrue(rejected.error().endsWith("access") && Files.readString(path).equals(before), "server rechecks held tool");
            c2sBuffer.release(); check.release(); responseBuffer.release();
        } finally { map.npcRuntime().reset(); core.unregisterMap(map); }
        helper.succeed();
    }
}
