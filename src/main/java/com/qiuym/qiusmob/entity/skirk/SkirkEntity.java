package com.qiuym.qiusmob.entity.skirk;

import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.QiusMob;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SkirkEntity extends PathfinderMob {

    // ===== 同步数据 =====
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(SkirkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_LOCKED =
            SynchedEntityData.defineId(SkirkEntity.class, EntityDataSerializers.BOOLEAN);

    // ===== BossBar =====
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.qiusmob.skirk"),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS
    );

    // ===== 常量 =====
    public static final double MAX_HP = 5000.0;
    public static final double JUDGMENT_HP = 2500.0;
    public static final double ANNIHILATION_HP = 1000.0;
    public static final double AURA_RADIUS = 40.0;

    // ===== 觉醒状态 =====
    private int awakeningTick = 0;
    private boolean awakeningStarted = false;
    private int judgmentCooldown = 0;
    private int annihilationCooldown = 0;

    // ===== 主动技能状态 =====
    private int nCooldown = 0;
    private int jCooldown = 0;
    private Vec3 blackHoleCenter = null;
    private int blackHoleTicks = 0;
    private boolean sightModeActive = false;

    public SkirkEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 5000;
    }

    // ===== 属性 =====
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 100.0)
                .add(Attributes.FOLLOW_RANGE, 100.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 30.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, SkirkPhase.DORMANT.ordinal());
        this.entityData.define(DATA_LOCKED, false);
    }

    // ===== 阶段读写 =====
    public SkirkPhase getPhase() {
        return SkirkPhase.values()[this.entityData.get(DATA_PHASE)];
    }

    public void setPhase(SkirkPhase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
    }

    public boolean isLocked() {
        return this.entityData.get(DATA_LOCKED);
    }

    public void setLocked(boolean locked) {
        this.entityData.set(DATA_LOCKED, locked);
        if (locked) {
            this.setInvulnerable(true);
        } else {
            this.setInvulnerable(false);
        }
    }

    // ===== 原版 AI =====
    @Override
    protected void registerGoals() {
        // 近战攻击
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false));

        // 被攻击后反击
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        
        if (this.getPhase() == SkirkPhase.DORMANT) {
            return;
        }

        // 主动索敌：玩家
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, true,
                entity -> {
                    if (!(entity instanceof Player p)) return false;
                    if (this.isAlliedTo(p)) return false;
                    return true;
                }
        ));
        
        if (this.getPhase() == SkirkPhase.JUDGMENT) {
            return;
        }

        // 主动索敌：非玩家生物
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this, LivingEntity.class, true,
                entity -> {
                    if (entity == this) return false;
                    if (entity instanceof Player) return false;
                    if (isBossPart(entity)) return false;
                    if (this.isAlliedTo(entity)) return false;
                    return true;
                }
        ));
    }
    
    @Override
    public void die(DamageSource source) {
        super.die(source);
    
        if (source.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = (ServerLevel) level();
    
            int hash = player.getUUID().hashCode();
            level.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).set(hash, null);
            level.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(0, null);
    
            player.displayClientMessage(
                    Component.translatable("message.qiusmob.skirk.bound"), true);
    
            // 授勋成就
            Advancement adv = player.getServer().getAdvancements()
                    .getAdvancement(new ResourceLocation(QiusMob.MOD_ID, "challenge_skirk"));
            if (adv != null) {
                player.getAdvancements().award(adv, "kill_skirk");
            }
        }
    }

    // ===== 主 tick =====
    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            clientTick();
            return;
        }

        serverTick();
    }

    private void serverTick() {
        SkirkPhase phase = getPhase();

        bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (nCooldown > 0) nCooldown--;
        if (jCooldown > 0) jCooldown--;

        tickBlackHole();
        tickSightBeam();

        switch (phase) {
            case DORMANT -> dormantTick();
            case AWAKENING -> awakeningTick();
            case AWAKENED -> awakenedTick();
            case JUDGMENT -> judgmentTick();
            case ANNIHILATION -> annihilationTick();
        }
    }

    private void clientTick() {
        if (this.random.nextInt(3) == 0) {
            this.level().addParticle(
                    ParticleTypes.PORTAL,
                    this.getX() + (this.random.nextDouble() - 0.5) * 3,
                    this.getY() + this.random.nextDouble() * 2.5,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 3,
                    0, 0.1, 0
            );
        }
    }

    // ===== 沉睡阶段 =====
    private void dormantTick() {
        this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
    }

    // ===== 觉醒阶段 =====
    private void awakeningTick() {
        if (!awakeningStarted) {
            awakeningStarted = true;
            awakeningTick = 0;
            setLocked(true);

            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, this.blockPosition(),
                        SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 5.0f, 0.5f);
            }
        }

        awakeningTick++;

        if (awakeningTick <= 100) {
            double targetY = this.getY() + 10.0 / 100.0;
            this.teleportTo(this.getX(), targetY, this.getZ());
            this.setDeltaMovement(0, 0, 0);

            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        this.getX(), this.getY() + 1, this.getZ(),
                        20, 1, 1, 1, 0.2);
                serverLevel.sendParticles(ParticleTypes.SOUL,
                        this.getX(), this.getY() + 1, this.getZ(),
                        10, 0.5, 0.5, 0.5, 0.05);
            }
        }

        if (awakeningTick > 100 && awakeningTick <= 140) {
            this.setDeltaMovement(0, 0, 0);
        }

        if (awakeningTick == 120) {
            summonLightningRain(50, 99999.0f);
        }

        if (awakeningTick > 140 && awakeningTick <= 200) {
            double targetY = this.getY() - 10.0 / 60.0;
            this.teleportTo(this.getX(), targetY, this.getZ());
            this.setDeltaMovement(0, 0, 0);
        }

        if (awakeningTick >= 200) {
            setLocked(false);
            setPhase(SkirkPhase.AWAKENED);
            awakeningStarted = false;
        }
    }

    // ===== 觉醒后 =====
    private void awakenedTick() {
        if (this.getHealth() < ANNIHILATION_HP) {
            setPhase(SkirkPhase.ANNIHILATION);
            return;
        }
        if (this.getHealth() < JUDGMENT_HP) {
            setPhase(SkirkPhase.JUDGMENT);
            return;
        }

        executeN();
    }

    // ===== 审判阶段 =====
    private void judgmentTick() {
        if (this.getHealth() < ANNIHILATION_HP) {
            setPhase(SkirkPhase.ANNIHILATION);
            return;
        }

        executeN();
        executeJ();

        judgmentCooldown++;
        if (judgmentCooldown >= 40) {
            judgmentCooldown = 0;
            summonLightningRain(25, 99.0f);
        }
    }

    // ===== 终焉阶段 =====
    private void annihilationTick() {
        executeN();
        executeJ();
        executeI();

        annihilationCooldown++;
        if (annihilationCooldown >= 40) {
            annihilationCooldown = 0;
            summonLightningRain(50, 99999.0f);
        }
    }

    // ===== N：终焉审判 =====
    private void executeN() {
        if (nCooldown > 0) return;
        nCooldown = 10;

        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        AABB box = AABB.ofSize(this.position(), AURA_RADIUS * 2, AURA_RADIUS * 2, AURA_RADIUS * 2);
        List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, box);

        for (LivingEntity target : entities) {
            if (target == this) continue;
            if (isBossPart(target)) continue;
            if (this.isAlliedTo(target)) continue;

            target.hurt(this.damageSources().magic(), 99.0f);

            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    30, 0.5, 0.5, 0.5, 0.3);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    15, 0.3, 0.3, 0.3, 0.05);
        }

        serverLevel.playSound(null, this.blockPosition(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0f, 0.5f);
    }

    // ===== J：黑洞掌控 =====
    private void executeJ() {
        if (jCooldown > 0) return;
        if (blackHoleCenter != null) return;
        jCooldown = 300;

        blackHoleCenter = this.position().add(0, 20, 0);
        blackHoleTicks = 200;

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.blockPosition(),
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 3.0f, 0.5f);
        }
    }

    private void tickBlackHole() {
        if (blackHoleCenter == null || blackHoleTicks <= 0) {
            blackHoleCenter = null;
            return;
        }

        blackHoleTicks--;

        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        double renderRadius = 10.0;

        for (int i = 0; i < 150; i++) {
            double theta = this.random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * this.random.nextDouble() - 1);
            double r = renderRadius * Math.cbrt(this.random.nextDouble());
            double x = blackHoleCenter.x + r * Math.sin(phi) * Math.cos(theta);
            double y = blackHoleCenter.y + r * Math.cos(phi);
            double z = blackHoleCenter.z + r * Math.sin(phi) * Math.sin(theta);

            serverLevel.sendParticles(ParticleTypes.PORTAL, x, y, z, 1, 0, 0, 0, 0);
            if (i % 5 == 0) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0, 0, 0, 0);
            }
        }

        AABB attractBox = AABB.ofSize(blackHoleCenter, 160, 160, 160);
        List<Entity> entities = serverLevel.getEntitiesOfClass(Entity.class, attractBox);

        for (Entity e : entities) {
            if (e == this) continue;
            if (isBossPart(e)) continue;
            if (this.isAlliedTo(e)) continue;

            if (e instanceof ItemEntity item) {
                item.discard();
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        item.getX(), item.getY(), item.getZ(), 20, 0.3, 0.3, 0.3, 0.2);
                continue;
            }

            if (!(e instanceof LivingEntity living)) continue;

            Vec3 toCenter = blackHoleCenter.subtract(e.position());
            double dist = toCenter.length();
            if (dist < 0.01) continue;

            Vec3 step = toCenter.normalize().scale(1.5);
            living.teleportTo(
                    living.getX() + step.x,
                    living.getY() + step.y,
                    living.getZ() + step.z
            );
            living.setDeltaMovement(0, 0, 0);
            living.hurtMarked = true;

            if (dist < 5.0) {
                living.hurt(living.damageSources().magic(), 200.0f);
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        living.getX(), living.getY() + 1, living.getZ(),
                        15, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }

    // ===== I：深渊视线 =====
    private void executeI() {
        if (sightModeActive) return;
        sightModeActive = true;

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.blockPosition(),
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 2.0f, 1.5f);
        }
    }

    private void tickSightBeam() {
        if (!sightModeActive) return;
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 eye = this.getEyePosition();
        Vec3 look = this.getLookAngle();
        double maxDist = 40.0;

        for (double d = 1.0; d <= maxDist; d += 1.0) {
            Vec3 pos = eye.add(look.scale(d));
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.05);
        }

        Vec3 end = eye.add(look.scale(maxDist));
        AABB hitBox = new AABB(eye, end).inflate(1.5);
        List<LivingEntity> hits = serverLevel.getEntitiesOfClass(LivingEntity.class, hitBox);
        for (LivingEntity target : hits) {
            if (target == this) continue;
            if (isBossPart(target)) continue;
            if (this.isAlliedTo(target)) continue;
            target.hurt(this.damageSources().magic(), 500.0f);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX(), target.getY() + 1, target.getZ(),
                    20, 0.5, 0.5, 0.5, 0.1);
        }
    }

    // ===== 辅助 =====
    private boolean isBossPart(Entity e) {
        String className = e.getClass().getName();
        return className.contains("DevourerSegmentEntity");
    }

    // ===== 雷击辅助 =====
    private void summonLightningRain(int count, float damage) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double dist = this.random.nextDouble() * AURA_RADIUS;
            double x = this.getX() + Math.cos(angle) * dist;
            double z = this.getZ() + Math.sin(angle) * dist;
            double y = this.getY();

            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    x, y + 20, z, 30, 1, 20, 1, 0.5);

            AABB hitBox = AABB.ofSize(new Vec3(x, y, z), 5, 10, 5);
            List<LivingEntity> hit = serverLevel.getEntitiesOfClass(LivingEntity.class, hitBox);
            for (LivingEntity e : hit) {
                if (e == this) continue;
                if (isBossPart(e)) continue;
                if (this.isAlliedTo(e)) continue;
                e.hurt(this.damageSources().magic(), damage);
            }

            serverLevel.sendParticles(ParticleTypes.FLASH,
                    x, y, z, 1, 0, 0, 0, 0);
        }

        serverLevel.playSound(null, this.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 5.0f, 0.7f);
    }

    // ===== 受伤 =====
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isLocked()) {
            return false;
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            boolean canAttack = serverLevel.getGameRules()
                    .getRule(com.qiuym.qiusmob.ModEvents.CAN_ATTACK_BOSS_KEY).get();
            if (!canAttack) {
                return false;
            }
        }

        // 仇恨绑定：攻击者非盟友时设为目标
        if (source.getEntity() instanceof LivingEntity attacker) {
            if (attacker != this && !isBossPart(attacker) && !this.isAlliedTo(attacker)) {
                this.setTarget(attacker);
            }
        }

        boolean result = super.hurt(source, amount);

        if (result && getPhase() == SkirkPhase.DORMANT) {
            setPhase(SkirkPhase.AWAKENING);
            triggerAwakeningAOE();
        }

        return result;
    }

    private void triggerAwakeningAOE() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        AABB box = AABB.ofSize(this.position(), AURA_RADIUS * 2, AURA_RADIUS * 2, AURA_RADIUS * 2);
        List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity e : entities) {
            if (e == this) continue;
            if (isBossPart(e)) continue;
            if (this.isAlliedTo(e)) continue;
            e.hurt(this.damageSources().magic(), 99999.0f);
        }
    }

    // ===== BossBar =====
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    // ===== NBT =====
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SkirkPhase", getPhase().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SkirkPhase")) {
            setPhase(SkirkPhase.values()[tag.getInt("SkirkPhase")]);
        }
    }

    // ===== 其他 =====
    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource damageSource) {
        return false;
    }
}