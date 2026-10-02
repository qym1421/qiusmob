package com.qiuym.qiusmob.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.qiuym.qiusmob.ModEvents;
import com.qiuym.qiusmob.entity.consciousness.AbyssConsciousnessEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
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
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> setBindingDeath(context,
                            EntityArgument.getPlayer(context, "player")))
                    )
                )
                .then(Commands.literal("skirk")
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
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> setDevourerOwner(context,
                            EntityArgument.getPlayer(context, "player")))
                    )
                )
            )
            .then(Commands.literal("consciousness")
                .then(Commands.literal("bind")
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

    // ===== 绑定死亡君主（互斥：清空丝柯克） =====
    private static int setBindingDeath(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;

        int hash = player.getUUID().hashCode();
        serverLevel.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).set(0, null);
        serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(hash, null);

        context.getSource().sendSuccess(() ->
                Component.literal("§a已绑定死亡君主: " + player.getName().getString()),
                true
        );
        return 1;
    }

    // ===== 绑定深渊孤月（互斥：清空死亡君主） =====
    private static int setBindingSkirk(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;

        int hash = player.getUUID().hashCode();
        serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(0, null);
        serverLevel.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).set(hash, null);

        context.getSource().sendSuccess(() ->
                Component.literal("§5已绑定深渊孤月: " + player.getName().getString()),
                true
        );
        return 1;
    }

    // ===== 清除所有绑定 =====
    private static int clearBindings(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;

        serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).set(0, null);
        serverLevel.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).set(0, null);

        context.getSource().sendSuccess(() ->
                Component.literal("§e已清除所有 Boss 绑定"),
                true
        );
        return 1;
    }

    // ===== 查询绑定信息 =====
    private static int getBindingInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;

        int deathHash = serverLevel.getGameRules().getRule(ModEvents.BINDING_PLAYER_KEY).get();
        int skirkHash = serverLevel.getGameRules().getRule(ModEvents.SKIRK_BINDING_KEY).get();

        context.getSource().sendSuccess(() ->
                Component.literal("§6===== QiusMob 绑定信息 ====="),
                false
        );
        context.getSource().sendSuccess(() ->
                Component.literal("§c死亡君主 Hash: §7" + (deathHash == 0 ? "无" : deathHash)),
                false
        );
        context.getSource().sendSuccess(() ->
                Component.literal("§5深渊孤月 Hash: §7" + (skirkHash == 0 ? "无" : skirkHash)),
                false
        );
        return 1;
    }

    // ===== 神明吞噬者拥有者绑定 =====
    private static int setDevourerOwner(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;

        int hash = player.getUUID().hashCode();
        serverLevel.getGameRules().getRule(ModEvents.DEVOURER_OWNER_KEY).set(hash, null);

        context.getSource().sendSuccess(() ->
                Component.literal("§a已设置神明吞噬者拥有者: " + player.getName().getString() + " (Hash: " + hash + ")"),
                true
        );
        return 1;
    }
    
    private static int bindConsciousness(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;
    
        // 找最近的意识实体
        AbyssConsciousnessEntity entity = null;
        for (AbyssConsciousnessEntity e : serverLevel.getEntitiesOfClass(
                AbyssConsciousnessEntity.class, player.getBoundingBox().inflate(64))) {
            entity = e;
            break;
        }
    
        if (entity == null) {
            context.getSource().sendFailure(Component.literal("§c附近没有深渊意识！"));
            return 0;
        }
    
        entity.bindTo(player);
        context.getSource().sendSuccess(() ->
                Component.literal("§5已将深渊意识绑定到: " + player.getName().getString()), true);
        return 1;
    }
    
    private static int unbindConsciousness(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;
    
        for (AbyssConsciousnessEntity e : serverLevel.getEntitiesOfClass(
                AbyssConsciousnessEntity.class, context.getSource().getPlayerOrException().getBoundingBox().inflate(64))) {
            e.unbind();
        }
    
        context.getSource().sendSuccess(() -> Component.literal("§e已解除绑定"), true);
        return 1;
    }
    
    private static int consciousnessInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel serverLevel = getServerLevel(context);
        if (serverLevel == null) return 0;
    
        for (AbyssConsciousnessEntity e : serverLevel.getEntitiesOfClass(
                AbyssConsciousnessEntity.class, context.getSource().getPlayerOrException().getBoundingBox().inflate(64))) {
            context.getSource().sendSuccess(() ->
                    Component.literal("§5意识阶段: " + e.getPhase().name()), false);
            context.getSource().sendSuccess(() ->
                    Component.literal("§5绑定对象: " + (e.getTargetUUID() != null ? e.getTargetUUID() : "无")), false);
            return 1;
        }
    
        context.getSource().sendFailure(Component.literal("§c附近没有深渊意识！"));
        return 0;
    }

    // ===== 辅助方法 =====
    private static ServerLevel getServerLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Level level = context.getSource().getLevel();
        if (!(level instanceof ServerLevel)) {
            context.getSource().sendFailure(Component.literal("§c只能在服务端执行！"));
            return null;
        }
        return (ServerLevel) level;
    }
}