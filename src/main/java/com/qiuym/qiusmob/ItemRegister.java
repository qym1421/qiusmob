package com.qiuym.qiusmob;

import com.qiuym.qiusmob.QiusMob;
import com.qiuym.qiusmob.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ItemRegister {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, QiusMob.MOD_ID);

    public static final RegistryObject<CreativeModeTab> QIUSMOB_TAB = TABS.register("qiusmob_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("§4§lQiusMob"))
                    .icon(() -> new ItemStack(EntityEggs.DEATH_SPAWN_EGG.get()))
                    .displayItems((parameters, output) -> {
                        // 在这里添加模组的所有物品
                        output.accept(EntityEggs.DEATH_SPAWN_EGG.get());
                        output.accept(EntityEggs.SKIRK_SPAWN_EGG.get());
                        output.accept(EntityEggs.ABYSS_CONSCIOUSNESS_SPAWN_EGG.get());
                    })
                    .build()
    );

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}