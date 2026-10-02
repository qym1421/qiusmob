package com.qiuym.qiusmob.worldgen;

import com.qiuym.qiusmob.QiusMob;
import com.qiuym.qiusmob.worldgen.feature.SkirkAltarFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, QiusMob.MOD_ID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> SKIRK_ALTAR =
            FEATURES.register("skirk_altar",
                    () -> new SkirkAltarFeature(NoneFeatureConfiguration.CODEC));
}