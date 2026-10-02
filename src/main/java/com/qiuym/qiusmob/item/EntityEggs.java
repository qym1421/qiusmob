package com.qiuym.qiusmob.item;

import com.qiuym.qiusmob.EntityRegister;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityEggs {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "qiusmob");

    public static final RegistryObject<Item> DEATH_SPAWN_EGG = ITEMS.register("death_spawn_egg",
            () -> new ForgeSpawnEggItem(() -> EntityRegister.DEATH.get(),
                    0x5A0F1B,
                    0xC41A22,
                    new Item.Properties()
            )
    );
    
    public static final RegistryObject<Item> SKIRK_SPAWN_EGG = ITEMS.register("skirk_spawn_egg",
            () -> new ForgeSpawnEggItem(EntityRegister.SKIRK,
                    0x1a0033,  // 主色：深渊紫黑
                    0x4b0082,  // 副色：深紫
                    new Item.Properties()));
                    
    public static final RegistryObject<Item> ABYSS_CONSCIOUSNESS_SPAWN_EGG = ITEMS.register(
            "abyss_consciousness_spawn_egg",
            () -> new ForgeSpawnEggItem(
                    EntityRegister.ABYSS_CONSCIOUSNESS,
                    0x1A0033, 0x8A2BE2,
                    new Item.Properties()));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}