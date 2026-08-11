package com.qiuym.qiusmob.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.qiuym.qiusmob.ModEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("qiusmob")
            .then(Commands.literal("binding")
                .then(Commands.literal("death")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> setBindingDeath(context,
                            EntityArgument.getPlayer(context, "player")))
                    )
                )
                .then(Commands.literal("clear")
                    .executes(context -> clearBindingDeath(context))
                )
                .then(Commands.literal("info")
                    .executes(context -> getBindingInfo(context))
                )
            )
        );
    }

    private static int setBindingDeath(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException {
        ServerLevel serverLevel;
        if (!(context.getSource().getLevel() instanceof ServerLevel)) {
            context.getSource().sendFailure(Component.literal("§c只能在服务端执行！"));
            return 0;
        } else {
            serverLevel = (ServerLevel) context.getSource().getLevel();
        }

        int hash = player.getUUID().hashCode();
        serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(hash, null);

        context.getSource().sendSuccess(() ->
                Component.literal("§a已绑定死亡君主: " + player.getName().getString()),
                true
        );
        return 1;
    }

    private static int clearBindingDeath(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel serverLevel;
        if (!(context.getSource().getLevel() instanceof ServerLevel)) {
            context.getSource().sendFailure(Component.literal("§c只能在服务端执行！"));
            return 0;
        } else {
            serverLevel = (ServerLevel) context.getSource().getLevel();
        }

        serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(0, null);

        context.getSource().sendSuccess(() ->
                Component.literal("§e已清除死亡君主绑定"),
                true
        );
        return 1;
    }

    private static int getBindingInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel serverLevel;
        if (!(context.getSource().getLevel() instanceof ServerLevel)) {
            context.getSource().sendFailure(Component.literal("§c只能在服务端执行！"));
            return 0;
        } else {
            serverLevel = (ServerLevel) context.getSource().getLevel();
        }

        int hash = serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).get();
        if (hash == 0) {
            context.getSource().sendSuccess(() ->
                    Component.literal("§c当前没有绑定任何玩家"),
                    false
            );
            return 0;
        }

        context.getSource().sendSuccess(() ->
                Component.literal("§a绑定玩家 UUID Hash: §7" + hash),
                false
        );
        return 1;
    }
}