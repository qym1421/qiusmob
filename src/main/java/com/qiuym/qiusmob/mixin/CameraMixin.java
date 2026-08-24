package com.qiuym.qiusmob.mixin;

import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.UUID;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(
        method = "setup",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setPosition(DDD)V",
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onSetup(BlockGetter level, Entity entity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }

        if (!PlayerConvertToQiusMob.isPossessing(player)) {
            return;
        }

        UUID targetUUID = PlayerConvertToQiusMob.getPossessedTarget(player);
        if (targetUUID == null) {
            return;
        }

        LivingEntity target = null;
        if (level instanceof ClientLevel clientLevel) {
            for (LivingEntity e : clientLevel.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(player.position(), 64, 64, 64))) {
                if (e.getUUID().equals(targetUUID)) {
                    target = e;
                    break;
                }
            }
        }

        if (target == null) {
            return;
        }

        // 从实体眼睛位置开始计算
        Vec3 entityEyePos = target.getEyePosition();

        // 水平尺寸
        AABB boundingBox = target.getBoundingBox();
        double width = boundingBox.getXsize();
        double depth = boundingBox.getZsize();
        double size = Math.max(width, depth);

        // 修正值： (0.375 + size/80) * size + 0.5
        double offsetMagnitude = (0.375 + size / 80.0) * size + 0.5;

        // 实体视线方向
        float yRot = target.getYRot();
        float xRot = target.getXRot();
        Vec3 lookVec = Vec3.directionFromRotation(xRot, yRot);

        // 最终位置 = 实体眼睛位置 + 视线方向修正（水平方向）
        Vec3 finalPos = entityEyePos.add(lookVec.scale(offsetMagnitude));

        setPosition(finalPos.x, finalPos.y, finalPos.z);
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void onGetMaxZoom(double desiredDistance, CallbackInfoReturnable<Double> cir) {
        if (!(this.entity instanceof Player player)) {
            return;
        }

        if (!PlayerConvertToQiusMob.isPossessing(player)) {
            return;
        }

        UUID targetUUID = PlayerConvertToQiusMob.getPossessedTarget(player);
        if (targetUUID == null) {
            return;
        }

        LivingEntity target = null;
        if (player.level() instanceof ClientLevel clientLevel) {
            for (LivingEntity entity : clientLevel.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(player.position(), 64, 64, 64))) {
                if (entity.getUUID().equals(targetUUID)) {
                    target = entity;
                    break;
                }
            }
        }

        if (target == null) {
            return;
        }

        AABB boundingBox = target.getBoundingBox();
        double width = boundingBox.getXsize();
        double depth = boundingBox.getZsize();

        double size = Math.max(width, depth);
        double finalDistance = (4.0 + size / 50.0) * size + 4.0;

        cir.setReturnValue(finalDistance);
    }
}