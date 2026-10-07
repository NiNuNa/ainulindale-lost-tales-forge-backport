package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.client.motion.Motions;

/**
 * When a tip shows: the moment the pointer reaches something, fading in
 * on the motion file's beat ({@link MotionIds#UI_TIP_SHOW}), as the
 * button under it lights. Once a tip has shown, tips are warm: for a
 * moment after it goes, the next one stands whole at once, so reading
 * along a row of buttons never waits for a fade. A press puts the tip
 * away until the pointer moves to something else.
 */
final class TipTiming {
    /** What the pointer rests on now: the tip's words and where they come from; empty for nothing. */
    private String key = "";
    /** When a tip last showed, for the warm moment after it. */
    private long lastShownNanos = Long.MIN_VALUE / 2;
    private boolean pressed;
    private final MotionTransition fade =
            new MotionTransition(MotionIds.UI_TIP_SHOW);

    /**
     * How strongly the tip for {@code key} shows at {@code nowNanos}, 0 to
     * 1; an empty key is no tip.
     */
    float share(String key, long nowNanos) {
        String next = key == null ? "" : key;
        if (!next.equals(this.key)) {
            boolean warm = nowNanos - this.lastShownNanos <= warmNanos();
            this.key = next;
            this.pressed = false;
            // Warm, the next tip stands whole at once; cold, it fades in.
            this.fade.settle(warm && next.length() > 0);
        }
        if (this.key.length() == 0 || this.pressed) {
            return 0.0F;
        }
        this.lastShownNanos = nowNanos;
        this.fade.advance(nowNanos, true);
        return this.fade.clamped();
    }

    /** A press: the tip goes until the pointer reaches something else. */
    void pressed() {
        this.pressed = true;
    }

    /** How long after a tip went the next one still stands at once. */
    private static long warmNanos() {
        return (long)(Motions.param(MotionIds.UI_TIP_SHOW, "warm", 800.0F)
                * 1000000.0F);
    }
}
