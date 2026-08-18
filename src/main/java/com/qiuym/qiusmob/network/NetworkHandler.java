package com.qiuym.qiusmob.network;

import com.qiuym.qiusmob.QiusMob;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(QiusMob.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        INSTANCE.registerMessage(id++, TransformPacket.class,
                TransformPacket::encode,
                TransformPacket::new,
                TransformPacket::handle);
        INSTANCE.registerMessage(id++, SkillPacket.class,
                SkillPacket::encode,
                SkillPacket::new,
                SkillPacket::handle);
        INSTANCE.registerMessage(id++, TransformSyncPacket.class,
                TransformSyncPacket::encode, 
                TransformSyncPacket::new, 
                TransformSyncPacket::handle);
        INSTANCE.registerMessage(id++, SyncPacket.class,
                SyncPacket::encode, 
                SyncPacket::new, 
                SyncPacket::handle);
        INSTANCE.registerMessage(id++, MovementPacket.class,
                MovementPacket::encode, 
                MovementPacket::new, 
                MovementPacket::handle);
        INSTANCE.registerMessage(id++, ViewModePacket.class,
                ViewModePacket::encode, 
                ViewModePacket::new, 
                ViewModePacket::handle);
    }
}