package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * A split's divider being dragged: the room is shared where the pointer
 * carries it, each side keeping a quarter at least, live without writing
 * the layout and once on release; Escape puts the share back. A locked
 * window's divider moves too, as its member list's edge does.
 */
final class SplitDividerDrag implements WindowGestures.ContentDrag {
    private final String windowId;
    /** A page of the split, which names it. */
    private final WindowTab side;
    private final boolean stacked;
    /** How far into the divider the pointer took hold of it. */
    private final double grabOffset;
    /** The share the split had, for Escape. */
    private final double storedShare;

    SplitDividerDrag(String windowId, WindowSplit split, double grabOffset) {
        this.windowId = windowId;
        this.side = split.first();
        this.stacked = split.isStacked();
        this.grabOffset = grabOffset;
        this.storedShare = split.share();
    }

    @Override
    public void move(double mouseX, double mouseY) {
        WindowFrame frame = WindowFrame.find(this.windowId);
        if (frame == null || !frame.drawn) {
            return;
        }
        LostTalesUiHitBox room = WindowDrawing.contentBox(frame);
        double share = this.stacked
                ? WindowSplit.shareAt(mouseY - this.grabOffset, room.top,
                        room.top + room.height)
                : WindowSplit.shareAt(mouseX - this.grabOffset, room.left,
                        room.left + room.width);
        WindowLayout.shareSplit(this.side, share, false);
    }

    /** The share the divider was left at is written down, once. */
    @Override
    public void release() {
        WindowLayout.persist();
    }

    @Override
    public void cancel() {
        WindowLayout.shareSplit(this.side, this.storedShare, false);
    }

    @Override
    public boolean holdsPointer() {
        return true;
    }

    @Override
    public LostTalesMapCursor.Pose pose() {
        return this.stacked ? LostTalesMapCursor.Pose.RESIZE_VERTICAL
                : LostTalesMapCursor.Pose.RESIZE_HORIZONTAL;
    }
}
