package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import org.lwjgl.opengl.GL11;

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
        frame.advanceFill(ContentView.fillOf(window));
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
        // What lies behind the window is cut away, then the world under
        // it softened, over the box it shows and its frame's ring.
        LostTalesUiHitBox shown = frame.drawnBox();
        cutBehind(shown, opening.getOpacity());
        softenBehind(shown, opening.getOpacity());
    }

    /**
     * Cuts away whatever was drawn behind a window or a sub-window standing
     * in {@code box}, its frame's ring included: the world as it stood
     * before any window is pasted over it first, so no surface ever lies
     * over another and nothing behind shows through.
     */
    public static void cutBehind(LostTalesUiHitBox box, float opacity) {
        if (box != null) {
            LostTalesGuiRegionBlur.getInstance().cutFramedRegion(box.left,
                    box.top, box.left + box.width, box.top + box.height,
                    WindowPlacement.FRAME_WIDTH, opacity);
        }
    }

    /**
     * Softens the world under a window, a sub-window or a snap preview in
     * {@code box}, its frame's ring included, while the windows' blur is
     * on: what every window stands on, after {@link #cutBehind}.
     */
    public static void softenBehind(LostTalesUiHitBox box, float opacity) {
        if (box != null) {
            LostTalesGuiRegionBlur.getInstance().drawFramedRegion(box.left,
                    box.top, box.left + box.width, box.top + box.height,
                    WindowPlacement.FRAME_WIDTH, opacity);
        }
    }

    /**
     * A window's row as it stands this frame, its tabs {@code tabs}: the
     * tab in front, and the whole-pixel geometry of where the window is
     * drawn, motion included, with the fraction past it that is applied
     * when the row is drawn. What the screen adds its own state to, and
     * what a pinned window's row is drawn from while playing.
     */
    static TabRow.Row rowOf(Window window, WindowFrame frame,
                            List<WindowTab> tabs) {
        TabRow.Row row = new TabRow.Row();
        row.tabs = tabs;
        row.selected = WindowFrame.activeTab(window, tabs);
        row.rowBottom = (int)Math.floor(frame.tabRowBottom());
        row.rowBottomExact = frame.tabRowBottom();
        row.fractionX = (float)(frame.drawnLeft()
                - Math.floor(frame.drawnLeft()));
        row.fractionY = (float)(row.rowBottomExact - row.rowBottom);
        row.left = (int)Math.floor(frame.drawnLeft()) + 2;
        row.right = (int)Math.floor(frame.drawnLeft()) + (int)Math.round(
                frame.boxRight - frame.boxLeft) - 2;
        // The edge as it really stands, so the tabs follow a resize by
        // the fraction the edge moves rather than a pixel at a time.
        row.rightExact = Math.floor(frame.drawnLeft())
                + (frame.boxRight - frame.boxLeft) - 2;
        row.offsetX = 0;
        row.locked = window.isLocked();
        return row;
    }

    /**
     * A page window's page: its surface under the row, then the page drawn
     * on whole pixels in a matrix moved by the fraction the window stands
     * on. The pointer is the screen's, or away where the page is not
     * under it; {@code depthTest} is whether depth testing was on as the
     * frame began, for a page drawn as a screen of its own.
     */
    static void drawPage(Minecraft minecraft, WindowFrame frame,
                         LostTalesGuiAnimationSample shown, double pointerX,
                         double pointerY, float partialTicks,
                         boolean depthTest) {
        drawPageSurface(minecraft, frame, shown);
        PageContent content = WindowPages.contentOf(frame.page);
        if (content == null) {
            return;
        }
        LostTalesUiHitBox exact = pageBox(frame);
        LostTalesUiHitBox whole = wholePageBox(frame);
        float fractionX = (float)(exact.left - whole.left);
        float fractionY = (float)(exact.top - whole.top);
        boolean depth = depthTest && content.wantsDepthTest();
        GL11.glPushMatrix();
        if (depth) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        try {
            GL11.glTranslatef(fractionX, fractionY, 0.0F);
            content.draw(minecraft, whole, exact.left, exact.top,
                    Double.isNaN(pointerX) ? pointerX : pointerX - fractionX,
                    Double.isNaN(pointerY) ? pointerY : pointerY - fractionY,
                    partialTicks, Math.round(255.0F * shown.getOpacity()));
        } finally {
            if (depth) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            GL11.glPopMatrix();
        }
    }

    /**
     * The page's box in a page window as drawn this frame: the window's
     * width from under its tool strip down to its foot's rule, laid on
     * the display's grid.
     */
    public static LostTalesUiHitBox pageBox(WindowFrame frame) {
        double left = frame.drawnLeft();
        double top = LostTalesDisplayPixels.snap(frame.historyTop());
        double bottom = frame.footTop();
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
     * frame's ring beside it: the sub-windows' surface, the inset plum
     * black thinned by the windows' opacity. The bar carries the ring
     * beside and under itself.
     */
    public static void drawPageSurface(Minecraft minecraft, WindowFrame frame,
                                       LostTalesGuiAnimationSample opening) {
        if (minecraft == null || frame == null || !frame.drawn
                || opening == null) {
            return;
        }
        LostTalesUiHitBox page = pageBox(frame);
        float left = (float)page.left;
        float right = (float)(page.left + page.width);
        float top = (float)page.top;
        float bottom = (float)(page.top + page.height);
        int surface = WindowStyle.insetArgb(opening.getOpacity()
                * WindowStyle.opacity(minecraft));
        frame.contentEdge.first(surface);
        frame.contentShade.clear();
        LostTalesUiInk.fillRect(left, top, right, bottom, surface);
        fillFrameSides(left, right, top, bottom, surface, surface);
    }

    /**
     * The window's bottom hairline over its bar strip, drawn after the bar
     * so it lies over the edge shade: one GUI pixel tall exactly like the
     * top rule — the bar strip's first row, mirroring the tab strip whose
     * last row is the top rule. Behind it the row wears the bar's surface,
     * the colour it touches, so the bar runs up under the rule.
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
     * strips — each stretch continuing exactly what it runs beside, its
     * colour and its strength, as that was drawn this frame: the tab
     * strip's over and beside the strip, the tool strip's beside it and
     * beside the rows the two rules around it stand on. The frame's edge
     * drawn over its inner pixel lies on it as a framed button's frame
     * lies on its surface. What the window holds draws its own stretch
     * below.
     */
    public static void drawFrameSurface(Minecraft minecraft,
                                        WindowFrame frame,
                                        LostTalesGuiAnimationSample opening) {
        if (minecraft == null || frame == null || !frame.drawn
                || opening == null) {
            return;
        }
        int ring = WindowPlacement.FRAME_WIDTH;
        int beside = frame.tabBar.drawnStripArgb;
        int besideTools = frame.tabBar.drawnToolArgb;
        float left = (float)frame.drawnLeft();
        float right = left + (float)(frame.boxRight - frame.boxLeft);
        float top = (float)(frame.boxTop + frame.motionY);
        float stripRule = (float)frame.tabRowBottom() - 1.0F;
        float historyTop = (float)frame.historyTop();
        // The ring's outermost corner pixels lie outside the frame's
        // rounding, as a framed button's footprint corners do.
        LostTalesUiInk.fillRect(left - ring + 1, top - ring, right + ring - 1,
                top - ring + 1, beside);
        LostTalesUiInk.fillRect(left - ring, top - ring + 1, right + ring, top,
                beside);
        fillFrameSides(left, right, top, stripRule, beside, beside);
        fillFrameSides(left, right, stripRule, historyTop, besideTools,
                besideTools);
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
     * ({@link LostTalesUiWindowFrame#drawEdgesBelow}), so they stand
     * wherever the bar does.
     */
    public static void drawFrameEdges(Minecraft minecraft, WindowFrame frame,
                                      LostTalesGuiAnimationSample opening) {
        if (minecraft == null || frame == null || !frame.drawn
                || opening == null || frame.isFilledByPage()) {
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

    /**
     * What a window its page fills ({@link ContentView}) is cut to while
     * it is drawn: the box it shows, and its frame's ring beside it, so
     * the ring's sides are drawn as they always are, in the surface each
     * stretch runs beside. Null for a window shown whole.
     */
    public static LostTalesUiHitBox filledCut(WindowFrame frame) {
        if (frame == null || !frame.isFilledByPage()) {
            return null;
        }
        LostTalesUiHitBox box = frame.drawnBox();
        int ring = WindowPlacement.FRAME_WIDTH;
        return new LostTalesUiHitBox(box.left - ring, box.top,
                box.width + 2 * ring, box.height);
    }

    /**
     * The rest of the frame round a window its page fills: the ring's rows
     * over and under the box it shows, continuing exactly what touches
     * that edge — the tab strip, the tool strip or the bar while they slide
     * out past it, else what the page holds there, stretch by stretch (a
     * conversation's timestamp area, its panel thinning out as it does,
     * its member list) — softened as the window is, and the frame's edges
     * round the box. The window draws none of its own edges while its page
     * fills it.
     */
    public static void drawFilledRing(Minecraft minecraft, WindowFrame frame,
                                      LostTalesGuiAnimationSample opening) {
        if (minecraft == null || frame == null || !frame.drawn
                || opening == null || !frame.isFilledByPage()) {
            return;
        }
        LostTalesUiHitBox box = frame.drawnBox();
        int ring = WindowPlacement.FRAME_WIDTH;
        float left = (float)box.left;
        float right = (float)(box.left + box.width);
        float top = (float)box.top;
        float bottom = (float)(box.top + box.height);
        LostTalesGuiRegionBlur blur = LostTalesGuiRegionBlur.getInstance();
        blur.drawFramedBand(left - ring, top - ring, right + ring, top,
                top - ring, bottom + ring, null, opening.getOpacity());
        blur.drawFramedBand(left - ring, bottom, right + ring, bottom + ring,
                top - ring, bottom + ring, null, opening.getOpacity());
        // The ring's outermost corner pixels lie outside the frame's
        // rounding, as a framed button's footprint corners do.
        fillRingRow(frame, top, left - ring + 1, right + ring - 1,
                top - ring, top - ring + 1);
        fillRingRow(frame, top, left - ring, right + ring, top - ring + 1,
                top);
        fillRingRow(frame, bottom - 1.0F, left - ring, right + ring, bottom,
                bottom + ring - 1);
        fillRingRow(frame, bottom - 1.0F, left - ring + 1, right + ring - 1,
                bottom + ring - 1, bottom + ring);
        LostTalesUiWindowFrame.drawEdges(left, top, right, bottom,
                Math.round(255.0F * opening.getOpacity()));
    }

    /**
     * One row of the ring from {@code from} to {@code to}, over or under
     * the window's row at {@code y}, continuing what that row wears there:
     * the strips' one surface, or what the window holds stretch by
     * stretch, with the shades that lie over it at its edges.
     */
    private static void fillRingRow(WindowFrame frame, float y, float from,
                                    float to, float top, float bottom) {
        if (y < frame.tabRowBottom() - 1.0D) {
            LostTalesUiInk.fillRect(from, top, to, bottom,
                    frame.tabBar.drawnStripArgb);
            return;
        }
        if (y < frame.historyTop() || y >= frame.footTop()
                || frame.contentEdge.isEmpty()) {
            LostTalesUiInk.fillRect(from, top, to, bottom,
                    frame.tabBar.drawnToolArgb);
            return;
        }
        frame.contentEdge.fill(from, to, top, bottom);
        frame.contentShade.fill(from, to, top, bottom);
    }
}
