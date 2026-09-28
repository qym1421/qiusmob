package com.qiuym.qiusmob.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qiuym.qiusmob.QiusMob;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;

public class SkirkPlayerRenderer extends PlayerRenderer {

    private static final ResourceLocation SKIRK_TEXTURE = new ResourceLocation(
            QiusMob.MOD_ID, "textures/entity/skirk.png");

    public SkirkPlayerRenderer(EntityRendererProvider.Context context, boolean slim) {
        super(context, slim);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
        return SKIRK_TEXTURE;
    }
}