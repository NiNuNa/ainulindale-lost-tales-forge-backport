package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * The press-to-drag state machines of the windows: a tab carried along
 * its row, torn off into a window of its own and docked into another; a
 * window moved by its strip, its grip or its tabs, snapped against a
 * neighbour or into a part of the screen; a window resized by an edge
 * or a corner; and whatever a window's content drags itself
 * ({@link ContentDrag}: a conversation's scrollbar, its member list's
 * edge). The screen arms them from its presses, feeds them the pointer
 * every frame and every mouse event, and asks them what is being
 * dragged; they move the layout live and write it down once, on
 * release. A carried window's landing is shown
 * as it is found: the neighbour's edge lit, the snap bar at the top of
 * the screen, and the preview pane in the part of the screen it fills.
 */
public final class WindowGestures {
    /** What the screen the windows stand on does for the drags. */
    public interface Host {
        /** A window's tab row as it is laid out now. */
        TabRow.Row rowFor(Window window, WindowFrame frame,
                          LostTalesGuiAnimationSample opening);

        /** The opening motion the windows are drawn with now. */
        LostTalesGuiAnimationSample opening();

        /** A window was taken hold of: it comes forward and takes the keys. */
        void selectWindow(Window window);

        /** A tab was dropped or picked: it comes in front and takes the keys. */
        void selectTab(WindowTab tab);

        /** A window moved or changed its size: whatever follows it follows. */
        void windowsMoved();
    }

    /**
     * A drag a window's content runs itself — a conversation's scrollbar,
     * its member list's edge. It follows the pointer, in precise GUI
     * pixels, ends when the button comes up, and puts back what it
     * changed on Escape.
     */
    public interface ContentDrag {
        void move(double mouseX, double mouseY);

        void release();

        void cancel();

        /** Whether it counts as dragging, so nothing else answers the pointer meanwhile. */
        boolean holdsPointer();

        /** How the pointer looks while it runs; null for the look of what it is over. */
        LostTalesMapCursor.Pose pose();
    }

    /** Distance from another window's edge at which a drag snaps and links. */
    public static final int LINK_SNAP = 6;

    /** Pointer travel before a press on a tab becomes a drag. */
    public static final int DRAG_THRESHOLD = 4;
    /*
     * Leaving a row and coming back to it are two questions about one
     * pointer, so each gets its own reach: the pull that tears a run
     * out is longer than the one that puts it back, and between the two
     * lies a band where neither happens. The band is wider than the
     * travel that starts a drag at all, so a hand that never holds quite
     * still cannot cross from out to in and back every other frame.
     *
     * Both are the pointer's distance to the strip itself — the bar the
     * player sees, band and ends alike. One object measured against one
     * rectangle: past a corner the two overhangs count as one
     * straight-line pull, so a run carried out diagonally travels the
     * same distance as one carried straight off the strip or straight
     * past its end, and no direction is a cheaper way out than another.
     */

    /** The pull, measured from the strip, that tears a dragged run out
     *  of its row — the same in every direction. Less than a tab row,
     *  so a run comes free before the pointer has crossed a whole row
     *  of the window it is leaving. */
    public static final int DETACH_DISTANCE = 20;
    /** The reach, measured the same way, within which the row a run was
     *  torn out of takes it back. */
    public static final int RETURN_DISTANCE = 12;
    /** How far above or below a row's band a carried run may still be
     *  offered to it. */
    public static final int DOCK_BAND_SLACK = 7;
    /**
     * How long the pointer rests pressed against a screen edge, past the
     * strip, before the tabs come out that way: a row standing against
     * the edge cannot be pulled the whole pull toward it, since the
     * pointer goes no further, and the rest tells a pull from a hand that
     * only brushed the edge while sliding a tab along.
     */
    static final long EDGE_TEAR_NANOS = 150L * 1000000L;
    /** Horizontal slack around a row that still counts as dropping on it. */
    private static final int DOCK_SLACK = 24;
    /** How far outside its edge a window still answers to a resize. */
    static final int RESIZE_BORDER = 4;
    /** How far along an edge from a corner still counts as that corner. */
    static final int RESIZE_CORNER = 12;
    /**
     * How near a screen edge the pointer must come, in GUI pixels, for a
     * dragged window to snap to it: a band along the edge rather than
     * the edge itself, as Windows keeps a snap zone well inside its
     * screen edges, so a drag need not press against the border.
     */
    static final double SNAP_REACH = 16.0D;
    /**
     * The share of a side edge at either end that snaps to the corner's
     * quarter of the screen rather than the edge's half.
     */
    static final double SNAP_CORNER_SHARE = 0.2D;
    /**
     * The share of the top and bottom edges at either end that snaps to
     * the corner's quarter: one column of the twelve the parts of the
     * screen are laid on, the corner itself, as on the desktop, so the
     * top edge beside it is free for the thirds.
     */
    static final double SNAP_EDGE_CORNER_SHARE = 1.0D / 12.0D;

    private final Host host;
    private Minecraft mc;
    private FontRenderer font;
    private int screenWidth;
    private int screenHeight;

    private TabDrag tabDrag;
    private WindowDrag windowDrag;
    private WindowResize windowResize;
    private FillResize fillResize;
    private ContentDrag contentDrag;
    private final SnapPreview snapPreview = new SnapPreview();
    private final SnapLayouts.Bar snapBar = new SnapLayouts.Bar();
    /** What a window let go in a zone of a layout offers the other windows. */
    private final SnapAssist snapAssist;

    public WindowGestures(Host host, SnapAssist snapAssist) {
        this.host = host;
        this.snapAssist = snapAssist;
    }

