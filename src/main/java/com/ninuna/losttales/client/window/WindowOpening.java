package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationState;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import com.ninuna.losttales.client.motion.Motion;
import com.ninuna.losttales.client.motion.MotionBeat;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;

/**
 * How the windows come in when their screen opens: every other Lost Tales
 * screen's motion, so they arrive like the rest of the interface. Every
 * window enters as one piece, its row, its content and its input bar
 * alike. The motion files can give the input bars an entrance of their
 * own ({@link MotionIds#WINDOW_BAR_APPEAR}, no time by default): then the
 * bars stay out of the window's motion and come up from below by
 * themselves.
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

    /**
     * Whether the input bars have an entrance of their own: their motion
     * was given a time. Read from the motion itself, so the bars keep to
     * one way of coming in whatever the speed or reduced motion say.
     */
    public static boolean barsEnterOnTheirOwn() {
        MotionBeat beat = Motions.get(MotionIds.WINDOW_BAR_APPEAR)
                .beat(Motion.ON);
        return beat != null && beat.durationMillis() > 0;
    }

    /**
     * How far below its place every window's input bar stands now, while
     * the bars have an entrance of their own: up from below with a brief
     * overshoot. Nothing while they ride their window.
     */
    public static synchronized float barOffset() {
        long duration = Motions.travelNanos(MotionIds.WINDOW_BAR_APPEAR);
        if (duration <= 0L || openedNanos <= 0L) {
            return 0.0F;
        }
        return barOffsetAt((System.nanoTime() - openedNanos)
                / (float)duration);
    }

    /** Where the bars stand {@code progress} of the way through their own entrance. */
    static float barOffsetAt(float progress) {
        String id = MotionIds.WINDOW_BAR_APPEAR;
        float distance = Motions.param(id, "distance", 13.0F);
        float swing = Motions.param(id, "swing", 1.25F);
        float swings = Motions.param(id, "swings", 2.5F);
        float p = LostTalesGuiEasing.clamp(progress);
        float settled = LostTalesGuiEasing.smoothStep(p);
        float followThrough = (float)Math.sin(p * Math.PI * swings)
                * (1.0F - p) * (1.0F - p);
        return distance * (1.0F - settled) + swing * followThrough;
    }

    /** Forgets the entrance with the session. */
    public static synchronized void clear() {
        openedNanos = 0L;
    }
}
