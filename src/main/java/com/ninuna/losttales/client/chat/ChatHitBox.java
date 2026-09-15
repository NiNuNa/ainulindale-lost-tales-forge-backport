package com.ninuna.losttales.client.chat;

/**
 * A rectangle something on screen answers on — a button's frame, a
 * glyph's square, a panel, a window — as its left and top edge and its
 * size in GUI pixels. It holds a point from its first pixel up to the
 * pixel past its last, exactly as everything the chat draws is
 * measured, so a box laid on a drawn thing answers on that thing's
 * pixels and no others. Every hover, press and hit test in the chat
 * asks this one question, so no two of them can read an edge
 * differently.
 */
final class ChatHitBox {
    final double left;
    final double top;
    final double width;
    final double height;

    ChatHitBox(double left, double top, double width, double height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    /** Whether the point lies in the box with these edges and size. */
    static boolean contains(double x, double y, double left, double top,
                            double width, double height) {
        return width > 0.0D && height > 0.0D
                && x >= left && x < left + width
                && y >= top && y < top + height;
    }

    boolean contains(double x, double y) {
        return contains(x, y, this.left, this.top, this.width, this.height);
    }

    /** The same box with {@code by} more pixels answering on every side. */
    ChatHitBox grown(double by) {
        return new ChatHitBox(this.left - by, this.top - by,
                this.width + 2.0D * by, this.height + 2.0D * by);
    }

    /** The pixel past the box's last column. */
    double right() {
        return this.left + this.width;
    }

    /** The pixel past the box's last row. */
    double bottom() {
        return this.top + this.height;
    }
}
