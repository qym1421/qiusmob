package com.qiuym.qiusmob.mixin;

import com.qiuym.qiusmob.api.IEyeHeightSetter;
import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements IEyeHeightSetter {

    @Unique
    private Float customEyeHeight = null;

    @Override
    public void setEyeHeight(float height) {
        this.customEyeHeight = height;
    }

    @Override
    public void resetEyeHeight() {
        this.customEyeHeight = null;
    }

    @Inject(method = "getEyeHeight", at = @At("HEAD"), cancellable = true)
    private void onGetEyeHeight(CallbackInfoReturnable<Float> cir) {
        if (customEyeHeight != null) {
            cir.setReturnValue(customEyeHeight);
        }
    }

    /**
     * @reason 修改攀爬逻辑，允许变形玩家在水平碰撞时攀爬
     * @author Qiuym
     */
    @Overwrite
    public boolean onClimbable() {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self.isSwimming()) {
            return false;
        }

        BlockState state = self.getBlockStateOn();
        if (state.is(net.minecraft.tags.BlockTags.CLIMBABLE)) {
            return true;
        }

        if (self instanceof Player player && PlayerConvertToQiusMob.isTransformed(player)) {
            if (player.horizontalCollision) {
                return true;
            }
        }

        return false;
    }
}