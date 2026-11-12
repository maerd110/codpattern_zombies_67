package com.cdp.codpattern.app.zombies.npc;

import com.cdp.codpattern.zombiesaddon.ZombiesAddonConstants;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ZombiesNpcRegister {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZombiesAddonConstants.MOD_ID);
    public static final RegistryObject<EntityType<ZombiesNpcEntity>> NPC = TYPES.register("story_npc", () ->
            EntityType.Builder.of(ZombiesNpcEntity::new, MobCategory.MISC).sized(0.6F, 1.8F)
                    .clientTrackingRange(10).updateInterval(1).build("codpattern_zombies:story_npc"));
    private ZombiesNpcRegister() { }
    public static void install(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(ZombiesNpcRegister::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(NPC.get(), Mob.createMobAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,.27).add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE,64).build());
    }
}
