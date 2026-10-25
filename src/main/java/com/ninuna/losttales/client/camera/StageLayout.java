package com.ninuna.losttales.client.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Where the character stands on a stage view's screen (WIN-50): in the
 * widest stretch across the screen that no window covers, the full height
 * of the screen less a margin, its feet near the foot of that stretch and
 * the nameplate along it.
 *
 * <p>Pure geometry from the screen's size and the stretches its windows
 * cover, so a test can ask it every question the stage does.</p>
 */
public final class StageLayout {
    /** Clear of the screen's edges and of a window's side. */
    public static final int MARGIN = 8;
    /** The narrowest stretch worth standing the character in. */
    public static final int MIN_WIDTH = 48;
    /** How much of the stage's height the character is fitted into. */
    private static final float FIGURE_FIT = 0.78F;
    /** Where the feet stand, as a share of the stage's height from its top. */
    private static final float BASELINE_FRACTION = 0.88F;

    private final int left;
    private final int top;
    private final int width;
    private final int height;

    private StageLayout(int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    /**
     * The stage on a screen {@code screenWidth} by {@code screenHeight},
     * beside windows covering each of {@code covered}, a stretch as its
     * left and right edge. With no stretch wide enough, the stage is the
     * whole screen, the character standing in its middle behind the
     * windows.
     */
    public static StageLayout of(int screenWidth, int screenHeight,
                                 List<int[]> covered) {
        int screenW = Math.max(1, screenWidth);
        int screenH = Math.max(1, screenHeight);
        List<int[]> spans = new ArrayList<int[]>();
        if (covered != null) {
            for (int[] span : covered) {
                if (span != null && span.length == 2 && span[1] > span[0]) {
                    spans.add(new int[] {Math.max(0, span[0]),
                            Math.min(screenW, span[1])});
                }
            }
        }
        Collections.sort(spans, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0;
            }
        });
        int bestLeft = 0;
        int bestRight = 0;
        int from = 0;
        for (int[] span : spans) {
            if (span[0] - from > bestRight - bestLeft) {
                bestLeft = from;
                bestRight = span[0];
            }
            from = Math.max(from, span[1]);
        }
        if (screenW - from > bestRight - bestLeft) {
            bestLeft = from;
            bestRight = screenW;
        }
        int innerLeft = bestLeft + MARGIN;
        int innerWidth = bestRight - bestLeft - 2 * MARGIN;
        if (innerWidth < MIN_WIDTH) {
            innerLeft = MARGIN;
            innerWidth = Math.max(1, screenW - 2 * MARGIN);
        }
        return new StageLayout(innerLeft, MARGIN, innerWidth,
                Math.max(1, screenH - 2 * MARGIN));
    }

    public int getLeft() { return this.left; }
    public int getTop() { return this.top; }
    public int getWidth() { return this.width; }
    public int getHeight() { return this.height; }
    public int getRight() { return this.left + this.width; }
    public int getBottom() { return this.top + this.height; }

    /** Whether a point stands on the stage. */
    public boolean contains(double x, double y) {
        return x >= this.left && x < getRight() && y >= this.top
                && y < getBottom();
    }

    /** Where the character stands across the stage. */
    public float getFigureCenterX() {
        return this.left + this.width / 2.0F;
    }

    /** Where the character's feet are, before any zoom. */
    public float getFigureBaselineY() {
        return this.top + this.height * BASELINE_FRACTION;
    }

    /** The height the character is fitted into at a zoom of one. */
    public int getFigureFitHeight() {
        return Math.max(16, Math.round(this.height * FIGURE_FIT));
    }

    /** Where the middle of the fitted character stands down the screen, before any zoom. */
    public float getFigureMiddleY() {
        return getFigureBaselineY() - getFigureFitHeight() / 2.0F;
    }
}
