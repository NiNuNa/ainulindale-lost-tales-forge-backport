package com.ninuna.losttales.client.gui;

/** Startup dimensions only; custom launcher sizes and later resizing remain vanilla. */
public final class LostTalesWindowDefaults {
    public static final int WIDTH = 1024;
    public static final int HEIGHT = 768;

    private LostTalesWindowDefaults() {}

    /** Runs before Minecraft records both its current and restored window size. */
    public static int[] resolve(int width, int height) {
        if (width == 854 && height == 480) {
            return new int[] {WIDTH, HEIGHT};
        }
        return new int[] {width, height};
    }
}
