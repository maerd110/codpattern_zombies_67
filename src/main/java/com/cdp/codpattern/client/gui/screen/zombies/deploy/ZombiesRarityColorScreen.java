package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import com.cdp.codpattern.config.zombies.ZombiesWeaponRulesConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Presets and exact RGB entry share the same validated color value. */
final class ZombiesRarityColorScreen extends Screen {
    private static final String[] COLORS={"#22C55E","#3B82F6","#A855F7","#FF9800","#EF4444","#EC4899","#14B8A6","#06B6D4","#FACC15","#84CC16","#6366F1","#F97316","#FFFFFF","#9CA3AF","#78716C","#111827"};
    private final Screen parent; private final Consumer<String> choose; private String value;
    private EditBox hex; private Button apply;
    ZombiesRarityColorScreen(Screen parent,String value,Consumer<String> choose){super(Component.literal("选择品阶颜色"));this.parent=parent;this.value=value;this.choose=choose;}
    @Override protected void init(){
        int left=Math.max(12,width/2-150),w=Math.min(300,width-24);
        hex=addRenderableWidget(new EditBox(font,left,34,w,20,Component.literal("颜色 #RRGGBB")));hex.setMaxLength(7);hex.setValue(value);
        hex.setResponder(v->{value=v;if(apply!=null)apply.active=ZombiesWeaponRulesConfig.validColor(v);});
        for(int i=0;i<COLORS.length;i++){String color=COLORS[i];int x=left+(i%4)*(w/4),y=80+(i/4)*27;
            var b=addRenderableWidget(new Button(x,y,w/4-4,23,Component.literal(color),ignored->hex.setValue(color),n->n.get()){
                @Override public void renderWidget(GuiGraphics g,int mx,int my,float tick){int rgb=0xFF000000|Integer.parseInt(color.substring(1),16);g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),rgb);if(color.equalsIgnoreCase(value)){g.renderOutline(getX(),getY(),getWidth(),getHeight(),0xFFFFFFFF);g.drawCenteredString(font,"✓",getX()+getWidth()/2,getY()+7,0xFFFFFFFF);}}
            });b.setTooltip(Tooltip.create(Component.literal(color)));
        }
        apply=addRenderableWidget(Button.builder(Component.literal("使用此颜色"),b->{minecraft.setScreen(parent);choose.accept(value.toUpperCase(java.util.Locale.ROOT));}).bounds(left,height-30,w/2-2,20).build());apply.active=ZombiesWeaponRulesConfig.validColor(value);apply.setTooltip(Tooltip.create(Component.literal("使用此颜色")));
        addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(left+w/2+2,height-30,w/2-2,20).build());
    }
    @Override public void render(GuiGraphics g,int x,int y,float tick){g.fill(0,0,width,height,0xF0101923);g.drawCenteredString(font,title,width/2,12,0xFFE8EFED);if(ZombiesWeaponRulesConfig.validColor(value)){int color=0xFF000000|Integer.parseInt(value.substring(1),16);g.drawCenteredString(font,"品阶颜色预览 · "+value,width/2,63,color);}else g.drawCenteredString(font,"请输入 #RRGGBB",width/2,63,0xFFEF4444);super.render(g,x,y,tick);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
