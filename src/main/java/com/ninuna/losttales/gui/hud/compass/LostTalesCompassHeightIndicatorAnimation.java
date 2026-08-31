package com.ninuna.losttales.gui.hud.compass;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;

/**
 * Frame-rate-independent motion for the compass height chevrons.
 *
 * <p>The movement stays below one pixel once settled so it reads as life and
 * direction, not HUD jitter. A newly visible arrow anticipates opposite its
 * destination, eases through a damped overshoot, and settles into a slow
 * directional breath. A brighter emphasis pulse then travels through a stack
 * in the represented direction, with enough rest between passes to remain a
 * cue rather than visual noise. The second escalation arrow follows a moment
 * later and collapses softly when the marker returns to the first height
 * tier.</p>
 */
final class LostTalesCompassHeightIndicatorAnimation {
    static final long ENTER_NANOS = 280_000_000L;
    static final long SECONDARY_DELAY_NANOS = 55_000_000L;
    static final long EXIT_NANOS = 170_000_000L;
    static final long IDLE_PERIOD_NANOS = 1_500_000_000L;
    static final long FLOW_PERIOD_NANOS = 1_250_000_000L;
    static final long STALE_AFTER_NANOS = 350_000_000L;

    private static final double TWO_PI = Math.PI * 2.0D;

    private String markerKey;
    private int screenDirection;
    private int tier;
    private int previousTier;
    private long markerStartedNanos;
    private long tierChangedNanos;
    private long lastSeenNanos;

    Frame frame(String markerKeyIn, int screenDirectionIn,
                int tierIn, long nowNanos) {
        int direction = screenDirectionIn < 0 ? -1 : 1;
        int requestedTier = tierIn >= 2 ? 2 : 1;
        String key = markerKeyIn == null ? "" : markerKeyIn;

        if (this.lastSeenNanos != 0L
                && elapsed(this.lastSeenNanos, nowNanos)
                        > STALE_AFTER_NANOS) {
            reset();
        }
        this.lastSeenNanos = nowNanos;

        boolean markerChanged = this.markerKey == null
                || !this.markerKey.equals(key)
                || this.screenDirection != direction;
        if (markerChanged) {
            this.markerKey = key;
            this.screenDirection = direction;
            this.tier = requestedTier;
            this.previousTier = 0;
            this.markerStartedNanos = nowNanos;
            this.tierChangedNanos = nowNanos;
        } else if (this.tier != requestedTier) {
            this.previousTier = this.tier;
            this.tier = requestedTier;
            this.tierChangedNanos = nowNanos;
        }

        long markerAge = elapsed(this.markerStartedNanos, nowNanos);
        long tierAge = elapsed(this.tierChangedNanos, nowNanos);
        boolean secondaryExiting = this.tier == 1
                && this.previousTier == 2 && tierAge < EXIT_NANOS;
        boolean showSecondary = this.tier >= 2 || secondaryExiting;
        int arrowCount = showSecondary ? 2 : 1;
        Pose primary = sample(markerAge, markerAge,
                direction, 0, arrowCount, 0.0F);
        if (!showSecondary) {
            return new Frame(primary, null);
        }

        long secondaryEntryAge;
        float exitProgress = 0.0F;
        if (secondaryExiting) {
            secondaryEntryAge = ENTER_NANOS;
            exitProgress = (float)tierAge / (float)EXIT_NANOS;
        } else {
            long secondaryStarted = this.previousTier == 1
                    ? this.tierChangedNanos : this.markerStartedNanos;
            secondaryEntryAge = delayedElapsed(
                    secondaryStarted, nowNanos, SECONDARY_DELAY_NANOS);
        }
        Pose secondary = sample(secondaryEntryAge, markerAge,
                direction, 1, arrowCount, exitProgress);
        return new Frame(primary, secondary);
    }

    void reset() {
        this.markerKey = null;
        this.screenDirection = 0;
        this.tier = 0;
        this.previousTier = 0;
        this.markerStartedNanos = 0L;
        this.tierChangedNanos = 0L;
        this.lastSeenNanos = 0L;
    }

