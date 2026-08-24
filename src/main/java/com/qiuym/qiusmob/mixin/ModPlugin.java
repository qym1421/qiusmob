package com.qiuym.qiusmob.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ModPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 检查 dog 模组是否存在（用于 MixinDevourerHeadEntity）
        if ("com.qiuym.qiusmob.mixin.MixinDevourerHeadEntity".equals(mixinClassName)) {
            boolean dogExists = getClass().getClassLoader()
                    .getResource("it/yuruni/dog/entity/boss/DevourerHeadEntity.class") != null;
            return dogExists;
        }

        // 检查 EyesAreBack 模组是否存在（用于 MixinEyeLingOnEntityTickUpdateProcedure）
        if ("com.qiuym.qiusmob.mixin.MixinEyeTickProcedures".equals(mixinClassName)) {
            boolean eyesExists = getClass().getClassLoader()
                    .getResource("net/mcreator/theeyesareback/procedures/EyeLingOnEntityTickUpdateProcedure.class") != null;
            return eyesExists;
        }

        // 其他 Mixin 默认启用
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}