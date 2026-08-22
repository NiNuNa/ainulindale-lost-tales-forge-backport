package com.ninuna.losttales.client.chat;

import java.util.Arrays;

/**
 * The screen-space bands the chat renderer actually drew this frame, one per
 * visible line, in GUI coordinates. Every mouse-to-line mapping (hover card,
 * component hover, clicks, clipboard) resolves against these instead of
 * re-deriving the layout, so the hit test can never drift from the picture:
 * scroll, view filtering, chat scale, entry motion and view transitions are
 * all already baked into the recorded rectangles. Backed by arrays that are
 * reused frame to frame; nothing is allocated while recording.
 */
final class ChatLineBands {
    private static final int INITIAL_CAPACITY = 24;

    private int[] viewIndex = new int[INITIAL_CAPACITY];
    private float[] left = new float[INITIAL_CAPACITY];
    private float[] right = new float[INITIAL_CAPACITY];
    private float[] top = new float[INITIAL_CAPACITY];
    private float[] bottom = new float[INITIAL_CAPACITY];
    private int count;
    private float scale = 1.0F;
    private Object source;
    private int sourceSize;

    /** Starts a new frame; {@code source} identifies the line list drawn. */
    void reset(Object source, int sourceSize, float scale) {
        this.count = 0;
        this.source = source;
        this.sourceSize = sourceSize;
        this.scale = scale <= 0.0F ? 1.0F : scale;
    }

    /**
     * Records one drawn band. {@code left} is where the line's own x origin
     * (text start) landed on screen, including any horizontal motion.
     */
    void add(int lineViewIndex, float bandLeft, float bandRight,
             float bandTop, float bandBottom) {
        if (this.count == this.viewIndex.length) {
            int capacity = this.viewIndex.length * 2;
            this.viewIndex = Arrays.copyOf(this.viewIndex, capacity);
            this.left = Arrays.copyOf(this.left, capacity);
            this.right = Arrays.copyOf(this.right, capacity);
            this.top = Arrays.copyOf(this.top, capacity);
            this.bottom = Arrays.copyOf(this.bottom, capacity);
        }
        this.viewIndex[this.count] = lineViewIndex;
        this.left[this.count] = bandLeft;
        this.right[this.count] = bandRight;
        this.top[this.count] = Math.min(bandTop, bandBottom);
        this.bottom[this.count] = Math.max(bandTop, bandBottom);
        this.count++;
    }

    /** True when the recorded bands describe the given line list. */
    boolean describes(Object lines, int size) {
        return this.source == lines && this.sourceSize == size;
    }

    /** Band index under the point, or -1. Bands never overlap. */
    int find(float x, float y) {
        for (int index = 0; index < this.count; index++) {
            if (y >= this.top[index] && y < this.bottom[index]
                    && x >= this.left[index] && x < this.right[index]) {
                return index;
            }
        }
        return -1;
    }

    int count() {
        return this.count;
    }

    int viewIndexOf(int band) {
        return this.viewIndex[band];
    }

    float leftOf(int band) {
        return this.left[band];
    }

    float rightOf(int band) {
        return this.right[band];
    }

    float topOf(int band) {
        return this.top[band];
    }

    float bottomOf(int band) {
        return this.bottom[band];
    }

    float scale() {
        return this.scale;
    }

    /** Converts a screen x into the line's own (unscaled) text space. */
    float localX(int band, float screenX) {
        return (screenX - this.left[band]) / this.scale;
    }
}
