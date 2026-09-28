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
            int deathHash = level.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).get();
            int skirkHash = level.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).get();
            int playerHash = player.getUUID().hashCode();

            // 没有任何绑定
            if (deathHash == 0 && skirkHash == 0) {
                player.displayClientMessage(
                        Component.literal("§c§l【QiusMod】 §r当前没有绑定任何 Boss！"),
                        true
                );
                return;
            }

            // 判断玩家是哪个 Boss 的绑定者
            PlayerConvertToQiusMob.TransformType type = null;
            if (deathHash == playerHash) {
                type = PlayerConvertToQiusMob.TransformType.DEATH;
            } else if (skirkHash == playerHash) {
                type = PlayerConvertToQiusMob.TransformType.SKIRK;
            }

            if (type == null) {
                player.displayClientMessage(
                        Component.literal("§c§l【QiusMod】 §r你不是被选中的 Boss 化身！"),
                        true
                );
                return;
            }

            // 执行变形
            PlayerConvertToQiusMob.setTransformed(player, transformed, type);
        });
        ctx.get().setPacketHandled(true);
    }
}