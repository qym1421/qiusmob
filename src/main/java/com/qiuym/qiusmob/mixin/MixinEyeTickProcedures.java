package com.qiuym.qiusmob.mixin;

import net.mcreator.theeyesareback.procedures.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为所有眼睛实体的 Tick 过程注入队伍检查。
 * 不重写任何逻辑，仅在方法末尾检查目标是否同队。
 */
@Mixin(value = {
    CrawlingEnderEyeminationOnEntityTickUpdateProcedure.class,
    DestroyerOnEntityTickUpdateProcedure.class,
    EnderEYeminationOnEntityTickUpdateProcedure.class,
    EnragedEyeHumanOnEntityTickUpdateProcedure.class,
    ExtremeEyeHumanoidOnEntityTickUpdateProcedure.class,
    EyeColonyOnEntityTickUpdateProcedure.class,
    EyeColonyStage2OnEntityTickUpdateProcedure.class,
    EyeColonyStage3OnEntityTickUpdateProcedure.class,
    EyeHumanoidOnEntityTickUpdateProcedure.class,
    EyeHumanOnEntityTickUpdateProcedure.class,
    EyeLingOnEntityTickUpdateProcedure.class,
    EyeSourceStage1OnEntityTickUpdateProcedure.class,
    EyeSpidaOnEntityTickUpdateProcedure.class,
    EyeSquiggleOnEntityTickUpdateProcedure.class,
    EyeTreeStage2OnEntityTickUpdateProcedure.class,
    EyeTreeStage3OnEntityTickUpdateProcedure.class,
    EyeTurretOnEntityTickUpdateProcedure.class,
    EYeVedetteOnEntityTickUpdateProcedure.class,
    FlyingEyeLingOnEntityTickUpdateProcedure.class,
    FlyingEyeSquidOnEntityTickUpdateProcedure.class,
    HyperFlyingEyeOnEntityTickUpdateProcedure.class
    // EyeTumor 已排除（不存在对应的 OnEntityTickUpdateProcedure）
})
public class MixinEyeTickProcedures {

    /**
     * 在 execute 方法所有逻辑执行完毕后，检查目标是否同队。
     * 若是同队则清除目标，且不取消原方法。
     */
    @Inject(
        method = "execute",
        at = @At("RETURN"),
        remap = false,
        cancellable = false
    )
    private static void onExecuteReturn(
            LevelAccessor world,
            double x,
            double y,
            double z,
            Entity entity,
            CallbackInfo ci
    ) {
        if (!(entity instanceof Mob mob)) return;

        LivingEntity target = mob.getTarget();
        if (target == null) return;

        // 队伍检查：同队且禁止友伤 → 清除目标
        Team mobTeam = mob.getTeam();
        Team targetTeam = target.getTeam();
        if (mobTeam != null && targetTeam != null && mobTeam == targetTeam) {
            if (!mobTeam.isAllowFriendlyFire()) {
                mob.setTarget(null);
            }
        }
    }
}