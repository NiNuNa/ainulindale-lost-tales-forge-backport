package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import net.minecraft.client.gui.FontRenderer;

/**
 * The strip across a sub-window's top: a window's tool strip, its height
 * and its secondary colour, with the rule on its last row. The icon and the name
 * stand at its left; at its right, as a window's tab row keeps them, the
 * padlock, a hairline, the cross, another hairline and the grip; a window
 * that cannot be closed has the padlock, one hairline and the grip. Under it
 * the content stands on the inset surface, and the frame's ring wears
 * whichever of the two it runs beside.
 */
final class SubWindowStrip {
    /** A window's tool strip, rule included. */
    static final int HEIGHT = WindowPlacement.TOOL_STRIP_HEIGHT;
    /** Clear pixels from the box's edge to the icon. */
    /** Clear pixels between the name and the padlock. */
    private static final int NAME_GAP = 5;
    /** Room for a few letters of the name, the least a strip keeps for it. */
    private static final int FEW_LETTERS = 12;
    /** The capitals' top: centred in the rows above the rule, the odd pixel up. */
    static final int TEXT_TOP = (HEIGHT - 1 - LostTalesUiInk.CAP_HEIGHT) / 2;
    /** Clear space between a control and a hairline or the frame. */
    private static final int EDGE_GAP = WindowStyle.EDGE_GAP;
    private static final int DIVIDER_WIDTH = WindowStyle.DIVIDER_WIDTH;
    private static final int DIVIDER_HEIGHT = TabRow.END_CONTROL_SIZE;
    private static final int CLOSE_WIDTH = LostTalesUiSheet.CLOSE.getWidth();
    private static final int CLOSE_HEIGHT = LostTalesUiSheet.CLOSE.getHeight();
    private static final int GRIP_WIDTH = LostTalesUiSheet.GRIP.getWidth();
    private static final int GRIP_HEIGHT = LostTalesUiSheet.GRIP.getHeight();
    /**
     * The controls' run at the strip's right end, from the padlock's left
     * to the edge: each with the gap after it, and the grip's inset.
     */
    private static final int CONTROLS_WIDTH = LockAnimation.WIDTH + EDGE_GAP
            + DIVIDER_WIDTH + EDGE_GAP + CLOSE_WIDTH + EDGE_GAP + DIVIDER_WIDTH
            + EDGE_GAP + GRIP_WIDTH + EDGE_GAP;
    /** The same run with no cross: the padlock, one hairline and the grip. */
    private static final int CROSSLESS_CONTROLS_WIDTH = LockAnimation.WIDTH
            + EDGE_GAP + DIVIDER_WIDTH + EDGE_GAP + GRIP_WIDTH + EDGE_GAP;

    final String label;
    final boolean icon;
    /** Whether it has a cross: false for a window that cannot be closed. */
    final boolean closable;
    final int textX;
    /**
     * What the padlock and the cross answer on: their ink and the end
     * controls' clearing; the cross's is null on a strip without one.
     */
    final LostTalesUiHitBox lockBox;
    final LostTalesUiHitBox closeBox;
    /** What the grip answers on: the strip from the second hairline to the edge. */
    final LostTalesUiHitBox gripBox;
    private final int lockX;
    private final int lockTop;
    private final int closeX;
    private final int closeTop;
    private final int firstDividerX;
    private final int secondDividerX;
    private final int dividerTop;
    private final int gripX;
    private final int gripTop;

    private SubWindowStrip(String label, boolean icon, boolean closable,
                           int textX, int top, int right) {
        this.label = label;
        this.icon = icon;
        this.closable = closable;
        this.textX = textX;
        int textTop = top + TEXT_TOP;
        this.gripX = right - EDGE_GAP - GRIP_WIDTH;
        this.secondDividerX = this.gripX - EDGE_GAP - DIVIDER_WIDTH;
        this.closeX = closable
                ? this.secondDividerX - EDGE_GAP - CLOSE_WIDTH
                : this.secondDividerX + DIVIDER_WIDTH + EDGE_GAP;
        this.firstDividerX = closable
                ? this.closeX - EDGE_GAP - DIVIDER_WIDTH : this.secondDividerX;
        this.lockX = this.firstDividerX - EDGE_GAP - LockAnimation.WIDTH;
        this.gripTop = textTop + WindowStyle.centredBoxTop(GRIP_HEIGHT);
        this.closeTop = textTop + WindowStyle.centredBoxTop(CLOSE_HEIGHT);
        this.dividerTop = textTop + WindowStyle.centredBoxTop(DIVIDER_HEIGHT);
        // The resting padlock is centred like its neighbours; the room its
        // shackle swings in stands above it.
        this.lockTop = textTop
                + WindowStyle.centredBoxTop(LockAnimation.SHUT_HEIGHT)
                - (LockAnimation.HEIGHT - LockAnimation.SHUT_HEIGHT);
        this.lockBox = new LostTalesUiHitBox(this.lockX, this.lockTop,
                LockAnimation.WIDTH, LockAnimation.HEIGHT)
                .grown(TabRow.END_CONTROL_SLACK);
        this.closeBox = closable ? new LostTalesUiHitBox(this.closeX,
                this.closeTop, CLOSE_WIDTH, CLOSE_HEIGHT)
                .grown(TabRow.END_CONTROL_SLACK) : null;
        int gripLeft = this.secondDividerX + DIVIDER_WIDTH;
        this.gripBox = new LostTalesUiHitBox(gripLeft, top, right - gripLeft,
                HEIGHT - 1);
    }

