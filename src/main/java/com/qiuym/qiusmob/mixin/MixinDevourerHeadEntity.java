package com.qiuym.qiusmob.mixin;

import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import it.yuruni.dog.entity.boss.DevourerHeadEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mixin(DevourerHeadEntity.class)
public abstract class MixinDevourerHeadEntity {

    // ================== Shadow ==================

    @Shadow(remap = false)
    private List<UUID> segmentUUIDs;

    @Shadow(remap = false)
    private int targetDwell;

    // ================== 反射：PlayerConvertToQiusMob.possessedTargetMap ==================

    @Unique
    private static Field possessedTargetMapField;

    @Unique
    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> getPossessedTargetMap() {
        if (possessedTargetMapField == null) {
            try {
                possessedTargetMapField = PlayerConvertToQiusMob.class.getDeclaredField("possessedTargetMap");
                possessedTargetMapField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
                return null;
            }
        }
        try {
            return (Map<UUID, UUID>) possessedTargetMapField.get(null);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Unique
    private boolean isPossessed() {
        Entity self = (Entity) (Object) this;
        Map<UUID, UUID> map = getPossessedTargetMap();
        if (map == null) return false;
        return map.containsValue(self.getUUID());
    }

    // ================== 拥有者绑定模式 ==================

    @Unique
    private int getDevourerOwnerHash() {
        Entity self = (Entity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }
        return serverLevel.getGameRules()
                .getRule(ModEvents.DEVOURER_OWNER_KEY)
                .get();
    }

    @Unique
    private boolean isDevourerOwner(Player player) {
        return player.getUUID().hashCode() == getDevourerOwnerHash();
    }

    @Unique
    private boolean isDevourerOwnerModeActive() {
        return getDevourerOwnerHash() != 0;
    }

    @Unique
    private Player findOwner() {
        Entity self = (Entity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) return null;
        int ownerHash = getDevourerOwnerHash();
        for (Player player : serverLevel.players()) {
            if (player.getUUID().hashCode() == ownerHash) {
                return player;
            }
        }
        return null;
    }

    // ================== 绕圈状态 ==================

    @Unique
    private UUID orbitPlayerId = null;

    @Unique
    private int orbitTicks = 0;

    @Unique
    private boolean orbitDirection = true;

    @Unique
    private final java.util.Random orbitRandom = new java.util.Random();

    @Unique
    private void startOrbit(Player player) {
        if (player == null) {
            orbitPlayerId = null;
            return;
        }
        orbitPlayerId = player.getUUID();
        orbitTicks = 0;
        orbitDirection = orbitRandom.nextBoolean();
    }

    @Unique
    private void stopOrbit() {
        orbitPlayerId = null;
        orbitTicks = 0;
        Entity self = (Entity) (Object) this;
        self.setNoGravity(false);
    }

    @Unique
    private void tickOrbit() {
        if (orbitPlayerId == null) return;
        Entity self = (Entity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) {
            stopOrbit();
            return;
        }

        Player player = serverLevel.getPlayerByUUID(orbitPlayerId);
        if (player == null || !player.isAlive() || player.isSpectator()) {
            stopOrbit();
            return;
        }

        orbitTicks++;
        double angle = orbitTicks * 0.06 * (orbitDirection ? 1 : -1);
        double radius = 10.0;

        double targetX = player.getX() + Math.cos(angle) * radius;
        double targetZ = player.getZ() + Math.sin(angle) * radius;
        double yOffset = 5.0 + (Math.sin(orbitTicks * 0.08) * 2.5 + 2.5);
        double targetY = player.getY() + yOffset;

        Vec3 toTarget = new Vec3(targetX - self.getX(), targetY - self.getY(), targetZ - self.getZ());
        if (toTarget.lengthSqr() < 1.0E-4) return;

        Vec3 motion = toTarget.normalize().scale(0.35);
        self.setDeltaMovement(motion);

        double dx = targetX - self.getX();
        double dz = targetZ - self.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        self.setYRot(yaw);
        self.yRotO = yaw;
        self.setXRot(0);
        self.xRotO = 0;

        self.setNoGravity(true);
    }

    // ================== 主逻辑：替换 updateThreatTarget ==================

    @Inject(
            method = "updateThreatTarget",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onUpdateThreatTarget(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Mob mob = (Mob) self;

        if (isPossessed()) {
            stopOrbit();
            mob.setTarget(null);
            ci.cancel();
            return;
        }

        // ===== 拥有者绑定模式 =====
        if (isDevourerOwnerModeActive()) {
            Player owner = findOwner();
            if (owner == null || !owner.isAlive() || owner.isSpectator()) {
                mob.setTarget(null);
                startOrbit(null);
                ci.cancel();
                return;
            }

            AABB ownerBox = new AABB(
                    owner.getX() - 12.5, owner.getY() - 5.0, owner.getZ() - 12.5,
                    owner.getX() + 12.5, owner.getY() + 5.0, owner.getZ() + 12.5
            );

            List<LivingEntity> candidates = self.level().getEntitiesOfClass(
                    LivingEntity.class,
                    ownerBox,
                    entity -> isValidTarget(self, entity)
            );

            if (candidates.isEmpty()) {
                mob.setTarget(null);
                startOrbit(owner);
                ci.cancel();
                return;
            }

            candidates.sort(targetPriorityComparator(self));
            mob.setTarget(candidates.get(0));
            this.targetDwell = 0;
            stopOrbit();
            ci.cancel();
            return;
        }

        // ===== 全局模式 =====

        // 1. 优先：最近的敌对玩家（不同队伍）
        Player enemyPlayer = pickNearestEnemyPlayer(self);
        if (enemyPlayer != null) {
            mob.setTarget(enemyPlayer);
            this.targetDwell = 0;
            stopOrbit();
            ci.cancel();
            return;
        }

        // 2. 其次：40×60×40 内最近的 LivingEntity（非玩家）
        LivingEntity nearest = pickNearestLivingEntity(self, 20, 30, 20);
        if (nearest != null) {
            mob.setTarget(nearest);
            this.targetDwell = 0;
            stopOrbit();
            ci.cancel();
            return;
        }

        // 3. 都没有：绕圈
        Player orbitTarget = findOrbitTarget(self);
        if (orbitTarget != null) {
            startOrbit(orbitTarget);
        } else {
            stopOrbit();
        }
        mob.setTarget(null);
        ci.cancel();
    }

    // ================== 每 tick 驱动绕圈 ==================

    @Inject(method = "m_8119_", at = @At("TAIL"), remap = false)
    private void onTick(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Mob mob = (Mob) self;

        if (mob.getTarget() != null && mob.getTarget().isAlive()) {
            stopOrbit();
            return;
        }

        if (isPossessed()) {
            stopOrbit();
            return;
        }

        tickOrbit();
    }

    // ================== 目标校验 ==================

    @Unique
    private boolean isValidTarget(Entity self, LivingEntity entity) {
        if (entity == self) return false;
        if (!entity.isAlive()) return false;
        if (entity.isSpectator()) return false;

        if (this.segmentUUIDs != null && this.segmentUUIDs.contains(entity.getUUID())) {
            return false;
        }
        if (entity.getClass().getName().contains("DevourerSegmentEntity")) {
            return false;
        }

        if (isFriendly(self, entity)) {
            return false;
        }

        if (entity instanceof Player player) {
            return !player.isSpectator() && !isFriendly(self, player);
        }

        return true;
    }

    @Unique
    private boolean isFriendly(Entity self, Entity other) {
        if (self.getTeam() != null && self.getTeam().equals(other.getTeam())) {
            return true;
        }

        if (isDevourerOwnerModeActive() && other instanceof Player p) {
            if (isDevourerOwner(p)) return true;
        }

        Map<UUID, UUID> map = getPossessedTargetMap();
        if (map != null) {
            if (map.containsValue(other.getUUID())) return true;
        }

        return false;
    }

    // ================== 候选筛选 ==================

    @Unique
    private Player pickNearestEnemyPlayer(Entity self) {
        double range = ((Mob) self).getAttributeValue(Attributes.FOLLOW_RANGE);
        List<Player> players = self.level().getEntitiesOfClass(
                Player.class,
                self.getBoundingBox().inflate(range),
                p -> p.isAlive()
                        && !p.isSpectator()
                        && !isFriendly(self, p)
        );
        if (players.isEmpty()) return null;
        players.sort(Comparator.comparingDouble(self::distanceToSqr));
        return players.get(0);
    }

    @Unique
    private LivingEntity pickNearestLivingEntity(Entity self, double dx, double dy, double dz) {
        AABB box = self.getBoundingBox().inflate(dx, dy, dz);
        List<LivingEntity> list = self.level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> isValidTarget(self, e) && !(e instanceof Player)
        );
        if (list.isEmpty()) return null;
        list.sort(Comparator.comparingDouble(self::distanceToSqr));
        return list.get(0);
    }

    @Unique
    private Player findOrbitTarget(Entity self) {
        if (!(self.level() instanceof ServerLevel serverLevel)) return null;

        if (orbitPlayerId != null) {
            Player bound = serverLevel.getPlayerByUUID(orbitPlayerId);
            if (bound != null && bound.isAlive() && !bound.isSpectator()) {
                return bound;
            }
        }

        Player nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : serverLevel.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            double d = self.distanceToSqr(p);
            if (d < bestDist) {
                bestDist = d;
                nearest = p;
            }
        }
        return nearest;
    }

    // ================== 排序 ==================

    @Unique
    private Comparator<LivingEntity> targetPriorityComparator(Entity self) {
        return (a, b) -> {
            boolean aIsPlayer = a instanceof Player;
            boolean bIsPlayer = b instanceof Player;
            if (aIsPlayer && !bIsPlayer) return -1;
            if (!aIsPlayer && bIsPlayer) return 1;
            return Double.compare(self.distanceToSqr(a), self.distanceToSqr(b));
        };
    }

    // ================== 兼容 isValidPrey ==================

    @Inject(
            method = "isValidPrey",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onIsValidPrey(Player player, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;

        if (!player.isAlive() || player.isSpectator()) {
            cir.setReturnValue(false);
            return;
        }

        if (isDevourerOwnerModeActive()) {
            cir.setReturnValue(isDevourerOwner(player) && !isFriendly(self, player));
            return;
        }

        cir.setReturnValue(!isFriendly(self, player));
    }
}