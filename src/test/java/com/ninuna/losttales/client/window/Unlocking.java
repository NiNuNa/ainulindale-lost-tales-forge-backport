package com.ninuna.losttales.client.window;

/**
 * Unlocks windows for tests that move or close their tabs as a hand
 * does. A new player's windows open locked, and a locked window keeps
 * its place, its size and its tabs. Nothing is written: the layout's
 * listener is not told.
 */
public final class Unlocking {
    private Unlocking() {}

    /** The window, unlocked. */
    public static Window of(Window window) {
        window.setLocked(false);
        return window;
    }

    /** Every window the layout holds now, unlocked. */
    public static void all() {
        for (Window window : WindowLayout.windows()) {
            window.setLocked(false);
        }
    }
}
