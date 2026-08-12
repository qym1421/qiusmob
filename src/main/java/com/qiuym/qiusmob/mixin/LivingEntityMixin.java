package com.qiuym.qiusmob.mixin;

import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

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