package com.qiuym.qiusmob.client;

import com.qiuym.qiusmob.EntityRegister;
import com.qiuym.qiusmob.QiusMob;
import com.qiuym.qiusmob.client.render.*;
import com.qiuym.qiusmob.entity.Death;
import com.qiuym.qiusmob.item.EntityEggs;
import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import com.qiuym.qiusmob.client.render.consciousness.AbyssConsciousnessRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Client {

    // ===== 内部类1：模组事件（Mod Bus） =====
    @Mod.EventBusSubscriber(modid = QiusMob.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {

        private static EntityRendererProvider.Context cachedContext;

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(
                (EntityType<? extends Death>) EntityRegister.DEATH.get(),
                DeathRender::new
            );

            event.registerEntityRenderer(
                EntityRegister.SKIRK.get(),
                SkirkRenderer::new
            );
            
            event.registerEntityRenderer(
                    EntityRegister.ABYSS_CONSCIOUSNESS.get(),
                    AbyssConsciousnessRenderer::new
            );
        }

        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            cachedContext = event.getContext();
        
            var slim = event.getSkin("slim");
            if (slim instanceof PlayerRenderer r) {
                r.addLayer(new AbyssCorruptionLayer(r));
            }
        }

        @SubscribeEvent
        public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
            // 死亡君主刷怪蛋
            event.register((stack, tintIndex) -> {
                if (tintIndex == 0) return 0x5A0F1B;
                if (tintIndex == 1) return 0xC41A22;
                return 0xFFFFFF;
            }, EntityEggs.DEATH_SPAWN_EGG.get());

            // 深渊孤月·丝柯克刷怪蛋
            event.register((stack, tintIndex) -> {
                if (tintIndex == 0) return 0x1A0033;  // 深渊紫黑
                if (tintIndex == 1) return 0x8A2BE2;  // 星尘紫
                return 0xFFFFFF;
            }, EntityEggs.SKIRK_SPAWN_EGG.get());
            
            event.register((stack, tintIndex) -> {
                if (tintIndex == 0) return 0x1A0033;
                if (tintIndex == 1) return 0x8A2BE2;
                return 0xFFFFFF;
            }, EntityEggs.ABYSS_CONSCIOUSNESS_SPAWN_EGG.get());
        }

        public static EntityRendererProvider.Context getContext() {
            return cachedContext;
        }
    }

    // ===== 内部类2：游戏事件（Game Bus） =====
    @Mod.EventBusSubscriber(modid = QiusMob.MOD_ID, value = Dist.CLIENT)
    public static class GameEvents {

        private static final Map<UUID, DeathPlayerRenderer> DEATH_RENDERERS = new HashMap<>();
        private static final Map<UUID, SkirkPlayerRenderer> SKIRK_RENDERERS = new HashMap<>();
        private static boolean isRendering = false;

        @SubscribeEvent
        public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
            var player = event.getEntity();
            var uuid = player.getUUID();

            if (!PlayerConvertToQiusMob.isTransformed(player)) {
                DEATH_RENDERERS.remove(uuid);
                SKIRK_RENDERERS.remove(uuid);
                return;
            }

            // 检查游戏规则：是否允许皮肤替换
            if (player.level() instanceof ServerLevel serverLevel) {
                GameRules.BooleanValue rule = serverLevel.getGameRules()
                        .getRule(com.qiuym.qiusmob.ModEvents.ENABLE_SKIN_REPLACE_KEY);
                if (!rule.get()) {
                    return;
                }
            }

            // 防止递归
            if (isRendering) {
                return;
            }

            var context = Client.ModEvents.getContext();
            if (context == null) {
                return;
            }

            AbstractClientPlayer abstractPlayer = (AbstractClientPlayer) player;
            PlayerConvertToQiusMob.TransformType type = PlayerConvertToQiusMob.getTransformType(player);

            event.setCanceled(true);
            isRendering = true;

            try {
                if (type == PlayerConvertToQiusMob.TransformType.SKIRK) {
                    SkirkPlayerRenderer renderer = SKIRK_RENDERERS.get(uuid);
                    if (renderer == null) {
                        renderer = new SkirkPlayerRenderer(context, true);  // true = Alex 纤细模型
                        SKIRK_RENDERERS.put(uuid, renderer);
                    }
                    renderer.render(
                        abstractPlayer, 0, 0,
                        event.getPoseStack(),
                        event.getMultiBufferSource(),
                        event.getPackedLight()
                    );
                } else {
                    DeathPlayerRenderer renderer = DEATH_RENDERERS.get(uuid);
                    if (renderer == null) {
                        renderer = new DeathPlayerRenderer(context);
                        DEATH_RENDERERS.put(uuid, renderer);
                    }
                    renderer.render(
                        abstractPlayer, 0, 0,
                        event.getPoseStack(),
                        event.getMultiBufferSource(),
                        event.getPackedLight()
                    );
                }
            } finally {
                isRendering = false;
            }
        }
    }
}