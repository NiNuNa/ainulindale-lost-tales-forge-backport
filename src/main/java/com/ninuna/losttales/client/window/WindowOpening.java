package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationState;
import com.ninuna.losttales.client.motion.Motions;

/**
 * How the windows come in when their screen opens: every other Lost Tales
 * screen's motion, so they arrive like the rest of the interface. Every
 * window, its row and its content take the same sample and enter as one;
 * only the chat's input bar keeps an entrance of its own.
 */
public final class WindowOpening {
    private static long openedNanos;
    private static final LostTalesGuiAnimationState STATE =
            new LostTalesGuiAnimationState();

    private WindowOpening() {}

    /** Starts the entrance: the screen has just opened. */
    public static synchronized void start() {
        openedNanos = System.nanoTime();
        STATE.restart();
    }

    /** Where the entrance stands now; settled before it ever started. */
    public static synchronized LostTalesGuiAnimationSample sample() {
        if (!Motions.enabled() || openedNanos <= 0L) {
            return LostTalesGuiAnimationSample.SETTLED;
        }
        return STATE.sample(System.nanoTime());
    }

    /** Forgets the entrance with the session. */
    public static synchronized void clear() {
        openedNanos = 0L;
    }
}
