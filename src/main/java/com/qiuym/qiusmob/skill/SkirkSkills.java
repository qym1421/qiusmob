package com.qiuym.qiusmob.skill;

import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkirkSkills {

    // N 冷却
    private static final Map<UUID, Integer> nCooldown = new ConcurrentHashMap<>();

    // J 黑洞
    private static final Map<UUID, BlackHole> blackHoles = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> jCooldown = new ConcurrentHashMap<>();

    // I 视线模式
    private static final Map<UUID, Boolean> sightModeActive = new ConcurrentHashMap<>();

    // ==================== N：终焉审判 ====================
    public static void executeN(ServerPlayer player) {
        if (!isSkirk(player)) return;

        UUID uuid = player.getUUID();
        if (nCooldown.getOrDefault(uuid, 0) > 0) return;
        nCooldown.put(uuid, 10);

        ServerLevel level = player.serverLevel();
        double radius = 60.0;
        AABB box = AABB.ofSize(player.position(), radius * 2, radius * 2, radius * 2);

        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);

        for (LivingEntity target : entities) {
            if (target == player) continue;

            if (target instanceof ServerPlayer other) {
                var team = player.getTeam();
                var otherTeam = other.getTeam();
                if (team != null && team == otherTeam && !team.isAllowFriendlyFire()) {
                    continue;
                }
            }

            target.hurt(player.damageSources().magic(), 99999.0f);

            level.sendParticles(ParticleTypes.PORTAL,
                    target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    30, 0.5, 0.5, 0.5, 0.3);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    15, 0.3, 0.3, 0.3, 0.05);
        }

        level.playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 0.5f);
    }

    // ==================== J：黑洞掌控 ====================
    public static void executeJ(ServerPlayer player) {
        if (!isSkirk(player)) return;

        UUID uuid = player.getUUID();
        if (jCooldown.getOrDefault(uuid, 0) > 0) return;
        jCooldown.put(uuid, 300);

        ServerLevel level = player.serverLevel();

        BlackHole hole = new BlackHole(level, player.getUUID(), 200);
        blackHoles.put(uuid, hole);

        level.playSound(null, player.blockPosition(),
                SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 3.0f, 0.5f);
    }

    // ==================== I：深渊视线模式 ====================
    public static void executeI(ServerPlayer player) {
        if (!isSkirk(player)) return;

        UUID uuid = player.getUUID();
        boolean current = sightModeActive.getOrDefault(uuid, false);
        boolean newState = !current;
        sightModeActive.put(uuid, newState);

        com.qiuym.qiusmob.network.NetworkHandler.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.qiuym.qiusmob.network.SightModePacket(newState)
        );

        player.displayClientMessage(
                Component.literal(newState ? "§5§l【深渊视线】§r 开启" : "§5§l【深渊视线】§r 关闭"),
                true
        );

        ServerLevel level = player.serverLevel();
        if (newState) {
            level.playSound(null, player.blockPosition(),
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2.0f, 1.5f);
        }
    }

    // ==================== 视线发射 ====================
    public static void fireSightBeam(ServerPlayer player) {
        if (!isSkirk(player)) return;
        if (!sightModeActive.getOrDefault(player.getUUID(), false)) return;

        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double maxDist = 40.0;

        for (double d = 1.0; d <= maxDist; d += 0.5) {
            Vec3 pos = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.PORTAL,
                    pos.x, pos.y, pos.z,
                    3, 0.1, 0.1, 0.1, 0.05);
        }

        Vec3 end = eye.add(look.scale(maxDist));
        AABB hitBox = new AABB(eye, end).inflate(1.5);
        List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, hitBox);
        for (LivingEntity target : hits) {
            if (target == player) continue;
            if (target instanceof ServerPlayer other) {
                var team = player.getTeam();
                var otherTeam = other.getTeam();
                if (team != null && team == otherTeam && !team.isAllowFriendlyFire()) {
                    continue;
                }
            }
            target.hurt(player.damageSources().magic(), 500.0f);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX(), target.getY() + 1, target.getZ(),
                    20, 0.5, 0.5, 0.5, 0.1);
        }
    }

    // ==================== Tick 驱动 ====================
    public static void tick(ServerPlayer player) {
        UUID uuid = player.getUUID();

        int nCd = nCooldown.getOrDefault(uuid, 0);
        if (nCd > 0) nCooldown.put(uuid, nCd - 1);

        int jCd = jCooldown.getOrDefault(uuid, 0);
        if (jCd > 0) jCooldown.put(uuid, jCd - 1);

        BlackHole hole = blackHoles.get(uuid);
        if (hole != null) {
            boolean alive = hole.tick(player);
            if (!alive) blackHoles.remove(uuid);
        }
    }

    // ==================== 辅助 ====================
    private static boolean isSkirk(ServerPlayer player) {
        return PlayerConvertToQiusMob.isTransformed(player)
                && PlayerConvertToQiusMob.getTransformType(player)
                    == PlayerConvertToQiusMob.TransformType.SKIRK;
    }

    // ==================== 黑洞 ====================
    private static class BlackHole {
        final ServerLevel level;
        final UUID ownerUUID;
        int remainingTicks;

        BlackHole(ServerLevel level, UUID ownerUUID, int duration) {
            this.level = level;
            this.ownerUUID = ownerUUID;
            this.remainingTicks = duration;
        }

        boolean tick(ServerPlayer owner) {
            if (remainingTicks <= 0) return false;
            if (owner == null || !owner.isAlive()) return false;
            remainingTicks--;

            // 黑洞中心：玩家头顶 20 格
            Vec3 center = owner.position().add(0, 20, 0);

            // 渲染半径 10
            double renderRadius = 10.0;

            // 球形粒子
            for (int i = 0; i < 150; i++) {
                double theta = level.random.nextDouble() * Math.PI * 2;
                double phi = Math.acos(2 * level.random.nextDouble() - 1);
                double r = renderRadius * Math.cbrt(level.random.nextDouble());
                double x = center.x + r * Math.sin(phi) * Math.cos(theta);
                double y = center.y + r * Math.cos(phi);
                double z = center.z + r * Math.sin(phi) * Math.sin(theta);

                level.sendParticles(ParticleTypes.PORTAL, x, y, z, 1, 0, 0, 0, 0);
                if (i % 5 == 0) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0, 0, 0, 0);
                }
            }

            // 吸引范围 80 格（160 立方体）
            AABB attractBox = AABB.ofSize(center, 160, 160, 160);
            List<Entity> entities = new ArrayList<>(level.getEntitiesOfClass(Entity.class, attractBox));

            for (Entity e : entities) {
                if (e.getUUID().equals(ownerUUID)) continue;
                if (e instanceof ServerPlayer other) {
                    var team = owner.getTeam();
                    var otherTeam = other.getTeam();
                    if (team != null && team == otherTeam && !team.isAllowFriendlyFire()) {
                        continue;
                    }
                }

                double dist = e.position().distanceTo(center);
                if (dist < 0.01) continue;

                // 掉落物：直接销毁
                if (e instanceof ItemEntity item) {
                    item.discard();
                    level.sendParticles(ParticleTypes.PORTAL,
                            item.getX(), item.getY(), item.getZ(),
                            20, 0.3, 0.3, 0.3, 0.2);
                    continue;
                }

                if (!(e instanceof LivingEntity living)) continue;

                // teleportTo 强制拉向中心
                Vec3 toCenter = center.subtract(e.position());
                Vec3 step = toCenter.normalize().scale(1.5);
                living.teleportTo(
                        living.getX() + step.x,
                        living.getY() + step.y,
                        living.getZ() + step.z
                );
                living.setDeltaMovement(0, 0, 0);
                living.hurtMarked = true;

                // 进入 5 格内：每 tick 99999 伤害 + 粒子
                if (dist < 5.0) {
                    living.hurt(owner.damageSources().magic(), 99999.0f);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            living.getX(), living.getY() + 1, living.getZ(),
                            20, 0.5, 0.5, 0.5, 0.1);
                    level.sendParticles(ParticleTypes.PORTAL,
                            living.getX(), living.getY() + 1, living.getZ(),
                            30, 0.5, 0.5, 0.5, 0.2);
                }
            }

            return true;
        }
    }
}