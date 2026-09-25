package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-window render state: the tab row, the tool strip, the window's box
 * on screen, the motion it is drawn with, and how far that box stands
 * toward filling the screen. Frames are keyed by window id and outlive
 * the screen, so tab easing and hit testing stay continuous across
 * opening and closing; a frame whose window no longer exists is pruned
 * on the next draw. A system with state of its own in every window
 * makes its own kind of frame ({@link #setMaker}): the chat keeps a
 * window's lines, member list and message toolbar in its frame.
 *
 * <p>Every window's box is the one {@link WindowPlacement} derives
 * from the window's percent offsets, with the newest line on the
 * baseline, the
 * window's input bar below it, and the tab row standing on the topmost
 * drawn line, or on one empty line when the view is empty. The box is
 * kept in fractional pixels, and the opening motion the window was drawn
 * with is kept alongside, so the row and the bar the screen draws land on
 * exactly the same fractional position as the lines.</p>
 */
public class WindowFrame {
    private static final Map<String, WindowFrame> FRAMES =
            new HashMap<String, WindowFrame>();

    public final String windowId;
    public final TabRow tabBar = new TabRow();
    /** The window's tool strip: where its controls stand, and their motions. */
    final ToolStrip.State toolStrip = new ToolStrip.State();
    /** The page shown while open instead of a conversation; null for none. */
    public PageTab page;
    public boolean drawn;
    public double boxLeft;
    public double boxTop;
    public double boxRight;
    public double boxBottom;
    /** The edge the newest message sits on; the bar hangs below it. */
    double baseline;
    /**
     * Top of the drawn message stack (screen y, motion included): the
     * edge the tab row stands a padding above. An open window's is its
     * box's own edge whatever its tab in front holds — a full window
     * cuts its topmost line there, and a shorter history leaves empty
     * rows under it — so switching tabs never moves the row; the closed
     * feed's sits on the lines it has.
     */
    double stackTop;
    /** Message-line room of the box drawn this frame, in pixels,
     *  measured so that {@code baseline - room} is itself a snapped
     *  display position: the content top is snapped as one value rather
     *  than as the difference of two snapped ones, so it never wobbles
     *  against a moving baseline while a resize runs. */
    public double room;
    /** Top of the input bar at rest, one chat line below the baseline. */
    private double restingBarTop;
    /** Chat scale the box was drawn at; sizes one empty line. */
    public float scale = 1.0F;
    /** Opening motion the window was drawn with this frame. */
    float motionX;
    public float motionY;
    /**
     * A window made from tabs carried out of their row fades in as it
     * appears under the pointer, on one quick beat; every other window
     * stands whole from the start.
     */
    private final MotionTransition appearMotion =
            new MotionTransition(MotionIds.CHAT_WINDOW_APPEAR);
    private boolean appearing;
    /** How much of its opacity the window showed when last drawn. */
    private float shown = 1.0F;
    /**
     * How far the window has come along its glide to the part of the
     * screen it fills, or back to its own box, eased from the moment it
     * was sent, so its box glides rather than jumping. Every glide is
     * one leg from the box drawn as it began ({@link #fillLegFrom},
     * null for the window's own box) to what it is bound for
     * ({@link #fillLegTo}, none for the window's own box).
     */
    private final MotionTransition fillMotion =
            new MotionTransition(MotionIds.CHAT_WINDOW_FILL, true);
    /** Whether {@link #fillMotion} has been advanced at all yet. */
    private boolean fillSeen;
    private WindowPlacement.Box fillLegFrom;
    private Window.ScreenFill fillLegFromFill = Window.ScreenFill.NONE;
    private Window.ScreenFill fillLegTo = Window.ScreenFill.NONE;
    /** The box the window was last laid in ({@link #begin}). */
    private WindowPlacement.Box placed;
    /**
     * How long the row still counts a finished glide as gliding. The
     * frame a glide settles on moves the edges its last step, and the
     * tabs have to take that step with them rather than ease after it.
     */
    private static final long GLIDE_TAIL_NANOS = 100L * 1000000L;
    /** When an advance last found the glide still moving; 0 before one has. */
    private long fillGlideNanos;

    /** Makes the frame of a window; a system with state of its own in every window makes its own kind. */
    public interface Maker {
        WindowFrame make(String windowId);
    }

    private static Maker maker = new Maker() {
        @Override
        public WindowFrame make(String windowId) {
            return new WindowFrame(windowId);
        }
    };

    protected WindowFrame(String windowId) {
        this.windowId = windowId;
    }

    /** What makes every window's frame from now on. */
    public static synchronized void setMaker(Maker made) {
        maker = made;
        FRAMES.clear();
    }

    /** Shows a page in the window: what the window held before is let go. */
    public void showPage(PageTab shown) {
        this.page = shown;
        pageShown();
    }

    /** What a kind of frame lets go of when its window shows a page. */
    protected void pageShown() {}

    public static synchronized WindowFrame of(Window window) {
        WindowFrame frame = FRAMES.get(window.getId());
        if (frame == null) {
            frame = maker.make(window.getId());
            FRAMES.put(window.getId(), frame);
        }
        return frame;
    }

    /** Every window's frame kept now. */
    public static synchronized List<WindowFrame> frames() {
        return new ArrayList<WindowFrame>(FRAMES.values());
    }

    /** Forgets every window's frame. */
    public static synchronized void clearFrames() {
        FRAMES.clear();
    }

    public static synchronized WindowFrame find(String windowId) {
        return FRAMES.get(windowId);
    }

    /** The frontmost drawn window under a screen point, or null. */
    public static WindowFrame drawnAt(double mouseX, double mouseY) {
        List<WindowFrame> frames = drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            if (frames.get(index).contains(mouseX, mouseY)) {
                return frames.get(index);
            }
        }
        return null;
    }

    /** Frames of windows drawn this frame, back to front. */
    public static synchronized List<WindowFrame> drawnFrames() {
        List<WindowFrame> result = new ArrayList<WindowFrame>();
        for (Window window : WindowLayout.stacked()) {
            WindowFrame frame = FRAMES.get(window.getId());
            if (frame != null && frame.drawn) {
                result.add(frame);
            }
        }
        return result;
    }

    /** Drops frames of windows that no longer exist. */
    public static synchronized void prune(List<Window> windows) {
        if (FRAMES.size() <= windows.size()) {
            return;
        }
        List<String> stale = new ArrayList<String>();
        for (String id : FRAMES.keySet()) {
            boolean alive = false;
            for (int index = 0; index < windows.size(); index++) {
                if (windows.get(index).getId().equals(id)) {
                    alive = true;
                    break;
                }
            }
            if (!alive) {
                stale.add(id);
            }
        }
        for (String id : stale) {
            FRAMES.remove(id);
        }
    }

    /** The window's tabs that can be shown now. */
    public static List<WindowTab> visibleTabs(Window window) {
        List<WindowTab> tabs = window.getTabs();
        List<WindowTab> result = new ArrayList<WindowTab>(tabs.size());
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index).isAvailable()) {
                result.add(tabs.get(index));
            }
        }
        return result;
    }

    /** The tab in front, or the first visible tab when it is unavailable. */
    public static WindowTab activeTab(Window window,
                                      List<WindowTab> visibleTabs) {
        WindowTab active = window.getActiveTab();
        if (active != null && visibleTabs.contains(active)) {
            return active;
        }
        return visibleTabs.isEmpty() ? null : visibleTabs.get(0);
    }

    /**
     * Starts the window's frame from its placement box. The box is
     * dragged in fractions of a GUI pixel so it follows the mouse
     * exactly, and laid on whole display pixels to be drawn: the text,
     * the heads and the emojis in it are pixel art, and between two
     * pixels they crawl. A display pixel is the finest step the screen
     * has, so the motion loses nothing by landing on one.
     */
    public void begin(WindowPlacement.Box box, float chatScale,
               float openingMotionX, float openingMotionY) {
        this.placed = box;
        this.boxLeft = LostTalesDisplayPixels.snap(box.x);
        this.boxTop = LostTalesDisplayPixels.snap(box.y);
        this.boxRight = this.boxLeft + box.width;
        this.boxBottom = this.boxTop + box.height;
        this.baseline = LostTalesDisplayPixels.snap(box.baseline());
        this.restingBarTop = LostTalesDisplayPixels.snap(box.barTop());
        this.scale = chatScale <= 0.0F ? 1.0F : chatScale;
        this.motionX = openingMotionX;
        this.motionY = openingMotionY;
        // The content top snapped as one value; see {@link #room}.
        this.room = this.baseline
                - LostTalesDisplayPixels.snap(box.baseline() - box.room);
        // The stack fills the box until the draw lays it on the drawn
        // baseline.
        this.stackTop = this.baseline + this.motionY - this.room;
    }

    /**
     * Moves the window's fill motion on to this instant, toward the part
     * of the screen the window fills. Called once a frame before the
     * window is measured; the first call stands the window in its state
     * rather than travelling into it. A change of fill starts a new
     * leg from the box drawn last — the window's own box, the part of
     * the screen it filled, or wherever a glide had brought it — so a
     * window sent from one fill to another sets out from where it
     * stands; a leg back to the window's own box forgets its start once
     * it arrives, so the box is the window's own again exactly.
     */
    public void advanceFill(Window.ScreenFill fill) {
        long now = System.nanoTime();
        Window.ScreenFill wanted = fill == null
                ? Window.ScreenFill.NONE : fill;
        if (this.fillSeen && !this.fillMotion.isSettled()) {
            this.fillGlideNanos = now;
        }
        if (this.fillSeen && !wanted.equals(this.fillLegTo)) {
            this.fillLegFromFill = this.fillLegTo;
            this.fillLegFrom = this.placed;
            this.fillLegTo = wanted;
            this.fillMotion.settle(false);
        } else if (!this.fillSeen) {
            this.fillLegTo = wanted;
        }
        this.fillSeen = true;
        this.fillMotion.advance(now, true);
        if (this.fillLegTo == Window.ScreenFill.NONE
                && this.fillMotion.isSettled()) {
            this.fillLegFrom = null;
            this.fillLegFromFill = Window.ScreenFill.NONE;
        }
    }

    /**
     * Starts the window's fade in: a window just made from tabs carried
     * out of their row, or moved into a window of their own.
     */
    public void beginAppearing() {
        this.appearMotion.settle(false);
        this.appearing = true;
    }

    /**
     * How much of its opacity the window shows this frame: all of it,
     * but for a window still fading in. Asked once a frame, before the
     * window is drawn.
     */
    public float appearShare() {
        if (!this.appearing) {
            this.shown = 1.0F;
            return 1.0F;
        }
        float share = this.appearMotion.advance(System.nanoTime(), true);
        if (this.appearMotion.isSettled()) {
            this.appearing = false;
        }
        this.shown = Math.max(0.0F, Math.min(1.0F, share));
        return this.shown;
    }

    /**
     * The share {@link #appearShare} last gave: what the window's input
     * bar, drawn after the window, fades in by.
     */
    public float shownShare() {
        return this.shown;
    }

    /** Whether {@link #advanceFill} has placed the window at all yet. */
    boolean hasSeenFill() {
        return this.fillSeen;
    }

    /** The box the running leg set out from, or null for the window's own. */
    WindowPlacement.Box fillLegFrom() {
        return this.fillLegFrom;
    }

    /** What the running leg is bound for; none for the window's own box. */
    Window.ScreenFill fillLegTo() {
        return this.fillLegTo;
    }

    /**
     * Stands the window in {@code fill} at once, with no glide: a part
     * whose edge the player is dragging follows the pointer rigidly.
     */
    void holdFill(Window.ScreenFill fill) {
        this.fillLegTo = fill == null ? Window.ScreenFill.NONE : fill;
        this.fillLegFrom = null;
        this.fillLegFromFill = Window.ScreenFill.NONE;
        this.fillSeen = true;
        this.fillMotion.settle(true);
    }

    /** How far along its leg the window has come, 0..1. */
    float fillShare() {
        return this.fillMotion.clamped();
    }

    /**
     * How far the window stands toward filling the whole screen, 0..1:
     * what the fullscreen control's glyph crosses over by, so it points
     * inward exactly as the window takes the whole screen and outward
     * as it gives it back — or fills a half or a quarter instead.
     */
    public float fullShare() {
        if (this.fillLegTo == Window.ScreenFill.FULL) {
            return this.fillMotion.clamped();
        }
        return this.fillLegFromFill == Window.ScreenFill.FULL
                ? 1.0F - this.fillMotion.clamped() : 0.0F;
    }

    /**
     * Whether the window stands in its own box, and not in a part of
     * the screen or on the way to or from one. Its edges are its own to
     * resize only then.
     */
    boolean isInOwnBox() {
        return !this.fillSeen || (this.fillLegTo == Window.ScreenFill.NONE
                && this.fillLegFrom == null);
    }

    /**
     * Whether the window is gliding to or from a part of the screen, the
     * frame the glide settles on and a moment after it included.
     */
    public boolean isFillGliding() {
        return this.fillSeen && (!this.fillMotion.isSettled()
                || System.nanoTime() - this.fillGlideNanos
                        < GLIDE_TAIL_NANOS);
    }

    /** Records where the drawn message stack ended up this frame. */
    public void setStackTop(double screenY) {
        this.stackTop = screenY;
    }

    /**
     * Left edge as drawn this frame, opening motion included and laid on
     * a whole display pixel. Everything the window draws is measured
     * from here, so everything in it lands on the same grid: the pixel
     * art especially, which is sampled one texel to one pixel and shows
     * every fraction of a pixel as a texel of the wrong width.
     */
    public double drawnLeft() {
        return LostTalesDisplayPixels.snap(this.boxLeft + this.motionX);
    }

    /** The edge the newest message sits on, as drawn this frame. */
    public double drawnBaseline() {
        return LostTalesDisplayPixels.snap(this.baseline + this.motionY);
    }

    /**
     * Top of the window's input bar at rest. The bar has an entrance of
     * its own and does not ride the window's opening motion.
     */
    public double barTop() {
        return this.restingBarTop;
    }

    /**
     * Bottom of the tab row (screen y, fractional): the tool strip and
     * the top margin above the drawn message stack — the row's last
     * pixel row is the window's top rule, the tool strip hangs under it,
     * and the first content pixel lies the margin below that, so the
     * topmost line keeps clear of the strip. The stack of an open window
     * ends on its box's own edge, so the row and the box top never drift
     * apart whatever the chat scale is or the tab in front holds.
     */
    public double tabRowBottom() {
        return historyTop() - WindowPlacement.TOOL_STRIP_HEIGHT;
    }

    /**
     * Top of the history's panel (screen y, fractional): the margin
     * above the drawn message stack, under the tool strip.
     */
    public double historyTop() {
        return this.stackTop - WindowPlacement.HISTORY_TOP_MARGIN;
    }

    /** The window's box as it is drawn this frame, opening motion included. */
    LostTalesUiHitBox drawnBox() {
        return new LostTalesUiHitBox(drawnLeft(), this.boxTop + this.motionY,
                this.boxRight - this.boxLeft, this.boxBottom - this.boxTop);
    }

    /**
     * Whether the point lies within the window as it is drawn — its
     * strip, its messages and its bar, opening motion included — from
     * its first pixel up to the pixel past its last, as everything it
     * draws is measured. The border outside it is the resize band's.
     */
    public boolean contains(double x, double y) {
        return LostTalesUiHitBox.contains(x, y, drawnLeft(),
                this.boxTop + this.motionY, this.boxRight - this.boxLeft,
                this.boxBottom - this.boxTop);
    }
}
