package com.ninuna.losttales.client.motion;

/**
 * One strand of a track's change over a beat, laid over a window of the
 * beat: from {@link #begin()} to {@link #end()}, each a share of the
 * beat's time.
 *
 * <ul>
 *   <li><b>travel</b>: from where the part stood to where the beat takes
 *   it, on a curve. A track travels once.</li>
 *   <li><b>bump</b>: out by a size and back, half a sine wave: the dip of
 *   an anticipation, or an overshoot past the mark.</li>
 *   <li><b>ring</b>: a spring settling in alternating lobes that shrink,
 *   the first of the size given: the follow-through after a press.</li>
 *   <li><b>keys</b>: a shape drawn by hand through keys, each at a share
 *   of the window with the offset it reaches and the curve that reaches
 *   it, from nothing at the window's start and back to nothing at its
 *   end.</li>
 * </ul>
 *
 * <p>Bumps, rings and keys are added to the travel. They are whole on a
 * beat that travels all the way from rest to its pose, and as much
 * smaller as a beat begun partway has less of that way left.</p>
 */
public final class MotionLayer {
    /** What a layer does. */
    public enum Kind { TRAVEL, BUMP, RING, KEYS }

    /** The most lobes a ring may swing through. */
    public static final int MAX_LOBES = 8;
    /** The most keys one layer may hold. */
    public static final int MAX_KEYS = 32;
    /** The latest share a key may stand at: the window ends back on nothing. */
    private static final float LAST_KEY = 0.999F;

    private final Kind kind;
    private final float begin;
    private final float end;
    private final MotionCurve curve;
    private final float size;
    private final int lobes;
    private final float[] keyAt;
    private final float[] keyValue;
    private final MotionCurve[] keyCurve;

    private MotionLayer(Kind kind, float begin, float end, MotionCurve curve,
                        float size, int lobes, float[] keyAt,
                        float[] keyValue, MotionCurve[] keyCurve) {
        this.kind = kind;
        float first = clamp01(begin);
        this.begin = first;
        this.end = Math.max(first, clamp01(end));
        this.curve = curve == null ? MotionCurve.EASE_OUT : curve;
        this.size = size;
        this.lobes = Math.max(1, Math.min(MAX_LOBES, lobes));
        this.keyAt = keyAt;
        this.keyValue = keyValue;
        this.keyCurve = keyCurve;
    }

    /** From the part's place to the beat's, on {@code curve}, over the window. */
    public static MotionLayer travel(MotionCurve curve, float begin, float end) {
        return new MotionLayer(Kind.TRAVEL, begin, end, curve, 0.0F, 1, null,
                null, null);
    }

    /** Out by {@code size} and back over the window. */
    public static MotionLayer bump(float size, float begin, float end) {
        return new MotionLayer(Kind.BUMP, begin, end, null, size, 1, null,
                null, null);
    }

    /** A spring's lobes over the window, the first of {@code size}. */
    public static MotionLayer ring(float size, int lobes, float begin,
                                   float end) {
        return new MotionLayer(Kind.RING, begin, end, null, size, lobes, null,
                null, null);
    }

    /**
     * A shape through keys: {@code at} shares of the window, rising, with
     * the offsets reached and the curves reaching them. The way back to
     * nothing after the last key travels on {@code closing}.
     */
    public static MotionLayer keys(float[] at, float[] values,
                                   MotionCurve[] curves, MotionCurve closing,
                                   float begin, float end) {
        int count = Math.min(MAX_KEYS, Math.min(at.length,
                Math.min(values.length, curves.length)));
        float[] keptAt = new float[count];
        float[] keptValue = new float[count];
        MotionCurve[] keptCurve = new MotionCurve[count];
        float last = 0.0F;
        for (int index = 0; index < count; index++) {
            last = Math.max(last, Math.min(LAST_KEY, clamp01(at[index])));
            keptAt[index] = last;
            keptValue[index] = values[index];
            keptCurve[index] = curves[index] == null ? MotionCurve.EASE_IN_OUT
                    : curves[index];
        }
        return new MotionLayer(Kind.KEYS, begin, end, closing, 0.0F, 1, keptAt,
                keptValue, keptCurve);
    }

