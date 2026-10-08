package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * One sub-window: a kind, what it holds, the window it belongs to, where
 * it stands and whether it is locked. Its box takes its strip — a
 * window's tool strip holding its icon, its name and its controls
 * ({@link SubWindowStrip}) — and under that its content; the frame runs
 * just outside the box, as a window's does.
 *
 * <p>A sub-window opens locked, at the place its kind was last locked in
 * or where its opener puts it. Locked, it is measured from its window's
 * room — the window's box, a frame and two clear pixels in from its edges
 * — so it rides along as that window moves, and it can be neither moved
 * nor resized. Unlocked, it stands where the player carries it on the
 * screen, past its window's edges if they like, and stays there while the
 * window moves. Either way it is held on the screen. A window on the bare
 * screen has the screen for its room.</p>
 *
 * <p>Its place may stand between whole pixels while it is moved; it is
 * drawn laid on the display's grid and every hit test asks the box it was
 * drawn in.</p>
 */
public final class SubWindow {
    /** The strip across its top: a window's tool strip. */
    public static final int STRIP_HEIGHT = SubWindowStrip.HEIGHT;
    /** How far a window rises into place as it opens, in pixels. */
    static final float RISE = 5.0F;

    final SubWindowKind kind;
    /** Which of its kind it is: a card's person; empty for a kind with one window. */
    final String key;
    public final SubWindowContent content;
    /** The window it belongs to; null for one on the bare screen. */
    public String parentId;
    /**
     * Where it stands, fractions and all: from its room's top left while
     * locked, on the screen while unlocked.
     */
    double x;
    double y;
    /** The size the player gave it, or its content's own. */
    int wantedWidth;
    int wantedHeight;
    /** Whether it stands in its place in its window, and neither moves nor resizes. */
    boolean locked = true;
    /** The room it was last laid out in; null while its window is not drawn. */
    LostTalesUiHitBox room;
    /** Where it stands on the screen, as laid out. */
    public double left;
    public double top;
    int width;
    int height;
    private boolean open = true;
    private final MotionTransition openness =
            new MotionTransition(MotionIds.WINDOW_SUB_OPEN);
    /** How far it shows while it may be tucked away ({@link SubWindowContent#isTucked}). */
    private final MotionTransition untucked =
            new MotionTransition(MotionIds.WINDOW_SUB_OPEN);
    final LostTalesUiButtonMotion closeMotion = new LostTalesUiButtonMotion(
            LostTalesUiButtonMotion.Character.SNAP);
    final LostTalesUiButtonMotion lockMotion = new LostTalesUiButtonMotion(
            LostTalesUiButtonMotion.Character.LIFT);
    /** The padlock on its strip, turning as it is locked and unlocked. */
    final LockAnimation lock = new LockAnimation();
    /** How far the grip has lit under the pointer, and when that was last stepped. */
    float gripFade;
    long gripNanos;
    /** When a press the padlock held back last lit it; 0 for never. */
    long lockNudgeNanos;
    /** Where the box was drawn this frame, laid on the display's grid. */
    double drawnLeft;
    double drawnTop;
    /** How far it was drawn from the whole pixels its content is laid out on. */
    public float fractionX;
    public float fractionY;
    /** The strip as it was drawn this frame; null before the first draw. */
    SubWindowStrip strip;
    /** How far it had opened as it was drawn this frame, tucked away or not. */
    float shownShare;
    /** How far it had opened as it was drawn this frame, leaving its tucking aside: what it rises by. */
    float openedShare;
    /**
     * Whether the player has given the window its size, in this opening
     * or an earlier one of its kind. One they have not takes its
     * content's own size as it opens, and again as its menu turns to
     * something else.
     */
    boolean sized;
    /**
     * Whether it waits, undrawn: its window is not drawn, or it works
     * on an input bar and no window is open to carry one.
     */
    boolean hidden;

    SubWindow(SubWindowKind kind, String key,
                    SubWindowContent content, String parentId) {
        this.kind = kind;
        this.key = key == null ? "" : key;
        this.content = content;
        this.parentId = parentId;
        this.untucked.settle(!content.isTucked());
    }

    public boolean isOpen() {
        return this.open;
    }

    /** Whether it stands locked in its window's room. */
    public boolean isLocked() {
        return this.locked;
    }

    /** Opens it again while it is still fading out, or starts it closing. */
    void setOpen(boolean open) {
        this.open = open;
    }

    /** How far it shows now, 0 to 1, stepping its fades: opening and closing, tucking away and back. */
    float advanceShare(long nanos) {
        this.openedShare = this.openness.advance(nanos, this.open);
        return this.openedShare
                * this.untucked.advance(nanos, !this.content.isTucked());
    }

    /** Whether it has faded out altogether and can go. */
    boolean isGone() {
        return !this.open && this.shownShare <= 0.0F;
    }

    /** Whether it belongs to the window {@code windowId}, null standing for the screen. */
    public boolean belongsTo(String windowId) {
        return this.parentId == null ? windowId == null
                : this.parentId.equals(windowId);
    }

