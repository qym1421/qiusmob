package com.qiuym.qiusmob.client.render.consciousness;

import com.qiuym.qiusmob.QiusMob;
import com.qiuym.qiusmob.entity.consciousness.AbyssConsciousnessEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class AbyssConsciousnessRenderer extends GeoEntityRenderer<AbyssConsciousnessEntity> {

    public AbyssConsciousnessRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                ResourceLocation.fromNamespaceAndPath(QiusMob.MOD_ID, "abyss_consciousness"),
                "head"
        ));
        this.shadowRadius = 0.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssConsciousnessEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(
                QiusMob.MOD_ID, "textures/entity/abyss_consciousness.png");
    }
}