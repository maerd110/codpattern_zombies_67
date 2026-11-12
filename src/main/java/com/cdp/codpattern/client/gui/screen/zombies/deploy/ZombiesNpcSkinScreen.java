package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import com.cdp.codpattern.app.zombies.npc.ZombiesNpcPlayerSkin;
import com.cdp.codpattern.client.zombies.ZombiesNpcSkins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** A lookup does not change the draft until the deployer explicitly applies its result. */
public final class ZombiesNpcSkinScreen extends Screen {
    private final Screen parent;
    private final Consumer<ZombiesNpcPlayerSkin> apply;
    private String query="", notice="";
    private ZombiesNpcPlayerSkin result;
    private boolean busy;
    private long generation;
    private Button search, use;
    private EditBox input;
    public ZombiesNpcSkinScreen(Screen parent, Consumer<ZombiesNpcPlayerSkin> apply){super(label("title"));this.parent=parent;this.apply=apply;}
    private static Component label(String key){return Component.translatable("gui.codpattern.zombies.npc.player_skin."+key);}
    @Override protected void init(){
        int span=Math.min(360,width-32),x=(width-span)/2;
        input=addRenderableWidget(new EditBox(font,x,48,span,20,label("input")));
        input.setMaxLength(36);input.setValue(query);input.setHint(label("input"));input.setResponder(value->{query=value;result=null;generation++;updateButtons();});input.setEditable(!busy);
        search=addRenderableWidget(Button.builder(label("lookup"),b->lookup()).bounds(x,76,span,20).build());
        search.setTooltip(Tooltip.create(label("lookup")));
        use=addRenderableWidget(Button.builder(label("apply"),b->{if(result!=null){minecraft.setScreen(parent);apply.accept(result);}}).bounds(x,height-58,span,20).build());
        use.setTooltip(Tooltip.create(label("apply")));
        addRenderableWidget(Button.builder(label("back"),b->onClose()).bounds(x,height-30,span,20).build());
        updateButtons();setInitialFocus(input);
    }
    private void updateButtons(){if(search!=null)search.active=!busy&&!query.isBlank();if(use!=null)use.active=!busy&&result!=null;}
    private void lookup(){
        if(busy)return;busy=true;input.setEditable(false);result=null;notice=label("waiting").getString();long request=++generation;updateButtons();var client=minecraft;
        ZombiesNpcSkins.lookup(query).whenComplete((skin,error)->client.execute(()->{
            if(request!=generation)return;
            busy=false;result=skin;
            if(error==null)notice=skin.name()+" · "+skin.uuid();
            else {var cause=error;while(cause.getCause()!=null)cause=cause.getCause();notice=cause instanceof java.util.concurrent.TimeoutException?label("timeout").getString():cause.getMessage();}
            rebuildWidgets();
        }));
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        renderBackground(g);super.render(g,mouseX,mouseY,partial);g.drawCenteredString(font,title,width/2,20,0xFF83D9BA);
        int y=106;for(var line:font.split(Component.literal(notice==null?"":notice),Math.max(80,width-40))){g.drawString(font,line,20,y,0xFFE8F1F4);y+=font.lineHeight+2;}
        if(result!=null&&height-y>130){var texture=ZombiesNpcSkins.resolve(result.synced());net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,texture.texture(),width/2-24,y+12,48);}
        g.drawCenteredString(font,label("hint"),width/2,height-78,0xFF8CA7B6);
    }
    @Override public void onClose(){generation++;busy=false;minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