    /**
     * Lays the window out: as large as the player wants it, never larger
     * than {@code screen} nor smaller than its least, where it stands —
     * measured from {@code room} while locked — and held on the screen.
     */
    void layOut(LostTalesUiHitBox room, LostTalesUiHitBox screen) {
        this.room = room;
        this.width = fitted(this.wantedWidth, minWidth(),
                (int)Math.floor(screen.width));
        this.height = fitted(this.wantedHeight, minHeight(),
                (int)Math.floor(screen.height));
        double wantedLeft = this.locked ? room.left + this.x : this.x;
        double wantedTop = this.locked ? room.top + this.y : this.y;
        this.left = screen.left + held(wantedLeft - screen.left,
                screen.width - this.width);
        this.top = screen.top + held(wantedTop - screen.top,
                screen.height - this.height);
    }

    /** A size no smaller than {@code min} and, above that, no larger than {@code room}. */
    static int fitted(int size, int min, int room) {
        return Math.max(min, Math.min(room, size));
    }

    /** A place from 0 to {@code most}, and 0 where there is no room at all. */
    static double held(double place, double most) {
        return Math.max(0.0D, Math.min(most, place));
    }

    /** The box as it was drawn this frame. */
    LostTalesUiHitBox drawnBox() {
        return new LostTalesUiHitBox(this.drawnLeft, this.drawnTop,
                this.width, this.height);
    }

    /** The box where it stands, fractions and all. */
    public LostTalesUiHitBox box() {
        return new LostTalesUiHitBox(this.left, this.top, this.width,
                this.height);
    }

    /**
     * The content box in its own whole pixels: under the strip, the
     * space the content is drawn and asked in, the matrix having moved
     * by the fraction the window stands on.
     */
    public LostTalesUiHitBox wholeContentBox() {
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
        return onControl(this.strip == null ? null : this.strip.closeBox,
                x, y);
    }

    /** Whether the point is on the strip's padlock, as drawn. */
    boolean lockContains(double x, double y) {
        return onControl(this.strip == null ? null : this.strip.lockBox,
                x, y);
    }

    /** Whether the point is on the strip's grip, as drawn. */
    boolean gripContains(double x, double y) {
        return onControl(this.strip == null ? null : this.strip.gripBox,
                x, y);
    }

    private boolean onControl(LostTalesUiHitBox control, double x,
                              double y) {
        return this.open && control != null
                && control.contains(x - this.fractionX, y - this.fractionY);
    }

    /**
     * The edge or corner of the window the point is on: the band
     * {@link WindowGestures#RESIZE_BORDER} wide just outside the box,
     * a corner reaching {@link WindowGestures#RESIZE_CORNER} along
     * both its edges, as a window's do; null anywhere else, anywhere while
     * it is locked, and on a permanent window, which keeps its own size.
     */
    WindowGestures.ResizeEdge edgeAt(double x, double y) {
        if (!this.open || this.locked || this.content.isPermanent()) {
            return null;
        }
        LostTalesUiHitBox box = drawnBox();
        if (!box.grown(WindowGestures.RESIZE_BORDER).contains(x, y)
                || box.contains(x, y)) {
            return null;
        }
        double right = box.left + box.width;
        double bottom = box.top + box.height;
        boolean onLeft = x < box.left;
        boolean onRight = x >= right;
        boolean onTop = y < box.top;
        boolean onBottom = y >= bottom;
        boolean nearLeft = x <= box.left + WindowGestures.RESIZE_CORNER;
        boolean nearRight = x >= right - WindowGestures.RESIZE_CORNER;
        boolean nearTop = y <= box.top + WindowGestures.RESIZE_CORNER;
        boolean nearBottom = y >= bottom - WindowGestures.RESIZE_CORNER;
        if ((onTop || onLeft) && nearTop && nearLeft) {
            return WindowGestures.ResizeEdge.TOP_LEFT;
        }
        if ((onTop || onRight) && nearTop && nearRight) {
            return WindowGestures.ResizeEdge.TOP_RIGHT;
        }
        if ((onBottom || onLeft) && nearBottom && nearLeft) {
            return WindowGestures.ResizeEdge.BOTTOM_LEFT;
        }
        if ((onBottom || onRight) && nearBottom && nearRight) {
            return WindowGestures.ResizeEdge.BOTTOM_RIGHT;
        }
        if (onLeft) {
            return WindowGestures.ResizeEdge.LEFT;
        }
        if (onRight) {
            return WindowGestures.ResizeEdge.RIGHT;
        }
        return onTop ? WindowGestures.ResizeEdge.TOP
                : WindowGestures.ResizeEdge.BOTTOM;
    }

    /** The name on its strip: its content's, else its kind's. */
    String title() {
        String title = this.content.stripTitle();
        return title != null ? title : this.kind.title();
    }

    /** The narrowest the window may be: its content's, and room for its strip. */
    int minWidth() {
        return Math.max(this.content.minWidth(), SubWindowStrip.minWidth());
    }

    /** The shortest the window may be: its strip over its content's least. */
    int minHeight() {
        return STRIP_HEIGHT + this.content.minHeight();
    }
}
