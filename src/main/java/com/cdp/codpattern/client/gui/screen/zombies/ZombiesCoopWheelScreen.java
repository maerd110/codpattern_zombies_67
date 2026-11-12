package com.cdp.codpattern.client.gui.screen.zombies;

import com.cdp.codpattern.client.zombies.ZombiesCoopClient;
import com.cdp.codpattern.app.zombies.npc.ZombiesCoopNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import java.util.UUID;

/** Six equal sectors; click to confirm, centre or Escape to cancel. The world keeps running. */
public final class ZombiesCoopWheelScreen extends Screen {
    public final UUID session;
    private final int preferred;
    private static final String[] NAMES={"共享银行","呼叫 NPC","指定驻守","保护玩家","恢复任务","清除标记"};
    private static final String[] HELP={"存入或取出全队共享点数","让选中的 NPC 临时靠近你","关闭轮盘后，中键指定地面","关闭轮盘后，中键指定队友","恢复选中 NPC 的部署安排","清除自己发出的当前标记"};
    private static final String[] OPS={"open","call","arm_guard","arm_protect","resume_near","clear_ping"};
    public ZombiesCoopWheelScreen(int preferred){super(Component.literal("队伍协作"));this.preferred=preferred;session=ZombiesCoopClient.state().getUUID("session");}
    private float outer(){return Math.min(132,Math.min(width*.37f,height*.34f));}
    private float inner(){return outer()*.34f;}
    public int sector(double x,double y){double dx=x-width/2d,dy=y-height/2d,r=Math.hypot(dx,dy);if(r<inner()||r>outer())return -1;double angle=Math.atan2(dy,dx)+Math.PI/2+Math.PI/6;if(angle<0)angle+=Math.PI*2;return ((int)(angle/(Math.PI/3)))%6;}
    private boolean enabled(int index){return ZombiesCoopClient.state().getBoolean("interactive")&&(index==0||index==5||!ZombiesCoopClient.state().getList("npcs",10).isEmpty());}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0x70101922);int hovered=sector(mouseX,mouseY);float cx=width/2f,cy=height/2f,outer=outer(),inner=inner();g.flush();
        boolean cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE),depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);RenderSystem.disableCull();RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShader(GameRenderer::getPositionColorShader);var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);var matrix=g.pose().last().pose();
        for(int sector=0;sector<6;sector++){int color=!enabled(sector)?0xE01A242C:sector==hovered?0xF0396570:0xED192D39;double start=-Math.PI/2+(sector-.5)*Math.PI/3+.025,end=start+Math.PI/3-.05;
            for(int step=0;step<20;step++){double a=start+(end-start)*step/20,c=start+(end-start)*(step+1)/20;float ax=(float)Math.cos(a),ay=(float)Math.sin(a),bx=(float)Math.cos(c),by=(float)Math.sin(c);
                b.vertex(matrix,cx+ax*inner,cy+ay*inner,0).color(color).endVertex();b.vertex(matrix,cx+ax*outer,cy+ay*outer,0).color(color).endVertex();b.vertex(matrix,cx+bx*outer,cy+by*outer,0).color(color).endVertex();b.vertex(matrix,cx+bx*inner,cy+by*inner,0).color(color).endVertex();}}
        BufferUploader.drawWithShader(b.end());if(cull)RenderSystem.enableCull();if(depth)RenderSystem.enableDepthTest();
        for(int i=0;i<6;i++){double a=-Math.PI/2+i*Math.PI/3;int x=Math.round(cx+(float)Math.cos(a)*(inner+outer)*.51f),y=Math.round(cy+(float)Math.sin(a)*(inner+outer)*.51f);int color=!enabled(i)?0xFF627582:i==hovered?0xFFFFD27A:0xFFC9E3E7;g.drawCenteredString(font,NAMES[i],x,y-3,color);}
        g.drawCenteredString(font,"队伍协作",width/2,(int)(cy-outer-22),0xFFE1EFF2);g.drawCenteredString(font,"协作",width/2,(int)cy-9,0xFF83D9BA);g.drawCenteredString(font,"Esc 取消",width/2,(int)cy+5,0xFF8DA7B5);
        String help=hovered>=0?(!enabled(hovered)?"当前没有可执行该操作的 NPC":HELP[hovered]):"移动鼠标选择 · 左键确认";g.drawCenteredString(font,font.plainSubstrByWidth(help,width-16),width/2,(int)(cy+outer+12),0xFFBBD1DA);
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1){onClose();return true;}if(button!=0)return false;int index=sector(x,y);if(index<0){onClose();return true;}if(!enabled(index))return true;onClose();ZombiesCoopClient.request(OPS[index],0,preferred,ZombiesCoopNetwork.ZERO,session);return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(ZombiesCoopClient.BANK.matches(key,scan)){onClose();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean isPauseScreen(){return false;}
}
