package com.cdp.codpattern.client.gui.screen.zombies;
import com.cdp.codpattern.client.zombies.ZombiesCoopClient;
import com.cdp.codpattern.app.zombies.npc.ZombiesCoopNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import java.util.UUID;
/** Compact gameplay controls; opening it does not pause the round. */
public final class ZombiesCoopScreen extends Screen {
    public final UUID session;
    private final UUID token;private final boolean npc;private final int entity;
    private final net.minecraft.nbt.ListTag choices;
    private EditBox amount;private Button deposit,withdraw,swap;private int left,top,panelWidth;
    public ZombiesCoopScreen(boolean npc,int entity,UUID token){super(Component.literal(npc?"NPC 支援":"全队共享银行"));this.npc=npc;this.entity=entity;this.token=token;this.session=ZombiesCoopClient.state().getUUID("session");this.choices=ZombiesCoopClient.state().getList("guns",Tag.TAG_COMPOUND).copy();}
    @Override protected void init(){panelWidth=Math.min(380,width-24);left=(width-panelWidth)/2;top=Math.max(8,(height-210)/2);
        if(!npc){amount=new EditBox(font,left+18,top+82,panelWidth-36,22,Component.literal("存取点数"));amount.setMaxLength(10);amount.setFilter(s->s.matches("[0-9]*"));amount.setValue("100");addRenderableWidget(amount);int half=(panelWidth-44)/2;
            deposit=addRenderableWidget(Button.builder(Component.literal("存入银行"),b->send("deposit",number())).bounds(left+18,top+115,half,22).build());withdraw=addRenderableWidget(Button.builder(Component.literal("从银行取出"),b->send("withdraw",number())).bounds(left+26+half,top+115,half,22).build());
        }else{swap=addRenderableWidget(Button.builder(Component.literal("选择背包中的枪械进行交换"),b->chooseGun()).bounds(left+12,top+111,panelWidth-24,22).build());addRenderableWidget(Button.builder(Component.literal("恢复 NPC 原任务"),b->{send("resume",0);onClose();}).bounds(left+12,top+140,panelWidth-24,20).build());}
        addRenderableWidget(Button.builder(Component.literal("关闭"),b->onClose()).bounds(left+panelWidth-82,top+179,70,20).build());
    }
    private void chooseGun(){var entries=new java.util.ArrayList<com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesNpcStackPickerScreen.Entry>();for(var item:choices){var tag=(CompoundTag)item;var stack=net.minecraft.world.item.ItemStack.of(tag.getCompound("item"));var stats=com.cdp.codpattern.app.zombies.npc.ZombiesCoopRuntime.tags(stack);String caption=stack.getHoverName().getString();if(stats!=null)caption+=" · "+com.cdp.codpattern.client.zombies.ZombiesRarityDisplay.fromRarityId(stats.rarityId()).map(com.cdp.codpattern.client.zombies.ZombiesRarityDisplay.Entry::label).orElse("预设")+" · Lv"+stats.weaponLevel()+" +"+stats.upgradeLevel();entries.add(new com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesNpcStackPickerScreen.Entry(stack,Integer.toString(tag.getInt("slot")),caption,1));}minecraft.setScreen(new com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesNpcStackPickerScreen(this,"选择要交付的枪械",entries,selected->send("swap",Integer.parseInt(selected.value()))));}
    private int number(){try{return Integer.parseInt(amount.getValue());}catch(Exception ignored){return 0;}}
    private void send(String op,int value){ZombiesCoopClient.request(op,value,entity,token,session);if(swap!=null)swap.active=false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.fill(left,top,left+panelWidth,top+210,0xF015242F);g.fill(left,top,left+panelWidth,top+3,0xFF83D9BA);g.drawString(font,title,left+14,top+14,0xFFE1EDF3,false);
        var state=ZombiesCoopClient.state();if(!npc){g.drawString(font,"银行余额："+state.getLong("bank"),left+18,top+37,0xFFFFD27A,false);g.drawString(font,"个人点数："+(long)state.getDouble("points"),left+18,top+54,0xFFCEE0E7,false);g.drawString(font,"输入需要存入或取出的点数",left+18,top+69,0xFF9CB6C6,false);deposit.active=state.getBoolean("interactive")&&number()>0&&number()<=state.getDouble("points");withdraw.active=state.getBoolean("interactive")&&number()>0&&number()<=state.getLong("bank");}
        else{CompoundTag target=null;for(var entry:state.getList("npcs",Tag.TAG_COMPOUND))if(entry instanceof CompoundTag n&&n.getInt("id")==entity)target=n;
            if(target!=null){g.drawString(font,target.getString("name"),left+14,top+36,0xFF83D9BA,false);g.drawString(font,font.plainSubstrByWidth("当前："+ZombiesCoopClient.gun(target).getString()+" · "+ZombiesCoopClient.quality(target),panelWidth-28),left+14,top+53,0xFFE1EDF3,false);}
            String held="背包与快捷栏 · "+choices.size()+" 把可交换枪械";g.drawString(font,font.plainSubstrByWidth(held,panelWidth-28),left+14,top+76,0xFFFFD27A,false);g.drawString(font,"部署自带枪械不会发放给玩家",left+14,top+92,0xFF9CB6C6,false);
        }
        g.drawString(font,font.plainSubstrByWidth(ZombiesCoopClient.notice(),panelWidth-28),left+14,top+166,0xFFFFD27A,false);super.render(g,mx,my,partial);
    }
    @Override public boolean isPauseScreen(){return false;}
}
