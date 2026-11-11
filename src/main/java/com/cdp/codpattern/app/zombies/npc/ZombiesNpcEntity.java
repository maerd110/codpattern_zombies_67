package com.cdp.codpattern.app.zombies.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class ZombiesNpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(ZombiesNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> POSTURE = SynchedEntityData.defineId(ZombiesNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> STORY_ALIVE = SynchedEntityData.defineId(ZombiesNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private ZombiesNpcRuntime owner;
    private String npcId = "";

    public ZombiesNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SKIN, "minecraft:textures/entity/player/wide/steve.png");
        entityData.define(POSTURE, 0);
        entityData.define(STORY_ALIVE, true);
    }

    void bind(ZombiesNpcRuntime runtime, String id) { owner = runtime; npcId = id; }
    public String skin() { return entityData.get(SKIN); }
    public int posture() { return entityData.get(POSTURE); }
    public boolean storyAlive() { return entityData.get(STORY_ALIVE); }

    void apply(ZombiesNpcConfig.Appearance appearance) {
        if (appearance.name() != null) {
            setCustomName(net.minecraft.network.chat.Component.literal(appearance.name()));
            setCustomNameVisible(!appearance.name().isEmpty());
        }
        if (appearance.skin() != null) entityData.set(SKIN, appearance.skin());
        if (appearance.pose() != null) entityData.set(POSTURE, appearance.pose().ordinal());
        if (appearance.alive() != null) {
            entityData.set(STORY_ALIVE, appearance.alive());
            dead = !appearance.alive();
            deathTime = 0;
            setHealth(appearance.alive() ? getMaxHealth() : 0);
        }
        if (appearance.invulnerable() != null) setInvulnerable(appearance.invulnerable());
        if (appearance.position() != null) {
            var p = appearance.position();
            if (owner != null) owner.keepLoaded(net.minecraft.core.BlockPos.containing(p.x(), p.y(), p.z()));
            moveTo(p.x(), p.y(), p.z(), p.yaw(), 0);
            setYHeadRot(p.yaw());
            setYBodyRot(p.yaw());
        }
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && owner != null) {
            owner.interact(serverPlayer, npcId, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
        return !storyAlive() || super.isInvulnerableTo(source);
    }
    @Override public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!storyAlive() || dead) return;
        super.die(source);
        if (!dead) {
            if (getHealth() <= 0) setHealth(1);
            return;
        }
        entityData.set(STORY_ALIVE, false);
        entityData.set(POSTURE, ZombiesNpcConfig.Pose.LYING.ordinal());
    }
    // The normal death event runs, while the story corpse remains available for scripted revival.
    @Override protected void tickDeath() { deathTime = 0; }
    @Override public void push(double x, double y, double z) { }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    // Story entities belong to one runtime. Disk-loaded entities are removed by ZombiesNpcEvents.
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("StoryNpc", npcId);
    }
}
