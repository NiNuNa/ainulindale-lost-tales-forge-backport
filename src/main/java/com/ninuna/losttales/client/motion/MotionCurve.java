package com.ninuna.losttales.client.motion;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How far along its way a moving value is at each share of its time: the
 * curve a motion travels on. The named curves are the ones a motion file
 * writes by name; {@code cubic-bezier(x1, y1, x2, y2)} is a hand-made one,
 * written as CSS writes it.
 *
 * <p>Some curves overshoot or swing: they pass their end and come back,
 * or bounce off it. Reduced motion travels those on {@link #EASE_OUT}
 * instead.</p>
 */
public final class MotionCurve {
    private enum Shape {
        LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, SINE_IN, SINE_OUT,
        SINE_IN_OUT, BACK_OUT, SETTLE, SPRING, BOUNCE, BEZIER
    }

    /** Steady from start to end. */
    public static final MotionCurve LINEAR = new MotionCurve("linear", Shape.LINEAR);
    /** Slow away, fast arrival. */
    public static final MotionCurve EASE_IN = new MotionCurve("ease_in", Shape.EASE_IN);
    /** Fast away, gentle arrival: what most controls travel on. */
    public static final MotionCurve EASE_OUT = new MotionCurve("ease_out", Shape.EASE_OUT);
    /** Slow in and slow out, symmetric. */
    public static final MotionCurve EASE_IN_OUT = new MotionCurve("ease_in_out", Shape.EASE_IN_OUT);
    /** The gentlest start, a quarter of a sine wave. */
    public static final MotionCurve SINE_IN = new MotionCurve("sine_in", Shape.SINE_IN);
    /** The gentlest arrival, a quarter of a sine wave. */
    public static final MotionCurve SINE_OUT = new MotionCurve("sine_out", Shape.SINE_OUT);
    /** Half a sine wave: soft at both ends. */
    public static final MotionCurve SINE_IN_OUT = new MotionCurve("sine_in_out", Shape.SINE_IN_OUT);
    /** Decelerating, a little past the end and back. */
    public static final MotionCurve BACK_OUT = new MotionCurve("back_out", Shape.BACK_OUT);
    /**
     * Slow in and slow out with a small hump of follow-through over the
     * middle, never past the end: a panel that arrives with a little
     * weight rather than gliding to a stop.
     */
    public static final MotionCurve SETTLE = new MotionCurve("settle", Shape.SETTLE);
    /** A spring let go: past the end, back, and settling in a few swings. */
    public static final MotionCurve SPRING = new MotionCurve("spring", Shape.SPRING);
    /** Dropped onto the end, bouncing to rest on it. */
    public static final MotionCurve BOUNCE = new MotionCurve("bounce", Shape.BOUNCE);

    private static final List<MotionCurve> NAMED = Collections.unmodifiableList(
            Arrays.asList(LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, SINE_IN,
                    SINE_OUT, SINE_IN_OUT, BACK_OUT, SETTLE, SPRING, BOUNCE));
    private static final String BEZIER_PREFIX = "cubic-bezier(";
    /** How far the spring's swing decays over its time. */
    private static final double SPRING_DAMPING = 6.0D;
    /** How many half swings the spring makes over its time. */
    private static final double SPRING_SWINGS = 3.0D;
    private static final double SPRING_END = 1.0D
            - Math.exp(-SPRING_DAMPING) * Math.cos(SPRING_SWINGS * Math.PI);

    private final String name;
    private final Shape shape;
    private final float x1;
    private final float y1;
    private final float x2;
    private final float y2;

    private MotionCurve(String name, Shape shape) {
        this(name, shape, 0.0F, 0.0F, 1.0F, 1.0F);
    }

    private MotionCurve(String name, Shape shape, float x1, float y1, float x2,
                        float y2) {
        this.name = name;
        this.shape = shape;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    /** Every named curve, in the order a picker offers them. */
    public static List<MotionCurve> named() {
        return NAMED;
    }

    /**
     * A hand-made curve through two control points, as CSS's
     * {@code cubic-bezier} draws one. The points' times are kept inside
     * 0 to 1; their values may leave it, which makes the curve overshoot.
     */
    public static MotionCurve bezier(float x1, float y1, float x2, float y2) {
        float a = clamp01(x1);
        float c = clamp01(x2);
        float b = Math.max(-2.0F, Math.min(3.0F, y1));
        float d = Math.max(-2.0F, Math.min(3.0F, y2));
        return new MotionCurve(BEZIER_PREFIX + number(a) + ", " + number(b)
                + ", " + number(c) + ", " + number(d) + ")", Shape.BEZIER,
                a, b, c, d);
    }

    /** The curve a file names, or null for a name that is none. */
    public static MotionCurve parse(String text) {
        if (text == null) {
            return null;
        }
        String name = text.trim().toLowerCase(Locale.ROOT);
        for (MotionCurve curve : NAMED) {
            if (curve.name.equals(name)) {
                return curve;
            }
        }
        if (!name.startsWith(BEZIER_PREFIX) || !name.endsWith(")")) {
            return null;
        }
        String[] parts = name.substring(BEZIER_PREFIX.length(),
                name.length() - 1).split(",");
        if (parts.length != 4) {
            return null;
        }
        float[] values = new float[4];
        try {
            for (int index = 0; index < 4; index++) {
                values[index] = Float.parseFloat(parts[index].trim());
                if (Float.isNaN(values[index])
                        || Float.isInfinite(values[index])) {
                    return null;
                }
            }
        } catch (NumberFormatException malformed) {
            return null;
        }
        return bezier(values[0], values[1], values[2], values[3]);
    }

    /** The name a file writes this curve by. */
    public String name() {
        return this.name;
    }

    /** Whether the curve passes its end, swings or bounces before it rests. */
    public boolean overshoots() {
        switch (this.shape) {
            case BACK_OUT:
            case SPRING:
            case BOUNCE:
                return true;
            case BEZIER:
                return this.y1 < 0.0F || this.y1 > 1.0F || this.y2 < 0.0F
                        || this.y2 > 1.0F;
            default:
                return false;
        }
    }

    /** The curve reduced motion travels on: this one, or ease out in place of a swing. */
    public MotionCurve reduced() {
        return overshoots() ? EASE_OUT : this;
    }

    /** The share of the way covered at {@code progress}, which is kept inside 0 to 1. */
    public float apply(float progress) {
        float t = clamp01(progress);
        switch (this.shape) {
            case LINEAR:
                return t;
            case EASE_IN:
                return t * t * t;
            case EASE_OUT:
                return LostTalesGuiEasing.easeOutCubic(t);
            case EASE_IN_OUT:
                return LostTalesGuiEasing.smoothStep(t);
            case SINE_IN:
                return 1.0F - (float)Math.cos(t * Math.PI / 2.0D);
            case SINE_OUT:
                return (float)Math.sin(t * Math.PI / 2.0D);
            case SINE_IN_OUT:
                return (float)(1.0D - Math.cos(t * Math.PI)) / 2.0F;
            case BACK_OUT:
                return LostTalesGuiEasing.subtleBackOut(t);
            case SETTLE:
                return clamp01(LostTalesGuiEasing.smoothStep(t)
                        + (float)Math.sin(t * Math.PI) * (1.0F - t) * 0.08F);
            case SPRING:
                return (float)((1.0D - Math.exp(-SPRING_DAMPING * t)
                        * Math.cos(SPRING_SWINGS * Math.PI * t)) / SPRING_END);
            case BOUNCE:
                return bounce(t);
            default:
                return bezierAt(t);
        }
    }

    private static float bounce(float t) {
        final float n = 7.5625F;
        final float d = 2.75F;
        if (t < 1.0F / d) {
            return n * t * t;
        }
        if (t < 2.0F / d) {
            float u = t - 1.5F / d;
            return n * u * u + 0.75F;
        }
        if (t < 2.5F / d) {
            float u = t - 2.25F / d;
            return n * u * u + 0.9375F;
        }
        float u = t - 2.625F / d;
        return n * u * u + 0.984375F;
    }

    /** The bezier's value at time {@code x}: its parameter found by bisection. */
    private float bezierAt(float x) {
        float low = 0.0F;
        float high = 1.0F;
        float s = x;
        for (int step = 0; step < 24; step++) {
            float at = coordinate(s, this.x1, this.x2);
            if (Math.abs(at - x) < 1.0E-5F) {
                break;
            }
            if (at < x) {
                low = s;
            } else {
                high = s;
            }
            s = (low + high) / 2.0F;
        }
        return coordinate(s, this.y1, this.y2);
    }

    /** One coordinate of the curve at parameter {@code s}, its ends at 0 and 1. */
    private static float coordinate(float s, float first, float second) {
        float inverse = 1.0F - s;
        return 3.0F * inverse * inverse * s * first
                + 3.0F * inverse * s * s * second + s * s * s;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static String number(float value) {
        String text = String.valueOf(Math.round(value * 1000.0F) / 1000.0F);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MotionCurve
                && ((MotionCurve)other).name.equals(this.name);
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public String toString() {
        return this.name;
    }
}
