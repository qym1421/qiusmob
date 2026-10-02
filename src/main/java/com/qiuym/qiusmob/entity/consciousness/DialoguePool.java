package com.qiuym.qiusmob.consciousness;

import java.util.List;
import java.util.Random;

public class DialoguePool {

    private static final Random RANDOM = new Random();

    public static final List<String> IDLE_WATCH = List.of(
            "dialogue.qiusmob.consciousness.idle_watch",
            "dialogue.qiusmob.consciousness.idle_watch2",
            "dialogue.qiusmob.consciousness.idle_watch3"
    );

    public static String randomIdleWatch() {
        return IDLE_WATCH.get(RANDOM.nextInt(IDLE_WATCH.size()));
    }
}