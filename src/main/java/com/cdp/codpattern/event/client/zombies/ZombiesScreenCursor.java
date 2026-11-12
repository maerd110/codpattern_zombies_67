package com.cdp.codpattern.event.client.zombies;

import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.Map;
import java.util.WeakHashMap;

/** Remember the GUI cursor before vanilla locks it for gameplay; restore it after GUI initialization. */
@Mod.EventBusSubscriber(modid=ZombiesAddonConstants.MOD_ID,value=Dist.CLIENT)
public final class ZombiesScreenCursor {
    private record Position(double x,double y) { }
    private static Position last;
    private static final Map<Screen,Position> opening=new WeakHashMap<>();
    private static java.util.function.BiConsumer<Double,Double> moveCursor=(x,y)->GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(),x,y);
    private static Position capture(){
        var client=Minecraft.getInstance();var window=client.getWindow();
        double x=client.mouseHandler.xpos()/Math.max(1,window.getScreenWidth());
        double y=client.mouseHandler.ypos()/Math.max(1,window.getScreenHeight());
        return Double.isFinite(x)&&Double.isFinite(y)?new Position(x,y):last;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void closing(ScreenEvent.Closing event){last=capture();}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void opening(ScreenEvent.Opening event){
        if(event.isCanceled()||event.getNewScreen()==null)return;
        var position=event.getCurrentScreen()==null?last:capture();
        if(position!=null)opening.put(event.getNewScreen(),position);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void initialized(ScreenEvent.Init.Post event){
        var position=opening.remove(event.getScreen());var client=Minecraft.getInstance();
        if(position==null||!client.isWindowActive()||client.mouseHandler.isMouseGrabbed())return;
        var window=client.getWindow();int width=Math.max(1,window.getScreenWidth()),height=Math.max(1,window.getScreenHeight());
        moveCursor.accept(Math.max(0,Math.min(width-1,position.x()*width)),Math.max(0,Math.min(height-1,position.y()*height)));
    }
    private ZombiesScreenCursor(){ }
}
