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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerConvertToQiusMob {
    private static final Map<UUID, Boolean> transformedMap = new HashMap<>();
    private static final Map<UUID, BossAI> bossAIMap = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> bossEventMap = new HashMap<>();
    private static final Map<UUID, UUID> possessedMap = new HashMap<>();
    private static final Map<UUID, UUID> possessedTargetMap = new HashMap<>();
    private static final Map<UUID, Boolean> originalAwareMap = new HashMap<>();
    private static final Map<UUID, GameType> originalGameModeMap = new HashMap<>();
    private static final Map<UUID, Boolean> viewModeMap = new HashMap<>();

    public static boolean isTransformed(Player player) {
        return transformedMap.getOrDefault(player.getUUID(), false);
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

        if (isPossessing(player)) {
            UUID targetUUID = possessedMap.get(player.getUUID());
            if (targetUUID == null) return;

            LivingEntity target = null;
            if (player.level() instanceof ServerLevel serverLevel) {
                for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(player.position(), 64, 64, 64))) {
                    if (entity.getUUID().equals(targetUUID)) {
                        target = entity;
                        break;
                    }
                }
            }
            if (target instanceof Mob mob) {
                // 只切换 AI，但不禁用（自由模式恢复AI）
            }
        }
    }

    public static void setTransformed(Player player, boolean transformed) {
        if (player.level().isClientSide()) return;
        UUID uuid = player.getUUID();
        boolean currentState = transformedMap.getOrDefault(uuid, false);

        if (currentState == transformed) {
            return;
        }

        if (transformed) {
            transformedMap.put(uuid, true);
            applyTransformation(player);
        } else {
            transformedMap.put(uuid, false);
            removeTransformation(player);
        }
    }

    public static void toggleTransformation(Player player) {
        setTransformed(player, !isTransformed(player));
    }

    private static boolean isAutoMode(Player player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            GameRules.BooleanValue rule = serverLevel.getGameRules()
                    .getRule(ModEvents.DEATH_CAN_SUBJECTIVE_ATTACK_KEY);
            return rule.get();
        }
        return true;
    }

    private static void applyTransformation(Player player) {
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
                    .getRule(ModEvents.CAN_ATTACK_DEATH_KEY);
            if (!rule.get()) {
                player.setInvulnerable(true);
            } else {
                player.setInvulnerable(false);
            }
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
            gameRules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(true, server);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getAbilities().mayfly = true;
            serverPlayer.getAbilities().flying = true;
            serverPlayer.onUpdateAbilities();
        }

        player.displayClientMessage(
                Component.literal("§a§l【QiusMod】 §r变形成功！"),
                true
        );

        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new TransformSyncPacket(true));
        }
    }

    private static void removeTransformation(Player player) {
        UUID uuid = player.getUUID();

        if (isPossessing(player)) {
            releasePossess(player);
        }

        bossAIMap.remove(uuid);

        ServerBossEvent bossEvent = bossEventMap.remove(uuid);
        if (bossEvent != null) {
            bossEvent.removeAllPlayers();
        }

        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setNoGravity(false);

        player.getAttributes().getInstance(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        player.getAttributes().getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(0.1D);
        player.setHealth(20.0f);

        if (player instanceof ServerPlayer serverPlayer) {
            var server = serverPlayer.getServer();
            var level = serverPlayer.serverLevel();
            var gameRules = level.getGameRules();

            gameRules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, server);
            server.getCommands().performPrefixedCommand(
                    serverPlayer.createCommandSourceStack(),
                    "effect clear @s minecraft:night_vision"
            );
            gameRules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(true, server);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE ||
                serverPlayer.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR) {
                serverPlayer.getAbilities().mayfly = true;
                serverPlayer.getAbilities().flying = true;
            } else {
                serverPlayer.getAbilities().mayfly = false;
                serverPlayer.getAbilities().flying = false;
            }
            serverPlayer.onUpdateAbilities();
        }

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

    // ========== 夺舍 ==========

    public static void executePossess(ServerPlayer player, UUID targetUUID) {
        if (!isTransformed(player)) {
            player.displayClientMessage(
                    Component.literal("§c§l【QiusMod】 §r请先变形！"),
                    true
            );
            return;
        }

        if (isPossessing(player)) {
            releasePossess(player);
            return;
        }

        if (targetUUID == null) {
            player.displayClientMessage(
                    Component.literal("§c§l【QiusMod】 §r没有找到目标！"),
                    true
            );
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
            player.displayClientMessage(
                    Component.literal("§c§l【QiusMod】 §r目标已不存在！"),
                    true
            );
            return;
        }

        if (!target.isAlive()) {
            player.displayClientMessage(
                    Component.literal("§c§l【QiusMod】 §r目标已死亡！"),
                    true
            );
            return;
        }

        if (target instanceof Player) {
            player.displayClientMessage(
                    Component.literal("§c§l【QiusMod】 §r不能夺舍玩家！"),
                    true
            );
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

        // 服务端设置眼睛高度（通过 Mixin 覆写）
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
        if (original != null) {
            serverPlayer.setGameMode(original);
        } else {
            serverPlayer.setGameMode(GameType.SURVIVAL);
        }

        // 重置眼睛高度（客户端的 SyncPacket 也会同步处理）
        ((IEyeHeightSetter) player).resetEyeHeight();

        PlayerMovementState.remove(playerUUID);
        viewModeMap.remove(playerUUID);

        // 发送重置包，告知客户端解除夺舍
        if (player instanceof ServerPlayer serverPlayer2) {
            NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer2),
                    new SyncPacket(player.getX(), player.getY(), player.getZ(), 0, 0, 0, false)
            );
        }

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
            if (original != null) {
                serverPlayer.setGameMode(original);
            } else {
                serverPlayer.setGameMode(GameType.SURVIVAL);
            }

            // 重置眼睛高度（客户端的 SyncPacket 也会同步处理）
            ((IEyeHeightSetter) player).resetEyeHeight();

            PlayerMovementState.remove(playerUUID);
            viewModeMap.remove(playerUUID);

            // 发送重置包，告知客户端解除夺舍
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

    private static void invokeBossAI(Player player, String methodName) {
        if (!isTransformed(player)) {
            player.displayClientMessage(
                    Component.literal("§c§l【QiusMod】 §r请先变形！"),
                    true
            );
            return;
        }

        BossAI bossAI = bossAIMap.get(player.getUUID());
        if (bossAI == null) {
            player.displayClientMessage(
                    Component.literal("§c§l【QiusMod】 §rBossAI 未初始化！"),
                    true
            );
            return;
        }

        if (!methodName.equals("executeAOE") && !methodName.equals("executeLeap")) {
            if (bossAI.getTarget() == null) {
                player.displayClientMessage(
                        Component.literal("§c§l【QiusMod】 §r没有找到目标！"),
                        true
                );
                return;
            }
        }

        try {
            AABB aabb = AABB.ofSize(player.position(), 48, 20, 48);
            LivingEntity nearestTarget = player.level().getEntitiesOfClass(
                    LivingEntity.class, aabb
            ).stream()
                    .filter(e -> e != player && e.isAlive())
                    .min((a, b) -> Double.compare(
                            player.distanceToSqr(a),
                            player.distanceToSqr(b)
                    ))
                    .orElse(null);

            if (nearestTarget != null) {
                Field targetField = BossAI.class.getDeclaredField("target");
                targetField.setAccessible(true);
                targetField.set(bossAI, nearestTarget);
            } else {
                if (!methodName.equals("executeAOE") && !methodName.equals("executeLeap")) {
                    player.displayClientMessage(
                            Component.literal("§c§l【QiusMod】 §r没有找到目标！"),
                            true
                    );
                    return;
                }
            }
        } catch (Exception e) {
            if (!methodName.equals("executeAOE") && !methodName.equals("executeLeap")) {
                player.displayClientMessage(
                        Component.literal("§c§l【QiusMod】 §r目标设置失败！"),
                        true
                );
                return;
            }
        }

        try {
            Method method = BossAI.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(bossAI);
        } catch (Exception e) {
            e.printStackTrace();
            player.displayClientMessage(
                    Component.literal("§c§l【QiusMod】 §r技能释放失败！"),
                    true
            );
        }
    }

    public static void executeMelee(Player player) {
        invokeBossAI(player, "executeMelee");
    }

    public static void executeLeap(Player player) {
        invokeBossAI(player, "executeLeap");
    }

    public static void executeAOE(Player player) {
        invokeBossAI(player, "executeAOE");
    }

    public static void executeUltimate(Player player) {
        invokeBossAI(player, "executeJudgment");
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new Handler());
    }

    public static class Handler {
        @SubscribeEvent
        public void onLivingTick(LivingEvent.LivingTickEvent event) {
            if (event.getEntity().level().isClientSide()) return;
            LivingEntity entity = event.getEntity();

            // ===== 被夺舍的实体 =====
            UUID targetUUID = entity.getUUID();
            if (possessedTargetMap.containsKey(targetUUID)) {
                if (!entity.isAlive()) {
                    releasePossessByTarget(entity);
                    return;
                }

                UUID playerUUID = possessedTargetMap.get(targetUUID);
                Player player = entity.level().getPlayerByUUID(playerUUID);
                if (player == null || !player.isAlive()) {
                    releasePossessByTarget(entity);
                    return;
                }

                if (!isTransformed(player)) {
                    releasePossessByTarget(entity);
                    return;
                }

                if (!possessedMap.containsKey(playerUUID)) {
                    releasePossessByTarget(entity);
                    return;
                }

                if (entity instanceof Mob mob) {
                    PlayerMovementState.State input = PlayerMovementState.get(player.getUUID());
                    boolean locked = viewModeMap.getOrDefault(playerUUID, true);

                    // 服务端持续设置眼睛高度（确保 SyncPacket 正确同步）
                    ((IEyeHeightSetter) player).setEyeHeight(mob.getEyeHeight());

                    if (locked) {
                        // 锁定模式：清空导航，不改变 AI
                        mob.getNavigation().stop();

                        // 实体朝向跟随玩家视角
                        mob.setYRot(player.getYRot());
                        mob.setXRot(player.getXRot());
                        mob.setYHeadRot(player.getYRot());

                        // 移动控制
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
                    // 自由模式：不干预，AI 自行处理

                    // 玩家坐标始终跟随实体
                    player.setPos(mob.getX(), mob.getY(), mob.getZ());

                    // 发送同步包到客户端（携带夺舍状态 true）
                    if (player instanceof ServerPlayer serverPlayer) {
                        NetworkHandler.INSTANCE.send(
                                PacketDistributor.PLAYER.with(() -> serverPlayer),
                                new SyncPacket(mob.getX(), mob.getY(), mob.getZ(), 0, 0, mob.getEyeHeight(), true)
                        );
                    }
                }
                return;
            }

            // ===== 玩家变形逻辑 =====
            if (!(entity instanceof Player player)) return;

            UUID uuid = player.getUUID();

            if (!isTransformed(player)) return;

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.getAbilities().mayfly = true;
                serverPlayer.onUpdateAbilities();
            }

            BossAI bossAI = bossAIMap.get(uuid);
            if (bossAI != null && bossAI.hasValidTarget()) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    Vec3 pos = player.position();
                    serverLevel.sendParticles(ParticleTypes.ENCHANT,
                            pos.x, pos.y + 2, pos.z,
                            2, 0.5, 0.5, 0.5, 0.05);
                }
            }

            ServerBossEvent bossEvent = bossEventMap.get(uuid);
            if (bossEvent != null) {
                if (!player.isAlive()) {
                    bossEvent.removeAllPlayers();
                    setTransformed(player, false);
                    return;
                }
                bossEvent.setProgress(player.getHealth() / player.getMaxHealth());
            }

            float healthRatio = player.getHealth() / player.getMaxHealth();
            if (player.getHealth() < player.getMaxHealth()) {
                float regenAmount;
                if (healthRatio > 0.6f) {
                    regenAmount = 0.25f;
                } else if (healthRatio > 0.3f) {
                    regenAmount = 0.5f;
                } else {
                    regenAmount = 0.0f;
                }
                player.setHealth(Math.min(player.getHealth() + regenAmount, player.getMaxHealth()));
            }

            if (player.isInWater()) {
                player.setAirSupply(player.getMaxAirSupply());
            }

            if (bossAI != null) {
                bossAI.tickPassive();
            }
        }
    }
}