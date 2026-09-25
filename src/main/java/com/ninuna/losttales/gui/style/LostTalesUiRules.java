package com.ninuna.losttales.gui.style;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import net.minecraft.client.renderer.Tessellator;

/**
 * The interface's rules and shades: a rule is ivory, full at its middle
 * and nothing at its ends; a shade hangs from an edge and thins away
 * from it.
 */
public final class LostTalesUiRules {
    private LostTalesUiRules() {}

    /** Opacity of an edge fade on the edge it hangs from: a third. */
    public static final int EDGE_FADE_ALPHA = Math.round(255.0F / 3.0F);

    /**
     * Mesh resolution of an edge fade. The horizontal ramp is linear, so
     * two columns carry it exactly; the vertical one is eased, and each
     * row is a straight segment of that curve, so the rows are what
     * decides whether the gradient bands. Against the blurred, flat
     * backdrop the chat opens over, a coarse ramp shows its seams, so
     * the curve is cut finely, in one column of quads.
     */
    private static final int EDGE_FADE_ROWS = 32;

    /** Columns the bell of a tab's own shade is drawn in. */
    private static final int BELL_FADE_COLUMNS = 8;

    /**
     * A tab's own shade above the rule, in the tab's colour: the edge
     * fade with a bell across the tab's width, full at its middle and
     * nothing at either side, eased the way the vertical ramp is, so
     * the colour gathers under the tab's middle and is gone where the
     * tab meets its neighbours.
     */
    public static void drawBellFade(float left, float right, float edge,
                             float limit, float height, int alpha, int rgb) {
        if (right <= left) {
            return;
        }
        float[] columnX = new float[BELL_FADE_COLUMNS + 1];
        float[] columnWeight = new float[columnX.length];
        for (int column = 0; column <= BELL_FADE_COLUMNS; column++) {
            float t = column / (float)BELL_FADE_COLUMNS;
            columnX[column] = left + (right - left) * t;
            columnWeight[column] = 1.0F - LostTalesGuiEasing.smoothStep(
                    Math.abs(t - 0.5F) * 2.0F);
        }
        drawEdgeFade(columnX, columnWeight, edge, limit, height, alpha, rgb);
    }

    /**
     * As above in {@code rgb}, drawing only the stretch from
     * {@code from} to {@code to} of the band from {@code left} to
     * {@code right}: what the tab strip draws in pieces, each tab's
     * stretch in the tab's own colour and the selected tab's left out.
     * {@code ramped} gives the band the history's left-to-right ramp,
     * one ramp across every piece; without it the shade is even across
     * the whole width, as the strip's is, so its far end shows the
     * shade as its near end does.
     */
    public static void drawEdgeFade(float left, float right, float from, float to,
                             float edge, float limit, float height,
                             int alpha, int rgb, boolean ramped) {
        float drawLeft = Math.max(left, from);
        float drawRight = Math.min(right, to);
        if (right <= left || drawRight <= drawLeft) {
            return;
        }
        // The ramp is linear across the whole span, so the drawn stretch
        // takes its weight at either end from the span's own line.
        float[] columnX = {drawLeft, drawRight};
        float[] columnWeight = ramped
                ? new float[] {
                        1.0F - (drawLeft - left) / (right - left),
                        1.0F - (drawRight - left) / (right - left)}
                : new float[] {1.0F, 1.0F};
        drawEdgeFade(columnX, columnWeight, edge, limit, height, alpha, rgb);
    }