    /** Called from {@code initGui}, which also runs on every resize. */
    public void bind(Minecraft mc, FontRenderer font, int screenWidth,
              int screenHeight) {
        this.mc = mc;
        this.font = font;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /* ---- What is being dragged ---- */

    public boolean isDragging() {
        return (this.tabDrag != null && this.tabDrag.active)
                || this.windowDrag != null
                || (this.windowResize != null && this.windowResize.active)
                || this.fillResize != null
                || (this.contentDrag != null && this.contentDrag.holdsPointer());
    }

    /** Whether a window's edge is being dragged: its content follows the resize rigidly. */
    public boolean isResizing() {
        return this.windowResize != null && this.windowResize.active;
    }

    /** The drag a window's content is running, or null. */
    public ContentDrag contentDrag() {
        return this.contentDrag;
    }

    /** Starts a drag a window's content runs, at the pointer where it is now. */
    public void startContentDrag(ContentDrag drag) {
        this.contentDrag = drag;
        if (drag != null) {
            drag.move(WindowPlacement.preciseMouseX(this.mc, this.screenWidth),
                    WindowPlacement.preciseMouseY(this.mc, this.screenHeight));
        }
    }

    /** Whether a window drag is carrying this window. */
    public boolean isMovingWindow(String windowId) {
        return this.windowDrag != null
                && windowId.equals(this.windowDrag.windowId);
    }

    /** Whether a live resize is reshaping this window. */
    public boolean isResizingWindow(String windowId) {
        return (this.windowResize != null && this.windowResize.active
                && windowId.equals(this.windowResize.windowId))
                || (this.fillResize != null
                        && this.fillResize.ids.contains(windowId));
    }

    /** The live tab drag, or null while no tab is being carried. */
    public TabDrag activeTabDrag() {
        return this.tabDrag != null && this.tabDrag.active ? this.tabDrag : null;
    }

    /**
     * The edge a resize holds, armed or live, or null. A resize in
     * progress keeps saying so wherever the pointer has gone, the way a
     * pressed control keeps its look.
     */
    public ResizeEdge armedResizeEdge() {
        if (this.fillResize != null) {
            return this.fillResize.edge;
        }
        return this.windowResize == null ? null : this.windowResize.edge;
    }

    /* ---- Per frame ---- */

    /**
     * Follows the pointer with whatever is being dragged. Mouse events
     * reach the screen at tick rate; a live drag is re-read from the
     * pointer every drawn frame so the window and the dock target stay
     * under the cursor. While a resize runs the scroll follows its
     * clamp rigidly instead of gliding after it.
     */
    public void advance(int mouseX, int mouseY) {
        if (this.windowResize != null && this.windowResize.active) {
            updateResize();
        } else if (this.fillResize != null) {
            updateFillResize();
        } else if (this.contentDrag != null) {
            moveContentDrag();
        } else if (this.windowDrag != null) {
            moveDraggedWindow(mouseX, mouseY);
        } else if (this.tabDrag != null && this.tabDrag.active) {
            dragTabs(this.tabDrag, mouseX, mouseY);
        }
    }

    /**
     * The edge a carried window is about to link to, lit along its whole
     * length: the target's frame on that side, both of its pixels, from
     * corner to corner, where the frame is drawn — so the light lies in
     * the gap the two windows will keep, over the frame it stands for,
     * and never on either window's own pixels.
     */
    public void drawLinkHighlight() {
        Landing landing = activeLanding();
        if (landing == null || landing.snapTargetId == null) {
            return;
        }
        WindowFrame target = WindowFrame.find(landing.snapTargetId);
        if (target == null || !target.drawn) {
            return;
        }
        float ring = WindowPlacement.FRAME_WIDTH;
        float left = (float)target.drawnLeft();
        float right = left + (float)(target.boxRight - target.boxLeft);
        float top = (float)(target.boxTop + target.motionY);
        float bottom = (float)(target.boxBottom + target.motionY);
        int colour = LostTalesUiInk.argb(
                WindowStyle.LANDING_RGB, 0xFF);
        switch (landing.snapSide) {
            case ABOVE:
                LostTalesUiInk.fillRect(left - ring, top - ring,
                        right + ring, top, colour);
                return;
            case BELOW:
                LostTalesUiInk.fillRect(left - ring, bottom,
                        right + ring, bottom + ring, colour);
                return;
            case LEFT:
                LostTalesUiInk.fillRect(left - ring, top - ring,
                        left, bottom + ring, colour);
                return;
            default:
                LostTalesUiInk.fillRect(right, top - ring,
                        right + ring, bottom + ring, colour);
        }
    }

    /** The pane showing where a carried window goes, for the screen to draw under it. */
    public SnapPreview snapPreview() {
        return this.snapPreview;
    }

    /** Draws the snap bar where its motion has brought it, at {@code opacity}. */
    public void drawSnapBar(float opacity) {
        this.snapBar.draw(this.mc, this.screenWidth, this.screenHeight,
                opacity);
    }

    /** Where the window being carried right now would land, or null while none is. */
    private Landing activeLanding() {
        if (this.windowDrag != null) {
            return this.windowDrag.landing;
        }
        if (this.tabDrag != null && this.tabDrag.active
                && this.tabDrag.detachedWindowId != null) {
            return this.tabDrag.landing;
        }
        return null;
    }

    /* ---- Mouse events ---- */

    /**
     * A press-and-move with the left button. A resize and a window drag
     * are live from their press; an armed tab drag becomes live once the
     * pointer has travelled the threshold; a live one follows the
     * pointer. True when a drag took the event, so the screen leaves
     * vanilla's own handling alone.
     */
    public boolean onDragMove(int mouseX, int mouseY) {
        if (this.contentDrag != null) {
            moveContentDrag();
            return true;
        }
        if (this.windowResize != null) {
            // Live from the press: an edge follows the pointer from the
            // first pixel, with no travel to overcome first.
            updateResize();
            return true;
        }
        if (this.fillResize != null) {
            updateFillResize();
            return true;
        }
        if (this.windowDrag != null) {
            moveDraggedWindow(mouseX, mouseY);
            return true;
        }
        if (this.tabDrag != null) {
            if (!this.tabDrag.active
                    && travelled(mouseX, mouseY, this.tabDrag.pressX,
                            this.tabDrag.pressY)) {
                this.tabDrag.active = true;
            }
            if (this.tabDrag.active) {
                dragTabs(this.tabDrag, mouseX, mouseY);
            }
            return true;
        }
        return false;
    }

    private static boolean travelled(int mouseX, int mouseY, int pressX,
                                     int pressY) {
        return Math.abs(mouseX - pressX) >= DRAG_THRESHOLD
                || Math.abs(mouseY - pressY) >= DRAG_THRESHOLD;
    }

    /**
     * The left button coming up: a live resize commits, a window drag and
     * a live tab drag write the layout down, a tab press that never
     * travelled collapses the group to the pressed tab.
     */
    public void onRelease() {
        if (this.contentDrag != null) {
            ContentDrag drag = this.contentDrag;
            this.contentDrag = null;
            drag.release();
        }
        if (this.windowResize != null) {
            WindowResize resize = this.windowResize;
            this.windowResize = null;
            if (resize.moved()) {
                commitResize(resize);
            } else {
                this.snapPreview.release(false);
            }
        }
        if (this.fillResize != null) {
            // Where the edge was left is written down, once.
            boolean moved = this.fillResize.moved;
            this.fillResize = null;
            if (moved) {
                WindowLayout.persist();
            }
        }
        if (this.windowDrag != null) {
            WindowDrag drag = this.windowDrag;
            this.windowDrag = null;
            // Touching another window only shows what it would stick to;
            // locking it is what sticks it.
            land(drag.windowId, drag.landing);
            WindowLayout.persist();
        }
        if (this.tabDrag != null) {
            TabDrag drag = this.tabDrag;
            this.tabDrag = null;
            if (drag.active) {
                dropTab(drag);
            } else if (drag.collapsesOnRelease) {
                // Pressed and released without travelling: the press
                // was a pick after all, so the group gives way to it.
                TabSelection.selectOnly(drag.sourceWindowId, drag.tab);
            }
        }
    }

    /**
     * Escape, or the screen closing: a window or tab drag ends where it
     * stands, a resize goes back as it was.
     */
    public void cancelDrags() {
        // Nothing carried lands anywhere: the preview goes back into its
        // window and the snap bar goes up.
        this.snapPreview.release(false);
        this.snapBar.hide();
        if (this.tabDrag != null && this.tabDrag.active) {
            // The tabs are already where the drag left them — in a row
            // they slid into, or in a window of their own — so ending
            // the carry writes that down rather than putting the row
            // back together, and the layout on screen and on disk agree.
            WindowLayout.persist();
        }
        this.tabDrag = null;
        if (this.contentDrag != null) {
            ContentDrag drag = this.contentDrag;
            this.contentDrag = null;
            drag.cancel();
        }
        if (this.windowResize != null) {
            WindowResize resize = this.windowResize;
            this.windowResize = null;
            if (resize.active) {
                // Escape means "as it was": the unpersisted live
                // dimensions give way to the stored ones, and nothing
                // is written.
                restoreResize(resize);
            }
        }
        if (this.fillResize != null) {
            // Every part the edge moved takes back its edges.
            FillResize resize = this.fillResize;
            this.fillResize = null;
            for (int index = 0; index < resize.ids.size(); index++) {
                holdFill(resize.ids.get(index), resize.starts.get(index));
            }
        }
        if (this.windowDrag != null) {
            WindowLayout.persist();
            this.windowDrag = null;
        }
    }

    private void moveContentDrag() {
        this.contentDrag.move(
                WindowPlacement.preciseMouseX(this.mc, this.screenWidth),
                WindowPlacement.preciseMouseY(this.mc, this.screenHeight));
    }

    /* ---- Resizing ---- */

    /**
     * The edge or corner of a window the pointer is on. A window is
     * resized the way any window is: the edges the drag does not touch
     * stay exactly where they are, so the opposite edge is the anchor.
     */
    public enum ResizeEdge {
        LEFT(true, false, true, false),
        RIGHT(true, false, false, false),
        TOP(false, true, false, true),
        BOTTOM(false, true, false, false),
        TOP_LEFT(true, true, true, true),
        TOP_RIGHT(true, true, false, true),
        BOTTOM_LEFT(true, true, true, false),
        BOTTOM_RIGHT(true, true, false, false);

        /** Which sizes this edge carries. */
        final boolean horizontal;
        final boolean vertical;
        /** Which side follows the pointer; the other side is the anchor. */
        final boolean fromLeft;
        final boolean fromTop;

        ResizeEdge(boolean horizontal, boolean vertical, boolean fromLeft,
                   boolean fromTop) {
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.fromLeft = fromLeft;
            this.fromTop = fromTop;
        }

        boolean isCorner() {
            return this.horizontal && this.vertical;
        }
    }

    /** A window and the edge of it the pointer is on. */
    public static final class ResizeTarget {
        public final WindowFrame frame;
        public final ResizeEdge edge;

        ResizeTarget(WindowFrame frame, ResizeEdge edge) {
            this.frame = frame;
            this.edge = edge;
        }
    }

    /**
     * A window being resized by one of its edges or corners. The window
     * keeps its anchors — the edges the drag does not touch — and the
     * window itself follows the pointer: the temporary size is written
     * into the layout without persisting it, so the history reflows and
     * redraws live, the file is written once on release, and Escape
     * puts the remembered dimensions back exactly.
     */
    private static final class WindowResize {
        final String windowId;
        final ResizeEdge edge;
        /** The box the drag started from, in screen pixels. */
        final double startLeft;
        final double startRight;
        final double startTop;
        final double startBottom;
        /** The stored (committed) state to restore when it is cancelled. */
        final double storedHeight;
        final int storedWidth;
        final double storedOffsetX;
        final double storedOffsetY;
        /** Pointer's offset from the edge it took hold of. */
        final double grabX;
        final double grabY;
        final int pressX;
        final int pressY;
        /**
         * A resize is live from the press, so nothing under the pointer
         * has to be overcome before the edge moves.
         */
        final boolean active = true;
        /** The box under the pointer right now. */
        double left;
        double right;
        double top;
        double bottom;
        /** The height the box asks for, fractions included. */
        double height;
        /**
         * The column the window fills if it is let go now — its top edge
         * at the top of the screen or its bottom edge at the bottom — or
         * none.
         */
        Window.ScreenFill column = Window.ScreenFill.NONE;

        WindowResize(String windowId, ResizeEdge edge, double left,
                     double right, double top, double bottom, double grabX,
                     double grabY, int pressX, int pressY, double height,
                     Window window) {
            this.windowId = windowId;
            this.edge = edge;
            this.startLeft = left;
            this.startRight = right;
            this.startTop = top;
            this.startBottom = bottom;
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.grabX = grabX;
            this.grabY = grabY;
            this.pressX = pressX;
            this.pressY = pressY;
            this.height = height;
            this.storedHeight = window.getOwnHeight();
            this.storedWidth = window.getOwnWidth();
            this.storedOffsetX = window.getOffsetX();
            this.storedOffsetY = window.getOffsetY();
        }

        /**
         * Whether the box has left where it started: a press on an edge
         * that is released in place is not a resize, and writes nothing.
         */
        boolean moved() {
            return this.left != this.startLeft || this.right != this.startRight
                    || this.top != this.startTop
                    || this.bottom != this.startBottom;
        }
    }

    /**
     * The window edge a press at this point would take hold of, or null.
     * One classification, in one order: what is drawn above the windows
     * — the pickers, the completion lists, the settings menu, the input
     * bar group — owns the pointer first; the resize border comes next,
     * ahead of the tab strip along the window's top edge;
     * the strip and the windows themselves come last. Hover, the cursor,
     * mouse-down and the drag it starts all ask this same question, so
     * what the pointer shows is what the press does.
     */
    public static ResizeTarget resizeUnderPointer(double mouseX, double mouseY,
                                           PointerRegions regions) {
        if (regions.containsOverlay(mouseX, mouseY)) {
            return null;
        }
        return resizeTargetAt(mouseX, mouseY);
    }

    /**
     * The edge of an unlocked window under the pointer, or null. The
     * band lies just outside the window, where nothing is drawn, so a
     * tab, a control or the input field never loses a click to it; the
     * frontmost window wins where two overlap.
     */
    static ResizeTarget resizeTargetAt(double mouseX, double mouseY) {
        List<WindowFrame> frames = WindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            WindowFrame frame = frames.get(index);
            if (coversPoint(frame, mouseX, mouseY)) {
                // The window in front owns everything inside it, so a
                // window behind it never takes a click through it.
                return null;
            }
            Window window = WindowLayout.window(frame.windowId);
            if (window == null || window.isLocked()) {
                continue;
            }
            // A window filling a part of the screen answers only on the
            // edges it has inside the screen, and only once it stands
            // there: the rest are the screen's.
            ResizeEdge edge = outOfItsOwnBox(window)
                    ? filledEdgeAt(frame, window, mouseX, mouseY)
                    : edgeAt(frame, mouseX, mouseY);
            if (edge != null) {
                return new ResizeTarget(frame, edge);
            }
        }
        return null;
    }

