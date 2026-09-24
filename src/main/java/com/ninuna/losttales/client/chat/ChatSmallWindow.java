package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * One small window: a kind, what it holds, and where it stands. Its box
 * takes its title strip — one tab, as a chat window's row with a single
 * tab in it — and under that its content; the frame runs just outside
 * the box, as a chat window's does. The window's place may stand between
 * whole pixels while it is moved; it is drawn laid on the display's grid
 * and every hit test asks the box it was drawn in.
 */
final class ChatSmallWindow {
    /** The title strip: a chat window's tab row, without the tool strip. */
    static final int STRIP_HEIGHT = ChatChannelTabBar.ROW_HEIGHT;
    /** How far a window rises into place as it opens, in pixels. */
    static final float RISE = 5.0F;

    final ChatSmallWindowKind kind;
    /** Which of its kind it is: a card's person; empty for a kind with one window. */
    final String key;
    final ChatSmallWindowContent content;
    /** The box, strip and content: where the window stands, fractions and all. */
    double left;
    double top;
    int width;
    int height;
    private boolean open = true;
    private final MotionTransition openness =
            new MotionTransition(MotionIds.CHAT_SMALL_WINDOW_OPEN);
    final LostTalesUiButtonMotion closeMotion = new LostTalesUiButtonMotion(
            LostTalesUiButtonMotion.Character.SNAP);
    /** Where the box was drawn this frame, laid on the display's grid. */
    double drawnLeft;
    double drawnTop;
    /** How far it was drawn from the whole pixels its content is laid out on. */
    float fractionX;
    float fractionY;
    /** The strip's one tab as it was drawn this frame; null before the first draw. */
    ChatChannelTabBar.LoneTab tab;
    /** How far it had opened as it was drawn this frame. */
    float shownShare;
    /**
     * Whether the player has given the window its size, in this opening
     * or an earlier one of its kind. One they have not takes its
     * content's own size as it opens, and again as its menu turns to
     * something else.
     */
    boolean sized;
    /** Whether it waits, undrawn, for the input bar it works on: no chat window is open. */
    boolean hidden;

    ChatSmallWindow(ChatSmallWindowKind kind, String key,
                    ChatSmallWindowContent content, double left, double top,
                    int width, int height) {
        this.kind = kind;
        this.key = key == null ? "" : key;
        this.content = content;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.drawnLeft = left;
        this.drawnTop = top;
    }

    boolean isOpen() {
        return this.open;
    }

    /** Opens it again while it is still fading out, or starts it closing. */
    void setOpen(boolean open) {
        this.open = open;
    }

    /** How far it has opened now, 0 to 1, stepping its fade. */
    float advanceShare(long nanos) {
        return this.openness.advance(nanos, this.open);
    }

    /** Whether it has faded out altogether and can go. */
    boolean isGone() {
        return !this.open && this.shownShare <= 0.0F;
    }

    /** The box as it was drawn this frame. */
    LostTalesUiHitBox drawnBox() {
        return new LostTalesUiHitBox(this.drawnLeft, this.drawnTop,
                this.width, this.height);
    }

    /** The box where it stands, fractions and all. */
    LostTalesUiHitBox box() {
        return new LostTalesUiHitBox(this.left, this.top, this.width,
                this.height);
    }

    /**
     * The content box in its own whole pixels: under the strip, the
     * space the content is drawn and asked in, the matrix having moved
     * by the fraction the window stands on.
     */
    LostTalesUiHitBox wholeContentBox() {
        return new LostTalesUiHitBox(Math.floor(this.drawnLeft),
                Math.floor(this.drawnTop) + STRIP_HEIGHT, this.width,
                Math.max(0, this.height - STRIP_HEIGHT));
    }

    /** Whether the point is on the window as drawn, its strip and content. */
    boolean contains(double x, double y) {
        return this.open && drawnBox().contains(x, y);
    }

    /** Whether the point is on the title strip as drawn. */
    boolean stripContains(double x, double y) {
        return this.open && LostTalesUiHitBox.contains(x, y, this.drawnLeft,
                this.drawnTop, this.width, STRIP_HEIGHT);
    }

    /** Whether the point is on the strip's cross, as drawn. */
    boolean closeContains(double x, double y) {
        return this.open && this.tab != null && this.tab.closeBox.contains(
                x - this.fractionX, y - this.fractionY);
    }

    /**
     * The edge or corner of the window the point is on: the band
     * {@link ChatWindowGestures#RESIZE_BORDER} wide just outside the box,
     * a corner reaching {@link ChatWindowGestures#RESIZE_CORNER} along
     * both its edges, as a chat window's do; null anywhere else.
     */
    ChatWindowGestures.ResizeEdge edgeAt(double x, double y) {
        if (!this.open) {
            return null;
        }
        LostTalesUiHitBox box = drawnBox();
        if (!box.grown(ChatWindowGestures.RESIZE_BORDER).contains(x, y)
                || box.contains(x, y)) {
            return null;
        }
        double right = box.left + box.width;
        double bottom = box.top + box.height;
        boolean onLeft = x < box.left;
        boolean onRight = x >= right;
        boolean onTop = y < box.top;
        boolean onBottom = y >= bottom;
        boolean nearLeft = x <= box.left + ChatWindowGestures.RESIZE_CORNER;
        boolean nearRight = x >= right - ChatWindowGestures.RESIZE_CORNER;
        boolean nearTop = y <= box.top + ChatWindowGestures.RESIZE_CORNER;
        boolean nearBottom = y >= bottom - ChatWindowGestures.RESIZE_CORNER;
        if ((onTop || onLeft) && nearTop && nearLeft) {
            return ChatWindowGestures.ResizeEdge.TOP_LEFT;
        }
        if ((onTop || onRight) && nearTop && nearRight) {
            return ChatWindowGestures.ResizeEdge.TOP_RIGHT;
        }
        if ((onBottom || onLeft) && nearBottom && nearLeft) {
            return ChatWindowGestures.ResizeEdge.BOTTOM_LEFT;
        }
        if ((onBottom || onRight) && nearBottom && nearRight) {
            return ChatWindowGestures.ResizeEdge.BOTTOM_RIGHT;
        }
        if (onLeft) {
            return ChatWindowGestures.ResizeEdge.LEFT;
        }
        if (onRight) {
            return ChatWindowGestures.ResizeEdge.RIGHT;
        }
        return onTop ? ChatWindowGestures.ResizeEdge.TOP
                : ChatWindowGestures.ResizeEdge.BOTTOM;
    }

    /** The name on its strip: its content's, else its kind's. */
    String title() {
        String title = this.content.stripTitle();
        return title != null ? title : this.kind.title();
    }

    /** The narrowest the window may be: its content's, and room for its tab. */
    int minWidth() {
        return Math.max(this.content.minWidth(), MIN_STRIP_WIDTH);
    }

    /** The shortest the window may be: its strip over its content's least. */
    int minHeight() {
        return STRIP_HEIGHT + this.content.minHeight();
    }

    /** Room for the tab's icon and cross with a few letters of its name between. */
    private static final int MIN_STRIP_WIDTH = 64;
}
