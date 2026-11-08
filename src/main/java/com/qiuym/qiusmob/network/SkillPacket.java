package com.qiuym.qiusmob.network;

import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import com.qiuym.qiusmob.skill.SkirkSkills;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
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
            if (player == null) return;

            if ("possess".equals(skill)) {
                if (PlayerConvertToQiusMob.isPossessing(player)) {
                    PlayerConvertToQiusMob.executePossess(player, null);
                    return;
                }

                double range = 48.0;
                AABB searchBox = AABB.ofSize(player.position(), range * 2, range, range * 2);
                List<LivingEntity> targets = player.level().getEntitiesOfClass(
                        LivingEntity.class, searchBox,
                        entity -> entity != player && entity.isAlive());

                if (targets.isEmpty()) {
                    player.displayClientMessage(
                            Component.literal("§c§l【QiusMod】 §r没有看到任何实体！"), true);
                    return;
                }
                targets.sort((a, b) -> Double.compare(player.distanceToSqr(a), player.distanceToSqr(b)));
                PlayerConvertToQiusMob.executePossess(player, targets.get(0).getUUID());
                return;
            }

            switch (skill) {
                case "melee" -> PlayerConvertToQiusMob.executeMelee(player);
                case "leap" -> PlayerConvertToQiusMob.executeLeap(player);
                case "aoe" -> PlayerConvertToQiusMob.executeAOE(player);
                case "ultimate" -> PlayerConvertToQiusMob.executeUltimate(player);
                case "skirk_n" -> SkirkSkills.executeN(player);
                case "skirk_j" -> SkirkSkills.executeJ(player);
                case "skirk_i" -> SkirkSkills.executeI(player);
                case "skirk_fire" -> SkirkSkills.fireSightBeam(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}