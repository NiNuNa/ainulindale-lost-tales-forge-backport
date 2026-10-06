package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;

/**
 * What every list in the windows draws alike, a menu's, a picker's or a
 * page's: its headings, the row under the pointer, and where more of it
 * waits, a short honey fade on each edge the rows go on past and a thin
 * scrollbar at its right.
 */
@SideOnly(Side.CLIENT)
public final class WindowLists {
    /** How deep the honey fade on an edge the list goes on past is: half the tab strip's shade. */
    public static final float SCROLL_FADE_DEPTH =
            WindowStyle.TOP_EDGE_FADE_HEIGHT / 2.0F;
    /** How far the scrollbar stands in from the list's right edge. */
    public static final int SCROLLBAR_INSET = 2;
    /** The room a list's words leave at its right for the scrollbar: its hairline and the clear past it. */
    public static final int SCROLLBAR_ROOM = SCROLLBAR_INSET + 1;
    /** The shortest the scrollbar's thumb gets. */
    private static final int MIN_THUMB = 4;
    /** A heading's hairline: nearly opaque, a firmer line than a field's. */
    private static final int HEADING_RULE_ALPHA = 0xE0;
    /** How far above a row's foot a heading's words stand. */
    private static final int HEADING_TEXT_RISE = 10;
    private static final int HONEY_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);
    /**
     * Whether the page being drawn reaches the window's frame on its left
     * and on its right: one side of a split meets the divider there, whose
     * line is no ring to recolour. Both while no page is drawn.
     */
    private static boolean framedLeft = true;
    private static boolean framedRight = true;

    private WindowLists() {}

    /**
     * How opaque a page's surface stands at {@code alpha}, which a lit row
     * on it recolours: the window's inset surface at the window's opacity.
     */
    public static int pageSurfaceAlpha(Minecraft minecraft, int alpha) {
        return WindowStyle.insetArgb(Math.max(0, Math.min(255, alpha))
                / 255.0F * WindowStyle.opacity(minecraft)) >>> 24;
    }

    /** A list's field: a row and the hairline under it. */
    public static int fieldHeight() {
        return MenuWindow.rowHeight() + 1;
    }

    /**
     * A list's field from {@code left} to {@code right}, {@code top} down:
     * its icon on the capitals of what is typed, the field after it, while
     * it is empty the prompt in the aside tone and italics, a pixel clear
     * of the caret and cut at {@code promptRight}, and the hairline under
     * it that parts it from the rows. The field is placed here.
     */
    public static void drawField(FontRenderer font, GuiTextField field,
                                 LostTalesUiSheet icon, String prompt,
                                 int left, int top, int right,
                                 int promptRight, int alpha) {
        int height = fieldHeight();
        int textY = top + LostTalesUiInk.centredStart(height - 1,
                LostTalesUiInk.CAP_HEIGHT);
        LostTalesUiInk.beginContent();
        icon.drawWithShadow(left, textY
                + WindowStyle.centredBoxTop(icon.getHeight()), alpha);
        int textX = left + icon.getWidth() + TabIcons.GAP;
        if (field.getText().length() == 0) {
            int promptX = textX + LostTalesUiCaret.WIDTH + 1;
            LostTalesUiInk.drawText(font, "§o" + trimmed(font,
                    prompt == null ? "" : prompt, promptRight - promptX),
                    promptX, textY, WindowStyle.asideRgb(), alpha);
        }
        // The windows' own field, at the window's fade: it scrolls to its
        // caret as a bar's does.
        field.xPosition = textX;
        field.yPosition = textY;
        field.width = Math.max(1, right - textX - LostTalesUiCaret.WIDTH);
        WindowFields.draw(field, alpha);
        drawFieldRule(left, top + height - 1, right, alpha);
    }

    /**
     * A field's hairline, a pixel high from {@code top}, {@code left} to
     * {@code right}: what parts a field from the rows under it.
     */
    public static void drawFieldRule(int left, int top, int right, int alpha) {
        Gui.drawRect(left, top, right, top + 1,
                LostTalesUiInk.argb(LostTalesUiInk.SURFACE_HIGHLIGHT_RGB,
                        Math.min(alpha, 0xA0)));
    }

    /**
     * A heading over rows, a row high from {@code rowY}: its words in sand
     * from {@code textLeft}, ivory while {@code lit}, cut at {@code right},
     * over a hairline from {@code left} to {@code right} on the row's foot.
     */
    public static void drawHeading(FontRenderer font, String text, int left,
                                   int textLeft, int right, int rowY,
                                   int rowHeight, boolean lit, int alpha) {
        LostTalesUiInk.drawText(font, trimmed(font, text, right - textLeft),
                textLeft, headingTextTop(rowY, rowHeight),
                lit ? LostTalesUiInk.IVORY
                        : LostTalesColors.rgb(LostTalesColors.SAND), alpha);
        Gui.drawRect(left, rowY + rowHeight - 1, right, rowY + rowHeight,
                LostTalesUiInk.argb(LostTalesUiInk.SURFACE_HIGHLIGHT_RGB,
                        Math.round(HEADING_RULE_ALPHA * alpha / 255.0F)));
    }

    /** Says which sides of the page about to be drawn meet the window's frame; see {@link #drawLitRow}. */
    static void framedSides(boolean left, boolean right) {
        framedLeft = left;
        framedRight = right;
    }

    /**
     * A lit row, the one under the pointer or a chosen one, from
     * {@code left} to {@code right}: the window's surface recoloured to its
     * lighter shade, and where the row reaches a side of its box
     * ({@code boxLeft}, {@code boxRight}) that is the window's frame, the
     * frame's ring beside it with it. Drawn before anything lands on the row.
     */
    public static void drawLitRow(double boxLeft, double boxRight,
                                  double left, double top, double right,
                                  double bottom, int surfaceAlpha) {
        WindowStyle.recolourFlat((float)left, (float)top, (float)right,
                (float)bottom, surfaceAlpha, LostTalesUiInk.SURFACE_RGB,
                LostTalesUiInk.SURFACE_HIGHLIGHT_RGB);
        int ring = LostTalesUiWindowFrame.WIDTH;
        if (framedLeft && left <= boxLeft) {
            WindowStyle.recolourFlat((float)(boxLeft - ring), (float)top,
                    (float)boxLeft, (float)bottom, surfaceAlpha,
                    LostTalesUiInk.SURFACE_RGB,
                    LostTalesUiInk.SURFACE_HIGHLIGHT_RGB);
        }
        if (framedRight && right >= boxRight) {
            WindowStyle.recolourFlat((float)boxRight, (float)top,
                    (float)(boxRight + ring), (float)bottom, surfaceAlpha,
                    LostTalesUiInk.SURFACE_RGB,
                    LostTalesUiInk.SURFACE_HIGHLIGHT_RGB);
        }
    }

    /** A separator's room: two clear pixels, its hairline, two clear pixels. */
    public static final int SEPARATOR_HEIGHT = 5;

    /**
     * A separator a {@link #SEPARATOR_HEIGHT} high from {@code rowY}: a quiet
     * hairline from {@code left} to {@code right}, the tab row's dividers
     * laid on their side.
     */
    public static void drawSeparator(int left, int right, int rowY, int alpha) {
        LostTalesUiRules.drawRule(left, right, rowY + 2, rowY + 3,
                Math.round(WindowStyle.DIVIDER_ALPHA * alpha / 255.0F));
    }

    /** Where a heading's words stand in its row: the top of their capitals. */
    public static int headingTextTop(int rowY, int rowHeight) {
        return rowY + rowHeight - HEADING_TEXT_RISE;
    }

    /**
     * Where more of a list waits. A honey fade hangs from each edge the
     * rows go on past, {@code edgeTop} or {@code edgeBottom}: the frame,
     * or the line the rows pass under, so it touches it, as the chat
     * history's shade touches the window's edge; it is drawn over the
     * rows and under the frame. A scrollbar stands at the right of the
     * band the rows show in, {@code bandTop} to {@code bandBottom}, a
     * hairline track with an ivory thumb that stands where {@code scroll}
     * (the drawn offset, so it glides with the rows) is in
     * {@code maxScroll}. Nothing while the list fits.
     */
    public static void drawScroll(double listLeft, double edgeTop,
                                  double listRight, double edgeBottom,
                                  double bandTop, double bandBottom,
                                  double scroll, double maxScroll, int alpha) {
        float left = (float)listLeft;
        float right = (float)listRight;
        float top = (float)bandTop;
        float bottom = (float)bandBottom;
        float band = bottom - top;
        if (maxScroll <= 0.5D || band <= 0.0F || right <= left) {
            return;
        }
        int fade = Math.round(LostTalesUiRules.EDGE_FADE_ALPHA * alpha
                / 255.0F);
        if (scroll > 0.5D) {
            LostTalesUiRules.drawEdgeFade(left, right, left, right,
                    (float)edgeTop, (float)edgeBottom, SCROLL_FADE_DEPTH,
                    fade, HONEY_RGB, false);
        }
        if (scroll < maxScroll - 0.5D) {
            LostTalesUiRules.drawEdgeFade(left, right, left, right,
                    (float)edgeBottom, (float)edgeTop, SCROLL_FADE_DEPTH,
                    fade, HONEY_RGB, false);
        }
        float thumb = Math.max(MIN_THUMB,
                (float)(band * band / (band + maxScroll)));
        double share = Math.max(0.0D, Math.min(1.0D, scroll / maxScroll));
        float thumbTop = (float)LostTalesDisplayPixels.snap(
                top + (band - thumb) * share);
        float thumbBottom = Math.min(bottom, thumbTop + thumb);
        float x = right - SCROLLBAR_INSET;
        int track = LostTalesUiInk.argb(LostTalesUiInk.SURFACE_HIGHLIGHT_RGB,
                Math.min(alpha, 120));
        LostTalesUiInk.fillRect(x, top, x + 1, thumbTop, track);
        LostTalesUiInk.fillRect(x, thumbBottom, x + 1, bottom, track);
        LostTalesUiInk.fillRect(x, thumbTop, x + 1, thumbBottom,
                LostTalesUiInk.argb(LostTalesUiInk.IVORY, Math.min(alpha, 200)));
    }

    private static String trimmed(FontRenderer font, String text, int width) {
        return font.getStringWidth(text) <= width ? text
                : LostTalesSkyrimUiStyle.trimToWidth(font, text,
                        Math.max(0, width));
    }
}
