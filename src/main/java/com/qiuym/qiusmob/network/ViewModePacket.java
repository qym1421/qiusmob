package com.qiuym.qiusmob.network;

import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ViewModePacket {
    private final boolean locked;

    public ViewModePacket(boolean locked) {
        this.locked = locked;
    }

    public ViewModePacket(FriendlyByteBuf buf) {
        this.locked = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(locked);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) {
                PlayerConvertToQiusMob.setViewMode(player, locked);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}