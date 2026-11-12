package com.cdp.codpattern.app.zombies.service;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
/** One round, one team vault. Transfers never count as newly earned score. */
public final class ZombiesSharedBank {
    private long balance;
    public synchronized long balance(){return balance;}
    public synchronized void reset(){balance=0;}
    public synchronized void transfer(ZombiesPlayerRuntimeState player,int amount,boolean deposit){
        if(player==null||amount<=0)throw new IllegalArgumentException("请输入正整数点数");
        synchronized(player){
            if(deposit){if(balance+amount>Integer.MAX_VALUE)throw new IllegalArgumentException("银行已达到点数上限");if(!player.spendPoints(amount))throw new IllegalArgumentException("个人点数不足");balance+=amount;}
            else {if(balance<amount)throw new IllegalArgumentException("银行点数不足");if(player.points()+amount>Integer.MAX_VALUE)throw new IllegalArgumentException("个人点数达到上限");balance-=amount;player.refundPoints(amount);}
        }
    }
}
