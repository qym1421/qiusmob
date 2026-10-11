package com.qiuym.qiusmob.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qiuym.qiusmob.QiusMob;
import com.qiuym.qiusmob.client.ClientState;
import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public class AbyssCorruptionLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation CORRUPTION_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    QiusMob.MOD_ID, "textures/entity/abyss_corruption.png");

    public AbyssCorruptionLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {

        // 已变形 → 不显示（自定义模型会覆盖）
        if (PlayerConvertToQiusMob.isTransformed(player)) return;

        // 未绑定 Skirk → 不显示
        if (!ClientState.isSkirkBound()) return;

        VertexConsumer consumer = buffer.getBuffer(
                RenderType.entityTranslucent(CORRUPTION_TEXTURE));

        this.getParentModel().renderToBuffer(
                poseStack, consumer, packedLight,
                LivingEntityRenderer.getOverlayCoords(player, 0.0F),
                1.0F, 1.0F, 1.0F, 1.0F
        );
    }
}