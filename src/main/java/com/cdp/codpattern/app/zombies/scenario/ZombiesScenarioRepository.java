package com.cdp.codpattern.app.zombies.scenario;

import com.cdp.codpattern.app.zombies.npc.ZombiesNpcRepository;
import com.cdp.codpattern.config.zombies.ZombiesConfigPaths;
import net.minecraft.server.MinecraftServer;
import java.nio.file.*;
import java.io.*;

/** One atomic file commits wave overrides, cast, dialogue and events together. Legacy files remain readable. */
public final class ZombiesScenarioRepository {
    private ZombiesScenarioRepository() { }
    public static Path path(MinecraftServer server,String map){return ZombiesConfigPaths.zombiesMapRulesRoot(server,map).resolve("scenario.json");}
    public static ZombiesScenarioConfig load(Path path){try{if(!Files.exists(path))return ZombiesScenarioConfig.empty();var c=ZombiesNpcRepository.GSON.fromJson(Files.readString(path),ZombiesScenarioConfig.class);if(c==null)throw new IllegalArgumentException("关卡剧情文件为空");return c;}catch(IOException e){throw new UncheckedIOException(e);}}
    public static void save(Path path,ZombiesScenarioConfig config)throws IOException{
        Files.createDirectories(path.getParent());var tmp=Files.createTempFile(path.getParent(),"scenario-",".tmp");try{Files.writeString(tmp,ZombiesNpcRepository.GSON.toJson(config));try{Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
    }
}
