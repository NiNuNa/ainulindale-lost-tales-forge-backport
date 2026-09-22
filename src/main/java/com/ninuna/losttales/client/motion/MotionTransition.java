package com.ninuna.losttales.client.motion;

/**
 * One value going from off to on and back, interruptibly: a panel opening,
 * a control fading in, a strip sliding out. Its motion's {@link Motion#ON}
 * and {@link Motion#OFF} beats say how long each way takes and on which
 * curve.
 *
 * <p>The value is read from elapsed time rather than accumulated, so it
 * looks the same at every frame rate, and one instance allocates nothing
 * per frame. A transition turned around half way begins its return from
 * the value it is showing and takes only the share of the time that
 * distance is worth, so nothing teleports to the far end to start back
 * from it, and a control toggled rapidly stays continuous.</p>
 *
 * <p>A transition that moves something through space says so
 * ({@link #MotionTransition(String, boolean)}); reduced motion sets it
 * down at its end at once, while one that fades keeps its time on a
 * curve that does not swing. A curve that swings leaves the 0 to 1 range
 * briefly; {@link #clamped()} is what an index or an alpha reads.</p>
 */
public final class MotionTransition {
    /** Below this a leg has nothing left to travel and simply lands. */
    private static final float SETTLE_EPSILON = 0.001F;

    private final String motionId;
    private final boolean travel;
    private float value;
    /** The value the running leg set out from. */
    private float legFrom;
    /** Where the running leg is bound: 0 or 1. */
    private float legTo;
    private long legStartedNanos;
    private long legDurationNanos;
    private MotionCurve legCurve = MotionCurve.LINEAR;
    private boolean started;

    /** A transition that fades, reveals or otherwise changes in place. */
    public MotionTransition(String motionId) {
        this(motionId, false);
    }

    /** A transition, {@code travel} when it moves something through space. */
    public MotionTransition(String motionId, boolean travel) {
        this.motionId = motionId;
        this.travel = travel;
    }

    /**
     * Advances to {@code nowNanos} toward on or off and answers the
     * value. The first call settles on {@code on} rather than travelling
     * to it: a thing is drawn in the state it is already in the first
     * time it is seen, not animated into it.
     */
    public float advance(long nowNanos, boolean on) {
        float target = on ? 1.0F : 0.0F;
        if (!this.started) {
            settle(on);
            return this.value;
        }
        if (target != this.legTo) {
            MotionBeat beat = Motions.get(this.motionId)
                    .beat(on ? Motion.ON : Motion.OFF);
            long nanos = beat == null || this.travel && Motions.reduced()
                    ? 0L : Motions.beatNanos(beat.durationMillis());
            // Turned around: the new leg starts from what is on screen,
            // and is worth only the distance that is actually left.
            float distance = Math.abs(target - this.value);
            this.legFrom = this.value;
            this.legTo = target;
            this.legStartedNanos = nowNanos;
            this.legDurationNanos = (long)(nanos * Math.min(1.0F, distance));
            MotionCurve curve = beat == null ? MotionCurve.LINEAR : beat.curve();
            this.legCurve = Motions.reduced() ? curve.reduced() : curve;
            if (this.legDurationNanos <= 0L || distance <= SETTLE_EPSILON) {
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
        // otherwise leave a settled transition reporting itself in motion.
        long elapsed = nowNanos - this.legStartedNanos;
        if (elapsed >= this.legDurationNanos) {
            this.value = this.legTo;
            this.legDurationNanos = 0L;
            return this.value;
        }
        float progress = elapsed / (float)this.legDurationNanos;
        this.value = this.legFrom
                + (this.legTo - this.legFrom) * this.legCurve.apply(progress);
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

    /** The value bounded to 0 to 1: what an index or an alpha reads. */
    public float clamped() {
        return Math.max(0.0F, Math.min(1.0F, this.value));
    }

    /** Whether the transition has arrived and nothing is travelling. */
    public boolean isSettled() {
        return this.legDurationNanos <= 0L
                && Math.abs(this.legTo - this.value) <= SETTLE_EPSILON;
    }
}