    public Kind kind() {
        return this.kind;
    }

    public float begin() {
        return this.begin;
    }

    public float end() {
        return this.end;
    }

    /** The travel's curve, and the curve a keys layer closes on. */
    public MotionCurve curve() {
        return this.curve;
    }

    /** A bump's or a ring's size. */
    public float size() {
        return this.size;
    }

    public int lobes() {
        return this.lobes;
    }

    /** The number of keys a keys layer holds. */
    public int keyCount() {
        return this.keyAt == null ? 0 : this.keyAt.length;
    }

    public float keyAt(int index) {
        return this.keyAt[index];
    }

    public float keyValue(int index) {
        return this.keyValue[index];
    }

    public MotionCurve keyCurve(int index) {
        return this.keyCurve[index];
    }

    /** The same layer on another curve: what reduced motion travels. */
    MotionLayer withCurve(MotionCurve replaced) {
        return new MotionLayer(this.kind, this.begin, this.end, replaced,
                this.size, this.lobes, this.keyAt, this.keyValue,
                this.keyCurve);
    }

    /** How far through its window the layer is at {@code progress} of the beat. */
    float windowShare(float progress) {
        if (this.end <= this.begin) {
            return progress >= this.end ? 1.0F : 0.0F;
        }
        return clamp01((progress - this.begin) / (this.end - this.begin));
    }

    /** The share of its way a travel has covered, which may pass one. */
    float travelShare(float progress) {
        return this.curve.apply(windowShare(progress));
    }

    /** What a bump, ring or keys layer adds at {@code progress}, before scaling. */
    float offset(float progress) {
        if (progress <= this.begin || progress >= this.end) {
            return 0.0F;
        }
        float share = windowShare(progress);
        switch (this.kind) {
            case BUMP:
                return this.size * (float)Math.sin(Math.PI * share);
            case RING:
                return this.size * ringOut(share, this.lobes);
            case KEYS:
                return keysAt(share);
            default:
                return 0.0F;
        }
    }

    private float keysAt(float share) {
        float fromAt = 0.0F;
        float fromValue = 0.0F;
        for (int index = 0; index < this.keyAt.length; index++) {
            float at = this.keyAt[index];
            if (share <= at) {
                float span = at - fromAt;
                float within = span <= 0.0F ? 1.0F : (share - fromAt) / span;
                return fromValue + (this.keyValue[index] - fromValue)
                        * this.keyCurve[index].apply(within);
            }
            fromAt = at;
            fromValue = this.keyValue[index];
        }
        float within = (share - fromAt) / (1.0F - fromAt);
        return fromValue * (1.0F - this.curve.apply(within));
    }

    /**
     * A spring settling through {@code lobes} triangle lobes, each leaning
     * the other way from the one before and reaching less far. Zero at
     * both ends and one at the first lobe's peak.
     */
    static float ringOut(float progress, int lobes) {
        if (lobes <= 0 || progress <= 0.0F || progress >= 1.0F) {
            return 0.0F;
        }
        float scaled = progress * lobes;
        int lobe = (int)scaled;
        if (lobe >= lobes) {
            return 0.0F;
        }
        float within = scaled - lobe;
        float triangle = within > 0.5F ? (1.0F - within) * 2.0F : within * 2.0F;
        float decay = (lobes - lobe) / (float)lobes;
        return lobe % 2 == 0 ? triangle * decay : -triangle * decay;
    }

    private static float clamp01(float value) {
        return Float.isNaN(value) ? 0.0F : Math.max(0.0F, Math.min(1.0F, value));
    }
}
