package com.cdp.codpattern.app.zombies.npc;

import net.minecraft.server.level.ServerPlayer;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Ordered, bounded admin-only uploads keep each client packet below Minecraft's payload limit. */
public final class ZombiesNpcEditorUpload {
    private static final Map<UUID,Assembly> ACTIVE=new HashMap<>();
    private static final class Assembly {
        final ZombiesNpcDialogueNetwork.EditorFragment first;final ByteArrayOutputStream bytes=new ByteArrayOutputStream();final long created=System.currentTimeMillis();int next;
        Assembly(ZombiesNpcDialogueNetwork.EditorFragment first){this.first=first;}
    }
    public static ZombiesNpcDialogueNetwork.EditorRequest accept(ServerPlayer player,ZombiesNpcDialogueNetwork.EditorFragment part){
        long now=System.currentTimeMillis();ACTIVE.values().removeIf(value->now-value.created>30000);
        UUID owner=player.getUUID();
        try{
            if(!(player.getMainHandItem().getItem() instanceof com.phasetranscrystal.fpsmatch.common.item.zombies.ZombiesDeployTool)||!com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper.hasAdminAccess(player))throw new IllegalArgumentException("需要管理员权限并手持部署工具");
            if(part.total()<2||part.total()>33||part.index()<0||part.index()>=part.total()||part.bytes().length<1||part.bytes().length>28000)throw new IllegalArgumentException("编辑上传分段无效");
            if(part.index()==0){if(!ACTIVE.containsKey(owner)&&ACTIVE.size()>=16)throw new IllegalArgumentException("部署上传繁忙，请稍后重试");ACTIVE.put(owner,new Assembly(part));}
            var upload=ACTIVE.get(owner);if(upload==null||upload.next!=part.index()||!upload.first.token().equals(part.token())||!upload.first.map().equals(part.map())||!upload.first.operation().equals(part.operation())||upload.first.total()!=part.total())throw new IllegalArgumentException("编辑上传不完整，请重新保存");
            if(upload.bytes.size()+part.bytes().length>900000)throw new IllegalArgumentException("编辑数据过大，请拆分场景");upload.bytes.writeBytes(part.bytes());upload.next++;
            if(upload.next<part.total())return null;ACTIVE.remove(owner);return new ZombiesNpcDialogueNetwork.EditorRequest(part.token(),part.map(),part.operation(),ZombiesNpcEditorWire.readUpload(upload.bytes.toByteArray()));
        }catch(RuntimeException error){ACTIVE.remove(owner);throw error;}
    }
    public static void clear(){ACTIVE.clear();}
    private ZombiesNpcEditorUpload(){}
}
