package com.ninuna.losttales.client.gui.animation;

/**
 * One interruptible, reversible transition between two states, shared by
 * the mod's GUI and HUD controls.
 *
 * <p>The value is read from elapsed time rather than accumulated, so it
 * looks the same at every frame rate, and one instance allocates nothing
 * per frame. What separates it from a plain timer is where a leg starts:
 * a transition that is turned around half way begins its return
 * <em>from the value it is showing</em> and takes only the share of the
 * duration that distance is worth. Nothing ever teleports to the far end
 * to start travelling back from it, and a control toggled rapidly stays
 * continuous instead of flicking between poses.</p>
 *
 * <p>{@link LostTalesUiEasing#BACK_OUT} leaves the 0..1 range briefly at
 * the end of a leg; {@link #clamped()} is what a caller that cannot take
 * that — a frame index, an alpha — should read.</p>
 */
public final class LostTalesUiTransition {
    /** Below this the leg has nothing left to travel and simply lands. */
    private static final float SETTLE_EPSILON = 0.001F;

    private float value;
    /** The value the running leg set out from. */
    private float legFrom;
    /** Where the running leg is bound: 0 or 1. */
    private float legTo;
    private long legStartedNanos;
    private long legDurationNanos;
    private boolean started;

    /**
     * Advances to {@code nowNanos} and answers the eased value. The
     * first call settles on {@code on} rather than travelling to it: a
     * control is drawn in the state it is already in the first time it
     * is seen, not animated into it.
     */
    public float advance(long nowNanos, boolean on, int durationMillis,
                         LostTalesUiEasing easing) {
        float target = on ? 1.0F : 0.0F;
        if (!this.started) {
            this.started = true;
            settle(on);
            return this.value;
        }
        if (durationMillis <= 0) {
            settle(on);
            return this.value;
        }
        if (target != this.legTo) {
            // Turned around: the new leg starts from what is on screen,
            // and is worth only the distance that is actually left.
            float distance = Math.abs(target - this.value);
            this.legFrom = this.value;
            this.legTo = target;
            this.legStartedNanos = nowNanos;
            this.legDurationNanos = (long)(Math.max(1, durationMillis)
                    * 1000000L * Math.min(1.0F, distance));
            if (this.legDurationNanos <= 0L
                    || distance <= SETTLE_EPSILON) {
                settle(on);
                return this.value;
            }
        }
        if (this.legDurationNanos <= 0L) {
            this.value = this.legTo;
            return this.value;
        }
        // The end of the leg is decided on the clock rather than on a
        // float progress: a division that lands a hair under one would
        // otherwise leave a settled control reporting itself in motion.
        long elapsed = nowNanos - this.legStartedNanos;
        if (elapsed >= this.legDurationNanos) {
            this.value = this.legTo;
            this.legDurationNanos = 0L;
            return this.value;
        }
        float progress = LostTalesGuiEasing.clamp(
                elapsed / (float)this.legDurationNanos);
        this.value = this.legFrom
                + (this.legTo - this.legFrom) * easing.apply(progress);
        return this.value;
    }

    /** Jumps to the state without travelling; also arms the first sight. */
    public void settle(boolean on) {
        this.started = true;
        this.value = on ? 1.0F : 0.0F;
        this.legFrom = this.value;
        this.legTo = this.value;
        this.legDurationNanos = 0L;
    }

    /** The value as last advanced, overshoot included. */
    public float value() {
        return this.value;
    }

    /** The value bounded to 0..1: what an index or an alpha reads. */
    public float clamped() {
        return LostTalesGuiEasing.clamp(this.value);
    }

    /** Whether the transition has arrived and nothing is travelling. */
    public boolean isSettled() {
        return this.legDurationNanos <= 0L
                && Math.abs(this.legTo - this.value) <= SETTLE_EPSILON;
    }
}