    /**
     * The edge fade over the columns given — each with its own share of
     * the opacity, the shade blending straight between neighbours —
     * from {@code edge} toward {@code limit} for at most {@code height}.
     */
    private static void drawEdgeFade(float[] columnX, float[] columnWeight,
                                     float edge, float limit, float height,
                                     int alpha, int rgb) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (safeAlpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        boolean downward = limit > edge;
        int backdropRgb = rgb;
        float far = downward
                ? Math.min(limit, edge + height)
                : Math.max(limit, edge - height);
        if (far == edge) {
            return;
        }
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
        for (int rowIndex = 0; rowIndex < EDGE_FADE_ROWS; rowIndex++) {
            // Each band runs from nearer the edge to farther from it;
            // its top and bottom are then taken in screen order so the
            // winding is the backdrop's either way.
            float near = edge + (far - edge)
                    * (rowIndex / (float)EDGE_FADE_ROWS);
            float away = edge + (far - edge)
                    * ((rowIndex + 1) / (float)EDGE_FADE_ROWS);
            float nearWeight = 1.0F - LostTalesGuiEasing.smoothStep(
                    rowIndex / (float)EDGE_FADE_ROWS);
            float awayWeight = 1.0F - LostTalesGuiEasing.smoothStep(
                    (rowIndex + 1) / (float)EDGE_FADE_ROWS);
            float y0 = downward ? near : away;
            float y1 = downward ? away : near;
            float v0 = downward ? nearWeight : awayWeight;
            float v1 = downward ? awayWeight : nearWeight;
            for (int column = 0; column + 1 < columnX.length; column++) {
                float x0 = columnX[column];
                float x1 = columnX[column + 1];
                if (x1 <= x0) {
                    continue;
                }
                float h0 = columnWeight[column];
                float h1 = columnWeight[column + 1];
                // Same winding as the backdrop: the GUI pass culls back
                // faces.
                tessellator.setColorRGBA_I(backdropRgb,
                        Math.round(safeAlpha * h1 * v1));
                tessellator.addVertex(x1, y1, 0.0D);
                tessellator.setColorRGBA_I(backdropRgb,
                        Math.round(safeAlpha * h1 * v0));
                tessellator.addVertex(x1, y0, 0.0D);
                tessellator.setColorRGBA_I(backdropRgb,
                        Math.round(safeAlpha * h0 * v0));
                tessellator.addVertex(x0, y0, 0.0D);
                tessellator.setColorRGBA_I(backdropRgb,
                        Math.round(safeAlpha * h0 * v1));
                tessellator.addVertex(x0, y1, 0.0D);
            }
        }
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
    }

    /**
     * The chat's rule: a band of the chat's ivory between {@code left}
     * and {@code right}, opaque at the centre and fading to nothing at
     * either end, so the edges the messages stand between read as
     * edges. The tab row draws the window's top rule with this as its
     * last pixel row; the bottom rule is drawn directly under the
     * baseline as the bar strip's first row.
     */
    public static void drawRule(float left, float right, float top, float bottom,
                         int alpha) {
        drawRuleAround(left, right, top, bottom, alpha, 0.0F, 0.0F);
    }

