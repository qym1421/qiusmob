package com.qiuym.qiusmob;

import com.qiuym.qiusmob.entity.Death;
import com.qiuym.qiusmob.entity.skirk.SkirkEntity;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = QiusMob.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {

    public static GameRules.Key<GameRules.BooleanValue> ENABLE_BOSS_AI_KEY;
    public static GameRules.Key<GameRules.BooleanValue> CAN_ATTACK_BOSS_KEY;
    public static GameRules.Key<GameRules.BooleanValue> DEATH_CAN_SUBJECTIVE_ATTACK_KEY;
    public static GameRules.Key<GameRules.IntegerValue> BINDING_PLAYER_KEY;
    public static GameRules.Key<GameRules.BooleanValue> ENABLE_SKIN_REPLACE_KEY;
    public static GameRules.Key<GameRules.IntegerValue> DEVOURER_OWNER_KEY;
    public static GameRules.Key<GameRules.IntegerValue> SKIRK_BINDING_KEY;

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(EntityRegister.DEATH.get(), Death.createAttributes().build());
        event.put(EntityRegister.SKIRK.get(), SkirkEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ENABLE_BOSS_AI_KEY = GameRules.register("qiusmobEnableBossAI",
                    GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));

            CAN_ATTACK_BOSS_KEY = GameRules.register("qiusmobCanAttackBoss",
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
            
            DEVOURER_OWNER_KEY = GameRules.register("qiusmobDevourerOwner",
                    GameRules.Category.MISC,
                    GameRules.IntegerValue.create(0));
                    
            SKIRK_BINDING_KEY = GameRules.register("qiusmobSkirkBinding",
                    GameRules.Category.MISC,
                    GameRules.IntegerValue.create(0));
        });
    }
}