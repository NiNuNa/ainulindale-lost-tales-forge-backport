package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.OptionGlyph;
import com.ninuna.losttales.client.window.TabRow;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WordButton;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * The map's question: a shaded map, and a popup in the window frame with
 * the question, the reason it cannot be answered yes where there is one,
 * and three framed word buttons side by side, as a window's question asks
 * it.
 *
 * <p>Both map popups ask the player exactly this shape of question, so the
 * popup, its geometry and its buttons live here and the popups only
 * decide what the three actions mean.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapChoicePrompt {
    private static final int SCREEN_SHADE =
            LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0x66);
    private static final int MAX_WIDTH = 240;
    private static final int SCREEN_MARGIN = 8;
    private static final int FRAME = WindowStyle.POPUP_FRAME;
    /** The menus' clear room round the words and the buttons, across and down. */
    private static final int PADDING_X = MenuWindow.PADDING_X;
    private static final int PADDING_Y = MenuWindow.PADDING_Y;
    /** The words' room: a menu note's two lines and the clear above and under them. */
    private static final int NOTE_LINE = MenuWindow.NOTE_LINE;
    private static final int NOTE_PADDING = MenuWindow.NOTE_PADDING;
    private static final int NOTE_HEIGHT = NOTE_PADDING * 2 + NOTE_LINE * 2;
    private static final int POPUP_HEIGHT = FRAME * 2 + PADDING_Y * 2
            + NOTE_HEIGHT + LostTalesUiFramedButton.HEIGHT;
    /**
     * Gap between the popup and the map point framed beside it, in GUI
     * pixels. Enough for the marker and the name above it to clear the
     * popup, and no more: the point of framing it there is that it reads as
     * part of the question.
     */
    private static final int ANCHOR_CLEARANCE = 26;
    /** Closest the framed point may come to the edge of the screen. */
    private static final int ANCHOR_SCREEN_MARGIN = 14;
    /**
     * The step-to-the-next-place arrows stand outside the popup rather than
     * among its buttons: they move the question rather than answering it,
     * and a player must never reach for one and hit "Yes". Each answers on
     * a window's end-control square, its glyph in the middle.
     */
    private static final int STEP_ARROW_SIZE = TabRow.END_CONTROL_SIZE;
    private static final int STEP_ARROW_GAP = WindowStyle.EDGE_GAP;
    /**
     * The step arrows' glyphs: the windows' chevron laid on its side, as a
     * pattern until it is painted.
     */
    private static final OptionGlyph PREVIOUS_GLYPH = OptionGlyph.pattern(
            "..#", ".##", "###", ".##", "..#");
    private static final OptionGlyph NEXT_GLYPH = OptionGlyph.pattern(
            "#..", "##.", "###", "##.", "#..");

    private LostTalesMapChoicePrompt() {}

    /**
     * Where a map point this popup is about should come to rest: centred just
     * above the popup.
     *
     * <p>Derived from the popup actually laid out at this screen size and GUI
     * scale. Sitting it just clear of the popup rather than halfway to the
     * top of the screen is what makes the marker read as the subject of the
     * question. Only when there is no room above (a short screen, a large
     * GUI scale) does it go below.</p>
     */
    static float focusAnchorY(int screenWidth, int screenHeight) {
        Layout layout = calculateLayout(screenWidth, screenHeight);
        float above = layout.y - ANCHOR_CLEARANCE;
        if (above >= ANCHOR_SCREEN_MARGIN) {
            return above;
        }
        float below = layout.y + layout.height + ANCHOR_CLEARANCE;
        float floor = Math.max(0, screenHeight - ANCHOR_SCREEN_MARGIN);
        return below <= floor ? below
                : Math.max(0.0F, Math.min(floor, above));
    }

    static void renderShadeFixed(
            int screenWidth, int screenHeight, int[] litArea) {
        LostTalesMapPopupAnimation.pushFixed();
        try {
            drawShade(screenWidth, screenHeight, litArea);
        } finally {
            LostTalesMapPopupAnimation.pop();
        }
    }

    /**
     * The popup's surface and frame, the question in ivory and, under it,
     * the reason it cannot be answered yes in the aside tone.
     */
    static void renderPanelContents(
            FontRenderer font, Layout layout,
            String title, String subtitle) {
        WindowStyle.drawPopup(layout.x, layout.y, layout.x + layout.width,
                layout.y + layout.height, 1.0F);
        int left = layout.x + FRAME + PADDING_X;
        int room = Math.max(0, layout.width - (FRAME + PADDING_X) * 2);
        int lineTop = layout.y + FRAME + PADDING_Y + NOTE_PADDING
                + LostTalesUiInk.centredStart(NOTE_LINE,
                        LostTalesUiInk.CAP_HEIGHT);
        LostTalesUiCutName.drawText(font, title == null ? "" : title, left,
                lineTop, room, LostTalesUiInk.IVORY, 255);
        if (subtitle != null && subtitle.length() > 0) {
            LostTalesUiCutName.drawText(font, subtitle, left,
                    lineTop + NOTE_LINE, room, WindowStyle.asideRgb(), 255);
        }
    }

    /**
     * Dims the map behind the popup, leaving one rectangle at full strength.
     *
     * <p>The marker the question is about is inside that rectangle, so it
     * stays as readable as it was on the open map while everything around it
     * steps back. Drawn as the four bands around the opening rather than as a
     * hole punched through, which keeps it to plain rectangles.</p>
     */
    private static void drawShade(
            int screenWidth, int screenHeight, int[] litArea) {
        if (litArea == null || litArea.length < 4
                || litArea[2] <= 0 || litArea[3] <= 0) {
            Gui.drawRect(0, 0, screenWidth, screenHeight, SCREEN_SHADE);
            return;
        }
        int left = Math.max(0, Math.min(screenWidth, litArea[0]));
        int top = Math.max(0, Math.min(screenHeight, litArea[1]));
        int right = Math.max(left,
                Math.min(screenWidth, litArea[0] + litArea[2]));
        int bottom = Math.max(top,
                Math.min(screenHeight, litArea[1] + litArea[3]));
        Gui.drawRect(0, 0, screenWidth, top, SCREEN_SHADE);
        Gui.drawRect(0, bottom, screenWidth, screenHeight, SCREEN_SHADE);
        Gui.drawRect(0, top, left, bottom, SCREEN_SHADE);
        Gui.drawRect(right, top, screenWidth, bottom, SCREEN_SHADE);
    }

    /**
     * One answer: a framed word button, its word centred, red until lit
     * for an answer that ends something, greyed where it cannot be taken.
     */
    static void drawButton(
            FontRenderer font, Bounds bounds, String label,
            boolean hovered, boolean enabled, boolean ending,
            LostTalesUiButtonMotion motion) {
        if (bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        WordButton.draw(font, new LostTalesUiHitBox(bounds.x, bounds.y,
                        bounds.width, bounds.height), label == null ? "" : label,
                ending, enabled, hovered, motion, 255, popupSurfaceAlpha());
    }

    /** How opaque the popup's surface stands, which its buttons' surfaces match. */
    static int popupSurfaceAlpha() {
        return WindowStyle.insetArgb(
                WindowStyle.opacity(Minecraft.getMinecraft())) >>> 24;
    }

    static Layout calculateLayout(int screenWidth, int screenHeight) {
        int width = Math.min(MAX_WIDTH,
                Math.max(0, screenWidth - SCREEN_MARGIN * 2));
        int height = Math.min(POPUP_HEIGHT,
                Math.max(0, screenHeight - SCREEN_MARGIN * 2));
        int x = Math.max(0, LostTalesUiInk.centredStart(screenWidth, width));
        int y = Math.max(0, LostTalesUiInk.centredStart(screenHeight,
                height));
        int contentLeft = x + FRAME + PADDING_X;
        int contentWidth = Math.max(0, width - (FRAME + PADDING_X) * 2);
        int buttonY = Math.max(y, y + height - FRAME - PADDING_Y
                - LostTalesUiFramedButton.HEIGHT);
        int buttonHeight = Math.max(0, Math.min(
                LostTalesUiFramedButton.HEIGHT, y + height - buttonY));
        // Three buttons of one width, a framed button's gap apart; the
        // last takes the pixels the division leaves.
        int gap = WindowStyle.BUTTON_GAP;
        int slot = Math.max(0, (contentWidth - gap * 2) / 3);
        Bounds first = new Bounds(contentLeft, buttonY, slot, buttonHeight);
        Bounds second = new Bounds(first.x + slot + gap, buttonY, slot,
                buttonHeight);
        int thirdX = second.x + slot + gap;
        Bounds third = new Bounds(thirdX, buttonY,
                Math.max(0, contentLeft + contentWidth - thirdX),
                buttonHeight);
        int arrowY = y + LostTalesUiInk.centredStart(height, STEP_ARROW_SIZE);
        // Off the ends of the popup, and only where the screen has room for
        // them; a narrow screen keeps the popup and loses the arrows, which
        // the keys can still do.
        int previousX = x - STEP_ARROW_GAP - STEP_ARROW_SIZE;
        int nextX = x + width + STEP_ARROW_GAP;
        Bounds previous = previousX >= 0
                ? new Bounds(previousX, arrowY,
                        STEP_ARROW_SIZE, STEP_ARROW_SIZE)
                : Bounds.none();
        Bounds next = nextX + STEP_ARROW_SIZE <= screenWidth
                ? new Bounds(nextX, arrowY, STEP_ARROW_SIZE, STEP_ARROW_SIZE)
                : Bounds.none();
        return new Layout(x, y, width, height,
                first, second, third, previous, next);
    }

    /**
     * One of the step arrows: its glyph in the middle of its square, in
     * ivory lighting to the accent under the one shadow, dipping and rising
     * as every pressable glyph does (LOOK-20).
     *
     * @param pointLeft which way the arrow points
     */
    static void drawStepArrow(
            Bounds bounds, boolean pointLeft, boolean hovered,
            LostTalesUiButtonMotion motion) {
        if (bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        motion.advance(System.nanoTime(), hovered);
        OptionGlyph glyph = pointLeft ? PREVIOUS_GLYPH : NEXT_GLYPH;
        int left = bounds.x + LostTalesUiInk.centredStart(bounds.width,
                glyph.width());
        int top = bounds.y + LostTalesUiInk.centredStart(bounds.height,
                glyph.height());
        LostTalesUiButton.beginPose(motion, left, top, glyph.width(),
                glyph.height());
        try {
            glyph.draw(left, top, motion.lit(), 255);
        } finally {
            LostTalesUiButton.endPose();
            LostTalesUiInk.beginContent();
        }
    }

    /** The popup's footprint, the three answers and the two step arrows. */
    static final class Layout {
        final int x;
        final int y;
        final int width;
        final int height;
        final Bounds first;
        final Bounds second;
        final Bounds third;
        final Bounds previous;
        final Bounds next;

        private Layout(int x, int y, int width, int height,
                       Bounds first, Bounds second, Bounds third,
                       Bounds previous, Bounds next) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.first = first;
            this.second = second;
            this.third = third;
            this.previous = previous;
            this.next = next;
        }
    }

    static final class Bounds {
        final int x;
        final int y;
        final int width;
        final int height;

        Bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        /** A control the screen had no room for: drawn nowhere, hit never. */
        static Bounds none() {
            return new Bounds(0, 0, 0, 0);
        }

        boolean contains(int pointX, int pointY) {
            return this.width > 0 && this.height > 0
                    && LostTalesUiHitBox.contains(pointX, pointY, this.x,
                            this.y, this.width, this.height);
        }
    }
}
