package com.qiuym.qiusmob.network;

import com.qiuym.qiusmob.api.IEyeHeightSetter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncPacket {
    private final double x;
    private final double y;
    private final double z;
    private final float yRot;
    private final float xRot;
    private final float eyeHeight;
    private final boolean possessing;  // 新增：是否处于夺舍状态

    public SyncPacket(double x, double y, double z, float yRot, float xRot, float eyeHeight, boolean possessing) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.xRot = xRot;
        this.eyeHeight = eyeHeight;
        this.possessing = possessing;
    }

    public SyncPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.yRot = buf.readFloat();
        this.xRot = buf.readFloat();
        this.eyeHeight = buf.readFloat();
        this.possessing = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yRot);
        buf.writeFloat(xRot);
        buf.writeFloat(eyeHeight);
        buf.writeBoolean(possessing);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            // 位置同步（无论何种模式都同步）
            player.setPos(x, y, z);

            // 根据 possessing 标志决定眼睛高度的行为
            if (possessing) {
                // 夺舍中：设置为目标实体的眼睛高度
                ((IEyeHeightSetter) player).setEyeHeight(eyeHeight);
            } else {
                // 已解除夺舍：重置为默认值
                ((IEyeHeightSetter) player).resetEyeHeight();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}