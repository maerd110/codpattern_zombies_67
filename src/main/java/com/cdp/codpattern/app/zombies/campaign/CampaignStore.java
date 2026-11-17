package com.cdp.codpattern.app.zombies.campaign;

import com.google.gson.*;
import com.cdp.codpattern.app.zombies.npc.ZombiesNpcRepository;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** A single copy-on-write commit includes every participant and account reward. No partially updated parties. */
public final class CampaignStore {
    public static final Gson GSON=ZombiesNpcRepository.GSON;
    private static final Map<MinecraftServer,CampaignStore> STORES=new WeakHashMap<>();
    private final Path file;
    private JsonObject state;
    public static CampaignStore get(MinecraftServer server){return STORES.computeIfAbsent(server,s->new CampaignStore(s.getWorldPath(LevelResource.ROOT).resolve("data/codpattern_zombies/campaign-state.json")));}
    public static Path definitions(MinecraftServer server){return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig/codpattern_zombies/campaigns.json");}
    public static CampaignDefinition definition(MinecraftServer server){CampaignAuthoringTransaction.recover(server);var file=definitions(server);try{return Files.exists(file)?Objects.requireNonNull(GSON.fromJson(Files.readString(file),CampaignDefinition.class)):CampaignDefinition.empty();}catch(IOException e){throw new UncheckedIOException(e);}}
    public CampaignStore(Path file){this.file=file;try{state=Files.exists(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject():initial();if(state.get("version").getAsInt()!=1||!state.has("accounts"))throw new IllegalArgumentException("未知存档版本，停止读取以保护旧档");}catch(IOException e){throw new UncheckedIOException(e);}}
    private static JsonObject initial(){var s=new JsonObject();s.addProperty("version",1);s.add("accounts",new JsonObject());return s;}
    public JsonObject snapshot(){return state.deepCopy();}
    public void transaction(Consumer<JsonObject> change){var next=state.deepCopy();change.accept(next);atomic(file,GSON.toJson(next));state=next.deepCopy();}
    public static JsonObject account(JsonObject root,UUID id){var accounts=root.getAsJsonObject("accounts");if(!accounts.has(id.toString())){var a=new JsonObject();a.add("saves",new JsonObject());a.add("collections",new JsonObject());a.add("completed",new JsonObject());a.addProperty("name",id.toString());a.addProperty("public",true);accounts.add(id.toString(),a);}return accounts.getAsJsonObject(id.toString());}
    public JsonObject account(UUID id){var accounts=state.getAsJsonObject("accounts");return accounts.has(id.toString())?accounts.getAsJsonObject(id.toString()).deepCopy():account(initial(),id);}
    public JsonArray publicProfiles(){var list=new JsonArray();for(var entry:state.getAsJsonObject("accounts").entrySet()){var account=entry.getValue().getAsJsonObject();if(account.get("public").getAsBoolean()){var row=new JsonObject();row.addProperty("id",entry.getKey());row.add("name",account.get("name"));list.add(row);}}return list;}
    public JsonObject save(UUID owner,String save){var saves=account(owner).getAsJsonObject("saves");if(!saves.has(save))throw new IllegalArgumentException("存档不存在或不属于你");return saves.getAsJsonObject(save).deepCopy();}
    public static JsonObject tip(JsonObject save){var h=save.getAsJsonArray("history");if(h==null||h.isEmpty())throw new IllegalArgumentException("存档没有安全检查点");return h.get(h.size()-1).getAsJsonObject();}
    public static void atomic(Path path,String text){
        try{Files.createDirectories(path.getParent());com.cdp.codpattern.config.storage.StorageFiles.checkPath(path);var tmp=Files.createTempFile(path.getParent(),"campaign-",".tmp");try{
            try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE)){var bytes=StandardCharsets.UTF_8.encode(text);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}
            // Fail closed on filesystems without atomic replacement; old checkpoint remains intact.
            Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(tmp);}}catch(IOException e){throw new UncheckedIOException("存档写入失败；保留上一次安全检查点",e);}
    }
    public static String revision(Object value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(GSON.toJson(value).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public static void forget(MinecraftServer server){STORES.remove(server);}
}
