package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * The tabs of one chat window, laid out the way a browser lays out
 * its tabs. Tabs stand side by side with a one-pixel seam on the
 * window's top rule; the selected tab is drawn last, lifted, and
 * brighter. A tab is built rather than stretched: the sheet's left and
 * right border pieces at its ends, a line joining their tips, and the
 * tab's interior tone filling the span between them, so a label of any
 * length leaves the artwork undistorted. Switching is a hard cut: the
 * picked tab is forward the frame it is picked, however the pick
 * happened — a click, the keyboard, or a command bringing the console
 * forward — so nothing sweeps across the tabs between. A tab with
 * unread messages carries textual counters after its name:
 * {@code [p]} pings in salmon, then {@code [x]} other unread lines in
 * honey; a muted tab is drawn dim and italic. The channel's colour runs
 * across each tab's face two rows under that line, full at the centre
 * and fading to nothing at both ends.
 *
 * <p>Every tab carries a settings cog and a close cross, and its name
 * gives way before they do: a crowded row gives the labels less room,
 * widest first, down to a first letter, drawing each name whole and
 * cutting it where its room ends — resting the pointer on such a tab
 * slides the name along to show the rest — and only when even that is
 * not enough does it give anything else up — the counters,
 * then the other tabs' cogs, then their crosses, the selected tab
 * keeping its own controls throughout. The row's other controls stand at its two ends:
 * the restore {@code +} follows the last tab, since what it opens joins
 * that row, and the window's own controls are gathered against the
 * right edge beside the grip that moves it, in the order a title bar
 * reads them — the lock, a hairline, the settings cog and the close
 * cross, another hairline, then the grip. All of them keep their room
 * however many tabs there are, all of them are centred on the same row
 * of the strip, and the bare stretch left between the two ends drags
 * the window as the grip does. Geometry is computed once per change of
 * inputs and reused by drawing and hit testing, so a frame allocates
 * nothing.</p>
 *
 * <p>A tab being dragged is one of the row's own tabs throughout: it
 * leans toward the pointer where it stands and changes places with a
 * neighbour as it passes them, so the row always reads as it will once
 * the button comes up. Nothing is ever drawn floating free of the
 * strip, and no insertion bar is needed to say where a tab would land
 * — it is already there.</p>
 *
 * <p>Several tabs of one row can be <em>marked</em> at once
 * ({@link ChatTabSelection}); a marked tab wears the lit shape a hovered
 * one wears, so a group reads as picked out without shouting. The
 * channel accent says nothing about it: an accent is what a channel is,
 * and it is drawn at full strength on every tab whatever the pointer and
 * the marks are doing. Marks are the pointer's, not the row's: the row
 * only draws what it is handed.</p>
 *
 * <p>The row is positioned by its window: {@code rowBottom} and
 * {@code offsetX} come from the frame that drew the window, so the row
 * enters, settles and fades with the same shared motion state as the
 * history it stands on.</p>
 */