    /**
     * The rule with the stretch from {@code holeLeft} to
     * {@code holeRight} left out — where the selected tab stands on it —
     * hung from the tab: each piece is full where it meets the tab's
     * border and fades to nothing at the strip's end, so the rule and
     * the tab read as one. Without a hole, the whole rule as
     * {@link #drawRule} draws it.
     */
    public static void drawRuleAround(float left, float right, float top,
                               float bottom, int alpha, float holeLeft,
                               float holeRight) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (safeAlpha < LostTalesUiInk.MIN_VISIBLE_ALPHA
                || right <= left || bottom <= top) {
            return;
        }
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
        if (holeRight <= holeLeft || holeRight <= left || holeLeft >= right) {
            drawRuleSpan(tessellator, left, right, left, right, top, bottom,
                    safeAlpha);
        } else {
            drawRuleRamp(tessellator, left, Math.max(left, holeLeft), top,
                    bottom, safeAlpha);
            drawRuleRamp(tessellator, right, Math.min(right, holeRight), top,
                    bottom, safeAlpha);
        }
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
    }

    /**
     * One piece of a rule from {@code faint}, where it is nothing, to
     * {@code full}, where it is opaque; either may be the left end.
     */
    private static void drawRuleRamp(Tessellator tessellator, float faint,
                                     float full, float top, float bottom,
                                     int alpha) {
        if (faint == full) {
            return;
        }
        float leftX = Math.min(faint, full);
        float rightX = Math.max(faint, full);
        int leftAlpha = faint < full ? 0 : alpha;
        int rightAlpha = faint < full ? alpha : 0;
        // Same winding as the backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(LostTalesUiInk.IVORY, rightAlpha);
        tessellator.addVertex(rightX, bottom, 0.0D);
        tessellator.addVertex(rightX, top, 0.0D);
        tessellator.setColorRGBA_I(LostTalesUiInk.IVORY, leftAlpha);
        tessellator.addVertex(leftX, top, 0.0D);
        tessellator.addVertex(leftX, bottom, 0.0D);
    }

    /**
     * The stretch of a rule from {@code from} to {@code to}: opaque at
     * the rule's centre and nothing at its ends, the stretch cut at the
     * centre when it crosses it so each piece is one linear ramp.
     */
    private static void drawRuleSpan(Tessellator tessellator, float left,
                                     float right, float from, float to,
                                     float top, float bottom, int alpha) {
        if (to <= from) {
            return;
        }
        float centre = (left + right) / 2.0F;
        if (from < centre && to > centre) {
            drawRuleSpan(tessellator, left, right, from, centre, top, bottom,
                    alpha);
            drawRuleSpan(tessellator, left, right, centre, to, top, bottom,
                    alpha);
            return;
        }
        int fromAlpha = ruleAlpha(left, right, from, alpha);
        int toAlpha = ruleAlpha(left, right, to, alpha);
        // Same winding as the backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(LostTalesUiInk.IVORY, toAlpha);
        tessellator.addVertex(to, bottom, 0.0D);
        tessellator.addVertex(to, top, 0.0D);
        tessellator.setColorRGBA_I(LostTalesUiInk.IVORY, fromAlpha);
        tessellator.addVertex(from, top, 0.0D);
        tessellator.addVertex(from, bottom, 0.0D);
    }

    /** The rule's opacity at {@code x}: full at its centre, none at its ends. */
    private static int ruleAlpha(float left, float right, float x, int alpha) {
        float half = (right - left) / 2.0F;
        float centre = (left + right) / 2.0F;
        return half <= 0.0F ? alpha : Math.round(alpha
                * Math.max(0.0F, 1.0F - Math.abs(x - centre) / half));
    }

    /**
     * The vertical counterpart of {@link #drawRule}: a one-pixel-wide
     * column of the chat's ivory between {@code top} and {@code bottom},
     * opaque at its vertical centre and fading to nothing toward both
     * ends. The timestamp column's separator is drawn with this.
     */
    public static void drawVerticalRule(float left, float right, float top,
                                 float bottom, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (safeAlpha < LostTalesUiInk.MIN_VISIBLE_ALPHA
                || right <= left || bottom <= top) {
            return;
        }
        float centre = (top + bottom) / 2.0F;
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
        // Same winding as the backdrop: the GUI pass culls back faces.
        // Top half: transparent end to opaque centre.
        tessellator.setColorRGBA_I(LostTalesUiInk.IVORY, safeAlpha);
        tessellator.addVertex(left, centre, 0.0D);
        tessellator.addVertex(right, centre, 0.0D);
        tessellator.setColorRGBA_I(LostTalesUiInk.IVORY, 0);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        // Bottom half: opaque centre to transparent end.
        tessellator.setColorRGBA_I(LostTalesUiInk.IVORY, 0);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.setColorRGBA_I(LostTalesUiInk.IVORY, safeAlpha);
        tessellator.addVertex(right, centre, 0.0D);
        tessellator.addVertex(left, centre, 0.0D);
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
    }
}
