package com.qiuym.qiusmob.entity.skirk;

public enum SkirkPhase {
    DORMANT,        // 沉睡：站立不动，不攻击
    AWAKENING,      // 觉醒：升空、锁血、觉醒伤害
    AWAKENED,       // 觉醒后：正常战斗
    JUDGMENT,       // 审判（<2500）
    ANNIHILATION    // 终焉（<1000）
}