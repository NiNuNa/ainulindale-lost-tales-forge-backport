package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiLayerFade;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import java.util.HashMap;
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
                                  WindowFrame frame, OtherPage page,
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
        // A split shows its other side beside the page in front.
        WindowSplit split = window.shownSplit();
        if (shows(split, page)) {
            frame.showSplit(split, page, split.other(page));
        }
        // What lies behind the window is cut away, then the world under
        // it softened, over the box it shows and its frame's ring.
        LostTalesUiHitBox shown = frame.drawnBox();
        cutBehind(shown, opening.getOpacity());
        softenBehind(shown, opening.getOpacity());
        beginStackFade(minecraft, frame, shown);
    }

    /** What a window others lie over is drawn into, laid back over the world at its strength. */
    private static final LostTalesUiLayerFade STACK_FADE = new LostTalesUiLayerFade();
    /** Whether a window's fade is open, waiting for {@link #endStackFade}. */
    private static boolean stackFading;

    /**
     * Starts fading a window others lie over as one picture, once it
     * stands on the world it cut itself onto in {@code box}: everything it
     * draws from here on, its frame's ring and its bar included, shows at
     * its strength over that world when {@link #endStackFade} lays the
     * world back. Nothing for a window in front.
     */
    public static void beginStackFade(Minecraft minecraft, WindowFrame frame,
                                      LostTalesUiHitBox box) {
        if (stackFading || frame == null || box == null
                || frame.stackShare >= 1.0F) {
            return;
        }
        int ring = WindowPlacement.FRAME_WIDTH + LostTalesUiInk.SHADOW_OFFSET;
        stackFading = STACK_FADE.begin(minecraft, box.left - ring,
                box.top - ring, box.width + 2 * ring, box.height + 2 * ring);
    }

    /** Ends a window's fade, if one is open: the world is laid back at what the window does not show. */
    static void endStackFade(Minecraft minecraft, WindowFrame frame) {
        if (stackFading) {
            stackFading = false;
            STACK_FADE.end(minecraft, frame == null ? 1.0F : frame.stackShare);
        }
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
            clearDepth(box);
        }
    }

    /**
     * Clears the depth under a box and its frame's ring, so nothing an
     * earlier window drew there with a depth (a flat picture, an item)
     * holds back what this one draws.
     */
    private static void clearDepth(LostTalesUiHitBox box) {
        float ring = WindowPlacement.FRAME_WIDTH;
        boolean clipped = LostTalesUiClip.beginLocal(Minecraft.getMinecraft(),
                (float)box.left - ring, (float)box.top - ring,
                (float)(box.left + box.width) + ring,
                (float)(box.top + box.height) + ring);
        if (!clipped) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDepthMask(true);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL11.glPopAttrib();
            LostTalesUiClip.end(true);
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
                            List<WindowPage> tabs) {
        TabRow.Row row = new TabRow.Row();
        row.tabs = tabs;
        row.selected = WindowFrame.activeTab(window, tabs);
        // The split the window draws, its other page standing forward
        // with the one in front.
        row.splitPartner = frame.splitOther != null && tabs.contains(frame.splitOther)
                ? frame.splitOther : null;
        // Every split the row shows takes one tab's room, in front or not.
        for (WindowSplit split : window.splits()) {
            if (tabs.contains(split.first()) && tabs.contains(split.second())) {
                if (row.splitPairs.isEmpty()) {
                    row.splitPairs = new HashMap<WindowPage, WindowPage>();
                }
                row.splitPairs.put(split.first(), split.second());
                row.splitPairs.put(split.second(), split.first());
            }
        }
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
     * on, and in a split the other page beside it with the divider
     * between. The pointer is the screen's, or away where the page is
     * not under it, for each page; {@code depthTest} is whether depth
     * testing was on as the frame began, for a page drawn as a screen of
     * its own.
     */
    static void drawPage(Minecraft minecraft, WindowFrame frame,
                         LostTalesGuiAnimationSample shown, double pointerX,
                         double pointerY, double splitPointerX,
                         double splitPointerY, float partialTicks,
                         boolean depthTest) {
        drawPageSurface(minecraft, frame, shown);
        drawPageIn(minecraft, WindowPages.contentOf(frame.page), pageBox(frame),
                contentBox(frame),
                shown, pointerX, pointerY, partialTicks, depthTest);
        drawSplitPage(minecraft, frame, shown, splitPointerX, splitPointerY,
                partialTicks, depthTest);
    }

    /**
     * The split's other page, beside what is in front, with the divider
     * between; the divider alone while the other side is a conversation,
     * which its own part draws. Nothing while there is no split.
     */
    public static void drawSplitPage(Minecraft minecraft, WindowFrame frame,
                                     LostTalesGuiAnimationSample shown,
                                     double pointerX, double pointerY,
                                     float partialTicks, boolean depthTest) {
        if (frame.split == null) {
            return;
        }
        if (frame.splitPage != null) {
            if (frame.page == null) {
                // Beside a conversation the page lies on a surface of its
                // own; beside a page it shares the window's.
                LostTalesUiHitBox box = splitPageBox(frame);
                LostTalesUiInk.fillRect((float)box.left, (float)box.top,
                        (float)(box.left + box.width), (float)(box.top + box.height),
                        WindowStyle.insetArgb(shown.getOpacity()
                                * WindowStyle.opacity(minecraft)));
            }
            drawPageIn(minecraft, WindowPages.contentOf(frame.splitPage),
                    splitPageBox(frame), contentBox(frame), shown, pointerX, pointerY,
                    partialTicks, depthTest);
        }
        drawDivider(minecraft, frame, shown);
    }

    /** One page in its box, on whole pixels moved by the fraction the box stands on. */
    private static void drawPageIn(Minecraft minecraft, PageContent content,
                                   LostTalesUiHitBox exact,
                                   LostTalesUiHitBox room,
                                   LostTalesGuiAnimationSample shown,
                                   double pointerX, double pointerY,
                                   float partialTicks, boolean depthTest) {
        if (content == null || exact == null) {
            return;
        }
        LostTalesUiHitBox whole = wholeBox(exact);
        float fractionX = (float)(exact.left - whole.left);
        float fractionY = (float)(exact.top - whole.top);
        boolean depth = depthTest && content.wantsDepthTest();
        GL11.glPushMatrix();
        if (depth) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        // A side of a split that meets the divider has no ring to light.
        WindowLists.framedSides(exact.left <= room.left + 0.5D,
                exact.left + exact.width >= room.left + room.width - 0.5D);
        try {
            GL11.glTranslatef(fractionX, fractionY, 0.0F);
            content.draw(minecraft, whole, exact.left, exact.top,
                    Double.isNaN(pointerX) ? pointerX : pointerX - fractionX,
                    Double.isNaN(pointerY) ? pointerY : pointerY - fractionY,
                    partialTicks, Math.round(255.0F * shown.getOpacity()));
        } finally {
            WindowLists.framedSides(true, true);
            if (depth) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            GL11.glPopMatrix();
        }
    }

    /**
     * The room pages take in a page window as drawn this frame: the
     * window's width from under its tool strip down to its foot's rule,
     * laid on the display's grid.
     */
    public static LostTalesUiHitBox contentBox(WindowFrame frame) {
        double left = frame.drawnLeft();
        double top = LostTalesDisplayPixels.snap(frame.historyTop());
        double bottom = frame.footTop();
        return new LostTalesUiHitBox(left, top, frame.boxRight - frame.boxLeft,
                Math.max(0.0D, bottom - top));
    }

    /** The box of what is in front: the whole room, or its side of the split shown. */
    public static LostTalesUiHitBox pageBox(WindowFrame frame) {
        return frame.split == null ? contentBox(frame) : sideBox(frame, frame.splitFront);
    }

    /** The box of the split's other page; null while the other side is no page, or there is no split. */
    public static LostTalesUiHitBox splitPageBox(WindowFrame frame) {
        return frame.splitPage == null ? null : sideBox(frame, frame.splitPage);
    }

    /** The box of the split's other side, a page or a conversation; null while there is no split. */
    public static LostTalesUiHitBox otherSideBox(WindowFrame frame) {
        return frame.splitOther == null ? null : sideBox(frame, frame.splitOther);
    }

    /** Whether a window shows {@code split} with {@code front} in front: its other side can be seen. */
    public static boolean shows(WindowSplit split, WindowPage front) {
        WindowPage other = split == null ? null : split.other(front);
        return other != null && other.isAvailable() && WindowView.isShown(other);
    }

    /** The box of whichever side shows {@code page}; null for a page the window does not show. */
    public static LostTalesUiHitBox boxOfPage(WindowFrame frame, OtherPage page) {
        if (page == null) {
            return null;
        }
        if (page.equals(frame.page)) {
            return pageBox(frame);
        }
        return page.equals(frame.splitPage) ? splitPageBox(frame) : null;
    }

    /** The divider between a split's sides, the band it is dragged by; null while the window shows one page. */
    public static LostTalesUiHitBox dividerBox(WindowFrame frame) {
        if (frame.split == null) {
            return null;
        }
        LostTalesUiHitBox room = contentBox(frame);
        if (frame.split.isStacked()) {
            double at = frame.split.dividerAt(room.top, room.top + room.height);
            return new LostTalesUiHitBox(room.left, at, room.width, WindowSplit.DIVIDER);
        }
        double at = frame.split.dividerAt(room.left, room.left + room.width);
        return new LostTalesUiHitBox(at, room.top, WindowSplit.DIVIDER, room.height);
    }

    private static LostTalesUiHitBox sideBox(WindowFrame frame, WindowPage side) {
        LostTalesUiHitBox room = contentBox(frame);
        double[] box = frame.split.box(side.equals(frame.split.first()), room.left,
                room.top, room.left + room.width, room.top + room.height);
        return new LostTalesUiHitBox(box[0], box[1], box[2] - box[0], box[3] - box[1]);
    }

    /** A box on whole pixels; what stands in it is drawn off them by the fraction the box stands on. */
    public static LostTalesUiHitBox wholeBox(LostTalesUiHitBox exact) {
        return new LostTalesUiHitBox(Math.floor(exact.left),
                Math.floor(exact.top), exact.width, exact.height);
    }

    /** The divider between a split's sides: one rule along its middle, in the colour of the window's rules. */
    private static void drawDivider(Minecraft minecraft, WindowFrame frame,
                                    LostTalesGuiAnimationSample shown) {
        LostTalesUiHitBox divider = dividerBox(frame);
        if (divider == null) {
            return;
        }
        int alpha = Math.round(255.0F * shown.getOpacity());
        if (frame.split.isStacked()) {
            float y = (float)(divider.top + WindowSplit.DIVIDER / 2);
            LostTalesUiRules.drawRule((float)divider.left,
                    (float)(divider.left + divider.width), y, y + 1.0F, alpha);
        } else {
            float x = (float)(divider.left + WindowSplit.DIVIDER / 2);
            LostTalesUiRules.drawStandingRule(x, x + 1.0F, (float)divider.top,
                    (float)(divider.top + divider.height), alpha);
        }
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
        LostTalesUiHitBox page = contentBox(frame);
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
                LostTalesUiTheme.secondaryRgb(),
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
