package com.qiuym.qiusmob.network;

import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SkillPacket {
    private final String skill;

    public SkillPacket(String skill) {
        this.skill = skill;
    }

    public SkillPacket(FriendlyByteBuf buf) {
        this.skill = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(skill);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) {
                switch (skill) {
                    case "melee" -> PlayerConvertToQiusMob.executeMelee(player);
                    case "leap" -> PlayerConvertToQiusMob.executeLeap(player);
                    case "aoe" -> PlayerConvertToQiusMob.executeAOE(player);
                    case "ultimate" -> PlayerConvertToQiusMob.executeUltimate(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}