    /**
     * Lays the strip out across {@code left} to {@code right} from
     * {@code top}, whole pixels: the icon when {@code icon}, the name cut
     * to the room the controls leave it, and the controls, the cross
     * among them while {@code closable}.
     */
    static SubWindowStrip layOut(FontRenderer font, int left, int right,
                                 int top, String title, boolean icon,
                                 boolean closable) {
        int textX = left + EDGE_GAP
                + (icon ? TabIcons.SLOT + TabIcons.GAP : 0);
        int room = Math.max(0, right - controlsWidth(closable) - NAME_GAP
                - textX);
        String shown = font.getStringWidth(title) <= room ? title
                : LostTalesSkyrimUiStyle.trimToWidth(font, title, room);
        return new SubWindowStrip(shown, icon, closable, textX, top, right);
    }

    private static int controlsWidth(boolean closable) {
        return closable ? CONTROLS_WIDTH : CROSSLESS_CONTROLS_WIDTH;
    }

    /**
     * How wide a strip must be to show {@code title} whole beside its
     * controls, and its icon when {@code icon}: what a window opening at
     * its content's own size is at least as wide as.
     */
    static int widthFor(FontRenderer font, String title, boolean icon) {
        return EDGE_GAP + (icon ? TabIcons.SLOT + TabIcons.GAP : 0)
                + font.getStringWidth(title) + NAME_GAP + CONTROLS_WIDTH;
    }

    /** The narrowest strip: its icon, a few letters, and the controls. */
    static int minWidth() {
        return EDGE_GAP + TabIcons.SLOT + TabIcons.GAP + FEW_LETTERS
                + NAME_GAP + CONTROLS_WIDTH;
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
     * The icon, the name in ivory, and the controls: the padlock turned to
     * {@code locked} and lit while {@code lockLit}, the cross, the grip
     * lit by {@code gripFade}, and the hairlines between them.
     */
    void drawContent(FontRenderer font, int left, int top,
                     LostTalesUiSheet iconGlyph, SubWindow window,
                     boolean lockLit, int alpha) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        int textTop = top + TEXT_TOP;
        LostTalesUiInk.beginContent();
        if (this.icon && iconGlyph != null) {
            iconGlyph.drawWithShadow(left + EDGE_GAP
                            + LostTalesUiInk.centredStart(TabIcons.SIZE,
                                    iconGlyph.getWidth()),
                    textTop + WindowStyle.centredBoxTop(iconGlyph.getHeight()),
                    alpha);
        }
        LostTalesUiInk.drawText(font, this.label, this.textX, textTop,
                LostTalesUiInk.IVORY, alpha);
        // The padlock's button beat lifts and springs it whole, so the
        // turn of its shackle stays its own.
        LostTalesUiButton.beginPose(window.lockMotion, this.lockX,
                this.lockTop, LockAnimation.WIDTH, LockAnimation.HEIGHT);
        try {
            window.lock.draw(this.lockX, this.lockTop, window.isLocked(),
                    lockLit, alpha);
        } finally {
            LostTalesUiButton.endPose();
        }
        int divider = Math.round(WindowStyle.DIVIDER_ALPHA * alpha / 255.0F);
        if (this.closable) {
            WindowStyle.drawDivider(this.firstDividerX, this.dividerTop,
                    DIVIDER_HEIGHT, divider);
            LostTalesUiButton.drawGlyph(LostTalesUiSheet.CLOSE,
                    LostTalesUiSheet.CLOSE_HOVER, window.closeMotion,
                    this.closeX, this.closeTop, alpha);
        }
        WindowStyle.drawDivider(this.secondDividerX, this.dividerTop,
                DIVIDER_HEIGHT, divider);
        // A locked sub-window's grip moves nothing: greyed, as the
        // window's own grip is.
        LostTalesUiSheet.drawPairWithShadow(LostTalesUiSheet.GRIP,
                LostTalesUiSheet.GRIP_HOVER, window.gripFade, this.gripX,
                this.gripTop, window.isLocked()
                        ? Math.round(alpha * WindowStyle.UNAVAILABLE_OPACITY)
                        : alpha);
    }
}
