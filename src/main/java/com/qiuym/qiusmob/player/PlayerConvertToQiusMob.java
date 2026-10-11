package com.qiuym.qiusmob.player;

import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.api.IEyeHeightSetter;
import com.qiuym.qiusmob.entity.ai.BossAI;
import com.qiuym.qiusmob.mixin.LivingEntityAccessor;
import com.qiuym.qiusmob.network.NetworkHandler;
import com.qiuym.qiusmob.network.SyncPacket;
import com.qiuym.qiusmob.network.TransformSyncPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerConvertToQiusMob {

    public enum TransformType {
        DEATH, SKIRK
    }

    public enum SkirkPhase {
        DORMANT, AWAKENING, AWAKENED, JUDGMENT, ANNIHILATION
    }

    private static final Map<UUID, Boolean> transformedMap = new HashMap<>();
    private static final Map<UUID, TransformType> transformTypeMap = new HashMap<>();
    private static final Map<UUID, BossAI> bossAIMap = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> bossEventMap = new HashMap<>();
    private static final Map<UUID, UUID> possessedMap = new HashMap<>();
    private static final Map<UUID, UUID> possessedTargetMap = new HashMap<>();
    private static final Map<UUID, Boolean> originalAwareMap = new HashMap<>();
    private static final Map<UUID, GameType> originalGameModeMap = new HashMap<>();
    private static final Map<UUID, Boolean> viewModeMap = new HashMap<>();
    private static final Map<UUID, SkirkPhase> skirkPhaseMap = new HashMap<>();
    private static final Map<UUID, Integer> skirkTimerMap = new HashMap<>();
    private static final Map<UUID, Integer> skirkAwakeningTickMap = new HashMap<>();
    private static final Map<UUID, Boolean> skirkAwakeningStartedMap = new HashMap<>();

    public static boolean isTransformed(Player player) {
        return transformedMap.getOrDefault(player.getUUID(), false);
    }

    public static TransformType getTransformType(Player player) {
        return transformTypeMap.getOrDefault(player.getUUID(), TransformType.DEATH);
    }

    public static boolean isPossessing(Player player) {
        return possessedMap.containsKey(player.getUUID());
    }

    public static UUID getPossessedTarget(Player player) {
        return possessedMap.get(player.getUUID());
    }

    public static boolean isViewLocked(Player player) {
        return viewModeMap.getOrDefault(player.getUUID(), true);
    }

    public static void setViewMode(Player player, boolean locked) {
        viewModeMap.put(player.getUUID(), locked);
    }

    private static void enforceBindingExclusivity(ServerPlayer player, TransformType type) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        int hash = player.getUUID().hashCode();
        switch (type) {
            case DEATH -> {
                serverLevel.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).set(0, null);
                serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(hash, null);
            }
            case SKIRK -> {
                serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(0, null);
                serverLevel.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).set(hash, null);
            }
        }
    }

    public static void setTransformed(Player player, boolean transformed) {
        setTransformed(player, transformed, TransformType.DEATH);
    }

    public static void setTransformed(Player player, boolean transformed, TransformType type) {
        if (player.level().isClientSide()) return;
        UUID uuid = player.getUUID();
        boolean currentState = transformedMap.getOrDefault(uuid, false);

        if (currentState == transformed) return;

        if (transformed) {
            if (player instanceof ServerPlayer serverPlayer) {
                enforceBindingExclusivity(serverPlayer, type);
            }
            transformedMap.put(uuid, true);
            transformTypeMap.put(uuid, type);
            applyTransformation(player, type);
        } else {
            TransformType oldType = getTransformType(player);
            transformedMap.put(uuid, false);
            transformTypeMap.remove(uuid);
            removeTransformation(player, oldType);
        }
    }

    public static void toggleTransformation(Player player) {
        setTransformed(player, !isTransformed(player), getTransformType(player));
    }

    private static void applyTransformation(Player player, TransformType type) {
        switch (type) {
            case DEATH -> applyDeathTransformation(player);
            case SKIRK -> applySkirkTransformation(player);
        }
    }

    private static void applyDeathTransformation(Player player) {
        UUID uuid = player.getUUID();
        Level level = player.level();
        if (level.isClientSide()) return;

        ServerBossEvent bossEvent = new ServerBossEvent(
                Component.translatable("entity.qiusmob.death"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );
        if (level instanceof ServerLevel serverLevel) {
            for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
                bossEvent.addPlayer(p);
            }
        }
        bossEventMap.put(uuid, bossEvent);

        BossAI bossAI = new BossAI(player);
        bossAIMap.put(uuid, bossAI);

        player.getAttributes().getInstance(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        player.getAttributes().getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(0.5D);
        player.setHealth(1000.0f);

        if (level instanceof ServerLevel serverLevel) {
            GameRules.BooleanValue rule = serverLevel.getGameRules()
                    .getRule(ModEvents.CAN_ATTACK_BOSS_KEY);
            player.setInvulnerable(!rule.get());
        }

        bossEvent.setProgress(player.getHealth() / player.getMaxHealth());

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    player.getX(), player.getY() + 1, player.getZ(),
                    50, 1, 1, 1, 0.1);
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            var server = serverPlayer.getServer();
            var level2 = serverPlayer.serverLevel();
            var gameRules = level2.getGameRules();
            gameRules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, server);
            server.getCommands().performPrefixedCommand(
                    serverPlayer.createCommandSourceStack(),
                    "effect give @s minecraft:night_vision infinite 0 true"
            );
            server.getCommands().performPrefixedCommand(
                    serverPlayer.createCommandSourceStack(),
                    "effect give @s minecraft:saturation infinite 0 true"
            );
            gameRules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(true, server);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getAbilities().mayfly = true;
            serverPlayer.getAbilities().flying = true;
            serverPlayer.onUpdateAbilities();
        }

        player.displayClientMessage(
                Component.literal("§c§l【QiusMod】 §r死亡君主降临！"),
                true
        );

        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new TransformSyncPacket(true));
        }
    }

    private static void applySkirkTransformation(Player player) {
        UUID uuid = player.getUUID();
        Level level = player.level();
        if (level.isClientSide()) return;

        ServerBossEvent bossEvent = new ServerBossEvent(
                Component.translatable("entity.qiusmob.skirk"),
                BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS
        );
        if (level instanceof ServerLevel serverLevel) {
            for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
                bossEvent.addPlayer(p);
            }
        }
        bossEventMap.put(uuid, bossEvent);

        player.getAttributes().getInstance(Attributes.MAX_HEALTH).setBaseValue(5000.0D);
        player.getAttributes().getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        player.getAttributes().getInstance(Attributes.ATTACK_DAMAGE).setBaseValue(99999.0D);

        // 不直接 setHealth(5000)，改为从当前血量开始每 tick +50
        // 初始化阶段为 DORMANT
        skirkPhaseMap.put(uuid, SkirkPhase.DORMANT);
        skirkTimerMap.put(uuid, 0);
        skirkAwakeningTickMap.put(uuid, 0);
        skirkAwakeningStartedMap.put(uuid, false);

        if (level instanceof ServerLevel serverLevel) {
            GameRules.BooleanValue rule = serverLevel.getGameRules()
                    .getRule(ModEvents.CAN_ATTACK_BOSS_KEY);
            player.setInvulnerable(!rule.get());
        }

        bossEvent.setProgress(1.0f);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 1, player.getZ(),
                    80, 2, 2, 2, 0.3);
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    player.getX(), player.getY() + 1, player.getZ(),
                    40, 1, 1, 1, 0.1);
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 2.0f, 0.7f);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            var server = serverPlayer.getServer();
            var level2 = serverPlayer.serverLevel();
            var gameRules = level2.getGameRules();
            gameRules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, server);
            server.getCommands().performPrefixedCommand(
                    serverPlayer.createCommandSourceStack(),
                    "effect give @s minecraft:night_vision infinite 0 true"
            );
            server.getCommands().performPrefixedCommand(
                    serverPlayer.createCommandSourceStack(),
                    "effect give @s minecraft:saturation infinite 0 true"
            );
            gameRules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(true, server);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getAbilities().mayfly = true;
            serverPlayer.getAbilities().flying = true;
            serverPlayer.onUpdateAbilities();
        }

        player.displayClientMessage(
                Component.literal("§5§l【QiusMod】 §r深渊孤月降临！"),
                true
        );

        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new TransformSyncPacket(true));
        }
    }

    private static void removeTransformation(Player player, TransformType type) {
        switch (type) {
            case DEATH -> removeDeathTransformation(player);
            case SKIRK -> removeSkirkTransformation(player);
        }
    }

    private static void removeDeathTransformation(Player player) {
        UUID uuid = player.getUUID();
        if (isPossessing(player)) releasePossess(player);
        bossAIMap.remove(uuid);
        ServerBossEvent bossEvent = bossEventMap.remove(uuid);
        if (bossEvent != null) bossEvent.removeAllPlayers();

        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setNoGravity(false);
        player.getAttributes().getInstance(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        player.getAttributes().getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(0.1D);
        player.setHealth(20.0f);

        clearEffects(player);
        if (player instanceof ServerPlayer serverPlayer) restoreFlight(serverPlayer);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    player.getX(), player.getY() + 1, player.getZ(),
                    30, 1, 1, 1, 0.1);
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        player.displayClientMessage(
                Component.literal("§e§l【QiusMod】 §r解除变形！"),
                true
        );

        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new TransformSyncPacket(false));
        }
    }

    private static void removeSkirkTransformation(Player player) {
        UUID uuid = player.getUUID();
        if (isPossessing(player)) releasePossess(player);
        ServerBossEvent bossEvent = bossEventMap.remove(uuid);
        if (bossEvent != null) bossEvent.removeAllPlayers();

        skirkPhaseMap.remove(uuid);
        skirkTimerMap.remove(uuid);
        skirkAwakeningTickMap.remove(uuid);
        skirkAwakeningStartedMap.remove(uuid);

        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setNoGravity(false);
        player.getAttributes().getInstance(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        player.getAttributes().getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(0.1D);
        player.getAttributes().getInstance(Attributes.ATTACK_DAMAGE).setBaseValue(1.0D);
        player.setHealth(20.0f);

        clearEffects(player);
        if (player instanceof ServerPlayer serverPlayer) restoreFlight(serverPlayer);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 1, player.getZ(),
                    50, 1, 1, 1, 0.2);
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        player.displayClientMessage(
                Component.literal("§5§l【QiusMod】 §r深渊退去！"),
                true
        );

        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new TransformSyncPacket(false));
        }
    }

    private static void clearEffects(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            var server = serverPlayer.getServer();
            var level = serverPlayer.serverLevel();
            var gameRules = level.getGameRules();
            gameRules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, server);
            server.getCommands().performPrefixedCommand(
                    serverPlayer.createCommandSourceStack(),
                    "effect clear @s minecraft:night_vision"
            );
            server.getCommands().performPrefixedCommand(
                    serverPlayer.createCommandSourceStack(),
                    "effect clear @s minecraft:saturation"
            );
            gameRules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(true, server);
        }
    }

    private static void restoreFlight(ServerPlayer serverPlayer) {
        if (serverPlayer.gameMode.getGameModeForPlayer() == GameType.CREATIVE ||
            serverPlayer.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            serverPlayer.getAbilities().mayfly = true;
            serverPlayer.getAbilities().flying = true;
        } else {
            serverPlayer.getAbilities().mayfly = false;
            serverPlayer.getAbilities().flying = false;
        }
        serverPlayer.onUpdateAbilities();
    }

    // ========== 夺舍 ==========

    public static void executePossess(ServerPlayer player, UUID targetUUID) {
        if (!isTransformed(player)) {
            player.displayClientMessage(Component.literal("§c§l【QiusMod】 §r请先变形！"), true);
            return;
        }
        if (isPossessing(player)) {
            releasePossess(player);
            return;
        }
        if (targetUUID == null) {
            player.displayClientMessage(Component.literal("§c§l【QiusMod】 §r没有找到目标！"), true);
            return;
        }

        Level level = player.level();
        if (level.isClientSide()) return;

        LivingEntity target = null;
        if (level instanceof ServerLevel serverLevel) {
            for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(player.position(), 64, 64, 64))) {
                if (entity.getUUID().equals(targetUUID)) {
                    target = entity;
                    break;
                }
            }
        }
        if (target == null) {
            player.displayClientMessage(Component.literal("§c§l【QiusMod】 §r目标已不存在！"), true);
            return;
        }
        if (!target.isAlive()) {
            player.displayClientMessage(Component.literal("§c§l【QiusMod】 §r目标已死亡！"), true);
            return;
        }
        if (target instanceof Player) {
            player.displayClientMessage(Component.literal("§c§l【QiusMod】 §r不能夺舍玩家！"), true);
            return;
        }

        possessEntity(player, target);
    }

    private static void possessEntity(ServerPlayer player, LivingEntity target) {
        UUID playerUUID = player.getUUID();
        UUID targetUUID = target.getUUID();

        possessedMap.put(playerUUID, targetUUID);
        possessedTargetMap.put(targetUUID, playerUUID);
        viewModeMap.put(playerUUID, true);

        if (target instanceof Mob mob) {
            originalAwareMap.put(targetUUID, mob.isNoAi());
            mob.setPersistenceRequired();
        }

        originalGameModeMap.put(playerUUID, player.gameMode.getGameModeForPlayer());
        player.setGameMode(GameType.SPECTATOR);

        ((IEyeHeightSetter) player).setEyeHeight(target.getEyeHeight());

        player.setPos(target.getX(), target.getY(), target.getZ());
        player.setYRot(target.getYRot());
        player.setXRot(target.getXRot());

        player.displayClientMessage(
                Component.literal("§a§l【QiusMod】 §r已夺舍 " + target.getName().getString() + "！按 U 解除"),
                true
        );
    }

    public static void releasePossess(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID playerUUID = player.getUUID();
        UUID targetUUID = possessedMap.remove(playerUUID);
        if (targetUUID == null) return;

        possessedTargetMap.remove(targetUUID);

        LivingEntity target = null;
        if (player.level() instanceof ServerLevel serverLevel) {
            for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(player.position(), 64, 64, 64))) {
                if (entity.getUUID().equals(targetUUID)) {
                    target = entity;
                    break;
                }
            }
        }

        if (target != null && target.isAlive()) {
            if (target instanceof Mob mob) {
                mob.setNoAi(originalAwareMap.getOrDefault(targetUUID, false));
                originalAwareMap.remove(targetUUID);
            }
            target.setInvulnerable(false);
        }

        GameType original = originalGameModeMap.remove(playerUUID);
        serverPlayer.setGameMode(original != null ? original : GameType.SURVIVAL);

        ((IEyeHeightSetter) player).resetEyeHeight();
        PlayerMovementState.remove(playerUUID);
        viewModeMap.remove(playerUUID);

        NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> serverPlayer),
                new SyncPacket(player.getX(), player.getY(), player.getZ(), 0, 0, 0, false)
        );

        serverPlayer.displayClientMessage(
                Component.literal("§e§l【QiusMod】 §r已解除夺舍！"),
                true
        );
    }

    public static void releasePossessByTarget(LivingEntity target) {
        UUID targetUUID = target.getUUID();
        UUID playerUUID = possessedTargetMap.remove(targetUUID);
        if (playerUUID == null) return;

        possessedMap.remove(playerUUID);

        if (target instanceof Mob mob) {
            mob.setNoAi(originalAwareMap.getOrDefault(targetUUID, false));
            originalAwareMap.remove(targetUUID);
        }
        target.setInvulnerable(false);

        Player player = target.level().getPlayerByUUID(playerUUID);
        if (player instanceof ServerPlayer serverPlayer) {
            GameType original = originalGameModeMap.remove(playerUUID);
            serverPlayer.setGameMode(original != null ? original : GameType.SURVIVAL);

            ((IEyeHeightSetter) player).resetEyeHeight();
            PlayerMovementState.remove(playerUUID);
            viewModeMap.remove(playerUUID);

            NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SyncPacket(player.getX(), player.getY(), player.getZ(), 0, 0, 0, false)
            );

            serverPlayer.displayClientMessage(
                    Component.literal("§c§l【QiusMod】 §r夺舍目标已死亡，自动解除！"),
                    true
            );
        }
    }

    // ========== 技能（仅死亡君主） ==========

    private static void invokeBossAI(Player player, String methodName) {
        if (!isTransformed(player) || getTransformType(player) != TransformType.DEATH) return;

        BossAI bossAI = bossAIMap.get(player.getUUID());
        if (bossAI == null) return;

        if (!methodName.equals("executeAOE") && !methodName.equals("executeLeap")) {
            if (bossAI.getTarget() == null) return;
        }

        try {
            AABB aabb = AABB.ofSize(player.position(), 48, 20, 48);
            LivingEntity nearestTarget = player.level().getEntitiesOfClass(LivingEntity.class, aabb)
                    .stream()
                    .filter(e -> e != player && e.isAlive())
                    .min((a, b) -> Double.compare(player.distanceToSqr(a), player.distanceToSqr(b)))
                    .orElse(null);

            if (nearestTarget != null) {
                Field targetField = BossAI.class.getDeclaredField("target");
                targetField.setAccessible(true);
                targetField.set(bossAI, nearestTarget);
            } else if (!methodName.equals("executeAOE") && !methodName.equals("executeLeap")) {
                return;
            }

            Method method = BossAI.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(bossAI);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void executeMelee(Player player) { invokeBossAI(player, "executeMelee"); }
    public static void executeLeap(Player player) { invokeBossAI(player, "executeLeap"); }
    public static void executeAOE(Player player) { invokeBossAI(player, "executeAOE"); }
    public static void executeUltimate(Player player) { invokeBossAI(player, "executeJudgment"); }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new Handler());
    }

    // ========== 丝柯克阶段机制 ==========

    private static void tickSkirkPhase(Player player, ServerLevel serverLevel) {
        UUID uuid = player.getUUID();
        SkirkPhase phase = skirkPhaseMap.getOrDefault(uuid, SkirkPhase.DORMANT);
        int timer = skirkTimerMap.getOrDefault(uuid, 0);

        // DORMANT 阶段：每 tick +50 血，满血后切换到 AWAKENED
        if (phase == SkirkPhase.DORMANT) {
            float current = player.getHealth();
            float max = player.getMaxHealth();
            if (current < max) {
                player.setHealth(Math.min(current + 50.0f, max));
            } else {
                skirkPhaseMap.put(uuid, SkirkPhase.AWAKENED);
                player.displayClientMessage(Component.literal("§5§l【深渊觉醒】"), true);
            }
            return;
        }

        if (phase == SkirkPhase.AWAKENING) {
            // 保留旧逻辑（如果以后想用）
            boolean started = skirkAwakeningStartedMap.getOrDefault(uuid, false);
            int awakeningTick = skirkAwakeningTickMap.getOrDefault(uuid, 0);

            if (!started) {
                skirkAwakeningStartedMap.put(uuid, true);
                skirkAwakeningTickMap.put(uuid, 0);
                player.setInvulnerable(true);

                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 5.0f, 0.5f);
                awakeningTick = 0;
            }

            awakeningTick++;

            if (awakeningTick <= 100) {
                double targetY = player.getY() + 25.0 / 100.0;
                player.teleportTo(player.getX(), targetY, player.getZ());
                player.setDeltaMovement(0, 0, 0);
                player.hurtMarked = true;

                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        player.getX(), player.getY() + 1, player.getZ(),
                        20, 1, 1, 1, 0.2);
                serverLevel.sendParticles(ParticleTypes.SOUL,
                        player.getX(), player.getY() + 1, player.getZ(),
                        10, 0.5, 0.5, 0.5, 0.05);
            }

            if (awakeningTick > 100 && awakeningTick <= 140) {
                player.setDeltaMovement(0, 0, 0);
                player.hurtMarked = true;
            }

            if (awakeningTick == 120) {
                summonLightningRain(player, serverLevel, 50, 99999.0f);
            }

            if (awakeningTick > 140 && awakeningTick <= 200) {
                double targetY = player.getY() - 10.0 / 60.0;
                player.teleportTo(player.getX(), targetY, player.getZ());
                player.setDeltaMovement(0, 0, 0);
                player.hurtMarked = true;
            }

            if (awakeningTick >= 200) {
                player.setInvulnerable(false);
                skirkPhaseMap.put(uuid, SkirkPhase.AWAKENED);
                skirkAwakeningStartedMap.put(uuid, false);
                skirkAwakeningTickMap.put(uuid, 0);
            } else {
                skirkAwakeningTickMap.put(uuid, awakeningTick);
            }
            return;
        }

        float health = player.getHealth();
        if (health < 1000.0f && phase != SkirkPhase.ANNIHILATION) {
            skirkPhaseMap.put(uuid, SkirkPhase.ANNIHILATION);
            skirkTimerMap.put(uuid, 0);
            player.displayClientMessage(Component.literal("§5§l【终焉降临】"), true);
            return;
        }
        if (health < 2500.0f && phase == SkirkPhase.AWAKENED) {
            skirkPhaseMap.put(uuid, SkirkPhase.JUDGMENT);
            skirkTimerMap.put(uuid, 0);
            player.displayClientMessage(Component.literal("§5§l【审判】"), true);
            return;
        }

        timer++;
        if (timer >= 40) {
            timer = 0;
            switch (phase) {
                case JUDGMENT -> summonLightningRain(player, serverLevel, 25, 99.0f);
                case ANNIHILATION -> summonLightningRain(player, serverLevel, 50, 99999.0f);
            }
        }
        skirkTimerMap.put(uuid, timer);

        if (phase == SkirkPhase.ANNIHILATION) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 1, player.getZ(),
                    5, 1, 1, 1, 0.1);
        }
    }

    private static void summonLightningRain(Player player, ServerLevel serverLevel, int count, float damage) {
        double radius = 40.0;
        for (int i = 0; i < count; i++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double dist = player.getRandom().nextDouble() * radius;
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            double y = player.getY();

            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    x, y + 20, z, 30, 1, 20, 1, 0.5);

            AABB hitBox = AABB.ofSize(new Vec3(x, y, z), 5, 10, 5);
            List<LivingEntity> hit = serverLevel.getEntitiesOfClass(LivingEntity.class, hitBox);
            for (LivingEntity e : hit) {
                if (e == player) continue;
                if (e instanceof Player other) {
                    var team = player.getTeam();
                    var otherTeam = other.getTeam();
                    if (team != null && team == otherTeam && !team.isAllowFriendlyFire()) {
                        continue;
                    }
                }
                e.hurt(player.damageSources().magic(), damage);
            }
            serverLevel.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        }

        serverLevel.playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 5.0f, 0.7f);
    }

    // ========== Tick 处理器 ==========

    public static class Handler {

        @SubscribeEvent
        public void onLivingHurt(LivingHurtEvent event) {
            if (event.getEntity().level().isClientSide()) return;
            if (!(event.getEntity() instanceof Player player)) return;
            if (!isTransformed(player)) return;
            if (getTransformType(player) != TransformType.SKIRK) return;

            UUID uuid = player.getUUID();
            SkirkPhase phase = skirkPhaseMap.getOrDefault(uuid, SkirkPhase.DORMANT);

            // 只有 AWAKENING 阶段锁血
            if (phase == SkirkPhase.AWAKENING) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onLivingTick(LivingEvent.LivingTickEvent event) {
            if (event.getEntity().level().isClientSide()) return;
            LivingEntity entity = event.getEntity();

            UUID targetUUID = entity.getUUID();
            if (possessedTargetMap.containsKey(targetUUID)) {
                if (!entity.isAlive()) { releasePossessByTarget(entity); return; }

                UUID playerUUID = possessedTargetMap.get(targetUUID);
                Player player = entity.level().getPlayerByUUID(playerUUID);
                if (player == null || !player.isAlive()) { releasePossessByTarget(entity); return; }
                if (!isTransformed(player)) { releasePossessByTarget(entity); return; }
                if (!possessedMap.containsKey(playerUUID)) { releasePossessByTarget(entity); return; }

                if (entity instanceof Mob mob) {
                    PlayerMovementState.State input = PlayerMovementState.get(player.getUUID());
                    boolean locked = viewModeMap.getOrDefault(playerUUID, true);

                    ((IEyeHeightSetter) player).setEyeHeight(mob.getEyeHeight());

                    if (locked) {
                        mob.getNavigation().stop();
                        mob.setYRot(player.getYRot());
                        mob.setXRot(player.getXRot());
                        mob.setYHeadRot(player.getYRot());

                        if (input.forward != 0 || input.left != 0) {
                            double speed = 0.3D;
                            Vec3 moveVec;

                            if (mob.isNoGravity()) {
                                float yRot = mob.getYRot();
                                float xRot = mob.getXRot();
                                Vec3 forwardVec = new Vec3(0, 0, input.forward * speed)
                                        .xRot(-xRot * ((float) Math.PI / 180F))
                                        .yRot(-yRot * ((float) Math.PI / 180F));
                                Vec3 leftVec = new Vec3(input.left * speed, 0, 0)
                                        .yRot(-yRot * ((float) Math.PI / 180F));
                                moveVec = forwardVec.add(leftVec);
                                mob.setDeltaMovement(moveVec.x, moveVec.y, moveVec.z);
                            } else {
                                moveVec = new Vec3(input.left * speed, 0, input.forward * speed)
                                        .yRot(-player.getYRot() * ((float) Math.PI / 180F));
                                mob.setDeltaMovement(moveVec.x, mob.getDeltaMovement().y, moveVec.z);
                            }
                            mob.hurtMarked = true;
                        }

                        if (input.jumping) {
                            mob.setDeltaMovement(mob.getDeltaMovement().x, 0.4, mob.getDeltaMovement().z);
                            mob.hurtMarked = true;
                        }
                    }

                    player.setPos(mob.getX(), mob.getY(), mob.getZ());

                    if (player instanceof ServerPlayer serverPlayer) {
                        NetworkHandler.INSTANCE.send(
                                PacketDistributor.PLAYER.with(() -> serverPlayer),
                                new SyncPacket(mob.getX(), mob.getY(), mob.getZ(), 0, 0, mob.getEyeHeight(), true)
                        );
                    }
                }
                return;
            }

            if (!(entity instanceof Player player)) return;
            UUID uuid = player.getUUID();
            if (!isTransformed(player)) return;

            TransformType type = getTransformType(player);

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.getAbilities().mayfly = true;
                serverPlayer.onUpdateAbilities();
            }

            if (type == TransformType.SKIRK) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL,
                            player.getX(), player.getY() + 1, player.getZ(),
                            5, 0.5, 0.5, 0.5, 0.1);

                    tickSkirkPhase(player, serverLevel);

                    if (player instanceof ServerPlayer serverPlayer) {
                        com.qiuym.qiusmob.skill.SkirkSkills.tick(serverPlayer);
                    }
                }
            } else {
                BossAI bossAI = bossAIMap.get(uuid);
                if (bossAI != null && bossAI.hasValidTarget()) {
                    if (player.level() instanceof ServerLevel serverLevel) {
                        Vec3 pos = player.position();
                        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                                pos.x, pos.y + 2, pos.z,
                                2, 0.5, 0.5, 0.5, 0.05);
                    }
                }
                if (bossAI != null) bossAI.tickPassive();
            }

            ServerBossEvent bossEvent = bossEventMap.get(uuid);
            if (bossEvent != null) {
                if (!player.isAlive()) {
                    bossEvent.removeAllPlayers();
                    setTransformed(player, false, type);
                    return;
                }
                bossEvent.setProgress(player.getHealth() / player.getMaxHealth());
            }

            if (type == TransformType.DEATH) {
                float healthRatio = player.getHealth() / player.getMaxHealth();
                if (player.getHealth() < player.getMaxHealth()) {
                    float regenAmount;
                    if (healthRatio > 0.6f) regenAmount = 0.25f;
                    else if (healthRatio > 0.3f) regenAmount = 0.5f;
                    else regenAmount = 0.0f;
                    player.setHealth(Math.min(player.getHealth() + regenAmount, player.getMaxHealth()));
                }
            }

            if (player.isInWater()) {
                player.setAirSupply(player.getMaxAirSupply());
            }
        }
    }
}