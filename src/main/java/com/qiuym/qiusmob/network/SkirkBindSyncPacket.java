package com.qiuym.qiusmob.network;

import com.qiuym.qiusmob.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SkirkBindSyncPacket {
    private final boolean bound;

    public SkirkBindSyncPacket(boolean bound) {
        this.bound = bound;
    }

    public SkirkBindSyncPacket(FriendlyByteBuf buf) {
        this.bound = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(bound);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientState.setSkirkBound(bound);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}