    /**
     * Whether the point lies within the window as it was drawn — its
     * strip, its messages and its bar — rather than on the border
     * outside it: the one test the window's own hover and press ask.
     */
    static boolean coversPoint(WindowFrame frame, double mouseX,
                               double mouseY) {
        return frame.contains(mouseX, mouseY);
    }

    /**
     * Which edge or corner of one window's box a point lies on: the
     * band {@link #RESIZE_BORDER} pixels wide just outside the pixels
     * the window draws, never on them.
     */
    static ResizeEdge edgeAt(WindowFrame frame, double mouseX,
                             double mouseY) {
        double left = frame.drawnLeft();
        double right = left + (frame.boxRight - frame.boxLeft);
        double top = frame.boxTop + frame.motionY;
        double bottom = frame.boxBottom + frame.motionY;
        if (!new LostTalesUiHitBox(left, top, right - left, bottom - top)
                .grown(RESIZE_BORDER).contains(mouseX, mouseY)) {
            return null;
        }
        boolean onLeft = mouseX < left;
        boolean onRight = mouseX >= right;
        boolean onTop = mouseY < top;
        boolean onBottom = mouseY >= bottom;
        if (!onLeft && !onRight && !onTop && !onBottom) {
            // Inside the window: its tabs, messages and bar own this.
            return null;
        }
        // A corner reaches a little way along both of its edges, so it
        // is as easy to catch as it is in any window manager.
        boolean nearLeft = mouseX <= left + RESIZE_CORNER;
        boolean nearRight = mouseX >= right - RESIZE_CORNER;
        boolean nearTop = mouseY <= top + RESIZE_CORNER;
        boolean nearBottom = mouseY >= bottom - RESIZE_CORNER;
        if ((onTop || onLeft) && nearTop && nearLeft) {
            return ResizeEdge.TOP_LEFT;
        }
        if ((onTop || onRight) && nearTop && nearRight) {
            return ResizeEdge.TOP_RIGHT;
        }
        if ((onBottom || onLeft) && nearBottom && nearLeft) {
            return ResizeEdge.BOTTOM_LEFT;
        }
        if ((onBottom || onRight) && nearBottom && nearRight) {
            return ResizeEdge.BOTTOM_RIGHT;
        }
        if (onLeft) {
            return ResizeEdge.LEFT;
        }
        if (onRight) {
            return ResizeEdge.RIGHT;
        }
        return onTop ? ResizeEdge.TOP : ResizeEdge.BOTTOM;
    }

    /**
     * The inner edge of a window filling a part of the screen a point
     * lies on, or null: a side, never a corner, of a part standing where
     * it goes, and only a side that lies inside the screen rather than
     * on its border.
     */
    static ResizeEdge filledEdgeAt(WindowFrame frame, Window window,
                                   double mouseX, double mouseY) {
        Window.ScreenFill fill = window.getFill();
        if (fill == Window.ScreenFill.NONE || frame.isFillGliding()) {
            return null;
        }
        ResizeEdge edge = edgeAt(frame, mouseX, mouseY);
        if (edge == null) {
            return null;
        }
        switch (edge) {
            case LEFT:
                return fill.leftShare() > 0.0D ? edge : null;
            case RIGHT:
                return fill.rightShare() < 1.0D ? edge : null;
            case TOP:
                return fill.topShare() > 0.0D ? edge : null;
            case BOTTOM:
                return fill.bottomShare() < 1.0D ? edge : null;
            default:
                return null;
        }
    }

    /** The pointer an edge is shown with: across it, or along its corner. */
    public static LostTalesMapCursor.Pose cursorPose(ResizeEdge edge) {
        switch (edge) {
            case LEFT:
            case RIGHT:
                return LostTalesMapCursor.Pose.RESIZE_HORIZONTAL;
            case TOP:
            case BOTTOM:
                return LostTalesMapCursor.Pose.RESIZE_VERTICAL;
            case TOP_RIGHT:
            case BOTTOM_LEFT:
                // The sheet's diagonal runs bottom-left to top-right.
                return LostTalesMapCursor.Pose.RESIZE_DIAGONAL;
            default:
                return LostTalesMapCursor.Pose.RESIZE_ANTI_DIAGONAL;
        }
    }

    /**
     * Takes hold of an edge at the pointer's position. The resize is
     * live at once: the edge follows the pointer from the first pixel
     * of travel, and a press released where it was made changes
     * nothing.
     */
    public void armResize(ResizeTarget target, Window window, int mouseX,
                   int mouseY) {
        WindowFrame frame = target.frame;
        WindowLayout.raise(frame.windowId);
        this.host.selectWindow(window);
        if (outOfItsOwnBox(window)) {
            armFillResize(target.edge, window);
            return;
        }
        double left = frame.drawnLeft();
        double right = left + (frame.boxRight - frame.boxLeft);
        double top = frame.boxTop + frame.motionY;
        double bottom = frame.boxBottom + frame.motionY;
        double pointerX = WindowPlacement.preciseMouseX(this.mc,
                this.screenWidth);
        double pointerY = WindowPlacement.preciseMouseY(this.mc,
                this.screenHeight);
        this.windowResize = new WindowResize(frame.windowId, target.edge, left,
                right, top, bottom,
                pointerX - (target.edge.fromLeft ? left : right),
                pointerY - (target.edge.fromTop ? top : bottom),
                mouseX, mouseY,
                WindowPlacement.currentHeight(window, this.mc), window);
    }

    /**
     * Follows the pointer with the window itself: the edges the drag
     * holds move, the opposite ones stay, and neither size may leave the
     * screen or fall below what a window needs to be readable. Height is
     * as continuous as width — the window keeps the pixel height it is
     * dragged to and clips its topmost line rather than snapping to a
     * whole one. The size and position are written into the layout
     * without persisting, so the window reflows live and nothing is
     * saved or synchronized until the drag commits.
     */
    private void updateResize() {
        WindowResize resize = this.windowResize;
        Window window = WindowLayout.window(resize.windowId);
        if (window == null || window.isLocked() || outOfItsOwnBox(window)) {
            this.windowResize = null;
            return;
        }
        int margin = WindowPlacement.EDGE_MARGIN;
        // The floor follows the window's own tabs, so an edge stops
        // where the row would otherwise start hiding one.
        double minWidth = WindowPlacement.minBoxWidth(this.mc, window);
        double pointerX = WindowPlacement.preciseMouseX(this.mc,
                this.screenWidth) - resize.grabX;
        double pointerY = WindowPlacement.preciseMouseY(this.mc,
                this.screenHeight) - resize.grabY;
        resize.left = resize.startLeft;
        resize.right = resize.startRight;
        if (resize.edge.horizontal) {
            // The margin bounds a window's width exactly as it bounds
            // its position: it may reach the margin and go no further.
            if (resize.edge.fromLeft) {
                resize.left = Math.max(margin, Math.min(
                        resize.startRight - minWidth, pointerX));
            } else {
                resize.right = Math.min(this.screenWidth - margin, Math.max(
                        resize.startLeft + minWidth, pointerX));
            }
        }
        resize.height = WindowPlacement.currentHeight(window, this.mc);
        resize.top = resize.startTop;
        resize.bottom = resize.startBottom;
        if (resize.edge.vertical) {
            // The screen bounds the height the way it bounds the width.
            double room = resize.edge.fromTop
                    ? resize.startBottom - margin
                    : this.screenHeight - margin - resize.startTop;
            double height = clampedHeight(Math.min(room, resize.edge.fromTop
                    ? resize.startBottom - pointerY
                    : pointerY - resize.startTop),
                    WindowPlacement.minHeight(this.mc));
            resize.height = height;
            if (resize.edge.fromTop) {
                resize.top = resize.startBottom - height;
            } else {
                resize.bottom = resize.startTop + height;
            }
        }
        // The top edge carried to the top of the screen, or the bottom
        // edge to its bottom, stretches the window to the screen's whole
        // height in its own column when it is let go, as a desktop
        // window's does; the frosted pane shows it until then.
        double rawY = WindowPlacement.preciseMouseY(this.mc,
                this.screenHeight);
        boolean reaching = resize.edge == ResizeEdge.TOP
                ? rawY < SNAP_REACH
                : resize.edge == ResizeEdge.BOTTOM
                        && rawY > this.screenHeight - SNAP_REACH;
        resize.column = reaching ? WindowPlacement.columnFill(
                resize.startLeft, resize.startRight, this.screenWidth)
                : Window.ScreenFill.NONE;
        this.snapPreview.aim(resize.windowId, resize.column);
        applyLiveResize(resize, window);
    }

