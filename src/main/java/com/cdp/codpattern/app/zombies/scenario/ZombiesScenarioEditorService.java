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
    public record Snapshot(String map,String revision,ZombiesScenarioConfig config,List<String> rules,List<String> mobs,boolean editable,String error,ZombiesScenarioConfig.Scene scene) {
        public Snapshot(String map,String revision,ZombiesScenarioConfig config,List<String> rules,List<String> mobs,boolean editable,String error){this(map,revision,config,rules,mobs,editable,error,null);}
    }
    private ZombiesScenarioEditorService() { }
    public static Snapshot perform(ServerPlayer player,String name,String operation,String payload){
        try{
            if(!(player.getMainHandItem().getItem() instanceof ZombiesDeployTool)||!ToolAccessHelper.hasAdminAccess(player))throw new IllegalArgumentException("需要管理员权限并手持僵尸部署工具");
            var map=FpsMatchMapRegistry.listMaps(com.cdp.codpattern.app.match.BuiltInGameModes.ZOMBIES).stream().filter(m->m instanceof ZombiesMap&&m.getMapName().equals(name)).map(m->(ZombiesMap)m).findFirst().orElseThrow(()->new IllegalArgumentException("找不到关卡"));
            if(operation.equals("capture")){
                if(map.currentPhase()!=ZombiesGamePhase.WAITING)throw new IllegalArgumentException("对局进行中不能读取场景");
                var request=JsonParser.parseString(payload).getAsJsonObject();
                var from=ZombiesNpcRepository.GSON.fromJson(request.get("from"),net.minecraft.core.BlockPos.class);
                var to=ZombiesNpcRepository.GSON.fromJson(request.get("to"),net.minecraft.core.BlockPos.class);
                var scene=captureScene(player,map,from,to);
                var response=new Snapshot(name,"",null,List.of(),List.of(),true,"",scene);
                ZombiesNpcEditorWire.checkResponse(ZombiesNpcRepository.GSON.toJson(response));return response;
            }
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
    static ZombiesScenarioConfig.Scene captureScene(ServerPlayer player,ZombiesMap map,net.minecraft.core.BlockPos from,net.minecraft.core.BlockPos to){
        if(from==null||to==null||player.serverLevel()!=map.getServerLevel())throw new IllegalArgumentException("请在关卡所在维度选择场景");
        var bounds=new ZombiesScenarioConfig.Region(from,to,"minecraft:air"); // Bounds are checked before reading or allocating cells.
        var min=new net.minecraft.core.BlockPos(Math.min(from.getX(),to.getX()),Math.min(from.getY(),to.getY()),Math.min(from.getZ(),to.getZ()));
        int sx=Math.abs(from.getX()-to.getX())+1,sy=Math.abs(from.getY()-to.getY())+1,sz=Math.abs(from.getZ()-to.getZ())+1;
        var palette=new ArrayList<String>();var indexes=new HashMap<String,Integer>();var blocks=new ArrayList<Integer>();
        for(int y=0;y<sy;y++)for(int z=0;z<sz;z++)for(int x=0;x<sx;x++){
            var pos=min.offset(x,y,z);var level=player.serverLevel();
            if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>256*256||!level.hasChunkAt(pos))throw new IllegalArgumentException("源场景必须在玩家附近 256 格内的已加载区域");
            var state=level.getBlockState(pos);
            if(!ZombiesNpcRuntime.supportsStoryBlock(state))throw new IllegalArgumentException("场景包含暂不支持的方块："+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())+"，位置 "+pos.toShortString());
            String specification=net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state);
            Integer index=indexes.get(specification);if(index==null){index=palette.size();indexes.put(specification,index);palette.add(specification);}blocks.add(index);
        }
        return new ZombiesScenarioConfig.Scene(sx,sy,sz,palette,blocks);
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
