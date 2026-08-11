package com.qiuym.qiusmob.network;

import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TransformPacket {
    private final boolean transformed;

    public TransformPacket(boolean transformed) {
        this.transformed = transformed;
    }

    public TransformPacket(FriendlyByteBuf buf) {
        this.transformed = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(transformed);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();

            // ===== 检查绑定（用 UUID 哈希） =====
            int bindingHash = level.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).get();
            int playerHash = player.getUUID().hashCode();

            if (bindingHash == 0) {
                player.displayClientMessage(
                        Component.literal("§c§l【QiusMod】 §r当前没有绑定任何玩家！"),
                        true
                );
                return;
            }

            if (bindingHash != playerHash) {
                player.displayClientMessage(
                        Component.literal("§c§l【QiusMod】 §r你不是被选中的死亡君主！"),
                        true
                );
                return;
            }

            // 执行变形
            PlayerConvertToQiusMob.setTransformed(player, transformed);
        });
        ctx.get().setPacketHandled(true);
    }
}