    /**
     * The height a dragged edge may give a window: the dragged edge
     * follows the pointer continuously between the least box and the
     * largest a layout may hold, so nothing under the pointer steps or
     * jitters while the drag runs.
     */
    static double clampedHeight(double wanted, double least) {
        return Math.max(least, Math.min(wanted, WindowLayout.MAX_WINDOW_SIZE));
    }

    /**
     * Gives the window the dimensions under the pointer, unpersisted, so
     * the next frame draws and reflows the real window at them.
     */
    private void applyLiveResize(WindowResize resize, Window window) {
        if (resize.edge.vertical) {
            WindowLayout.setWindowHeight(resize.windowId, resize.height,
                    false);
        }
        // The width goes first: a window's stored position is a percent
        // of the travel its own width leaves, so the percent has to be
        // worked out against the width the window is about to have.
        if (resize.edge.horizontal) {
            WindowLayout.setWindowWidth(resize.windowId,
                    (int)Math.round(resize.right - resize.left), false);
            if (resize.edge.fromLeft) {
                // The width is whole pixels, so one edge takes its
                // rounding: the edge under the hand, which steps by a
                // pixel as the pointer crosses the half, while the
                // anchored edge stays exactly where it was — as the left
                // edge does when the right one is dragged. With the
                // rounding left on the far edge, that edge and
                // everything hanging from it wobbled a pixel either way
                // with the pointer.
                resize.left = resize.right - WindowPlacement
                        .windowWidth(window, this.mc);
            }
        }
        // The window keeps the corner the drag did not hold: its left
        // edge and its baseline are what the layout stores, so a drag
        // from the left or the bottom moves them by exactly as much as
        // the box grew.
        double baseline = resize.bottom
                - WindowPlacement.barHeight(this.mc);
        WindowLayout.setPosition(resize.windowId,
                WindowPlacement.windowPercentX(window, resize.left,
                        this.mc, this.screenWidth),
                WindowPlacement.windowPercentY(window, baseline, this.mc,
                        this.screenHeight), false);
        this.host.windowsMoved();
    }

    /**
     * Writes the dimensions the drag ends on down, once. Let go stretched
     * to the screen's height, the window keeps the box it had before the
     * drag as its own and fills its column instead.
     */
    private void commitResize(WindowResize resize) {
        Window window = WindowLayout.window(resize.windowId);
        if (window == null) {
            this.snapPreview.release(false);
            return;
        }
        boolean column = resize.column != Window.ScreenFill.NONE;
        if (column) {
            restoreResize(resize);
            WindowLayout.setFill(resize.windowId, resize.column, false);
        } else {
            applyLiveResize(resize, window);
        }
        this.snapPreview.release(column);
        WindowLayout.persist();
    }

    /** Puts the dimensions from before the resize back, cancelling it. */
    private void restoreResize(WindowResize resize) {
        Window window = WindowLayout.window(resize.windowId);
        if (window == null) {
            return;
        }
        WindowLayout.setWindowHeight(resize.windowId, resize.storedHeight,
                false);
        WindowLayout.setWindowWidth(resize.windowId, resize.storedWidth,
                false);
        WindowLayout.setPosition(resize.windowId, resize.storedOffsetX,
                resize.storedOffsetY, false);
        this.host.windowsMoved();
    }

    /* ---- Resizing a part of the screen ---- */

    /** How close two parts' edges lie to be one edge, in shares of the screen. */
    private static final double LINE_TOLERANCE = 1.0E-4D;

    /**
     * An inner edge of a window filling a part of the screen being
     * dragged, as a snapped desktop window's is: the edge follows the
     * pointer, and every window filling a part that meets it along that
     * edge gives way with it, so the two stay a window gap apart and
     * share the screen between them. The parts become the player's own;
     * Escape puts every one of them back.
     */
    private static final class FillResize {
        final ResizeEdge edge;
        /** The pointer's offset from the edge's line at the press, in pixels. */
        final double grab;
        /** Every window the edge moves, the dragged one first. */
        final List<String> ids = new ArrayList<String>();
        /** The part each filled at the press. */
        final List<Window.ScreenFill> starts =
                new ArrayList<Window.ScreenFill>();
        /** Whether the line is each part's right or bottom side, not its left or top. */
        final List<Boolean> farSides = new ArrayList<Boolean>();
        boolean moved;

        FillResize(ResizeEdge edge, double grab) {
            this.edge = edge;
            this.grab = grab;
        }

        void add(String id, Window.ScreenFill start, boolean farSide) {
            this.ids.add(id);
            this.starts.add(start);
            this.farSides.add(Boolean.valueOf(farSide));
        }
    }

    /**
     * Takes hold of an inner edge of the part a window fills, and of the
     * same edge of every part meeting it there.
     */
    private void armFillResize(ResizeEdge edge, Window window) {
        Window.ScreenFill fill = window.getFill();
        boolean across = edge == ResizeEdge.LEFT || edge == ResizeEdge.RIGHT;
        boolean far = edge == ResizeEdge.RIGHT || edge == ResizeEdge.BOTTOM;
        double line = lineOf(fill, across, far);
        double size = across ? this.screenWidth : this.screenHeight;
        double pointer = across
                ? WindowPlacement.preciseMouseX(this.mc, this.screenWidth)
                : WindowPlacement.preciseMouseY(this.mc, this.screenHeight);
        FillResize resize = new FillResize(edge, pointer - line * size);
        resize.add(window.getId(), fill, far);
        for (Window other : WindowLayout.windows()) {
            Window.ScreenFill theirs = other.getFill();
            if (other == window || other.isLocked()
                    || theirs == Window.ScreenFill.NONE
                    || Math.abs(lineOf(theirs, across, !far) - line)
                            > LINE_TOLERANCE) {
                continue;
            }
            boolean alongside = across
                    ? theirs.topShare() < fill.bottomShare()
                            && theirs.bottomShare() > fill.topShare()
                    : theirs.leftShare() < fill.rightShare()
                            && theirs.rightShare() > fill.leftShare();
            if (alongside) {
                resize.add(other.getId(), theirs, !far);
            }
        }
        this.fillResize = resize;
    }

    /** A part's side: its left or right edge across, its top or bottom one down. */
    private static double lineOf(Window.ScreenFill fill, boolean across,
                                 boolean far) {
        if (across) {
            return far ? fill.rightShare() : fill.leftShare();
        }
        return far ? fill.bottomShare() : fill.topShare();
    }

    /**
     * Follows the pointer with the edge: every part it moves keeps room
     * for the smallest window, and the line stops where one of them
     * would be left with less.
     */
    private void updateFillResize() {
        FillResize resize = this.fillResize;
        boolean across = resize.edge == ResizeEdge.LEFT
                || resize.edge == ResizeEdge.RIGHT;
        double size = across ? this.screenWidth : this.screenHeight;
        if (size <= 0.0D) {
            return;
        }
        double pointer = across
                ? WindowPlacement.preciseMouseX(this.mc, this.screenWidth)
                : WindowPlacement.preciseMouseY(this.mc, this.screenHeight);
        double room = (across ? WindowPlacement.minBoxWidth(this.mc)
                : WindowPlacement.minHeight(this.mc))
                + 2.0D * WindowPlacement.EDGE_MARGIN;
        double least = room / size;
        double low = 0.0D;
        double high = 1.0D;
        for (int index = 0; index < resize.ids.size(); index++) {
            Window.ScreenFill start = resize.starts.get(index);
            if (resize.farSides.get(index).booleanValue()) {
                low = Math.max(low, lineOf(start, across, false) + least);
            } else {
                high = Math.min(high, lineOf(start, across, true) - least);
            }
        }
        if (low > high) {
            return;
        }
        double line = Math.max(low, Math.min(high,
                (pointer - resize.grab) / size));
        for (int index = 0; index < resize.ids.size(); index++) {
            Window.ScreenFill start = resize.starts.get(index);
            boolean farSide = resize.farSides.get(index).booleanValue();
            Window.ScreenFill moved = across
                    ? Window.ScreenFill.free(
                            farSide ? start.leftShare() : line,
                            start.topShare(),
                            farSide ? line : start.rightShare(),
                            start.bottomShare())
                    : Window.ScreenFill.free(start.leftShare(),
                            farSide ? start.topShare() : line,
                            start.rightShare(),
                            farSide ? line : start.bottomShare());
            holdFill(resize.ids.get(index), moved);
        }
        resize.moved = true;
        this.host.windowsMoved();
    }

    /** Stands a window in a part at once, unpersisted, with no glide. */
    private static void holdFill(String windowId, Window.ScreenFill fill) {
        WindowLayout.setFill(windowId, fill, false);
        WindowFrame frame = WindowFrame.find(windowId);
        if (frame != null) {
            frame.holdFill(fill);
        }
    }

    /* ---- Resizing a member list ---- */

    /* ---- Moving a window ---- */

