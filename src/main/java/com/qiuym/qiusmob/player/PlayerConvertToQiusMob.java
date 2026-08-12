package com.qiuym.qiusmob.player;

import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.entity.ai.BossAI;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerConvertToQiusMob {
    private static final Map<UUID, Boolean> transformedMap = new HashMap<>();
    private static final Map<UUID, BossAI> bossAIMap = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> bossEventMap = new HashMap<>();

    public static boolean isTransformed(Player player) {
        return transformedMap.getOrDefault(player.getUUID(), false);
    }

    public static void setTransformed(Player player, boolean transformed) {
        if (player.level().isClientSide()) return;
        UUID uuid = player.getUUID();
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

        System.out.println("[QiusMob] 服务端执行变形");

        // 1. 创建 BossBar
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

        // 2. 创建寄生式 BossAI（以玩家为宿主）
        BossAI bossAI = new BossAI(player);
        bossAIMap.put(uuid, bossAI);

        // 3. 初始化玩家属性（1000血）
        player.getAttributes().getInstance(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        player.getAttributes().getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(0.5D);
        player.setHealth(1000.0f);

        // 4. 检查 gamerule qiusmobCanAttackDeath
        if (level instanceof ServerLevel serverLevel) {
            GameRules.BooleanValue rule = serverLevel.getGameRules()
                    .getRule(ModEvents.CAN_ATTACK_DEATH_KEY);
            if (!rule.get()) {
                player.setInvulnerable(true);
                System.out.println("[QiusMob] 规则 qiusmobCanAttackDeath=false，玩家无敌");
            } else {
                player.setInvulnerable(false);
            }
        }

        // 5. BossBar 进度初始化
        bossEvent.setProgress(player.getHealth() / player.getMaxHealth());

        // 6. 特效
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    player.getX(), player.getY() + 1, player.getZ(),
                    50, 1, 1, 1, 0.1);
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        // 7. 夜视效果
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

        // 8. 客户端消息
        player.displayClientMessage(
                Component.literal("§a§l【QiusMod】 §r变形成功！"),
                true
        );

        System.out.println("[QiusMob] 变形成功！BossAI 寄生在玩家身上");
    }

    private static void removeTransformation(Player player) {
        UUID uuid = player.getUUID();

        bossAIMap.remove(uuid);

        ServerBossEvent bossEvent = bossEventMap.remove(uuid);
        if (bossEvent != null) {
            bossEvent.removeAllPlayers();
        }

        // 恢复玩家状态
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setNoGravity(false);

        // 恢复玩家属性
        player.getAttributes().getInstance(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        player.getAttributes().getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(0.1D);
        player.setHealth(20.0f);

        // 移除夜视效果
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

        System.out.println("[QiusMob] 解除变形，玩家已恢复");
    }

    // ========== 技能触发 ==========
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

        // ===== 统一检查 target（除 AOE 和 Leap 外强制要求） =====
        // AOE 和 Leap 不需要 target
        if (!methodName.equals("executeAOE") && !methodName.equals("executeLeap")) {
            if (bossAI.getTarget() == null) {
                player.displayClientMessage(
                        Component.literal("§c§l【QiusMod】 §r没有找到目标！"),
                        true
                );
                return;
            }
        }

        // ===== 设置目标（AOE 和 Leap 不需要，但为了兼容保留） =====
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
                System.out.println("[QiusMob] 技能目标: " + nearestTarget.getName().getString());
            } else {
                // AOE 和 Leap 允许无目标
                if (!methodName.equals("executeAOE") && !methodName.equals("executeLeap")) {
                    player.displayClientMessage(
                            Component.literal("§c§l【QiusMod】 §r没有找到目标！"),
                            true
                    );
                    return;
                }
                // AOE 和 Leap 无目标时继续执行
                System.out.println("[QiusMob] 无目标，执行" + methodName + "（无需目标）");
            }
        } catch (Exception e) {
            // 目标设置失败，但 AOE 和 Leap 允许无目标
            if (!methodName.equals("executeAOE") && !methodName.equals("executeLeap")) {
                player.displayClientMessage(
                        Component.literal("§c§l【QiusMod】 §r目标设置失败！"),
                        true
                );
                return;
            }
        }

        // 调用技能
        try {
            Method method = BossAI.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(bossAI);
            System.out.println("[QiusMob] 调用 BossAI." + methodName + "() 成功");
        } catch (Exception e) {
            System.out.println("[QiusMob] 调用 BossAI." + methodName + "() 失败: " + e.getMessage());
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

    // ========== 事件注册 ==========
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new Handler());
    }

    public static class Handler {
        @SubscribeEvent
        public void onLivingTick(LivingEvent.LivingTickEvent event) {
            if (event.getEntity().level().isClientSide()) return;
            if (!(event.getEntity() instanceof Player player)) return;

            UUID uuid = player.getUUID();

            if (!isTransformed(player)) return;

            // ===== 神性光环（粒子特效） =====
            BossAI bossAI = bossAIMap.get(uuid);
            if (bossAI != null && bossAI.hasValidTarget()) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    Vec3 pos = player.position();
                    serverLevel.sendParticles(ParticleTypes.ENCHANT,
                            pos.x, pos.y + 2, pos.z,
                            2, 0.5, 0.5, 0.5, 0.05);
                }
            }

            // ===== BossBar 更新 =====
            ServerBossEvent bossEvent = bossEventMap.get(uuid);
            if (bossEvent != null) {
                if (!player.isAlive()) {
                    bossEvent.removeAllPlayers();
                    setTransformed(player, false);
                    return;
                }
                bossEvent.setProgress(player.getHealth() / player.getMaxHealth());
            }

            // ===== 回血机制 =====
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

            // ===== 水下呼吸 =====
            if (player.isInWater()) {
                player.setAirSupply(player.getMaxAirSupply());
            }

            // ===== BossAI 被动 =====
            if (bossAI != null) {
                bossAI.tickPassive();
            }
        }
    }
}