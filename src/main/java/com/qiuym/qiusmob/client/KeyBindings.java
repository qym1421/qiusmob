package com.qiuym.qiusmob.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.network.NetworkHandler;
import com.qiuym.qiusmob.network.SkillPacket;
import com.qiuym.qiusmob.network.TransformPacket;
import com.qiuym.qiusmob.player.PlayerConvertToQiusMob;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "qiusmob", value = Dist.CLIENT)
public class KeyBindings {
    public static final String KEY_CATEGORY = "key.category.qiusmob";

    public static KeyMapping transformKey = new KeyMapping(
            "key.qiusmob.transform",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            KEY_CATEGORY
    );

    public static KeyMapping ultimateKey = new KeyMapping(
            "key.qiusmob.ultimate",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY
    );

    public static KeyMapping meleeKey = new KeyMapping(
            "key.qiusmob.melee",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            KEY_CATEGORY
    );

    public static KeyMapping leapKey = new KeyMapping(
            "key.qiusmob.leap",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            KEY_CATEGORY
    );

    public static KeyMapping aoeKey = new KeyMapping(
            "key.qiusmob.aoe",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            KEY_CATEGORY
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(transformKey);
        event.register(ultimateKey);
        event.register(meleeKey);
        event.register(leapKey);
        event.register(aoeKey);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // R - 切换变形
        if (transformKey.consumeClick()) {
            // 客户端检查绑定（但最终验证在服务端）
            NetworkHandler.INSTANCE.sendToServer(new TransformPacket(
                !PlayerConvertToQiusMob.isTransformed(mc.player)
            ));
            return;
        }

        // 未变形 → 不处理技能
        if (!PlayerConvertToQiusMob.isTransformed(mc.player)) return;

        // O - 终末审判
        if (ultimateKey.consumeClick()) {
            System.out.println("[QiusMob] 客户端发送技能: ultimate");
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("ultimate"));
            return;
        }

        // N - 近战攻击
        if (meleeKey.consumeClick()) {
            System.out.println("[QiusMob] 客户端发送技能: melee");
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("melee"));
            return;
        }

        // J - 跳跃猛击
        if (leapKey.consumeClick()) {
            System.out.println("[QiusMob] 客户端发送技能: leap");
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("leap"));
            return;
        }

        // I - 范围爆发
        if (aoeKey.consumeClick()) {
            System.out.println("[QiusMob] 客户端发送技能: aoe");
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("aoe"));
            return;
        }
    }
}