    /**
     * Where a carried window lands when the button comes up: the
     * neighbour whose edge it has snapped to, and the part of the screen
     * it fills. One rides every carry, whether the window was taken by
     * its strip or carried out of a row by its tabs.
     */
    static final class Landing {
        /** Window whose edge the carried one is snapped to right now, or null. */
        String snapTargetId;
        /** Which side of that target the carried window sits on. */
        Window.LinkSide snapSide = Window.LinkSide.BELOW;
        /**
         * The part of the screen the window fills on release — the
         * pointer is in a screen edge's zone, a corner's, or a layout's
         * on the snap bar — or none.
         */
        Window.ScreenFill screenFill = Window.ScreenFill.NONE;
        /** The layout that part is a zone of, which snap assist offers the rest of; null for none. */
        Window.ScreenFill[] layout;
        /** The other windows a suggestion sends to their zones with it, each with its zone. */
        Map<String, Window.ScreenFill> companions =
                Collections.<String, Window.ScreenFill>emptyMap();
    }

    /**
     * A window being moved by its strip, its grip or its tabs, live from
     * the press. Offsets are fractional: the drag follows the raw mouse so
     * the window glides instead of stepping by whole GUI pixels.
     */
    static final class WindowDrag {
        final String windowId;
        final double grabOffsetX;
        final double grabOffsetY;
        final int pressX;
        final int pressY;
        final Landing landing = new Landing();
        /**
         * Whether the hold was taken afresh in the window's own box, as
         * it is once for a window carried out of the part of the screen
         * it filled.
         */
        boolean rebased;

        WindowDrag(String windowId, double grabOffsetX, double grabOffsetY,
                   int pressX, int pressY) {
            this.windowId = windowId;
            this.grabOffsetX = grabOffsetX;
            this.grabOffsetY = grabOffsetY;
            this.pressX = pressX;
            this.pressY = pressY;
        }
    }

    /**
     * Takes hold of a window by its strip or its grip from the pointer's
     * current position, live at once. A window taken hold of comes to the
     * front, dragged or not.
     */
    public void armWindowDrag(WindowFrame frame, int mouseX, int mouseY) {
        WindowLayout.raise(frame.windowId);
        this.windowDrag = new WindowDrag(frame.windowId,
                WindowPlacement.preciseMouseX(this.mc, this.screenWidth)
                        - frame.boxLeft,
                WindowPlacement.preciseMouseY(this.mc, this.screenHeight)
                        - frame.baseline,
                mouseX, mouseY);
    }

    /**
     * Keeps the dragged window under the pointer, from the raw mouse so
     * the motion is as fine as the display. A window filling a part of
     * the screen stays put until the pointer has really travelled, and
     * then gives the screen back under it.
     */
    private void moveDraggedWindow(int mouseX, int mouseY) {
        Window window = WindowLayout.window(this.windowDrag.windowId);
        if (window == null || window.isLocked()) {
            this.windowDrag = null;
            this.snapPreview.release(false);
            this.snapBar.hide();
            return;
        }
        if (!this.windowDrag.rebased && outOfItsOwnBox(window)) {
            // Pressed but not yet carried: a press that never travels
            // leaves the window where it stands.
            if (!travelled(mouseX, mouseY, this.windowDrag.pressX,
                    this.windowDrag.pressY)) {
                return;
            }
            holdInOwnBox(window);
        }
        carry(window, this.windowDrag.landing,
                WindowPlacement.preciseMouseX(this.mc, this.screenWidth)
                        - this.windowDrag.grabOffsetX,
                WindowPlacement.preciseMouseY(this.mc, this.screenHeight)
                        - this.windowDrag.grabOffsetY);
    }

    /**
     * Carries a window to where the pointer asks for it — its left edge
     * at {@code x}, its baseline at {@code baseline} — the same whether it
     * was taken by its strip or carried out of a row by its tabs: windows
     * stuck together move as one piece; a window alone sticks to a
     * neighbour's edge it comes near; and with the pointer in a snap
     * zone — a screen edge, a corner, a layout on the snap bar — the
     * preview shows the part of the screen the window fills when the
     * button comes up, which outranks sticking to a neighbour. The
     * window itself goes on following the pointer at its own size.
     */
    private void carry(Window window, Landing landing, double x,
                       double baseline) {
        WindowPlacement.Anchor anchor = WindowPlacement.constrainWindow(
                window, this.mc, x, baseline, this.screenWidth,
                this.screenHeight);
        List<Window> group = WindowLayout.linkedGroup(window);
        if (group.size() > 1) {
            moveGroup(window, group, anchor);
            landing.snapTargetId = null;
            landing.screenFill = Window.ScreenFill.NONE;
            this.snapBar.hide();
            this.snapPreview.aim(window.getId(), Window.ScreenFill.NONE);
            return;
        }
        WindowPlacement.Anchor snapped = snapToNeighbour(window, anchor,
                landing);
        WindowLayout.setPosition(window.getId(),
                WindowPlacement.windowPercentX(window, snapped.x, this.mc,
                        this.screenWidth),
                WindowPlacement.windowPercentY(window, snapped.baseline,
                        this.mc, this.screenHeight), false);
        double pointerX = WindowPlacement.preciseMouseX(this.mc,
                this.screenWidth);
        double pointerY = WindowPlacement.preciseMouseY(this.mc,
                this.screenHeight);
        // On the snap bar all the way down, and on the top edge above
        // it, its zones decide, and its padding lands the window nowhere;
        // anywhere else, the bar peeking included, the screen's edges and
        // corners.
        Window.ScreenFill onBar = this.snapBar.follow(this.mc,
                window.getId(), pointerX, pointerY, this.screenWidth,
                this.screenHeight);
        landing.screenFill = onBar != null ? onBar
                : snapZoneAt(pointerX, pointerY, this.screenWidth,
                        this.screenHeight, SnapLayouts.offersThirds(
                                this.mc, this.screenWidth, this.screenHeight));
        landing.layout = onBar != null ? this.snapBar.litLayout()
                : SnapLayouts.layoutFor(landing.screenFill);
        landing.companions = onBar != null ? this.snapBar.litCompanions()
                : Collections.<String, Window.ScreenFill>emptyMap();
        if (landing.screenFill != Window.ScreenFill.NONE) {
            landing.snapTargetId = null;
        }
        this.snapPreview.aim(window.getId(), landing.screenFill,
                landing.companions);
    }

    /**
     * Ends a carry: a window let go in a snap zone fills that part of
     * the screen — gliding into it from where it was dropped, which stays
     * its own box to come back to — and the preview and the snap bar go.
     * Let go on a suggestion, the suggestion's other windows go to their
     * zones with it; otherwise the rest of the zone's layout is offered
     * to the other windows.
     */
    private void land(String windowId, Landing landing) {
        boolean fills = landing.screenFill != Window.ScreenFill.NONE;
        if (fills) {
            for (Map.Entry<String, Window.ScreenFill> companion
                    : landing.companions.entrySet()) {
                Window other = WindowLayout.window(companion.getKey());
                if (other != null && !other.isLocked()) {
                    WindowLayout.raise(other.getId());
                    WindowLayout.setFill(other.getId(),
                            companion.getValue(), true);
                }
            }
            WindowLayout.raise(windowId);
            WindowLayout.setFill(windowId, landing.screenFill, false);
            if (landing.companions.isEmpty()) {
                this.snapAssist.offer(windowId, landing.layout,
                        landing.screenFill);
            }
        }
        this.snapPreview.release(fills);
        this.snapBar.hide();
    }

    /**
     * Moves stuck windows as one piece: every one of them takes the same
     * step the carried one took, in both directions, so the group keeps
     * its shape whichever way it is carried. The step is measured between
     * resting boxes, all read before any of them moves; a resting box is
     * also where a window that has just given the screen back is going.
     */
    private void moveGroup(Window window, List<Window> group,
                           WindowPlacement.Anchor anchor) {
        List<WindowPlacement.Box> resting =
                new ArrayList<WindowPlacement.Box>(group.size());
        for (int index = 0; index < group.size(); index++) {
            resting.add(WindowPlacement.restingBounds(group.get(index),
                    this.mc, this.screenWidth, this.screenHeight));
        }
        int carried = group.indexOf(window);
        WindowPlacement.Box origin = carried >= 0 ? resting.get(carried)
                : WindowPlacement.restingBounds(window, this.mc,
                        this.screenWidth, this.screenHeight);
        double deltaX = anchor.x - origin.x;
        double deltaY = anchor.baseline - origin.baseline();
        for (int index = 0; index < group.size(); index++) {
            Window member = group.get(index);
            WindowPlacement.Box at = resting.get(index);
            WindowPlacement.Anchor moved =
                    WindowPlacement.constrainWindow(member, this.mc,
                            at.x + deltaX, at.baseline() + deltaY,
                            this.screenWidth, this.screenHeight);
            WindowLayout.setPosition(member.getId(),
                    WindowPlacement.windowPercentX(member, moved.x,
                            this.mc, this.screenWidth),
                    WindowPlacement.windowPercentY(member,
                            moved.baseline, this.mc, this.screenHeight),
                    false);
        }
    }

