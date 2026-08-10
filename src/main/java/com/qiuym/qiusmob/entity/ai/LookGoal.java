package com.qiuym.qiusmob.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class LookGoal extends Goal {
    private final Mob mob;
    private final double range;
    private final float speed;
    private LivingEntity target;

    public LookGoal(Mob mob, double range, float speed) {
        this.mob = mob;
        this.range = range;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 优先看攻击目标
        LivingEntity attackTarget = mob.getTarget();
        if (attackTarget != null && attackTarget.isAlive()) {
            target = attackTarget;
            return true;
        }

        // 没有攻击目标 → 看最近的玩家
        Player nearestPlayer = mob.level().getNearestPlayer(mob, range);
        if (nearestPlayer != null) {
            target = nearestPlayer;
            return true;
        }

        target = null;
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive()) {
            return false;
        }
        // 如果目标已死亡或太远，停止
        if (mob.distanceToSqr(target) > range * range) {
            return false;
        }
        return true;
    }

    @Override
    public void tick() {
        if (target != null) {
            mob.getLookControl().setLookAt(target, speed, speed);
        }
    }

    @Override
    public void stop() {
        target = null;
    }
}