package com.qiuym.qiusmob.entity;

import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.entity.ai.BossAI;
import com.qiuym.qiusmob.entity.ai.LookGoal;
import com.qiuym.qiusmob.EntityRegister;
import com.qiuym.qiusmob.QiusMob;
import com.qiuym.qiusmob.entity.consciousness.AbyssConsciousnessEntity;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class Death extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.qiusmob.death"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS
    );

    public Death(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 480.6D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }

        if (level() instanceof ServerLevel serverLevel) {
            GameRules.BooleanValue rule = serverLevel.getGameRules()
                    .getRule(ModEvents.CAN_ATTACK_BOSS_KEY);
            if (rule.get()) {
                return super.hurt(source, amount);
            }
        }

        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }
    
    @Override
    public void die(DamageSource source) {
        super.die(source);
    
        if (source.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = (ServerLevel) level();
    
            int hash = player.getUUID().hashCode();
            level.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(hash, null);
            level.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).set(0, null);
    
            // 生成深渊意识
            AbyssConsciousnessEntity consciousness = EntityRegister.ABYSS_CONSCIOUSNESS.get().create(level);
            if (consciousness != null) {
                consciousness.moveTo(player.getX() - 2, player.getY(), player.getZ() - 2,
                        player.getYRot(), 0);
                consciousness.bindTo(player);
                level.addFreshEntity(consciousness);
            }
    
            player.displayClientMessage(
                    Component.translatable("message.qiusmob.death.bound"), true);
    
            // 授勋成就
            Advancement adv = player.getServer().getAdvancements()
                    .getAdvancement(new ResourceLocation(QiusMob.MOD_ID, "challenge_death"));
            if (adv != null) {
                player.getAdvancements().award(adv, "kill_death");
            }
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(0, new LookGoal(this, 32.0, 30.0f));

        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));

        if (level() instanceof ServerLevel serverLevel) {
            GameRules.BooleanValue rule = serverLevel.getGameRules()
                    .getRule(ModEvents.ENABLE_BOSS_AI_KEY);
            if (rule.get()) {
                this.goalSelector.addGoal(1, new BossAI(this));
                this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
            }
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void tick() {
        super.tick();
        bossEvent.setProgress(getHealth() / getMaxHealth());

        float healthRatio = getHealth() / getMaxHealth();

        if (!level().isClientSide && getHealth() < getMaxHealth()) {
            float regenAmount;
            if (healthRatio > 0.6f) {
                regenAmount = 0.25f;
            } else if (healthRatio > 0.3f) {
                regenAmount = 0.5f;
            } else {
                regenAmount = 0.0f;
            }
            setHealth(Math.min(getHealth() + regenAmount, getMaxHealth()));
        }

        // ===== 水下呼吸：在水下不缺氧 =====
        if (isInWater()) {
            setAirSupply(getMaxAirSupply());
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();

        // ===== 水下移动 =====
        if (isInWater()) {
            LivingEntity target = getTarget();
            if (target != null && target.isAlive()) {
                Vec3 toTarget = new Vec3(
                    target.getX() - getX(),
                    target.getY() - getY(),
                    target.getZ() - getZ()
                );
                // 目标在上方 → 上浮
                if (toTarget.y > 0.5) {
                    setDeltaMovement(getDeltaMovement().add(0, 0.04, 0));
                }
                // 目标在下方 → 下沉（不主动下潜，只保持不动）
                // 水平移动由 BossAI 负责
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "death_ctrl", 5, state -> {
            if (state.isMoving()) {
                state.setAnimation(RawAnimation.begin().thenLoop("walk"));
            } else {
                state.setAnimation(RawAnimation.begin().thenLoop("idle"));
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}