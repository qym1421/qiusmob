package com.qiuym.qiusmob.network;

import com.qiuym.qiusmob.client.KeyBindings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TransformSyncPacket {
    private final boolean transformed;

    public TransformSyncPacket(boolean transformed) {
        this.transformed = transformed;
    }

    public TransformSyncPacket(FriendlyByteBuf buf) {
        this.transformed = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(transformed);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 更新客户端的本地状态
            KeyBindings.setTransformed(transformed);
        });
        ctx.get().setPacketHandled(true);
    }
}