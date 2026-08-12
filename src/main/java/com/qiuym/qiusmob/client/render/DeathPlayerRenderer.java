package com.qiuym.qiusmob.client.render;

import com.qiuym.qiusmob.QiusMob;
import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;

public class DeathPlayerRenderer extends PlayerRenderer {

    private static final ResourceLocation DEATH_TEXTURE = new ResourceLocation(QiusMob.MOD_ID, "textures/entity/death.png");

    public DeathPlayerRenderer(EntityRendererProvider.Context context) {
        // true = Alex 模型（细手臂）
        super(context, true);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
        if (PlayerConvertToQiusMob.isTransformed(player)) {
            return DEATH_TEXTURE;
        }
        return super.getTextureLocation(player);
    }
}