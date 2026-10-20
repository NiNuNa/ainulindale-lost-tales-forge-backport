package com.ninuna.losttales.progression;

/**
 * Repeats count less (PROG-08): the same act on the same thing counts in
 * full {@code free} times, then each repeat counts {@code shrinkPercent}
 * less than the one before, never below {@code floorPercent}. One repeat
 * is forgotten every {@code recoverySeconds}, so the worth comes back over
 * time. All four are server settings.
 */
public final class RepeatRule {
    private final int free;
    private final double keep;
    private final double floor;
    private final double recoveryMillis;

    public RepeatRule(int free, double shrinkPercent, double floorPercent,
                      int recoverySeconds) {
        this.free = Math.max(0, free);
        this.keep = 1.0D - Math.max(0.0D, Math.min(100.0D, shrinkPercent)) / 100.0D;
        this.floor = Math.max(0.0D, Math.min(100.0D, floorPercent)) / 100.0D;
        this.recoveryMillis = Math.max(1, recoverySeconds) * 1000.0D;
    }

    /** The repeats still remembered {@code elapsedMillis} after {@code count} were. */
    public double recovered(double count, long elapsedMillis) {
        return Math.max(0.0D, count - Math.max(0L, elapsedMillis) / this.recoveryMillis);
    }

    /** The share an act counts for with {@code repeats} remembered before it. */
    public double share(double repeats) {
        if (repeats < this.free) {
            return 1.0D;
        }
        double past = Math.floor(repeats) - this.free + 1.0D;
        return Math.max(this.floor, Math.pow(this.keep, past));
    }
}
