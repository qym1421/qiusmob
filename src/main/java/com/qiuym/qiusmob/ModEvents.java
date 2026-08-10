package com.qiuym.qiusmob;

import com.qiuym.qiusmob.entity.Death;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = QiusMob.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {

    public static GameRules.Key<GameRules.BooleanValue> ENABLE_BOSS_AI_KEY;
    public static GameRules.Key<GameRules.BooleanValue> CAN_ATTACK_DEATH_KEY;
    public static GameRules.Key<GameRules.BooleanValue> DEATH_CAN_SUBJECTIVE_ATTACK_KEY;

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(EntityRegister.DEATH.get(), Death.createAttributes().build());
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ENABLE_BOSS_AI_KEY = GameRules.register("qiusmobEnableBossAI",
                    GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));

            CAN_ATTACK_DEATH_KEY = GameRules.register("qiusmobCanAttackDeath",
                    GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));
            DEATH_CAN_SUBJECTIVE_ATTACK_KEY = GameRules.register("qiusmobDeathCanSubjectiveAttack",
                    GameRules.Category.MISC,
                    GameRules.BooleanValue.create(false));
        });
    }
}