    /**
     * The part of the screen a window dragged with the pointer at
     * ({@code x}, {@code y}) snaps to, as a desktop window snaps: a half
     * from a side edge, a quarter from a corner — the end of a side edge
     * nearest the corner, {@link #SNAP_CORNER_SHARE} of its length, or
     * the end of the top or bottom edge, {@link #SNAP_EDGE_CORNER_SHARE}
     * of its length — and from the top edge between the corners the
     * whole screen, or, on a screen wide enough to offer the thirds
     * ({@code thirds}), the left or the right third from the edge's own
     * left or right third, as Windows 11 snaps on a large screen (Nils,
     * 2026-09-19). The bottom edge snaps only at its corners; none
     * anywhere else.
     */
    static Window.ScreenFill snapZoneAt(double x, double y,
                                            int screenWidth,
                                            int screenHeight,
                                            boolean thirds) {
        boolean atLeft = x < SNAP_REACH;
        boolean atRight = x > screenWidth - SNAP_REACH;
        boolean atTop = y < SNAP_REACH;
        boolean atBottom = y > screenHeight - SNAP_REACH;
        boolean nearTop = y < screenHeight * SNAP_CORNER_SHARE;
        boolean nearBottom = y > screenHeight * (1.0D - SNAP_CORNER_SHARE);
        boolean nearLeft = x < screenWidth * SNAP_EDGE_CORNER_SHARE;
        boolean nearRight = x > screenWidth * (1.0D - SNAP_EDGE_CORNER_SHARE);
        if (atLeft || atRight) {
            if (nearTop) {
                return atLeft ? Window.ScreenFill.TOP_LEFT
                        : Window.ScreenFill.TOP_RIGHT;
            }
            if (nearBottom) {
                return atLeft ? Window.ScreenFill.BOTTOM_LEFT
                        : Window.ScreenFill.BOTTOM_RIGHT;
            }
            return atLeft ? Window.ScreenFill.LEFT
                    : Window.ScreenFill.RIGHT;
        }
        if (atTop) {
            if (nearLeft || nearRight) {
                return nearLeft ? Window.ScreenFill.TOP_LEFT
                        : Window.ScreenFill.TOP_RIGHT;
            }
            if (thirds && x < screenWidth / 3.0D) {
                return Window.ScreenFill.LEFT_THIRD;
            }
            if (thirds && x >= screenWidth * 2.0D / 3.0D) {
                return Window.ScreenFill.RIGHT_THIRD;
            }
            return Window.ScreenFill.FULL;
        }
        if (atBottom) {
            return nearLeft ? Window.ScreenFill.BOTTOM_LEFT
                    : nearRight ? Window.ScreenFill.BOTTOM_RIGHT
                    : Window.ScreenFill.NONE;
        }
        return Window.ScreenFill.NONE;
    }

    /**
     * Whether a window stands anywhere but in its own box: filling a
     * part of the screen, or still gliding to or from one. Its edges are
     * not its own to resize then, and a drag takes hold of it afresh.
     */
    private static boolean outOfItsOwnBox(Window window) {
        WindowFrame frame = WindowFrame.find(window.getId());
        return window.getFill() != Window.ScreenFill.NONE
                || (frame != null && !frame.isInOwnBox());
    }

    /**
     * Takes hold of a window carried off by its strip while it stands
     * outside its own box — filling a part of the screen, or still
     * gliding to or from one — the way a snapped desktop window comes
     * down when its title bar is dragged: it lets the screen go, and the
     * pointer holds its own box the same share of the way across the
     * strip and at the same depth, so the window shrinks toward the hand
     * and moves on under it.
     */
    private void holdInOwnBox(Window window) {
        WindowFrame frame = WindowFrame.find(window.getId());
        WindowLayout.setFill(window.getId(), Window.ScreenFill.NONE,
                false);
        if (frame == null || !frame.drawn) {
            this.windowDrag.rebased = true;
            return;
        }
        double pointerX = WindowPlacement.preciseMouseX(this.mc,
                this.screenWidth);
        double pointerY = WindowPlacement.preciseMouseY(this.mc,
                this.screenHeight);
        double drawnWidth = frame.boxRight - frame.boxLeft;
        double across = drawnWidth <= 0.0D ? 0.5D : Math.max(0.0D,
                Math.min(1.0D, (pointerX - frame.boxLeft) / drawnWidth));
        int width = WindowPlacement.windowWidth(window, this.mc);
        double height = WindowPlacement.currentHeight(window, this.mc);
        int barHeight = WindowPlacement.barHeight(this.mc);
        double depth = pointerY - frame.boxTop;
        WindowDrag held = new WindowDrag(window.getId(), across * width,
                depth - (height - barHeight), this.windowDrag.pressX,
                this.windowDrag.pressY);
        held.rebased = true;
        this.windowDrag = held;
    }

    /**
     * Snaps the carried window to another window's edge when it comes
     * within a few pixels of it, a window gap apart, and remembers that
     * edge in {@code landing} so it can be lit and a lock can link the
     * two; the snapped place is returned.
     */
    private WindowPlacement.Anchor snapToNeighbour(Window window,
                                   WindowPlacement.Anchor anchor,
                                   Landing landing) {
        int margin = WindowPlacement.WINDOW_GAP;
        int width = WindowPlacement.windowWidth(window, this.mc);
        double height = WindowPlacement.currentHeight(window, this.mc);
        int barHeight = WindowPlacement.barHeight(this.mc);
        double top = anchor.baseline - (height - barHeight);
        double bottom = anchor.baseline + barHeight;
        landing.snapTargetId = null;
        double best = Double.MAX_VALUE;
        double baseline = anchor.baseline;
        double x = anchor.x;
        List<WindowFrame> frames = WindowFrame.drawnFrames();
        for (int index = 0; index < frames.size(); index++) {
            WindowFrame frame = frames.get(index);
            Window other = WindowLayout.window(frame.windowId);
            // A window filling the screen has no edge of its own to be
            // stuck to.
            if (other == null || other == window
                    || other.getFill() != Window.ScreenFill.NONE
                    || window.getId().equals(other.getLinkTarget())) {
                continue;
            }
            boolean overlapsColumn = anchor.x < frame.boxRight + margin
                    && anchor.x + width + margin > frame.boxLeft;
            if (overlapsColumn) {
                double aboveGap = Math.abs(frame.boxTop - margin - bottom);
                if (aboveGap <= LINK_SNAP && aboveGap < best) {
                    best = aboveGap;
                    baseline = frame.boxTop - margin - barHeight;
                    x = anchor.x;
                    landing.snapTargetId = other.getId();
                    landing.snapSide = Window.LinkSide.ABOVE;
                }
                double belowGap = Math.abs(top - (frame.boxBottom + margin));
                if (belowGap <= LINK_SNAP && belowGap < best) {
                    best = belowGap;
                    baseline = frame.boxBottom + margin + (height - barHeight);
                    x = anchor.x;
                    landing.snapTargetId = other.getId();
                    landing.snapSide = Window.LinkSide.BELOW;
                }
            }
            // A side snap wants the two windows level with one another,
            // the way a top or bottom snap wants them in one column.
            boolean overlapsRow = top < frame.boxBottom + margin
                    && bottom + margin > frame.boxTop;
            if (!overlapsRow) {
                continue;
            }
            double leftGap = Math.abs(
                    anchor.x + width + margin - frame.boxLeft);
            if (leftGap <= LINK_SNAP && leftGap < best) {
                best = leftGap;
                // A side snap moves the window onto the edge it is
                // catching, exactly as a top or bottom snap does; the
                // baseline it already has is the level it keeps.
                x = frame.boxLeft - margin - width;
                baseline = anchor.baseline;
                landing.snapTargetId = other.getId();
                landing.snapSide = Window.LinkSide.LEFT;
            }
            double rightGap = Math.abs(
                    anchor.x - (frame.boxRight + margin));
            if (rightGap <= LINK_SNAP && rightGap < best) {
                best = rightGap;
                x = frame.boxRight + margin;
                baseline = anchor.baseline;
                landing.snapTargetId = other.getId();
                landing.snapSide = Window.LinkSide.RIGHT;
            }
        }
        return WindowPlacement.constrainWindow(window, this.mc, x,
                baseline, this.screenWidth, this.screenHeight);
    }

    /* ---- Carrying tabs ---- */

    /**
     * A press on a tab that may become a drag; tabs move in raw space.
     * The drag carries every tab that moves with it — the marked group
     * when the pressed tab is one of them, the pressed tab alone
     * otherwise.
     *
     * <p>Carried clear of every row the tabs become a window of their
     * own at once rather than on release, the way a browser tears a tab
     * off; {@link #detachedWindowId} names that window from then on,
     * and it is what follows the pointer instead of the ghost.</p>
     *
     * <p>A window holding one tab starts its drag already torn off, the
     * window it is in being the one it was torn off into: there is
     * nothing to reorder and nothing to take out, so that window is
     * what follows the pointer from the first pixel, and carrying it
     * onto another window's row docks the tab there like any other.</p>
     */
    public static final class TabDrag {
        public final WindowTab tab;
        public final List<WindowTab> group;
        final String sourceWindowId;
        final int pressX;
        final int pressY;
        public final int grabOffsetX;
        /**
         * Where in the <em>window</em> the pressed tab was taken hold
         * of: the same point of the same tab, measured from the left
         * edge of whatever window is carrying it. A row begins its tabs
         * past the search control, so a tab torn off into a window of
         * its own does not start where it did in the row it left; this
         * is what keeps the cursor on the pixel it closed on across
         * that hand-over.
         */
        final int grabOffsetInWindowX;
        /** Where in the row's height the tab was taken hold of. */
        final int grabOffsetY;
        /** Whether releasing without a drag leaves only the pressed tab. */
        final boolean collapsesOnRelease;
        boolean active;
        /**
         * The row the tabs were last thrown out of, or null before they
         * have been thrown out of any. That row asks a little more of
         * them before taking them back — see the return distance — so
         * the two answers about one pointer cannot argue at its edges.
         * It is the row they <em>left</em> rather than the one the drag
         * began in: a tab carried into another window and pulled out of
         * it again needs the same guard there, and a row a tab has never
         * left needs none at all.
         */
        String leftRowId;
        /** Window the tabs were torn off into, or null while in a row. */
        String detachedWindowId;
        /** Where the pointer is, so the row can lean the tab toward it. */
        public int pointerX;
        /** Since when the pointer has been pressed against a screen edge past the strip; 0 while not. */
        long againstEdgeSince;
        /**
         * Whether the tabs last came out by resting against a screen edge.
         * The row they left then takes them back only once the pointer is
         * on the strip itself: at the edge the pointer cannot get further
         * away than the reach that would take them back.
         */
        boolean tornAtEdge;
        /** Whether this frame's answer to {@link #hasLeftItsRow} came from the edge rest. */
        boolean leavingAtEdge;
        /** Window row the tab would dock into at the current pointer. */
        String targetWindowId;
        int targetIndex = -1;
        /** Where the window the tabs were torn off into lands, as any carried window does. */
        final Landing landing = new Landing();

