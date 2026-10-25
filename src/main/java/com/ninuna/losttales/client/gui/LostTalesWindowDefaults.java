package com.ninuna.losttales.client.gui;

/** Startup dimensions only; custom launcher sizes and later resizing remain vanilla. */
public final class LostTalesWindowDefaults {
    public static final int WIDTH = 1024;
    public static final int HEIGHT = 768;

    private LostTalesWindowDefaults() {}

    /** The startup width; runs before Minecraft records its current and restored size. */
    public static int width(int width, int height) {
        return isLegacyDefault(width, height) ? WIDTH : width;
    }

    /** The startup height, worked out from the same pair as {@link #width}. */
    public static int height(int width, int height) {
        return isLegacyDefault(width, height) ? HEIGHT : height;
    }

    private static boolean isLegacyDefault(int width, int height) {
        return width == 854 && height == 480;
    }
}
