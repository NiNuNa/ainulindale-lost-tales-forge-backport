package com.ninuna.losttales.gui.style;

/**
 * The frame every window wears, and every panel that stands like one: a
 * lit framed button's frame just outside the window's box, differing only
 * in that it is no button, it fades and it is always lit (Nils,
 * 2026-09-23). Its edges and corners come from the sheet's
 * {@code WINDOW_FRAME_*} cells, which carry the lit button's ink texel for
 * texel. The left and bottom edges are strongest at the bottom-left corner
 * and the top and right ones at the top-right, each fading to nothing at
 * the far corner, and the corners fade with them. The ring the frame lies
 * on is the surface of whatever it runs beside, which the window paints.
 */
public final class LostTalesUiWindowFrame {
    /** The frame's ink: one pixel. */
    public static final int EDGE_WIDTH = 1;
    /** The ring outside the box: a pixel of surface, and the ink over the inner one. */
    public static final int WIDTH = EDGE_WIDTH + 1;

    /**
     * How far each corner's artwork runs along the two edges it joins:
     * from the box's corner, where the frame's ring ends, to the corner
     * cell's inner side. The edges stop there and the corner draws on, so
     * no pixel of the frame lies twice.
     */
    static final int CORNER_ARM = LostTalesUiFramedButton.CORNER - WIDTH;

    /**
     * Where the frame's edges run across a corner cell: a texel in from
     * its outer sides, past the ring's outer pixel.
     */
    static final int EDGE_LINE = WIDTH - EDGE_WIDTH;

    private LostTalesUiWindowFrame() {}

    /**
     * The ring round the box {@code left} to {@code right} by {@code top}
     * to {@code bottom}: {@link #WIDTH} wide just outside the box, in
     * {@code surface}, its four outermost corner pixels left out, as a
     * framed button's footprint corners are.
     */
    public static void drawSurface(float left, float top, float right,
                                   float bottom, int surface) {
        int ring = WIDTH;
        LostTalesUiInk.fillRect(left - ring + 1, top - ring, right + ring - 1,
                top - ring + 1, surface);
        LostTalesUiInk.fillRect(left - ring, top - ring + 1, right + ring, top,
                surface);
        LostTalesUiInk.fillRect(left - ring, top, left, bottom, surface);
        LostTalesUiInk.fillRect(right, top, right + ring, bottom, surface);
        LostTalesUiInk.fillRect(left - ring, bottom, right + ring,
                bottom + ring - 1, surface);
        LostTalesUiInk.fillRect(left - ring + 1, bottom + ring - 1,
                right + ring - 1, bottom + ring, surface);
    }

    /**
     * The frame's edges round the box {@code left} to {@code right} by
     * {@code top} to {@code bottom}, at {@code alpha}: one pixel just
     * outside each side, over the ring's inner pixel. A window whose lower
     * part moves on its own (a chat window's input bar) draws the two
     * halves itself: {@link #drawEdgesAbove} and {@link #drawEdgesBelow}.
     */
    public static void drawEdges(float left, float top, float right,
                                 float bottom, int alpha) {
        float split = bottom - CORNER_ARM;
        drawEdgesAbove(left, top, right, bottom, split, alpha);
        drawEdgesBelow(left, top, right, bottom, split, alpha);
    }

