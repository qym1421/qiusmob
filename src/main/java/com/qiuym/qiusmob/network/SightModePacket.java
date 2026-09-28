package com.qiuym.qiusmob.network;

import com.qiuym.qiusmob.client.SightModeClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SightModePacket {
    private final boolean active;

    public SightModePacket(boolean active) {
        this.active = active;
    }

    public SightModePacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                SightModeClient.setSkyMode(active);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}