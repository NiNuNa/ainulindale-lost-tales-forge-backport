package com.ninuna.losttales.core;

import java.util.List;

/**
 * Hooks for {@link HookGuardTest}: each answers its value, or fails the
 * way {@link #failure} says while it is set.
 */
public final class GuardTestHooks {

    /** What every hook throws while set; null for a hook that works. */
    public static volatile Throwable failure;

    private GuardTestHooks() {}

    private static void failIfAsked() {
        Throwable fault = failure;
        if (fault instanceof RuntimeException) {
            throw (RuntimeException)fault;
        }
        if (fault instanceof Error) {
            throw (Error)fault;
        }
    }

    public static void nothing() {
        failIfAsked();
    }

    public static boolean answer() {
        failIfAsked();
        return true;
    }

    public static Object object() {
        failIfAsked();
        return "the hook's";
    }

    public static float scale(float value) {
        failIfAsked();
        return value * 2;
    }

    public static double wide(int number, double value, long count) {
        failIfAsked();
        return -1;
    }

    public static int limit() {
        failIfAsked();
        return 7;
    }

    public static String name() {
        failIfAsked();
        return "named";
    }

    public static int length(CharSequence text) {
        failIfAsked();
        return -1;
    }

    public static int size(List<?> list) {
        failIfAsked();
        return -1;
    }

    public static int larger(int left, int right, boolean extra) {
        failIfAsked();
        return -1;
    }

    public static void add(List<Object> list, Object element) {
        failIfAsked();
    }

    public static int across(java.awt.Point point) {
        failIfAsked();
        return -1;
    }

    public static Object build(String text) {
        failIfAsked();
        return null;
    }
}