final class ChatChannelTabBar {
    /** Height of a resting tab's border pieces; the selected pair adds
     *  the lift. */
    private static final int PIECE_HEIGHT =
            ChatIconSheet.TAB_LEFT.getHeight();
    /**
     * Body height of a resting tab: the sheet's resting border pieces
     * whole, and under them the window's top rule, which is the strip's
     * last row and the one row a tab does not draw on. The selected tab
     * rises out of the row — its border pieces are exactly the lift
     * taller — and its interior rises with it rather than growing.
     */
    static final int HEIGHT = PIECE_HEIGHT + 1;
    /** Pixels the selected tab rises out of the row. */
    static final int LIFT = 3;
    /** Full height of the row: a resting tab plus the lift. */
    static final int ROW_HEIGHT = HEIGHT + LIFT;
    /** Width of a tab's border pieces; the same in every state. */
    private static final int BORDER_WIDTH = ChatIconSheet.TAB_LEFT.getWidth();
    /**
     * The rows of a tab, measured from its top: a chamfered row, the
     * line joining the border tips, a clear row, the channel accent,
     * another clear row, and then the interior — as tall as the icon it
     * holds, so the label sits level with it whatever either measures.
     */
    private static final int TIP_ROW = 1;
    private static final int ACCENT_ROW = TIP_ROW + 2;
    private static final int INTERIOR_TOP = ACCENT_ROW + 2;
    private static final int INTERIOR_HEIGHT = ChatChannelIcons.SIZE;
    /** Rows a capital letter takes; what text is centred by. */
    private static final int CAP_HEIGHT = 7;
    /** Seam between neighbouring tabs. */
    static final int TAB_GAP = 1;
    static final int PADDING_X = 6;
    /** Gap between the label and a counter, and between the counters. */
    private static final int COUNTER_GAP = 3;
    /** Hit square of a control inside the selected tab. */
    static final int CONTROL_SIZE = 7;
    static final int CONTROL_GAP = 2;
    /**
     * The square the chat draws and clicks its small controls in away
     * from the tab row — the character menu's padlock, the empty
     * state's {@code +} — and the height of the row's own hairlines.
     * The row itself lays its end controls out by their ink instead, so
     * the space between them reads the same whatever each measures.
     */
    static final int END_CONTROL_SIZE = 9;
    /** Clear space between the row's end controls, ink edge to ink edge. */
    private static final int END_CONTROL_GAP = 5;
    /** Slack a press on an end control is allowed either side of its ink. */
    private static final int END_CONTROL_SLACK = 2;
    /**
     * Share of a neighbour a dragged tab crosses before it takes that
     * neighbour's place: a third of it, the reach at which the move
     * already reads as meant without asking for half the tab. A third
     * cannot be one line in the row — the only line that reads as the
     * same travel from both sides is the halfway point — so every
     * boundary keeps two, a third in from either side, and the band
     * between them holds whatever place the run already has; see
     * {@link #reorderSlot}.
     */
    private static final int SWAP_SHARE = 3;
    /** Slack leaning against a swap, so a run resting exactly on one of
     *  its lines does not shake across it as the hand does. */
    private static final int SWAP_GUARD = 2;
    /**
     * The retreat that takes a just-made swap back. The two lines of a
     * boundary lie a third in from either side of the tab between the
     * places, so the moment a run crosses one it already stands past
     * the other; read cold, every swap would swap straight back. The
     * boundary just crossed therefore answers to the crossing instead:
     * the swap stands until the run is carried this far past the far
     * line — from where that line reads as any other, with this same
     * step in hand to tremble in — or retreats this far behind the
     * point it swapped at, which undoes it about where it was made.
     */
    private static final int SWAP_UNDO = 4;
    /** How long the tabs a dragged one passed take to close up behind it. */
    private static final double SLIDE_SECONDS = 0.10D;
    /**
     * The hover marquee that shows a cut name whole: it waits for the
     * pointer to rest, slides the name left about three letters a second
     * until the end is in view, rests there long enough to read, and
     * slides back — and again for as long as the pointer stays.
     */
    static final double MARQUEE_START_DELAY_SECONDS = 0.5D;
    static final double MARQUEE_SPEED_PX_PER_SECOND = 20.0D;
    static final double MARQUEE_END_PAUSE_SECONDS = 0.8D;
    /** The hairline dividing the row's controls. */
    private static final int DIVIDER_WIDTH =
            LostTalesChatVisualStyle.DIVIDER_WIDTH;
    private static final int DIVIDER_HEIGHT = END_CONTROL_SIZE;
    /**
     * Clear space kept at the row's right end, which the grip stands
     * against and no control crosses.
     */
    private static final int GRIP_INSET = 3;
    /*
     * The ink each end control takes. The lock is measured by the room
     * its whole swing needs rather than by the padlock at rest: the
     * shackle reaches out to the right as it opens, and a neighbour
     * placed against the resting shape would be swung into.
     */
    private static final int LOCK_WIDTH = ChatLockAnimation.WIDTH;
    private static final int PLUS_WIDTH = ChatIconSheet.PLUS.getWidth();
    private static final int COG_WIDTH = ChatIconSheet.COG.getWidth();
    private static final int CLOSE_WIDTH = ChatIconSheet.CLOSE.getWidth();
    private static final int GRIP_WIDTH = ChatIconSheet.GRIP.getWidth();
    /**
     * The tab search control at the row's left end: the sheet's chevron
     * run, pointing down while its panel is away and folding up to a
     * rule and over as the panel opens. Every frame is the same width,
     * so the row's geometry does not move as it plays.
     */
    private static final ChatIconSheet[] SEARCH_FRAMES = {
            ChatIconSheet.CHEVRON_1, ChatIconSheet.CHEVRON_2,
            ChatIconSheet.CHEVRON_3, ChatIconSheet.CHEVRON_4,
            ChatIconSheet.CHEVRON_5};
    private static final ChatIconSheet[] SEARCH_FRAMES_HOVER = {
            ChatIconSheet.CHEVRON_1_HOVER, ChatIconSheet.CHEVRON_2_HOVER,
            ChatIconSheet.CHEVRON_3_HOVER, ChatIconSheet.CHEVRON_4_HOVER,
            ChatIconSheet.CHEVRON_5_HOVER};
    private static final int SEARCH_WIDTH =
            SEARCH_FRAMES[0].getWidth();
    /**
     * How far the strip reaches left of {@link Row#left}: the row is
     * laid out from the first thing standing in it, and the surface it
     * stands on begins here, which is the window's own left edge.
     */
    private static final int STRIP_INSET = 2;
    /**
     * Clear space either side of the search control: the window's edge,
     * this, the control, this again, and then the first tab.
     */
    private static final int SEARCH_MARGIN = 3;
    /** Where the search control's ink begins, measured from the row's left. */
    private static final int SEARCH_INSET = SEARCH_MARGIN - STRIP_INSET;
    /** Where the row's tabs begin: past the search control and its gaps. */
    private static final int SEARCH_RUN =
            SEARCH_INSET + SEARCH_WIDTH + SEARCH_MARGIN;
    /** Room the grip keeps at the row's right end: its glyph and inset. */
    static final int MIN_GRIP_WIDTH = GRIP_WIDTH + GRIP_INSET;
    /**
     * Opacity of a tab's surface, which the bar paints itself in one
     * layer under the border artwork's ink. The artwork carries a
     * preview of the surface behind its ink at this same alpha; the
     * preview is cut away at draw time on {@link #TAB_INK_THRESHOLD}.
     */
    private static final int TAB_SURFACE_ALPHA = 0xAB;
    /**
     * Fragment-alpha share separating a tab piece's ink from the
     * surface preview behind it: ink is authored fully opaque, the
     * preview at {@link #TAB_SURFACE_ALPHA}, and only what clears the
     * threshold is drawn. The surface itself is painted by the bar in a
     * single layer, so the states stay one colour instead of stacking;
     * {@link ChatIconSheetTest} keeps the artwork on the right sides of
     * the threshold.
     */
    static final float TAB_INK_THRESHOLD = 0.85F;
    /** The line joining a resting tab's border tips. */
    private static final int TIP_RGB =
            LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE);
    /** The same line on a hovered or selected tab. */
    private static final int TIP_LIT_RGB =
            LostTalesColors.rgb(LostTalesColors.IVORY);
    private static final int PING_COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.SALMON);
    private static final int UNREAD_COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);

    private List<Tab> cachedTabs = Collections.emptyList();
    private List<ChatTab> cachedChannels = Collections.emptyList();
    private ChatTab cachedSelected;
    private final Map<ChatTab, String> cachedLabels =
            new HashMap<ChatTab, String>();
    private final Map<ChatTab, Integer> cachedPings =
            new HashMap<ChatTab, Integer>();
    private final Map<ChatTab, Integer> cachedOther =
            new HashMap<ChatTab, Integer>();
    private final Map<ChatTab, Boolean> cachedMuted =
            new HashMap<ChatTab, Boolean>();
    private FontRenderer cachedFont;
    /** The end lock's swing; one row, one lock, one state. */
    private final ChatLockAnimation lockAnimation = new ChatLockAnimation();
    private int cachedLeft = Integer.MIN_VALUE;
    private int cachedRight = Integer.MIN_VALUE;
    private boolean cachedShowClose;
    private boolean cachedShowRestore;
    private boolean cachedWindowControls;
    private int cachedClosedUnread;
    /** {@code [n]} after the restore control, or empty. */
    private String restoreBadge = "";
    /** Width of the restore control together with its badge. */
    private int restoreWidth;
    /** Right edge of the last tab, before the end controls. */
    private int tabsRight;
    /** How wide the run the hand is carrying is, and where in it the
     *  pressed tab stands; both zero while nothing is being carried. */
    private int draggedRunWidth;
    private int draggedPressedOffset;
    /** The reorder memory of the drag this row is carrying, and the tab
     *  it belongs to; a new drag starts it afresh. */
    private final ReorderLatch reorderLatch = new ReorderLatch();
    private ChatTab reorderOwner;
    /** Limit the tabs may not cross; the end controls begin here. */
    private int tabsLimit = Integer.MAX_VALUE;

    /** The search control's chevron: one run, one open/closed state. */
    private final ChatIconFlipbook searchChevron =
            new ChatIconFlipbook(SEARCH_FRAMES, SEARCH_FRAMES_HOVER);
    /**
     * How far each of the row's own controls has crossed to its hovered
     * artwork. A control the pointer leaves crosses back the same way,
     * so nothing in the strip ever swaps in one frame.
     */
    private float restoreFade;
    private float windowSettingsFade;
    private float windowCloseFade;
    private float gripFade;
    /** Seconds since the row was last drawn; what the fades step by. */
    private double frameElapsed;
    /**
     * Where the run of controls after the tabs is drawn: attached to the
     * edge the tabs are drawn to this frame, so it carries along with a
     * closing tab's neighbours and stands in its new place at once when
     * a tab opens.
     */
    /** The row's own lower cut, handed down to each tab's contents. */
    private double rowClipBottom = Double.NaN;
    /** {@link Row#fractionX} of the row being drawn, for {@link #clipX}. */
    private double clipFractionX;
    private float drawnTabsRight;
    /** That edge measured from the row's left. */
    private float drawnTabsRightOffset;
    /**
     * Whether the places the last layout handed out were a tab joining
     * or leaving rather than two changing over. Room a joining tab takes
     * is given up at once — the tabs to its right are simply there, the
     * way the hairline and the {@code +} after them are — and only room
     * handed back is travelled. A reorder is not room changing hands at
     * all, so there both directions travel.
     */
    private boolean seedGivesRoomAtOnce;
    /** What the controls still owe to a tab joining or leaving the row. */
    private float controlsOwed;
    private boolean controlsNeedSeed;
    private float controlsSeedOffset;
    /** Whether the edge has been placed at all yet. */
    private boolean tabsRightSeen;
    private long slideNanos;
    /** Whether the row is showing the restore control at all. */
    private boolean showRestore;
    private int lockX = -1;
    private int restoreX = -1;
    /** Left edge of the window's own cog and cross; -1 when absent. */
    private int windowSettingsX = -1;
    private int windowCloseX = -1;
    /** Left edge of the hairline between the last tab and the +. */
    private int tabDividerX = -1;
    /** The fraction of a pixel that run is really drawn at. */
    private float restoreRunFraction;
    /** Left edges of the hairlines around the window's own controls. */
    private int firstDividerX = -1;
    private int secondDividerX = -1;
    /** Right edge of the end controls; the grip starts here. */
    private int controlsRight;
    private float alphaScale = 1.0F;

    /**
     * What a point in the row resolves to. {@code SETTINGS} and
     * {@code CLOSE} carry a tab and act on it; {@code WINDOW_SETTINGS}
     * and {@code WINDOW_CLOSE} carry none and act on the window.
     */
    enum HitKind {
        TAB, CLOSE, SETTINGS, SEARCH, LOCK, RESTORE, WINDOW_SETTINGS,
        WINDOW_CLOSE, GRIP
    }

    static final class Hit {
        final HitKind kind;
        final ChatTab tab;
        /** Whether the tab's name is cut short in the row; false for anything but a tab. */
        final boolean labelClipped;

        Hit(HitKind kind, ChatTab tab) {
            this(kind, tab, false);
        }

        Hit(HitKind kind, ChatTab tab, boolean labelClipped) {
            this.kind = kind;
            this.tab = tab;
            this.labelClipped = labelClipped;
        }
    }

    /** The per-frame inputs of one window's row, filled by the screen. */
    static final class Row {
        List<ChatTab> tabs = Collections.emptyList();
        ChatTab selected;
        /** Tabs marked in this row; the selected one need not be among them. */
        List<ChatTab> marked = Collections.emptyList();
        /** Resting left edge of the first tab, screen space. */
        int left;
        /** Right limit the row may not cross, screen space. */
        int right;
        /** Screen y the tab bodies stand on. */
        int rowBottom;
        /**
         * The same edge before it was floored to a whole pixel, which is
         * where the strip is really drawn. The clip that keeps the row's
         * shadows off the rule is measured from this.
         */
        double rowBottomExact;
        /** Horizontal motion the whole row is drawn with. */
        int offsetX;
        /**
         * The fraction of a GUI pixel the whole row is drawn shifted by,
         * on each axis. The screen lays the row out in whole pixels and
         * draws it inside a matrix moved by this remainder, so the tabs
         * sit on the same display pixels the window does. A scissor
         * does not see the matrix: every clip edge the row computes in
         * its own whole-pixel space is moved by this before it is cut,
         * or the cut lands up to a GUI pixel left of the artwork and
         * steps as the window glides.
         */
        float fractionX;
        float fractionY;
        boolean locked;
        /** Whether a close cross is offered on the selected tab. */
        boolean closable;
        /**
         * Whether the window's own cog and cross are offered. A locked
         * window keeps the tabs and the size it has, so it offers
         * neither; its padlock is what unlocks it again.
         */
        boolean windowControls;
        /** Whether the restore control is offered after the row. */
        boolean showRestore;
        /** Whether this row's tab search panel is open right now. */
        boolean searchOpen;
        /** Whether this row's restore list is open right now. */
        boolean restoreOpen;
        /** Unread lines waiting in closed channels, shown after the +. */
        int closedUnread;
        /** Whether this window is the one being dragged right now. */
        boolean moving;
        /**
         * The tab being dragged along this row, or null. It keeps its
         * place in the row and is nudged toward the pointer rather than
         * leaving the strip: a tab under the hand is still one of the
         * row's tabs until it is carried clear of it altogether.
         */
        ChatTab dragging;
        /**
         * Every tab travelling with it, in row order, {@link #dragging}
         * among them. A marked group is carried as one long tab: the
         * pressed one sits under the hand and the rest keep their places
         * against it, so the run leans, swaps and stops as a single
         * thing rather than as one tab with strays following it.
         */
        List<ChatTab> draggedGroup = Collections.emptyList();
        /** Where the dragged tab's left edge follows the pointer. */
        int draggedLeft = Integer.MIN_VALUE;
        /**
         * Whether this window is being resized right now. The row then
         * takes the layout it is given at once instead of easing into
         * it: the tabs are part of the very geometry the hand is
         * dragging, so every one of them follows the edge in the same
         * frame — nothing trails it, and nothing goes on correcting
         * itself after the drag stops.
         */
        boolean resizing;
    }

    /**
     * How far inside a window's left edge its first tab begins: the
     * strip's own inset and the search control's run. A tab carried out
     * of one row and into a window of its own lands here, so a drag has
     * to allow for it to keep the cursor on the pixel it grabbed.
     */
    static int tabRunLeftInset() {
        return STRIP_INSET + SEARCH_RUN;
    }

    /** Highest pixel any tab can reach (the selected tab fully lifted). */
    static int rowTop(int rowBottom) {
        return rowBottom - ROW_HEIGHT;
    }

    /** Whether a GUI-space point lies in the row's vertical band. */
    static boolean inRowBand(Row row, int mouseY) {
        return mouseY >= rowTop(row.rowBottom) && mouseY < row.rowBottom;
    }

    /**
     * What lies under a GUI-space point: a tab, one of the selected tab's
     * controls, an end control, the grip, or nothing.
     */
    Hit hitAt(FontRenderer font, Row row, int mouseX, int mouseY) {
        if (!inRowBand(row, mouseY)) {
            return null;
        }
        List<Tab> tabs = layout(font, row);
        int localX = mouseX - row.offsetX;
        if (tabs.isEmpty()) {
            return null;
        }
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (localX < tab.x || localX >= tab.x + tab.width) {
                continue;
            }
            if (tab.closeX >= 0 && localX >= tab.closeX - 1
                    && localX < tab.closeX + CONTROL_SIZE + 1) {
                return new Hit(HitKind.CLOSE, tab.tab);
            }
            if (tab.settingsX >= 0 && localX >= tab.settingsX - 1
                    && localX < tab.settingsX + CONTROL_SIZE + 1) {
                return new Hit(HitKind.SETTINGS, tab.tab);
            }
            return new Hit(HitKind.TAB, tab.tab, tab.labelWidth > tab.labelRoom);
        }
        if (hitsControl(localX, row.left + SEARCH_INSET, SEARCH_WIDTH)) {
            return new Hit(HitKind.SEARCH, null);
        }
        if (hitsControl(localX, this.lockX, LOCK_WIDTH)) {
            return new Hit(HitKind.LOCK, null);
        }
        if (hitsControl(localX, this.restoreX, this.restoreWidth)) {
            return new Hit(HitKind.RESTORE, null);
        }
        if (hitsControl(localX, this.windowSettingsX, COG_WIDTH)) {
            return new Hit(HitKind.WINDOW_SETTINGS, null);
        }
        if (hitsControl(localX, this.windowCloseX, CLOSE_WIDTH)) {
            return new Hit(HitKind.WINDOW_CLOSE, null);
        }
        if (localX >= this.controlsRight && localX < row.right) {
            // A locked window's grip is inert: no hover, no tip, no drag.
            return row.locked ? null : new Hit(HitKind.GRIP, null);
        }
        return null;
    }

    /**
     * Whether a point in the row lies on an end control: its ink, and a
     * little either side of it, since the controls are laid out by ink
     * and a five-pixel glyph is a small thing to hit exactly. A control
     * the row is not showing (a negative x) is never hit.
     */
    private static boolean hitsControl(int localX, int x, int width) {
        return x >= 0 && localX >= x - END_CONTROL_SLACK
                && localX < x + width + END_CONTROL_SLACK;
    }

    /**
     * Whether the point lies on the grip's own glyph rather than
     * anywhere in the empty stretch that also drags the window. The
     * glyph is what the hover highlight and the move tip answer to, so
     * neither follows a pointer resting on the bare strip.
     */
    boolean isOverGripHandle(FontRenderer font, Row row, int mouseX,
                             int mouseY) {
        if (row == null || row.locked || !inRowBand(row, mouseY)) {
            return false;
        }
        layout(font, row);
        if (row.right - this.controlsRight < MIN_GRIP_WIDTH) {
            return false;
        }
        int localX = mouseX - row.offsetX;
        int right = row.right - GRIP_INSET;
        return localX >= right - ChatIconSheet.GRIP.getWidth()
                && localX < right;
    }

    /**
     * Insertion index for a run of tabs carried over this row, into the
     * row's <em>full</em> tab list. The row's tabs make one more place
     * than there are tabs, and the run takes the one its own centre has
     * reached: before the first drawn tab whose centre lies right of
     * the run's, and past everything — trimmed trailing tabs included —
     * once it has passed them all, so a crowded row can still take a
     * tab to its very end. Measured from the carried run itself,
     * row-local, rather than from the pointer: where the run lands then
     * does not depend on where along it the hand happens to hold it,
     * and the two end places ask only that the run stand mostly past
     * the end tab, never that it cover it.
     */
    int dropIndexAt(FontRenderer font, Row row, int runLeft, int runWidth) {
        List<Tab> tabs = layout(font, row);
        int centre = runLeft + runWidth / 2;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (centre < tab.x + tab.width / 2) {
                return tab.rowIndex;
            }
        }
        return row.tabs.size();
    }

    /**
     * Draws the row. {@code offsetX} and {@code alphaScale} are the
     * window's own motion, so tabs and lines arrive and fade together;
     * the rectangle registered with {@code regions} is the one painted.
     */
    void draw(FontRenderer font, ChatPointerRegions regions, Row row,
              int mouseX, int mouseY, float alphaScale) {
        List<Tab> tabs = layout(font, row);
        if (row.dragging == null) {
            // No hand on this row: whatever the last drag remembered
            // about its crossings is over, and the next starts clean.
            this.reorderOwner = null;
            this.reorderLatch.clear();
        }
        if (tabs.isEmpty()) {
            return;
        }
        advanceSlides(tabs, row);
        this.alphaScale = Math.max(0.0F, Math.min(1.0F, alphaScale));
        // Hit testing answers for the places the tabs will settle in,
        // not the places they are drawn while one of them is under the
        // hand. Asking it during a drag lights whichever tab's resting
        // slot the pointer happens to be crossing — a cog or a cross on
        // a tab the hand is only passing over — so the row simply
        // answers nothing until the tab is put down.
        Hit hovered = row.dragging != null ? null
                : hitAt(font, row, mouseX, mouseY);
        int bottom = row.rowBottom;
        // The window's title strip, ending exactly on the rule row:
        // drawn outside the clip below, so the inward-rounded cut can
        // never open a bright seam — anything a tab gives up to the cut
        // shows this same surface.
        Gui.drawRect(row.offsetX + row.left - STRIP_INSET,
                rowTop(bottom),
                row.offsetX + row.right + STRIP_INSET, bottom - 1,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_RGB,
                        scaled(LostTalesChatVisualStyle.SURFACE_ALPHA)));
        // Everything else the strip draws stops short of the rule it
        // ends on: a sprite's shadow falls a pixel down and right, and
        // the rule is a hairline the window is bounded by, not something
        // to cast onto. Inward, so not one display pixel of a tab or a
        // shadow ever lands on the rule row; the rule itself is drawn
        // after the clip.
        this.rowClipBottom = row.rowBottomExact - 1.0D;
        this.clipFractionX = row.fractionX;
        boolean clipped = LostTalesChatOverlayRenderer.beginVerticalClip(
                Minecraft.getMinecraft(), Double.NaN,
                this.rowClipBottom, true);
        try {
            // A window being dragged holds the grip's highlight wherever the
            // pointer has gone, the way a control keeps its pressed look.
            this.gripFade = LostTalesChatVisualStyle.hoverFade(
                    this.gripFade, row.moving
                            || isOverGripHandle(font, row, mouseX, mouseY),
                    this.frameElapsed);
            drawGrip(row.offsetX + this.controlsRight, row.offsetX + row.right,
                    bottom, this.gripFade);
            // The tab in front draws last so it stands over its
            // neighbours; a dragged one draws last of all, since it
            // slides across them.
            Tab selectedTab = null;
            for (int index = 0; index < tabs.size(); index++) {
                Tab tab = tabs.get(index);
                if (isCarried(row, tab.tab)) {
                    continue;
                }
                if (tab.tab.equals(row.selected)) {
                    selectedTab = tab;
                    continue;
                }
                drawTab(font, tab, row, bottom, hovered, false);
            }
            if (selectedTab != null) {
                drawTab(font, selectedTab, row, bottom, hovered, true);
            }
            // The carried run draws last of all and in row order, so it
            // slides across its neighbours as one piece.
            for (int index = 0; index < tabs.size(); index++) {
                Tab tab = tabs.get(index);
                if (isCarried(row, tab.tab)) {
                    drawTab(font, tab, row, bottom, hovered,
                            tab.tab.equals(row.selected));
                }
            }
            // The end controls sit centred in the strip, like the selected
            // tab's label; the badge's caps share that centre.
            if (this.lockX >= 0) {
                drawLock(row.offsetX + this.lockX, bottom, row.locked,
                        hovered != null && hovered.kind == HitKind.LOCK);
            }
            GL11.glPushMatrix();
            GL11.glTranslatef(this.restoreRunFraction, 0.0F, 0.0F);
            if (this.tabDividerX >= 0) {
                drawDivider(row.offsetX + this.tabDividerX, bottom);
            }
            if (this.restoreX >= 0) {
                boolean restoreHovered = hovered != null
                        && hovered.kind == HitKind.RESTORE;
                this.restoreFade = fade(this.restoreFade, hovered,
                        HitKind.RESTORE);
                // The control says which way it goes: a + while the
                // list it opens is away, and the same crossbar without
                // its upright — a minus — while the list is out.
                drawEndControl(
                        row.restoreOpen ? ChatIconSheet.MINUS
                                : ChatIconSheet.PLUS,
                        row.restoreOpen ? ChatIconSheet.MINUS_HOVER
                                : ChatIconSheet.PLUS_HOVER,
                        this.restoreFade, row.offsetX + this.restoreX,
                        bottom);
                if (this.restoreBadge.length() > 0) {
                    // What waits behind the +, in the tabs' unread honey.
                    LostTalesChatVisualStyle.drawColored(font, this.restoreBadge,
                            row.offsetX + this.restoreX + PLUS_WIDTH
                                    + COUNTER_GAP,
                            centredInStrip(bottom, CAP_HEIGHT),
                            UNREAD_COUNTER_RGB, scaled(0xFF));
                }
            }
            GL11.glPopMatrix();
            // The tab search sits at the row's left end, before the
            // first tab, where a browser keeps it.
            drawSearch(row.offsetX + row.left + SEARCH_INSET, bottom,
                    row.searchOpen,
                    hovered != null && hovered.kind == HitKind.SEARCH);
            // The window's own controls, in the order a title bar
            // reads: the lock (drawn above), a hairline, its settings
            // and close, another hairline, then the grip.
            if (this.firstDividerX >= 0) {
                drawDivider(row.offsetX + this.firstDividerX, bottom);
            }
            if (this.windowSettingsX >= 0) {
                this.windowSettingsFade = fade(this.windowSettingsFade,
                        hovered, HitKind.WINDOW_SETTINGS);
                drawEndControl(ChatIconSheet.COG, ChatIconSheet.COG_HOVER,
                        this.windowSettingsFade,
                        row.offsetX + this.windowSettingsX, bottom);
            }
            if (this.windowCloseX >= 0) {
                this.windowCloseFade = fade(this.windowCloseFade, hovered,
                        HitKind.WINDOW_CLOSE);
                drawEndControl(ChatIconSheet.CLOSE,
                        ChatIconSheet.CLOSE_HOVER, this.windowCloseFade,
                        row.offsetX + this.windowCloseX, bottom);
            }
            if (this.secondDividerX >= 0) {
                drawDivider(row.offsetX + this.secondDividerX, bottom);
            }
            // Last of everything the strip holds, so the tabs, the end
            // controls and the bare stretch between them all sink into
            // the rule together rather than each on its own. It is the
            // very shade the window hangs from its own top edge, hung
            // the other way up — same routine, so the two sides of the
            // rule cannot drift apart.
            LostTalesChatOverlayRenderer.drawEdgeFade(
                    row.offsetX + row.left - STRIP_INSET,
                    row.offsetX + row.right + STRIP_INSET,
                    bottom - 1, rowTop(bottom),
                    LostTalesChatOverlayRenderer.TOP_EDGE_FADE_HEIGHT,
                    scaled(LostTalesChatOverlayRenderer.EDGE_FADE_ALPHA));
        } finally {
            LostTalesChatOverlayRenderer.endVerticalClip(clipped);
        }
        // The window's top rule: the strip's last row, over the tabs
        // and controls, the exact width of the strip. It stands on one
        // row of the history's own backdrop, as the bottom rule does, so
        // both edges of a window read the same way.
        LostTalesChatOverlayRenderer.drawBackdropRow(
                row.offsetX + row.left - STRIP_INSET, bottom - 1,
                row.offsetX + row.right + STRIP_INSET, bottom,
                scaled(LostTalesChatOverlayRenderer.backdropRowAlpha(
                        Minecraft.getMinecraft())));
        LostTalesChatOverlayRenderer.drawRule(
                row.offsetX + row.left - STRIP_INSET,
                row.offsetX + row.right + STRIP_INSET, bottom - 1, bottom,
                scaled(0xFF));
        regions.addWindow(row.offsetX + row.left - STRIP_INSET,
                rowTop(bottom), row.offsetX + row.right + STRIP_INSET,
                bottom);
    }

    /**
     * Where a run of {@code height} rows sits in a tab's interior,
     * centred on the icon that fills it. The icon is an even number of
     * rows and the caps and the control glyphs are odd, so neither can
     * land on its centre; the remainder is spent above the run, which
     * is the half they read as level with the icon from.
     */
    private static int centredInInterior(int interiorTop, int height) {
        return interiorTop + (INTERIOR_HEIGHT - height + 1) / 2;
    }

    private int scaled(int alpha) {
        int result = Math.round(alpha * this.alphaScale);
        return result < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                ? 0 : result;
    }

    private void drawTab(FontRenderer font, Tab tab, Row row,
                         int rowBottom, Hit hovered, boolean selected) {
        boolean marked = row.marked.contains(tab.tab);
        // A marked tab wears the lit shape outright; a hovered one
        // crosses to it and back rather than swapping in a frame.
        tab.hoverFade = LostTalesChatVisualStyle.hoverFade(tab.hoverFade,
                hovered != null && tab.tab.equals(hovered.tab),
                this.frameElapsed);
        float lit = marked ? 1.0F : tab.hoverFade;
        // Selection is a hard cut: the picked tab is forward at once,
        // nothing sweeps across the tabs between.
        int lift = selected ? LIFT : 0;
        int top = rowBottom - HEIGHT - lift;
        // A tab draws its border pieces whole and stops one row short
        // of the strip's last row, which is the window's top rule; the
        // selected one rises out of the row, it does not sink.
        float left = row.offsetX + drawnX(row, tab);
        float width = drawnWidth(row, tab);
        float right = left + width;
        // Everything but the name and the controls is a fixed size, so
        // a tab drawn wider or narrower than the one it is easing toward
        // owes that whole difference to the room the two share: which
        // controls stand in it, and how much of the name shows beside
        // them, is read off the width the tab is drawn at this frame.
        TabControls drawn = controlsFor(tab.labelWidth,
                Math.round(width) - tab.fixedWidth, row.closable, selected);
        float dim = 1.0F;
        // One surface painted under border ink and span alike; the
        // artwork's own backdrop texels are cut away inside.
        drawTabShape(left, right, top, selected, lit,
                scaled(Math.round(0xFF * dim)),
                scaled(Math.round(TAB_SURFACE_ALPHA * dim)));
        // Channel accent across the tab's face, clear of the border
        // artwork on every side: full at the centre and gone at the ends
        // like the edge rules. The accent is the channel's own and says
        // nothing about the pointer, so being forward, hovered or marked
        // never changes it; only a dragged tab's faintness reaches it.
        int accentAlpha = scaled(Math.round(0xFF * dim));
        drawAccent(left + BORDER_WIDTH, right - BORDER_WIDTH,
                top + ACCENT_ROW,
                ClientChatChannelState.displayColor(tab.tab), accentAlpha);
        // Text is always at full opacity; a muted tab is told by its
        // italics alone.
        int textAlpha = scaled(Math.round(255 * dim));
        // A name the row has cut short is read whole by resting the
        // pointer on it: the marquee runs on the clock while the tab is
        // hovered and glides home once it is not, so a pointer sweeping
        // the row starts nothing and a name that fits never moves.
        int overflow = tab.labelWidth - drawn.labelRoom;
        boolean labelHovered = hovered != null && hovered.kind == HitKind.TAB
                && tab.tab.equals(hovered.tab);
        if (labelHovered && overflow > 0 && LostTalesConfig.enableChatAnimations) {
            tab.hoverSeconds += this.frameElapsed;
            tab.marqueeOffset = (float) marqueeOffset(tab.hoverSeconds, overflow);
        } else {
            tab.hoverSeconds = 0.0D;
            tab.marqueeOffset = eased(tab.marqueeOffset, 0.0F, this.frameElapsed);
        }
        // Everything inside the tab is cut off at the tab's own edge as
        // it narrows, the way a browser cuts a tab's label. The row's
        // own lower cut goes with it, since a scissor replaces the one
        // before it rather than narrowing it.
        boolean clipped = LostTalesChatOverlayRenderer.beginClip(
                Minecraft.getMinecraft(), clipX(left),
                clipX(right - BORDER_WIDTH), Double.NaN, this.rowClipBottom,
                true);
        try {
            drawTabContents(font, tab, row, hovered, left, right,
                    top + INTERIOR_TOP, drawn, textAlpha,
                    scaled(Math.round(0xFF * dim)));
        } finally {
            LostTalesChatOverlayRenderer.endVerticalClip(clipped);
        }
    }

    /**
     * What stands inside a tab, one clear row under the accent: the icon
     * at its own size, the label's caps and the counters level with it,
     * and whichever of the tab's own controls its drawn width holds
     * against its right end. The selected tab's lift carries all of it
     * up rather than re-centring it. Drawn inside the caller's clip, so
     * a tab narrower than its contents shows as much of them as it has
     * room for.
     */
    private void drawTabContents(FontRenderer font, Tab tab, Row row,
                                 Hit hovered, float left, float right,
                                 int interiorTop, TabControls drawn,
                                 int textAlpha, int controlAlpha) {
        // The words are drawn at whole coordinates inside a matrix moved
        // by whatever fraction of a pixel the tab stands on, since the
        // font draws at whole ones: the glyphs then land on the same
        // display pixels the tab's own artwork does.
        int wholeLeft = (int)Math.floor(left);
        float fraction = left - wholeLeft;
        int textX = wholeLeft + PADDING_X;
        int textY = centredInInterior(interiorTop, CAP_HEIGHT);
        if (tab.icon != null) {
            GL11.glPushMatrix();
            GL11.glTranslatef(fraction, 0.0F, 0.0F);
            try {
                ChatChannelIcons.draw(Minecraft.getMinecraft(), tab.tab, textX,
                        centredInInterior(interiorTop, ChatChannelIcons.SIZE),
                        textAlpha);
            } finally {
                GL11.glPopMatrix();
            }
            textX += ChatChannelIcons.SIZE + ChatChannelIcons.GAP;
        }
        drawTabLabel(font, tab, textX, fraction, textY, textAlpha, left, right,
                drawn.labelRoom);
        drawTabCounters(font, tab, textX + drawn.labelRoom, fraction, textY,
                textAlpha);
        drawTabControls(tab, hovered, drawn, right, interiorTop,
                controlAlpha);
    }

    /**
     * The name, whole, in the room its drawn width leaves it. A name
     * wider than its room is cut at the room's end — inside the tab's
     * own cut, both given to the scissor at once since one replaces the
     * other — and slid left by the marquee while hovered, its offset
     * laid on a display pixel so the glyphs stay on theirs.
     */
    private void drawTabLabel(FontRenderer font, Tab tab, int textX,
                              float fraction, int textY, int textAlpha,
                              float tabLeft, float tabRight, int labelRoom) {
        String text = tab.muted ? "§o" + tab.label : tab.label;
        if (tab.labelWidth <= labelRoom) {
            drawWords(font, text, textX, fraction, textY, textAlpha);
            return;
        }
        double roomLeft = textX + fraction;
        double clipLeft = Math.max(tabLeft, roomLeft);
        double clipRight = Math.min(tabRight - BORDER_WIDTH,
                roomLeft + labelRoom);
        if (clipRight <= clipLeft) {
            return;
        }
        boolean clipped = LostTalesChatOverlayRenderer.beginClip(
                Minecraft.getMinecraft(), clipX(clipLeft), clipX(clipRight),
                Double.NaN, this.rowClipBottom, true);
        try {
            double offset = snapped(tab.marqueeOffset, displayStep());
            drawWords(font, text, textX, (float)(fraction - offset), textY,
                    textAlpha);
        } finally {
            LostTalesChatOverlayRenderer.endVerticalClip(clipped);
        }
    }

    /**
     * A horizontal clip edge in the row's own space, moved to where the
     * row is really drawn. The vertical edge needs no such move:
     * {@link #rowClipBottom} is measured from the exact edge already.
     */
    private double clipX(double rowX) {
        return rowX + this.clipFractionX;
    }

    /** Text at a whole x inside a matrix moved by the fraction it stands on. */
    private static void drawWords(FontRenderer font, String text, int x,
                                  float fraction, int y, int alpha) {
        GL11.glPushMatrix();
        GL11.glTranslatef(fraction, 0.0F, 0.0F);
        try {
            LostTalesChatVisualStyle.drawPlain(font, text, x, y, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The counters after the name's room, left to right. */
    private static void drawTabCounters(FontRenderer font, Tab tab, int textX,
                                        float fraction, int textY,
                                        int textAlpha) {
        if (tab.pingText.length() == 0 && tab.otherText.length() == 0) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(fraction, 0.0F, 0.0F);
        try {
            if (tab.pingText.length() > 0) {
                textX += COUNTER_GAP;
                LostTalesChatVisualStyle.drawColored(font, tab.pingText,
                        textX, textY, PING_COUNTER_RGB, textAlpha);
                textX += tab.pingWidth;
            }
            if (tab.otherText.length() > 0) {
                textX += COUNTER_GAP;
                LostTalesChatVisualStyle.drawColored(font, tab.otherText,
                        textX, textY, UNREAD_COUNTER_RGB, textAlpha);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * The tab's own cog and cross, whichever its drawn width holds.
     * Never dimmed with the label: only the window's own fade and a
     * dragged tab's faintness reach them. Their hit squares are centred
     * in the interior like the caps, and each sprite is centred in its
     * square in turn. They hug the tab's drawn right end, so a tab still
     * easing to another width does not leave them stranded in the
     * middle, and one that no longer holds them hands their room to the
     * name the same frame.
     */
    private void drawTabControls(Tab tab, Hit hovered, TabControls drawn,
                                 float right, int interiorTop,
                                 int controlAlpha) {
        int controlTop = centredInInterior(interiorTop, CONTROL_SIZE);
        float edge = right - PADDING_X;
        if (drawn.close) {
            float closeX = edge - CONTROL_SIZE;
            tab.closeFade = LostTalesChatVisualStyle.hoverFade(tab.closeFade,
                    onControl(hovered, tab, HitKind.CLOSE),
                    this.frameElapsed);
            drawTabControl(ChatIconSheet.CLOSE, ChatIconSheet.CLOSE_HOVER,
                    tab.closeFade, closeX, controlTop, controlAlpha);
            edge = closeX - CONTROL_GAP;
        }
        if (drawn.cog) {
            tab.cogFade = LostTalesChatVisualStyle.hoverFade(tab.cogFade,
                    onControl(hovered, tab, HitKind.SETTINGS),
                    this.frameElapsed);
            drawTabControl(ChatIconSheet.COG, ChatIconSheet.COG_HOVER,
                    tab.cogFade, edge - CONTROL_SIZE, controlTop,
                    controlAlpha);
        }
    }

    /**
     * A tab's shape: the interior filling the span its two border pieces
     * enclose, the line joining their tips, and the pieces themselves.
     * The pieces are drawn at their own size — a longer label widens the
     * span, never the artwork — and the selected pair stands two rows
     * taller than the resting ones, which is the lift its caller has
     * already made room for. Everything that draws a tab comes through
     * here, so the row and a dragged tab's ghost cannot drift apart.
     */
    private static void drawTabShape(float left, float right, int top,
                                     boolean selected, float lit,
                                     int spriteAlpha, int interiorAlpha) {
        ChatIconSheet leftPiece;
        ChatIconSheet rightPiece;
        // The lit border artwork laid over the resting pair as far as the
        // tab has crossed to it; its tones cross by the same share, so
        // the whole tab lights together rather than in two steps.
        ChatIconSheet leftLit = null;
        ChatIconSheet rightLit = null;
        int surfaceRgb;
        int tipRgb;
        if (selected) {
            leftPiece = ChatIconSheet.TAB_SELECTED_LEFT;
            rightPiece = ChatIconSheet.TAB_SELECTED_RIGHT;
            surfaceRgb = LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB;
            tipRgb = TIP_LIT_RGB;
        } else {
            leftPiece = ChatIconSheet.TAB_LEFT;
            rightPiece = ChatIconSheet.TAB_RIGHT;
            leftLit = ChatIconSheet.TAB_HOVER_LEFT;
            rightLit = ChatIconSheet.TAB_HOVER_RIGHT;
            surfaceRgb = LostTalesChatVisualStyle.blend(
                    LostTalesChatVisualStyle.SURFACE_RGB,
                    LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB, lit);
            tipRgb = LostTalesChatVisualStyle.blend(TIP_RGB, TIP_LIT_RGB,
                    lit);
        }
        float spanLeft = left + BORDER_WIDTH;
        float spanRight = right - BORDER_WIDTH;
        int height = leftPiece.getHeight();
        // The tab's one surface, painted in a single layer across its
        // whole chamfered footprint — the top row inset a pixel each
        // side, the body below it, border regions and span alike, at the
        // tab's own fractional edges since a tab settles on a display
        // pixel rather than a whole GUI one. One layer whose tone
        // travels is what keeps the states one colour: a tab fully
        // crossed to the lit artwork stands on exactly the surface the
        // selected tab stands on, where a hover layer stacked over the
        // resting one would darken past it — and the border pieces can
        // never read as another tone than the span between them, since
        // both are this same paint.
        LostTalesChatOverlayRenderer.fillRect(left + 1, top, right - 1,
                top + 1,
                LostTalesChatVisualStyle.argb(surfaceRgb, interiorAlpha));
        LostTalesChatOverlayRenderer.fillRect(left, top + 1, right,
                top + height,
                LostTalesChatVisualStyle.argb(surfaceRgb, interiorAlpha));
        if (spanRight > spanLeft) {
            // The tips are the pieces' innermost lit columns, one pixel
            // outside the span, so the line meets both without a gap.
            LostTalesChatOverlayRenderer.fillRect(spanLeft, top + TIP_ROW,
                    spanRight, top + TIP_ROW + 1,
                    LostTalesChatVisualStyle.argb(tipRgb, spriteAlpha));
        }
        // The pieces bring ink alone to the frame: their backdrop texels
        // only preview the surface painted above and are cut away on the
        // ink threshold, or they would stack a second layer over it. The
        // test sees texture and vertex alpha multiplied, so the
        // threshold is scaled by the share each piece is drawn at.
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        try {
            GL11.glAlphaFunc(GL11.GL_GREATER,
                    TAB_INK_THRESHOLD * spriteAlpha / 255.0F);
            leftPiece.draw(left, top, spriteAlpha);
            rightPiece.draw(spanRight, top, spriteAlpha);
            int over = Math.round(spriteAlpha * lit);
            if (leftLit != null
                    && over >= LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
                GL11.glAlphaFunc(GL11.GL_GREATER,
                        TAB_INK_THRESHOLD * over / 255.0F);
                leftLit.draw(left, top, over);
                rightLit.draw(spanRight, top, over);
            }
        } finally {
            // The threshold vanilla's GUI runs under, as the item
            // renderer also leaves it.
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        }
    }

    /**
     * A one-pixel band of the channel's colour from {@code left} to
     * {@code right}: the given alpha at the centre, nothing at either
     * end, the same profile as the window's edge rules.
     */
    private static void drawAccent(float left, float right, int y, int rgb,
                                   int alpha) {
        if (right <= left || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float centre = (left + right) / 2.0F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        // Same winding as the chat backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(rgb, alpha);
        tessellator.addVertex(centre, y + 1, 0.0D);
        tessellator.addVertex(centre, y, 0.0D);
        tessellator.setColorRGBA_I(rgb, 0);
        tessellator.addVertex(left, y, 0.0D);
        tessellator.addVertex(left, y + 1, 0.0D);
        tessellator.setColorRGBA_I(rgb, 0);
        tessellator.addVertex(right, y + 1, 0.0D);
        tessellator.addVertex(right, y, 0.0D);
        tessellator.setColorRGBA_I(rgb, alpha);
        tessellator.addVertex(centre, y, 0.0D);
        tessellator.addVertex(centre, y + 1, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Where a tab is drawn: under the pointer while it is the one being
     * dragged, and otherwise wherever it has eased to on its way to the
     * place the layout gave it, so the tabs a dragged one passes close
     * up behind it rather than jumping.
     */
    private float drawnX(Row row, Tab tab) {
        return isCarried(row, tab.tab) ? draggedLeft(row, tab)
                : (float)(row.left + tab.drawnLeftOffset
                        + snapped(tab.slide, displayStep()));
    }

    /**
     * How wide a tab is drawn: the width it is easing toward once the
     * row has settled, and on the way there the width it is passing
     * through. A tab under the hand keeps its exact size — it is not
     * being resized, it is being carried.
     */
    private static float drawnWidth(Row row, Tab tab) {
        return isCarried(row, tab.tab) ? tab.width : tab.drawnWidthSnapped;
    }

    /**
     * How fine a step the row may move in: one display pixel, in the
     * GUI's own units. The finest the screen has, and what everything
     * else in the chat lays its pixel art on.
     */
    private static double displayStep() {
        return 1.0D / ChatWindowFrame.displayScaleFactor();
    }

    /** {@code value} laid on the nearest whole display pixel. */
    private static double snapped(double value, double step) {
        return Math.round(value / step) * step;
    }

    /**
     * Advances the row toward its own layout. Every tab keeps the place
     * and the size it was drawn at and eases to the ones it holds now,
     * over {@link #SLIDE_SECONDS}, and the controls standing after the
     * tabs travel with them — so a tab opening or closing, a drag
     * settling, and a window being dragged narrower all carry the row
     * along instead of snapping it. Places are measured from the row's
     * own left edge, so a window <em>moved</em> takes its strip with it
     * in one piece and nothing swings along behind it. The tab under the
     * hand is the one exception: it follows the pointer rigidly.
     */
    private void advanceSlides(List<Tab> tabs, Row row) {
        long now = System.nanoTime();
        double elapsed = this.slideNanos == 0L ? 0.0D
                : (now - this.slideNanos) / 1.0E9D;
        this.slideNanos = now;
        this.frameElapsed = elapsed;
        measureRun(tabs, row);
        boolean animate = LostTalesConfig.enableChatAnimations;
        // The row is laid down from one running cursor over the widths
        // the tabs are actually drawn at, rather than each tab easing to
        // a place of its own. Places of their own were the jitter: while
        // a row reflows, a tab's own travel and its neighbour's are at
        // different points of the same curve, so the gap between them
        // genuinely opened and closed by fractions of a pixel — and
        // rounding each of them apart turned those fractions into whole
        // pixels that disagreed. With one cursor the seam is the seam,
        // and a tab's left edge can only move the way its neighbours'
        // widths do: one way, smoothly.
        double step = displayStep();
        double[] exact = new double[tabs.size()];
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (!animate || isCarried(row, tab.tab) || row.resizing) {
                // Its own size at once: a carried tab is not being
                // resized but carried, and a tab in a window whose edge
                // is under the hand is part of the very geometry being
                // dragged — a width still easing there is a width
                // trailing the input, and the whole row must follow the
                // edge as one layout rather than as tabs chasing
                // targets of their own.
                tab.drawnWidth = tab.width;
            } else {
                tab.drawnWidth = eased(tab.drawnWidth, tab.width, elapsed);
            }
            exact[index] = tab.drawnWidth;
        }
        // The seams laid on display pixels from the exact running total,
        // each width then being what lies between two seams: every seam
        // moves the way the total does, one way, however the widths
        // exchange room among themselves.
        double[] seams = placeSeams(exact, TAB_GAP, step, SEARCH_RUN);
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            boolean carried = isCarried(row, tab.tab);
            tab.drawnLeftOffset = (float)seams[index];
            tab.drawnWidthSnapped = (float)Math.max(step,
                    seams[index + 1] - seams[index] - TAB_GAP);
            if (tab.needsSeed) {
                // First sight of the tab in this layout: it owes the
                // distance from where it was drawn to the slot it has
                // just been given, and pays it off below.
                tab.needsSeed = false;
                float owed = (float)(tab.seedLeftOffset
                        - tab.drawnLeftOffset);
                // Positive is a tab drawn right of where it now belongs,
                // which is room handed back to it: that it travels. A
                // negative one would be catching up to room taken from
                // it, and it does not travel to give room up — it is
                // simply out of the way.
                tab.slide = this.seedGivesRoomAtOnce
                        ? Math.max(0.0F, owed) : owed;
            }
            if (carried) {
                // Held at the pointer, and its debt recorded from there,
                // so letting go leaves it to travel the rest of the way
                // instead of arriving the instant the button comes up.
                tab.slide = (float)(draggedLeft(row, tab) - row.left
                        - tab.drawnLeftOffset);
            } else if (!animate || row.resizing) {
                // A resize snaps travel too: a slide still paying off
                // mid-drag would hold part of the row off the layout
                // the edge is being dragged to.
                tab.slide = 0.0F;
            } else {
                tab.slide = eased(tab.slide, 0.0F, elapsed);
            }
        }
        // Last, so the edge the controls stand at is worked out from
        // the places the tabs have just eased to.
        advanceTabsRight(tabs, row, elapsed);
        placeLeftRun();
    }

    /**
     * The edge the row's tabs are drawn to: past the furthest of them.
     * An empty row ends where its tabs would have begun.
     */
    private float drawnTabsRight(List<Tab> tabs, Row row) {
        float right = row.left + SEARCH_RUN;
        boolean pushing = isPushingControls(tabs, row);
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (isCarried(row, tab.tab)) {
                right = Math.max(right, pushing ? draggedRunRight(row)
                        : tab.x + tab.width);
                continue;
            }
            right = Math.max(right,
                    drawnX(row, tab) + drawnWidth(row, tab));
        }
        return right;
    }

    /**
     * Whether the run under the hand is driving the controls after the
     * tabs: it is, whenever it is the last of them. They give way in
     * front of it the way a browser's add-tab control does, and come
     * back behind it as it eases into its slot on release.
     */
    private boolean isPushingControls(List<Tab> tabs, Row row) {
        return !tabs.isEmpty()
                && isCarried(row, tabs.get(tabs.size() - 1).tab);
    }

    /**
     * Advances the edge the controls after the tabs stand at. It follows
     * the tabs, and the tabs ease — but not every change reaches it
     * through one of them: a tab leaving the <em>end</em> of the row
     * moves none of its neighbours, so the edge would fall a whole tab's
     * width in one frame and the hairline and the {@code +} would jump
     * to their new place instead of travelling to it. So the edge eases
     * in its own right, on the same curve.
     *
     * <p>They are <em>attached</em> to it, not following it: a row
     * reflowing to new widths moves them exactly as it moves its tabs,
     * with nothing of their own to lag by. Only a tab joining or leaving
     * the row can move this edge without moving a tab — a tab leaving
     * the end of a row moves no neighbour at all — and that one jump is
     * travelled, on the very curve a tab's own travel uses, so what
     * moves is one thing. Room given up is taken at once: the controls
     * are never found in front of the tabs. Kept relative to the row's
     * left, like the tabs' own places, so a window being carried about
     * takes its controls with it rigidly. The one thing they do not ease
     * after is the hand: a run pushing them along drives them directly,
     * or they would trail the very tab that is shoving them.</p>
     */
    private void advanceTabsRight(List<Tab> tabs, Row row, double elapsed) {
        float exact = drawnTabsRight(tabs, row) - row.left;
        if (!this.tabsRightSeen || !LostTalesConfig.enableChatAnimations
                || isPushingControls(tabs, row)) {
            this.tabsRightSeen = true;
            this.controlsOwed = 0.0F;
        } else {
            if (this.controlsNeedSeed) {
                // The row has just gained or lost a tab, which is the
                // one thing that can move this edge without moving a
                // tab: everything else reaches it through the widths,
                // which are already travelling. Room given up is taken
                // at once — the controls are never found in front of
                // the tabs — and room handed back is owed and paid off
                // on the very curve a tab's own travel uses, so the two
                // move as one thing rather than one following another.
                this.controlsNeedSeed = false;
                this.controlsOwed = Math.max(0.0F,
                        this.controlsSeedOffset - exact);
            }
            this.controlsOwed = eased(this.controlsOwed, 0.0F, elapsed);
        }
        this.drawnTabsRightOffset = exact + this.controlsOwed;
        this.drawnTabsRight = row.left + this.drawnTabsRightOffset;
    }

    /** Whether a tab is one of those the hand is carrying. */
    private static boolean isCarried(Row row, ChatTab tab) {
        return tab != null && row.draggedLeft != Integer.MIN_VALUE
                && (tab.equals(row.dragging)
                        || row.draggedGroup.contains(tab));
    }

    /**
     * How far left a hovered name is shifted after {@code elapsedSeconds}
     * of hovering, given the {@code overflowPx} of it the tab cannot
     * show: nothing during the start delay, then out to the overflow,
     * a pause, back to nothing, a pause, and round again. Zero when
     * nothing overflows. A pure reading of the clock, so it looks the
     * same at any frame rate.
     */
    static double marqueeOffset(double elapsedSeconds, int overflowPx) {
        if (overflowPx <= 0) {
            return 0.0D;
        }
        double time = elapsedSeconds - MARQUEE_START_DELAY_SECONDS;
        if (time <= 0.0D) {
            return 0.0D;
        }
        double slide = overflowPx / MARQUEE_SPEED_PX_PER_SECOND;
        double cycle = 2.0D * (slide + MARQUEE_END_PAUSE_SECONDS);
        double phase = time % cycle;
        if (phase < slide) {
            return phase * MARQUEE_SPEED_PX_PER_SECOND;
        }
        if (phase < slide + MARQUEE_END_PAUSE_SECONDS) {
            return overflowPx;
        }
        if (phase < 2.0D * slide + MARQUEE_END_PAUSE_SECONDS) {
            return overflowPx - (phase - slide - MARQUEE_END_PAUSE_SECONDS)
                    * MARQUEE_SPEED_PX_PER_SECOND;
        }
        return 0.0D;
    }

    /** One step toward {@code target}, arriving rather than creeping. */
    private static float eased(float current, float target,
                               double elapsed) {
        float value = (float)LostTalesChatMotion.approach(current, target,
                elapsed, SLIDE_SECONDS);
        // Inside one display pixel there is nothing left to draw, and
        // an exponential tail spends longer and longer covering it — the
        // last steps arriving further and further apart, which reads as
        // stepping rather than as the end of a slide. So the last one is
        // simply arrived at, and no sooner: snapping a whole GUI pixel
        // would itself be a jump of three at GUI scale three.
        return Math.abs(target - value) < displayStep() ? target : value;
    }

    /**
     * Puts the hairline and the restore control after the tabs, at the
     * edge the row is currently drawn to. Both drawing and hit testing
     * read these, so a control answers where it is seen even while the
     * row is still settling.
     */
    private void placeLeftRun() {
        if (!this.showRestore) {
            this.tabDividerX = -1;
            this.restoreX = -1;
            this.restoreRunFraction = 0.0F;
            return;
        }
        // Whole pixels for hit testing, and the fraction the tabs stand
        // on kept beside them: the run is drawn inside a matrix moved by
        // it, so it travels with the tabs a display pixel at a time
        // rather than a whole GUI pixel behind them.
        float edge = this.drawnTabsRight;
        int whole = (int)Math.floor(edge);
        this.restoreRunFraction = edge - whole;
        this.tabDividerX = whole + END_CONTROL_GAP;
        this.restoreX = this.tabDividerX + DIVIDER_WIDTH + END_CONTROL_GAP;
    }

    /**
     * Where a dragged tab's left edge is drawn: under the pointer, held
     * where the tab was taken hold of, and never past either end of the
     * tabs' own span. Its slot is what it falls back to before the
     * pointer has been read.
     */
    private int draggedLeft(Row row, Tab tab) {
        if (row.draggedLeft == Integer.MIN_VALUE) {
            return tab.x;
        }
        return draggedRunLeft(row) + tab.runOffset;
    }

    /**
     * Where the carried run's own left edge is drawn: the pressed tab
     * under the hand, the rest of the run held against it, and the whole
     * of it kept inside the row.
     *
     * <p>A run being reordered stops where the row's tabs settle to, so
     * the hairline and the {@code +} it would otherwise push are a firm
     * border. One still arriving from another window may go on to the
     * row's own limit, which is where the end controls begin.</p>
     */
    private int draggedRunLeft(Row row) {
        int wanted = row.draggedLeft - this.draggedPressedOffset;
        return Math.max(row.left + SEARCH_RUN,
                Math.min(carryLimit(row) - this.draggedRunWidth, wanted));
    }

    /**
     * The edge a carried run is stopped at: the row's own limit, where
     * its end controls begin. The hairline and the {@code +} are not a
     * border to a tab under the hand — a run carried to the end pushes
     * them along in front of it, whether it has just arrived from
     * another window or has lived in this row all along, and whether or
     * not it has been put down since. They are where the row's tabs
     * come to rest, not a wall the hand is stopped by. Only the drawing
     * is stopped here: whether the pull has left the row is measured
     * from the pointer against the strip itself, so the clamp cannot
     * make one direction of pull cheaper than another.
     */
    private int carryLimit(Row row) {
        // The room the tabs move in runs from the search control to the
        // window's padlock, and the hairline and the {@code +} live
        // inside it, trailing the last tab. So a tab stops far enough
        // short of the padlock for those to still fit before it: run the
        // tab itself all the way up and it shoves them over the padlock.
        //
        // That is what {@code tabsLimit} already is — the row's own
        // right-hand limit, the padlock less the room the restore run
        // keeps. It is measured from the strip, never from the tabs, so
        // it does not move as tabs come and go and the place a tab was
        // taken from is still inside the room a moment later.
        return this.tabsLimit;
    }

    /**
     * Measures the run the hand is carrying: each of its tabs' place
     * inside it, how wide the whole of it is, and how far into it the
     * pressed tab stands. Everything that positions, leans or bounds the
     * run reads these, so a group is one shape rather than a set of tabs
     * that happen to travel together. Cheap and idempotent — every
     * caller that has just laid the row out runs it.
     */
    private void measureRun(List<Tab> tabs, Row row) {
        int offset = 0;
        this.draggedPressedOffset = 0;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (!isCarried(row, tab.tab)) {
                continue;
            }
            tab.runOffset = offset;
            if (tab.tab.equals(row.dragging)) {
                this.draggedPressedOffset = offset;
            }
            offset += tab.width + TAB_GAP;
        }
        this.draggedRunWidth = offset == 0 ? 0 : offset - TAB_GAP;
    }

    /** How wide the run this row is carrying is drawn, or zero. */
    int carriedRunWidth(FontRenderer font, Row row) {
        measureRun(layout(font, row), row);
        return this.draggedRunWidth;
    }

    /** The run's right edge as drawn. */
    private int draggedRunRight(Row row) {
        return draggedRunLeft(row) + this.draggedRunWidth;
    }

    /**
     * Which place in the row a tab being dragged along it has reached:
     * past every tab it has crossed a third of the way onto, the ones
     * travelling with it left out. An insert-before index into the
     * row's tab list, or -1 while the tab is not in this row.
     *
     * <p>Measured against the row with the tabs being dragged taken out
     * of it, so the places do not move as the run passes between them:
     * a threshold that moved with the run would be crossed again the
     * moment it was crossed, and the row would shake. The thresholds
     * themselves, and the memory that keeps a fresh swap from swapping
     * straight back, live in {@link #reorderSlot}; here the row is only
     * turned into the rest positions that rule reads.</p>
     */
    int slideIndexAt(FontRenderer font, Row row, List<ChatTab> group) {
        List<Tab> tabs = layout(font, row);
        measureRun(tabs, row);
        boolean holdsDragged = false;
        int others = 0;
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index).tab.equals(row.dragging)) {
                holdsDragged = true;
            }
            if (!group.contains(tabs.get(index).tab)) {
                others++;
            }
        }
        if (!holdsDragged) {
            return -1;
        }
        if (row.dragging == null || !row.dragging.equals(this.reorderOwner)) {
            // A drag this row has not seen yet — newly pressed, or just
            // docked in from another window: its memory starts clean.
            this.reorderOwner = row.dragging;
            this.reorderLatch.clear();
        }
        // The row with the carried run lifted out: where each remaining
        // tab would rest, which is also where the run itself would rest
        // at each place it could take.
        int[] restLeft = new int[others];
        int[] widths = new int[others];
        int cursor = row.left + SEARCH_RUN;
        int at = 0;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (group.contains(tab.tab)) {
                continue;
            }
            restLeft[at] = cursor;
            widths[at] = tab.width;
            cursor += tab.width + TAB_GAP;
            at++;
        }
        return reorderSlot(draggedRunLeft(row), heldBefore(tabs, group),
                restLeft, widths, TAB_GAP, this.reorderLatch);
    }

    /**
     * What one drag remembers about the last place it changed: the
     * boundary it crossed, the way it went, and where the run's left
     * edge stood when it did. {@link #reorderSlot} says why a boundary
     * just crossed needs remembering at all.
     */
    static final class ReorderLatch {
        /** Index in the rest arrays of the tab last swapped with; -1
         *  while no swap stands in need of holding. */
        int boundary = -1;
        /** Whether that swap carried the run rightward. */
        boolean rightward;
        /** The run's left edge at the moment of the swap. */
        int crossedAt;

        void clear() {
            this.boundary = -1;
        }
    }

    /**
     * The place a dragged run has reached among the row's other tabs.
     * {@code restLeft} and {@code width} describe those tabs with the
     * run lifted out — each one's resting left edge, which is also the
     * run's own rest at each place it could take — {@code left} is
     * where the run's left edge is now, and {@code current} the place
     * it holds.
     *
     * <p>Every boundary between two places is two lines, a third of the
     * tab in from either side: coming from the left the run takes the
     * far place at the first line, coming from the right it takes it
     * back at the second, so the reach is a third of the neighbour
     * whichever way the hand goes. Between the lines the run keeps
     * whatever place it has — except at the boundary just crossed. Its
     * nearer line already lies behind a run that has only just crossed
     * the farther one, so read cold it would hand the swap straight
     * back; the {@code latch} instead holds that one boundary to the
     * crossing itself, until the run is carried a clear step past the
     * far line or retreats {@link #SWAP_UNDO} behind the point it
     * swapped at. A fast drag crosses as many boundaries in one step as
     * the pointer did.</p>
     */
    static int reorderSlot(int left, int current, int[] restLeft,
                           int[] width, int gap, ReorderLatch latch) {
        int best = 0;
        for (int index = 0; index < width.length; index++) {
            int slot = index + 1;
            int third = width[index] / SWAP_SHARE;
            // The line a run coming from the left takes this place at,
            // and the one a run coming back over the tab gives it up
            // at; the guard leans against the move either way.
            int enter = restLeft[index] + third + SWAP_GUARD;
            int keep = restLeft[index] + width[index] + gap - third
                    - SWAP_GUARD;
            int threshold;
            if (latch.boundary == index) {
                if (latch.rightward) {
                    if (left >= keep + SWAP_UNDO) {
                        // Carried a clear step past the far line: the
                        // swap no longer needs holding, and the line
                        // takes over from here. The step is what keeps
                        // the line real — released exactly on it, the
                        // run would sit with no room to tremble, and a
                        // slow slide across it rocked the pair back and
                        // forth as the hand breathed.
                        latch.clear();
                        threshold = keep;
                    } else {
                        threshold = Math.min(keep,
                                latch.crossedAt - SWAP_UNDO);
                    }
                } else {
                    if (left <= enter - SWAP_UNDO) {
                        latch.clear();
                        threshold = enter;
                    } else {
                        threshold = Math.max(enter,
                                latch.crossedAt + SWAP_UNDO);
                    }
                }
            } else {
                threshold = slot > current ? enter : keep;
            }
            if (left >= threshold) {
                best = slot;
            }
        }
        if (best != current) {
            // Remember the boundary between the old place and the new
            // one nearest the new: the one a tremor would recross first.
            latch.boundary = best > current ? best - 1 : best;
            latch.rightward = best > current;
            latch.crossedAt = left;
        }
        return best;
    }

    /** The place the dragged run holds now: the tabs left before it. */
    private static int heldBefore(List<Tab> tabs, List<ChatTab> group) {
        int slot = 0;
        for (int index = 0; index < tabs.size(); index++) {
            if (group.contains(tabs.get(index).tab)) {
                return slot;
            }
            slot++;
        }
        return slot;
    }

    /* The controls are the sheet's sprites, drawn 1:1 and centred in
       their hit squares; hovering swaps in the sprite's hover state. */

    private void drawTabControl(ChatIconSheet resting,
                                ChatIconSheet hovered, float fade, float x,
                                int y, int alpha) {
        ChatIconSheet.drawPairWithShadow(resting, hovered, fade,
                x + (CONTROL_SIZE - resting.getWidth()) / 2,
                y + (CONTROL_SIZE - resting.getHeight()) / 2, alpha);
    }

    /**
     * The y that centres a sprite of the given height in the strip above
     * the window's top rule, which takes the strip's last row.
     */
    /**
     * Where a control of {@code height} pixels stands to sit in the
     * middle of the strip. The strip's last row is the window's top
     * rule, so the rows a control may use are one fewer; an odd
     * remainder is spent below, which puts every control the strip
     * carries on one centre row whatever each of them measures.
     */
    private static int centredInStrip(int rowBottom, int height) {
        return rowTop(rowBottom) + (ROW_HEIGHT - 1 - height) / 2;
    }

    /**
     * The lock's body keeps its place whichever way the shackle goes:
     * every frame starts at the same left edge and stands on the same
     * row, the open shackle reaching past the square into the gap.
     */
    private void drawLock(int x, int rowBottom, boolean locked,
                          boolean hovered) {
        // The frames stand on the floor of the box the swing needs, and
        // that box is taller than the padlock at rest: centring the box
        // would leave the resting lock a row below its neighbours, so
        // the resting shape is what is centred and the swing reaches up
        // out of the strip's middle.
        this.lockAnimation.draw(x,
                centredInStrip(rowBottom, ChatLockAnimation.SHUT_HEIGHT)
                        - (ChatLockAnimation.HEIGHT
                                - ChatLockAnimation.SHUT_HEIGHT),
                locked, hovered, scaled(0xFF));
    }

    /**
     * One of the row's end controls — the restore {@code +}, the
     * window's cog, the window's cross — drawn where it was laid out
     * and centred in the strip, the way the lock beside them is.
     */
    private void drawEndControl(ChatIconSheet resting, ChatIconSheet hovered,
                                float fade, int x, int rowBottom) {
        ChatIconSheet.drawPairWithShadow(resting, hovered, fade, x,
                centredInStrip(rowBottom, resting.getHeight()),
                scaled(0xFF));
    }

    /** Whether the pointer is on that control of that very tab. */
    private static boolean onControl(Hit hovered, Tab tab, HitKind kind) {
        return hovered != null && hovered.kind == kind
                && hovered.tab != null && hovered.tab.equals(tab.tab);
    }

    /** One step of a control's crossfade, by what the row is hovering. */
    private float fade(float progress, Hit hovered, HitKind kind) {
        return LostTalesChatVisualStyle.hoverFade(progress,
                hovered != null && hovered.kind == kind, this.frameElapsed);
    }

    /**
     * A hairline between two of the window's controls: the chat's own
     * vertical rule, as tall as a control and no taller, so it divides
     * without drawing a second edge across the strip.
     */
    private void drawDivider(int x, int rowBottom) {
        LostTalesChatVisualStyle.drawDivider(x,
                centredInStrip(rowBottom, DIVIDER_HEIGHT), DIVIDER_HEIGHT,
                scaled(LostTalesChatVisualStyle.DIVIDER_ALPHA));
    }

    /**
     * The tab search control: the chevron whose run says whether its
     * panel is out, centred in the strip like every other control the
     * row carries. The frames differ in height, so the run is centred in
     * the strip's control band rather than pinned to one row of it.
     */
    private void drawSearch(int x, int rowBottom, boolean open,
                            boolean hovered) {
        this.searchChevron.advance(open, hovered);
        this.searchChevron.draw(x, rowTop(rowBottom), SEARCH_WIDTH,
                ROW_HEIGHT - 1, scaled(0xFF));
    }

    /** The drag handle at the strip's right end, where a title bar keeps it. */
    private void drawGrip(int left, int right, int rowBottom, float fade) {
        if (right - left < MIN_GRIP_WIDTH) {
            return;
        }
        ChatIconSheet.drawPairWithShadow(ChatIconSheet.GRIP,
                ChatIconSheet.GRIP_HOVER, fade,
                right - GRIP_INSET - ChatIconSheet.GRIP.getWidth(),
                centredInStrip(rowBottom, ChatIconSheet.GRIP.getHeight()),
                scaled(0xFF));
    }

    /**
     * Resting geometry for the row, cached until the font, limits, tab
     * list, selection, a label, a counter, a mute or the control set
     * changes. The end controls keep their room at the right; the tabs
     * share what is left: whole while that fits, else the tab in front
     * whole and the others given one common room for their names and
     * controls, the counters going when even that leaves no room for
     * their icons. A tab that still finds no room is left out of the
     * row rather than crossing the limit; it stays open and reachable
     * by cycling. Which controls a tab shows inside its room is the
     * draw's to decide, from the width it is drawn at.
     */
    List<Tab> layout(FontRenderer font, Row row) {
        if (font == null || row == null || row.tabs == null
                || row.tabs.isEmpty()) {
            this.cachedTabs = Collections.emptyList();
            this.lockX = -1;
            this.restoreX = -1;
            this.windowSettingsX = -1;
            this.windowCloseX = -1;
            this.tabDividerX = -1;
            this.firstDividerX = -1;
            this.secondDividerX = -1;
            this.tabsRight = row == null ? 0 : row.left + SEARCH_RUN;
            this.controlsRight = this.tabsRight;
            return this.cachedTabs;
        }
        boolean showClose = row.closable;
        if (isLayoutCurrent(font, row, showClose)) {
            return this.cachedTabs;
        }
        List<ChatTab> channels = row.tabs;
        // Whether the row holds a different set of tabs, rather than the
        // same ones in another order. A tab joining or leaving gives or
        // hands back room; a reorder only swaps two of them over.
        this.seedGivesRoomAtOnce =
                channels.size() != this.cachedChannels.size()
                        || !channels.containsAll(this.cachedChannels);
        if (this.tabsRightSeen && this.seedGivesRoomAtOnce) {
            // A tab joining or leaving is the only change that can move
            // the controls without moving a tab, since one leaving the
            // end of a row moves no neighbour at all. Remembered here,
            // where the old row is still known, and paid off by the
            // slide — which gives up room at once and travels the room
            // handed back, however the tab left: closed by its cross,
            // or carried off by the hand.
            //
            // Two tabs changing places is not that. The tab that ends up
            // last is travelling to its new size and place already, and
            // the controls are read off it, so they arrive with it. Give
            // them a travel of their own here and they set off on a
            // second motion of their own alongside the tab's, which
            // reads as their coming unstuck from it.
            this.controlsNeedSeed = true;
            this.controlsSeedOffset = this.drawnTabsRightOffset;
        }
        int count = channels.size();
        String badge = row.showRestore
                ? ClientChatChannelViews.counterText(row.closedUnread) : "";
        int badgeWidth = badge.length() == 0 ? 0
                : COUNTER_GAP + font.getStringWidth(badge);
        int restoreRun = row.showRestore ? restoreRunWidth(badgeWidth) : 0;
        int windowRun = windowControlsWidth(row.windowControls);
        int endControls = restoreRun + END_CONTROL_GAP + windowRun
                + MIN_GRIP_WIDTH;
        int limit = row.right - endControls;
        this.tabsLimit = limit;
        int available = limit - row.left - SEARCH_RUN
                - TAB_GAP * (count - 1);
        int controls = controlsWidth(showClose);
        int[] labelWidths = new int[count];
        int[] counters = new int[count];
        int natural = 0;
        for (int index = 0; index < count; index++) {
            ChatTab channel = channels.get(index);
            labelWidths[index] = font.getStringWidth(
                    this.cachedLabels.get(channel));
            counters[index] = countersWidth(
                    font.getStringWidth(ClientChatChannelViews.counterText(
                            count(this.cachedPings, channel))),
                    font.getStringWidth(ClientChatChannelViews.counterText(
                            count(this.cachedOther, channel))));
            natural += PADDING_X * 2 + labelWidths[index] + counters[index]
                    + iconWidth(channels.get(index)) + controls;
        }
        boolean controlsEverywhere = natural <= available;
        int selectedIndex = 0;
        for (int index = 0; index < count; index++) {
            if (channels.get(index).equals(row.selected)) {
                selectedIndex = index;
            }
        }
        // What each tab's name and controls have between them: the whole
        // of both while the row holds every tab whole, else the tab in
        // front keeps the whole of its own and the others share what is
        // left as one common room. Which controls a tab shows inside its
        // room, and how much of the room its name keeps, is decided
        // where the tab is drawn, from the width it is drawn at.
        int[] content = new int[count];
        for (int index = 0; index < count; index++) {
            content[index] = labelWidths[index] + controls;
        }
        boolean showCounters = true;
        int[] fixed = fixedWidths(channels, counters, true);
        int first = 0;
        int last = count - 1;
        if (!controlsEverywhere) {
            // The tab in front is reserved whole — name, cog and cross,
            // since it is the one being worked with — and the other tabs
            // share what is left: with their counters while that leaves
            // every one of them its icon, without them otherwise. Only a
            // row that cannot hold even bare icons shows fewer tabs, and
            // then a run around the tab in front rather than whichever
            // happen to be leftmost, so a tab does not come and go as
            // the selection moves.
            int room = sharedRoom(fixed, content, selectedIndex, available,
                    first, last);
            if (room < 0) {
                showCounters = false;
                fixed = fixedWidths(channels, counters, false);
                room = sharedRoom(fixed, content, selectedIndex, available,
                        first, last);
            }
            while (room < 0 && first <= last) {
                if (last > selectedIndex) {
                    last--;
                } else if (first < selectedIndex) {
                    first++;
                } else {
                    break;
                }
                room = sharedRoom(fixed, content, selectedIndex, available,
                        first, last);
            }
            capOthers(content, Math.max(0, room), selectedIndex, first, last);
        }
        List<Tab> tabs = new ArrayList<Tab>(count);
        int x = row.left + SEARCH_RUN;
        for (int index = first; index <= last; index++) {
            ChatTab channel = channels.get(index);
            boolean selected = channel.equals(row.selected);
            // The whole name, drawn into the room the row gives it and
            // cut where that room ends: a narrowing tab shows a little
            // less of its name with every pixel, never a letter less
            // every few.
            String label = this.cachedLabels.get(channel);
            int labelWidth = labelWidths[index];
            String pingText = showCounters
                    ? ClientChatChannelViews.counterText(
                            count(this.cachedPings, channel)) : "";
            String otherText = showCounters
                    ? ClientChatChannelViews.counterText(
                            count(this.cachedOther, channel)) : "";
            int pingWidth = font.getStringWidth(pingText);
            int otherWidth = font.getStringWidth(otherText);
            ChatEmoji icon = ChatChannelIcons.iconOf(channel);
            int width = fixed[index] + content[index];
            // The controls the tab shows once it has settled, for the
            // hit test, which answers for the places the tabs settle in.
            TabControls settled = controlsFor(labelWidth, content[index],
                    showClose, selected);
            int edge = x + width - PADDING_X;
            int closeX = -1;
            int settingsX = -1;
            if (settled.close) {
                closeX = edge - CONTROL_SIZE;
                edge = closeX - CONTROL_GAP;
            }
            if (settled.cog) {
                settingsX = edge - CONTROL_SIZE;
            }
            Boolean muted = this.cachedMuted.get(channel);
            Tab built = new Tab(channel, index, icon, label, labelWidth,
                    settled.labelRoom, content[index], fixed[index],
                    pingText, pingWidth, otherText, otherWidth, x, width,
                    settingsX, closeX, muted != null && muted.booleanValue());
            // A tab the row has not held before stands in its own place
            // at its own size; one it has keeps what it was drawn at
            // below, and travels from there.
            built.drawnLeftOffset = x - row.left;
            built.drawnWidthSnapped = width;
            built.drawnWidth = width;
            // A tab the row already held keeps where it was drawn, so a
            // change of places is travelled rather than jumped; one that
            // has just opened starts in its own place.
            for (int at = 0; at < this.cachedTabs.size(); at++) {
                if (this.cachedTabs.get(at).tab.equals(channel)) {
                    Tab was = this.cachedTabs.get(at);
                    built.drawnWidth = was.drawnWidth;
                    built.drawnWidthSnapped = was.drawnWidthSnapped;
                    // Where it was on screen a moment ago. The cursor
                    // that will place it is not known until the row is
                    // advanced, so the travel it owes is worked out
                    // there, against the slot it actually lands in.
                    built.seedLeftOffset = was.drawnLeftOffset
                            + was.slide;
                    built.needsSeed = true;
                    built.hoverFade = was.hoverFade;
                    built.cogFade = was.cogFade;
                    built.closeFade = was.closeFade;
                    built.hoverSeconds = was.hoverSeconds;
                    built.marqueeOffset = was.marqueeOffset;
                    break;
                }
            }
            tabs.add(built);
            x += width + TAB_GAP;
        }
        this.tabsRight = tabs.isEmpty()
                ? row.left + SEARCH_RUN : x - TAB_GAP;
        if (!this.tabsRightSeen) {
            // First sight: the controls stand where the row puts them
            // rather than travelling in from nowhere. Every frame after
            // this the slide advances the edge, and a layout worked out
            // between two draws must not undo what it has reached.
            this.tabsRightSeen = true;
            this.drawnTabsRightOffset = drawnTabsRight(tabs, row) - row.left;
            this.drawnTabsRight = row.left + this.drawnTabsRightOffset;
        }
        // The + opens a channel into this row, so it stands with the
        // tabs; everything that acts on the window itself stands with
        // the grip, against the row's right edge.
        this.showRestore = row.showRestore;
        this.restoreBadge = row.showRestore ? badge : "";
        this.restoreWidth = row.showRestore ? PLUS_WIDTH + badgeWidth : 0;
        placeLeftRun();
        // Where the window's own controls may begin: past the tabs and
        // past the restore control standing after them.
        int leftX = this.tabsRight + END_CONTROL_GAP
                + (row.showRestore ? restoreRunWidth(badgeWidth) : 0);
        int controlX = Math.max(leftX,
                row.right - MIN_GRIP_WIDTH - windowRun);
        this.lockX = controlX;
        controlX += LOCK_WIDTH + END_CONTROL_GAP;
        this.firstDividerX = controlX;
        controlX += DIVIDER_WIDTH + END_CONTROL_GAP;
        if (row.windowControls) {
            this.windowSettingsX = controlX;
            controlX += COG_WIDTH + END_CONTROL_GAP;
            this.windowCloseX = controlX;
            controlX += CLOSE_WIDTH + END_CONTROL_GAP;
            this.secondDividerX = controlX;
            controlX += DIVIDER_WIDTH + END_CONTROL_GAP;
        } else {
            // A locked window keeps its tabs, its size and its place, so
            // it offers neither control; its lock alone divides off the
            // grip.
            this.windowSettingsX = -1;
            this.windowCloseX = -1;
            this.secondDividerX = -1;
        }
        this.controlsRight = controlX;
        this.cachedTabs = Collections.unmodifiableList(tabs);
        this.cachedChannels = new ArrayList<ChatTab>(channels);
        this.cachedSelected = row.selected;
        this.cachedFont = font;
        this.cachedLeft = row.left;
        this.cachedRight = row.right;
        this.cachedShowClose = showClose;
        this.cachedWindowControls = row.windowControls;
        this.cachedShowRestore = row.showRestore;
        this.cachedClosedUnread = row.closedUnread;
        return this.cachedTabs;
    }

    /**
     * What a tab takes before its name and controls: its padding, its
     * icon, and its counters while the row still shows them. What is
     * left of a tab's width past this is the room its name and controls
     * share.
     */
    private static int[] fixedWidths(List<ChatTab> channels, int[] counters,
                                     boolean withCounters) {
        int[] fixed = new int[channels.size()];
        for (int index = 0; index < fixed.length; index++) {
            fixed[index] = PADDING_X * 2 + iconWidth(channels.get(index))
                    + (withCounters ? counters[index] : 0);
        }
        return fixed;
    }

    /**
     * The room the tabs of the run share once the tab in front has its
     * whole content and every tab its fixed part: what the other tabs'
     * names and controls are capped against. Negative when the fixed
     * parts alone overflow the row, which is when the row gives up its
     * counters and then, failing that, whole tabs. Seams are only paid
     * for between the tabs the run shows.
     */
    private static int sharedRoom(int[] fixed, int[] content, int selectedIndex,
                                  int available, int first, int last) {
        int room = available + TAB_GAP * (fixed.length - (last - first + 1));
        for (int index = first; index <= last; index++) {
            room -= fixed[index];
            if (index == selectedIndex) {
                room -= content[index];
            }
        }
        return room;
    }

    /**
     * Caps the content of every tab in the run but the one in front to
     * one common width that fits {@code room} together, as
     * {@link #capLabels} does; the tab in front is left whole.
     */
    private static void capOthers(int[] content, int room, int selectedIndex,
                                  int first, int last) {
        int span = 0;
        for (int index = first; index <= last; index++) {
            if (index != selectedIndex) {
                span++;
            }
        }
        int[] others = new int[span];
        for (int index = first, at = 0; index <= last; index++) {
            if (index != selectedIndex) {
                others[at++] = content[index];
            }
        }
        capLabels(others, room);
        for (int index = first, at = 0; index <= last; index++) {
            if (index != selectedIndex) {
                content[index] = others[at++];
            }
        }
    }

    /**
     * Which of a tab's controls stand in a content room of the given
     * width, and how much of the room its name keeps. The tab in front
     * always shows both — its name gives way before they do — while a
     * tab behind gives them up in stages as its room shrinks: the cog
     * goes the moment the room can no longer hold the whole name with
     * every control, and the cross once less than half the name would
     * show beside it, each time handing its room to the name. Nothing
     * here moves a tab's width: the room is the tab's, and only what
     * stands in it changes.
     */
    static TabControls controlsFor(int labelWidth, int contentRoom,
                                   boolean closable, boolean active) {
        int room = Math.max(0, contentRoom);
        int control = CONTROL_GAP + CONTROL_SIZE;
        int both = control + (closable ? control : 0);
        if (active) {
            return new TabControls(true, closable,
                    Math.max(0, Math.min(labelWidth, room - both)));
        }
        if (room >= labelWidth + both) {
            return new TabControls(true, closable, labelWidth);
        }
        if (closable && room - control >= (labelWidth + 1) / 2) {
            return new TabControls(false, true,
                    Math.min(labelWidth, room - control));
        }
        return new TabControls(false, false, Math.min(labelWidth, room));
    }

    /** What {@link #controlsFor} decides. */
    static final class TabControls {
        final boolean cog;
        final boolean close;
        /** Pixels of the room the name keeps, never past the name itself. */
        final int labelRoom;

        TabControls(boolean cog, boolean close, int labelRoom) {
            this.cog = cog;
            this.close = close;
            this.labelRoom = labelRoom;
        }
    }

    /**
     * The narrowest a tab behind the one in front may be drawn: its
     * padding and its icon, the name and the controls gone. What a row
     * counts each other tab at when asked whether one more fits.
     */
    static int minimumTabWidth(ChatTab tab) {
        return PADDING_X * 2 + iconWidth(tab);
    }

    /**
     * What a row of these tabs needs at the least: the widest of them
     * whole — whichever tab comes to the front must fit with its whole
     * name and both controls — and every other at its minimum. Widths
     * pair up by index.
     */
    static int reservedRowWidth(int[] naturalWidths, int[] minimumWidths) {
        int widest = -1;
        int minimums = 0;
        for (int index = 0; index < naturalWidths.length; index++) {
            minimums += minimumWidths[index];
            if (widest < 0 || naturalWidths[index] > naturalWidths[widest]) {
                widest = index;
            }
        }
        return widest < 0 ? 0
                : minimums - minimumWidths[widest] + naturalWidths[widest];
    }

    /**
     * Where the seams of a row of exact widths land, the first at
     * {@code start}: the running total of the widths and the gaps, each
     * laid on a display pixel. The exact total moves one way while the
     * widths exchange room on one curve, so every seam moves one way
     * too — rounding each width apart made the total wobble by a pixel
     * as neighbours crossed their own rounding at different moments.
     * Two tabs of one exact width may be drawn a display pixel apart
     * for a frame; a seam stepping back and forth is what this trades
     * that for.
     */
    static double[] placeSeams(double[] widths, double gap, double step,
                               double start) {
        double[] seams = new double[widths.length + 1];
        double exact = start;
        seams[0] = snapped(exact, step);
        for (int index = 0; index < widths.length; index++) {
            exact += widths[index] + gap;
            seams[index + 1] = snapped(exact, step);
        }
        return seams;
    }

    /**
     * Shortens the widest labels to one common cap, the largest at which
     * they all fit {@code room} together; labels already narrower keep
     * their width. With no room at all every label goes to nothing.
     */
    static void capLabels(int[] widths, int room) {
        int widest = 0;
        for (int index = 0; index < widths.length; index++) {
            widest = Math.max(widest, widths[index]);
        }
        for (int cap = widest; cap >= 0; cap--) {
            int total = 0;
            for (int index = 0; index < widths.length; index++) {
                total += Math.min(widths[index], cap);
            }
            if (total <= room) {
                for (int index = 0; index < widths.length; index++) {
                    widths[index] = Math.min(widths[index], cap);
                }
                return;
            }
        }
        for (int index = 0; index < widths.length; index++) {
            widths[index] = 0;
        }
    }

    /** Room the tab's icon takes before the label, with its gap. */
    private static int iconWidth(ChatTab tab) {
        return ChatChannelIcons.iconOf(tab) == null ? 0
                : ChatChannelIcons.SIZE + ChatChannelIcons.GAP;
    }

    private static int controlsWidth(boolean showClose) {
        return CONTROL_GAP + CONTROL_SIZE
                + (showClose ? CONTROL_GAP + CONTROL_SIZE : 0);
    }

    /**
     * Room the window's own controls take at the row's right end: its
     * lock and the hairline after it always, and on an unlocked window
     * its settings cog, its close cross and a second hairline before
     * the grip. Each is followed by its own gap.
     */
    private static int windowControlsWidth(boolean unlocked) {
        return LOCK_WIDTH + END_CONTROL_GAP
                + DIVIDER_WIDTH + END_CONTROL_GAP
                + (unlocked ? COG_WIDTH + END_CONTROL_GAP + CLOSE_WIDTH
                        + END_CONTROL_GAP + DIVIDER_WIDTH + END_CONTROL_GAP
                        : 0);
    }

    /** Room the hairline, the {@code +} and its badge take after the tabs. */
    private static int restoreRunWidth(int badgeWidth) {
        return END_CONTROL_GAP + DIVIDER_WIDTH + END_CONTROL_GAP
                + PLUS_WIDTH + badgeWidth;
    }

    /**
     * Room a row keeps clear of its tabs however many there are: the
     * restore control after the last tab, the window's own controls and
     * the grip at the right end. The restore control is reserved with
     * the widest badge the counter can produce, so the tabs do not
     * reflow as the count behind the {@code +} changes and a row sized
     * to fit stays fitting once something goes unread.
     */
    private static int endControlsWidth(ChatWindow window,
                                        FontRenderer font) {
        int badge = font.getStringWidth(ClientChatChannelViews.counterText(
                ClientChatChannelViews.MAX_UNREAD + 1));
        return restoreRunWidth(COUNTER_GAP + badge) + END_CONTROL_GAP
                + windowControlsWidth(!window.isLocked())
                + MIN_GRIP_WIDTH;
    }

    /**
     * The chat width at which the window's row shows every one of its
     * tabs whole: each tab's padding, icon, whole label, counters and
     * both controls, the seams between them, and the room the end
     * controls keep. Zero when the row cannot be measured, which leaves
     * the window following the game's own chat width.
     */
    static int chatWidthForWholeRow(Minecraft minecraft, ChatWindow window) {
        if (minecraft == null || minecraft.fontRenderer == null
                || window == null) {
            return 0;
        }
        FontRenderer font = minecraft.fontRenderer;
        List<ChatTab> tabs = ChatWindowFrame.visibleTabs(window);
        if (tabs.isEmpty()) {
            return 0;
        }
        int rowWidth = SEARCH_RUN + endControlsWidth(window, font)
                + TAB_GAP * (tabs.size() - 1);
        for (int index = 0; index < tabs.size(); index++) {
            rowWidth += naturalWidth(font, tabs.get(index));
        }
        // The screen lays the row out two pixels inside the window's box
        // on either side, and a chat width describes the box.
        return Math.max(ChatWindowPlacement.minChatWidth(minecraft),
                ChatWindowPlacement.chatWidthForBox(rowWidth + 4,
                        minecraft));
    }

    /**
     * A tab's width with nothing given up: its padding, icon, whole
     * name, counters and both controls. What the tab in front is
     * reserved, and what every tab shows while the row has room.
     */
    private static int naturalWidth(FontRenderer font, ChatTab tab) {
        return PADDING_X * 2 + iconWidth(tab) + controlsWidth(true)
                + font.getStringWidth(ClientChatChannelState.displayName(tab))
                + countersWidth(
                        font.getStringWidth(ClientChatChannelViews.counterText(
                                ClientChatChannelViews.unreadPingCount(tab))),
                        font.getStringWidth(ClientChatChannelViews.counterText(
                                ClientChatChannelViews.unreadOtherCount(tab))));
    }

    /**
     * The chat width below which the row could not hold its tabs at
     * their least: the widest of them whole — whichever comes to the
     * front keeps its whole name and both controls — every other at its
     * icon alone, the seams between them, and the room the end controls
     * hold. Zero when the row cannot be measured.
     *
     * <p>This is what bounds a resize. A window may be dragged as narrow
     * as its own tabs allow and no narrower, so a tab can never be made
     * to vanish by pulling an edge — the other tabs' names give way down
     * to their icons and the edge then stops. The same measure decides
     * whether a row has room for one more tab.</p>
     */
    static int chatWidthForNarrowestRow(Minecraft minecraft,
                                        ChatWindow window) {
        if (minecraft == null || minecraft.fontRenderer == null
                || window == null) {
            return 0;
        }
        FontRenderer font = minecraft.fontRenderer;
        List<ChatTab> tabs = ChatWindowFrame.visibleTabs(window);
        if (tabs.isEmpty()) {
            return 0;
        }
        int rowWidth = SEARCH_RUN + endControlsWidth(window, font)
                + TAB_GAP * (tabs.size() - 1) + reservedRowWidth(font, tabs);
        return Math.max(ChatWindowPlacement.minChatWidth(minecraft),
                ChatWindowPlacement.chatWidthForBox(
                        rowWidth + STRIP_INSET * 2, minecraft));
    }

    /** {@link #reservedRowWidth(int[], int[])} over the tabs themselves. */
    private static int reservedRowWidth(FontRenderer font, List<ChatTab> tabs) {
        int[] natural = new int[tabs.size()];
        int[] minimum = new int[tabs.size()];
        for (int index = 0; index < tabs.size(); index++) {
            natural[index] = naturalWidth(font, tabs.get(index));
            minimum[index] = minimumTabWidth(tabs.get(index));
        }
        return reservedRowWidth(natural, minimum);
    }

    private static int count(Map<ChatTab, Integer> counters, ChatTab tab) {
        Integer value = counters.get(tab);
        return value == null ? 0 : value.intValue();
    }

    /** Refreshes the cache keys and reports whether the layout still holds. */
    private boolean isLayoutCurrent(FontRenderer font, Row row,
                                    boolean showClose) {
        boolean current = font == this.cachedFont
                && row.left == this.cachedLeft
                && row.right == this.cachedRight
                && (row.selected == null ? this.cachedSelected == null
                        : row.selected.equals(this.cachedSelected))
                && showClose == this.cachedShowClose
                && row.windowControls == this.cachedWindowControls
                && row.showRestore == this.cachedShowRestore
                && row.closedUnread == this.cachedClosedUnread
                && row.tabs.equals(this.cachedChannels);
        for (int index = 0; index < row.tabs.size(); index++) {
            ChatTab tab = row.tabs.get(index);
            String label = ClientChatChannelState.displayName(tab);
            int pings = ClientChatChannelViews.unreadPingCount(tab);
            int other = ClientChatChannelViews.unreadOtherCount(tab);
            boolean muted = ChatWindowLayout.isMuted(tab);
            Boolean cachedMute = this.cachedMuted.get(tab);
            if (!label.equals(this.cachedLabels.get(tab))
                    || pings != count(this.cachedPings, tab)
                    || other != count(this.cachedOther, tab)
                    || cachedMute == null
                    || muted != cachedMute.booleanValue()) {
                this.cachedLabels.put(tab, label);
                this.cachedPings.put(tab, Integer.valueOf(pings));
                this.cachedOther.put(tab, Integer.valueOf(other));
                this.cachedMuted.put(tab, Boolean.valueOf(muted));
                current = false;
            }
        }
        return current;
    }

    private static int countersWidth(int pingWidth, int otherWidth) {
        return (pingWidth > 0 ? COUNTER_GAP + pingWidth : 0)
                + (otherWidth > 0 ? COUNTER_GAP + otherWidth : 0);
    }

    /**
     * Whether the window's row has room for {@code candidates} besides
     * the tabs it shows: the same measure that bounds a resize — the
     * widest tab whole and every other at its icon, the seams between
     * them, and the end controls' reserved room, the restore control's
     * counted at its widest badge so the answer does not flap as the
     * {@code +} comes and goes. This is what the auto-open policy asks
     * before filing a new tab into a window, and what a dock asks before
     * a carried tab joins a row: a row that can hold one more tab at
     * that least takes it, however tight that makes the names; one that
     * cannot is full, and the channel opens elsewhere. Without a
     * renderer to measure with the answer is yes, which keeps the layout
     * usable headlessly.
     */
    static boolean rowHasRoomFor(Minecraft minecraft, ChatWindow window,
                                 List<ChatTab> candidates) {
        if (minecraft == null || minecraft.fontRenderer == null
                || window == null || candidates == null) {
            return true;
        }
        FontRenderer font = minecraft.fontRenderer;
        List<ChatTab> tabs = new ArrayList<ChatTab>(
                ChatWindowFrame.visibleTabs(window));
        for (ChatTab candidate : candidates) {
            if (candidate != null && !tabs.contains(candidate)) {
                tabs.add(candidate);
            }
        }
        if (tabs.isEmpty()) {
            return true;
        }
        net.minecraft.client.gui.ScaledResolution resolution =
                new net.minecraft.client.gui.ScaledResolution(minecraft,
                        minecraft.displayWidth, minecraft.displayHeight);
        ChatWindowPlacement.Box box = ChatWindowPlacement.windowBounds(
                window, minecraft, resolution.getScaledWidth(),
                resolution.getScaledHeight());
        // The row spans the window minus the two-pixel insets the screen
        // lays it out with.
        int rowWidth = box.width - 4 - SEARCH_RUN;
        int available = rowWidth - endControlsWidth(window, font)
                - TAB_GAP * (tabs.size() - 1);
        return reservedRowWidth(font, tabs) <= available;
    }

    static final class Tab {
        final ChatTab tab;
        /** The tab's index in the row's full list; a trimmed run skips some. */
        final int rowIndex;
        /** The channel's emoji before the label, or null. */
        final ChatEmoji icon;
        /** The whole name; the draw shows as much of it as its room leaves. */
        final String label;
        final int labelWidth;
        /**
         * Pixels the name keeps once the tab has settled at its width,
         * beside whichever controls that width holds; less than the name
         * is wide when the row is crowded. The draw reads its own from
         * the width the tab is drawn at.
         */
        final int labelRoom;
        /** Pixels the name and the controls share once the tab has settled. */
        final int contentRoom;
        /** Pixels before that room: padding, icon and counters. */
        final int fixedWidth;
        /**
         * How far the name is shifted left to show its hidden end while
         * the tab is hovered, and how long the pointer has rested on it;
         * both carried on when the row is laid out again.
         */
        float marqueeOffset;
        double hoverSeconds;
        /** {@code [p]} unread pings, or empty. */
        final String pingText;
        final int pingWidth;
        /** {@code [x]} other unread lines, or empty. */
        final String otherText;
        final int otherWidth;
        /** Resting left edge before the row's horizontal motion. */
        final int x;
        /**
         * How wide the tab is drawn while it settles into the width the
         * row has given it, and where the row's running cursor has put
         * it — whole pixels, laid down one after another, so the seam
         * between two tabs is exactly the seam and never a rounding of
         * two numbers that were rounded apart.
         */
        float drawnWidth;
        /**
         * The width and place the tab is really drawn at, both laid on
         * whole <em>display</em> pixels rather than whole GUI ones. A
         * display pixel is the finest step the screen has — a third of a
         * GUI pixel at GUI scale three — so a row reflowing moves in
         * steps that small instead of jumping a whole GUI pixel at a
         * time, and the border artwork still lands exactly on its own
         * texels. The places are the seams of the exact running total,
         * each laid on a display pixel, and the width is what lies
         * between two seams; see {@link #placeSeams}.
         */
        float drawnWidthSnapped;
        float drawnLeftOffset;
        /**
         * What this tab still owes to a change of <em>place</em>: the
         * distance from where it is drawn to the slot the row has given
         * it, eased away to nothing. A tab whose place has not changed
         * owes nothing and stands exactly on the cursor, which is what
         * keeps a row reflowing to new widths from shuffling by a pixel
         * — every tab has one position, not one of its own.
         */
        float slide;
        /** Where it was drawn before the row was laid out again. */
        private float seedLeftOffset;
        private boolean needsSeed;
        /** Where this tab stands inside the run being carried, if it is
         *  one of them; measured each frame by {@code measureRun}. */
        int runOffset;
        /** How far the tab and its controls have crossed to their lit
         *  artwork; carried on when the row is laid out again. */
        float hoverFade;
        float cogFade;
        float closeFade;
        final int width;
        /** Resting left edge of the cog once settled, or -1 when the tab shows none. */
        final int settingsX;
        /** Resting left edge of the close cross once settled, or -1. */
        final int closeX;
        final boolean muted;

        Tab(ChatTab tab, int rowIndex, ChatEmoji icon, String label,
            int labelWidth, int labelRoom, int contentRoom, int fixedWidth,
            String pingText, int pingWidth, String otherText, int otherWidth,
            int x, int width, int settingsX, int closeX, boolean muted) {
            this.tab = tab;
            this.rowIndex = rowIndex;
            this.icon = icon;
            this.label = label;
            this.labelWidth = labelWidth;
            this.labelRoom = labelRoom;
            this.contentRoom = contentRoom;
            this.fixedWidth = fixedWidth;
            this.pingText = pingText;
            this.pingWidth = pingWidth;
            this.otherText = otherText;
            this.otherWidth = otherWidth;
            this.x = x;
            this.width = width;
            this.drawnWidth = width;
            this.drawnWidthSnapped = width;
            this.settingsX = settingsX;
            this.closeX = closeX;
            this.muted = muted;
        }
    }
}
