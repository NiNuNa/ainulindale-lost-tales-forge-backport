package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowFrame;
import com.ninuna.losttales.client.window.WindowGestures;
import com.ninuna.losttales.client.window.WindowLayout;
import java.util.List;

/**
 * The drags a conversation runs in its window, through the window's
 * gestures: its scrollbar's thumb carried, and its member list resized
 * by the list's edge.
 */
final class ChatWindowDrags {
    private ChatWindowDrags() {}

    /**
     * Grabs a scrollbar. Pressing the thumb carries it from where it was
     * taken hold of; pressing the track above or below jumps to there
     * and then carries it, the way a scrollbar anywhere else does.
     */
    static boolean grabScrollbar(WindowGestures gestures, double mouseX,
                                 double mouseY) {
        List<WindowFrame> frames = WindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatFrame frame = (ChatFrame)frames.get(index);
            if (!frame.scrollbarContains(mouseX, mouseY)) {
                continue;
            }
            float thumbHeight = frame.scrollbarThumbBottom
                    - frame.scrollbarThumbTop;
            float offset = frame.scrollbarThumbContains(mouseX, mouseY)
                    ? (float)(mouseY - frame.scrollbarThumbTop)
                    : thumbHeight / 2.0F;
            gestures.startContentDrag(new ScrollbarDrag(frame.windowId,
                    offset));
            return true;
        }
        return false;
    }

    /**
     * Which window's scrollbar should be showing: the one the pointer is
     * in, or the one whose bar is being dragged, so the bar does not
     * fade out from under a drag that has wandered off it. Set before
     * the windows draw, since the draw is what eases it in and out.
     */
    static void markScrollbarsWanted(WindowGestures gestures, double mouseX,
                                     double mouseY) {
        ScrollbarDrag dragged = gestures.contentDrag() instanceof ScrollbarDrag
                ? (ScrollbarDrag)gestures.contentDrag() : null;
        // Only the window the pointer is really in shows its bar: one
        // covered by another is not being read, whatever its box says.
        WindowFrame pointed = dragged == null
                ? WindowFrame.drawnAt(mouseX, mouseY) : null;
        for (WindowFrame each : WindowFrame.drawnFrames()) {
            ChatFrame frame = (ChatFrame)each;
            frame.scrollbarWanted = dragged != null
                    ? frame.windowId.equals(dragged.windowId)
                    : frame == pointed;
        }
    }

    /** Takes hold of a window's member list by its edge; live at once. */
    static void armMembersResize(WindowGestures gestures,
                                 ChatTabActions actions, ChatFrame frame,
                                 Window window, double mouseX) {
        if (frame == null || window == null || window.isLocked()) {
            return;
        }
        WindowLayout.raise(window.getId());
        actions.selectWindow(window);
        gestures.startContentDrag(new MembersResize(window.getId(),
                mouseX - frame.members.screenLeft,
                ChatLayout.getMembersWidth(window)));
    }

    /** Whether a member list's edge is being dragged. */
    static boolean isResizingMembers(WindowGestures gestures) {
        return gestures.contentDrag() instanceof MembersResize;
    }

    /** A scrollbar being dragged: which window, and where it was grabbed. */
    private static final class ScrollbarDrag
            implements WindowGestures.ContentDrag {
        final String windowId;
        /** Pointer offset inside the thumb when it was grabbed. */
        final float grabOffset;

        ScrollbarDrag(String windowId, float grabOffset) {
            this.windowId = windowId;
            this.grabOffset = grabOffset;
        }

        /**
         * Maps the pointer onto the history: where the thumb's top sits
         * in the travel it has is where the view sits in what it can
         * reach. The pointer is read to the display pixel, as the grab
         * measured it, so the thumb stays on the spot it was taken hold
         * of.
         */
        @Override
        public void move(double mouseX, double mouseY) {
            ChatFrame frame = ChatFrame.find(this.windowId);
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
            float top = (float)(mouseY - this.grabOffset);
            float taken = (frame.scrollbarTrackBottom - thumbHeight - top)
                    / travel;
            // The thumb's travel is the stack's height in pixels, and the
            // rows are not all one height: the frame turns the share of
            // the travel into the row offset that stands there.
            ClientChatChannelViews.scrollTo(frame.view,
                    frame.scrollRowsForShare(taken), frame.contentRows(),
                    frame.roomLines());
        }

        @Override
        public void release() {}

        @Override
        public void cancel() {}

        @Override
        public boolean holdsPointer() {
            return false;
        }

        @Override
        public LostTalesMapCursor.Pose pose() {
            return null;
        }
    }

    /**
     * A window's member list being resized by its left edge: the list
     * grows as the edge is carried left and narrows as it goes right,
     * between its heads alone and a third of the window, and the words
     * beside it reflow live. The width is written into the layout without
     * persisting while the drag runs, once on release, and Escape puts
     * the stored width back.
     */
    private static final class MembersResize
            implements WindowGestures.ContentDrag {
        final String windowId;
        /** How far right of the list's edge the pointer took hold of it. */
        final double grabOffset;
        /** The width the window had stored, for Escape. */
        final double storedWidth;

        MembersResize(String windowId, double grabOffset, double storedWidth) {
            this.windowId = windowId;
            this.grabOffset = grabOffset;
            this.storedWidth = storedWidth;
        }

        /**
         * Follows the pointer with the list's edge: the list is as wide
         * as from where the edge is now to the window's right edge,
         * bounded as the list lays itself out.
         */
        @Override
        public void move(double mouseX, double mouseY) {
            ChatFrame frame = ChatFrame.find(this.windowId);
            Window window = WindowLayout.window(this.windowId);
            if (frame == null || window == null || window.isLocked()
                    || frame.scale <= 0.0F) {
                return;
            }
            double edge = mouseX - this.grabOffset;
            float width = ChatMemberList.clampWidth(
                    (float)((frame.members.screenRight - edge) / frame.scale),
                    frame.members.minWidth, frame.members.maxWidth);
            ChatLayout.setMembersWidth(this.windowId, width, false);
        }

        /** The width the edge was left at is written down, once. */
        @Override
        public void release() {
            WindowLayout.persist();
        }

        /** Escape means "as it was": the list takes back its width. */
        @Override
        public void cancel() {
            ChatLayout.setMembersWidth(this.windowId, this.storedWidth, false);
        }

        @Override
        public boolean holdsPointer() {
            return true;
        }

        @Override
        public LostTalesMapCursor.Pose pose() {
            return LostTalesMapCursor.Pose.RESIZE_HORIZONTAL;
        }
    }
}
