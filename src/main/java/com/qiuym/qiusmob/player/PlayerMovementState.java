package com.qiuym.qiusmob.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerMovementState {
    private static final Map<UUID, State> cache = new HashMap<>();

    public static void set(UUID uuid, float forward, float left, boolean jumping) {
        cache.put(uuid, new State(forward, left, jumping));
    }

    public static State get(UUID uuid) {
        return cache.getOrDefault(uuid, new State(0, 0, false));
    }

    public static void remove(UUID uuid) {
        cache.remove(uuid);
    }

    public static class State {
        public final float forward;
        public final float left;
        public final boolean jumping;

        public State(float forward, float left, boolean jumping) {
            this.forward = forward;
            this.left = left;
            this.jumping = jumping;
        }
    }
}