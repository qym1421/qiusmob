package com.qiuym.qiusmob.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.qiuym.qiusmob.network.MovementPacket;
import com.qiuym.qiusmob.network.NetworkHandler;
import com.qiuym.qiusmob.network.SkillPacket;
import com.qiuym.qiusmob.network.TransformPacket;
import com.qiuym.qiusmob.network.ViewModePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "qiusmob", value = Dist.CLIENT)
public class KeyBindings {
    public static final String KEY_CATEGORY = "key.category.qiusmob";

    private static boolean clientTransformState = false;
    private static boolean clientViewLocked = true;  // 默认锁定模式
    private static int tickCounter = 0;

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
    
    public static KeyMapping possessKey = new KeyMapping(
            "key.qiusmob.possess",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            KEY_CATEGORY
    );

    public static KeyMapping viewModeKey = new KeyMapping(
            "key.qiusmob.view_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KEY_CATEGORY
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(transformKey);
        event.register(ultimateKey);
        event.register(meleeKey);
        event.register(leapKey);
        event.register(aoeKey);
        event.register(possessKey);
        event.register(viewModeKey);
        System.out.println("[QiusMob] KeyBindings 注册完成");
    }

    public static void setTransformed(boolean state) {
        clientTransformState = state;
    }

    public static void setViewLocked(boolean locked) {
        clientViewLocked = locked;
    }

    public static boolean isViewLocked() {
        return clientViewLocked;
    }

    // ===== 移动输入同步（每2 tick 发送一次） =====
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        tickCounter++;
        if (tickCounter % 2 != 0) return;  // 每2tick发送一次
        
        float forward = 0;
        float left = 0;
        boolean jumping = false;
        
        if (mc.options.keyUp.isDown()) forward += 1;
        if (mc.options.keyDown.isDown()) forward -= 1;
        if (mc.options.keyLeft.isDown()) left += 1;
        if (mc.options.keyRight.isDown()) left -= 1;
        if (mc.options.keyJump.isDown()) jumping = true;

        // 立即发送包
        NetworkHandler.INSTANCE.sendToServer(new MovementPacket(forward, left, jumping));
    }

    // ===== 按键输入 =====
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (transformKey.consumeClick()) {
            clientTransformState = !clientTransformState;
            // System.out.println("[QiusMob] 客户端发送 TransformPacket: " + clientTransformState);
            NetworkHandler.INSTANCE.sendToServer(new TransformPacket(clientTransformState));
            return;
        }

        // G 键切换视角模式
        if (viewModeKey.consumeClick()) {
            clientViewLocked = !clientViewLocked;
            NetworkHandler.INSTANCE.sendToServer(new ViewModePacket(clientViewLocked));
            mc.player.displayClientMessage(
                    Component.literal(clientViewLocked ? "§a【锁定模式】实体跟随玩家视角" : "§e【自由模式】实体AI自主行动"),
                    true
            );
            // System.out.println("[QiusMob] 切换视角模式: " + (clientViewLocked ? "锁定" : "自由"));
            return;
        }

        if (!clientTransformState) return;

        if (ultimateKey.consumeClick()) {
            System.out.println("[QiusMob] 客户端发送技能: ultimate");
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("ultimate"));
            return;
        }

        if (meleeKey.consumeClick()) {
            System.out.println("[QiusMob] 客户端发送技能: melee");
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("melee"));
            return;
        }

        if (leapKey.consumeClick()) {
            System.out.println("[QiusMob] 客户端发送技能: leap");
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("leap"));
            return;
        }

        if (aoeKey.consumeClick()) {
            System.out.println("[QiusMob] 客户端发送技能: aoe");
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("aoe"));
            return;
        }
        
        if (possessKey.consumeClick()) {
            System.out.println("[QiusMob] 客户端发送技能: possess");
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("possess"));
            return;
        }
    }
}