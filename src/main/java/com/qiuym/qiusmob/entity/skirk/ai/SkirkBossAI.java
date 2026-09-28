package com.qiuym.qiusmob.entity.skirk.ai;

import com.qiuym.qiusmob.entity.skirk.SkirkEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class SkirkBossAI extends Goal {

    private final SkirkEntity boss;

    public SkirkBossAI(SkirkEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return false;
    }

    @Override
    public void tick() {
        // 留空，后续填充技能
    }
}