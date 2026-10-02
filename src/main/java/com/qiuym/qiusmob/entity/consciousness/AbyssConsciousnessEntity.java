package com.qiuym.qiusmob.entity.consciousness;

import com.qiuym.qiusmob.consciousness.DialoguePool;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;

public class AbyssConsciousnessEntity extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(AbyssConsciousnessEntity.class, EntityDataSerializers.INT);

    private UUID targetUUID = null;
    private boolean isDying = false;
    private int dyingTicks = 0;
    private boolean isSpawning = false;
    private int spawningTicks = 0;
    private boolean isReaching = false;
    private int reachingTicks = 0;
    private int lockTicks = 0;
    private int touchCooldown = 0;
    private int dialogueCooldown = 600;
    private int pathFailTicks = 0;
    private boolean wasOffline = false;

    private LivingEntity pendingAttackTarget = null;
    private int pendingAttackTicks = 0;
    private int particleSkillCooldown = 0;

    public AbyssConsciousnessEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setNoGravity(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, ConsciousnessPhase.NO_TARGET.ordinal());
    }

    // ===== 唯一性检查 =====
    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (level().isClientSide()) return;

        // 检查世界中是否已存在另一个意识
        List<AbyssConsciousnessEntity> others = level().getEntitiesOfClass(
                AbyssConsciousnessEntity.class,
                new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000),
                e -> e != this && e.isAlive()
        );

        if (!others.isEmpty()) {
            // 已经存在，移除自己
            this.discard();
        }
    }

    public ConsciousnessPhase getPhase() {
        return ConsciousnessPhase.values()[this.entityData.get(DATA_PHASE)];
    }

    public void setPhase(ConsciousnessPhase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
    }

    private Player getTargetPlayer() {
        if (targetUUID == null) return null;
        if (!(level() instanceof ServerLevel sl)) return null;
        return sl.getPlayerByUUID(targetUUID);
    }

    public boolean isBoundTo(Player player) {
        return getPhase() == ConsciousnessPhase.BOUND
                && targetUUID != null
                && targetUUID.equals(player.getUUID());
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public void bindTo(Player player) {
        this.targetUUID = player.getUUID();
        setPhase(ConsciousnessPhase.BOUND);
        setHealth(getMaxHealth());
    }

    public void unbind() {
        this.targetUUID = null;
        setPhase(ConsciousnessPhase.NO_TARGET);
    }

    // ===== 受伤 =====
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDying) return false;

        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            this.remove(Entity.RemovalReason.KILLED);
            return true;
        }

        if (getPhase() == ConsciousnessPhase.NO_TARGET) {
            if (source.getEntity() instanceof Player) {
                return super.hurt(source, amount);
            }
            return false;
        }

        Entity attacker = source.getEntity();
        Player target = getTargetPlayer();

        if (attacker == target) {
            if (touchCooldown <= 0 && !isReaching) {
                triggerTouchEvent();
            }
            sendDialogue("dialogue.qiusmob.consciousness.hit_by_target");
            return false;
        }

        if (attacker instanceof LivingEntity living) {
            sendDialogue("dialogue.qiusmob.consciousness.angry_other");
            scheduleCounterAttack(living);
            return false;
        }

        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (getPhase() == ConsciousnessPhase.BOUND) return true;
        return super.isInvulnerableTo(source);
    }

    public void onTargetHurt(LivingEntity attacker) {
        if (!(level() instanceof ServerLevel)) return;
        if (attacker == this) return;
        if (!attacker.isAlive()) return;

        String[] pool = {
                "dialogue.qiusmob.consciousness.protect_target",
                "dialogue.qiusmob.consciousness.protect_target2"
        };
        sendDialogue(pool[random.nextInt(pool.length)]);

        scheduleCounterAttack(attacker);
    }

    // ===== 死亡 =====
    @Override
    public void die(DamageSource source) {
        if (isDying) return;

        if (getPhase() == ConsciousnessPhase.NO_TARGET) {
            isDying = true;
            dyingTicks = 50;
            setHealth(1.0f);
            triggerAnim("action", "dead");

            Player nearest = level().getNearestPlayer(this, 64.0);
            if (nearest != null) {
                targetUUID = nearest.getUUID();
                setPhase(ConsciousnessPhase.BOUND);
                setHealth(getMaxHealth());
                sendDialogue("dialogue.qiusmob.consciousness.first_death");
            }
            return;
        }
    }

    // ===== 触摸事件 =====
    private void triggerTouchEvent() {
        Player target = getTargetPlayer();
        if (target == null) return;

        isReaching = true;
        reachingTicks = 40;
        lockTicks = 20;
        touchCooldown = 200 + random.nextInt(200);

        Vec3 front = target.position().add(target.getLookAngle().scale(1.0));
        teleportTo(front.x, front.y, front.z);

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 240, 0, false, true));

        triggerAnim("action", "reach");
        sendDialogue("dialogue.qiusmob.consciousness.touch");
    }

    private void tickReach() {
        if (!isReaching) return;

        reachingTicks--;

        if (lockTicks > 0) {
            Player target = getTargetPlayer();
            if (target != null) {
                Vec3 front = position().add(getLookAngle().scale(1.0));
                target.teleportTo(front.x, front.y, front.z);
            }
            lockTicks--;
        }

        if (reachingTicks <= 0) {
            isReaching = false;
        }
    }

    // ===== 反击 =====
    private void scheduleCounterAttack(LivingEntity attacker) {
        if (particleSkillCooldown > 0) return;
        pendingAttackTarget = attacker;
        pendingAttackTicks = 10;
        particleSkillCooldown = 60;
    }

    private void tickCounterAttack() {
        if (particleSkillCooldown > 0) particleSkillCooldown--;

        if (pendingAttackTarget == null) return;
        pendingAttackTicks--;
        if (pendingAttackTicks > 0) return;

        if (pendingAttackTarget.isAlive()) {
            releaseRandomParticleSkill(pendingAttackTarget);
        }
        pendingAttackTarget = null;
    }

    private void releaseRandomParticleSkill(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return;

        int skill = random.nextInt(5);
        Vec3 pos = target.position();

        switch (skill) {
            case 0 -> {
                for (int i = 0; i < 100; i++) {
                    double theta = random.nextDouble() * Math.PI * 2;
                    double phi = Math.acos(2 * random.nextDouble() - 1);
                    double r = 2.0;
                    sl.sendParticles(ParticleTypes.PORTAL,
                            pos.x + r * Math.sin(phi) * Math.cos(theta),
                            pos.y + 1 + r * Math.cos(phi),
                            pos.z + r * Math.sin(phi) * Math.sin(theta),
                            1, 0, 0, 0, 0);
                }
                target.hurt(damageSources().magic(), 500.0f);
            }
            case 1 -> {
                for (double d = 0; d < 6.0; d += 0.2) {
                    double angle = d * Math.PI;
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.x + Math.cos(angle) * d,
                            pos.y + d,
                            pos.z + Math.sin(angle) * d,
                            2, 0, 0, 0, 0);
                }
                target.hurt(damageSources().magic(), 300.0f);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            }
            case 2 -> {
                for (int i = 0; i < 360; i += 10) {
                    double angle = Math.toRadians(i);
                    sl.sendParticles(ParticleTypes.SOUL,
                            pos.x + Math.cos(angle) * 3,
                            pos.y + 1,
                            pos.z + Math.sin(angle) * 3,
                            3, 0, 0, 0, 0);
                }
                target.hurt(damageSources().magic(), 400.0f);
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
            }
            case 3 -> {
                for (double y = 0; y < 5.0; y += 0.3) {
                    sl.sendParticles(ParticleTypes.ENCHANT,
                            pos.x, pos.y + y, pos.z,
                            5, 0.3, 0.1, 0.3, 0.05);
                }
                target.hurt(damageSources().magic(), 350.0f);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0));
            }
            case 4 -> {
                for (int i = 0; i < 80; i++) {
                    double theta = random.nextDouble() * Math.PI * 2;
                    double phi = Math.acos(2 * random.nextDouble() - 1);
                    double r = 3.0 - i * 0.03;
                    sl.sendParticles(ParticleTypes.REVERSE_PORTAL,
                            pos.x + r * Math.sin(phi) * Math.cos(theta),
                            pos.y + 1 + r * Math.cos(phi),
                            pos.z + r * Math.sin(phi) * Math.sin(theta),
                            1, 0, 0, 0, 0);
                }
                target.hurt(damageSources().magic(), 600.0f);
            }
        }

        sl.playSound(null, blockPosition(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.0f, 0.5f);
    }

    // ===== 跟随 =====
    private void tickFollow() {
        if (getPhase() != ConsciousnessPhase.BOUND) return;
        Player target = getTargetPlayer();
        if (target == null) return;

        double dist = distanceTo(target);

        if (dist > 15) {
            teleportNear(target);
            return;
        }

        if (dist < 5.0) {
            getNavigation().stop();
            pathFailTicks = 0;
            return;
        }

        getNavigation().moveTo(target, 1.0);

        if (getNavigation().isDone()) {
            pathFailTicks++;
            if (pathFailTicks > 100) {
                teleportNear(target);
                pathFailTicks = 0;
            }
        } else {
            pathFailTicks = 0;
        }
    }

    private void teleportNear(Player target) {
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = 3.0 + random.nextDouble() * 2.0;
        double x = target.getX() + Math.cos(angle) * radius;
        double z = target.getZ() + Math.sin(angle) * radius;
        teleportTo(x, target.getY(), z);
        triggerSpawn();
    }

    private void triggerSpawn() {
        isSpawning = true;
        spawningTicks = 50;
        triggerAnim("action", "spawn");
    }

    // ===== 头部朝向（只转头） =====
    private void tickLookAt() {
        LivingEntity lookTarget;

        if (getPhase() == ConsciousnessPhase.BOUND) {
            lookTarget = getTargetPlayer();
        } else {
            lookTarget = level().getNearestPlayer(this, 64.0);
        }

        if (lookTarget != null) {
            double dx = lookTarget.getX() - this.getX();
            double dz = lookTarget.getZ() - this.getZ();
            double dy = (lookTarget.getY() + lookTarget.getEyeHeight())
                      - (this.getY() + this.getEyeHeight());
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));

            this.yHeadRot = targetYaw;
            this.setXRot(targetPitch);
        }
    }

    // ===== 台词 =====
    private void tickDialogue() {
        if (dialogueCooldown > 0) {
            dialogueCooldown--;
            return;
        }
        dialogueCooldown = 600 + random.nextInt(600);

        if (getPhase() == ConsciousnessPhase.BOUND) {
            sendDialogue(DialoguePool.randomIdleWatch());
        }
    }

    private void sendDialogue(String key) {
        Player target = getTargetPlayer();
        if (target instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(key), false);
        }
    }

    // ===== 上线欢迎 =====
    private void tickOnlineWelcome() {
        Player target = getTargetPlayer();
        if (target == null) {
            wasOffline = true;
            return;
        }
        if (wasOffline) {
            wasOffline = false;
            sendDialogue("dialogue.qiusmob.consciousness.welcome_back");
        }
    }

    // ===== Tick =====
    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) return;

        if (isDying) {
            dyingTicks--;
            if (dyingTicks <= 0) {
                isDying = false;
                triggerSpawn();
            }
        }
        if (isSpawning) {
            spawningTicks--;
            if (spawningTicks <= 0) isSpawning = false;
        }
        if (touchCooldown > 0) touchCooldown--;

        tickReach();
        tickCounterAttack();
        tickLookAt();

        if (lockTicks > 0) return;

        tickFollow();
        tickDialogue();
        tickOnlineWelcome();

        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.PORTAL,
                    getX(), getY() + 1, getZ(),
                    3, 0.3, 0.3, 0.3, 0.05);
        }
    }

    // ===== 动画 =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(RawAnimation.begin()
                        .thenLoop("animation.abyss_consciousness.walk"));
            }
            return state.setAndContinue(RawAnimation.begin()
                    .thenLoop("animation.abyss_consciousness.idle"));
        }));

        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("spawn", RawAnimation.begin()
                        .thenPlay("animation.abyss_consciousness.spawn"))
                .triggerableAnim("dead", RawAnimation.begin()
                        .thenPlay("animation.abyss_consciousness.dead"))
                .triggerableAnim("reach", RawAnimation.begin()
                        .thenPlay("animation.abyss_consciousness.reach")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ===== NBT =====
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", getPhase().ordinal());
        if (targetUUID != null) tag.putUUID("Target", targetUUID);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Phase")) {
            setPhase(ConsciousnessPhase.values()[tag.getInt("Phase")]);
        }
        if (tag.contains("Target")) {
            targetUUID = tag.getUUID("Target");
        }
    }
}