package com.qiuym.qiusmob.client;

public class ClientState {
    private static boolean skirkBound = false;

    public static void setSkirkBound(boolean bound) {
        skirkBound = bound;
    }

    public static boolean isSkirkBound() {
        return skirkBound;
    }
}