        TabDrag(WindowTab tab, List<WindowTab> group, String sourceWindowId,
                int pressX, int pressY, int grabOffsetX,
                int grabOffsetInWindowX, int grabOffsetY,
                boolean collapsesOnRelease) {
            this.tab = tab;
            this.group = group;
            this.collapsesOnRelease = collapsesOnRelease;
            this.sourceWindowId = sourceWindowId;
            this.pressX = pressX;
            this.pressY = pressY;
            this.grabOffsetX = grabOffsetX;
            this.grabOffsetInWindowX = grabOffsetInWindowX;
            this.grabOffsetY = grabOffsetY;
        }
    }

    /**
     * The tabs a press on {@code tab} carries: the window's marked group
     * when the pressed tab is one of them, and the pressed tab alone
     * otherwise — which is also what an unmarked press has just left
     * marked.
     */
    public static List<WindowTab> draggedGroup(Window window, WindowTab tab) {
        List<WindowTab> marked = TabSelection.selectedIn(window);
        return marked.size() > 1 && marked.contains(tab) ? marked
                : Collections.singletonList(tab);
    }

    /**
     * Arms a drag of the tabs a press took hold of, remembering where in
     * the pressed tab the hand closed on it so the tab goes on sitting
     * under that same point wherever it is carried. A drag that starts
     * already torn off names the window it is in as the one carrying it.
     */
    public void armTabDrag(Window window, WindowFrame frame,
                    TabRow.Row row, WindowTab pressed,
                    List<WindowTab> group, int mouseX, int mouseY,
                    boolean collapsesOnRelease, boolean alreadyTornOff) {
        WindowLayout.raise(window.getId());
        int grabX = 0;
        int pressedX = Integer.MIN_VALUE;
        int firstOfGroupX = Integer.MIN_VALUE;
        List<TabRow.Tab> tabs = frame.tabBar.layout(this.font, row);
        for (TabRow.Tab tab : tabs) {
            if (tab.tab.equals(pressed)) {
                grabX = mouseX - (row.offsetX + tab.x);
                pressedX = tab.x;
            }
            if (firstOfGroupX == Integer.MIN_VALUE
                    && group.contains(tab.tab)) {
                firstOfGroupX = tab.x;
            }
        }
        // Where the pressed tab will stand in a window of its own: the
        // window's first tab begins past the search control, and the
        // tabs carried with it keep their order in front of it.
        int withinRun = pressedX == Integer.MIN_VALUE
                || firstOfGroupX == Integer.MIN_VALUE
                ? 0 : Math.max(0, pressedX - firstOfGroupX);
        TabDrag drag = new TabDrag(pressed, group, window.getId(), mouseX,
                mouseY, grabX,
                TabRow.tabRunLeftInset() + withinRun + grabX,
                mouseY - TabRow.rowTop(row.rowBottom),
                collapsesOnRelease);
        if (alreadyTornOff) {
            drag.detachedWindowId = window.getId();
        }
        this.tabDrag = drag;
    }

    /**
     * Finds the row the dragged tab would dock into at the pointer: a
     * row whose band the pointer is in (with some slack around its tabs)
     * and whose window is not locked.
     */
    private void updateDropTarget(TabDrag drag, int mouseX, int mouseY) {
        drag.targetWindowId = null;
        drag.targetIndex = -1;
        LostTalesGuiAnimationSample opening =
                this.host.opening();
        List<Window> windows = WindowLayout.stacked();
        for (int index = windows.size() - 1; index >= 0; index--) {
            Window window = windows.get(index);
            // A torn-off window rides under the pointer, so its own row
            // is always there: docking into it would mean nothing.
            if (window.isLocked()
                    || window.getId().equals(drag.detachedWindowId)) {
                continue;
            }
            WindowFrame frame = WindowFrame.of(window);
            TabRow.Row row = this.host.rowFor(window, frame,
                    opening);
            if (row == null) {
                continue;
            }
            int bandOff = bandOverhang(row, mouseY);
            if (bandOff > DOCK_BAND_SLACK) {
                continue;
            }
            // The whole strip docks, end controls and grip included: a
            // tab carried to the row's far end lands after the last tab
            // rather than the drag being cancelled there.
            int left = row.offsetX + row.left - DOCK_SLACK;
            int right = row.offsetX + row.right + DOCK_SLACK;
            if (mouseX < left || mouseX >= right) {
                continue;
            }
            // The row a tab was last thrown out of — whichever row that
            // is — asks one thing more of it before taking it back:
            // that the pointer has really come back within reach of the
            // strip it was pulled off. The reach back in is shorter than
            // the pull that tore it out, so the two cannot argue about
            // one pointer and a tab torn off at that row's edge is not
            // handed straight back on the next frame, flashing a window
            // up and taking it away again. Only that row asks: a row a
            // tab has never left has nothing to guard against. Tabs that
            // came out against a screen edge come back only onto the
            // strip itself, since the edge keeps the pointer within the
            // reach back in.
            int stripOff = stripOverhang(row, mouseX);
            if (window.getId().equals(drag.leftRowId)
                    && (drag.tornAtEdge ? stripOff > 0 || bandOff > 0
                            : pulledBeyond(stripOff, bandOff,
                                    RETURN_DISTANCE))) {
                continue;
            }
            // Where the carried run itself stands over this row: its
            // tabs ride at a fixed offset from the pointer — the grab
            // point, kept from the row they were pressed in — so the
            // row is offered the run where the hand is showing it, not
            // wherever along it the hand happens to hold it.
            int runWidth = carriedTabWidth(drag, opening);
            int runLeft = mouseX - runGrabOffsetX(drag) - row.offsetX;
            drag.targetWindowId = window.getId();
            drag.targetIndex = frame.tabBar.dropIndexAt(this.font, row,
                    runLeft, runWidth);
            return;
        }
    }

    /**
     * How far above or below the row's band the pointer stands; zero
     * anywhere inside it. One of the two overhangs every attach and
     * detach question is measured by — and both are the pointer's,
     * measured against the bar the player sees, so no direction of pull
     * is measured against a different thing than another.
     */
    private static int bandOverhang(TabRow.Row row, int mouseY) {
        int rowTop = TabRow.rowTop(row.rowBottom);
        return overhangOf(mouseY, rowTop, row.rowBottom);
    }

    /** The sideways twin: how far past either end of the strip the
     *  pointer stands, zero anywhere along it. */
    private static int stripOverhang(TabRow.Row row, int mouseX) {
        return overhangOf(mouseX, row.offsetX + row.left,
                row.offsetX + row.right);
    }

    /** How far outside {@code [lower, upper)} a position lies. */
    static int overhangOf(int at, int lower, int upper) {
        return at < lower ? lower - at : at >= upper ? at - upper : 0;
    }

    /**
     * Where the carried run's left edge rides, measured back from the
     * pointer: the grab offset within the pressed tab plus that tab's
     * place within the run — the very measure a torn-off window is held
     * by, so a run is offered to a row exactly where it is seen.
     */
    private static int runGrabOffsetX(TabDrag drag) {
        return drag.grabOffsetInWindowX - TabRow.tabRunLeftInset();
    }

    /**
     * Whether a pull of {@code dx} past the strip's ends and {@code dy}
     * off its band has reached {@code distance}: the two overhangs
     * taken as one straight-line pull — the pointer's distance to the
     * nearest point of the strip being left — so a diagonal pull
     * measures like any other.
     */
    public static boolean pulledBeyond(int dx, int dy, int distance) {
        return dx * dx + dy * dy >= distance * distance;
    }

    /**
     * How wide the carried run is drawn: measured in the row that holds
     * it right now — its own torn-off window while it is being carried
     * — so every row it is offered to is asked about a run of the size
     * it would actually take.
     */
    private int carriedTabWidth(TabDrag drag,
                                LostTalesGuiAnimationSample opening) {
        Window holder = WindowLayout.windowOf(drag.tab);
        if (holder == null) {
            return 0;
        }
        WindowFrame frame = WindowFrame.of(holder);
        TabRow.Row row = this.host.rowFor(holder, frame, opening);
        return row == null ? 0
                : frame.tabBar.carriedRunWidth(this.font, row);
    }

