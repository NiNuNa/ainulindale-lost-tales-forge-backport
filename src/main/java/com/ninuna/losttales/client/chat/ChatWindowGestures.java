package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * The press-to-drag state machines of the chat screen: a tab carried
 * along its row, torn off into a window of its own and docked into
 * another; a window moved by its strip, its grip or its messages and
 * snapped against a neighbour; a window resized by an edge or a
 * corner; a scrollbar's thumb carried. The screen arms them from its
 * presses, feeds them the pointer every frame and every mouse event,
 * and asks them what is being dragged; they move the layout live and
 * write it down once, on release.
 */
final class ChatWindowGestures {
    /** Where the screen's row description comes from. */
    interface RowSource {
        ChatChannelTabBar.Row rowFor(ChatWindow window, ChatWindowFrame frame,
                                     LostTalesGuiAnimationSample opening);
    }

    /** Pointer travel before a press on a tab becomes a drag. */
    static final int DRAG_THRESHOLD = 4;
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
    static final int DETACH_DISTANCE = 14;
    /** The reach, measured the same way, within which the row a run was
     *  torn out of takes it back. */
    static final int RETURN_DISTANCE = 9;
    /** How far above or below a row's band a carried run may still be
     *  offered to it. */
    static final int DOCK_BAND_SLACK = 7;
    /** Horizontal slack around a row that still counts as dropping on it. */
    private static final int DOCK_SLACK = 24;
    /** How far outside its edge a window still answers to a resize. */
    static final int RESIZE_BORDER = 4;
    /** How far along an edge from a corner still counts as that corner. */
    static final int RESIZE_CORNER = 12;
    private static final int LINK_HIGHLIGHT_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);

    private final RowSource rows;
    private final ChatTabActions tabActions;
    private final ChatInputBar bar;
    private Minecraft mc;
    private FontRenderer font;
    private int screenWidth;
    private int screenHeight;

    private TabDrag tabDrag;
    private WindowDrag windowDrag;
    private WindowResize windowResize;
    private ScrollbarDrag scrollbarDrag;

    ChatWindowGestures(RowSource rows, ChatTabActions tabActions,
                       ChatInputBar bar) {
        this.rows = rows;
        this.tabActions = tabActions;
        this.bar = bar;
    }

    /** Called from {@code initGui}, which also runs on every resize. */
    void bind(Minecraft mc, FontRenderer font, int screenWidth,
              int screenHeight) {
        this.mc = mc;
        this.font = font;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /* ---- What is being dragged ---- */

    boolean isDragging() {
        return (this.tabDrag != null && this.tabDrag.active)
                || (this.windowDrag != null && this.windowDrag.active)
                || (this.windowResize != null && this.windowResize.active);
    }

    /** Whether a live window drag is carrying this window. */
    boolean isMovingWindow(String windowId) {
        return this.windowDrag != null && this.windowDrag.active
                && windowId.equals(this.windowDrag.windowId);
    }

    /** Whether a live resize is reshaping this window. */
    boolean isResizingWindow(String windowId) {
        return this.windowResize != null && this.windowResize.active
                && windowId.equals(this.windowResize.windowId);
    }

    /** The live tab drag, or null while no tab is being carried. */
    TabDrag activeTabDrag() {
        return this.tabDrag != null && this.tabDrag.active ? this.tabDrag : null;
    }

    /**
     * The edge a resize holds, armed or live, or null. A resize in
     * progress keeps saying so wherever the pointer has gone, the way a
     * pressed control keeps its look.
     */
    ResizeEdge armedResizeEdge() {
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
    void advance(int mouseX, int mouseY) {
        ClientChatChannelViews.setScrollEasingSuppressed(
                this.windowResize != null && this.windowResize.active);
        if (this.windowResize != null && this.windowResize.active) {
            updateResize();
        } else if (this.windowDrag != null && this.windowDrag.active) {
            moveDraggedWindow();
        } else if (this.tabDrag != null && this.tabDrag.active) {
            dragTabs(this.tabDrag, mouseX, mouseY);
        }
    }

    /**
     * Which window's scrollbar should be showing: the one the pointer is
     * in, or the one whose bar is being dragged, so the bar does not
     * fade out from under a drag that has wandered off it. Set before
     * the windows draw, since the draw is what eases it in and out.
     */
    void markScrollbarsWanted(int mouseX, int mouseY) {
        // Only the window the pointer is really in shows its bar: one
        // covered by another is not being read, whatever its box says.
        ChatWindowFrame pointed = this.scrollbarDrag == null
                ? ChatWindowFrame.drawnAt(mouseX, mouseY) : null;
        for (ChatWindowFrame frame : ChatWindowFrame.drawnFrames()) {
            frame.scrollbarWanted = this.scrollbarDrag != null
                    ? frame.windowId.equals(this.scrollbarDrag.windowId)
                    : frame == pointed;
        }
    }

    /** The edge a drag is about to link to, lit along its whole width. */
    void drawLinkHighlight() {
        if (this.windowDrag == null || !this.windowDrag.active
                || this.windowDrag.snapTargetId == null) {
            return;
        }
        ChatWindowFrame target = ChatWindowFrame.find(
                this.windowDrag.snapTargetId);
        if (target == null || !target.drawn) {
            return;
        }
        int left = (int)Math.floor(target.boxLeft);
        int right = (int)Math.round(target.boxRight);
        int top = (int)Math.floor(target.boxTop);
        int bottom = (int)Math.round(target.boxBottom);
        int colour = LostTalesChatVisualStyle.argb(LINK_HIGHLIGHT_RGB, 0xFF);
        // The edge the window would stick to, lit along its whole length.
        switch (this.windowDrag.snapSide) {
            case ABOVE:
                Gui.drawRect(left, top - 1, right, top + 1, colour);
                return;
            case BELOW:
                Gui.drawRect(left, bottom - 1, right, bottom + 1, colour);
                return;
            case LEFT:
                Gui.drawRect(left - 1, top, left + 1, bottom, colour);
                return;
            default:
                Gui.drawRect(right - 1, top, right + 1, bottom, colour);
        }
    }

    /* ---- Mouse events ---- */

    /**
     * A press-and-move with the left button. Every armed drag becomes
     * live once the pointer has travelled the threshold; a live one
     * follows the pointer. True when a drag took the event, so the
     * screen leaves vanilla's own handling alone.
     */
    boolean onDragMove(int mouseX, int mouseY) {
        if (this.scrollbarDrag != null) {
            dragScrollbar(mouseY);
            return true;
        }
        if (this.windowResize != null) {
            if (!this.windowResize.active
                    && travelled(mouseX, mouseY, this.windowResize.pressX,
                            this.windowResize.pressY)) {
                this.windowResize.active = true;
                this.bar.closePickers();
            }
            if (this.windowResize.active) {
                updateResize();
            }
            return true;
        }
        if (this.windowDrag != null) {
            if (!this.windowDrag.active
                    && travelled(mouseX, mouseY, this.windowDrag.pressX,
                            this.windowDrag.pressY)) {
                this.windowDrag.active = true;
                this.bar.closePickers();
            }
            if (this.windowDrag.active) {
                moveDraggedWindow();
            }
            return true;
        }
        if (this.tabDrag != null) {
            if (!this.tabDrag.active
                    && travelled(mouseX, mouseY, this.tabDrag.pressX,
                            this.tabDrag.pressY)) {
                this.tabDrag.active = true;
                this.bar.closePickers();
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
     * The left button coming up: a live resize commits, a live window
     * drag and a live tab drag write the layout down, a tab press that
     * never travelled collapses the group to the pressed tab. Answers a
     * window press that never travelled — one that was a click on the
     * lines after all — for the screen to act on, or null.
     */
    WindowDrag onRelease() {
        this.scrollbarDrag = null;
        if (this.windowResize != null) {
            WindowResize resize = this.windowResize;
            this.windowResize = null;
            if (resize.active) {
                commitResize(resize);
            }
        }
        WindowDrag click = null;
        if (this.windowDrag != null) {
            WindowDrag drag = this.windowDrag;
            this.windowDrag = null;
            if (drag.active) {
                // Touching only shows what a window would stick to;
                // locking it is what sticks it.
                ChatWindowLayout.persist();
            } else {
                click = drag;
            }
        }
        if (this.tabDrag != null) {
            TabDrag drag = this.tabDrag;
            this.tabDrag = null;
            if (drag.active) {
                dropTab();
            } else if (drag.collapsesOnRelease) {
                // Pressed and released without travelling: the press
                // was a pick after all, so the group gives way to it.
                ChatTabSelection.selectOnly(drag.sourceWindowId, drag.tab);
            }
        }
        return click;
    }

    /** Ends every drag where it stands: Escape, or the screen closing. */
    void cancelDrags() {
        if (this.tabDrag != null && this.tabDrag.active) {
            // The tabs are already where the drag left them — in a row
            // they slid into, or in a window of their own — so ending
            // the carry writes that down rather than putting the row
            // back together. Leaving it unwritten was the one way the
            // layout on screen and the layout on disk could disagree.
            ChatWindowLayout.persist();
        }
        this.tabDrag = null;
        this.scrollbarDrag = null;
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
        if (this.windowDrag != null) {
            if (this.windowDrag.active) {
                ChatWindowLayout.persist();
            }
            this.windowDrag = null;
        }
    }

    /* ---- Scrollbars ---- */

    /** A scrollbar being dragged: which window, and where it was grabbed. */
    private static final class ScrollbarDrag {
        final String windowId;
        /** Pointer offset inside the thumb when it was grabbed. */
        final float grabOffset;

        ScrollbarDrag(String windowId, float grabOffset) {
            this.windowId = windowId;
            this.grabOffset = grabOffset;
        }
    }

    /**
     * Grabs a scrollbar. Pressing the thumb carries it from where it was
     * taken hold of; pressing the track above or below jumps to there
     * and then carries it, the way a scrollbar anywhere else does.
     */
    boolean grabScrollbar(int mouseX, int mouseY) {
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatWindowFrame frame = frames.get(index);
            if (!frame.scrollbarContains(mouseX, mouseY)) {
                continue;
            }
            float thumbHeight = frame.scrollbarThumbBottom
                    - frame.scrollbarThumbTop;
            float offset = mouseY >= frame.scrollbarThumbTop
                    && mouseY < frame.scrollbarThumbBottom
                            ? mouseY - frame.scrollbarThumbTop
                            : thumbHeight / 2.0F;
            this.scrollbarDrag = new ScrollbarDrag(frame.windowId, offset);
            dragScrollbar(mouseY);
            return true;
        }
        return false;
    }

    /**
     * Maps the pointer onto the history: where the thumb's top sits in
     * the travel it has is where the view sits in what it can reach.
     */
    private void dragScrollbar(int mouseY) {
        ChatWindowFrame frame = this.scrollbarDrag == null ? null
                : ChatWindowFrame.find(this.scrollbarDrag.windowId);
        if (frame == null || frame.view == null || frame.lines == null) {
            return;
        }
        float thumbHeight = frame.scrollbarThumbBottom
                - frame.scrollbarThumbTop;
        float travel = (frame.scrollbarTrackBottom
                - frame.scrollbarTrackTop) - thumbHeight;
        if (travel <= 0.0F) {
            return;
        }
        float top = mouseY - this.scrollbarDrag.grabOffset;
        float taken = (frame.scrollbarTrackBottom - thumbHeight - top)
                / travel;
        double roomLines = frame.roomLines();
        int rows = frame.contentRows();
        double reach = Math.max(0.0D, rows - roomLines);
        ClientChatChannelViews.scrollTo(frame.view,
                reach * Math.max(0.0F, Math.min(1.0F, taken)),
                rows, roomLines);
    }

    /* ---- Resizing ---- */

    /**
     * The edge or corner of a window the pointer is on. A window is
     * resized the way any window is: the edges the drag does not touch
     * stay exactly where they are, so the opposite edge is the anchor.
     */
    enum ResizeEdge {
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
    static final class ResizeTarget {
        final ChatWindowFrame frame;
        final ResizeEdge edge;

        ResizeTarget(ChatWindowFrame frame, ResizeEdge edge) {
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
        final double storedLines;
        final int storedWidth;
        final double storedOffsetX;
        final double storedOffsetY;
        /** Pointer's offset from the edge it took hold of. */
        final double grabX;
        final double grabY;
        final int pressX;
        final int pressY;
        boolean active;
        /** The box under the pointer right now. */
        double left;
        double right;
        double top;
        double bottom;
        /** Message lines the box asks for, fractions included. */
        double lines;

        WindowResize(String windowId, ResizeEdge edge, double left,
                     double right, double top, double bottom, double grabX,
                     double grabY, int pressX, int pressY, double lines,
                     ChatWindow window) {
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
            this.lines = lines;
            this.storedLines = window.getMaxLines();
            this.storedWidth = window.getWidth();
            this.storedOffsetX = window.getOffsetX();
            this.storedOffsetY = window.getOffsetY();
        }
    }

    /**
     * The window edge a press at this point would take hold of, or null.
     * One classification, in one order: what is drawn above the windows
     * — the pickers, the completion lists, the settings menu, the input
     * bar group — owns the pointer first; the resize border comes next,
     * ahead of the tab strip it overlaps along the window's top edge;
     * the strip and the windows themselves come last. Hover, the cursor,
     * mouse-down and the drag it starts all ask this same question, so
     * what the pointer shows is what the press does.
     */
    static ResizeTarget resizeUnderPointer(int mouseX, int mouseY,
                                           ChatPointerRegions regions) {
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
    static ResizeTarget resizeTargetAt(int mouseX, int mouseY) {
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatWindowFrame frame = frames.get(index);
            if (coversPoint(frame, mouseX, mouseY)) {
                // The window in front owns everything inside it, so a
                // window behind it never takes a click through it.
                return null;
            }
            ChatWindow window = ChatWindowLayout.window(frame.windowId);
            if (window == null || window.isLocked()) {
                continue;
            }
            ResizeEdge edge = edgeAt(frame, mouseX, mouseY);
            if (edge != null) {
                return new ResizeTarget(frame, edge);
            }
        }
        return null;
    }

    /**
     * Whether the point lies within the window as it was drawn — its
     * strip, its messages and its bar — rather than on the border
     * outside it.
     */
    static boolean coversPoint(ChatWindowFrame frame, int mouseX,
                               int mouseY) {
        double left = frame.drawnLeft();
        double right = left + (frame.boxRight - frame.boxLeft);
        double top = frame.boxTop + frame.motionY;
        double bottom = frame.boxBottom + frame.motionY;
        return mouseX > left && mouseX < right
                && mouseY > top && mouseY < bottom;
    }

    /** Which edge or corner of one window's box a point lies on. */
    static ResizeEdge edgeAt(ChatWindowFrame frame, int mouseX, int mouseY) {
        double left = frame.drawnLeft();
        double right = left + (frame.boxRight - frame.boxLeft);
        double top = frame.boxTop + frame.motionY;
        double bottom = frame.boxBottom + frame.motionY;
        if (mouseX < left - RESIZE_BORDER || mouseX > right + RESIZE_BORDER
                || mouseY < top - RESIZE_BORDER
                || mouseY > bottom + RESIZE_BORDER) {
            return null;
        }
        boolean onLeft = mouseX <= left;
        boolean onRight = mouseX >= right;
        boolean onTop = mouseY <= top;
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

    /** The pointer an edge is shown with: across it, or along its corner. */
    static LostTalesMapCursor.Pose cursorPose(ResizeEdge edge) {
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

    /** Arms a resize from the pointer's position on an edge. */
    void armResize(ResizeTarget target, ChatWindow window, int mouseX,
                   int mouseY) {
        ChatWindowFrame frame = target.frame;
        ChatWindowLayout.raise(frame.windowId);
        this.tabActions.selectWindow(window);
        double left = frame.drawnLeft();
        double right = left + (frame.boxRight - frame.boxLeft);
        double top = frame.boxTop + frame.motionY;
        double bottom = frame.boxBottom + frame.motionY;
        double pointerX = ChatWindowPlacement.preciseMouseX(this.mc,
                this.screenWidth);
        double pointerY = ChatWindowPlacement.preciseMouseY(this.mc,
                this.screenHeight);
        this.windowResize = new WindowResize(frame.windowId, target.edge, left,
                right, top, bottom,
                pointerX - (target.edge.fromLeft ? left : right),
                pointerY - (target.edge.fromTop ? top : bottom),
                mouseX, mouseY,
                ChatWindowPlacement.currentLines(window, this.mc), window);
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
        ChatWindow window = ChatWindowLayout.window(resize.windowId);
        if (window == null || window.isLocked()) {
            this.windowResize = null;
            return;
        }
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        // The floor follows the window's own tabs, so an edge stops
        // where the row would otherwise start hiding one.
        double minWidth = ChatWindowPlacement.minBoxWidth(this.mc, window);
        double pointerX = ChatWindowPlacement.preciseMouseX(this.mc,
                this.screenWidth) - resize.grabX;
        double pointerY = ChatWindowPlacement.preciseMouseY(this.mc,
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
        resize.lines = ChatWindowPlacement.currentLines(window, this.mc);
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
                    ChatWindowPlacement.lineStride(this.mc),
                    ChatWindowPlacement.rowHeight(this.mc)
                            + ChatWindowPlacement.HISTORY_TOP_MARGIN
                            + ChatWindowPlacement.barHeight(this.mc));
            resize.lines = ChatWindowPlacement.linesForHeight(height,
                    this.mc);
            if (resize.edge.fromTop) {
                resize.top = resize.startBottom - height;
            } else {
                resize.bottom = resize.startTop + height;
            }
        }
        applyLiveResize(resize, window);
    }

    /**
     * The height a dragged edge may give a window: the dragged edge
     * follows the pointer continuously between the smallest and largest
     * window — the chrome plus the fewest and the most message lines at
     * the line stride — so nothing under the pointer steps or jitters
     * while the drag runs.
     */
    static double clampedHeight(double wanted, double stride, double chrome) {
        return Math.max(chrome + ChatWindowLayout.MIN_WINDOW_LINES * stride,
                Math.min(wanted,
                        chrome + ChatWindowLayout.MAX_WINDOW_LINES * stride));
    }

    /**
     * Gives the window the dimensions under the pointer, unpersisted, so
     * the next frame draws and reflows the real window at them.
     */
    private void applyLiveResize(WindowResize resize, ChatWindow window) {
        if (resize.edge.vertical) {
            ChatWindowLayout.setWindowLines(resize.windowId, resize.lines,
                    false);
        }
        // The width goes first: a window's stored position is a percent
        // of the travel its own width leaves, so the percent has to be
        // worked out against the width the window is about to have.
        if (resize.edge.horizontal) {
            if (ChatWindowLines.isAvailable()) {
                ChatWindowLayout.setWindowWidth(resize.windowId,
                        ChatWindowPlacement.chatWidthForBox(
                                resize.right - resize.left, this.mc),
                        false);
            } else {
                ChatWindowLines.logUnavailableOnce();
            }
        }
        // The window keeps the corner the drag did not hold: its left
        // edge and its baseline are what the layout stores, so a drag
        // from the left or the bottom moves them by exactly as much as
        // the box grew.
        double baseline = resize.bottom
                - ChatWindowPlacement.barHeight(this.mc);
        ChatWindowLayout.setPosition(resize.windowId,
                ChatWindowPlacement.windowPercentX(window, resize.left,
                        this.mc, this.screenWidth),
                ChatWindowPlacement.windowPercentY(baseline, this.mc,
                        this.screenHeight), false);
        this.bar.updateInputBounds();
    }

    /** Writes the dimensions the drag ends on down, once. */
    private void commitResize(WindowResize resize) {
        ChatWindow window = ChatWindowLayout.window(resize.windowId);
        if (window == null) {
            return;
        }
        applyLiveResize(resize, window);
        ChatWindowLayout.persist();
    }

    /** Puts the dimensions from before the resize back, cancelling it. */
    private void restoreResize(WindowResize resize) {
        ChatWindow window = ChatWindowLayout.window(resize.windowId);
        if (window == null) {
            return;
        }
        ChatWindowLayout.setWindowLines(resize.windowId, resize.storedLines,
                false);
        ChatWindowLayout.setWindowWidth(resize.windowId, resize.storedWidth,
                false);
        ChatWindowLayout.setPosition(resize.windowId, resize.storedOffsetX,
                resize.storedOffsetY, false);
        this.bar.updateInputBounds();
    }

    /* ---- Moving a window ---- */

    /**
     * A window being moved. From the grip the drag is live at once; from
     * the messages it is armed by the press and becomes a drag only once
     * the pointer travels, so a plain click on a line still acts on the
     * line when the button comes up. Offsets are fractional: the drag
     * follows the raw mouse so the window glides instead of stepping by
     * whole GUI pixels.
     */
    static final class WindowDrag {
        final String windowId;
        final double grabOffsetX;
        final double grabOffsetY;
        final int pressX;
        final int pressY;
        boolean active;
        /** Window whose edge the drag is snapped to right now, or null. */
        String snapTargetId;
        /** Which side of that target the dragged window sits on. */
        ChatWindow.LinkSide snapSide = ChatWindow.LinkSide.BELOW;

        WindowDrag(String windowId, double grabOffsetX, double grabOffsetY,
                   int pressX, int pressY, boolean active) {
            this.windowId = windowId;
            this.grabOffsetX = grabOffsetX;
            this.grabOffsetY = grabOffsetY;
            this.pressX = pressX;
            this.pressY = pressY;
            this.active = active;
        }
    }

    /**
     * Arms a window drag from the pointer's current position; live at
     * once from the strip or the grip, armed only from the messages.
     * A window taken hold of comes to the front, dragged or not.
     */
    void armWindowDrag(ChatWindowFrame frame, int mouseX, int mouseY,
                       boolean active) {
        ChatWindowLayout.raise(frame.windowId);
        this.windowDrag = new WindowDrag(frame.windowId,
                ChatWindowPlacement.preciseMouseX(this.mc, this.screenWidth)
                        - frame.boxLeft,
                ChatWindowPlacement.preciseMouseY(this.mc, this.screenHeight)
                        - frame.baseline,
                mouseX, mouseY, active);
    }

    /**
     * Keeps the dragged window's percent position under the pointer,
     * from the raw mouse so the motion is as fine as the display.
     */
    private void moveDraggedWindow() {
        ChatWindow window = ChatWindowLayout.window(this.windowDrag.windowId);
        if (window == null || window.isLocked()) {
            this.windowDrag = null;
            return;
        }
        ChatWindowPlacement.Anchor anchor = ChatWindowPlacement.constrainWindow(
                window, this.mc,
                ChatWindowPlacement.preciseMouseX(this.mc, this.screenWidth)
                        - this.windowDrag.grabOffsetX,
                ChatWindowPlacement.preciseMouseY(this.mc, this.screenHeight)
                        - this.windowDrag.grabOffsetY,
                this.screenWidth, this.screenHeight);
        List<ChatWindow> group = ChatWindowLayout.linkedGroup(window);
        if (group.size() > 1) {
            // Stuck windows move as one piece: every one of them takes
            // the same step the dragged one took, in both directions, so
            // the group keeps its shape whichever way it is carried.
            ChatWindowFrame frame = ChatWindowFrame.find(window.getId());
            if (frame == null) {
                return;
            }
            double deltaX = anchor.x - frame.boxLeft;
            double deltaY = anchor.baseline - frame.baseline;
            for (int index = 0; index < group.size(); index++) {
                ChatWindow member = group.get(index);
                ChatWindowFrame memberFrame =
                        ChatWindowFrame.find(member.getId());
                if (memberFrame == null) {
                    continue;
                }
                ChatWindowPlacement.Anchor moved =
                        ChatWindowPlacement.constrainWindow(member, this.mc,
                                memberFrame.boxLeft + deltaX,
                                memberFrame.baseline + deltaY,
                                this.screenWidth, this.screenHeight);
                ChatWindowLayout.setPosition(member.getId(),
                        ChatWindowPlacement.windowPercentX(member, moved.x,
                                this.mc, this.screenWidth),
                        ChatWindowPlacement.windowPercentY(moved.baseline,
                                this.mc, this.screenHeight), false);
            }
            return;
        }
        ChatWindowPlacement.Anchor snapped = snapToNeighbour(window, anchor);
        ChatWindowLayout.setPosition(window.getId(),
                ChatWindowPlacement.windowPercentX(window, snapped.x, this.mc,
                        this.screenWidth),
                ChatWindowPlacement.windowPercentY(snapped.baseline, this.mc,
                        this.screenHeight), false);
    }

    /**
     * Snaps the dragged window to another window's top or bottom edge
     * when it comes within a few pixels of it, a margin apart, and
     * remembers that edge so the release links the two; the snapped
     * baseline is returned.
     */
    private ChatWindowPlacement.Anchor snapToNeighbour(ChatWindow window,
                                   ChatWindowPlacement.Anchor anchor) {
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        int width = ChatWindowPlacement.windowWidth(window, this.mc);
        double height = ChatWindowPlacement.currentHeight(window, this.mc);
        int barHeight = ChatWindowPlacement.barHeight(this.mc);
        double top = anchor.baseline - (height - barHeight);
        double bottom = anchor.baseline + barHeight;
        this.windowDrag.snapTargetId = null;
        double best = Double.MAX_VALUE;
        double baseline = anchor.baseline;
        double x = anchor.x;
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = 0; index < frames.size(); index++) {
            ChatWindowFrame frame = frames.get(index);
            ChatWindow other = ChatWindowLayout.window(frame.windowId);
            if (other == null || other == window
                    || window.getId().equals(other.getLinkTarget())) {
                continue;
            }
            boolean overlapsColumn = anchor.x < frame.boxRight + margin
                    && anchor.x + width + margin > frame.boxLeft;
            if (overlapsColumn) {
                double aboveGap = Math.abs(frame.boxTop - margin - bottom);
                if (aboveGap <= ChatTabActions.LINK_SNAP && aboveGap < best) {
                    best = aboveGap;
                    baseline = frame.boxTop - margin - barHeight;
                    x = anchor.x;
                    this.windowDrag.snapTargetId = other.getId();
                    this.windowDrag.snapSide = ChatWindow.LinkSide.ABOVE;
                }
                double belowGap = Math.abs(top - (frame.boxBottom + margin));
                if (belowGap <= ChatTabActions.LINK_SNAP && belowGap < best) {
                    best = belowGap;
                    baseline = frame.boxBottom + margin + (height - barHeight);
                    x = anchor.x;
                    this.windowDrag.snapTargetId = other.getId();
                    this.windowDrag.snapSide = ChatWindow.LinkSide.BELOW;
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
            if (leftGap <= ChatTabActions.LINK_SNAP && leftGap < best) {
                best = leftGap;
                // A side snap moves the window onto the edge it is
                // catching, exactly as a top or bottom snap does; the
                // baseline it already has is the level it keeps.
                x = frame.boxLeft - margin - width;
                baseline = anchor.baseline;
                this.windowDrag.snapTargetId = other.getId();
                this.windowDrag.snapSide = ChatWindow.LinkSide.LEFT;
            }
            double rightGap = Math.abs(
                    anchor.x - (frame.boxRight + margin));
            if (rightGap <= ChatTabActions.LINK_SNAP && rightGap < best) {
                best = rightGap;
                x = frame.boxRight + margin;
                baseline = anchor.baseline;
                this.windowDrag.snapTargetId = other.getId();
                this.windowDrag.snapSide = ChatWindow.LinkSide.RIGHT;
            }
        }
        return ChatWindowPlacement.constrainWindow(window, this.mc, x,
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
    static final class TabDrag {
        final ChatTab tab;
        final List<ChatTab> group;
        final String sourceWindowId;
        final int pressX;
        final int pressY;
        final int grabOffsetX;
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
        int pointerX;
        /** Window row the tab would dock into at the current pointer. */
        String targetWindowId;
        int targetIndex = -1;

        TabDrag(ChatTab tab, List<ChatTab> group, String sourceWindowId,
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
    static List<ChatTab> draggedGroup(ChatWindow window, ChatTab tab) {
        List<ChatTab> marked = ChatTabSelection.selectedIn(window);
        return marked.size() > 1 && marked.contains(tab) ? marked
                : Collections.singletonList(tab);
    }

    /**
     * Arms a drag of the tabs a press took hold of, remembering where in
     * the pressed tab the hand closed on it so the tab goes on sitting
     * under that same point wherever it is carried. A drag that starts
     * already torn off names the window it is in as the one carrying it.
     */
    void armTabDrag(ChatWindow window, ChatWindowFrame frame,
                    ChatChannelTabBar.Row row, ChatTab pressed,
                    List<ChatTab> group, int mouseX, int mouseY,
                    boolean collapsesOnRelease, boolean alreadyTornOff) {
        ChatWindowLayout.raise(window.getId());
        int grabX = 0;
        int pressedX = Integer.MIN_VALUE;
        int firstOfGroupX = Integer.MIN_VALUE;
        List<ChatChannelTabBar.Tab> tabs = frame.tabBar.layout(this.font, row);
        for (ChatChannelTabBar.Tab tab : tabs) {
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
                ChatChannelTabBar.tabRunLeftInset() + withinRun + grabX,
                mouseY - ChatChannelTabBar.rowTop(row.rowBottom),
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
                ClientChatChannelViews.openSample();
        List<ChatWindow> windows = ChatWindowLayout.stacked();
        for (int index = windows.size() - 1; index >= 0; index--) {
            ChatWindow window = windows.get(index);
            // A torn-off window rides under the pointer, so its own row
            // is always there: docking into it would mean nothing.
            if (window.isLocked()
                    || window.getId().equals(drag.detachedWindowId)) {
                continue;
            }
            ChatWindowFrame frame = ChatWindowFrame.of(window);
            ChatChannelTabBar.Row row = this.rows.rowFor(window, frame,
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
            // tab has never left has nothing to guard against.
            if (window.getId().equals(drag.leftRowId)
                    && pulledBeyond(stripOverhang(row, mouseX), bandOff,
                            RETURN_DISTANCE)) {
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
    private static int bandOverhang(ChatChannelTabBar.Row row, int mouseY) {
        int rowTop = ChatChannelTabBar.rowTop(row.rowBottom);
        return overhangOf(mouseY, rowTop, row.rowBottom);
    }

    /** The sideways twin: how far past either end of the strip the
     *  pointer stands, zero anywhere along it. */
    private static int stripOverhang(ChatChannelTabBar.Row row, int mouseX) {
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
        return drag.grabOffsetInWindowX - ChatChannelTabBar.tabRunLeftInset();
    }

    /**
     * Whether a pull of {@code dx} past the strip's ends and {@code dy}
     * off its band has reached {@code distance}: the two overhangs
     * taken as one straight-line pull — the pointer's distance to the
     * nearest point of the strip being left — so a diagonal pull
     * measures like any other.
     */
    static boolean pulledBeyond(int dx, int dy, int distance) {
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
        ChatWindow holder = ChatWindowLayout.windowOf(drag.tab);
        if (holder == null) {
            return 0;
        }
        ChatWindowFrame frame = ChatWindowFrame.of(holder);
        ChatChannelTabBar.Row row = this.rows.rowFor(holder, frame, opening);
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
        ChatWindow window = ChatWindowLayout.windowOf(drag.tab);
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
        ChatWindow detached = ChatWindowLayout.window(drag.detachedWindowId);
        if (detached != null) {
            carryWindow(drag, detached, mouseX, mouseY);
            return;
        }
        if (hasLeftItsRow(drag, mouseX, mouseY)) {
            tearOff(drag, mouseX, mouseY);
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
        ChatWindow target = ChatWindowLayout.window(targetWindowId);
        if (target == null || !ChatWindowLayout.moveTabs(drag.group,
                targetWindowId,
                listPosition(target, Collections.<ChatTab>emptyList(),
                        drag.targetIndex), false)) {
            return false;
        }
        drag.detachedWindowId = null;
        ChatWindowLayout.raise(targetWindowId);
        ChatTabSelection.selectAll(targetWindowId, drag.group);
        this.tabActions.selectChannel(drag.tab);
        return true;
    }

    /**
     * Puts the dragged tabs where the pointer has carried them in their
     * own row, so the row reads as it will once the button comes up.
     * Nothing is written while the drag runs; the file is saved once, on
     * release.
     */
    private void slideAlongRow(TabDrag drag, ChatWindow window) {
        ChatWindowFrame frame = ChatWindowFrame.of(window);
        ChatChannelTabBar.Row row = this.rows.rowFor(window, frame,
                ClientChatChannelViews.openSample());
        if (row == null) {
            return;
        }
        int slot = frame.tabBar.slideIndexAt(this.font, row, drag.group);
        if (slot >= 0) {
            ChatWindowLayout.moveTabs(drag.group, window.getId(),
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
    static int listPosition(ChatWindow window, List<ChatTab> moving,
                            int visibleSlot) {
        List<ChatTab> all = window.getTabs();
        int position = 0;
        int seen = 0;
        for (int index = 0; index < all.size(); index++) {
            ChatTab tab = all.get(index);
            if (moving.contains(tab)) {
                continue;
            }
            if (ClientChatChannelState.isAvailable(tab)) {
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
     * side — not the run, whose end tabs rest touching the room's very
     * edges, so measuring it made a sideways pull start counting from
     * the first pixel of the drag while an upward one still had the
     * band to cross. Leaving costs the full pull, coming back a shorter
     * reach, and between the two lies a band where nothing happens at
     * all — which is what keeps the two answers from arguing about one
     * pointer.
     */
    private boolean hasLeftItsRow(TabDrag drag, int mouseX, int mouseY) {
        ChatWindow window = ChatWindowLayout.windowOf(drag.tab);
        ChatWindowFrame frame = window == null ? null
                : ChatWindowFrame.of(window);
        ChatChannelTabBar.Row row = window == null ? null
                : this.rows.rowFor(window, frame,
                        ClientChatChannelViews.openSample());
        if (row == null) {
            return true;
        }
        return pulledBeyond(stripOverhang(row, mouseX),
                bandOverhang(row, mouseY), DETACH_DISTANCE);
    }

    /**
     * Tears the dragged tabs off into a window of their own, placed so
     * its row lands under the pointer. Refused at the window cap, where
     * the tabs stay in their row and the drag goes on as a ghost.
     */
    private void tearOff(TabDrag drag, int mouseX, int mouseY) {
        // Placed for the window it is about to become, which is as tall
        // and as wide as the one it is leaving. Measuring it as a window
        // of the smallest possible size — which is what asking for no
        // window at all answers — put it a whole window's height out for
        // the one frame before the carry corrected it.
        ChatWindow source = ChatWindowLayout.windowOf(drag.tab);
        ChatWindowPlacement.Anchor anchor = carriedAnchor(source, drag,
                mouseX, mouseY);
        ChatWindow window = ChatWindowLayout.detach(drag.group,
                ChatWindowPlacement.windowPercentX(source, anchor.x, this.mc,
                        this.screenWidth),
                ChatWindowPlacement.windowPercentY(anchor.baseline, this.mc,
                        this.screenHeight));
        if (window == null) {
            return;
        }
        drag.leftRowId = source == null ? drag.sourceWindowId
                : source.getId();
        drag.detachedWindowId = window.getId();
        ChatWindowLayout.raise(window.getId());
        ChatTabSelection.selectAll(window.getId(), drag.group);
        this.tabActions.selectChannel(drag.tab);
    }

    /** Keeps a torn-off window's row under the pointer as it moves. */
    private void carryWindow(TabDrag drag, ChatWindow window, int mouseX,
                             int mouseY) {
        ChatWindowPlacement.Anchor anchor = carriedAnchor(window, drag,
                mouseX, mouseY);
        ChatWindowLayout.setPosition(window.getId(),
                ChatWindowPlacement.windowPercentX(window, anchor.x, this.mc,
                        this.screenWidth),
                ChatWindowPlacement.windowPercentY(anchor.baseline, this.mc,
                        this.screenHeight), false);
    }

    /**
     * Where a window carrying the dragged tabs sits: its row under the
     * pointer, held where the tab was taken hold of, and kept on screen.
     * An empty window's row stands one line above its baseline.
     */
    private ChatWindowPlacement.Anchor carriedAnchor(ChatWindow window,
                                                     TabDrag drag,
                                                     int mouseX, int mouseY) {
        int rowTop = mouseY - drag.grabOffsetY;
        return ChatWindowPlacement.constrainWindow(window, this.mc,
                mouseX - drag.grabOffsetInWindowX,
                ChatWindowPlacement.baselineForRowTop(window, this.mc,
                        rowTop),
                this.screenWidth, this.screenHeight);
    }

    /**
     * Releases a dragged tab. The tabs have been where they are all
     * along — in a row they slid into, or in a window of their own —
     * so letting go decides nothing and moves nothing: only the resting
     * layout is written.
     *
     * <p>The drag is deliberately <em>not</em> asked once more here. It
     * has already run for every frame and every pointer event of the
     * carry, so it can have nothing left to say; asking again only gave
     * it one last chance to hand the tabs to whichever window the
     * pointer happened to be near, taking back a window the player could
     * plainly see they had just pulled out.</p>
     */
    private static void dropTab() {
        ChatWindowLayout.persist();
    }
}
