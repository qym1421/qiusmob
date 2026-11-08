package com.qiuym.qiusmob.network;

import com.qiuym.qiusmob.QiusMob;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class NetworkHandler {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(QiusMob.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    public static void register() {
        int id = 0;

        INSTANCE.registerMessage(id++, TransformPacket.class,
                TransformPacket::encode, TransformPacket::new,
                TransformPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, TransformSyncPacket.class,
                TransformSyncPacket::encode, TransformSyncPacket::new,
                TransformSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, SkillPacket.class,
                SkillPacket::encode, SkillPacket::new,
                SkillPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, MovementPacket.class,
                MovementPacket::encode, MovementPacket::new,
                MovementPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, SyncPacket.class,
                SyncPacket::encode, SyncPacket::new,
                SyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, SightModePacket.class,
                SightModePacket::encode, SightModePacket::new,
                SightModePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, ViewModePacket.class,
                ViewModePacket::encode, ViewModePacket::new,
                ViewModePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id, SkirkBindSyncPacket.class,
                SkirkBindSyncPacket::encode, SkirkBindSyncPacket::new,
                SkirkBindSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}