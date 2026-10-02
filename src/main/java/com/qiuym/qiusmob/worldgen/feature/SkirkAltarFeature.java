package com.qiuym.qiusmob.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class SkirkAltarFeature extends Feature<NoneFeatureConfiguration> {

    public SkirkAltarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();

        // 检查地面是否坚实
        if (!level.getBlockState(origin.below()).isSolid()) {
            return false;
        }

        // 生成 5×5 末地石环
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos pos = origin.offset(dx, 0, dz);
                boolean isEdge = Math.abs(dx) == 2 || Math.abs(dz) == 2;

                if (isEdge) {
                    level.setBlock(pos, Blocks.END_STONE.defaultBlockState(), 2);
                } else if (dx == 0 && dz == 0) {
                    level.setBlock(pos, Blocks.END_PORTAL_FRAME.defaultBlockState(), 2);
                } else {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        return true;
    }
}