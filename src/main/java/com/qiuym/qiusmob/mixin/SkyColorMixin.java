package com.qiuym.qiusmob.mixin;

import com.qiuym.qiusmob.client.SightModeClient;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class SkyColorMixin {

    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void onSetupColor(Camera camera, float partialTick, ClientLevel level,
                                     int renderDistance, float darkenWorldAmount,
                                     CallbackInfo ci) {
        if (SightModeClient.isSkyModeActive()) {
            fogRed = 0.15f;
            fogGreen = 0.0f;
            fogBlue = 0.30f;
        }
    }
}