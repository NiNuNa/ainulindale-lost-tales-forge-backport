package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.config.LostTalesConfig;

/**
 * When a tip shows, as a desktop's tips do. The pointer rests on one
 * thing for the Tip Delay setting's time, then its tip fades in. Once a
 * tip has shown, tips are warm: for a moment after it goes, the next
 * thing the pointer reaches shows its tip at once, so reading along a row
 * of buttons never waits. A press puts the tip away until the pointer
 * moves to something else. The waits are the motion file's
 * ({@link MotionIds#UI_TIP_SHOW}): they are the pointer's, so the
 * Animations settings leave them as they are; the fade is a beat like any
 * other.
 */
final class TipTiming {
    /** What the pointer rests on now: the tip's words and where they come from; empty for nothing. */
    private String key = "";
    private long restingSince;
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
            boolean warm = nowNanos - this.lastShownNanos
                    <= millis("warm", 800.0F);
            this.key = next;
            this.pressed = false;
            this.restingSince = nowNanos;
            // Warm, the next tip stands at once and whole; cold, it waits
            // for the rest and fades in.
            this.fade.settle(warm && next.length() > 0);
            if (warm) {
                this.restingSince = nowNanos - delayNanos();
            }
        }
        if (this.key.length() == 0 || this.pressed
                || nowNanos - this.restingSince < delayNanos()) {
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

    /** How long the pointer rests before a tip shows, by the Tip Delay setting. */
    private static long delayNanos() {
        String delay = LostTalesConfig.tipDelay;
        if ("INSTANT".equals(delay)) {
            return 0L;
        }
        return millis("MEDIUM".equals(delay) ? "medium"
                : "LONG".equals(delay) ? "long" : "short", 350.0F);
    }

    private static long millis(String param, float fallback) {
        return (long)(Motions.param(MotionIds.UI_TIP_SHOW, param, fallback)
                * 1000000.0F);
    }
}
