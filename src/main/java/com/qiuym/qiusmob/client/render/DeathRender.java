package com.qiuym.qiusmob.client.render;

import com.qiuym.qiusmob.QiusMob;
import com.qiuym.qiusmob.entity.Death;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DeathRender extends GeoEntityRenderer<Death> {

    public DeathRender(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(QiusMob.MOD_ID, "death"), "head"));
    }

    @Override
    public ResourceLocation getTextureLocation(Death entity) {
        return ResourceLocation.fromNamespaceAndPath(QiusMob.MOD_ID, "textures/entity/death.png");
    }
}