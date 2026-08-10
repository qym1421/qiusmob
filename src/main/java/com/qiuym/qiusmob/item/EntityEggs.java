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

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}