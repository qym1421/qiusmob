package com.qiuym.qiusmob.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qiuym.qiusmob.QiusMob;
import com.qiuym.qiusmob.entity.skirk.SkirkEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class SkirkRenderer extends HumanoidMobRenderer<SkirkEntity, HumanoidModel<SkirkEntity>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            QiusMob.MOD_ID, "textures/entity/skirk.png");

    public SkirkRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5f);

        // 添加护甲层（可选）
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(SkirkEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(SkirkEntity entity, PoseStack poseStack, float partialTickTime) {
        // 模型缩放（实体碰撞箱 1.8×2.0，标准玩家模型 0.9375 倍即可）
        poseStack.scale(1.0f, 1.0f, 1.0f);
    }
}