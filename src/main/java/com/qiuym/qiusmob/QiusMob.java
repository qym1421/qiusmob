package com.qiuym.qiusmob;

import com.qiuym.qiusmob.network.NetworkHandler;
import com.qiuym.qiusmob.player.*;
import com.qiuym.qiusmob.item.EntityEggs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(QiusMob.MOD_ID)
public class QiusMob {
    public static final String MOD_ID = "qiusmob";

    public QiusMob(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        EntityRegister.register(bus);
        EntityEggs.register(bus);
        ItemRegister.register(bus);
        NetworkHandler.register();
        PlayerConvertToQiusMob.register();
        MinecraftForge.EVENT_BUS.register(this);
    }
}