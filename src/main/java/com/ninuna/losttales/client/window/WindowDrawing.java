package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;

/**
 * How a window's own parts are drawn, whatever it holds: the surface its
 * frame lies on round the strips, the frame's edges, the bottom rule over
 * a bar strip, and a page window — laid out, its surface and its edges.
 * What a window holds draws itself between them.
 */
public final class WindowDrawing {
    private WindowDrawing() {}

    /**
     * A window with a page in front holds no lines: it is laid out as any
     * window is, on its own rectangle of the blurred frame, and the page
     * takes everything under its tool strip. The screen draws the page
     * itself ({@link #drawPageSurface}).
     */
    public static void layOutPage(Minecraft minecraft, Window window,
                                  WindowFrame frame, PageTab page,
                                  int screenWidth, int screenHeight,
                                  LostTalesGuiAnimationSample opening) {
        frame.showPage(page);
        frame.advanceFill(window.getFill());
        WindowPlacement.Box box = WindowPlacement.windowBounds(
                window, minecraft, screenWidth, screenHeight);
        GuiNewChat chat = WindowPlacement.chat(minecraft);
        frame.begin(box, chat == null ? 1.0F : chat.func_146244_h(),
                opening.getTranslationX(), opening.getTranslationY());
        // The row hangs from the window's top and the tool strip from the
        // row; the stack's top stands where a conversation's would, so
        // everything measured from it finds the row and the strip where
        // they are.
        frame.setStackTop(frame.boxTop + frame.motionY
                + TabRow.ROW_HEIGHT
                + WindowPlacement.TOOL_STRIP_HEIGHT
                + WindowPlacement.HISTORY_TOP_MARGIN);
        frame.drawn = true;
        int ring = WindowPlacement.FRAME_WIDTH;
        LostTalesGuiRegionBlur.getInstance().drawRegion(
                frame.drawnLeft() - ring, frame.boxTop + frame.motionY - ring,
                frame.drawnLeft() + (frame.boxRight - frame.boxLeft) + ring,
                frame.boxBottom + frame.motionY + ring, opening.getOpacity());
    }

    /**
     * The page's box in a page window as drawn this frame: the window's
     * width under its tool strip, or right under its row for a page with
     * none, down to its foot, laid on the display's grid.
     */
    public static LostTalesUiHitBox pageBox(WindowFrame frame) {
        double left = frame.drawnLeft();
        double top = LostTalesDisplayPixels.snap(hasToolStrip(frame)
                ? frame.historyTop() : frame.tabRowBottom());
        double bottom = LostTalesDisplayPixels.snap(
                frame.boxBottom + frame.motionY);
        return new LostTalesUiHitBox(left, top, frame.boxRight - frame.boxLeft,
                Math.max(0.0D, bottom - top));
    }

    /** The page box in whole pixels; the page is drawn off them by the fraction the window stands on. */
    public static LostTalesUiHitBox wholePageBox(WindowFrame frame) {
        LostTalesUiHitBox exact = pageBox(frame);
        return new LostTalesUiHitBox(Math.floor(exact.left),
                Math.floor(exact.top), exact.width, exact.height);
    }

    /**
     * A page window's surface under its tool strip, in one layer with the
     * frame's ring beside and under it: the sub-windows' surface, the
     * inset plum black thinned by the windows' opacity.
     */
    public static void drawPageSurface(Minecraft minecraft, WindowFrame frame,
                                       LostTalesGuiAnimationSample opening) {
        if (minecraft == null || frame == null || !frame.drawn
                || opening == null) {
            return;
        }
        LostTalesUiHitBox page = pageBox(frame);
        int ring = WindowPlacement.FRAME_WIDTH;
        float left = (float)page.left;
        float right = (float)(page.left + page.width);
        float top = (float)page.top;
        float bottom = (float)(page.top + page.height);
        int surface = WindowStyle.insetArgb(opening.getOpacity()
                * WindowStyle.opacity(minecraft));
        LostTalesUiInk.fillRect(left, top, right, bottom, surface);
        fillFrameSides(left, right, top, bottom, surface, surface);
        LostTalesUiInk.fillRect(left - ring, bottom, right + ring,
                bottom + ring - 1, surface);
        LostTalesUiInk.fillRect(left - ring + 1, bottom + ring - 1,
                right + ring - 1, bottom + ring, surface);
    }

    /** A page window's frame edges all round: it has no bar to carry the lower ones. */
    public static void drawPageFrameEdges(Minecraft minecraft,
                                          WindowFrame frame,
                                          LostTalesGuiAnimationSample opening) {
        if (minecraft == null || frame == null || !frame.drawn
                || opening == null) {
            return;
        }
        float left = (float)frame.drawnLeft();
        LostTalesUiWindowFrame.drawEdges(left,
                (float)(frame.boxTop + frame.motionY),
                left + (float)(frame.boxRight - frame.boxLeft),
                (float)(frame.boxBottom + frame.motionY),
                Math.round(255.0F * opening.getOpacity()));
    }