    private static Pose sample(long entranceAgeNanos, long idleAgeNanos,
                               int direction, int arrowIndex, int arrowCount,
                               float exitProgressIn) {
        float entranceProgress = clamp01((float)entranceAgeNanos
                / (float)ENTER_NANOS);
        float entranceAlpha = smoothStep(entranceProgress);

        // A damped response supplies both anticipation and one tiny overshoot.
        float response = 1.0F
                - (float)Math.exp(-6.5F * entranceProgress)
                * (float)Math.cos(8.0F * entranceProgress);
        float idleBlend = smoothStep((entranceProgress - 0.42F) / 0.58F);
        double idleShare = (double)(idleAgeNanos % IDLE_PERIOD_NANOS)
                / (double)IDLE_PERIOD_NANOS;
        double phase = idleShare * TWO_PI
                - direction * arrowIndex * 0.46D;
        float wave = (float)Math.sin(phase);
        float flowEmphasis = flowEmphasis(idleAgeNanos, direction,
                arrowIndex, arrowCount) * idleBlend;

        float entranceOffset = -direction * (1.0F - response) * 1.05F;
        float idleOffset = direction * 0.38F * wave * idleBlend;
        float flowOffset = direction * 0.16F * flowEmphasis;
        float exitProgress = smoothStep(exitProgressIn);
        float exitOffset = arrowIndex == 0 ? 0.0F
                : -0.55F * exitProgress;

        float scaleX = 1.12F - 0.12F * response
                - 0.012F * wave * idleBlend
                - 0.020F * flowEmphasis
                + 0.04F * exitProgress;
        float scaleY = 0.76F + 0.24F * response
                + 0.022F * wave * idleBlend
                + 0.055F * flowEmphasis
                - 0.08F * exitProgress;
        float idleAlpha = 0.975F
                + 0.025F * (0.5F + 0.5F * wave);
        float alpha = clamp01(entranceAlpha * idleAlpha
                * (1.0F - exitProgress));
        float brightness = 0.82F + 0.18F * flowEmphasis;

        return new Pose(entranceOffset + idleOffset + flowOffset
                        + exitOffset,
                scaleX, scaleY, brightness, alpha, flowEmphasis);
    }

    /**
     * A short bell-shaped highlight with an intentionally long quiet tail.
     * Downward stacks lead at index zero; upward stacks reverse that order so
     * the perceived energy always travels toward the represented height.
     */
    private static float flowEmphasis(long ageNanos, int direction,
                                      int arrowIndex, int arrowCount) {
        float cycle = (float)(ageNanos % FLOW_PERIOD_NANOS)
                / (float)FLOW_PERIOD_NANOS;
        int sequenceIndex = arrowCount <= 1 ? 0
                : direction > 0 ? arrowIndex
                : arrowCount - 1 - arrowIndex;
        float center = 0.14F + sequenceIndex * 0.22F;
        float distance = Math.abs(cycle - center);
        distance = Math.min(distance, 1.0F - distance);
        return smoothStep(1.0F - distance / 0.15F);
    }

    private static long delayedElapsed(long earlier, long later, long delay) {
        long value = elapsed(earlier, later);
        return value <= delay ? 0L : value - delay;
    }

    private static long elapsed(long earlier, long later) {
        return later <= earlier ? 0L : later - earlier;
    }

    private static float smoothStep(float value) {
        return LostTalesGuiEasing.smoothStep(value);
    }

    private static float clamp01(float value) {
        return LostTalesGuiEasing.clamp(value);
    }

    static final class Frame {
        private final Pose primary;
        private final Pose secondary;

        private Frame(Pose primary, Pose secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        int getArrowCount() {
            return this.secondary == null ? 1 : 2;
        }

        Pose getPose(int index) {
            if (index == 0) {
                return this.primary;
            }
            if (index == 1 && this.secondary != null) {
                return this.secondary;
            }
            throw new IndexOutOfBoundsException("height arrow " + index);
        }
    }

    static final class Pose {
        private final float offsetY;
        private final float scaleX;
        private final float scaleY;
        private final float brightness;
        private final float alpha;
        private final float flowEmphasis;

        private Pose(float offsetY, float scaleX, float scaleY,
                     float brightness, float alpha, float flowEmphasis) {
            this.offsetY = offsetY;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.brightness = brightness;
            this.alpha = alpha;
            this.flowEmphasis = flowEmphasis;
        }

        float getOffsetY() {
            return this.offsetY;
        }

        float getScaleX() {
            return this.scaleX;
        }

        float getScaleY() {
            return this.scaleY;
        }

        float getBrightness() {
            return this.brightness;
        }

        float getAlpha() {
            return this.alpha;
        }

        float getFlowEmphasis() {
            return this.flowEmphasis;
        }
    }
}
