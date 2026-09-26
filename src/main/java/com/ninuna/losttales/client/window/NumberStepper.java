package com.ninuna.losttales.client.window;

import java.util.Locale;

/**
 * A number setting's arithmetic: its bounds, its step and the places it
 * is shown to. A step lands on the next multiple of the step past the
 * value, ten of them with Shift, and stops at a bound. A value is typed
 * only in the characters a number of this kind can hold, and is taken
 * only as a number within the bounds.
 */
public final class NumberStepper {
    /** The steps a Shift-click takes. */
    public static final int FAST_STEPS = 10;
    /** The longest number a field for one takes. */
    public static final int MAX_TYPED_LENGTH = 12;
    /** How near a value has to be to a multiple of the step to stand on it. */
    private static final double ON_STEP = 1.0E-6D;

    public final double min;
    public final double max;
    public final double step;
    public final int decimals;

    /**
     * Bounds that do not follow in order are read the other way round; a
     * step that is not a positive number is read as one of the smallest
     * place shown.
     */
    public NumberStepper(double min, double max, double step, int decimals) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        this.decimals = Math.max(0, Math.min(6, decimals));
        this.step = step > 0.0D && !Double.isInfinite(step)
                ? step : Math.pow(10.0D, -this.decimals);
    }

    /** Whether a step that way moves the value at all. */
    public boolean canStep(double value, boolean up) {
        return up ? clamp(value) < this.max : clamp(value) > this.min;
    }

    /**
     * The value one step on, or with {@code fast} ten: up to the next
     * multiple of the step above it, down to the next below, stopping at
     * a bound.
     */
    public double stepped(double value, boolean up, boolean fast) {
        double at = clamp(value);
        int steps = fast ? FAST_STEPS : 1;
        double onGrid = at / this.step;
        double target = up
                ? (Math.floor(onGrid + ON_STEP) + steps) * this.step
                : (Math.ceil(onGrid - ON_STEP) - steps) * this.step;
        return clamp(rounded(target));
    }

    /** The value within the bounds, at the places shown. */
    public double clamp(double value) {
        if (Double.isNaN(value)) {
            return this.min;
        }
        return rounded(Math.max(this.min, Math.min(this.max, value)));
    }

    /** The value at the places shown. */
    public double rounded(double value) {
        double scale = Math.pow(10.0D, this.decimals);
        return Math.round(value * scale) / scale;
    }

    /** The value as its row reads it: always to the places shown. */
    public String format(double value) {
        return String.format(Locale.ROOT, "%." + this.decimals + "f",
                Double.valueOf(rounded(value)));
    }

    /**
     * Whether a field may hold {@code text} on the way to a number: digits,
     * a leading minus only where the bounds go below nought, and a point
     * with no more places after it than are shown only where any are.
     */
    public boolean mayBecome(String text) {
        if (text == null) {
            return false;
        }
        if (text.length() > MAX_TYPED_LENGTH) {
            return false;
        }
        int start = 0;
        if (text.startsWith("-")) {
            if (this.min >= 0.0D) {
                return false;
            }
            start = 1;
        }
        int point = -1;
        for (int index = start; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '.') {
                if (point >= 0 || this.decimals == 0) {
                    return false;
                }
                point = index;
            } else if (character < '0' || character > '9') {
                return false;
            }
        }
        return point < 0 || text.length() - point - 1 <= this.decimals;
    }

    /**
     * What {@code text} comes to as a value this setting takes, or null
     * where it is no number of this kind or lies past a bound.
     */
    public Double parse(String text) {
        String typed = text == null ? "" : text.trim();
        if (!mayBecome(typed)) {
            return null;
        }
        boolean digit = false;
        for (int index = 0; index < typed.length(); index++) {
            char character = typed.charAt(index);
            if (character >= '0' && character <= '9') {
                digit = true;
                break;
            }
        }
        if (!digit) {
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(typed);
        } catch (NumberFormatException unreadable) {
            return null;
        }
        double exact = rounded(value);
        if (exact < rounded(this.min) || exact > rounded(this.max)) {
            return null;
        }
        return Double.valueOf(clamp(exact));
    }
}
