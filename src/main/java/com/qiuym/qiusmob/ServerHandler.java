package com.qiuym.qiusmob;

import com.qiuym.qiusmob.entity.Death;
import com.qiuym.qiusmob.entity.consciousness.AbyssConsciousnessEntity;
import com.qiuym.qiusmob.entity.skirk.SkirkEntity;
import com.qiuym.qiusmob.network.NetworkHandler;
import com.qiuym.qiusmob.network.SkirkBindSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ServerHandler {

    private static final AABB WORLD_BOUNDS = new AABB(
            -30000000, -64, -30000000,
            30000000, 320, 30000000);

    // ===== 模组总线 =====
    @Mod.EventBusSubscriber(modid = QiusMob.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusHandler {
    }

    // ===== 游戏总线 =====
    @Mod.EventBusSubscriber(modid = QiusMob.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBusHandler {
    
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
        
            MinecraftServer server = event.getServer();
            if (server == null) return;
        
            // 每 20 tick 检测一次
            if (server.getTickCount() % 20 != 0) return;
        
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel level = player.serverLevel();
        
                int playerHash = player.getUUID().hashCode();
                int skirkHash = level.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).get();
                boolean bound = (skirkHash != 0 && skirkHash == playerHash);
        
                NetworkHandler.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new SkirkBindSyncPacket(bound)
                );
            }
        }

        // ===== 对象被攻击 → 意识反击 =====
        @SubscribeEvent
        public static void onLivingHurt(LivingHurtEvent event) {
            if (event.getEntity().level().isClientSide()) return;
            if (!(event.getEntity() instanceof Player player)) return;
            if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

            for (AbyssConsciousnessEntity consciousness : player.level().getEntitiesOfClass(
                    AbyssConsciousnessEntity.class, player.getBoundingBox().inflate(64))) {
                if (!consciousness.isBoundTo(player)) continue;
                if (attacker == consciousness) continue;

                consciousness.onTargetHurt(attacker);
                break;
            }
        }

        // ===== 放置凋灵骷髅头 → 召唤死亡君主 =====
        @SubscribeEvent
        public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
        
            BlockPos pos = event.getPos();
            BlockState state = level.getBlockState(pos);
        
            if (state.getBlock() != Blocks.WITHER_SKELETON_SKULL) return;
        
            if (hasDeathMonarch(level)) {
                player.displayClientMessage(
                        Component.translatable("message.qiusmob.death.already_exists"), true);
                return;
            }
        
            if (!isDeathAltar(level, pos)) return;
        
            summonDeath(level, pos, player);
        }
        
        private static boolean isDeathAltar(ServerLevel level, BlockPos skullPos) {
            if (!level.getBlockState(skullPos.below()).is(Blocks.SOUL_SAND)) return false;
            if (!level.getBlockState(skullPos.north()).is(Blocks.SOUL_SAND)) return false;
            if (!level.getBlockState(skullPos.south()).is(Blocks.SOUL_SAND)) return false;
            if (!level.getBlockState(skullPos.east()).is(Blocks.SOUL_SAND)) return false;
            if (!level.getBlockState(skullPos.west()).is(Blocks.SOUL_SAND)) return false;
            return true;
        }

        // ===== 右键末地传送门框架 → 召唤丝柯克 =====
        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            if (event.getItemStack().getItem() != Items.ENDER_EYE) return;

            BlockPos pos = event.getPos();
            if (!(level.getBlockState(pos).getBlock() instanceof EndPortalFrameBlock)) return;

            // 检查是否已有丝柯克
            if (hasSkirk(level)) {
                player.displayClientMessage(
                        Component.translatable("message.qiusmob.skirk.already_exists"), true);
                event.setCanceled(true);
                return;
            }

            // 检查结构
            if (!isSkirkAltar(level, pos)) return;

            // 授勋成就：找到结构
            grantAdvancement(player, "goal_skirk_altar", "found_altar");

            // 夜晚检查
            if (!level.isNight()) {
                player.displayClientMessage(
                        Component.translatable("message.qiusmob.skirk.night_only"), true);
                event.setCanceled(true);
                return;
            }

            summonSkirk(level, pos, player);
            event.setCanceled(true);
        }

        // ===== 结构检测：丝柯克 =====
        private static boolean isSkirkAltar(ServerLevel level, BlockPos center) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos check = center.offset(dx, 0, dz);
                    boolean isEdge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    BlockState state = level.getBlockState(check);

                    if (isEdge) {
                        if (!state.is(Blocks.END_STONE)) return false;
                    }
                }
            }
            return true;
        }

        // ===== 唯一性检查 =====
        private static boolean hasDeathMonarch(ServerLevel level) {
            return !level.getEntitiesOfClass(Death.class, WORLD_BOUNDS).isEmpty();
        }

        private static boolean hasSkirk(ServerLevel level) {
            return !level.getEntitiesOfClass(SkirkEntity.class, WORLD_BOUNDS).isEmpty();
        }

        // ===== 召唤死亡君主 =====
        private static void summonDeath(ServerLevel level, BlockPos pos, ServerPlayer player) {
            level.removeBlock(pos, false);
            level.removeBlock(pos.north(), false);
            level.removeBlock(pos.south(), false);
            level.removeBlock(pos.east(), false);
            level.removeBlock(pos.west(), false);

            Death death = EntityRegister.DEATH.get().create(level);
            if (death == null) return;

            death.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                    player.getYRot(), 0);
            level.addFreshEntity(death);

            // 降雷
            for (int i = 0; i < 10; i++) {
                double x = pos.getX() + level.random.nextInt(7) - 3;
                double z = pos.getZ() + level.random.nextInt(7) - 3;
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(x, pos.getY(), z);
                    level.addFreshEntity(bolt);
                }
            }

            player.displayClientMessage(
                    Component.translatable("message.qiusmob.death.summoned"), true);

            // 授勋成就：开端
            grantAdvancement(player, "goal_summon_death", "summon_death");
        }

        // ===== 召唤丝柯克 =====
        private static void summonSkirk(ServerLevel level, BlockPos pos, ServerPlayer player) {
            // 感染 20 格内方块
            for (int dx = -20; dx <= 20; dx++) {
                for (int dy = -10; dy <= 10; dy++) {
                    for (int dz = -20; dz <= 20; dz++) {
                        BlockPos check = pos.offset(dx, dy, dz);
                        if (level.random.nextFloat() < 0.6f) {
                            BlockState state = level.getBlockState(check);
                            if (state.isSolid()) {
                                if (level.random.nextBoolean()) {
                                    level.setBlock(check, Blocks.END_STONE.defaultBlockState(), 3);
                                } else {
                                    level.setBlock(check, Blocks.SOUL_SAND.defaultBlockState(), 3);
                                }
                            }
                        }
                    }
                }
            }

            SkirkEntity skirk = EntityRegister.SKIRK.get().create(level);
            if (skirk == null) return;

            skirk.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                    player.getYRot(), 0);
            level.addFreshEntity(skirk);

            player.displayClientMessage(
                    Component.translatable("message.qiusmob.skirk.summoned"), true);

            // 授勋成就：终灾
            grantAdvancement(player, "goal_skirk_defeat", "summon_skirk");
        }

        // ===== 授勋成就工具 =====
        private static void grantAdvancement(ServerPlayer player, String id, String criterion) {
            if (player.getServer() == null) return;
            Advancement adv = player.getServer().getAdvancements()
                    .getAdvancement(new ResourceLocation(QiusMob.MOD_ID, id));
            if (adv != null) {
                player.getAdvancements().award(adv, criterion);
            }
        }
    }
}