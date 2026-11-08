package com.qiuym.qiusmob.morph;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import tocraft.walkers.api.PlayerShape;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Mod.EventBusSubscriber(modid = "qiusmob", value = Dist.CLIENT)
public class AnimationCommand {

    private static final Map<UUID, List<String>> INJECTED_CONTROLLERS = new HashMap<>();
    private static final List<AnimEntry> ANIM_CACHE = new ArrayList<>();
    private static final Map<String, AnimEntry> ANIM_INDEX = new HashMap<>();
    private static volatile boolean cacheLoaded = false;

    private static final Map<UUID, String> SHAPE_CACHE = new HashMap<>();
    private static final Map<UUID, Integer> FORM_STATE = new HashMap<>();
    private static final Map<UUID, Long> PENDING_FORM_CHANGE = new HashMap<>();
    private static final Path CACHE_FILE = FMLPaths.CONFIGDIR.get().resolve("qiusmob_anim_cache.txt");

    private static final SuggestionProvider<CommandSourceStack> ANIM_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    ANIM_CACHE.stream().map(e -> e.fullId).toList(), builder);

    private static long lastAttackTrigger = 0;
    private static long lastUseTrigger = 0;
    private static final Map<UUID, AnimationController<GeoEntity>> MOVEMENT_CTRL = new HashMap<>();

    public static class AnimEntry {
        public final String namespace;
        public final String filePath;
        public final String animName;
        public final String fullId;
        public final String controllerName;
        public final String triggerName;
        public final long durationMs;

        public AnimEntry(String namespace, String filePath, String animName, long durationMs) {
            this.namespace = namespace;
            this.filePath = filePath;
            this.animName = animName;
            this.durationMs = durationMs;
            this.fullId = namespace + ":" + filePath + "@" + animName;
            String safe = (namespace + "_" + filePath + "_" + animName)
                    .replace('/', '_').replace('.', '_').replace('-', '_');
            this.controllerName = "qius_ctrl_" + safe;
            this.triggerName = "qius_trg_" + safe;
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            loadAnimCache();
            loadPersistentCache();
        });
    }

    private static void loadAnimCache() {
        if (cacheLoaded) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getResourceManager() == null) return;

        ANIM_CACHE.clear();
        ANIM_INDEX.clear();

        Map<ResourceLocation, Resource> files = mc.getResourceManager()
                .listResources("animations", p -> p.getPath().endsWith(".animation.json"));

        for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
            ResourceLocation loc = e.getKey();
            String namespace = loc.getNamespace();
            String path = loc.getPath();
            if (!path.endsWith(".animation.json")) continue;
            String filePath = path.substring(0, path.length() - ".animation.json".length());

            try (InputStreamReader reader = new InputStreamReader(e.getValue().open())) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("animations")) continue;
                JsonObject anims = root.getAsJsonObject("animations");
                for (String key : anims.keySet()) {
                    JsonObject animObj = anims.getAsJsonObject(key);
                    long durationMs = 0;
                    if (animObj.has("animation_length")) {
                        durationMs = (long) (animObj.get("animation_length").getAsDouble() * 1000);
                    }
                    AnimEntry entry = new AnimEntry(namespace, filePath, key, durationMs);
                    ANIM_CACHE.add(entry);
                    ANIM_INDEX.put(entry.fullId, entry);
                }
            } catch (Exception ignored) {
            }
        }
        ANIM_CACHE.sort(Comparator.comparing(a -> a.fullId));
        cacheLoaded = true;
    }

    private static void loadPersistentCache() {
        try {
            if (!Files.exists(CACHE_FILE)) return;
            for (String line : Files.readAllLines(CACHE_FILE)) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                UUID uuid = UUID.fromString(line.substring(0, eq));
                String value = line.substring(eq + 1);
                if (!value.isEmpty()) SHAPE_CACHE.put(uuid, value);
            }
        } catch (Exception ignored) {
        }
    }

    private static void savePersistentCache() {
        try {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<UUID, String> e : SHAPE_CACHE.entrySet()) {
                lines.add(e.getKey() + "=" + e.getValue());
            }
            Files.write(CACHE_FILE, lines);
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity shape = PlayerShape.getCurrentShape(mc.player);
        if (shape == null || !(shape instanceof GeoEntity geo)) {
            if (!SHAPE_CACHE.isEmpty()) {
                SHAPE_CACHE.clear();
                savePersistentCache();
            }
            stopAllInjected(mc.player.getUUID(), null);
            MOVEMENT_CTRL.remove(mc.player.getUUID());
            FORM_STATE.remove(mc.player.getUUID());
            PENDING_FORM_CHANGE.remove(mc.player.getUUID());
            return;
        }

        ResourceLocation typeId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(shape.getType());
        if (typeId == null) return;
        String shapeKey = typeId.getNamespace() + ":" + typeId.getPath();

        String cached = SHAPE_CACHE.get(mc.player.getUUID());
        if (cached == null || !cached.startsWith(shapeKey + "|")) {
            String filePrefix = findAnimFilePrefix(shapeKey);
            if (filePrefix == null) return;
            stopAllInjected(mc.player.getUUID(), geo);
            MOVEMENT_CTRL.remove(mc.player.getUUID());
            SHAPE_CACHE.put(mc.player.getUUID(), shapeKey + "|" + filePrefix);
            FORM_STATE.put(mc.player.getUUID(), 0);
            PENDING_FORM_CHANGE.remove(mc.player.getUUID());
            savePersistentCache();
            cached = SHAPE_CACHE.get(mc.player.getUUID());
        }

        String filePrefix = cached.substring(cached.indexOf('|') + 1);
        if (filePrefix.isEmpty()) return;

        Long changeEnd = PENDING_FORM_CHANGE.get(mc.player.getUUID());
        if (changeEnd != null && System.currentTimeMillis() >= changeEnd) {
            PENDING_FORM_CHANGE.remove(mc.player.getUUID());
            FORM_STATE.put(mc.player.getUUID(), 1);
            MOVEMENT_CTRL.remove(mc.player.getUUID());
        }

        int form = FORM_STATE.getOrDefault(mc.player.getUUID(), 0);

        if (!MOVEMENT_CTRL.containsKey(mc.player.getUUID())) {
            boolean hasChange = !findAnimCandidatesExact(filePrefix, "change").isEmpty();
            String idleAnim = pickAnim(filePrefix, "idle", form, !hasChange);
            String walkAnim = pickWalkAnim(filePrefix, form);
            String runAnim = pickRunAnim(filePrefix, form);
            String swimAnim = pickSwimAnim(filePrefix, form);
            if (idleAnim != null && walkAnim != null) {
                injectMovementController(geo, filePrefix, form, idleAnim, walkAnim, runAnim, swimAnim);
            }
        }
    }

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity shape = PlayerShape.getCurrentShape(mc.player);
        if (!(shape instanceof GeoEntity geo)) return;

        String cached = SHAPE_CACHE.get(mc.player.getUUID());
        if (cached == null) return;
        int bar = cached.indexOf('|');
        if (bar < 0) return;
        String filePrefix = cached.substring(bar + 1);
        if (filePrefix.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastAttackTrigger < 400) return;
        lastAttackTrigger = now;

        int form = FORM_STATE.getOrDefault(mc.player.getUUID(), 0);
        String attackAnim = pickAnim(filePrefix, "attack", form, false);
        if (attackAnim != null) {
            triggerOnce(geo, filePrefix, attackAnim);
        }
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity shape = PlayerShape.getCurrentShape(mc.player);
        if (!(shape instanceof GeoEntity geo)) return;

        String cached = SHAPE_CACHE.get(mc.player.getUUID());
        if (cached == null) return;
        int bar = cached.indexOf('|');
        if (bar < 0) return;
        String filePrefix = cached.substring(bar + 1);
        if (filePrefix.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastUseTrigger < 400) return;
        lastUseTrigger = now;

        if (FORM_STATE.getOrDefault(mc.player.getUUID(), 0) != 0) return;
        if (PENDING_FORM_CHANGE.containsKey(mc.player.getUUID())) return;

        List<String> changes = findAnimCandidatesExact(filePrefix, "change");
        if (changes.isEmpty()) return;

        String changeAnim = changes.get(new Random().nextInt(changes.size()));
        AnimEntry entry = ANIM_INDEX.get(filePrefix + "@" + changeAnim);
        long durationMs = entry != null && entry.durationMs > 0 ? entry.durationMs : 1000L;

        triggerOnce(geo, filePrefix, changeAnim);
        PENDING_FORM_CHANGE.put(mc.player.getUUID(), System.currentTimeMillis() + durationMs);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        int key = event.getKey();
        if (key < GLFW.GLFW_KEY_1 || key > GLFW.GLFW_KEY_9) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        Entity shape = PlayerShape.getCurrentShape(mc.player);
        if (!(shape instanceof GeoEntity geo)) return;

        String cached = SHAPE_CACHE.get(mc.player.getUUID());
        if (cached == null) return;
        int bar = cached.indexOf('|');
        if (bar < 0) return;
        String filePrefix = cached.substring(bar + 1);
        if (filePrefix.isEmpty()) return;

        List<String> skills = findSkillAnims(filePrefix);
        int index = key - GLFW.GLFW_KEY_1;
        if (index < 0 || index >= skills.size()) return;

        triggerOnce(geo, filePrefix, skills.get(index));
    }

    private static String pickAnim(String filePrefix, String base, int form, boolean allowRandom) {
        String suffix = form == 0 ? null : "_2";
        List<String> candidates = new ArrayList<>();
        String prefix = filePrefix + "@";
        String baseLower = base.toLowerCase(Locale.ROOT);
        for (AnimEntry e : ANIM_CACHE) {
            if (!e.fullId.startsWith(prefix)) continue;
            String name = e.animName.toLowerCase(Locale.ROOT);
            if (suffix == null) {
                if (name.equals(baseLower) || name.equals(baseLower + "_1")) {
                    candidates.add(e.animName);
                }
            } else {
                if (name.equals(baseLower + suffix)) {
                    candidates.add(e.animName);
                }
            }
        }
        if (candidates.isEmpty()) return null;
        if (allowRandom) {
            return candidates.get(new Random().nextInt(candidates.size()));
        }
        for (String c : candidates) {
            if (c.toLowerCase(Locale.ROOT).equals(baseLower)) return c;
        }
        return candidates.get(0);
    }

    private static String pickWalkAnim(String filePrefix, int form) {
        String suffix = form == 0 ? null : "_2";
        List<String> walkCandidates = new ArrayList<>();
        List<String> moveCandidates = new ArrayList<>();
        String prefix = filePrefix + "@";
        for (AnimEntry e : ANIM_CACHE) {
            if (!e.fullId.startsWith(prefix)) continue;
            String name = e.animName.toLowerCase(Locale.ROOT);
            if (suffix == null) {
                if (name.equals("walk") || name.equals("walk_1")) walkCandidates.add(e.animName);
                else if (name.equals("move") || name.equals("move_1")) moveCandidates.add(e.animName);
            } else {
                if (name.equals("walk" + suffix)) walkCandidates.add(e.animName);
                else if (name.equals("move" + suffix)) moveCandidates.add(e.animName);
            }
        }
        List<String> list = !walkCandidates.isEmpty() ? walkCandidates : moveCandidates;
        if (list.isEmpty()) return null;
        return list.get(new Random().nextInt(list.size()));
    }

    private static String pickRunAnim(String filePrefix, int form) {
        String suffix = form == 0 ? null : "_2";
        List<String> candidates = new ArrayList<>();
        String prefix = filePrefix + "@";
        for (AnimEntry e : ANIM_CACHE) {
            if (!e.fullId.startsWith(prefix)) continue;
            String name = e.animName.toLowerCase(Locale.ROOT);
            if (suffix == null) {
                if (name.equals("run") || name.equals("run_1")) candidates.add(e.animName);
            } else {
                if (name.equals("run" + suffix)) candidates.add(e.animName);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    private static String pickSwimAnim(String filePrefix, int form) {
        String suffix = form == 0 ? null : "_2";
        List<String> candidates = new ArrayList<>();
        String prefix = filePrefix + "@";
        for (AnimEntry e : ANIM_CACHE) {
            if (!e.fullId.startsWith(prefix)) continue;
            String name = e.animName.toLowerCase(Locale.ROOT);
            boolean water = name.startsWith("water");
            boolean swim = name.startsWith("swim");
            if (!water && !swim) continue;
            if (suffix == null) {
                if (name.equals("water") || name.equals("swim")
                        || name.equals("water_1") || name.equals("swim_1")) {
                    candidates.add(e.animName);
                }
            } else {
                if (name.equals("water" + suffix) || name.equals("swim" + suffix)) {
                    candidates.add(e.animName);
                }
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    private static List<String> findSkillAnims(String filePrefix) {
        List<String> result = new ArrayList<>();
        String prefix = filePrefix + "@";
        for (AnimEntry e : ANIM_CACHE) {
            if (!e.fullId.startsWith(prefix)) continue;
            if (e.animName.toLowerCase(Locale.ROOT).startsWith("skill")) {
                result.add(e.animName);
            }
        }
        Collections.sort(result);
        return result;
    }

    private static List<String> findAnimCandidatesExact(String filePrefix, String keyword) {
        List<String> result = new ArrayList<>();
        String prefix = filePrefix + "@";
        for (AnimEntry e : ANIM_CACHE) {
            if (!e.fullId.startsWith(prefix)) continue;
            if (e.animName.toLowerCase(Locale.ROOT).equals(keyword.toLowerCase(Locale.ROOT))) {
                result.add(e.animName);
            }
        }
        return result;
    }

    private static String findAnimFilePrefix(String shapeKey) {
        int colon = shapeKey.indexOf(':');
        if (colon < 0) return null;
        String namespace = shapeKey.substring(0, colon);
        String path = shapeKey.substring(colon + 1);

        String direct = namespace + ":animations/entity/" + path;
        for (AnimEntry e : ANIM_CACHE) {
            if ((e.namespace + ":" + e.filePath).equals(direct)) return direct;
        }
        for (AnimEntry e : ANIM_CACHE) {
            if (!e.namespace.equals(namespace)) continue;
            String lowerFile = e.filePath.toLowerCase(Locale.ROOT);
            String lowerPath = path.toLowerCase(Locale.ROOT);
            if (lowerFile.endsWith("/" + lowerPath) || lowerFile.endsWith(lowerPath)) {
                return e.namespace + ":" + e.filePath;
            }
        }
        for (AnimEntry e : ANIM_CACHE) {
            if (!e.namespace.equals(namespace)) continue;
            if (e.filePath.toLowerCase(Locale.ROOT).contains(path.toLowerCase(Locale.ROOT))) {
                return e.namespace + ":" + e.filePath;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void injectMovementController(GeoEntity geo, String filePrefix, int form,
                                                 String idleAnim, String walkAnim,
                                                 String runAnim, String swimAnim) {
        Entity entity = (Entity) geo;
        AnimatableInstanceCache cache = geo.getAnimatableInstanceCache();
        if (cache == null) return;
        AnimatableManager<?> manager = getManager(cache, entity.getId());
        if (manager == null) return;
        Map<String, AnimationController<?>> controllers = (Map) manager.getAnimationControllers();
        if (controllers == null) return;

        String ctrlName = "qius_move_" + filePrefix.replaceAll("[^a-z0-9_]", "_") + "_f" + form;
        if (controllers.containsKey(ctrlName)) {
            MOVEMENT_CTRL.put(entity.getUUID(), (AnimationController<GeoEntity>) controllers.get(ctrlName));
            return;
        }

        RawAnimation idle = RawAnimation.begin().thenLoop(idleAnim);
        RawAnimation walk = RawAnimation.begin().thenLoop(walkAnim);
        RawAnimation run = runAnim != null ? RawAnimation.begin().thenLoop(runAnim) : walk;
        RawAnimation swim = swimAnim != null ? RawAnimation.begin().thenLoop(swimAnim) : walk;

        AnimationController<GeoEntity> ctrl = new AnimationController<>(
                geo, ctrlName, 0, test -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && (mc.player.isSwimming() || mc.player.isInWater())) {
                        return test.setAndContinue(swim);
                    }
                    if (test.isMoving()) {
                        if (mc.player != null && mc.player.isSprinting()) {
                            return test.setAndContinue(run);
                        }
                        return test.setAndContinue(walk);
                    }
                    return test.setAndContinue(idle);
                }
        );
        controllers.put(ctrlName, ctrl);
        MOVEMENT_CTRL.put(entity.getUUID(), ctrl);
    }

    private static String triggerOnce(GeoEntity geo, String filePrefix, String animName) {
        String fullId = filePrefix + "@" + animName;
        AnimEntry entry = ANIM_INDEX.get(fullId);
        if (entry == null) {
            String ns = filePrefix.substring(0, filePrefix.indexOf(':'));
            String fp = filePrefix.substring(filePrefix.indexOf(':') + 1);
            entry = new AnimEntry(ns, fp, animName, 0);
        }
        if (!injectController(geo, entry)) return null;
        geo.triggerAnim(entry.controllerName, entry.triggerName);
        return entry.controllerName;
    }

    private static void stopAllInjected(UUID playerId, GeoEntity geo) {
        if (geo == null) {
            INJECTED_CONTROLLERS.remove(playerId);
            return;
        }
        Entity entity = (Entity) geo;
        AnimatableInstanceCache cache = geo.getAnimatableInstanceCache();
        if (cache == null) return;
        AnimatableManager<?> manager = getManager(cache, entity.getId());
        if (manager == null) return;
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<String, AnimationController<?>> controllers = (Map) manager.getAnimationControllers();
        if (controllers == null) return;
        List<String> injected = INJECTED_CONTROLLERS.remove(playerId);
        if (injected == null) return;
        for (String name : injected) {
            AnimationController<?> ctrl = controllers.get(name);
            if (ctrl != null) ctrl.stop();
        }
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (!cacheLoaded) loadAnimCache();
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("animation")
                .then(Commands.literal("play")
                        .then(Commands.argument("animation", StringArgumentType.greedyString())
                                .suggests(ANIM_SUGGESTIONS)
                                .executes(ctx -> playAnim(ctx,
                                        StringArgumentType.getString(ctx, "animation"), null))
                        )
                        .then(Commands.argument("entity", EntityArgument.player())
                                .then(Commands.argument("animation", StringArgumentType.greedyString())
                                        .suggests(ANIM_SUGGESTIONS)
                                        .executes(ctx -> playAnim(ctx,
                                                StringArgumentType.getString(ctx, "animation"),
                                                EntityArgument.getPlayer(ctx, "entity")))
                                )
                        )
                )
                .then(Commands.literal("stop")
                        .executes(ctx -> stopAnim(ctx, null))
                        .then(Commands.argument("entity", EntityArgument.player())
                                .executes(ctx -> stopAnim(ctx, EntityArgument.getPlayer(ctx, "entity")))
                        )
                )
        );
    }

    private static int playAnim(CommandContext<CommandSourceStack> ctx, String fullId, Player target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        Player player = target != null ? target : mc.player;

        AnimEntry entry = ANIM_INDEX.get(fullId);
        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal("§c未知动画: " + fullId));
            return 0;
        }

        Entity shape = PlayerShape.getCurrentShape(player);
        if (shape == null || !(shape instanceof GeoEntity geo)) {
            ctx.getSource().sendFailure(Component.literal("§c该玩家当前没有变形"));
            return 0;
        }

        ResourceLocation typeId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(shape.getType());
        if (typeId == null) return 0;
        String shapeKey = typeId.getNamespace() + ":" + typeId.getPath();
        String filePrefix = entry.namespace + ":" + entry.filePath;

        if (!injectController(geo, entry)) {
            ctx.getSource().sendFailure(Component.literal("§c注入失败"));
            return 0;
        }
        geo.triggerAnim(entry.controllerName, entry.triggerName);

        SHAPE_CACHE.put(player.getUUID(), shapeKey + "|" + filePrefix);
        savePersistentCache();
        MOVEMENT_CTRL.remove(player.getUUID());
        FORM_STATE.put(player.getUUID(), 0);
        PENDING_FORM_CHANGE.remove(player.getUUID());

        ctx.getSource().sendSuccess(() ->
                Component.literal("§a播放: " + entry.animName), false);
        return 1;
    }

    private static int stopAnim(CommandContext<CommandSourceStack> ctx, Player target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        Player player = target != null ? target : mc.player;
        Entity shape = PlayerShape.getCurrentShape(player);
        if (shape instanceof GeoEntity geo) {
            stopAllInjected(player.getUUID(), geo);
        } else {
            stopAllInjected(player.getUUID(), null);
        }
        MOVEMENT_CTRL.remove(player.getUUID());
        FORM_STATE.remove(player.getUUID());
        PENDING_FORM_CHANGE.remove(player.getUUID());
        ctx.getSource().sendSuccess(() ->
                Component.literal("§e已停止动画"), false);
        return 1;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean injectController(GeoEntity geo, AnimEntry entry) {
        Entity entity = (Entity) geo;
        AnimatableInstanceCache cache = geo.getAnimatableInstanceCache();
        if (cache == null) return false;
        AnimatableManager<?> manager = getManager(cache, entity.getId());
        if (manager == null) return false;
        Map<String, AnimationController<?>> controllers = (Map) manager.getAnimationControllers();
        if (controllers == null) return false;
        if (controllers.containsKey(entry.controllerName)) return true;

        AnimationController<GeoEntity> ctrl = new AnimationController<>(
                geo, entry.controllerName, 0, state -> PlayState.STOP
        );
        ctrl.triggerableAnim(entry.triggerName,
                RawAnimation.begin().thenPlay(entry.animName));
        controllers.put(entry.controllerName, ctrl);
        INJECTED_CONTROLLERS.computeIfAbsent(entity.getUUID(), k -> new ArrayList<>())
                .add(entry.controllerName);
        return true;
    }

    private static AnimatableManager<?> getManager(AnimatableInstanceCache cache, int entityId) {
        if (entityId >= 0) {
            try {
                AnimatableManager<?> m = cache.getManagerForId(entityId);
                if (m != null) return m;
            } catch (Exception ignored) {
            }
        }
        try {
            Field f = cache.getClass().getDeclaredField("managers");
            f.setAccessible(true);
            Object obj = f.get(cache);
            if (obj instanceof Map<?, ?> map && !map.isEmpty()) {
                return (AnimatableManager<?>) map.values().iterator().next();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}