    /**
     * The upper part of the frame's edges: the top edge between its
     * corners, the two top corners, and the side edges from under the
     * corners down to {@code split}, all on the whole frame's ramps.
     */
    public static void drawEdgesAbove(float left, float top, float right,
                                      float bottom, float split, int alpha) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA
                || right <= left || bottom <= top) {
            return;
        }
        int edge = EDGE_WIDTH;
        int ring = WIDTH;
        int cell = LostTalesUiFramedButton.CORNER;
        int arm = CORNER_ARM;
        float span = bottom - top + edge;
        float edgeTop = top - edge;
        float between = right - left - 2 * arm;
        drawEdge(false, false, left + arm, edgeTop, between,
                edgeAlphaAt(alpha, right, left, left + arm),
                edgeAlphaAt(alpha, right, left, right - arm));
        drawEdge(true, false, left - edge, top + arm, split - top - arm,
                rampAlpha(alpha, bottom, span, top + arm),
                rampAlpha(alpha, bottom, span, split));
        drawEdge(true, true, right, top + arm, split - top - arm,
                rampDownAlpha(alpha, edgeTop, span, top + arm),
                rampDownAlpha(alpha, edgeTop, span, split));
        drawCorner(LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT, left - ring,
                top - ring, false, false,
                rampAlpha(alpha, bottom, span, top),
                rampAlpha(alpha, bottom, span, top + arm),
                edgeAlphaAt(alpha, right, left, left),
                edgeAlphaAt(alpha, right, left, left + arm));
        drawCorner(LostTalesUiSheet.WINDOW_FRAME_TOP_RIGHT,
                right + ring - cell, top - ring, true, false,
                rampDownAlpha(alpha, edgeTop, span, top),
                rampDownAlpha(alpha, edgeTop, span, top + arm),
                edgeAlphaAt(alpha, right, left, right),
                edgeAlphaAt(alpha, right, left, right - arm));
    }

    /**
     * The lower part of the frame's edges: the side edges from
     * {@code split} down to the bottom corners, the bottom edge between
     * them, and the two bottom corners, all on the whole frame's ramps.
     */
    public static void drawEdgesBelow(float left, float top, float right,
                                      float bottom, float split, int alpha) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA
                || right <= left || bottom <= top) {
            return;
        }
        int edge = EDGE_WIDTH;
        int ring = WIDTH;
        int cell = LostTalesUiFramedButton.CORNER;
        int arm = CORNER_ARM;
        float span = bottom - top + edge;
        float edgeTop = top - edge;
        drawEdge(true, false, left - edge, split, bottom - arm - split,
                rampAlpha(alpha, bottom, span, split),
                rampAlpha(alpha, bottom, span, bottom - arm));
        drawEdge(true, true, right, split, bottom - arm - split,
                rampDownAlpha(alpha, edgeTop, span, split),
                rampDownAlpha(alpha, edgeTop, span, bottom - arm));
        drawEdge(false, true, left + arm, bottom, right - left - 2 * arm,
                edgeAlphaAt(alpha, left, right, left + arm),
                edgeAlphaAt(alpha, left, right, right - arm));
        drawCorner(LostTalesUiSheet.WINDOW_FRAME_BOTTOM_LEFT, left - ring,
                bottom + ring - cell, false, true,
                rampAlpha(alpha, bottom, span, bottom),
                rampAlpha(alpha, bottom, span, bottom - arm),
                edgeAlphaAt(alpha, left, right, left),
                edgeAlphaAt(alpha, left, right, left + arm));
        drawCorner(LostTalesUiSheet.WINDOW_FRAME_BOTTOM_RIGHT,
                right + ring - cell, bottom + ring - cell, true, true,
                rampDownAlpha(alpha, edgeTop, span, bottom),
                rampDownAlpha(alpha, edgeTop, span, bottom - arm),
                edgeAlphaAt(alpha, left, right, right),
                edgeAlphaAt(alpha, left, right, right - arm));
    }

    /**
     * A texel's index across a frame's corner cell, {@code depth} texels
     * in from the cell's outer side: counted from the cell's start on a
     * side it starts on, from its end on one it ends on ({@code far}).
     */
    static int cornerTexel(boolean far, int depth) {
        return far ? LostTalesUiFramedButton.CORNER - 1 - depth : depth;
    }

    /**
     * The opacity of a corner's rounding pixel: the mean of the two arm
     * pixels beside it, the first of each arm, each at its centre on the
     * arm's fade from its near opacity to its far one.
     */
    static int bendAlpha(int sideNear, int sideFar, int crossNear,
                         int crossFar) {
        float first = 0.5F / CORNER_ARM;
        return Math.round((sideNear + (sideFar - sideNear) * first
                + crossNear + (crossFar - crossNear) * first) / 2.0F);
    }

    /**
     * One corner: the corner cell {@code corner}, its top-left at
     * ({@code cellX}, {@code cellY}), on the frame's right for
     * {@code right} and at its foot for {@code bottom}. Only its ink is
     * drawn, an arm on the side edge, an arm on the top or bottom edge
     * (the cross arm) and the rounding pixel in the bend, each on the
     * frame's ramps: the side arm fading from {@code sideNear} at the bend
     * to {@code sideFar} at its other end, the cross arm from
     * {@code crossNear} to {@code crossFar}, the rounding pixel at the mean
     * of the two pixels beside it. The lit corners stand at the ramps' full
     * ends and the faded ones at their empty ends, so every corner of the
     * frame is one shape.
     */
    static void drawCorner(LostTalesUiSheet corner, float cellX, float cellY,
                           boolean right, boolean bottom, int sideNear,
                           int sideFar, int crossNear, int crossFar) {
        int ring = WIDTH;
        int u = corner.getTextureU();
        int v = corner.getTextureV();
        // Each arm runs along the edges' line between the box's corner, a
        // ring in, and the cell's inner side.
        int column = cornerTexel(right, EDGE_LINE);
        int row = cornerTexel(bottom, EDGE_LINE);
        int sideFirst = bottom ? 0 : ring;
        int crossFirst = right ? 0 : ring;
        LostTalesUiSheet.drawFading(u + column, v + sideFirst, CORNER_ARM,
                true, cellX + column, cellY + sideFirst, CORNER_ARM,
                bottom ? sideFar : sideNear, bottom ? sideNear : sideFar);
        LostTalesUiSheet.drawFading(u + crossFirst, v + row, CORNER_ARM,
                false, cellX + crossFirst, cellY + row, CORNER_ARM,
                right ? crossFar : crossNear, right ? crossNear : crossFar);
        // The rounding pixel stands in the box's corner, diagonally inside
        // the bend, beside each arm's first pixel.
        int bendColumn = cornerTexel(right, ring);
        int bendRow = cornerTexel(bottom, ring);
        int bend = bendAlpha(sideNear, sideFar, crossNear, crossFar);
        LostTalesUiSheet.drawFading(u + bendColumn, v + bendRow, 1, true,
                cellX + bendColumn, cellY + bendRow, 1.0F, bend, bend);
    }

    /**
     * The corner cell an edge is stretched from, as a framed button
     * stretches its own: the top and bottom edges from their left
     * corner's innermost column, the sides from their top corner's
     * innermost row. {@code side} picks a side over a top or bottom edge,
     * {@code far} the right side or the bottom edge.
     */
    static LostTalesUiSheet edgeCorner(boolean side, boolean far) {
        if (side) {
            return far ? LostTalesUiSheet.WINDOW_FRAME_TOP_RIGHT
                    : LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT;
        }
        return far ? LostTalesUiSheet.WINDOW_FRAME_BOTTOM_LEFT
                : LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT;
    }

    /**
     * One stretch of an edge, drawn as a lit framed button draws that
     * edge: the texel of its corner cell's innermost column or row that
     * lies on the edges' line ({@link #edgeCorner}), stretched from
     * ({@code x}, {@code y}) over {@code length} pixels, down a side
     * ({@code side}) or rightward along the top or bottom edge, fading from
     * {@code startAlpha} to {@code endAlpha}. {@code far} is the right side
     * or the bottom edge.
     */
    static void drawEdge(boolean side, boolean far, float x, float y,
                         float length, int startAlpha, int endAlpha) {
        LostTalesUiSheet corner = edgeCorner(side, far);
        int innermost = LostTalesUiFramedButton.CORNER - 1;
        int line = cornerTexel(far, EDGE_LINE);
        LostTalesUiSheet.drawFading(
                corner.getTextureU() + (side ? line : innermost),
                corner.getTextureV() + (side ? innermost : line), 1, side,
                x, y, length, startAlpha, endAlpha);
    }

    /**
     * A horizontal edge's opacity at {@code x}, for an edge fading
     * linearly from {@code alpha} at {@code full} to nothing at
     * {@code gone}: the top edge from its right end to its left, the
     * bottom edge from its left end to its right.
     */
    static int edgeAlphaAt(int alpha, float full, float gone, float x) {
        float span = Math.abs(gone - full);
        if (span <= 0.0F) {
            return 0;
        }
        return Math.round(alpha * Math.max(0.0F,
                Math.min(1.0F, 1.0F - Math.abs(x - full) / span)));
    }

    /** The left side's ramp: its opacity at {@code y}, full at the bottom; clamped, linear. */
    private static int rampAlpha(int alpha, float rampBottom, float rampSpan,
                                 float y) {
        float share = 1.0F - (rampBottom - y) / rampSpan;
        return Math.round(alpha * Math.max(0.0F, Math.min(1.0F, share)));
    }

    /** The right side's ramp: its opacity at {@code y}, full at the top; clamped, linear. */
    private static int rampDownAlpha(int alpha, float rampTop, float rampSpan,
                                     float y) {
        float share = 1.0F - (y - rampTop) / rampSpan;
        return Math.round(alpha * Math.max(0.0F, Math.min(1.0F, share)));
    }
}
