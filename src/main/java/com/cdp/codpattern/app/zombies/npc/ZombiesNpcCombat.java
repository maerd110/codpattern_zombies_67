package com.cdp.codpattern.app.zombies.npc;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Room-owned support fire uses TaCZ's shooter, projectiles, cooldown and reload state. */
final class ZombiesNpcCombat {
    private final ZombiesNpcEntity npc;
    private ZombiesNpcConfig.Combat config;
    Vec3 anchor; UUID follow;
    private boolean drawn;
    UUID weaponRevision=UUID.randomUUID(),calledBy;
    int callUntil;
    ItemStack loan=ItemStack.EMPTY;
    boolean calledFor(net.minecraft.world.entity.player.Player p){return p!=null&&p.getUUID().equals(calledBy)&&npc.tickCount<callUntil;}
    void call(net.minecraft.server.level.ServerPlayer player){calledBy=player.getUUID();callUntil=npc.tickCount+1200;}
    void resume(){calledBy=null;callUntil=0;}
    void equipLoan(ItemStack stack){loan=stack.copy();loan.setCount(1);npc.setItemSlot(EquipmentSlot.MAINHAND,loan.copy());drawn=false;weaponRevision=UUID.randomUUID();}

    com.tacz.guns.api.entity.ShootResult lastResult;
    private int searchAt;
    ZombiesNpcCombat(ZombiesNpcEntity npc){this.npc=npc;}
    static ItemStack gun(String specification){
        var stack=ZombiesNpcInventory.parse(specification);var gun=IGun.getIGunOrNull(stack);
        if(gun==null||com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).isEmpty())throw new IllegalArgumentException("请选择已加载的 TaCZ 枪械");
        stack.setCount(1);return stack;
    }
    void configure(ZombiesNpcConfig.Combat next){
        config=next;drawn=false;anchor=npc.position();follow=null;
        npc.setItemSlot(EquipmentSlot.MAINHAND,next.mode()==ZombiesNpcConfig.CombatMode.OFF?ItemStack.EMPTY:loan.isEmpty()?gun(next.gun()):loan.copy());weaponRevision=UUID.randomUUID();
        npc.setDropChance(EquipmentSlot.MAINHAND,0);
    }
    boolean active(){return config!=null&&config.mode()!=ZombiesNpcConfig.CombatMode.OFF;}
    private void stop(){npc.setDeltaMovement(Vec3.ZERO);npc.setTarget(null);npc.getNavigation().stop();if(drawn)IGunOperator.fromLivingEntity(npc).aim(false);npc.setNoAi(true);npc.setNoGravity(true);}
    void tick(){
        var owner=npc.owner();
        if(!active()||owner==null||!owner.combatAllowed()||!npc.storyAlive()||npc.posture()!=0){stop();return;}
        npc.setNoAi(false);npc.setNoGravity(false);
        if(anchor==null)anchor=npc.position();
        if(npc.tickCount%10==0){
            Vec3 destination=anchor;
            var caller=owner.combatPlayers().stream().filter(this::calledFor).findFirst().orElse(null);
            if(caller!=null)destination=caller.position();
            else if(config.mode()==ZombiesNpcConfig.CombatMode.FOLLOW){
                var players=owner.combatPlayers();var leader=players.stream().filter(p->p.getUUID().equals(follow)).findFirst().orElseGet(()->players.stream().min(java.util.Comparator.comparingDouble(npc::distanceToSqr)).orElse(null));
                if(leader!=null){follow=leader.getUUID();destination=leader.position();}
            }
            if(owner.insideCombatArea(destination)&&npc.position().distanceToSqr(destination)>(config.mode()==ZombiesNpcConfig.CombatMode.FOLLOW?9:1))npc.getNavigation().moveTo(destination.x,destination.y,destination.z,1.0);
            else npc.getNavigation().stop();
        }
        var target=npc.getTarget();
        if(target!=null&&(!owner.combatTarget(target)||npc.distanceToSqr(target)>config.range()*config.range()||!npc.hasLineOfSight(target)))npc.setTarget(null);
        if(npc.tickCount>=searchAt){searchAt=npc.tickCount+5;
            npc.setTarget(npc.level().getEntitiesOfClass(LivingEntity.class,npc.getBoundingBox().inflate(config.range()),e->owner.combatTarget(e)&&npc.distanceToSqr(e)<=config.range()*config.range()&&npc.hasLineOfSight(e)).stream().min(java.util.Comparator.comparingDouble(npc::distanceToSqr)).orElse(null));
        }
        var operator=IGunOperator.fromLivingEntity(npc);var stack=npc.getMainHandItem();var gun=IGun.getIGunOrNull(stack);if(gun==null){stop();return;}
        // Infinite reserve, finite magazine: TaCZ still performs its actual reload/bolt sequence.
        gun.setMaxDummyAmmoAmount(stack,32767);if(gun.getDummyAmmoAmount(stack)<1024)gun.setDummyAmmoAmount(stack,32767);
        if(!drawn){operator.initialData();operator.draw(npc::getMainHandItem);drawn=true;}
        if(owner.combatPlayers().stream().anyMatch(p->calledFor(p)&&npc.distanceToSqr(p)<12)){operator.aim(false);npc.setTarget(null);return;}
        target=npc.getTarget();operator.aim(target!=null);if(target==null)return;
        Vec3 delta=target.getBoundingBox().getCenter().subtract(npc.getEyePosition());
        float yaw=(float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        npc.setYRot(yaw);npc.setYHeadRot(yaw);npc.setYBodyRot(yaw);npc.setXRot(pitch);
        // Do not shoot through a teammate, even though the damage guard also prevents friendly fire.
        if(npc.level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,npc.getBoundingBox().expandTowards(delta).inflate(.4),p->p.isAlive()&&p.getBoundingBox().inflate(.3).clip(npc.getEyePosition(),targetPoint()).isPresent()).size()>0)return;
        var result=operator.shoot(()->pitch,()->yaw);lastResult=result;
        switch(result){case NO_AMMO->operator.reload();case NEED_BOLT->operator.bolt();case NOT_DRAW->operator.draw(npc::getMainHandItem);default->{}}
    }
    private Vec3 targetPoint(){return npc.getTarget().getBoundingBox().getCenter();}
}
