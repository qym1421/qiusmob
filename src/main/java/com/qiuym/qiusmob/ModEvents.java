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
    public static GameRules.Key<GameRules.IntegerValue> BINDING_PLAYER_KEY;
    public static GameRules.Key<GameRules.BooleanValue> ENABLE_SKIN_REPLACE_KEY;

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
                    GameRules.BooleanValue.create(false));

            DEATH_CAN_SUBJECTIVE_ATTACK_KEY = GameRules.register("qiusmobDeathCanSubjectiveAttack",
                    GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));

            BINDING_PLAYER_KEY = GameRules.register("qiusmobBinding",
                    GameRules.Category.MISC,
                    GameRules.IntegerValue.create(0));
            
            ENABLE_SKIN_REPLACE_KEY = GameRules.register("qiusmobEnableSkinReplace",
                    GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));
        });
    }
}