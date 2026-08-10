package com.qiuym.qiusmob.client;

import com.qiuym.qiusmob.EntityRegister;
import com.qiuym.qiusmob.QiusMob;
import com.qiuym.qiusmob.client.render.DeathRender;
import com.qiuym.qiusmob.item.EntityEggs;
import com.qiuym.qiusmob.entity.Death;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QiusMob.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Client {
    @SubscribeEvent
    public static void regiserRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer((EntityType<? extends Death>) EntityRegister.DEATH.get(), (DeathRender::new));
    }
    
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) return 0x5A0F1B;  // 主色：暗红褐
            if (tintIndex == 1) return 0xC41A22;  // 斑点色：亮红
            return 0xFFFFFF;
        }, EntityEggs.DEATH_SPAWN_EGG.get());
    }
}