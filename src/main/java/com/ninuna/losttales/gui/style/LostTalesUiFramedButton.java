package com.ninuna.losttales.gui.style;

import org.lwjgl.opengl.GL11;

/**
 * A framed button of the chat: the sheet's rounded frame drawn to any
 * size around a control's content, the way the main menu's buttons frame
 * their labels ({@code LostTalesButtonStyle}). The corners are the
 * sheet's six-texel cells and the edges between them the corners'
 * innermost column and row stretched along, so a frame of any width or
 * height keeps the artwork's own tones and never scales a texel across.
 *
 * <p>The button paints one surface under the frame's ink — plum black at
 * the inset surface's two thirds, crossing to plum grey as it lights — in
 * a hole whatever it stands on leaves for it, so the two never lie one
 * over the other. The footprint's four corner pixels lie outside the
 * frame's rounding and stay with the surface around it
 * ({@link #fillCorners}). The frame's own backdrop texels only preview
 * that surface and are cut away on the tab pieces' ink threshold, as a
 * tab's are.</p>
 *
 * <p>The tab row's search control, the input bar's character button and
 * channel indicator, the jump-to-present button, a hovered message's
 * toolbar and the reaction chips are framed buttons: their content
 * centred inside, at least {@link #PADDING} clear pixels from the ink —
 * {@link #WIDE_PADDING} for the search control, the character button,
 * the indicator and the jump button, which have the room for it.</p>
 */
public final class LostTalesUiFramedButton {
    /** A corner cell's size; a frame is at least two corners each way. */
    public static final int CORNER = LostTalesUiSheet.FRAME_TOP_LEFT.getWidth();
    public static final int MIN_SIZE = CORNER * 2;
    /**
     * From the footprint's outer edge to the inside of the ink: a ring of
     * the button's surface, then the ink.
     */
    public static final int EDGE = 2;
    /** Clear pixels between the ink and what the button holds, at least. */
    public static final int PADDING = 2;
    /** From the footprint's outer edge to what the button holds. */
    public static final int INSET = EDGE + PADDING;
    /** The strips' buttons and the jump button keep a wider clearing round what they hold. */
    public static final int WIDE_PADDING = 3;
    public static final int WIDE_INSET = EDGE + WIDE_PADDING;
    /**
     * The one height of the framed buttons standing in a row of
     * controls — the character button, the channel indicator, the
     * jump-to-present button, the toolbar's buttons and the reaction
     * chips: an icon's box with the inset above and below it, so they
     * read as one row whatever each holds. The tab search keeps its own
     * square in the strip.
     */
    public static final int HEIGHT = LostTalesUiInk.ICON_SIZE + 2 * INSET;

    private LostTalesUiFramedButton() {}

    /** The surface's tone as far as the button has lit. */
    public static int surfaceRgb(float lit) {
        return LostTalesUiInk.blend(
                LostTalesUiInk.SURFACE_RGB,
                LostTalesUiInk.SURFACE_HIGHLIGHT_RGB, lit);
    }

