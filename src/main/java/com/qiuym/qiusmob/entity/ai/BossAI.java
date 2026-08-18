package com.qiuym.qiusmob.entity.ai;

import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.entity.Death;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;

import java.util.EnumSet;
import java.util.List;

public class BossAI extends Goal {
    private final Death mob;
    private final Player playerHost;
    private LivingEntity target;
    private int attackCooldown = 0;
    private int phaseTimer = 0;
    private int abilityCooldown = 0;

    // ========== 范围参数 ==========
    private static final double FOLLOW_RANGE = 48.0;    // 索敌范围
    private static final double ATTACK_RANGE = 48.0;    // 攻击范围
    private static final double MELEE_RANGE = 6.0;      // 近战范围
    private static final double AOE_RANGE = 16.0;       // 范围爆发范围
    private static final double LEAP_RANGE = 22.0;      // 跳跃猛击范围

    // ========== 伤害参数 ==========
    private static final int MELEE_DAMAGE = 748;         // 近战伤害（普通攻击）
    private static final int LEAP_DAMAGE = 780;          // 跳跃猛击伤害
    private static final int AOE_DAMAGE = 480;           // 范围爆发伤害
    private static final int LIGHTNING_DAMAGE = 180;     // 雷霆万钧伤害
    private static final int DIVINE_DAMAGE = 280;        // 神之惩戒伤害（天罚）
    private static final int JUDGMENT_DAMAGE = 740;      // 终末审判伤害（大招）

    // ========== 冷却参数 ==========
    private static final int MELEE_COOLDOWN = 15;        // 近战冷却
    private static final int LEAP_COOLDOWN = 30;         // 跳跃猛击冷却
    private static final int AOE_COOLDOWN = 40;          // 范围爆发冷却
    private static final int LIGHTNING_COOLDOWN = 25;    // 雷霆万钧冷却
    private static final int JUDGMENT_COOLDOWN = 60;     // 终末审判冷却
    private static final int DIVINE_COOLDOWN = 30;       // 神之惩戒冷却

    // ========== 移动参数 ==========
    private static final double MOVE_SPEED = 0.5;        // 移动速度

    // ========== 构造方法 ==========

    // 原版：Death 实体使用
    public BossAI(Death mob) {
        this.mob = mob;
        this.playerHost = null;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    // 玩家寄生模式：玩家使用
    public BossAI(Player player) {
        this.mob = null;
        this.playerHost = player;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    // ========== 获取执行者 ==========
    private LivingEntity getExecutor() {
        if (playerHost != null) return playerHost;
        return mob;
    }

    private Level getLevel() {
        LivingEntity executor = getExecutor();
        return executor != null ? executor.level() : null;
    }
    
    // ========== 获取目标 ==========
    public LivingEntity getTarget() {
        return target;
    }

    // ========== 判断目标是否有效 ==========
    public boolean hasValidTarget() {
        LivingEntity executor = getExecutor();
        if (executor == null) return false;
        if (target == null) return false;
        if (!target.isAlive()) return false;
        if (!shouldAttack(target)) return false;
        if (executor.distanceToSqr(target) > ATTACK_RANGE * ATTACK_RANGE) return false;
        return true;
    }

    // ========== 判断是否应该攻击该实体 ==========
    private boolean shouldAttack(LivingEntity entity) {
        if (entity == null) return false;
        LivingEntity executor = getExecutor();
        if (entity == executor) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator() || player.isInvulnerable()) return false;
        }
        
        Team executorTeam = executor.getTeam();
        Team targetTeam = entity.getTeam();
        if (executorTeam != null && targetTeam != null && executorTeam == targetTeam) {
            if (!executorTeam.isAllowFriendlyFire()) {
                return false;
            }
        }
        
        return true;
    }

    // ========== 检查是否允许自动索敌 ==========
    private boolean canAutoTarget() {
        LivingEntity executor = getExecutor();
        if (executor == null) return true;
        if (executor.level() instanceof ServerLevel serverLevel) {
            GameRules.BooleanValue rule = serverLevel.getGameRules()
                    .getRule(ModEvents.DEATH_CAN_SUBJECTIVE_ATTACK_KEY);
            return rule.get();
        }
        return true;
    }

