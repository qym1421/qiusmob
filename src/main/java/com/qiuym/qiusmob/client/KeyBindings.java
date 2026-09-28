package com.qiuym.qiusmob.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.qiuym.qiusmob.QiusMob;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = QiusMob.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {

    public static final String CATEGORY_GENERAL = "key.category.qiusmob.general";
    public static final String CATEGORY_DEATH = "key.category.qiusmob.death";
    public static final String CATEGORY_SKIRK = "key.category.qiusmob.skirk";
    private static boolean clientTransformState = false;

    public static void setTransformed(boolean state) {
        clientTransformState = state;
    }
    
    public static boolean isTransformed() {
        return clientTransformState;
    }

    // 通用
    public static final KeyMapping transformKey = new KeyMapping(
            "key.qiusmob.transform", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY_GENERAL);
    public static final KeyMapping possessKey = new KeyMapping(
            "key.qiusmob.possess", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY_GENERAL);
    public static final KeyMapping viewModeKey = new KeyMapping(
            "key.qiusmob.view_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY_GENERAL);

    // 死亡君主
    public static final KeyMapping ultimateKey = new KeyMapping(
            "key.qiusmob.death.ultimate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY_DEATH);
    public static final KeyMapping meleeKey = new KeyMapping(
            "key.qiusmob.death.melee", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY_DEATH);
    public static final KeyMapping leapKey = new KeyMapping(
            "key.qiusmob.death.leap", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY_DEATH);
    public static final KeyMapping aoeKey = new KeyMapping(
            "key.qiusmob.death.aoe", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY_DEATH);

    // 丝柯克
    public static final KeyMapping skirkN = new KeyMapping(
            "key.qiusmob.skirk.n", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY_SKIRK);
    public static final KeyMapping skirkJ = new KeyMapping(
            "key.qiusmob.skirk.j", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY_SKIRK);
    public static final KeyMapping skirkI = new KeyMapping(
            "key.qiusmob.skirk.i", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY_SKIRK);
    public static final KeyMapping skirkFire = new KeyMapping(
            "key.qiusmob.skirk.fire",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,  // 默认绑定鼠标左键
            CATEGORY_SKIRK
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(transformKey);
        event.register(possessKey);
        event.register(viewModeKey);
        event.register(ultimateKey);
        event.register(meleeKey);
        event.register(leapKey);
        event.register(aoeKey);
        event.register(skirkN);
        event.register(skirkJ);
        event.register(skirkI);
        event.register(skirkFire);
    }
}