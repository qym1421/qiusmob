package com.qiuym.qiusmob.entity.ai;

import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.entity.Death;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class BossAI extends Goal {
    private final Death mob;
    private LivingEntity target;
    private int attackCooldown = 0;
    private int phaseTimer = 0;
    private int abilityCooldown = 0;

    // ========== 范围参数 ==========
    private static final double FOLLOW_RANGE = 48.0;    // 索敌范围（格）
    private static final double ATTACK_RANGE = 48.0;    // 攻击范围（格），超出则放弃目标
    private static final double MELEE_RANGE = 6.0;      // 近战范围（格）
    private static final double AOE_RANGE = 16.0;       // 范围技能范围（格）
    private static final double LEAP_RANGE = 22.0;      // 跳跃攻击范围（格）

    // ========== 伤害参数 ==========
    private static final int MELEE_DAMAGE = 748;         // 近战伤害（普通攻击）
    private static final int LEAP_DAMAGE = 780;          // 跳跃猛击伤害（跃击）
    private static final int AOE_DAMAGE = 480;           // 范围爆发伤害（AOE）
    private static final int LIGHTNING_DAMAGE = 180;      // 雷霆万钧伤害（闪电）
    private static final int DIVINE_DAMAGE = 280;         // 神之惩戒伤害（天罚）
    private static final int JUDGMENT_DAMAGE = 740;      // 终末审判伤害（全屏大招）

    // ========== 冷却参数 ==========
    private static final int MELEE_COOLDOWN = 15;        // 近战冷却（tick）
    private static final int LEAP_COOLDOWN = 30;         // 跳跃猛击冷却（tick）
    private static final int AOE_COOLDOWN = 40;          // 范围爆发冷却（tick）
    private static final int LIGHTNING_COOLDOWN = 25;    // 雷霆万钧冷却（tick）
    private static final int JUDGMENT_COOLDOWN = 60;     // 终末审判冷却（tick）

    // ========== 移动参数 ==========
    private static final double MOVE_SPEED = 2.0;        // 移动速度

    public BossAI(Death mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    // ========== 判断是否应该攻击该实体 ==========
    private boolean shouldAttack(LivingEntity entity) {
        if (entity == null) return false;
        if (entity == mob) return false;
        if (!entity.isAlive()) return false;
        // 只对玩家做同盟检测
        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator() || player.isInvulnerable()) return false;
            if (mob.isAlliedTo(entity)) return false;
        }
        return true;
    }

    // ========== 检查是否允许自动索敌 ==========
    // 对应 gamerule: qiusmobDeathCanSubjectiveAttack
    // true  = 自动索敌
    // false = 不自动索敌，仅响应被动仇恨（setTarget）
    private boolean canAutoTarget() {
        if (mob.level() instanceof ServerLevel serverLevel) {
            GameRules.BooleanValue rule = serverLevel.getGameRules()
                    .getRule(ModEvents.DEATH_CAN_SUBJECTIVE_ATTACK_KEY);
            return rule.get();
        }
        return true;
    }

    // ========== 查找最近的可攻击目标 ==========
    private LivingEntity findNearestTarget() {
        List<LivingEntity> targets = mob.level().getEntitiesOfClass(
                LivingEntity.class,
                AABB.ofSize(mob.position(), FOLLOW_RANGE * 2, FOLLOW_RANGE, FOLLOW_RANGE * 2),
                entity -> entity != mob && entity.isAlive() && shouldAttack(entity)
        );

        if (!targets.isEmpty()) {
            targets.sort((a, b) -> Double.compare(
                    mob.distanceToSqr(a),
                    mob.distanceToSqr(b)
            ));
            return targets.get(0);
        }
        return null;
    }

    // ============================================================
    // 原版AI生命周期
    // ============================================================

    @Override
    public boolean canUse() {
        // ===== 1. 优先处理被动攻击（外部 setTarget） =====
        LivingEntity externalTarget = mob.getTarget();
        if (externalTarget != null && shouldAttack(externalTarget)) {
            target = externalTarget;
            return true;
        }

        // 外部目标无效 → 清空
        if (mob.getTarget() != null) {
            mob.setTarget(null);
        }

        // ===== 2. 检查是否允许自动索敌 =====
        if (!canAutoTarget()) {
            target = null;
            return false;
        }

        // ===== 3. 自动索敌 =====
        LivingEntity newTarget = findNearestTarget();
        if (newTarget != null) {
            target = newTarget;
            mob.setTarget(target);
            return true;
        }

        target = null;
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        // 目标无效 → 结束AI
        if (!shouldAttack(target)) {
            target = null;
            return false;
        }
        // 超出攻击范围 → 结束AI
        if (mob.distanceToSqr(target) > ATTACK_RANGE * ATTACK_RANGE) {
            target = null;
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        attackCooldown = 0;
        phaseTimer = 0;
        abilityCooldown = 0;
        mob.setAggressive(true);
        if (target != null) {
            mob.setTarget(target);
        }
    }

    @Override
    public void stop() {
        target = null;
        mob.setAggressive(false);
        mob.getNavigation().stop();
    }

    // ============================================================
    // 执行逻辑（不受 canAutoTarget 影响）
    // ============================================================

    @Override
    public void tick() {
        // 防御检查
        if (!shouldAttack(target)) {
            target = null;
            return;
        }

        phaseTimer++;
        if (abilityCooldown > 0) abilityCooldown--;
        if (attackCooldown > 0) attackCooldown--;

        double distance = mob.distanceToSqr(target);

        // ===== 距离判定 =====
        boolean inMeleeRange = distance <= MELEE_RANGE * MELEE_RANGE;
        boolean inAoeRange = distance <= AOE_RANGE * AOE_RANGE;
        boolean inLeapRange = distance <= LEAP_RANGE * LEAP_RANGE;

        // ===== 血量判定 =====
        float healthRatio = mob.getHealth() / mob.getMaxHealth();

        // ===== 血量低于10%：只执行终末审判，冷却为0 =====
        if (healthRatio <= 0.1f) {
            // 移动：靠近目标
            if (distance > MELEE_RANGE * MELEE_RANGE) {
                mob.getNavigation().moveTo(target, MOVE_SPEED);
            } else {
                mob.getNavigation().stop();
            }
            // 无限终末审判
            executeJudgment();
            spawnDivineAura();
            return;
        }

        // ===== 被动技能：雷霆万钧（固定间隔触发） =====
        if (healthRatio < 0.3f && abilityCooldown <= 0) {
            executeSummonLightning();
            abilityCooldown = LIGHTNING_COOLDOWN;
        }
        
        // ===== 移动与攻击逻辑 =====
        if (inMeleeRange) {
            // 近战范围 → 停止移动，近战攻击
            mob.getNavigation().stop();
            if (attackCooldown <= 0) {
                executeMelee(); // 普通攻击
            }
            // 顺便放AOE（如果冷却好了）
            if (abilityCooldown <= 0 && mob.getRandom().nextInt(3) == 0) {
                executeAOE(); // 范围爆发
                abilityCooldown = AOE_COOLDOWN;
            }
        } else if (inAoeRange) {
            // AOE范围（6~15格）→ 停止移动，远程放技能
            mob.getNavigation().stop();
            
            if (abilityCooldown <= 0) {
                // 随机选择远程技能
                int skill = mob.getRandom().nextInt(2);
                switch (skill) {
                    case 0 -> executeAOE();           // 范围爆发
                    case 1 -> {
                        if (inLeapRange) executeLeap(); // 跳跃猛击
                        else executeAOE();              // 跳跃范围外则放AOE
                    }
                }
                abilityCooldown = 10 + mob.getRandom().nextInt(10);
            }
        } else {
            // 超出AOE范围 → 靠近目标
            mob.getNavigation().moveTo(target, MOVE_SPEED);
        }

        // ===== 技能系统（周期性大招） =====
        if (abilityCooldown <= 0) {
            if (healthRatio <= 0.3) {
                executeJudgment(); // 终末审判
                abilityCooldown = JUDGMENT_COOLDOWN;
            } else if (healthRatio <= 0.6 && phaseTimer % 5 == 0) {
                executeDivinePunishment(); // 神之惩戒
                abilityCooldown = 30;
            }
        }

        spawnDivineAura(); // 神性光环
    }

    // ========== 技能 ==========

    // 普通攻击 —— 近战挥砍
    private void executeMelee() {
        if (attackCooldown > 0) return;
        attackCooldown = MELEE_COOLDOWN;

        if (mob.distanceToSqr(target) < MELEE_RANGE * MELEE_RANGE && shouldAttack(target)) {
            target.hurt(mob.damageSources().mobAttack(mob), MELEE_DAMAGE);
            target.knockback(3.0, target.getX() - mob.getX(), target.getZ() - mob.getZ());
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0f, 0.5f);
            spawnParticles(target.position(), ParticleTypes.SWEEP_ATTACK, 20);
        }
    }

    // 跳跃猛击 —— 跃向目标造成范围伤害
    private void executeLeap() {
        if (attackCooldown > 0) return;
        attackCooldown = LEAP_COOLDOWN;

        if (mob.distanceToSqr(target) < LEAP_RANGE * LEAP_RANGE) {
            Vec3 leapTarget = target.position();
            mob.setDeltaMovement(
                    (leapTarget.x - mob.getX()) * 0.6,
                    1.2,
                    (leapTarget.z - mob.getZ()) * 0.6
            );
            mob.hurtMarked = true;

            mob.level().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(mob.position(), 10, 5, 10))
                    .forEach(entity -> {
                        if (shouldAttack(entity)) {
                            entity.hurt(mob.damageSources().mobAttack(mob), LEAP_DAMAGE);
                        }
                    });

            spawnParticles(mob.position(), ParticleTypes.EXPLOSION_EMITTER, 30);
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.5f);
        }
    }

    // 范围爆发 —— 大范围AOE伤害
    private void executeAOE() {
        if (attackCooldown > 0) return;
        attackCooldown = AOE_COOLDOWN;

        mob.level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(mob.position(), AOE_RANGE, 8, AOE_RANGE))
                .forEach(entity -> {
                    if (shouldAttack(entity)) {
                        entity.hurt(mob.damageSources().mobAttack(mob), AOE_DAMAGE);
                        entity.knockback(2.0, entity.getX() - mob.getX(), entity.getZ() - mob.getZ());
                    }
                });

        spawnParticles(mob.position(), ParticleTypes.DRAGON_BREATH, 50);
        mob.level().playSound(null, mob.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 2.0f, 0.3f);
    }

    // 雷霆万钧 —— 召唤多道闪电
    private void executeSummonLightning() {
        if (attackCooldown > 0) return;
        attackCooldown = LIGHTNING_COOLDOWN;

        for (int i = 0; i < 5; i++) {
            double offsetX = mob.getRandom().nextGaussian() * 5;
            double offsetZ = mob.getRandom().nextGaussian() * 5;
            Vec3 strikePos = target.position().add(offsetX, 0, offsetZ);

            mob.level().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(strikePos, 4, 4, 4))
                    .forEach(entity -> {
                        if (shouldAttack(entity)) {
                            entity.hurt(mob.damageSources().lightningBolt(), LIGHTNING_DAMAGE);
                        }
                    });

            spawnParticles(strikePos, ParticleTypes.ELECTRIC_SPARK, 30);
        }

        mob.level().playSound(null, mob.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 3.0f, 0.5f);
    }

    // 神之惩戒 —— 天罚，压制范围内所有敌人
    private void executeDivinePunishment() {
        mob.level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(mob.position(), 25, 10, 25))
                .forEach(entity -> {
                    if (shouldAttack(entity)) {
                        entity.hurt(mob.damageSources().mobAttack(mob), DIVINE_DAMAGE);
                        entity.setDeltaMovement(0, -2, 0);
                        entity.hurtMarked = true;
                    }
                });

        spawnParticles(mob.position(), ParticleTypes.REVERSE_PORTAL, 40);
        mob.level().playSound(null, mob.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0f, 0.2f);
    }

    // 终末审判 —— 全屏大招，附带公告
    private void executeJudgment() {
        mob.level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(mob.position(), 50, 20, 50))
                .forEach(entity -> {
                    if (shouldAttack(entity)) {
                        entity.hurt(mob.damageSources().mobAttack(mob), JUDGMENT_DAMAGE);
                        entity.setDeltaMovement(0, 0.5, 0);
                        entity.hurtMarked = true;
                    }
                });

        spawnParticles(mob.position(), ParticleTypes.TOTEM_OF_UNDYING, 60);
        mob.level().playSound(null, mob.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0f, 0.3f);

        if (mob.level() instanceof ServerLevel serverLevel) {
            serverLevel.players().forEach(player ->
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§c§l『死亡君主』降下最终审判！"),
                            true
                    )
            );
        }

        abilityCooldown = JUDGMENT_COOLDOWN;
    }

    // ========== 辅助 ==========

    // 粒子特效
    private void spawnParticles(Vec3 pos, net.minecraft.core.particles.ParticleOptions particle, int count) {
        if (mob.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(particle, pos.x, pos.y + 1, pos.z, count, 0.5, 0.5, 0.5, 0.1);
        }
    }

    // 神性光环 —— 持续环绕粒子
    private void spawnDivineAura() {
        if (mob.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = mob.position();
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y + 2, pos.z,
                    2, 0.5, 0.5, 0.5, 0.05);
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    pos.x, pos.y + 0.5, pos.z,
                    1, 0.2, 0.2, 0.2, 0.02);
        }
    }
}