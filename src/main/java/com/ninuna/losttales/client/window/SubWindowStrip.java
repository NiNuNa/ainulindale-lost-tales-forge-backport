package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import net.minecraft.client.gui.FontRenderer;

/**
 * The strip across a sub-window's top: a window's tool strip, its height
 * and its plum grey, with the rule on its last row. The icon and the name
 * stand at its left, the cross at its right, and the window is carried by
 * it. Under it the content stands on the inset surface, and the frame's
 * ring wears whichever of the two it runs beside.
 */
final class SubWindowStrip {
    /** A window's tool strip, rule included. */
    static final int HEIGHT = WindowPlacement.TOOL_STRIP_HEIGHT;
    /** Clear pixels from the box's edge to the icon, and from the cross to the edge. */
    private static final int EDGE_MARGIN = 3;
    /** Clear pixels between the name and the cross. */
    private static final int CLOSE_GAP = 5;
    /** The capitals' top: centred in the rows above the rule, the odd pixel up. */
    static final int TEXT_TOP = (HEIGHT - 1 - LostTalesUiInk.CAP_HEIGHT) / 2;

    final String label;
    final boolean icon;
    final int textX;
    final LostTalesUiHitBox closeBox;

    private SubWindowStrip(String label, boolean icon, int textX,
                           LostTalesUiHitBox closeBox) {
        this.label = label;
        this.icon = icon;
        this.textX = textX;
        this.closeBox = closeBox;
    }

    /**
     * Lays the strip out across {@code left} to {@code right} from
     * {@code top}, whole pixels: the icon when {@code icon}, the name cut
     * to the room the cross leaves it, and the cross.
     */
    static SubWindowStrip layOut(FontRenderer font, int left, int right,
                                 int top, String title, boolean icon) {
        int textX = left + EDGE_MARGIN
                + (icon ? TabIcons.SLOT + TabIcons.GAP : 0);
        int closeLeft = right - EDGE_MARGIN - TabRow.CONTROL_SIZE;
        int room = Math.max(0, closeLeft - CLOSE_GAP - textX);
        String shown = font.getStringWidth(title) <= room ? title
                : LostTalesSkyrimUiStyle.trimToWidth(font, title, room);
        int textTop = top + TEXT_TOP;
        return new SubWindowStrip(shown, icon, textX,
                new LostTalesUiHitBox(closeLeft, textTop
                        + WindowStyle.centredBoxTop(TabRow.CONTROL_SIZE),
                        TabRow.CONTROL_SIZE, TabRow.CONTROL_SIZE));
    }

    /**
     * How wide a strip must be to show {@code title} whole beside its
     * cross, and its icon when {@code icon}: what a window opening at its
     * content's own size is at least as wide as.
     */
    static int widthFor(FontRenderer font, String title, boolean icon) {
        return EDGE_MARGIN * 2 + (icon ? TabIcons.SLOT + TabIcons.GAP : 0)
                + font.getStringWidth(title) + CLOSE_GAP + TabRow.CONTROL_SIZE;
    }

    /**
     * The window's surfaces round its box {@code left} to {@code right} by
     * {@code top} to {@code bottom}: the strip in {@code stripArgb}, the
     * content under it in {@code insetArgb}, and the frame's ring beside
     * each in that one's colour, its four outermost corner pixels left out
     * as {@link LostTalesUiWindowFrame#drawSurface} leaves them. Then the
     * strip's rule, across the ring as a window's tool strip rule runs.
     */
    static void drawSurfaces(float left, float top, float right, float bottom,
                             int stripArgb, int insetArgb, int alpha) {
        int ring = LostTalesUiWindowFrame.WIDTH;
        float stripBottom = Math.min(bottom, top + HEIGHT);
        LostTalesUiInk.fillRect(left - ring + 1, top - ring, right + ring - 1,
                top - ring + 1, stripArgb);
        LostTalesUiInk.fillRect(left - ring, top - ring + 1, right + ring, top,
                stripArgb);
        LostTalesUiInk.fillRect(left - ring, top, right + ring, stripBottom,
                stripArgb);
        LostTalesUiInk.fillRect(left, stripBottom, right, bottom, insetArgb);
        LostTalesUiInk.fillRect(left - ring, stripBottom, left, bottom,
                insetArgb);
        LostTalesUiInk.fillRect(right, stripBottom, right + ring, bottom,
                insetArgb);
        LostTalesUiInk.fillRect(left - ring, bottom, right + ring,
                bottom + ring - 1, insetArgb);
        LostTalesUiInk.fillRect(left - ring + 1, bottom + ring - 1,
                right + ring - 1, bottom + ring, insetArgb);
        if (alpha >= LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            LostTalesUiRules.drawRule(left - ring, right + ring,
                    stripBottom - 1, stripBottom, alpha);
        }
    }

    /**
     * The icon, the name in ivory and the cross, stepped by
     * {@code closeMotion}, on the strip laid out from {@code top}.
     */
    void drawContent(FontRenderer font, int left, int top,
                     LostTalesUiSheet iconGlyph,
                     LostTalesUiButtonMotion closeMotion, int alpha) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        int textTop = top + TEXT_TOP;
        LostTalesUiInk.beginContent();
        if (this.icon && iconGlyph != null) {
            iconGlyph.drawWithShadow(left + EDGE_MARGIN
                            + LostTalesUiInk.centredStart(TabIcons.SIZE,
                                    iconGlyph.getWidth()),
                    textTop + WindowStyle.centredBoxTop(iconGlyph.getHeight()),
                    alpha);
        }
        LostTalesUiInk.drawText(font, this.label, this.textX, textTop,
                LostTalesUiInk.IVORY, alpha);
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.CLOSE,
                LostTalesUiSheet.CLOSE_HOVER, closeMotion,
                (float)this.closeBox.left + (TabRow.CONTROL_SIZE
                        - LostTalesUiSheet.CLOSE.getWidth()) / 2,
                (int)this.closeBox.top + (TabRow.CONTROL_SIZE
                        - LostTalesUiSheet.CLOSE.getHeight()) / 2,
                alpha);
    }
}
