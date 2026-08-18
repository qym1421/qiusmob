package com.qiuym.qiusmob.mixin;

import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private Entity entity;

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

        // 直接取水平最大尺寸，不除以 2
        double size = Math.max(width, depth);

        // 新公式：(4 + size/50) * size + 4
        double finalDistance = (4.0 + size / 50.0) * size + 4.0;

        // 无上限
        cir.setReturnValue(finalDistance);
    }
}