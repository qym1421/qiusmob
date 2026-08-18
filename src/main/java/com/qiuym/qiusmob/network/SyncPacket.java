package com.qiuym.qiusmob.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncPacket {
    private final double x;
    private final double y;
    private final double z;

    public SyncPacket(double x, double y, double z, float yRot, float xRot) {
        this.x = x;
        this.y = y;
        this.z = z;
        // 视角参数保留但不使用
    }

    public SyncPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        buf.readFloat(); // 丢弃 yRot
        buf.readFloat(); // 丢弃 xRot
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(0); // 占位
        buf.writeFloat(0); // 占位
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            // ===== 只同步位置 =====
            player.setPos(x, y, z);
            // 玩家视角完全由鼠标控制，不覆盖
        });
        ctx.get().setPacketHandled(true);
    }
}