    /**
     * The window's bottom hairline over its bar strip, drawn after the bar
     * so it lies over the edge shade: one GUI pixel tall exactly like the
     * top rule — the bar strip's first row, mirroring the tab strip whose
     * last row is the top rule. Behind it the row wears the bar's surface,
     * the colour it touches (Nils), so the bar runs up under the rule.
     * Between the baseline and this row lies the window's trailing strip,
     * one line of always visible room.
     */
    public static void drawBottomRule(Minecraft minecraft, WindowFrame frame,
                                      LostTalesGuiAnimationSample opening) {
        if (minecraft == null || frame == null || !frame.drawn
                || opening == null) {
            return;
        }
        // The window's own width: the rule is the same rule the strip
        // draws along the top, so it ends where that ends.
        float left = (float)frame.drawnLeft();
        float right = left + (float)(frame.boxRight - frame.boxLeft);
        float top = (float)frame.drawnBaseline()
                + WindowPlacement.lineHeight(minecraft);
        LostTalesUiInk.fillRect(left, top, right, top + 1.0F, LostTalesUiInk.argb(
                LostTalesUiInk.SURFACE_HIGHLIGHT_RGB,
                Math.round(WindowStyle.INSET_ALPHA
                        * WindowStyle.opacity(minecraft)
                        * opening.getOpacity())));
        LostTalesUiRules.drawRule(left, right, top, top + 1.0F,
                Math.round(255.0F * opening.getOpacity()));
    }

    /**
     * The surface the window's frame lies on over the tab strip and the
     * tool strip: a ring a frame wide just outside the window's box — the
     * rows over it, corners included, and the columns beside the two
     * strips — each stretch in the colour of what it runs beside, as that
     * was drawn this frame (Nils): the tab strip's over and beside the
     * strip, the tool strip's beside it and beside the rows the two rules
     * around it stand on. The frame's edge drawn over its inner pixel lies
     * on it as a framed button's frame lies on its surface. What the
     * window holds draws its own stretch below.
     */
    public static void drawFrameSurface(Minecraft minecraft,
                                        WindowFrame frame,
                                        LostTalesGuiAnimationSample opening) {
        if (minecraft == null || frame == null || !frame.drawn
                || opening == null) {
            return;
        }
        TabRow strip = frame.tabBar;
        int ring = WindowPlacement.FRAME_WIDTH;
        float left = (float)frame.drawnLeft();
        float right = left + (float)(frame.boxRight - frame.boxLeft);
        float top = (float)(frame.boxTop + frame.motionY);
        float stripRule = (float)frame.tabRowBottom() - 1.0F;
        float historyTop = (float)frame.historyTop();
        // The ring's outermost corner pixels lie outside the frame's
        // rounding, as a framed button's footprint corners do.
        LostTalesUiInk.fillRect(left - ring + 1, top - ring, right + ring - 1,
                top - ring + 1, strip.drawnStripArgb);
        LostTalesUiInk.fillRect(left - ring, top - ring + 1, right + ring, top,
                strip.drawnStripArgb);
        fillFrameSides(left, right, top, stripRule, strip.drawnStripArgb,
                strip.drawnStripArgb);
        if (hasToolStrip(frame)) {
            fillFrameSides(left, right, stripRule, historyTop,
                    strip.drawnToolArgb, strip.drawnToolArgb);
        }
    }

    /** Whether the window shows a tool strip: every window but one whose page has none. */
    private static boolean hasToolStrip(WindowFrame frame) {
        return frame.page == null || frame.page.hasToolStrip();
    }

    /**
     * The frame's surface beside the window's box from {@code from} to
     * {@code to}: {@code leftArgb} a frame wide left of {@code left},
     * {@code rightArgb} right of {@code right}.
     */
    private static void fillFrameSides(float left, float right, float from,
                                       float to, int leftArgb,
                                       int rightArgb) {
        int ring = WindowPlacement.FRAME_WIDTH;
        LostTalesUiInk.fillRect(left - ring, from, left, to, leftArgb);
        LostTalesUiInk.fillRect(right, from, right + ring, to, rightArgb);
    }

    /**
     * The window's own part of its frame's edges, down to its bar strip
     * ({@link LostTalesUiWindowFrame#drawEdgesAbove}): the top edge, the
     * two top corners and the side edges' stretches above the bar. The
     * bar draws the rest with itself
     * ({@link LostTalesUiWindowFrame#drawEdgesBelow}), so it rides the
     * bar's entrance.
     */
    public static void drawFrameEdges(Minecraft minecraft, WindowFrame frame,
                                      LostTalesGuiAnimationSample opening) {
        if (minecraft == null || frame == null || !frame.drawn
                || opening == null) {
            return;
        }
        float left = (float)frame.drawnLeft();
        LostTalesUiWindowFrame.drawEdgesAbove(left,
                (float)(frame.boxTop + frame.motionY),
                left + (float)(frame.boxRight - frame.boxLeft),
                (float)(frame.boxBottom + frame.motionY),
                (float)frame.barTop(),
                Math.round(255.0F * opening.getOpacity()));
    }
}
