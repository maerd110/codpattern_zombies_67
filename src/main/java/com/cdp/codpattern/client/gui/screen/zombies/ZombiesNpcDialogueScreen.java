package com.cdp.codpattern.client.gui.screen.zombies;

import com.cdp.codpattern.app.zombies.npc.ZombiesNpcDialogueNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
import java.util.function.Consumer;

/** Non-pausing, server-owned dialogue. Closing leaves the graph waiting at this choice. */
public final class ZombiesNpcDialogueScreen extends Screen {
    private final ZombiesNpcDialogueNetwork.View view;
    private final Consumer<String> answer;
    private int left,top,panelWidth,panelHeight,textLeft,textTop,textWidth,textBottom,optionPage,textScroll,pageSize;
    private List<FormattedCharSequence> lines=List.of();
    private boolean submitted;
    public ZombiesNpcDialogueScreen(ZombiesNpcDialogueNetwork.View view) {
        this(view,option->ZombiesNpcDialogueNetwork.select(view.token(),option));
    }
    public ZombiesNpcDialogueScreen(ZombiesNpcDialogueNetwork.View view,Consumer<String> answer) {
        super(view.name());this.view=view;this.answer=answer;
    }
    public static void handle(ZombiesNpcDialogueNetwork.View view) {
        var client=Minecraft.getInstance();
        if(view.open())client.setScreen(new ZombiesNpcDialogueScreen(view));
        else if(client.screen instanceof ZombiesNpcDialogueScreen screen && screen.view.token().equals(view.token())) {
            screen.submitted=true;client.setScreen(null);
        }
    }
    @Override protected void init() {
        panelWidth=Math.min(740,width-24);panelHeight=Math.min(420,height-24);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        int portrait=width>=500?Math.min(160,panelWidth/4):0;
        textLeft=left+portrait+20;textTop=top+48;textWidth=panelWidth-portrait-40;
        pageSize=Math.max(1,Math.min(4,(panelHeight-130)/28));
        int visible=Math.min(pageSize,view.options().size());
        int optionsTop=top+panelHeight-42-visible*28;
        textBottom=optionsTop-12;
        lines=font.split(view.text(),textWidth);
        int pages=Math.max(1,(view.options().size()+pageSize-1)/pageSize);optionPage=Math.max(0,Math.min(optionPage,pages-1));
        for(int i=optionPage*pageSize;i<Math.min(view.options().size(),optionPage*pageSize+pageSize);i++) {
            var option=view.options().get(i);
            var label=Component.literal(font.plainSubstrByWidth(option.text(),textWidth-20));
            var button=addRenderableWidget(Button.builder(label,ignored->{if(!submitted){submitted=true;answer.accept(option.id());for(var child:children())if(child instanceof Button b)b.active=false;}})
                    .bounds(textLeft,optionsTop+(i%pageSize)*28,textWidth,24).build());
            button.setTooltip(Tooltip.create(Component.literal(option.text())));
        }
        var close=addRenderableWidget(Button.builder(Component.translatable("gui.codpattern.zombies.npc.conversation.close"),ignored->onClose())
                .bounds(left+panelWidth-90,top+panelHeight-28,74,20).build());
        close.active=true;
        if(pages>1){
            addRenderableWidget(Button.builder(Component.literal("‹"),b->{optionPage=(optionPage+pages-1)%pages;rebuildWidgets();}).bounds(textLeft,top+panelHeight-28,24,20).build());
            addRenderableWidget(Button.builder(Component.literal("›"),b->{optionPage=(optionPage+1)%pages;rebuildWidgets();}).bounds(textLeft+64,top+panelHeight-28,24,20).build());
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        g.fill(0,0,width,height,0xA8081019);
        g.fill(left-1,top-1,left+panelWidth+1,top+panelHeight+1,0xFF527B8C);
        g.fill(left,top,left+panelWidth,top+panelHeight,0xF3182632);
        g.fill(left,top,left+panelWidth,top+3,0xFF83D9BA);
        int portrait=textLeft-left-20;
        if(portrait>0){
            g.fill(left+12,top+14,left+portrait,top+panelHeight-14,0xFF101C27);
            if(minecraft.level!=null && minecraft.level.getEntity(view.entityId()) instanceof LivingEntity npc) {
                int scale=Math.min(65,(panelHeight-100)/3);
                InventoryScreen.renderEntityInInventoryFollowsMouse(g,left+portrait/2+6,top+panelHeight-62,scale,(float)(left+portrait/2-mx),(float)(top+80-my),npc);
            } else {
                g.drawCenteredString(font,Component.translatable("gui.codpattern.zombies.npc.conversation.portrait"),left+portrait/2,top+panelHeight/2,0xFF83D9BA);
            }
            g.drawCenteredString(font,Component.literal(font.plainSubstrByWidth(view.name().getString(),portrait-16)).withStyle(view.name().getStyle()),left+portrait/2+6,top+panelHeight-36,0xFFFFFFFF);
        }
        g.drawString(font,Component.literal(font.plainSubstrByWidth(view.name().getString(),textWidth)).withStyle(view.name().getStyle()),textLeft,top+22,0xFFFFFFFF,false);
        int rows=Math.max(1,(textBottom-textTop)/12);textScroll=Math.max(0,Math.min(textScroll,Math.max(0,lines.size()-rows)));
        g.enableScissor(textLeft,textTop,textLeft+textWidth,textBottom);
        for(int i=textScroll;i<Math.min(lines.size(),textScroll+rows);i++)g.drawString(font,lines.get(i),textLeft,textTop+(i-textScroll)*12,0xFFDCE7EA,false);
        g.disableScissor();
        if(lines.size()>rows)g.drawString(font,"↕",textLeft+textWidth-8,textBottom-10,0xFF83D9BA,false);
        if(view.options().isEmpty())g.drawString(font,Component.translatable("gui.codpattern.zombies.npc.conversation.none"),textLeft,textBottom+10,0xFF9DB0BA,false);
        if(view.options().size()>pageSize)g.drawString(font,(optionPage+1)+"/"+((view.options().size()+pageSize-1)/pageSize),textLeft+29,top+panelHeight-22,0xFFCADCE5,false);
        super.render(g,mx,my,partial);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){if(y>=textTop&&y<=textBottom){textScroll+=delta>0?-2:2;return true;}return super.mouseScrolled(x,y,delta);}
    @Override public void onClose(){if(!submitted){submitted=true;answer.accept("");}if(minecraft.screen==this)minecraft.setScreen(null);}
    @Override public void removed(){if(!submitted){submitted=true;answer.accept("");}}
    @Override public boolean isPauseScreen(){return false;}
}
