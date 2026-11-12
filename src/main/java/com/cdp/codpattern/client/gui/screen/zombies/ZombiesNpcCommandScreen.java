package com.cdp.codpattern.client.gui.screen.zombies;

import com.cdp.codpattern.client.zombies.ZombiesCoopClient;
import com.cdp.codpattern.app.zombies.npc.ZombiesCoopNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Every NPC command uses an explicit room-owned selection, never a silent nearest-NPC fallback. */
public final class ZombiesNpcCommandScreen extends Screen {
    public final UUID session;private final String operation;private int scroll,left,top,panelWidth,panelHeight;private final List<Button> rows=new ArrayList<>();private final List<CompoundTag> visible=new ArrayList<>();
    public ZombiesNpcCommandScreen(String operation,UUID session){super(Component.literal(switch(operation){case "call"->"选择要呼叫的 NPC";case "arm_guard"->"选择驻守 NPC";case "arm_protect"->"选择保护队友的 NPC";default->"选择恢复任务的 NPC";}));this.operation=operation;this.session=session;}
    private List<CompoundTag> options(){var out=new ArrayList<CompoundTag>();for(var raw:ZombiesCoopClient.state().getList("npcs",Tag.TAG_COMPOUND)){var n=(CompoundTag)raw;if(!n.contains("selectable")||n.getBoolean("selectable"))out.add(n);}return out;}
    @Override protected void init(){panelWidth=Math.min(430,width-24);panelHeight=Math.min(340,height-24);left=(width-panelWidth)/2;top=(height-panelHeight)/2;buildRows();}
    private void buildRows(){rows.forEach(this::removeWidget);rows.clear();visible.clear();var all=options();int count=Math.max(1,(panelHeight-80)/47);scroll=Math.max(0,Math.min(scroll,Math.max(0,all.size()-count)));
        for(int i=scroll;i<Math.min(all.size(),scroll+count);i++){var n=all.get(i);visible.add(n);int id=n.getInt("id"),y=top+40+(i-scroll)*47;rows.add(addRenderableWidget(Button.builder(Component.empty(),b->{onClose();ZombiesCoopClient.request(operation,0,id,ZombiesCoopNetwork.ZERO,session);}).bounds(left+12,y,panelWidth-24,43).build()));}
        rows.add(addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(left+panelWidth-82,top+panelHeight-28,70,20).build()));
    }
    @Override public void tick(){if(!ZombiesCoopClient.active()||!ZombiesCoopClient.state().hasUUID("session")||!session.equals(ZombiesCoopClient.state().getUUID("session"))){onClose();return;}var all=options();for(int i=0;i<visible.size();i++){int id=visible.get(i).getInt("id");rows.get(i).active=all.stream().anyMatch(n->n.getInt("id")==id);}}
    @Override public boolean mouseScrolled(double x,double y,double delta){scroll+=delta>0?-1:1;buildRows();return true;}
    @Override public boolean mouseClicked(double x,double y,int button){if(x<left||x>=left+panelWidth||y<top||y>=top+panelHeight){onClose();return true;}return super.mouseClicked(x,y,button);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.fill(left,top,left+panelWidth,top+panelHeight,0xF015242F);g.fill(left,top,left+panelWidth,top+3,0xFF83D9BA);g.drawString(font,title,left+12,top+12,0xFFE1EFF2,false);super.render(g,mx,my,partial);
        if(visible.isEmpty())g.drawString(font,"64 格内没有可行动的本房间 NPC",left+12,top+49,0xFF9CB6C6,false);
        for(int i=0;i<visible.size();i++){var n=visible.get(i);int y=top+46+i*47;g.drawString(font,font.plainSubstrByWidth(n.getString("name")+" · "+Math.round(n.getDouble("distance"))+" m",panelWidth-44),left+20,y,0xFF83D9BA,false);String gun=ZombiesCoopClient.gun(n).getString();g.drawString(font,font.plainSubstrByWidth(gun+" · "+ZombiesCoopClient.quality(n),panelWidth-44),left+20,y+15,0xFFCEE0E7,false);}
    }
    @Override public boolean isPauseScreen(){return false;}
}
