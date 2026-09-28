package com.qiuym.qiusmob.client;

public class SightModeClient {
    private static boolean skyModeActive = false;

    public static void setSkyMode(boolean active) {
        skyModeActive = active;
    }

    public static boolean isSkyModeActive() {
        return skyModeActive;
    }
}