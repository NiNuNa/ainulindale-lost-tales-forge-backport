package com.ninuna.losttales.client.window;

/**
 * Stretches of colour along a window's top and bottom edges, left to
 * right: each from the screen x it starts at, the first from anywhere
 * left, with its colour, its strength and, for one that thins out along
 * its width, its profile. The frame's ring over and under a window its
 * page fills continues them.
 */
public final class EdgeStretches {
    private static final int MAX = 4;

    private final float[] starts = new float[MAX];
    private final int[] argbs = new int[MAX];
    private final float[] curveLefts = new float[MAX];
    private final float[] curveRights = new float[MAX];
    private final float[][] weights = new float[MAX][];
    private int count;

    /** Forgets every stretch. */
    public void clear() {
        this.count = 0;
    }

    /** Starts over with one flat stretch in {@code argb}, reaching from anywhere left. */
    public void first(int argb) {
        first(argb, 0.0F, 0.0F, null);
    }

    /**
     * Starts over with one stretch reaching from anywhere left, thinning
     * out as {@code weights} say from screen x {@code curveLeft} to
     * {@code curveRight}; null weights for a flat one.
     */
    public void first(int argb, float curveLeft, float curveRight,
                      float[] weights) {
        this.count = 0;
        from(Float.NEGATIVE_INFINITY, argb, curveLeft, curveRight, weights);
    }

    /** A flat stretch in {@code argb} from screen x {@code fromX} rightwards. */
    public void from(float fromX, int argb) {
        from(fromX, argb, 0.0F, 0.0F, null);
    }

    /** As above, thinning out as {@code weights} say from {@code curveLeft} to {@code curveRight}. */
    public void from(float fromX, int argb, float curveLeft, float curveRight,
                     float[] weights) {
        if (this.count >= MAX) {
            return;
        }
        int index = this.count++;
        this.starts[index] = fromX;
        this.argbs[index] = argb;
        this.curveLefts[index] = curveLeft;
        this.curveRights[index] = curveRight;
        this.weights[index] = weights;
    }

    boolean isEmpty() {
        return this.count == 0;
    }

    /**
     * Fills {@code [left, right)} by {@code [top, bottom)} with the
     * stretches as they stand there, the first reaching past the left
     * end and the last past the right.
     */
    void fill(float left, float right, float top, float bottom) {
        for (int index = 0; index < this.count; index++) {
            float from = index == 0 ? left
                    : Math.max(left, this.starts[index]);
            float to = index == this.count - 1 ? right
                    : Math.min(right, this.starts[index + 1]);
            if (to > from) {
                WindowStyle.fillProfiled(from, top, to, bottom,
                        this.argbs[index], this.curveLefts[index],
                        this.curveRights[index], this.weights[index]);
            }
        }
    }
}
