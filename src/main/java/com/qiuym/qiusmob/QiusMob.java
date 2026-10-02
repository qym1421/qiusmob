package com.qiuym.qiusmob;

import com.qiuym.qiusmob.network.NetworkHandler;
import com.qiuym.qiusmob.player.*;
import com.qiuym.qiusmob.worldgen.ModFeatures;
import com.qiuym.qiusmob.item.EntityEggs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(QiusMob.MOD_ID)
public class QiusMob {
    public static final String MOD_ID = "qiusmob";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public QiusMob(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        EntityRegister.register(bus);
        EntityEggs.register(bus);
        ItemRegister.register(bus);
        NetworkHandler.register();
        PlayerConvertToQiusMob.register();
        ModFeatures.FEATURES.register(bus);
        MinecraftForge.EVENT_BUS.register(this);
        
        LOGGER.info("[QiusMob] 模组加载完成！");
    }
}