    /**
     * One frame of a tab drag. The tabs never leave the strip until they
     * leave it for good: along their own row they slide between their
     * neighbours as the pointer passes them, and only once the pointer
     * is carried clear of the row altogether do they become a window of
     * their own, which then follows the pointer.
     */
    private void dragTabs(TabDrag drag, int mouseX, int mouseY) {
        drag.pointerX = mouseX;
        updateDropTarget(drag, mouseX, mouseY);
        Window window = WindowLayout.windowOf(drag.tab);
        if (window == null) {
            return;
        }
        if (drag.targetWindowId != null
                && !window.getId().equals(drag.targetWindowId)
                && dockInto(drag, drag.targetWindowId)) {
            return;
        }
        // A dock that could not be made changes nothing, and the drag
        // goes on as it was: a window carried over a row that will not
        // take the tabs still follows the pointer rather than sticking
        // there with nothing happening.
        Window detached = WindowLayout.window(drag.detachedWindowId);
        if (detached != null) {
            carryWindow(drag, detached);
            return;
        }
        if (hasLeftItsRow(drag, mouseX, mouseY)) {
            tearOff(drag);
            return;
        }
        // Still in its row, wherever the pointer has wandered: a tab on
        // its way out of the strip goes on changing places until the
        // moment it leaves, rather than freezing the instant the pointer
        // steps off the row.
        slideAlongRow(drag, window);
    }

    /**
     * Joins the dragged tabs to another window's row where the pointer
     * has reached it: the reverse of tearing them off, and just as
     * immediate. A window they had been torn off into empties and goes
     * with them, so the strip they join is the only place they are.
     * Answers whether the row took them; a row that would not is left
     * alone and the drag carries on unchanged.
     */
    private boolean dockInto(TabDrag drag, String targetWindowId) {
        Window target = WindowLayout.window(targetWindowId);
        if (target == null || !WindowLayout.moveTabs(drag.group,
                targetWindowId,
                listPosition(target, Collections.<WindowTab>emptyList(),
                        drag.targetIndex), false)) {
            return false;
        }
        drag.detachedWindowId = null;
        drag.againstEdgeSince = 0L;
        drag.tornAtEdge = false;
        // The window the tabs rode in is gone, and its landing with it.
        this.snapPreview.reset();
        this.snapBar.hide();
        WindowLayout.raise(targetWindowId);
        TabSelection.selectAll(targetWindowId, drag.group);
        this.host.selectTab(drag.tab);
        return true;
    }

    /**
     * Puts the dragged tabs where the pointer has carried them in their
     * own row, so the row reads as it will once the button comes up.
     * Nothing is written while the drag runs; the file is saved once, on
     * release.
     */
    private void slideAlongRow(TabDrag drag, Window window) {
        WindowFrame frame = WindowFrame.of(window);
        TabRow.Row row = this.host.rowFor(window, frame,
                this.host.opening());
        if (row == null) {
            return;
        }
        int slot = frame.tabBar.slideIndexAt(this.font, row, drag.group);
        if (slot >= 0) {
            WindowLayout.moveTabs(drag.group, window.getId(),
                    listPosition(window, drag.group, slot), false);
        }
    }

    /**
     * Where in a window's own tab list a place counted among its visible
     * tabs falls. The list also holds open tabs the player cannot
     * currently see (Party outside a party, Faction without one),
     * sitting between them, so a place counted in visible tabs has to be
     * translated or the move lands beside the wrong neighbour. Tabs in
     * {@code moving} are left out of both counts: they are on their way
     * and are not what the place is measured against, which is also what
     * makes the answer the position the run ends up at once they have
     * been lifted out.
     */
    static int listPosition(Window window, List<WindowTab> moving,
                            int visibleSlot) {
        List<WindowTab> all = window.getTabs();
        int position = 0;
        int seen = 0;
        for (int index = 0; index < all.size(); index++) {
            WindowTab tab = all.get(index);
            if (moving.contains(tab)) {
                continue;
            }
            if (tab.isAvailable()) {
                if (seen == visibleSlot) {
                    return position;
                }
                seen++;
            }
            position++;
        }
        return position;
    }

    /**
     * Whether the pointer has carried the tabs clear of the row they are
     * still in. A row that is not on screen counts as left behind.
     * Carried past either end of the strip the pointer is just as
     * plainly on its way out as carried above or below it, and the two
     * overhangs are one pull, so a diagonal carry needs the same travel
     * as a straight one. It is the pointer that is measured on every
     * side, not the run: the run's end tabs rest against the room's very
     * edges, so a sideways pull measured on the run would count from the
     * first pixel of the drag while an upward one still had the band to
     * cross. Leaving costs the full pull, coming back a shorter
     * reach, and between the two lies a band where nothing happens at
     * all — which is what keeps the two answers from arguing about one
     * pointer.
     *
     * <p>Against the screen's sides or its foot the pull stops short
     * however far the hand goes, so resting there past the strip is pull
     * enough. Not against its top: a strip at the top of the screen lets
     * its tabs go downward, as a maximised browser's does, and a tab
     * pushed up only goes on sliding along its row.</p>
     */
    private boolean hasLeftItsRow(TabDrag drag, int mouseX, int mouseY) {
        drag.leavingAtEdge = false;
        Window window = WindowLayout.windowOf(drag.tab);
        WindowFrame frame = window == null ? null
                : WindowFrame.of(window);
        TabRow.Row row = window == null ? null
                : this.host.rowFor(window, frame,
                        this.host.opening());
        if (row == null) {
            return true;
        }
        int dx = stripOverhang(row, mouseX);
        int dy = bandOverhang(row, mouseY);
        if (pulledBeyond(dx, dy, DETACH_DISTANCE)) {
            return true;
        }
        boolean againstEdge = (dy > 0 && mouseY >= this.screenHeight - 1)
                || (dx > 0 && (mouseX <= 0 || mouseX >= this.screenWidth - 1));
        long now = System.nanoTime();
        if (!againstEdge) {
            drag.againstEdgeSince = 0L;
            return false;
        }
        if (drag.againstEdgeSince == 0L) {
            drag.againstEdgeSince = now;
        }
        drag.leavingAtEdge = now - drag.againstEdgeSince >= EDGE_TEAR_NANOS;
        return drag.leavingAtEdge;
    }

    /**
     * Tears the dragged tabs off into a window of their own, placed so
     * its row lands under the pointer. Refused at the window cap, where
     * the tabs stay in their row and the drag goes on as a ghost.
     */
    private void tearOff(TabDrag drag) {
        // Placed for the window it is about to become, which is as tall
        // and as wide as the one it is leaving. Measuring it as a window
        // of the smallest possible size — which is what asking for no
        // window at all answers — put it a whole window's height out for
        // the one frame before the carry corrected it.
        Window source = WindowLayout.windowOf(drag.tab);
        WindowPlacement.Anchor anchor = carriedAnchor(source, drag);
        Window window = WindowLayout.detach(drag.group,
                WindowPlacement.windowPercentX(source, anchor.x, this.mc,
                        this.screenWidth),
                WindowPlacement.windowPercentY(source, anchor.baseline,
                        this.mc, this.screenHeight));
        if (window == null) {
            return;
        }
        drag.leftRowId = source == null ? drag.sourceWindowId
                : source.getId();
        drag.detachedWindowId = window.getId();
        drag.tornAtEdge = drag.leavingAtEdge;
        drag.againstEdgeSince = 0L;
        // It appears under the pointer on a quick fade rather than in a
        // frame.
        WindowFrame.of(window).beginAppearing();
        WindowLayout.raise(window.getId());
        TabSelection.selectAll(window.getId(), drag.group);
        this.host.selectTab(drag.tab);
    }

    /**
     * Keeps a torn-off window's row under the pointer as it moves, and
     * carries it as a window taken by its strip is carried — sticking to
     * a neighbour, snapping into a part of the screen. A window filling a
     * part of the screen gives it back the moment it is carried, taking
     * its own size again with the tab still under the hand.
     */
    private void carryWindow(TabDrag drag, Window window) {
        if (window.getFill() != Window.ScreenFill.NONE) {
            WindowLayout.setFill(window.getId(),
                    Window.ScreenFill.NONE, false);
        }
        WindowPlacement.Anchor anchor = carriedAnchor(window, drag);
        carry(window, drag.landing, anchor.x, anchor.baseline);
    }

    /**
     * Where a window carrying the dragged tabs sits: its row under the
     * pointer's exact position, as a window taken by its strip follows
     * it, held where the tab was taken hold of, and kept on screen.
     */
    private WindowPlacement.Anchor carriedAnchor(Window window,
                                                     TabDrag drag) {
        double rowTop = WindowPlacement.preciseMouseY(this.mc,
                this.screenHeight) - drag.grabOffsetY;
        return WindowPlacement.constrainWindow(window, this.mc,
                WindowPlacement.preciseMouseX(this.mc, this.screenWidth)
                        - drag.grabOffsetInWindowX,
                WindowPlacement.baselineForRowTop(window, this.mc,
                        rowTop),
                this.screenWidth, this.screenHeight);
    }

    /**
     * Releases a dragged tab. The tabs have been where they are all
     * along — in a row they slid into, or in a window of their own — so
     * letting go moves no tab: a window of their own let go in a snap
     * zone fills it, as one dropped by its strip does, and the layout is
     * written.
     *
     * <p>The drag is deliberately <em>not</em> asked once more here. It
     * has already run for every frame and every pointer event of the
     * carry, so it can have nothing left to say; asking again only gave
     * it one last chance to hand the tabs to whichever window the
     * pointer happened to be near, taking back a window the player could
     * plainly see they had just pulled out.</p>
     */
    private void dropTab(TabDrag drag) {
        if (drag.detachedWindowId != null) {
            land(drag.detachedWindowId, drag.landing);
        }
        WindowLayout.persist();
    }
}
