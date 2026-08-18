package com.qiuym.qiusmob.network;

import com.qiuym.qiusmob.player.PlayerMovementState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MovementPacket {
    private final float forward;
    private final float left;
    private final boolean jumping;

    public MovementPacket(float forward, float left, boolean jumping) {
        this.forward = forward;
        this.left = left;
        this.jumping = jumping;
    }

    public MovementPacket(FriendlyByteBuf buf) {
        this.forward = buf.readFloat();
        this.left = buf.readFloat();
        this.jumping = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(forward);
        buf.writeFloat(left);
        buf.writeBoolean(jumping);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) {
                // System.out.println("[QiusMob] 服务端收到 MovementPacket: forward=" + forward + ", left=" + left + ", jumping=" + jumping + " from " + player.getName().getString());
                PlayerMovementState.set(player.getUUID(), forward, left, jumping);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}