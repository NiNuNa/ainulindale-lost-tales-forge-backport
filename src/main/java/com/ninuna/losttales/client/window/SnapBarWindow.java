package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * The snap bar: a sub-window at the top centre of every screen, which
 * cannot be closed, holding every layout the screen has room for, the
 * suggestions for the carried window first. As Windows 11 keeps its own,
 * it stands hidden above the screen. A window carried near the top brings
 * it out peeking, only its bottom frame and padding showing, and it comes
 * a little further down as the pointer nears it, never so far that it
 * covers the top edge's own snap band: while it peeks it answers nothing
 * and the screen's edges decide, so the band below it still fills the
 * screen as the top edge does. The pointer touching what shows of it
 * brings it all the way down: the zone under the pointer is then where
 * the window lands, and the bar round the zones lands it nowhere; a
 * pointer pressed against the top edge above the zones points at the zone
 * standing below it. It stays down while the pointer is within
 * {@link #REACH} of it, peeks again once the pointer goes further, and
 * hides as the pointer leaves the top of the screen or the carry ends.
 */
final class SnapBarWindow extends SubWindowContent {
    /** The kind the screen opens it as, and the layout file keeps its place by. */
    static final SubWindowKind KIND = SubWindowKind.register("snap_bar",
            "gui.losttales.window.sub.snap_bar");
    /** The panel's own frame, which the sub-window's frame stands in for. */
    private static final int FRAME = WindowPlacement.FRAME_WIDTH;
    /**
     * How far below the bar, all the way down, the pointer brings it out;
     * and how far off the bar the pointer may go while it is all the way
     * down before it peeks again.
     */
    static final int REACH = 24;
    /** How much of the bar shows as it peeks: its bottom frame and padding. */
    static final int PEEK = FRAME + SnapLayouts.PADDING;
    /**
     * The most of the bar that shows before the pointer touches it: half
     * the depth of the top edge's snap band ({@link WindowGestures#SNAP_REACH}),
     * so a pointer coming up to the bar crosses the other half of that
     * band first.
     */
    static final int PEEK_MOST = (int)(WindowGestures.SNAP_REACH / 2.0D);
    /** The most suggestions that lead the layouts: the halves and the half with two quarters. */
    private static final int SUGGESTION_SLOTS = 2;

    /** Where the bar stands: hidden, peeking, or all the way down. */
    enum Stage {
        HIDDEN,
        PEEKING,
        REVEALED
    }

    /** The bar travelling from its peek all the way down, and back. */
    private final MotionTransition reveal =
            new MotionTransition(MotionIds.WINDOW_SNAP_LAYOUTS, true);
    private Stage stage = Stage.HIDDEN;
    /** How far down the screen the peeking bar's bottom edge stands. */
    private double peek;
    /** How far down the screen the peek is bound for. */
    private double peekTarget;
    /** When the peek last moved on; 0 before it first does. */
    private long peekNanos;
    /** How far above where it stands the bar is drawn this frame. */
    private double above;
    /** The window last carried, whose suggestions lead the bar; null before any. */
    private String carried;
    /** The zone the pointer was last on, or -1. */
    private int lit = -1;
    /** The screen's size, for the layouts it offers. */
    private int screenWidth;
    private int screenHeight;
    /** Where the whole sub-window stands, strip and all; null before it is laid out. */
    private LostTalesUiHitBox standsAt;

    SnapBarWindow() {
        // Up from the start, so the bar comes down the first time too.
        this.reveal.settle(false);
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public boolean isTucked() {
        return this.stage == Stage.HIDDEN;
    }

    @Override
    public double drawnAbove() {
        return this.above;
    }

    @Override
    public LostTalesUiSheet stripIcon() {
        return null;
    }

    /** Takes the screen's size: the layouts it offers depend on it. */
    void bind(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * Where its sub-window stands this frame, strip and all, at
     * {@code nanos}: moves the peek and the way down on, every frame,
     * shown or not.
     */
    void standsAt(LostTalesUiHitBox box, long nanos) {
        this.standsAt = box;
        LostTalesUiHitBox resting = resting();
        if (resting == null) {
            this.above = 0.0D;
            return;
        }
        boolean out = this.stage != Stage.HIDDEN;
        float down = this.reveal.advance(nanos, this.stage == Stage.REVEALED);
        double elapsed = this.peekNanos == 0L ? 0.0D
                : (nanos - this.peekNanos) / 1.0E9D;
        this.peekNanos = nanos;
        this.peek = Motions.followTravel(MotionIds.WINDOW_SNAP_BAR_PEEK,
                this.peek, out ? this.peekTarget : 0.0D, elapsed);
        double bottom = this.peek + down * (resting.bottom() - this.peek);
        this.above = resting.bottom() - bottom;
    }

    private int slots(Minecraft minecraft) {
        return SnapLayouts.offered(minecraft, this.screenWidth,
                this.screenHeight).size() + SUGGESTION_SLOTS;
    }

    @Override
    public int naturalWidth() {
        return SnapLayouts.panelWidth(slots(Minecraft.getMinecraft()),
                Integer.MAX_VALUE) - 2 * FRAME;
    }

    @Override
    public int naturalHeight(int width) {
        return SnapLayouts.panelHeight(1, 1, SnapLayouts.thumbHeight(
                this.screenWidth, this.screenHeight)) - 2 * FRAME;
    }

    @Override
    public int minWidth() {
        return naturalWidth();
    }

    @Override
    public int minHeight() {
        return naturalHeight(naturalWidth());
    }

    /** The whole bar where it rests all the way down, its frame included; null before it is laid out. */
    private LostTalesUiHitBox resting() {
        return this.standsAt == null ? null : this.standsAt.grown(FRAME);
    }

    /** The content's box where it rests: under the strip. */
    private LostTalesUiHitBox contentBox() {
        LostTalesUiHitBox box = this.standsAt;
        return box == null ? null : new LostTalesUiHitBox(box.left,
                box.top + SubWindow.STRIP_HEIGHT, box.width,
                Math.max(0.0D, box.height - SubWindow.STRIP_HEIGHT));
    }

    /**
     * One frame of the carry of window {@code windowId} with the pointer at
     * ({@code x}, {@code y}) on the screen. Answers, while the bar is all
     * the way down, the part of the screen whose zone is under the
     * pointer or below it on the top edge; none on the bar between zones;
     * and null off the bar, and while it peeks or is hidden.
     */
    Window.ScreenFill follow(Minecraft minecraft, String windowId, double x,
                             double y) {
        LostTalesUiHitBox resting = resting();
        LostTalesUiHitBox content = contentBox();
        SnapLayouts.Panel panel = resting == null ? null
                : panel(minecraft, windowId, content.left, content.top);
        if (panel == null) {
            hide();
            return null;
        }
        this.carried = windowId;
        // The pointer touches the bar where it is drawn now, before this
        // frame's pointer moves the peek on.
        LostTalesUiHitBox drawn = new LostTalesUiHitBox(resting.left,
                resting.top - this.above, resting.width, resting.height);
        this.stage = stageFor(this.stage, x, y, resting, drawn);
        if (this.stage == Stage.PEEKING) {
            this.peekTarget = peekDepth(x, y, resting.left, resting.right(),
                    resting.bottom() + REACH);
        }
        boolean onBar = this.stage == Stage.REVEALED
                && (resting.contains(x, y) || y < resting.top
                        && x >= resting.left && x < resting.right());
        this.lit = !onBar ? -1 : y < content.top ? panel.zoneInColumn(x)
                : panel.zoneAt(x, y);
        return onBar ? panel.fillOf(this.lit) : null;
    }

    /**
     * The stage the bar goes to from {@code stage} with the pointer at
     * ({@code x}, {@code y}), the bar resting all the way down at
     * {@code resting} and drawn at {@code drawn}: hidden once the pointer
     * is {@link #REACH} or more below where it rests; all the way down
     * once the pointer touches what shows of it, and for as long as the
     * pointer stays within {@link #REACH} of where it rests; peeking
     * anywhere else.
     */
    static Stage stageFor(Stage stage, double x, double y,
                          LostTalesUiHitBox resting,
                          LostTalesUiHitBox drawn) {
        if (y >= resting.bottom() + REACH) {
            return Stage.HIDDEN;
        }
        if (drawn.contains(x, y) || (stage == Stage.REVEALED
                && resting.grown(REACH).contains(x, y))) {
            return Stage.REVEALED;
        }
        return Stage.PEEKING;
    }

    /**
     * How far down the screen the bar peeks with the pointer at
     * ({@code x}, {@code y}), the bar spanning {@code left} to
     * {@code right} and coming out above {@code reachBottom}:
     * {@link #PEEK} where the pointer brings it out, deeper in step with
     * the pointer's nearness to the line its edge peeks down to at most,
     * and {@link #PEEK_MOST} on that line.
     */
    static double peekDepth(double x, double y, double left, double right,
                            double reachBottom) {
        double across = Math.max(0.0D, Math.max(left - x, x - right));
        double down = Math.max(0.0D, y - PEEK_MOST);
        double away = Math.min(1.0D, Math.sqrt(across * across
                + down * down) / (reachBottom - PEEK_MOST));
        return PEEK + (PEEK_MOST - PEEK) * (1.0D - away);
    }

    /** The layout the zone under the pointer is one of, or null. */
    Window.ScreenFill[] litLayout(Minecraft minecraft) {
        SnapLayouts.Panel panel = litPanel(minecraft);
        return panel == null ? null : panel.layoutOf(this.lit);
    }

    /** The other windows the suggestion under the pointer sends with the carried one. */
    Map<String, Window.ScreenFill> litCompanions(Minecraft minecraft) {
        SnapLayouts.Panel panel = litPanel(minecraft);
        return panel == null ? Collections.<String, Window.ScreenFill>emptyMap()
                : panel.companionsOf(this.lit);
    }

    private SnapLayouts.Panel litPanel(Minecraft minecraft) {
        LostTalesUiHitBox content = contentBox();
        return content == null || this.lit < 0 ? null
                : panel(minecraft, this.carried, content.left, content.top);
    }

    /** The carry is over: the bar goes back up. */
    void hide() {
        this.stage = Stage.HIDDEN;
        this.lit = -1;
    }

    /**
     * The layouts laid out in a content box whose top left is
     * {@code left}, {@code top}: the suggestions for {@code windowId}
     * first, centred across the box.
     */
    private SnapLayouts.Panel panel(Minecraft minecraft, String windowId,
                                    double left, double top) {
        List<Window.ScreenFill[]> layouts = SnapLayouts.offered(minecraft,
                this.screenWidth, this.screenHeight);
        if (layouts.isEmpty()) {
            return null;
        }
        List<SnapLayouts.Suggestion> suggestions =
                SnapLayouts.suggestedFor(layouts, windowId);
        int count = suggestions.size() + layouts.size();
        double spare = SnapLayouts.panelWidth(slots(minecraft),
                Integer.MAX_VALUE) - SnapLayouts.panelWidth(count, count);
        return SnapLayouts.lay(suggestions, layouts, count,
                Math.floor(left - FRAME + spare / 2.0D), top - FRAME,
                this.screenWidth, this.screenHeight);
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     int alpha, int surfaceAlpha) {
        SnapLayouts.Panel panel = this.carried == null ? null
                : panel(minecraft, this.carried, box.left, box.top);
        if (panel == null) {
            return;
        }
        boolean clipped = beginClip(minecraft, clipX, clipY, box.width,
                box.height);
        try {
            SnapLayouts.drawZones(minecraft, panel, this.lit, alpha,
                    surfaceAlpha);
        } finally {
            endClip(clipped);
        }
    }

    /** Where it first stands: centred at the top of {@code screen}, the room it is held in. */
    LostTalesUiHitBox firstContentBox(LostTalesUiHitBox screen) {
        int width = naturalWidth();
        return new LostTalesUiHitBox(
                Math.floor(screen.left + (screen.width - width) / 2.0D),
                screen.top + SubWindow.STRIP_HEIGHT, width,
                naturalHeight(width));
    }
}
