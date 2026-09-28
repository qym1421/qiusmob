package com.qiuym.qiusmob.mixin;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RangedAttribute.class)
public abstract class MaxHealthMixin {

    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true)
    private void onSanitizeValue(double value, CallbackInfoReturnable<Double> cir) {
        RangedAttribute self = (RangedAttribute) (Object) this;
        if (self.getDescriptionId().equals("attribute.name.generic.max_health")) {
            cir.setReturnValue(Math.max(1.0, Math.min(5000.0, value)));
        }
    }
}