package com.qiuym.qiusmob.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.ServerHandler;
import com.qiuym.qiusmob.entity.consciousness.AbyssConsciousnessEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ModCommands {

    private static final String DEVOURER_MOD_CLASS = "it.yuruni.dog.Dog";

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        boolean devourerLoaded = isDevourerModLoaded();

        dispatcher.register(Commands.literal("qiusmob")
                .then(Commands.literal("binding")
                        .then(Commands.literal("death")
                                .executes(context -> setBindingDeath(context,
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> setBindingDeath(context,
                                                EntityArgument.getPlayer(context, "player")))
                                )
                        )
                        .then(Commands.literal("skirk")
                                .executes(context -> setBindingSkirk(context,
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> setBindingSkirk(context,
                                                EntityArgument.getPlayer(context, "player")))
                                )
                        )
                        .then(Commands.literal("clear")
                                .executes(context -> clearBindings(context))
                        )
                        .then(Commands.literal("info")
                                .executes(context -> getBindingInfo(context))
                        )
                        .then(Commands.literal("devourer")
                                .requires(source -> devourerLoaded)
                                .executes(context -> setDevourerOwner(context,
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> setDevourerOwner(context,
                                                EntityArgument.getPlayer(context, "player")))
                                )
                        )
                )
                .then(Commands.literal("consciousness")
                        .then(Commands.literal("bind")
                                .executes(context -> bindConsciousness(context,
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> bindConsciousness(context,
                                                EntityArgument.getPlayer(context, "player")))
                                )
                        )
                        .then(Commands.literal("unbind")
                                .executes(context -> unbindConsciousness(context))
                        )
                        .then(Commands.literal("info")
                                .executes(context -> consciousnessInfo(context))
                        )
                )
                .then(Commands.literal("attr")
                        .then(Commands.literal("apply")
                                .executes(context -> applyAttr(context,
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> applyAttr(context,
                                                EntityArgument.getPlayer(context, "player")))
                                )
                        )
                        .then(Commands.literal("remove")
                                .executes(context -> removeAttr(context,
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> removeAttr(context,
                                                EntityArgument.getPlayer(context, "player")))
                                )
                        )
                )
        );
    }

    private static boolean isDevourerModLoaded() {
        try {
            Class.forName(DEVOURER_MOD_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static int applyAttr(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        ServerHandler.ForgeBusHandler.applyMorphAttributes(player);
        context.getSource().sendSuccess(() ->
                Component.literal("§a已为 " + player.getName().getString() + " 应用变形属性加成"), true);
        return 1;
    }

    private static int removeAttr(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        ServerHandler.ForgeBusHandler.removeMorphAttributes(player);
        context.getSource().sendSuccess(() ->
                Component.literal("§e已移除 " + player.getName().getString() + " 的变形属性加成"), true);
        return 1;
    }

    private static int setBindingDeath(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;
        int hash = player.getUUID().hashCode();
        serverLevel.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).set(0, null);
        serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(hash, null);
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.qiusmob.binding.death",
                        player.getName().getString()), true);
        return 1;
    }

    private static int setBindingSkirk(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;
        int hash = player.getUUID().hashCode();
        serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(0, null);
        serverLevel.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).set(hash, null);
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.qiusmob.binding.skirk",
                        player.getName().getString()), true);
        return 1;
    }

    private static int clearBindings(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;
        serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(0, null);
        serverLevel.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).set(0, null);
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.qiusmob.binding.clear"), true);
        return 1;
    }

    private static int getBindingInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;
        int deathHash = serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).get();
        int skirkHash = serverLevel.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).get();
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.qiusmob.binding.info.header"), false);
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.qiusmob.binding.info.death",
                        deathHash == 0 ? Component.translatable("commands.qiusmob.binding.info.none")
                                : Component.literal(String.valueOf(deathHash))), false);
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.qiusmob.binding.info.skirk",
                        skirkHash == 0 ? Component.translatable("commands.qiusmob.binding.info.none")
                                : Component.literal(String.valueOf(skirkHash))), false);
        return 1;
    }

    private static int setDevourerOwner(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;
        int hash = player.getUUID().hashCode();
        serverLevel.getGameRules().getRule(ModEvents.DEVOURER_OWNER_KEY).set(hash, null);
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.qiusmob.binding.devourer",
                        player.getName().getString(), hash), true);
        return 1;
    }

    private static int bindConsciousness(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;
        AbyssConsciousnessEntity entity = null;
        for (AbyssConsciousnessEntity e : serverLevel.getEntitiesOfClass(
                AbyssConsciousnessEntity.class, player.getBoundingBox().inflate(64))) {
            entity = e;
            break;
        }
        if (entity == null) {
            context.getSource().sendFailure(
                    Component.translatable("commands.qiusmob.consciousness.not_found"));
            return 0;
        }
        entity.bindTo(player);
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.qiusmob.consciousness.bind",
                        player.getName().getString()), true);
        return 1;
    }

    private static int unbindConsciousness(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;
        for (AbyssConsciousnessEntity e : serverLevel.getEntitiesOfClass(
                AbyssConsciousnessEntity.class, context.getSource().getPlayerOrException().getBoundingBox().inflate(64))) {
            e.unbind();
        }
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.qiusmob.consciousness.unbind"), true);
        return 1;
    }

    private static int consciousnessInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;
        for (AbyssConsciousnessEntity e : serverLevel.getEntitiesOfClass(
                AbyssConsciousnessEntity.class, context.getSource().getPlayerOrException().getBoundingBox().inflate(64))) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.qiusmob.consciousness.info.phase", e.getPhase().name()), false);
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.qiusmob.consciousness.info.target",
                            e.getTargetUUID() != null ? e.getTargetUUID().toString()
                                    : Component.translatable("commands.qiusmob.binding.info.none")), false);
            return 1;
        }
        context.getSource().sendFailure(
                Component.translatable("commands.qiusmob.consciousness.not_found"));
        return 0;
    }

    private static ServerLevel getServerLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Level level = context.getSource().getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            context.getSource().sendFailure(
                    Component.translatable("commands.qiusmob.server_only"));
            return null;
        }
        return serverLevel;
    }
}