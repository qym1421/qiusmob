package com.qiuym.qiusmob.mixin;

import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import it.yuruni.dog.entity.boss.DevourerHeadEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
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

    @Shadow(remap = false)
    private List<UUID> segmentUUIDs;

    @Shadow(remap = false)
    private int targetDwell;

    @Unique
    private static Field possessedTargetMapField;

    @Unique
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

    @Inject(
            method = "updateThreatTarget",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onUpdateThreatTarget(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Mob mob = (Mob) self;

        // 被夺舍时取消自动索敌
        if (isPossessed()) {
            ci.cancel();
            return;
        }

        double range = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        AABB globalBox = self.getBoundingBox().inflate(range);
        List<LivingEntity> candidates;

        if (isDevourerOwnerModeActive()) {
            // ===== 拥有者绑定模式：仅在拥有者周围 25x10x25 区域内索敌 =====
            Player owner = null;
            if (self.level() instanceof ServerLevel serverLevel) {
                int ownerHash = getDevourerOwnerHash();
                for (Player player : serverLevel.players()) {
                    if (player.getUUID().hashCode() == ownerHash) {
                        owner = player;
                        break;
                    }
                }
            }

            if (owner == null || !owner.isAlive() || owner.isSpectator()) {
                mob.setTarget(null);
                ci.cancel();
                return;
            }

            AABB ownerBox = new AABB(
                    owner.getX() - 12.5,
                    owner.getY() - 5.0,
                    owner.getZ() - 12.5,
                    owner.getX() + 12.5,
                    owner.getY() + 5.0,
                    owner.getZ() + 12.5
            );

            candidates = self.level().getEntitiesOfClass(
                    LivingEntity.class,
                    ownerBox,
                    entity -> {
                        if (entity == self) return false;
                        if (!entity.isAlive()) return false;
                        if (entity.isSpectator()) return false;
                        if (this.segmentUUIDs != null && this.segmentUUIDs.contains(entity.getUUID())) {
                            return false;
                        }
                        if (entity.getClass().getName().contains("DevourerSegmentEntity")) {
                            return false;
                        }
                        if (self.isAlliedTo(entity)) {
                            return false;
                        }
                        if (entity instanceof Player player) {
                            return isDevourerOwner(player) && !player.isCreative();
                        }
                        return true;
                    }
            );

            if (candidates.isEmpty()) {
                mob.setTarget(null);
                ci.cancel();
                return;
            }

            candidates.sort((a, b) -> {
                boolean aIsPlayer = a instanceof Player;
                boolean bIsPlayer = b instanceof Player;
                if (aIsPlayer && !bIsPlayer) return -1;
                if (!aIsPlayer && bIsPlayer) return 1;
                return Double.compare(self.distanceToSqr(a), self.distanceToSqr(b));
            });

            mob.setTarget(candidates.get(0));
            this.targetDwell = 0;
            ci.cancel();
            return;
        }

        // ===== 非绑定模式：全局索敌 =====
        candidates = self.level().getEntitiesOfClass(
                LivingEntity.class,
                globalBox,
                entity -> {
                    if (entity == self) return false;
                    if (!entity.isAlive()) return false;
                    if (entity.isSpectator()) return false;
                    if (this.segmentUUIDs != null && this.segmentUUIDs.contains(entity.getUUID())) {
                        return false;
                    }
                    if (entity.getClass().getName().contains("DevourerSegmentEntity")) {
                        return false;
                    }
                    if (self.isAlliedTo(entity)) {
                        return false;
                    }
                    if (entity instanceof Player player) {
                        return !player.isCreative();
                    }
                    return true;
                }
        );

        if (candidates.isEmpty()) {
            mob.setTarget(null);
            ci.cancel();
            return;
        }

        candidates.sort((a, b) -> {
            boolean aIsPlayer = a instanceof Player;
            boolean bIsPlayer = b instanceof Player;
            if (aIsPlayer && !bIsPlayer) return -1;
            if (!aIsPlayer && bIsPlayer) return 1;
            return Double.compare(self.distanceToSqr(a), self.distanceToSqr(b));
        });

        mob.setTarget(candidates.get(0));
        this.targetDwell = 0;
        ci.cancel();
    }

    @Inject(
            method = "isValidPrey",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onIsValidPrey(Player player, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;

        if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
            cir.setReturnValue(false);
            return;
        }

        if (isDevourerOwnerModeActive()) {
            cir.setReturnValue(isDevourerOwner(player) && !self.isAlliedTo(player));
            return;
        }

        if (self.isAlliedTo(player)) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(true);
    }
}