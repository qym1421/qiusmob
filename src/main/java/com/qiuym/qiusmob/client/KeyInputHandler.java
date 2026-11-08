package com.qiuym.qiusmob.client;

import com.qiuym.qiusmob.QiusMob;
import com.qiuym.qiusmob.network.MovementPacket;
import com.qiuym.qiusmob.network.NetworkHandler;
import com.qiuym.qiusmob.network.SkillPacket;
import com.qiuym.qiusmob.network.TransformPacket;
import com.qiuym.qiusmob.network.ViewModePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QiusMob.MOD_ID, value = Dist.CLIENT)
public class KeyInputHandler {

    private static boolean clientTransformState = false;
    private static boolean clientViewLocked = true;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        tickCounter++;
        if (tickCounter % 2 == 0) {
            float forward = 0, left = 0;
            boolean jumping = false;
            if (mc.options.keyUp.isDown()) forward += 1;
            if (mc.options.keyDown.isDown()) forward -= 1;
            if (mc.options.keyLeft.isDown()) left += 1;
            if (mc.options.keyRight.isDown()) left -= 1;
            if (mc.options.keyJump.isDown()) jumping = true;
            NetworkHandler.INSTANCE.sendToServer(new MovementPacket(forward, left, jumping));
        }

        while (KeyBindings.transformKey.consumeClick()) {
            clientTransformState = !clientTransformState;
            NetworkHandler.INSTANCE.sendToServer(new TransformPacket(clientTransformState));
        }

        while (KeyBindings.viewModeKey.consumeClick()) {
            clientViewLocked = !clientViewLocked;
            NetworkHandler.INSTANCE.sendToServer(new ViewModePacket(clientViewLocked));
            mc.player.displayClientMessage(
                    Component.literal(clientViewLocked ? "§a【锁定模式】" : "§e【自由模式】"),
                    true);
        }

        if (!clientTransformState) return;

        while (KeyBindings.possessKey.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("possess"));
        }
        while (KeyBindings.ultimateKey.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("ultimate"));
        }
        while (KeyBindings.meleeKey.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("melee"));
        }
        while (KeyBindings.leapKey.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("leap"));
        }
        while (KeyBindings.aoeKey.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("aoe"));
        }
        while (KeyBindings.skirkN.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("skirk_n"));
        }
        while (KeyBindings.skirkJ.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("skirk_j"));
        }
        while (KeyBindings.skirkI.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("skirk_i"));
        }
        while (KeyBindings.skirkFire.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new SkillPacket("skirk_fire"));
        }
    }
}