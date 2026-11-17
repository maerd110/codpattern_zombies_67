package com.cdp.codpattern.app.zombies.campaign;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import com.google.gson.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Durable roll-forward journal: a campaign and its map programs are recovered as one authoring save. */
public final class CampaignAuthoringTransaction {
    private CampaignAuthoringTransaction(){}
    private static Path root(MinecraftServer server){return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();}
    private static Path journal(Path root){return root.resolve("serverconfig/codpattern_zombies/authoring-pending.json");}
    public static void recover(MinecraftServer server){recover(root(server));}
    public static void commit(MinecraftServer server,Map<Path,String> files){commit(root(server),files);}
    public static void commit(Path root,Map<Path,String> files){
        root=root.toAbsolutePath().normalize();recover(root);var writes=new JsonObject();
        for(var e:files.entrySet()){var target=e.getKey().toAbsolutePath().normalize();if(!target.startsWith(root.resolve("serverconfig")))throw new IllegalArgumentException("编辑目标必须位于当前世界的部署配置中");try{com.cdp.codpattern.config.storage.StorageFiles.checkPath(target);}catch(IOException error){throw new UncheckedIOException(error);}writes.addProperty(root.relativize(target).toString().replace('\\','/'),e.getValue());}
        if(writes.size()==0)return;CampaignStore.atomic(journal(root),writes.toString());recover(root);
    }
    public static void recover(Path root){root=root.toAbsolutePath().normalize();var journal=journal(root);if(!Files.exists(journal))return;
        try{var writes=JsonParser.parseString(Files.readString(journal)).getAsJsonObject();for(var e:writes.entrySet()){var target=root.resolve(e.getKey()).normalize();if(!target.startsWith(root.resolve("serverconfig"))||target.equals(journal))throw new IllegalArgumentException("部署事务文件路径无效");CampaignStore.atomic(target,e.getValue().getAsString());}Files.delete(journal);}
        catch(IOException e){throw new UncheckedIOException("部署保存尚未完成，原工作副本保留；恢复成功前暂停开局",e);}
    }
}