    // ========== 查找最近的可攻击目标 ==========
    private LivingEntity findNearestTarget() {
        LivingEntity executor = getExecutor();
        if (executor == null) return null;

        List<LivingEntity> targets = executor.level().getEntitiesOfClass(
                LivingEntity.class,
                AABB.ofSize(executor.position(), FOLLOW_RANGE * 2, FOLLOW_RANGE, FOLLOW_RANGE * 2),
                entity -> entity != executor && entity.isAlive() && shouldAttack(entity)
        );

        if (!targets.isEmpty()) {
            targets.sort((a, b) -> Double.compare(
                    executor.distanceToSqr(a),
                    executor.distanceToSqr(b)
            ));
            return targets.get(0);
        }
        return null;
    }

    // ========== 设置目标（供外部调用） ==========
    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    // ========== 技能方法 ==========

    // 普通攻击 —— 近战挥砍
    public void executeMelee() {
        LivingEntity executor = getExecutor();
        if (executor == null) return;
        Level level = getLevel();
        if (level == null) return;

        if (attackCooldown > 0) return;
        attackCooldown = MELEE_COOLDOWN;

        if (target == null || !shouldAttack(target)) return;
        if (executor.distanceToSqr(target) > MELEE_RANGE * MELEE_RANGE) return;

        float damage = MELEE_DAMAGE;
        if (executor instanceof Player player) {
            target.hurt(level.damageSources().playerAttack(player), damage);
        } else if (executor instanceof Mob mob) {
            target.hurt(level.damageSources().mobAttack(mob), damage);
        } else {
            target.hurt(level.damageSources().generic(), damage);
        }
        target.knockback(3.0, target.getX() - executor.getX(), target.getZ() - executor.getZ());

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + 1, target.getZ(),
                    20, 1, 1, 1, 0.1);
            serverLevel.playSound(null, executor.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.5f);
        }
    }

    // 跳跃猛击 —— 跃向目标造成范围伤害（无目标时朝面朝方向跳跃）
    public void executeLeap() {
        LivingEntity executor = getExecutor();
        if (executor == null) return;
        Level level = getLevel();
        if (level == null) return;

        if (attackCooldown > 0) return;
        attackCooldown = LEAP_COOLDOWN;

        Vec3 leapTarget;
        
        // 如果有目标，跳向目标
        if (target != null && shouldAttack(target) && executor.distanceToSqr(target) <= LEAP_RANGE * LEAP_RANGE) {
            leapTarget = target.position();
        } else if (executor instanceof Player) {
            // 玩家模式：无目标时朝面朝方向跳跃
            Vec3 lookVec = executor.getLookAngle();
            leapTarget = executor.position().add(lookVec.scale(5.0));
        } else {
            // 非玩家无目标：不执行
            return;
        }

        // 执行跳跃
        executor.setDeltaMovement(
                (leapTarget.x - executor.getX()) * 0.4,
                1.0,
                (leapTarget.z - executor.getZ()) * 0.4
        );
        executor.hurtMarked = true;

        // 落地范围伤害
        AABB aabb = AABB.ofSize(executor.position(), 10, 5, 10);
        float damage = LEAP_DAMAGE;
        level.getEntitiesOfClass(LivingEntity.class, aabb).forEach(entity -> {
            if (entity != executor && shouldAttack(entity)) {
                if (executor instanceof Player player) {
                    entity.hurt(level.damageSources().playerAttack(player), damage);
                } else if (executor instanceof Mob mob) {
                    entity.hurt(level.damageSources().mobAttack(mob), damage);
                } else {
                    entity.hurt(level.damageSources().generic(), damage);
                }
            }
        });

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    executor.getX(), executor.getY(), executor.getZ(),
                    30, 1, 1, 1, 0.1);
            serverLevel.playSound(null, executor.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.5f);
        }
    }

    // 范围爆发 —— 大范围AOE伤害（以自己为中心）
    public void executeAOE() {
        LivingEntity executor = getExecutor();
        if (executor == null) return;
        Level level = getLevel();
        if (level == null) return;

        if (attackCooldown > 0) return;
        attackCooldown = AOE_COOLDOWN;

        // 以自己为中心，不需要 target
        AABB aabb = AABB.ofSize(executor.position(), AOE_RANGE * 2, 8, AOE_RANGE * 2);
        float damage = AOE_DAMAGE;
        level.getEntitiesOfClass(LivingEntity.class, aabb).forEach(entity -> {
            if (entity != executor && shouldAttack(entity)) {
                if (executor instanceof Player player) {
                    entity.hurt(level.damageSources().playerAttack(player), damage);
                } else if (executor instanceof Mob mob) {
                    entity.hurt(level.damageSources().mobAttack(mob), damage);
                } else {
                    entity.hurt(level.damageSources().generic(), damage);
                }
                entity.knockback(2.0, entity.getX() - executor.getX(), entity.getZ() - executor.getZ());
            }
        });

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                    executor.getX(), executor.getY() + 1, executor.getZ(),
                    50, AOE_RANGE, 4, AOE_RANGE, 0.1);
            serverLevel.playSound(null, executor.blockPosition(),
                    SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 2.0f, 0.3f);
        }
    }

    // 雷霆万钧 —— 被动技能（目标周围召唤闪电）
    private void executeLightning() {
        LivingEntity executor = getExecutor();
        if (executor == null) return;
        Level level = getLevel();
        if (level == null) return;

        if (target == null || !shouldAttack(target)) return;

        float damage = LIGHTNING_DAMAGE;
        for (int i = 0; i < 5; i++) {
            double offsetX = executor.getRandom().nextGaussian() * 5;
            double offsetZ = executor.getRandom().nextGaussian() * 5;
            Vec3 strikePos = target.position().add(offsetX, 0, offsetZ);

            AABB aabb = AABB.ofSize(strikePos, 4, 4, 4);
            level.getEntitiesOfClass(LivingEntity.class, aabb).forEach(entity -> {
                if (entity != executor && shouldAttack(entity)) {
                    if (executor instanceof Player player) {
                        entity.hurt(level.damageSources().playerAttack(player), damage);
                    } else if (executor instanceof Mob mob) {
                        entity.hurt(level.damageSources().mobAttack(mob), damage);
                    } else {
                        entity.hurt(level.damageSources().generic(), damage);
                    }
                }
            });

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        strikePos.x, strikePos.y + 1, strikePos.z,
                        30, 0.5, 0.5, 0.5, 0.1);
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, executor.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 3.0f, 0.5f);
        }
    }

    // 神之惩戒 —— 天罚（以目标为中心）
    private void executeDivinePunishment() {
        LivingEntity executor = getExecutor();
        if (executor == null) return;
        Level level = getLevel();
        if (level == null) return;

        if (abilityCooldown > 0) return;
        abilityCooldown = DIVINE_COOLDOWN;

        if (target == null || !shouldAttack(target)) return;

        AABB aabb = AABB.ofSize(target.position(), 25, 10, 25);
        float damage = DIVINE_DAMAGE;
        level.getEntitiesOfClass(LivingEntity.class, aabb).forEach(entity -> {
            if (entity != executor && shouldAttack(entity)) {
                if (executor instanceof Player player) {
                    entity.hurt(level.damageSources().playerAttack(player), damage);
                } else if (executor instanceof Mob mob) {
                    entity.hurt(level.damageSources().mobAttack(mob), damage);
                } else {
                    entity.hurt(level.damageSources().generic(), damage);
                }
                entity.setDeltaMovement(0, -2, 0);
                entity.hurtMarked = true;
            }
        });

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    target.getX(), target.getY() + 1, target.getZ(),
                    40, 1, 1, 1, 0.1);
            serverLevel.playSound(null, executor.blockPosition(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 0.2f);
        }
    }

    // 终末审判 —— 全屏大招（以目标为中心）
    public void executeJudgment() {
        LivingEntity executor = getExecutor();
        if (executor == null) return;
        Level level = getLevel();
        if (level == null) return;

        if (abilityCooldown > 0) return;
        abilityCooldown = JUDGMENT_COOLDOWN;

        if (target == null || !shouldAttack(target)) return;

        AABB aabb = AABB.ofSize(target.position(), 50, 20, 50);
        float damage = JUDGMENT_DAMAGE;
        level.getEntitiesOfClass(LivingEntity.class, aabb).forEach(entity -> {
            if (entity != executor && shouldAttack(entity)) {
                if (executor instanceof Player player) {
                    entity.hurt(level.damageSources().playerAttack(player), damage);
                } else if (executor instanceof Mob mob) {
                    entity.hurt(level.damageSources().mobAttack(mob), damage);
                } else {
                    entity.hurt(level.damageSources().generic(), damage);
                }
                entity.setDeltaMovement(0, 0.5, 0);
                entity.hurtMarked = true;
            }
        });

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    target.getX(), target.getY() + 1, target.getZ(),
                    60, 50, 10, 50, 0.1);
            serverLevel.playSound(null, executor.blockPosition(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.0f, 0.3f);
            serverLevel.players().forEach(p ->
                    p.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§c§l〖死亡君主〗降下最终审判！"),
                            true
                    )
            );
        }
    }

    // ========== 冷却管理 ==========
    private void tickCooldowns() {
        if (attackCooldown > 0) attackCooldown--;
        if (abilityCooldown > 0) abilityCooldown--;
    }

    // ========== 被动触发 ==========
    public void tickPassive() {
        LivingEntity executor = getExecutor();
        if (executor == null) return;

        tickCooldowns();

        // ===== 检查目标是否超出范围 =====
        if (target != null) {
            if (!target.isAlive() || !shouldAttack(target) || 
                executor.distanceToSqr(target) > ATTACK_RANGE * ATTACK_RANGE) {
                target = null;
                System.out.println("[QiusMob] 目标超出范围或无效，已清空");
            }
        }

        // 自动索敌（如果没有目标）
        if (target == null || !target.isAlive() || !shouldAttack(target)) {
            if (canAutoTarget()) {
                target = findNearestTarget();
            }
        }

        if (target == null || !shouldAttack(target)) return;

        float healthRatio = executor.getHealth() / executor.getMaxHealth();

        // 被动技能：雷霆万钧（血量 ≤ 30% 自动触发）
        phaseTimer++;
        if (healthRatio <= 0.3f && phaseTimer >= LIGHTNING_COOLDOWN) {
            executeLightning();
            phaseTimer = 0;
        }

        // 阶段技能：神之惩戒（血量 ≤ 60% 自动触发）
        if (healthRatio <= 0.6f) {
            executeDivinePunishment();
        }
    }

    // ============================================================
    // 原版AI生命周期（仅用于 Death 实体模式）
    // ============================================================

    @Override
    public boolean canUse() {
        if (playerHost != null) return false;

        LivingEntity externalTarget = mob.getTarget();
        if (externalTarget != null && shouldAttack(externalTarget)) {
            target = externalTarget;
            return true;
        }
        if (mob.getTarget() != null) {
            mob.setTarget(null);
        }
        if (!canAutoTarget()) {
            target = null;
            return false;
        }
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
        if (playerHost != null) return false;
        if (!shouldAttack(target)) {
            target = null;
            return false;
        }
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
        if (mob != null) {
            mob.setAggressive(true);
        }
        if (target != null && mob != null) {
            mob.setTarget(target);
        }
    }

    @Override
    public void stop() {
        target = null;
        if (mob != null) {
            mob.setAggressive(false);
            mob.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        if (playerHost != null) return;

        if (!shouldAttack(target)) {
            target = null;
            return;
        }

        tickCooldowns();

        double distance = mob.distanceToSqr(target);
        boolean inMeleeRange = distance <= MELEE_RANGE * MELEE_RANGE;
        boolean inAoeRange = distance <= AOE_RANGE * AOE_RANGE;
        boolean inLeapRange = distance <= LEAP_RANGE * LEAP_RANGE;

        float healthRatio = mob.getHealth() / mob.getMaxHealth();

        if (healthRatio <= 0.1f) {
            if (distance > MELEE_RANGE * MELEE_RANGE) {
                mob.getNavigation().moveTo(target, MOVE_SPEED);
            } else {
                mob.getNavigation().stop();
            }
            executeJudgment();
            spawnDivineAura();
            return;
        }

        if (inMeleeRange) {
            mob.getNavigation().stop();
            if (attackCooldown <= 0) {
                executeMelee();
            }
            if (abilityCooldown <= 0 && mob.getRandom().nextInt(3) == 0) {
                executeAOE();
                abilityCooldown = AOE_COOLDOWN;
            }
        } else if (inAoeRange) {
            mob.getNavigation().stop();
            if (abilityCooldown <= 0) {
                int skill = mob.getRandom().nextInt(3);
                switch (skill) {
                    case 0 -> executeAOE();
                    case 1 -> executeLightning();
                    case 2 -> {
                        if (inLeapRange) executeLeap();
                        else executeLightning();
                    }
                }
                abilityCooldown = 10 + mob.getRandom().nextInt(10);
            }
        } else {
            mob.getNavigation().moveTo(target, MOVE_SPEED);
        }

        if (abilityCooldown <= 0) {
            if (healthRatio <= 0.3) {
                executeJudgment();
                abilityCooldown = JUDGMENT_COOLDOWN;
            } else if (healthRatio <= 0.6 && phaseTimer % 5 == 0) {
                executeDivinePunishment();
                abilityCooldown = 30;
            }
        }

        spawnDivineAura();
    }

    // ========== 辅助 ==========

    private void spawnDivineAura() {
        LivingEntity executor = getExecutor();
        if (executor == null) return;
        if (executor.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = executor.position();
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y + 2, pos.z,
                    2, 0.5, 0.5, 0.5, 0.05);
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    pos.x, pos.y + 0.5, pos.z,
                    1, 0.2, 0.2, 0.2, 0.02);
        }
    }
}