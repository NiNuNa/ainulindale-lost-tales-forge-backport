package com.ninuna.losttales.progression.perk;

/**
 * How a perk's share changes a number LOTR or the game keeps: a price, a
 * sale, a wait, a gain. Every result stays a whole, sensible number.
 */
public final class PerkShares {
    private PerkShares() {}

    /** A price paid, {@code share} lower, never below one coin while it costs anything. */
    public static int lower(int cost, double share) {
        if (cost <= 0 || !(share > 0.0D)) {
            return cost;
        }
        return Math.max(1, (int) Math.round(cost * (1.0D - Math.min(share, 1.0D))));
    }

    /** A value received, {@code share} higher. */
    public static int raise(int value, double share) {
        if (value <= 0 || !(share > 0.0D)) {
            return value;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(value * (1.0D + share)));
    }

    /** A number of ticks to wait, {@code share} shorter, at least one. */
    public static int shorter(int ticks, double share) {
        if (ticks <= 1 || !(share > 0.0D)) {
            return ticks;
        }
        return Math.max(1, (int) Math.round(ticks * (1.0D - Math.min(share, 1.0D))));
    }

    /** A gain, {@code share} larger; a loss stays as it was. */
    public static float gain(float gain, double share) {
        return gain > 0.0F && share > 0.0D ? (float) (gain * (1.0D + share)) : gain;
    }
}
