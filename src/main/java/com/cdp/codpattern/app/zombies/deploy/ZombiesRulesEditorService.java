package com.cdp.codpattern.app.zombies.deploy;

import com.cdp.codpattern.app.zombies.npc.*;
import com.cdp.codpattern.config.zombies.*;
import com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry;
import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.google.gson.*;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper;
import com.phasetranscrystal.fpsmatch.common.item.zombies.ZombiesDeployTool;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Edits one existing map-owned rules file at a time; never rewrites unrelated files. */
public final class ZombiesRulesEditorService {
    public record Snapshot(String map,String kind,String revision,JsonObject data,List<String> guns,boolean editable,String error,List<String> rarityIds){}
    public static String filename(String kind){return switch(kind){case "barriers"->"barrier_groups.json";case "wall"->"weapon_wall.json";case "box"->"mystery_box.json";case "rules"->"weapon_rules.json";default->throw new IllegalArgumentException("未知配置类型");};}
    public static JsonObject defaults(String kind){return switch(kind){case "barriers"->JsonParser.parseString(ZombiesBarrierGroupsConfig.EMPTY_TEMPLATE).getAsJsonObject();case "wall"->ZombiesNpcRepository.GSON.toJsonTree(ZombiesWeaponWallConfig.defaults()).getAsJsonObject();case "box"->ZombiesNpcRepository.GSON.toJsonTree(ZombiesMysteryBoxConfig.defaults()).getAsJsonObject();case "rules"->ZombiesNpcRepository.GSON.toJsonTree(ZombiesWeaponRulesConfig.defaults()).getAsJsonObject();default->throw new IllegalArgumentException("未知配置类型");};}
    public static Snapshot perform(ServerPlayer player,String name,String operation,String payload){
        String kind="";
        try{
            if(!(player.getMainHandItem().getItem() instanceof ZombiesDeployTool)||!ToolAccessHelper.hasAdminAccess(player))throw new IllegalArgumentException("需要管理员权限并手持部署工具");
            var request=JsonParser.parseString(payload).getAsJsonObject();kind=request.get("kind").getAsString();
            var map=FpsMatchMapRegistry.listMaps(com.cdp.codpattern.app.match.BuiltInGameModes.ZOMBIES).stream().filter(m->m instanceof ZombiesMap&&m.getMapName().equals(name)).map(m->(ZombiesMap)m).findFirst().orElseThrow(()->new IllegalArgumentException("找不到关卡"));
            var path=ZombiesConfigPaths.zombiesMapRulesRoot(player.getServer(),name).resolve(filename(kind));
            com.cdp.codpattern.config.storage.StorageFiles.checkPath(path);
            String original=Files.exists(path)?Files.readString(path):"<missing>";
            var data=original.equals("<missing>")?defaults(kind):JsonParser.parseString(original).getAsJsonObject();
            if(kind.equals("box")&&data.has("rarities")&&!data.has("rarityPools"))data.add("rarityPools",data.remove("rarities"));
            if(kind.equals("rules")&&data.has("upgrades")){var u=data.getAsJsonObject("upgrades");if(u.has("maxLevel")&&!u.has("maxUpgradeLevel"))u.add("maxUpgradeLevel",u.remove("maxLevel"));}
            complete(kind,data);
            var rarityPath=path.resolveSibling("weapon_rules.json");var rules=kind.equals("rules")?data:!kind.equals("barriers")&&Files.exists(rarityPath)?JsonParser.parseString(Files.readString(rarityPath)).getAsJsonObject():defaults("rules");
            var rarityIds=rules.getAsJsonArray("rarities").asList().stream().map(v->v.getAsJsonObject().get("id").getAsString()).toList();
            var guns=com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex().stream().map(e->e.getKey().toString()).sorted().toList();
            boolean editable=map.currentPhase()==ZombiesGamePhase.WAITING;
            if(operation.equals("config_save")){
                if(!editable)throw new IllegalArgumentException("对局进行中不能修改规则");
                if(!revision(original).equals(request.get("revision").getAsString()))throw new IllegalArgumentException("配置已被修改，请重新读取后再编辑");
                data=request.getAsJsonObject("data");validate(kind,data,new HashSet<>(guns));
                if(kind.equals("wall")||kind.equals("box"))for(var pool:data.getAsJsonArray("rarityPools"))if(!rarityIds.contains(pool.getAsJsonObject().get(kind.equals("wall")?"rarityId":"id").getAsString()))throw new IllegalArgumentException("请先在品阶与强化中配置该品阶的伤害倍率");
                if(kind.equals("rules")){
                    var keep=data.getAsJsonArray("rarities").asList().stream().map(v->v.getAsJsonObject().get("id").getAsString()).collect(java.util.stream.Collectors.toSet());
                    for(String other:List.of("wall","box")){var file=path.resolveSibling(filename(other));var existing=Files.exists(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject():defaults(other);var pools=existing.has("rarityPools")?existing.getAsJsonArray("rarityPools"):existing.getAsJsonArray("rarities");if(pools!=null)for(var value:pools){String id=value.getAsJsonObject().get(other.equals("wall")?"rarityId":"id").getAsString();if(!keep.contains(id))throw new IllegalArgumentException("品阶 "+id+" 仍被 "+filename(other)+" 引用，请先移除该武器池品阶");}}
                }
                String encoded=ZombiesNpcRepository.GSON.toJson(data);
                ZombiesNpcEditorWire.checkResponse(ZombiesNpcRepository.GSON.toJson(new Snapshot(name,kind,revision(encoded),data,guns,true,"",rarityIds)));
                save(path,encoded);original=encoded;
            }else if(!operation.equals("config_load"))throw new IllegalArgumentException("未知操作");
            var reply=new Snapshot(name,kind,revision(original),data,guns,editable,"",rarityIds);ZombiesNpcEditorWire.checkResponse(ZombiesNpcRepository.GSON.toJson(reply));return reply;
        }catch(Exception e){return new Snapshot(name,kind,"",null,List.of(),false,e.getMessage()==null?e.toString():e.getMessage(),List.of());}
    }
    public static void complete(String kind,JsonObject raw){
        if(kind.equals("barriers")){if(!raw.has("schemaVersion")||raw.get("schemaVersion").getAsInt()!=2||!raw.has("groups")||!raw.get("groups").isJsonObject())throw new IllegalArgumentException("屏障配置需要 schemaVersion=2 的 groups 格式");return;}
        if(!raw.has("schemaVersion")||raw.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("武器配置需要 schemaVersion=1");
        Object typed=switch(kind){case "wall"->ZombiesNpcRepository.GSON.fromJson(raw,ZombiesWeaponWallConfig.class);case "box"->ZombiesNpcRepository.GSON.fromJson(raw,ZombiesMysteryBoxConfig.class);case "rules"->ZombiesNpcRepository.GSON.fromJson(raw,ZombiesWeaponRulesConfig.class);default->throw new IllegalArgumentException();};
        fill(raw,ZombiesNpcRepository.GSON.toJsonTree(typed));
    }
    private static void fill(JsonElement target,JsonElement defaults){if(target.isJsonObject()&&defaults.isJsonObject()){for(var e:defaults.getAsJsonObject().entrySet()){var object=target.getAsJsonObject();if(!object.has(e.getKey())||object.get(e.getKey()).isJsonNull())object.add(e.getKey(),e.getValue().deepCopy());else fill(object.get(e.getKey()),e.getValue());}}else if(target.isJsonArray()&&defaults.isJsonArray())for(int i=0;i<Math.min(target.getAsJsonArray().size(),defaults.getAsJsonArray().size());i++)fill(target.getAsJsonArray().get(i),defaults.getAsJsonArray().get(i));}
    public static void save(Path path,String text)throws java.io.IOException{
        com.cdp.codpattern.config.storage.StorageFiles.checkPath(path);Files.createDirectories(path.getParent());var temp=Files.createTempFile(path.getParent(),"rules-editor-",".tmp");
        try{Files.writeString(temp,text);try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
    public static String revision(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static double number(JsonObject o,String key,double min,boolean integer){
        if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isNumber())throw new IllegalArgumentException(key+" 需要填写数字");
        double n=o.get(key).getAsDouble();if(!Double.isFinite(n)||n<min||integer&&(n!=Math.floor(n)||n>Integer.MAX_VALUE))throw new IllegalArgumentException(key+" 数值无效");return n;
    }
    public static void validate(String kind,JsonObject data,Set<String> available){
        if(kind.equals("barriers")){
            var parsed=ZombiesBarrierGroupsConfig.parse(data.toString(),null);if(!parsed.errors().isEmpty())throw new IllegalArgumentException(String.join("；",parsed.errors()));
            for(var group:parsed.groups().values())for(var entry:group.entries().values())if(!entry.requiredItem().isBlank()&&com.cdp.codpattern.app.zombies.item.ZombiesRequiredItem.parse(entry.requiredItem()).stack().isEmpty())throw new IllegalArgumentException("钥匙物品或 NBT 无效");return;
        }
        if(number(data,"schemaVersion",1,true)!=1)throw new IllegalArgumentException("仅支持 schemaVersion=1");
        var ids=new HashSet<String>();var array=data.getAsJsonArray(kind.equals("rules")?"rarities":"rarityPools");if(array==null||array.isEmpty()||array.size()>64)throw new IllegalArgumentException("需要 1 至 64 个品阶");
        if(kind.equals("box"))number(data,"cost",0,true);
        if(!kind.equals("rules"))number(data,"refreshIntervalWaves",1,true);
        for(var value:array){var pool=value.getAsJsonObject();String id=pool.get(kind.equals("wall")?"rarityId":"id").getAsString();if(!id.matches("[a-z0-9_-]{1,64}")||!ids.add(id))throw new IllegalArgumentException("品阶编号无效或重复");
            if(kind.equals("rules")){number(pool,"damageMultiplier",Double.MIN_VALUE,false);continue;}
            number(pool,"initialWeight",0,false);number(pool,"weightDeltaPerRefresh",-Double.MAX_VALUE,false);double min=number(pool,"minWeight",0,false);if(number(pool,"maxWeight",0,false)<min)throw new IllegalArgumentException("最大权重不能小于最小权重");if(kind.equals("wall"))number(pool,"price",0,true);
            var guns=pool.getAsJsonArray("guns");if(guns==null||guns.size()>4096)throw new IllegalArgumentException("枪械条目过多");var seen=new HashSet<String>();
            for(var raw:guns){var gun=raw.getAsJsonObject();String gunId=gun.get("gunId").getAsString();if(!available.contains(gunId)||!seen.add(gunId))throw new IllegalArgumentException("枪械未加载或重复："+gunId);number(gun,"weight",0,false);}
        }
        if(kind.equals("rules")){var upgrades=data.getAsJsonObject("upgrades");int max=(int)number(upgrades,"maxUpgradeLevel",0,true);if(max>100)throw new IllegalArgumentException("强化级别最多 100");var levels=upgrades.getAsJsonObject("levels");for(int i=1;i<=max;i++){var level=levels.getAsJsonObject(Integer.toString(i));if(level==null)throw new IllegalArgumentException("请设置强化级别 "+i);number(level,"price",0,true);number(level,"damageMultiplier",Double.MIN_VALUE,false);}}
    }
    private ZombiesRulesEditorService(){}
}