    /**
     * The button's surface in one layer over its footprint less the four
     * corner pixels, in {@link #surfaceRgb} at {@code alpha}.
     */
    public static void drawSurface(float left, float top, float width,
                            float height, float lit, int alpha) {
        if (width < MIN_SIZE || height < MIN_SIZE
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        int argb = LostTalesUiInk.argb(surfaceRgb(lit), alpha);
        float right = left + width;
        float bottom = top + height;
        LostTalesUiInk.fillRect(left + 1, top, right - 1,
                top + 1, argb);
        LostTalesUiInk.fillRect(left, top + 1, right,
                bottom - 1, argb);
        LostTalesUiInk.fillRect(left + 1, bottom - 1,
                right - 1, bottom, argb);
    }

    /**
     * The footprint's four corner pixels in {@code argb}: what a surface
     * the button stands in fills back after leaving the button's box out
     * of itself.
     */
    public static void fillCorners(float left, float top, float width, float height,
                            int argb) {
        float right = left + width;
        float bottom = top + height;
        LostTalesUiInk.fillRect(left, top, left + 1, top + 1,
                argb);
        LostTalesUiInk.fillRect(right - 1, top, right,
                top + 1, argb);
        LostTalesUiInk.fillRect(left, bottom - 1, left + 1,
                bottom, argb);
        LostTalesUiInk.fillRect(right - 1, bottom - 1, right,
                bottom, argb);
    }

    /**
     * The frame's ink: the resting frame whole, and the lit one laid over
     * it as far as {@code lit} has come, both cut to their ink, the sheet
     * one texel to one pixel from an origin the caller lays on a whole
     * display pixel.
     */
    public static void drawInk(float left, float top, int width, int height,
                        float lit, int alpha) {
        if (width < MIN_SIZE || height < MIN_SIZE
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        try {
            // The test sees texture and vertex alpha multiplied, so the
            // threshold is scaled by the share each frame is drawn at.
            GL11.glAlphaFunc(GL11.GL_GREATER,
                    LostTalesUiSheet.INK_THRESHOLD * alpha / 255.0F);
            drawFrame(false, left, top, width, height, alpha);
            int over = Math.round(alpha * Math.max(0.0F,
                    Math.min(1.0F, lit)));
            if (over >= LostTalesUiInk.MIN_VISIBLE_ALPHA) {
                GL11.glAlphaFunc(GL11.GL_GREATER,
                        LostTalesUiSheet.INK_THRESHOLD * over / 255.0F);
                drawFrame(true, left, top, width, height, over);
            }
        } finally {
            // The threshold vanilla's GUI runs under.
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        }
    }

    /**
     * One corner cell of a frame alone, cut to its ink, the sheet one
     * texel to one pixel from an origin the caller lays on a whole display
     * pixel: what a chat window's frame closes its brightest corners with,
     * so they round and shade as a framed button's do.
     */
    public static void drawCornerInk(LostTalesUiSheet corner, float left, float top,
                              int alpha) {
        if (corner == null
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        try {
            GL11.glAlphaFunc(GL11.GL_GREATER,
                    LostTalesUiSheet.INK_THRESHOLD * alpha / 255.0F);
            corner.draw(left, top, alpha);
        } finally {
            // The threshold vanilla's GUI runs under.
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        }
    }

    /** One colourway of the frame: its corners, and its edges between them. */
    private static void drawFrame(boolean lit, float left, float top,
                                  int width, int height, int alpha) {
        LostTalesUiSheet topLeft = lit ? LostTalesUiSheet.FRAME_LIT_TOP_LEFT
                : LostTalesUiSheet.FRAME_TOP_LEFT;
        LostTalesUiSheet topRight = lit ? LostTalesUiSheet.FRAME_LIT_TOP_RIGHT
                : LostTalesUiSheet.FRAME_TOP_RIGHT;
        LostTalesUiSheet bottomLeft = lit ? LostTalesUiSheet.FRAME_LIT_BOTTOM_LEFT
                : LostTalesUiSheet.FRAME_BOTTOM_LEFT;
        LostTalesUiSheet bottomRight = lit ? LostTalesUiSheet.FRAME_LIT_BOTTOM_RIGHT
                : LostTalesUiSheet.FRAME_BOTTOM_RIGHT;
        float right = left + width;
        float bottom = top + height;
        topLeft.draw(left, top, alpha);
        topRight.draw(right - CORNER, top, alpha);
        bottomLeft.draw(left, bottom - CORNER, alpha);
        bottomRight.draw(right - CORNER, bottom - CORNER, alpha);
        int span = width - MIN_SIZE;
        if (span > 0) {
            // The top and bottom edges: each left corner's innermost
            // column, stretched between the corners.
            LostTalesUiSheet.drawStretched(topLeft.getTextureU() + CORNER - 1,
                    topLeft.getTextureV(), 1, CORNER, left + CORNER, top,
                    span, CORNER, alpha);
            LostTalesUiSheet.drawStretched(
                    bottomLeft.getTextureU() + CORNER - 1,
                    bottomLeft.getTextureV(), 1, CORNER, left + CORNER,
                    bottom - CORNER, span, CORNER, alpha);
        }
        int rise = height - MIN_SIZE;
        if (rise > 0) {
            // The sides: each top corner's innermost row, stretched
            // between the corners.
            LostTalesUiSheet.drawStretched(topLeft.getTextureU(),
                    topLeft.getTextureV() + CORNER - 1, CORNER, 1, left,
                    top + CORNER, CORNER, rise, alpha);
            LostTalesUiSheet.drawStretched(topRight.getTextureU(),
                    topRight.getTextureV() + CORNER - 1, CORNER, 1,
                    right - CORNER, top + CORNER, CORNER, rise, alpha);
        }
    }
}
