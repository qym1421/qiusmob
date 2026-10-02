package com.qiuym.qiusmob;

import com.qiuym.qiusmob.entity.Death;
import com.qiuym.qiusmob.entity.skirk.SkirkEntity;
import com.qiuym.qiusmob.entity.consciousness.AbyssConsciousnessEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityRegister {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, QiusMob.MOD_ID);

    public static final RegistryObject<EntityType<Death>> DEATH = ENTITIES.register("death", () -> EntityType.Builder.of(Death::new, MobCategory.MONSTER).sized(0.6f, 1.8f).build(new ResourceLocation(QiusMob.MOD_ID, "death").toString()));
    
    public static final RegistryObject<EntityType<SkirkEntity>> SKIRK = ENTITIES.register("skirk",
            () -> EntityType.Builder.of(SkirkEntity::new, MobCategory.MONSTER)
                    .sized(1.8f, 2.0f)
                    .build(new ResourceLocation(QiusMob.MOD_ID, "skirk").toString()));
    
    public static final RegistryObject<EntityType<AbyssConsciousnessEntity>> ABYSS_CONSCIOUSNESS =
            ENTITIES.register("abyss_consciousness",
                    () -> EntityType.Builder.of(AbyssConsciousnessEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(64)
                            .build(ResourceLocation.fromNamespaceAndPath(QiusMob.MOD_ID, "abyss_consciousness").toString()));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}