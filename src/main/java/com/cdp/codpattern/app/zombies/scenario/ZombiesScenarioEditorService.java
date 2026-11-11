package com.cdp.codpattern.app.zombies.scenario;

import com.cdp.codpattern.app.zombies.npc.*;
import com.cdp.codpattern.app.zombies.service.*;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry;
import com.cdp.codpattern.config.zombies.ZombiesConfigPaths;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper;
import com.phasetranscrystal.fpsmatch.common.item.zombies.ZombiesDeployTool;
import java.nio.file.*;
import java.util.*;

public final class ZombiesScenarioEditorService {
    public record Snapshot(String map,String revision,ZombiesScenarioConfig config,List<String> rules,List<String> mobs,boolean editable,String error) { }
    private ZombiesScenarioEditorService() { }
    public static Snapshot perform(ServerPlayer player,String name,String operation,String payload){
        try{
            if(!(player.getMainHandItem().getItem() instanceof ZombiesDeployTool)||!ToolAccessHelper.hasAdminAccess(player))throw new IllegalArgumentException("需要管理员权限并手持僵尸部署工具");
            var map=FpsMatchMapRegistry.listMaps(com.cdp.codpattern.app.match.BuiltInGameModes.ZOMBIES).stream().filter(m->m instanceof ZombiesMap&&m.getMapName().equals(name)).map(m->(ZombiesMap)m).findFirst().orElseThrow(()->new IllegalArgumentException("找不到关卡"));
            var path=ZombiesScenarioRepository.path(player.getServer(),name);var current=effective(player.getServer(),name);boolean editable=map.currentPhase()==ZombiesGamePhase.WAITING;
            if(operation.equals("save")){
                if(!editable)throw new IllegalArgumentException("对局进行中不能保存关卡剧情");
                var request=JsonParser.parseString(payload).getAsJsonObject();if(!revision(current).equals(request.get("revision").getAsString()))throw new IllegalArgumentException("配置已被其他部署者修改，请重新读取后再编辑");
                var candidate=ZombiesNpcRepository.GSON.fromJson(request.get("config"),ZombiesScenarioConfig.class);
                var npcs=ZombiesNpcRepository.load(ZombiesNpcRepository.path(player.getServer(),name));map.scenarioRuntime().validate(candidate,npcs);
                ZombiesNpcEditorWire.checkResponse(ZombiesNpcRepository.GSON.toJson(snapshot(name,candidate,npcs,true)));
                ZombiesScenarioRepository.save(path,candidate);current=candidate;
            }else if(!operation.equals("load"))throw new IllegalArgumentException("未知操作");
            return snapshot(name,current,ZombiesNpcRepository.load(ZombiesNpcRepository.path(player.getServer(),name)),editable);
        }catch(Exception e){return new Snapshot(name,"",null,List.of(),List.of(),false,e.getMessage()==null?e.toString():e.getMessage());}
    }
    public static ZombiesScenarioConfig effective(net.minecraft.server.MinecraftServer server,String map){
        var path=ZombiesScenarioRepository.path(server,map);var current=ZombiesScenarioRepository.load(path);
        var waves=current.waves();if(waves==null){var loaded=new ZombiesWaveConfigRepository(ZombiesConfigPaths.zombiesMapWaves(server,map)).load();if(!loaded.isValid())throw new IllegalArgumentException(loaded.getIssues().toString());
            waves=loaded.getWaves().stream().map(w->{var o=ZombiesNpcRepository.GSON.toJsonTree(w).getAsJsonObject();o.addProperty("wave",w.getWave());return o;}).toList();}
        var dialogues=current.dialogues();if(!Files.exists(path)){var texts=new ZombiesWaveTextRepository(ZombiesConfigPaths.zombiesMapWaveText(server,map)).load();if(!texts.issues().isEmpty())throw new IllegalArgumentException(texts.issues().toString());
            dialogues=texts.definitions().stream().map(d->{var lines=new ArrayList<ZombiesScenarioConfig.Line>();
                for(int i=0;i<d.messages().size();i++){var m=d.messages().get(i);double seconds=i+1<d.messages().size()?d.messages().get(i+1).delayTicks()/20.0:1;lines.add(new ZombiesScenarioConfig.Line("",m.text(),seconds));}return new ZombiesScenarioConfig.Dialogue(d.wave(),lines,d.messages().isEmpty()?0:d.messages().get(0).delayTicks()/20.0);}).toList();}
        return new ZombiesScenarioConfig(1,waves,current.speakers(),dialogues,current.events());
    }
    private static Snapshot snapshot(String name,ZombiesScenarioConfig config,ZombiesNpcConfig npcs,boolean editable){var rules=new ArrayList<String>();for(var n:npcs.npcs())for(var r:n.rules())rules.add(n.id()+"."+r.id());return new Snapshot(name,revision(config),config,rules,ZombiesWaveValidator.supportedEntityIds(),editable,"");}
    public static String revision(ZombiesScenarioConfig c){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(ZombiesNpcRepository.GSON.toJson(c).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
