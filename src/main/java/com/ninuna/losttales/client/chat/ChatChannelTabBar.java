package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;

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
 * forward — so nothing sweeps across the tabs between. What waits
 * unread in a tab sits in its icon's corner ({@link ChatIconMark}); a
 * muted tab is drawn dim and italic. The channel's colour runs
 * across each tab's face two rows under that line, full at the centre
 * and fading to nothing at both ends.
 *
 * <p>Every tab is one width, browser-style: the row's default while
 * the row holds them all at it, and one narrower width they all share
 * once it does not — the tab in front included, which is never wider
 * than its neighbours. A narrowing tab draws its name whole and cuts it
 * where its room ends — resting the pointer on such a tab slides the
 * name along to show the rest — and its buttons go at fixed shares of
 * the full width, every tab's at once since every tab is one width: the
 * draft mark under two thirds, the cog under a half, the cross under a
 * third. A button fades as it goes while the name glides into its room,
 * so nothing jumps. The tab in front keeps its cross throughout; its
 * icon and name are cut short before it like any name, so the cross
 * ends where the icon stood. The row's other controls stand at its two ends:
 * the restore {@code +} follows the last tab, since what it opens joins
 * that row, and the window's own controls are gathered against the
 * right edge beside the grip that moves it, in the order a title bar
 * reads them — the lock, a hairline, the settings cog, the fullscreen
 * control and the close cross, another hairline, then the grip. All of
 * them keep their room however many tabs there are, all of them are
 * centred on the same row
 * of the strip, and the bare stretch left between the two ends drags
 * the window as the grip does. Geometry is computed once per change of
 * inputs and reused by drawing and hit testing, so a frame allocates
 * nothing.</p>
 *
 * <p>Every change of the row — a tab opening or closing, one carried
 * past its neighbours, into the row or out of it — moves the whole row
 * on one shared glide, as the window's own glide to the screen does:
 * each tab sets out from where it is drawn toward the place and width
 * it now has, all of them on the same curve at the same time, so no tab
 * trails another or finds its place in steps. A tab opening grows from
 * nothing where it joins, and a closed one shrinks away where it stood.
 * A tab closed by its cross leaves the others their width while the
 * pointer stays on the row, so the next cross is under the pointer, as a
 * browser does; they take their new width once it leaves.</p>
 *
 * <p>A tab being dragged is one of the row's own tabs throughout: its
 * top rises a pixel off the row as it is picked up, its feet staying on
 * the rule, and the tab in front crosses to its lifted contours; it leans
 * toward the pointer where it stands and changes places with a neighbour
 * as it passes them, so the row always reads as it will once the button
 * comes up. Nothing is ever drawn floating free of the strip, and no
 * insertion bar is needed to say where a tab would land — it is already
 * there. Put down, it settles into its place and glows once in its
 * channel's colour, and the tool strip under the row glows with it; the
 * strip's own surface round the tabs does not.</p>
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
    /** Height of a resting tab's border pieces; the selected pair is
     *  one row taller, the row it stands on the rule with. */
    private static final int PIECE_HEIGHT =
            LostTalesUiSheet.TAB_LEFT.getHeight();
    /**
     * Body height of a tab: the sheet's resting border pieces whole,
     * and under them the strip's rule, which is the strip's last row
     * and the one row a resting tab does not draw on. The selected tab
     * stands over that rule — its pieces are a row taller and end on
     * the rule row, their feet on it, joining the tool strip under it
     * in one surface, as a browser's chosen tab joins the bar below.
     */
    static final int HEIGHT = PIECE_HEIGHT + 1;
    /**
     * Rows the selected tab's top stands above a resting tab's: the
     * selected pieces' extra rows less the rule row they stand on, so
     * none — every tab stands at one top.
     */
    static final int LIFT = LostTalesUiSheet.TAB_SELECTED_LEFT.getHeight()
            - PIECE_HEIGHT - 1;
    /**
     * Clear rows the strip keeps above its tabs: the strip is this much
     * taller than the tab standing tallest in it.
     */
    static final int HEADROOM = 2;
    /** Full height of the row: a resting tab, the lift and the head-room. */
    static final int ROW_HEIGHT = HEIGHT + LIFT + HEADROOM;
    /** Width of a tab's border pieces inside the tab; the same in every state. */
    private static final int BORDER_WIDTH = LostTalesUiSheet.TAB_LEFT.getWidth();
    /**
     * How far the selected pieces reach past the tab's sides: their
     * feet, on the rule's row, spread this far out on either side, and
     * the rule's two pieces begin where the feet end.
     */
    static final int SELECTED_FOOT =
            LostTalesUiSheet.TAB_SELECTED_LEFT.getWidth() - BORDER_WIDTH;
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
    /**
     * The width every tab is drawn at while the row has room for them
     * all at it: a name of about a dozen letters beside its icon and
     * both controls. A row that cannot hold every tab at this width
     * narrows them all alike; a name wider than its room is cut, and
     * read whole by resting the pointer on it.
     */
    static final int DEFAULT_TAB_WIDTH = 128;
    /**
     * The shares of {@link #DEFAULT_TAB_WIDTH} under which every tab
     * gives a button up: the draft mark first, then the cog, then the
     * cross — the tab in front keeping its cross whatever its width.
     */
    static final double DRAFT_SHARE = 2.0D / 3.0D;
    static final double COG_SHARE = 1.0D / 2.0D;
    static final double CLOSE_SHARE = 1.0D / 3.0D;
    /** How far a carried tab rises off the row, and how long it takes to. */
    private static final float LIFT_PIXELS = 1.0F;
    /**
     * The depths the carried run's mask is laid at: its footprint in
     * front of everything the GUI draws, item icons included, and the
     * rest of the row far behind it.
     */
    private static final double MASK_NEAR = 900.0D;
    private static final double MASK_FAR = -500.0D;
    /**
     * The row of every border piece that stretches as a tab rises: the
     * rows above it are carried up with the tab's top, the rows from it
     * on stay where they stood, and it is drawn over the gap between.
     * It lies in the plain run of side line every piece has, resting,
     * lit, selected and lifted alike, so the stretch never shows; the
     * lifted pair's one extra row is left out there
     * ({@code ChatChannelTabBarTest} reads the sheet to hold both).
     */
    static final int LIFT_SEAM_ROW = 8;
    private static final double LIFT_SECONDS = 0.05D;
    /**
     * The glow a tab put down gives once in its channel's colour, on its
     * own surface and the tool strip's: how long it takes to fade, and
     * how far toward the colour the two go at their brightest.
     */
    private static final double GLOW_SECONDS = 0.45D;
    private static final float GLOW_TINT = 0.45F;
    /** Gap between the name and the draft mark after it, and after the {@code +}. */
    private static final int COUNTER_GAP = 3;
    /**
     * The draft mark after a tab's counters while the tab holds unsent
     * text: the sheet's own, with the chat's shadow like every other
     * sprite. It is a button: a press takes the input to the tab with
     * the whole draft chosen. The tab being typed in shows none; its
     * draft is in the field.
     */
    private static final int DRAFT_WIDTH =
            LostTalesUiSheet.DRAFT.getWidth();
    /**
     * The mark after the {@code +} for what waits in the channels it
     * would open: the ping tile or the white sphere, both this wide.
     */
    private static final int RESTORE_MARK_WIDTH = ChatIconMark.TILE_WIDTH;
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
    /**
     * Clear pixels round an end control's ink that answer with it, on
     * every side: a five-pixel glyph is a small thing to hit exactly,
     * and with them it answers on the {@link #END_CONTROL_SIZE} square.
     */
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
    private static final int PLUS_WIDTH = LostTalesUiSheet.PLUS.getWidth();
    private static final int COG_WIDTH = LostTalesUiSheet.COG.getWidth();
    private static final int CLOSE_WIDTH = LostTalesUiSheet.CLOSE.getWidth();
    private static final int FULLSCREEN_WIDTH =
            LostTalesUiSheet.FULLSCREEN.getWidth();
    private static final int GRIP_WIDTH = LostTalesUiSheet.GRIP.getWidth();
    /**
     * The tab search control at the row's left end: a framed button with
     * the sheet's chevron run centred in it, pointing down while its
     * panel is away and folding up to a rule and over as the panel
     * opens. The run rests in the sheet's muted chevrons and lights to
     * the ivory ones. The button keeps one size however the run plays,
     * so the row's geometry does not move.
     */
    private static final LostTalesUiSheet[] SEARCH_FRAMES = {
            LostTalesUiSheet.CHEVRON_1_MUTED, LostTalesUiSheet.CHEVRON_2_MUTED,
            LostTalesUiSheet.CHEVRON_3_MUTED, LostTalesUiSheet.CHEVRON_4_MUTED,
            LostTalesUiSheet.CHEVRON_5_MUTED};
    private static final LostTalesUiSheet[] SEARCH_FRAMES_HOVER = {
            LostTalesUiSheet.CHEVRON_1, LostTalesUiSheet.CHEVRON_2,
            LostTalesUiSheet.CHEVRON_3, LostTalesUiSheet.CHEVRON_4,
            LostTalesUiSheet.CHEVRON_5};
    /**
     * The search button's square: the chevron's width with the strips'
     * wide inset either side. Square, so the chevron's three rows stand
     * in its middle exactly, four clear rows above and below them.
     */
    private static final int SEARCH_SIZE = SEARCH_FRAMES[0].getWidth()
            + 2 * LostTalesUiFramedButton.WIDE_INSET;
    /**
     * How far the strip reaches left of {@link Row#left}: the row is
     * laid out from the first thing standing in it, and the surface it
     * stands on begins here, which is the window's own left edge.
     */
    private static final int STRIP_INSET = 2;
    /** The search bar's well, cut out of the tool strip; null for none. */
    private LostTalesUiHitBox toolStripHole;
    /**
     * The colours the strip and the tool strip were drawn in this frame,
     * each at the window's edge: the window's frame takes them where it
     * runs beside each, so its surface is always the colour it touches.
     */
    int drawnStripArgb;
    int drawnToolArgb;
    /**
     * Clear space either side of the search button: the window's frame
     * edge, this, the button, this again, and then the first tab.
     */
    private static final int SEARCH_MARGIN = 3;
    /**
     * Where the search button begins, measured from the row's left: the
     * margin in from the window's edge, the frame standing just outside
     * it.
     */
    private static final int SEARCH_LEFT = SEARCH_MARGIN - STRIP_INSET;
    /** Where the row's tabs begin: past the search button and its gaps. */
    private static final int SEARCH_RUN =
            SEARCH_LEFT + SEARCH_SIZE + SEARCH_MARGIN;
    /** Room the grip keeps at the row's right end: its glyph and inset. */
    static final int MIN_GRIP_WIDTH = GRIP_WIDTH + GRIP_INSET;
    /**
     * Opacity of a tab's surface, which the bar paints itself in one
     * layer under the border artwork's ink: the chat's inset opacity, two
     * thirds, so a tab nobody has picked or is pointing at wears exactly
     * the surface the typing well and the timestamp column wear. The
     * artwork previews the surface behind its ink at about this alpha;
     * the preview is cut away at draw time on {@link #TAB_INK_THRESHOLD}.
     */
    private static final int TAB_SURFACE_ALPHA =
            LostTalesChatVisualStyle.INSET_ALPHA;
    /**
     * Fragment-alpha share separating a tab piece's ink from the
     * surface preview behind it: ink is authored fully opaque, the
     * preview at about {@link #TAB_SURFACE_ALPHA}, and only what clears the
     * threshold is drawn. The surface itself is painted by the bar in a
     * single layer, so the states stay one colour instead of stacking;
     * {@link LostTalesUiSheetTest} keeps the artwork on the right sides of
     * the threshold.
     */
    static final float TAB_INK_THRESHOLD = LostTalesUiSheet.INK_THRESHOLD;
    /** The line joining a resting tab's border tips. */
    private static final int TIP_RGB =
            LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE);
    /**
     * The same line on a hovered, selected or lifted tab: the lifted pair's
     * tips wear the lit tones too.
     */
    private static final int TIP_LIT_RGB =
            LostTalesColors.rgb(LostTalesColors.IVORY);

    private List<Tab> cachedTabs = Collections.emptyList();
    private List<ChatTab> cachedChannels = Collections.emptyList();
    private ChatTab cachedSelected;
    private final Map<ChatTab, String> cachedLabels =
            new HashMap<ChatTab, String>();
    private final Map<ChatTab, Boolean> cachedDraft =
            new HashMap<ChatTab, Boolean>();
    private final Map<ChatTab, Boolean> cachedMuted =
            new HashMap<ChatTab, Boolean>();
    private FontRenderer cachedFont;
    /** The end lock's swing; one row, one lock, one state. */
    private final ChatLockAnimation lockAnimation = new ChatLockAnimation();
    private int cachedLeft = Integer.MIN_VALUE;
    private int cachedRight = Integer.MIN_VALUE;
    private double cachedRightExact = Double.NaN;
    /**
     * The row's right edge in whole pixels, and the fraction past it
     * the edge really stands at, laid on a display pixel: the end
     * controls are placed from the whole pixels and drawn inside a
     * matrix moved by the fraction, so they follow the edge exactly.
     */
    private int endEdge;
    private float endFraction;
    private boolean cachedShowClose;
    private boolean cachedShowRestore;
    private boolean cachedWindowControls;
    /** What waits in the channels the {@code +} would open, marked after it. */
    private ChatIconMark restoreMark = ChatIconMark.NONE;
    /** Width of the restore control together with its mark. */
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
    /**
     * How each of the strip's buttons answers the pointer: its crossing
     * to the lit artwork and the place it is drawn. One per button, since
     * each keeps its own beat ({@link LostTalesUiButtonMotion}). The grip is not
     * a button and only lights.
     */
    private final LostTalesUiButtonMotion searchMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion restoreMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    /**
     * The cog only rises. Its artwork is unchanged by a quarter turn and
     * has no whole-pixel form at any smaller angle, so a turn shows
     * either nothing or a mess (see the gui-rendering skill).
     */
    /**
     * The cog only rises. Its artwork is unchanged by a quarter turn and
     * has no whole-pixel form at any smaller angle, so a turn shows
     * either nothing at all or a mess; the same goes for the {@code +}.
     */
    private final LostTalesUiButtonMotion windowSettingsMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion windowFullscreenMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    /** Closing a window is decisive, so its cross answers like a switch. */
    private final LostTalesUiButtonMotion windowCloseMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.SNAP);
    private final LostTalesUiButtonMotion lockMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    private float gripFade;
    /** Seconds since the row was last drawn; what the fades step by. */
    private double frameElapsed;
    /** This frame's instant, which the buttons read their beats from. */
    private long frameNanos;
    /** How brightly the tool strip glows with the tab last put down, 0..1. */
    private float toolGlow;
    /** The channel colour the tool strip glows in. */
    private int toolGlowRgb;
    /** The row's own lower cut, handed down to each tab's contents. */
    private double rowClipBottom = Double.NaN;
    /** {@link Row#fractionX} of the row being drawn, for {@link #clipX}. */
    private double clipFractionX;
    /**
     * Where the run of controls after the tabs is drawn: the right edge
     * of the tabs as they are drawn this frame, so it travels with them
     * as they glide.
     */
    private float drawnTabsRight;
    /** Tabs closed out of the row, drawn shrinking to nothing until their glides end. */
    private final List<Tab> leavingTabs = new ArrayList<Tab>();
    /**
     * The one width the row's tabs share, gliding from one to the next
     * as they do: where it set out from, where it is bound, and as it is
     * drawn this frame. The buttons every tab gives up by width read the
     * drawn one.
     */
    private double sharedFrom = DEFAULT_TAB_WIDTH;
    private double sharedTo = DEFAULT_TAB_WIDTH;
    private double sharedDrawn = DEFAULT_TAB_WIDTH;
    private final MotionTransition sharedLeg =
            new MotionTransition(MotionIds.CHAT_TAB_MOVE, true);
    /**
     * How far every tab shows each of the buttons it gives up by width,
     * each going or coming as the row's width crosses its share: its ink
     * fades in one half of the transition and its room glides in the
     * other, so the room is there before the button shows and stays until
     * it has gone ({@link #roomPhase}, {@link #inkPhase}).
     */
    private final MotionTransition draftShown =
            new MotionTransition(MotionIds.CHAT_TAB_CONTROLS);
    private final MotionTransition cogShown =
            new MotionTransition(MotionIds.CHAT_TAB_CONTROLS);
    private final MotionTransition closeShown =
            new MotionTransition(MotionIds.CHAT_TAB_CONTROLS);
    /**
     * The width the row's tabs keep after one was closed by its cross,
     * while the pointer stays on the row; NaN while none is held.
     */
    private double heldWidth = Double.NaN;
    /** The tabs the hand carried last frame, to see them put down. */
    private List<ChatTab> carriedLastFrame = Collections.emptyList();
    /** When the row was last drawn. */
    private long lastFrameNanos;
    /** Whether the row is showing the restore control at all. */
    private boolean showRestore;
    private int lockX = -1;
    private int restoreX = -1;
    /**
     * Left edge of the window's own cog, fullscreen control and cross;
     * -1 when absent.
     */
    private int windowSettingsX = -1;
    private int windowFullscreenX = -1;
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
     * What a point in the row resolves to. {@code SETTINGS},
     * {@code CLOSE} and {@code DRAFT} carry a tab and act on it;
     * {@code WINDOW_SETTINGS}, {@code WINDOW_FULLSCREEN} and
     * {@code WINDOW_CLOSE} carry none and act on the window.
     */
    enum HitKind {
        TAB, CLOSE, SETTINGS, DRAFT, SEARCH, LOCK, RESTORE, WINDOW_SETTINGS,
        WINDOW_FULLSCREEN, WINDOW_CLOSE, GRIP
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
        /**
         * The same limit in fractions of a pixel, as the window's edge
         * really stands: the tabs share their room from this, so a
         * window dragged narrower narrows them by the fraction the edge
         * moved rather than by a whole pixel every few frames. Zero when
         * the row is measured in whole pixels only; {@link #right} then
         * stands in for it.
         */
        double rightExact;
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
         * Whether the window's own cog, fullscreen control and cross are
         * offered. A locked window keeps the tabs and the size it has, so
         * it offers none of them; its padlock is what unlocks it again.
         */
        boolean windowControls;
        /**
         * How far the window has travelled toward filling the screen,
         * 0..1: the fullscreen control crosses from its outward corners
         * to its inward ones with it.
         */
        float fullscreenShare;
        /** Whether the restore control is offered after the row. */
        boolean showRestore;
        /** Whether this row's tab search panel is open right now. */
        boolean searchOpen;
        /** Whether this row's restore list is open right now. */
        boolean restoreOpen;
        /** What waits unread in the closed channels, marked after the +. */
        ChatIconMark closedMark = ChatIconMark.NONE;
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
        /** Where the dragged tab's left edge follows the pointer, in whole pixels. */
        int draggedLeft = Integer.MIN_VALUE;
        /**
         * The same to the pointer's exact position, in the row's own
         * space: where the carried tab is drawn. NaN falls back to
         * {@link #draggedLeft}.
         */
        double draggedLeftExact = Double.NaN;
        /**
         * Whether this window is being resized right now. The row then
         * takes the layout it is given at once instead of easing into
         * it: the tabs are part of the very geometry the hand is
         * dragging, so every one of them follows the edge in the same
         * frame — nothing trails it, and nothing goes on correcting
         * itself after the drag stops.
         */
        boolean resizing;
        /**
         * Whether the window is gliding to or from filling the screen.
         * Like a resize, the row then takes the layout it is given at
         * once, so the tabs travel with the window's edges as one piece
         * instead of easing after them; unlike one, it goes on answering
         * the pointer, since no hand is on an edge.
         */
        boolean gliding;
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

    /**
     * Whether a screen y lies in the row's band as drawn: the row is laid
     * out in whole pixels and drawn moved by its fraction, so the band
     * is read the same way.
     */
    static boolean inRowBand(Row row, double mouseY) {
        double y = mouseY - row.fractionY;
        return y >= rowTop(row.rowBottom) && y < row.rowBottom;
    }

    /** The tool strip's left edge in row space, the window's own left edge. */
    static int toolStripLeft(Row row) {
        return row.offsetX + row.left - STRIP_INSET;
    }

    /** The tool strip's right edge, fractions included, the row laid out first. */
    double toolStripRight(FontRenderer font, Row row) {
        layout(font, row);
        return row.offsetX + this.endEdge + this.endFraction + STRIP_INSET;
    }

    /**
     * Where the message search's well is cut out of the tool strip's
     * surface, in row space; null for a whole strip. The well wears a
     * surface of its own, and no two surfaces are laid over each other.
     */
    void setToolStripHole(LostTalesUiHitBox hole) {
        this.toolStripHole = hole;
    }

    /**
     * Whether a screen y lies in the strip's whole band as drawn: the
     * row's band and the tool strip under its rule, down to the window's
     * top rule, which is the tool strip's last row.
     */
    static boolean inStripBand(Row row, double mouseY) {
        double y = mouseY - row.fractionY;
        return y >= rowTop(row.rowBottom)
                && y < row.rowBottom + ChatWindowPlacement.TOOL_STRIP_HEIGHT;
    }

    /**
     * The window's fullscreen control where it answers, in screen space:
     * the box the snap layouts hang from. Null while the row does not
     * show the control.
     */
    LostTalesUiHitBox fullscreenControlBox(Row row) {
        if (this.windowFullscreenX < 0) {
            return null;
        }
        LostTalesUiHitBox box = endControlBox(this.windowFullscreenX,
                FULLSCREEN_WIDTH, LostTalesUiSheet.FULLSCREEN.getHeight(),
                row.rowBottom);
        return new LostTalesUiHitBox(box.left + row.offsetX + row.fractionX,
                box.top + row.fractionY, box.width, box.height);
    }

    /**
     * What lies under a GUI-space point: a tab, one of the selected tab's
     * controls, an end control, the grip, or nothing. Every control
     * answers on its own box and nowhere else — the search button on its
     * frame, an end control on its square, a tab's cog and cross on
     * their squares — and each box is read from the same numbers the
     * control is drawn with, so where a control lights and where it
     * answers cannot differ.
     */
    Hit hitAt(FontRenderer font, Row row, double mouseX, double mouseY) {
        if (!inRowBand(row, mouseY)) {
            return null;
        }
        List<Tab> tabs = layout(font, row);
        double localX = mouseX - row.offsetX - row.fractionX;
        double localY = mouseY - row.fractionY;
        if (tabs.isEmpty()) {
            return null;
        }
        int bottom = row.rowBottom;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (localX < tab.x || localX >= tab.x + tab.width) {
                continue;
            }
            int tabTop = tabTop(bottom, tab.tab.equals(row.selected));
            if (tab.closeX >= 0 && tabControlBox(tab.closeX, tabTop)
                    .contains(localX, localY)) {
                return new Hit(HitKind.CLOSE, tab.tab);
            }
            if (tab.settingsX >= 0 && tabControlBox(tab.settingsX, tabTop)
                    .contains(localX, localY)) {
                return new Hit(HitKind.SETTINGS, tab.tab);
            }
            if (tab.draftX >= 0 && draftBox(tab.draftX, tabTop)
                    .contains(localX, localY)) {
                return new Hit(HitKind.DRAFT, tab.tab);
            }
            return new Hit(HitKind.TAB, tab.tab, tab.labelWidth > tab.labelRoom);
        }
        if (searchBox(row.left, bottom).contains(localX, localY)) {
            return new Hit(HitKind.SEARCH, null);
        }
        // A control the row is not showing (a negative x) is never hit.
        if (this.lockX >= 0 && lockBox(this.lockX, bottom)
                .contains(localX, localY)) {
            return new Hit(HitKind.LOCK, null);
        }
        if (this.restoreX >= 0 && endControlBox(this.restoreX,
                this.restoreWidth, LostTalesUiSheet.PLUS.getHeight(), bottom)
                .contains(localX, localY)) {
            return new Hit(HitKind.RESTORE, null);
        }
        if (this.windowSettingsX >= 0 && endControlBox(this.windowSettingsX,
                COG_WIDTH, LostTalesUiSheet.COG.getHeight(), bottom)
                .contains(localX, localY)) {
            return new Hit(HitKind.WINDOW_SETTINGS, null);
        }
        if (this.windowFullscreenX >= 0 && endControlBox(
                this.windowFullscreenX, FULLSCREEN_WIDTH,
                LostTalesUiSheet.FULLSCREEN.getHeight(), bottom)
                .contains(localX, localY)) {
            return new Hit(HitKind.WINDOW_FULLSCREEN, null);
        }
        if (this.windowCloseX >= 0 && endControlBox(this.windowCloseX,
                CLOSE_WIDTH, LostTalesUiSheet.CLOSE.getHeight(), bottom)
                .contains(localX, localY)) {
            return new Hit(HitKind.WINDOW_CLOSE, null);
        }
        if (localX >= this.controlsRight && localX < row.right) {
            // The stretch past the controls drags the window, the whole
            // band of it; only the grip's own glyph lights. A locked
            // window's grip is inert: no hover, no tip, no drag.
            return row.locked ? null : new Hit(HitKind.GRIP, null);
        }
        return null;
    }

    /**
     * Whether the point lies on the grip's own glyph — with the end
     * controls' clearing round it — rather than anywhere in the empty
     * stretch that also drags the window. The glyph is what the hover
     * highlight and the move tip answer to, so neither follows a pointer
     * resting on the bare strip.
     */
    boolean isOverGripHandle(FontRenderer font, Row row, double mouseX,
                             double mouseY) {
        if (row == null || row.locked || !inRowBand(row, mouseY)) {
            return false;
        }
        layout(font, row);
        if (row.right - this.controlsRight < MIN_GRIP_WIDTH) {
            return false;
        }
        double localX = mouseX - row.offsetX - row.fractionX;
        double localY = mouseY - row.fractionY;
        return gripGlyphBox(row.right, row.rowBottom)
                .grown(END_CONTROL_SLACK).contains(localX, localY);
    }

    /**
     * Whether a point lies on the strip as drawn: the row's band and the
     * tool strip under it, from the strip's inset left edge to where the
     * window's edge really stands, the stretch its surface covers and
     * its region claims. The tool strip is the strip's: it moves the
     * window as the bare row does.
     */
    boolean stripContains(FontRenderer font, Row row, double mouseX,
                          double mouseY) {
        if (row == null || !inStripBand(row, mouseY)) {
            return false;
        }
        layout(font, row);
        double localX = mouseX - row.offsetX - row.fractionX;
        return localX >= row.left - STRIP_INSET
                && localX < this.endEdge + this.endFraction + STRIP_INSET;
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
              double mouseX, double mouseY, float alphaScale) {
        if (!Double.isNaN(this.heldWidth) && !pointerOnRow(row, mouseX,
                mouseY)) {
            // The pointer has left the row a tab was closed from: the
            // tabs take the width the row gives them now.
            this.heldWidth = Double.NaN;
            this.cachedFont = null;
        }
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
        advanceRow(tabs, row);
        List<Tab> drawnTabs = withLeaving(tabs);
        this.alphaScale = Math.max(0.0F, Math.min(1.0F, alphaScale));
        // Hit testing answers for the places the tabs will settle in,
        // not the places they are drawn while one of them is under the
        // hand. Asking it during a drag lights whichever tab's resting
        // slot the pointer happens to be crossing — a cog or a cross on
        // a tab the hand is only passing over — so the row simply
        // answers nothing until the tab is put down. Nor while the
        // window's edge is under the hand: the row's band and the top
        // resize border meet at the window's top edge, and a resize
        // glides the band under a pointer that stands still there, so
        // asking would light and unlight a tab every other frame.
        Hit hovered = row.dragging != null || row.resizing ? null
                : hitAt(font, row, mouseX, mouseY);
        int bottom = row.rowBottom;
        // The window's title strip, ending exactly on the rule row:
        // drawn outside the clip below, so the inward-rounded cut can
        // never open a bright seam — anything a tab gives up to the cut
        // shows this same surface.
        // The strip ends where the window's edge really stands, fractions
        // included, as the end controls hanging from it do.
        float stripRight = row.offsetX + this.endEdge + this.endFraction
                + STRIP_INSET;
        // Every tab's footprint is left out of it: a tab wears its own
        // surface in a single layer, never over this one.
        // The search button stands in a hole of its own, in the box it
        // answers on.
        LostTalesUiHitBox search = searchBox(row.left, bottom);
        int searchLeft = row.offsetX + (int)search.left;
        int searchTop = (int)search.top;
        this.drawnStripArgb = LostTalesChatVisualStyle.surfaceArgb(
                surfaceShare());
        drawStripAround(row, drawnTabs, row.offsetX + row.left - STRIP_INSET,
                rowTop(bottom), stripRight, bottom - 1, searchLeft,
                searchTop, searchLeft + SEARCH_SIZE,
                searchTop + SEARCH_SIZE, this.drawnStripArgb);
        // Everything else the strip draws stops short of the rule it
        // ends on: a sprite's shadow falls a pixel down and right, and
        // the rule is a hairline the window is bounded by, not something
        // to cast onto. Inward, so not one display pixel of a tab or a
        // shadow ever lands on the rule row; the rule itself is drawn
        // after the clip.
        this.rowClipBottom = row.rowBottomExact - 1.0D;
        this.clipFractionX = row.fractionX;
        Tab selectedTab = null;
        boolean masked = false;
        boolean clipped = LostTalesChatOverlayRenderer.beginVerticalClip(
                Minecraft.getMinecraft(), Double.NaN,
                this.rowClipBottom, true);
        try {
            // The shade the strip's contents sink into above its rule,
            // first of everything: the tabs, the controls and the search
            // button all stand on it. In the backdrop's colour, even
            // across the strip's whole width, and with every tab's
            // footprint left out — a resting tab wears a shade of its
            // own colour, the selected one none.
            drawStripShade(row, drawnTabs,
                    row.offsetX + row.left - STRIP_INSET, stripRight, bottom);
            // A window being dragged holds the grip's highlight wherever the
            // pointer has gone, the way a control keeps its pressed look.
            this.gripFade = LostTalesChatVisualStyle.hoverFade(
                    this.gripFade, row.moving
                            || isOverGripHandle(font, row, mouseX, mouseY),
                    this.frameElapsed);
            // The grip and the window's controls hang from the edge as
            // it really stands: laid out from its whole pixels, drawn
            // inside a matrix moved by the fraction past them.
            GL11.glPushMatrix();
            GL11.glTranslatef(this.endFraction, 0.0F, 0.0F);
            try {
                drawGrip(row.offsetX + this.controlsRight,
                        row.offsetX + this.endEdge, bottom, this.gripFade);
            } finally {
                GL11.glPopMatrix();
            }
            // The run under the hand hides whatever it passes over: its
            // footprint is laid down first, and everything else in the
            // row is drawn only where it does not stand, so no tab ever
            // shows through another.
            masked = beginCarriedMask(row, tabs, bottom, stripRight);
            // A tab closed out of the row shrinks away behind the rest.
            for (int index = 0; index < this.leavingTabs.size(); index++) {
                drawTab(font, this.leavingTabs.get(index), row, bottom, null,
                        false);
            }
            // The tab in front is drawn after the rule, outside this
            // clip, so it stands over its neighbours, over the shade they
            // sink into and over the rule itself; the carried run is drawn
            // after everything, since it slides across the others.
            for (int index = 0; index < tabs.size(); index++) {
                Tab tab = tabs.get(index);
                if (tab.tab.equals(row.selected)) {
                    selectedTab = tab;
                    continue;
                }
                if (isCarried(row, tab.tab)) {
                    continue;
                }
                drawTab(font, tab, row, bottom, hovered, false);
            }
            // The end controls sit centred in the strip, like the selected
            // tab's label; the badge's caps share that centre.
            if (this.lockX >= 0) {
                GL11.glPushMatrix();
                GL11.glTranslatef(this.endFraction, 0.0F, 0.0F);
                try {
                    drawLock(row.offsetX + this.lockX, bottom, row.locked,
                            hovered != null && hovered.kind == HitKind.LOCK,
                            step(this.lockMotion, hovered, HitKind.LOCK));
                } finally {
                    GL11.glPopMatrix();
                }
            }
            GL11.glPushMatrix();
            GL11.glTranslatef(this.restoreRunFraction, 0.0F, 0.0F);
            if (this.tabDividerX >= 0) {
                drawDivider(row.offsetX + this.tabDividerX, bottom);
            }
            if (this.restoreX >= 0) {
                // The control says which way it goes: a + while the
                // list it opens is away, and the same crossbar without
                // its upright — a minus — while the list is out.
                drawEndControl(
                        row.restoreOpen ? LostTalesUiSheet.MINUS
                                : LostTalesUiSheet.PLUS,
                        row.restoreOpen ? LostTalesUiSheet.MINUS_HOVER
                                : LostTalesUiSheet.PLUS_HOVER,
                        step(this.restoreMotion, hovered, HitKind.RESTORE),
                        row.offsetX + this.restoreX, bottom);
                if (!this.restoreMark.isNone()) {
                    // What waits behind the +, marked as a tab's icon
                    // marks it: the ping tile, else the white sphere.
                    this.restoreMark.drawAt(row.offsetX + this.restoreX
                                    + PLUS_WIDTH + COUNTER_GAP,
                            centredInStrip(bottom, this.restoreMark.height()),
                            scaled(0xFF));
                }
            }
            GL11.glPopMatrix();
            // The tab search sits at the row's left end, before the
            // first tab, where a browser keeps it.
            step(this.searchMotion, hovered, HitKind.SEARCH, row.searchOpen);
            drawSearch(searchLeft, searchTop, row.searchOpen,
                    hovered != null && hovered.kind == HitKind.SEARCH);
            // The window's own controls, in the order a title bar
            // reads: the lock (drawn above), a hairline, its settings,
            // fullscreen and close, another hairline, then the grip —
            // all hanging from the edge as it really stands, like the
            // grip.
            GL11.glPushMatrix();
            GL11.glTranslatef(this.endFraction, 0.0F, 0.0F);
            try {
                if (this.firstDividerX >= 0) {
                    drawDivider(row.offsetX + this.firstDividerX, bottom);
                }
                if (this.windowSettingsX >= 0) {
                    drawEndControl(LostTalesUiSheet.COG, LostTalesUiSheet.COG_HOVER,
                            step(this.windowSettingsMotion, hovered,
                                    HitKind.WINDOW_SETTINGS),
                            row.offsetX + this.windowSettingsX, bottom);
                }
                if (this.windowFullscreenX >= 0) {
                    drawFullscreenControl(row.fullscreenShare,
                            step(this.windowFullscreenMotion, hovered,
                                    HitKind.WINDOW_FULLSCREEN),
                            row.offsetX + this.windowFullscreenX, bottom);
                }
                if (this.windowCloseX >= 0) {
                    drawEndControl(LostTalesUiSheet.CLOSE,
                            LostTalesUiSheet.CLOSE_HOVER,
                            step(this.windowCloseMotion, hovered,
                                    HitKind.WINDOW_CLOSE),
                            row.offsetX + this.windowCloseX, bottom);
                }
                if (this.secondDividerX >= 0) {
                    drawDivider(row.offsetX + this.secondDividerX, bottom);
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            LostTalesChatOverlayRenderer.endVerticalClip(clipped);
        }
        // The window's top rule: the strip's last row, over the tabs
        // and controls, the exact width of the strip. Behind it the row
        // wears the tool strip's surface, the colour it touches (Nils),
        // so the tool strip runs up under the rule. Where the selected
        // tab stands, neither is drawn: the tab's own surface takes that
        // stretch of the row, joining the tool strip under it, and the
        // rule's two pieces are full where they meet the feet of the
        // tab's border pieces and fade out toward the strip's ends, so
        // the rule reads as hanging from the tab.
        float selectedLeft = 0.0F;
        float selectedRight = 0.0F;
        if (selectedTab != null) {
            // The hole reaches to the ends of the selected pieces' feet:
            // the rule begins right where they do.
            selectedLeft = row.offsetX + drawnX(row, selectedTab)
                    - SELECTED_FOOT;
            selectedRight = selectedLeft + drawnWidth(row, selectedTab)
                    + 2 * SELECTED_FOOT;
        }
        float ruleLeft = row.offsetX + row.left - STRIP_INSET;
        int ruleRowArgb = toolSurfaceArgb();
        if (selectedRight <= selectedLeft || selectedRight <= ruleLeft
                || selectedLeft >= stripRight) {
            LostTalesUiInk.fillRect(ruleLeft, bottom - 1,
                    stripRight, bottom, ruleRowArgb);
        } else {
            LostTalesUiInk.fillRect(ruleLeft, bottom - 1,
                    Math.max(ruleLeft, selectedLeft), bottom, ruleRowArgb);
            LostTalesUiInk.fillRect(
                    Math.min(stripRight, selectedRight), bottom - 1,
                    stripRight, bottom, ruleRowArgb);
        }
        LostTalesChatOverlayRenderer.drawRuleAround(
                row.offsetX + row.left - STRIP_INSET, stripRight,
                bottom - 1, bottom, scaled(0xFF), selectedLeft,
                selectedRight);
        // The selected tab: over the other tabs, over the shade they sink
        // into and on the rule's row, its border pieces ending there so it
        // joins the tool strip under it. Its name is cut on the rule row
        // rather than above it.
        if (selectedTab != null && !isCarried(row, selectedTab.tab)) {
            if (masked) {
                // The clip just ended took the mask's test with it.
                resumeCarriedMask();
            }
            this.rowClipBottom = row.rowBottomExact;
            drawTab(font, selectedTab, row, bottom, hovered, true);
        }
        if (masked) {
            endCarriedMask(row, bottom, stripRight);
        }
        // The carried run, last of all and in row order, so it slides
        // across everything it passes as one piece.
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (!isCarried(row, tab.tab)) {
                continue;
            }
            boolean inFront = tab.tab.equals(row.selected);
            this.rowClipBottom = inFront ? row.rowBottomExact
                    : row.rowBottomExact - 1.0D;
            boolean cut = !inFront
                    && LostTalesChatOverlayRenderer.beginVerticalClip(
                            Minecraft.getMinecraft(), Double.NaN,
                            this.rowClipBottom, true);
            try {
                drawTab(font, tab, row, bottom, hovered, inFront);
            } finally {
                LostTalesChatOverlayRenderer.endVerticalClip(cut);
            }
        }
        // The tool strip under the strip's rule: a band of exactly the
        // selected tab's surface, the window's room for the controls
        // that read its history rather than pick a tab, empty for now,
        // and a handle on the window like the strip above it.
        // Its last row is a rule of its own — the window's top rule,
        // which the history's clip and its top shade hang from — on the
        // tool strip's own surface, which runs under it, as it runs under
        // the strip's rule (Nils).
        float stripLeft = row.offsetX + row.left - STRIP_INSET;
        int toolBottom = bottom + ChatWindowPlacement.TOOL_STRIP_HEIGHT;
        int toolArgb = toolSurfaceArgb();
        this.drawnToolArgb = toolArgb;
        LostTalesUiHitBox hole = this.toolStripHole;
        if (hole == null) {
            LostTalesUiInk.fillRect(stripLeft, bottom, stripRight,
                    toolBottom, toolArgb);
        } else {
            // The search bar's well is a hole in the strip: the surface
            // round it in four pieces, nothing under the well itself.
            float holeLeft = (float) hole.left;
            float holeTop = (float) hole.top;
            float holeRight = (float) (hole.left + hole.width);
            float holeBottom = (float) (hole.top + hole.height);
            LostTalesUiInk.fillRect(stripLeft, bottom, stripRight,
                    holeTop, toolArgb);
            LostTalesUiInk.fillRect(stripLeft, holeTop, holeLeft,
                    holeBottom, toolArgb);
            LostTalesUiInk.fillRect(holeRight, holeTop, stripRight,
                    holeBottom, toolArgb);
            LostTalesUiInk.fillRect(stripLeft, holeBottom, stripRight,
                    toolBottom, toolArgb);
        }
        LostTalesChatOverlayRenderer.drawRule(stripLeft, stripRight,
                toolBottom - 1, toolBottom, scaled(0xFF));
        regions.addWindow(row.offsetX + row.left - STRIP_INSET,
                rowTop(bottom), (int)Math.ceil(stripRight), toolBottom);
    }

    /**
     * The tool strip's surface as it stands this frame: the selected
     * tab's plum grey at two thirds, glowing with a tab just put down as
     * the tab does, so the tab in front and the strip light as one; the
     * rows the strip's two rules stand on wear it too.
     */
    private int toolSurfaceArgb() {
        return LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.blend(
                        LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                        this.toolGlowRgb, this.toolGlow * GLOW_TINT),
                scaled(TAB_SURFACE_ALPHA));
    }

    /** How far a tab's top stands risen off the row this frame, on the display's grid. */
    private static float raisedBy(Tab tab) {
        return (float)snapped(tab.lift * LIFT_PIXELS, displayStep());
    }

    /**
     * How much of its opacity the strip's surface shows: the window's
     * own motion and the game's chat opacity, as the panel under the
     * rule shows them.
     */
    private float surfaceShare() {
        return this.alphaScale
                * LostTalesChatVisualStyle.chatOpacity(Minecraft.getMinecraft());
    }

    /**
     * Where a run of {@code height} rows sits in a tab's interior,
     * centred on the icon that fills it. The icon is an even number of
     * rows and the caps and the control glyphs are odd, so neither can
     * land on its centre: each stands half a pixel up, its shadow counted
     * as part of its shape ({@link LostTalesUiInk#centredStart}).
     */
    private static int centredInInterior(int interiorTop, int height) {
        return interiorTop + LostTalesUiInk.centredStart(INTERIOR_HEIGHT,
                height);
    }

    private int scaled(int alpha) {
        int result = Math.round(alpha * this.alphaScale);
        return result < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                ? 0 : result;
    }

    /**
     * The strip's surface over {@code [left, right)} by {@code [top,
     * bottom)} with every tab's footprint left out, so each tab wears its
     * own surface in a single layer and the two never lie one over the
     * other. A tab's footprint is its body from the row under its top
     * down to the strip's end, and its top row a pixel in from each side:
     * the chamfer its border artwork cuts, where the strip shows. A tab
     * risen off the row reaches up as far as it has risen. Read
     * column by column, so wherever tabs overlap — a carried one over
     * its neighbours — the highest of them decides where the strip stops.
     * The search button's footprint, a box standing in the strip, is left
     * out too, all but its four corner pixels, which the frame's rounding
     * leaves to the strip.
     */
    private void drawStripAround(Row row, List<Tab> tabs, float left,
                                 float top, float right, float bottom,
                                 float holeLeft, float holeTop,
                                 float holeRight, float holeBottom,
                                 int argb) {
        int count = tabs.size();
        float[] lefts = new float[count];
        float[] rights = new float[count];
        float[] tops = new float[count];
        float[] edges = new float[count * 4 + 6];
        int edgeCount = 0;
        edges[edgeCount++] = left;
        edges[edgeCount++] = right;
        for (int index = 0; index < count; index++) {
            Tab tab = tabs.get(index);
            lefts[index] = row.offsetX + drawnX(row, tab);
            rights[index] = lefts[index] + drawnWidth(row, tab);
            tops[index] = tabTop(row.rowBottom,
                    tab.tab.equals(row.selected)) - raisedBy(tab);
            float[] tabEdges = {lefts[index], lefts[index] + 1.0F,
                    rights[index] - 1.0F, rights[index]};
            for (float edge : tabEdges) {
                if (edge > left && edge < right) {
                    edges[edgeCount++] = edge;
                }
            }
        }
        float[] holeEdges = {holeLeft, holeLeft + 1.0F, holeRight - 1.0F,
                holeRight};
        for (float edge : holeEdges) {
            if (edge > left && edge < right) {
                edges[edgeCount++] = edge;
            }
        }
        java.util.Arrays.sort(edges, 0, edgeCount);
        for (int index = 0; index + 1 < edgeCount; index++) {
            float from = edges[index];
            float to = edges[index + 1];
            if (to <= from) {
                continue;
            }
            float middle = (from + to) / 2.0F;
            float cover = bottom;
            for (int tabIndex = 0; tabIndex < count; tabIndex++) {
                if (middle < lefts[tabIndex] || middle >= rights[tabIndex]) {
                    continue;
                }
                boolean chamfer = middle < lefts[tabIndex] + 1.0F
                        || middle >= rights[tabIndex] - 1.0F;
                cover = Math.min(cover, tops[tabIndex] + (chamfer ? 1 : 0));
            }
            if (middle >= holeLeft && middle < holeRight) {
                // Above and below the button; its corner pixels stay the
                // strip's.
                boolean corner = middle < holeLeft + 1.0F
                        || middle >= holeRight - 1.0F;
                float cutTop = holeTop + (corner ? 1.0F : 0.0F);
                float cutBottom = holeBottom - (corner ? 1.0F : 0.0F);
                if (Math.min(cover, cutTop) > top) {
                    LostTalesUiInk.fillRect(from, top, to,
                            Math.min(cover, cutTop), argb);
                }
                if (cover > Math.max(top, cutBottom)) {
                    LostTalesUiInk.fillRect(from,
                            Math.max(top, cutBottom), to, cover, argb);
                }
                continue;
            }
            if (cover > top) {
                LostTalesUiInk.fillRect(from, top, to, cover,
                        argb);
            }
        }
    }

    private void drawTab(FontRenderer font, Tab tab, Row row,
                         int rowBottom, Hit hovered, boolean selected) {
        float left = row.offsetX + drawnX(row, tab);
        float width = drawnWidth(row, tab);
        if (width <= 0.0F) {
            // A tab opening from nothing, or closed down to it.
            return;
        }
        float right = left + width;
        boolean marked = row.marked.contains(tab.tab);
        boolean carried = isCarried(row, tab.tab);
        // A marked tab wears the lit shape outright; a hovered one, and
        // one the hand has picked up, crosses to it and back rather than
        // swapping in a frame.
        tab.hoverFade = LostTalesChatVisualStyle.hoverFade(tab.hoverFade,
                carried || hovered != null && tab.tab.equals(hovered.tab),
                this.frameElapsed);
        float lit = marked ? 1.0F : tab.hoverFade;
        // Selection is a hard cut: the picked tab is forward at once,
        // nothing sweeps across the tabs between.
        int top = rowBottom - HEIGHT - (selected ? LIFT : 0);
        int channelRgb = ClientChatChannelState.displayColor(tab.tab);
        // Picked up, the tab's top rises off the row a pixel — laid on
        // the display's grid, so its artwork keeps its texels — while its
        // feet stay where they stood, and it settles back once it is put
        // down.
        float raised = raisedBy(tab);
        // One surface painted under border ink and span alike; the
        // artwork's own backdrop texels are cut away inside. A tab just
        // put down glows once in its channel's colour.
        drawTabShape(left, right, top, raised, selected, lit, tab.lift,
                channelRgb, tab.glow * GLOW_TINT, scaled(0xFF),
                scaled(TAB_SURFACE_ALPHA));
        // A resting tab's own shade lies on its surface and under
        // everything the tab holds: the accent line, the icon, the name,
        // the counters and the controls all stand over it. It fades out
        // as the tab lights, on the crossfade its name takes the tab's
        // colour on, so a lit tab wears none, as the selected one.
        if (!selected && lit < 1.0F) {
            drawTabShade(tab, left, right, rowBottom, 1.0F - lit);
        }
        // Everything the tab holds rises with its top.
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, -raised, 0.0F);
        try {
            // Channel accent across the tab's face, clear of the border
            // artwork on every side: full at the centre and gone at the
            // ends like the edge rules. The accent is the channel's own
            // and says nothing about the pointer, so being forward,
            // hovered or marked never changes it.
            drawAccent(left + BORDER_WIDTH, right - BORDER_WIDTH,
                    top + ACCENT_ROW, channelRgb, scaled(0xFF));
            // A tab opening or closing lays its contents out at its full
            // width and lets its moving edge cut them, fading with it, so
            // nothing slides across anything as it grows or shrinks.
            double laidWidth = tab.laidWidth(width);
            float contentShare = laidWidth > width
                    ? (float)Math.max(0.0D, Math.min(1.0D, width / laidWidth))
                    : 1.0F;
            // Text is always at full opacity; a muted tab is told by its
            // italics alone. The name takes the tab's own colour as the
            // tab lights, on the same crossfade as its shape, and keeps
            // it while the tab is in front.
            int textAlpha = Math.round(scaled(0xFF) * contentShare);
            int labelRgb = LostTalesChatVisualStyle.blend(
                    LostTalesChatVisualStyle.IVORY, channelRgb,
                    selected ? 1.0F : lit);
            // Which buttons stand, and how much of the name shows beside
            // them, is read off the width the tab is laid out at and the
            // row's fades: every tab gives a button up together. A button
            // going gives up its ink first and its room after, and one
            // coming takes its room first and shows after, so the name
            // never runs under a button.
            float closeFade = closeShare(row, selected);
            float cogFade = this.cogShown.clamped();
            float draftFade = tab.draft ? this.draftShown.clamped() : 0.0F;
            TabRoom room = roomFor(laidWidth, iconWidth(tab.tab),
                    tab.labelWidth, drawnDraftWidth(tab, roomPhase(draftFade)),
                    roomPhase(closeFade), roomPhase(cogFade));
            // A name the row has cut short is read whole by resting the
            // pointer on it: the marquee runs on the clock while the tab
            // is hovered and glides home once it is not, so a pointer
            // sweeping the row starts nothing and a name that fits never
            // moves.
            int overflow = tab.labelWidth - (int)Math.floor(room.labelRoom);
            boolean labelHovered = hovered != null
                    && hovered.kind == HitKind.TAB
                    && tab.tab.equals(hovered.tab);
            if (labelHovered && overflow > 0 && Motions.enabled()) {
                tab.hoverSeconds += this.frameElapsed;
                tab.marqueeOffset = (float)marqueeOffset(tab.hoverSeconds,
                        overflow);
            } else {
                tab.hoverSeconds = 0.0D;
                tab.marqueeOffset = eased(tab.marqueeOffset, 0.0F,
                        this.frameElapsed);
            }
            // Everything inside the tab is cut off at the tab's own edge
            // as it narrows, the way a browser cuts a tab's label. The
            // row's own lower cut goes with it, since a scissor replaces
            // the one before it rather than narrowing it.
            boolean clipped = LostTalesChatOverlayRenderer.beginClip(
                    Minecraft.getMinecraft(), clipX(left),
                    clipX(right - BORDER_WIDTH), Double.NaN,
                    this.rowClipBottom, true);
            try {
                drawTabContents(font, tab, hovered, left, right,
                        top + INTERIOR_TOP, room, inkPhase(draftFade),
                        labelRgb, textAlpha);
                drawTabControls(tab, hovered, room, inkPhase(closeFade),
                        inkPhase(cogFade), left, top + INTERIOR_TOP,
                        textAlpha);
            } finally {
                LostTalesChatOverlayRenderer.endVerticalClip(clipped);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * What stands inside a tab before its buttons, one clear row under
     * the accent: the icon at its own size, wearing its unread mark in
     * its corner, the name's caps and the draft mark level with it. The
     * selected tab's lift carries all of it up rather than re-centring
     * it. All of it is cut where the buttons begin, sinking into that
     * edge: a tab too narrow for its name gives the name up first, and
     * the tab in front then its icon, so its cross ends where the icon
     * stood.
     */
    private void drawTabContents(FontRenderer font, Tab tab, Hit hovered,
                                 float left, float right, int interiorTop,
                                 TabRoom room, float draftShare,
                                 int labelRgb, int textAlpha) {
        // The words are drawn at whole coordinates inside a matrix moved
        // by whatever fraction of a pixel the tab stands on, since the
        // font draws at whole ones: the glyphs then land on the same
        // display pixels the tab's own artwork does.
        int wholeLeft = (int)Math.floor(left);
        float fraction = left - wholeLeft;
        int textX = wholeLeft + PADDING_X;
        int textY = centredInInterior(interiorTop, CAP_HEIGHT);
        double contentRight = Math.min(right - BORDER_WIDTH,
                left + room.contentRight);
        if (tab.icon != null) {
            drawTabIcon(tab, textX, fraction, interiorTop, textAlpha, left,
                    contentRight);
            textX += ChatChannelIcons.SLOT + ChatChannelIcons.GAP;
        }
        // The name's room as the tab is drawn, fractions included, so
        // where the name is cut and where the draft mark after it stands
        // move with the tab's edge by the same fraction the edge moves.
        drawTabLabel(font, tab, textX, fraction, textY, labelRgb, textAlpha,
                left, right, room.labelRoom);
        drawTabDraft(tab, hovered, textX + fraction + room.labelRoom, textY,
                textAlpha, draftShare, left, contentRight);
    }

    /**
     * The tab's icon at its own size, wearing its unread mark, cut where
     * the buttons begin once the tab is too narrow to hold it, sinking
     * into that edge as a cut name does.
     */
    private void drawTabIcon(final Tab tab, final int x, final float fraction,
                             int interiorTop, final int alpha, float tabLeft,
                             double contentRight) {
        double iconLeft = x + fraction;
        double iconRight = iconLeft + ChatChannelIcons.SLOT;
        final ChatIconMark mark = ChatIconMark.of(tab.tab);
        if (iconLeft >= contentRight) {
            return;
        }
        final int y = centredInInterior(interiorTop, ChatChannelIcons.SIZE);
        // A cut icon dims as it sinks into the edge, so its last pixels
        // never stand as a sliver beside the cross.
        final float visible = (float)Math.max(0.0D, Math.min(1.0D,
                (contentRight - iconLeft) / ChatChannelIcons.SLOT));
        LostTalesChatOverlayRenderer.FadingPainter painter =
                new LostTalesChatOverlayRenderer.FadingPainter() {
                    @Override
                    public void paint(float share) {
                        int shown = Math.round(alpha * visible * share);
                        if (shown < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
                            return;
                        }
                        GL11.glPushMatrix();
                        GL11.glTranslatef(fraction, 0.0F, 0.0F);
                        try {
                            ChatChannelIcons.draw(Minecraft.getMinecraft(),
                                    tab.tab, x, y, shown, mark);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                };
        if (iconRight <= contentRight) {
            painter.paint(1.0F);
            return;
        }
        double clipLeft = Math.max(tabLeft, iconLeft);
        float depth = LostTalesChatOverlayRenderer.sideFadeDepth(
                contentRight - clipLeft);
        LostTalesChatOverlayRenderer.drawFading(Minecraft.getMinecraft(),
                clipX(clipLeft), clipX(contentRight), Double.NaN,
                this.rowClipBottom, depth, 0.0F,
                LostTalesChatOverlayRenderer.sideFadeStrength(
                        iconRight - contentRight, depth), painter);
    }

    /**
     * The name, whole, in the room its drawn width leaves it. A name
     * wider than its room is cut at the room's end — inside the tab's
     * own cut, both given to the scissor at once since one replaces the
     * other — and slid left by the marquee while hovered, its offset
     * laid on a display pixel so the glyphs stay on theirs. A cut name
     * thins out into the edges it is cut at, as far as it runs past
     * each, the words themselves fading, so the cut is seamless on the
     * tab's surface.
     */
    private void drawTabLabel(FontRenderer font, Tab tab, int textX,
                              float fraction, int textY, int labelRgb,
                              int textAlpha, float tabLeft, float tabRight,
                              double labelRoom) {
        String text = tab.muted ? "§o" + tab.label : tab.label;
        if (tab.labelWidth <= labelRoom) {
            drawWords(font, text, textX, fraction, textY, labelRgb,
                    textAlpha);
            return;
        }
        double roomLeft = textX + fraction;
        double clipLeft = Math.max(tabLeft, roomLeft);
        double clipRight = Math.min(tabRight - BORDER_WIDTH,
                roomLeft + labelRoom);
        if (clipRight <= clipLeft) {
            return;
        }
        // A cut name thins out into the edge it is cut at, as far as
        // it has gone past it: the words themselves fade, so the cut is
        // seamless on the tab's surface.
        double offset = snapped(tab.marqueeOffset, displayStep());
        float depth = LostTalesChatOverlayRenderer.sideFadeDepth(
                clipRight - clipLeft);
        double wordsLeft = roomLeft - offset;
        LostTalesChatOverlayRenderer.drawFadingText(Minecraft.getMinecraft(),
                font, text, textX, (float)(fraction - offset), textY,
                labelRgb, textAlpha, clipX(clipLeft),
                clipX(clipRight), this.rowClipBottom, depth,
                LostTalesChatOverlayRenderer.sideFadeStrength(
                        clipLeft - wordsLeft, depth),
                LostTalesChatOverlayRenderer.sideFadeStrength(
                        wordsLeft + tab.labelWidth - clipRight, depth));
    }

    /**
     * The shade the strip's contents sink into above its rule, in the
     * backdrop's colour and even across the strip's whole width — the
     * history's own shade thins to the right; this one does not, so the
     * controls' end shows it as the tabs' end does — drawn in the
     * stretches between and beyond the tabs, every tab's footprint as it
     * is drawn this frame left out: a resting tab wears a shade of its
     * own colour over itself ({@link #drawTabShade}), the selected tab
     * none. It is the history's shade hung the other way up, by the
     * same routine.
     */
    private void drawStripShade(Row row, List<Tab> tabs, float left,
                                float right, int bottom) {
        int alpha = scaled(LostTalesChatOverlayRenderer.EDGE_FADE_ALPHA);
        List<Tab> ordered = new ArrayList<Tab>(tabs);
        final Row drawnIn = row;
        Collections.sort(ordered, new Comparator<Tab>() {
            @Override
            public int compare(Tab a, Tab b) {
                return Float.compare(drawnX(drawnIn, a), drawnX(drawnIn, b));
            }
        });
        float cursor = left;
        for (Tab tab : ordered) {
            float tabLeft = row.offsetX + drawnX(row, tab);
            float tabRight = Math.min(right, tabLeft + drawnWidth(row, tab));
            if (tabRight <= cursor) {
                continue;
            }
            if (tabLeft > cursor) {
                LostTalesChatOverlayRenderer.drawEdgeFade(left, right, cursor,
                        tabLeft, bottom - 1, rowTop(bottom),
                        LostTalesChatOverlayRenderer.TOP_EDGE_FADE_HEIGHT,
                        alpha, LostTalesChatVisualStyle.backdropRgb(), false);
            }
            cursor = tabRight;
        }
        if (cursor < right) {
            LostTalesChatOverlayRenderer.drawEdgeFade(left, right, cursor,
                    right, bottom - 1, rowTop(bottom),
                    LostTalesChatOverlayRenderer.TOP_EDGE_FADE_HEIGHT, alpha,
                    LostTalesChatVisualStyle.backdropRgb(), false);
        }
    }

    /**
     * A resting tab's own shade, over the tab: the same fade up from
     * the rule in the tab's channel accent, a bell across the tab's
     * width — strongest under its middle, gone at its sides — so the
     * tab sinks into the rule in its own colour; at {@code share} of its
     * strength, as far as the pointer has left the tab unlit.
     */
    private void drawTabShade(Tab tab, float left, float right,
                              int rowBottom, float share) {
        LostTalesChatOverlayRenderer.drawBellFade(left, right, rowBottom - 1,
                rowTop(rowBottom),
                LostTalesChatOverlayRenderer.TOP_EDGE_FADE_HEIGHT,
                scaled(Math.round(LostTalesChatOverlayRenderer.EDGE_FADE_ALPHA
                        * share)),
                ClientChatChannelState.displayColor(tab.tab));
    }

    /**
     * The tone of a tab's one surface: the selected tab's lit plum grey,
     * and a resting one's plum black crossed toward it as far as the
     * pointer has lit it. A cut name's side fades are drawn in the same
     * tone, so the name sinks into its own tab.
     */
    private static int tabSurfaceRgb(boolean selected, float lit) {
        return selected ? LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB
                : LostTalesChatVisualStyle.blend(
                        LostTalesChatVisualStyle.SURFACE_RGB,
                        LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB, lit);
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
                                  float fraction, int y, int rgb, int alpha) {
        GL11.glPushMatrix();
        GL11.glTranslatef(fraction, 0.0F, 0.0F);
        try {
            LostTalesChatVisualStyle.drawColored(font, text, x, y, rgb, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * The draft mark after the name's room, from the exact x the room
     * ends at: laid on a display pixel and drawn at whole coordinates
     * inside a matrix moved by the rest, like the name. It is a button
     * and moves as one.
     */
    private void drawTabDraft(Tab tab, Hit hovered, double exactX, int textY,
                              int textAlpha, float draftShare, float tabLeft,
                              double contentRight) {
        int draftAlpha = Math.round(textAlpha * draftShare);
        if (draftAlpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        double placed = snapped(exactX, displayStep());
        int textX = (int)Math.floor(placed) + COUNTER_GAP;
        float fraction = (float)(placed - Math.floor(placed));
        // Cut where the buttons begin, as everything before them is: the
        // mark going away slides under that edge as the name takes its
        // room.
        boolean clipped = LostTalesChatOverlayRenderer.beginClip(
                Minecraft.getMinecraft(), clipX(tabLeft), clipX(contentRight),
                Double.NaN, this.rowClipBottom, true);
        GL11.glPushMatrix();
        GL11.glTranslatef(fraction, 0.0F, 0.0F);
        try {
            // The mark's five rows on the capitals' seven, a row below
            // their top.
            LostTalesUiButton.drawGlyph(LostTalesUiSheet.DRAFT,
                    LostTalesUiSheet.DRAFT_HOVER,
                    step(tab.draftMotion, hovered, tab, HitKind.DRAFT),
                    textX, textY + 1, draftAlpha);
        } finally {
            GL11.glPopMatrix();
            LostTalesChatOverlayRenderer.endVerticalClip(clipped);
        }
    }

    /**
     * The tab's own cog and cross, as far as the row shows them: each
     * fades where it stands as the row's width crosses its share. Their
     * hit squares are centred in the interior like the caps, and each
     * sprite is centred in its square in turn. They hang from the tab's
     * drawn right end, laid on display pixels, so a tab still gliding to
     * another width carries them with its edge by exactly the fraction
     * it moves.
     */
    private void drawTabControls(Tab tab, Hit hovered, TabRoom room,
                                 float closeShare, float cogShare, float left,
                                 int interiorTop, int controlAlpha) {
        int controlTop = centredInInterior(interiorTop, CONTROL_SIZE);
        double step = displayStep();
        int closeAlpha = Math.round(controlAlpha * closeShare);
        if (closeAlpha >= LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            drawTabControl(LostTalesUiSheet.CLOSE,
                    LostTalesUiSheet.CLOSE_HOVER,
                    step(tab.closeMotion, hovered, tab, HitKind.CLOSE),
                    (float)snappedLeft(left + room.closeLeft, step),
                    controlTop, closeAlpha);
        }
        int cogAlpha = Math.round(controlAlpha * cogShare);
        if (cogAlpha >= LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            drawTabControl(LostTalesUiSheet.COG, LostTalesUiSheet.COG_HOVER,
                    step(tab.cogMotion, hovered, tab, HitKind.SETTINGS),
                    (float)snappedLeft(left + room.cogLeft, step),
                    controlTop, cogAlpha);
        }
    }

    /** How tall a tab's border pieces stand: the selected pair reaches the rule. */
    private static int pieceHeight(boolean selected) {
        return selected ? LostTalesUiSheet.TAB_SELECTED_LEFT.getHeight()
                : PIECE_HEIGHT;
    }

    /**
     * Lays the carried run's footprint into the depth buffer in front of
     * the row, the rest of the row's band laid far behind it, so what is
     * drawn next — the other tabs, the controls, the tab in front — shows
     * only where the run does not stand. The footprint is each carried
     * tab's own shape as drawn, its top risen as it rises, with its
     * chamfered top corners left open as the strip leaves them. Answers
     * whether a run is being carried at all.
     */
    private boolean beginCarriedMask(Row row, List<Tab> tabs, int rowBottom,
                                     float stripRight) {
        boolean any = false;
        for (int index = 0; index < tabs.size() && !any; index++) {
            any = isCarried(row, tabs.get(index).tab);
        }
        if (!any) {
            return false;
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        maskQuad(tessellator, row.offsetX + row.left - STRIP_INSET,
                rowTop(rowBottom) - LIFT_PIXELS, stripRight, rowBottom,
                MASK_FAR);
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (!isCarried(row, tab.tab)) {
                continue;
            }
            boolean inFront = tab.tab.equals(row.selected);
            float left = row.offsetX + drawnX(row, tab);
            float right = left + drawnWidth(row, tab);
            float top = tabTop(rowBottom, inFront) - raisedBy(tab);
            float bottom = tabTop(rowBottom, inFront) + pieceHeight(inFront);
            maskQuad(tessellator, left + 1.0F, top, right - 1.0F, top + 1.0F,
                    MASK_NEAR);
            maskQuad(tessellator, left, top + 1.0F, right, bottom, MASK_NEAR);
        }
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColorMask(true, true, true, true);
        resumeCarriedMask();
        return true;
    }

    /**
     * Tests what is drawn next against the carried run's footprint again:
     * a clip ending puts the depth test back as it found it, and the mask
     * has to hold across the rule to the tab in front.
     */
    private static void resumeCarriedMask() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    /**
     * Lays the row's band far behind the GUI again, the footprint with it,
     * and puts the depth state back as the chat screen keeps it: no test,
     * writes on.
     */
    private void endCarriedMask(Row row, int rowBottom, float stripRight) {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        maskQuad(tessellator, row.offsetX + row.left - STRIP_INSET,
                rowTop(rowBottom) - LIFT_PIXELS, stripRight, rowBottom,
                MASK_FAR);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    /** One rectangle of depth alone at {@code z}; the caller has the colour off. */
    private static void maskQuad(Tessellator tessellator, float left,
                                 float top, float right, float bottom,
                                 double z) {
        if (right <= left || bottom <= top) {
            return;
        }
        // Same winding as the rest of the GUI pass, which culls back faces.
        tessellator.addVertex(left, bottom, z);
        tessellator.addVertex(right, bottom, z);
        tessellator.addVertex(right, top, z);
        tessellator.addVertex(left, top, z);
    }

    /**
     * A tab's shape: the interior filling the span its two border pieces
     * enclose, the line joining their tips, and the pieces themselves.
     * The pieces are drawn at their own size — a longer label widens the
     * span, never the artwork — and the selected pair stands a row taller
     * than the resting ones, the row its feet stand on the rule with.
     * {@code raised} is how far the tab's top has risen off the row: the
     * shape stretches up by it along the pieces' seam row, its feet where
     * they were. The tab in front crosses to its lifted contours as far
     * as {@code lifted} says. Every tab comes through here.
     */
    private static void drawTabShape(float left, float right, int top,
                                     float raised, boolean selected,
                                     float lit, float lifted,
                                     int glowRgb, float glow,
                                     int spriteAlpha, int interiorAlpha) {
        LostTalesUiSheet leftPiece;
        LostTalesUiSheet rightPiece;
        // The lit border artwork laid over the resting pair as far as the
        // tab has crossed to it; its tones cross by the same share, so
        // the whole tab lights together rather than in two steps.
        LostTalesUiSheet leftLit = null;
        LostTalesUiSheet rightLit = null;
        // A tab just put down glows in its channel's colour: its one
        // surface crossed toward it, never a second layer over it.
        int surfaceRgb = LostTalesChatVisualStyle.blend(
                tabSurfaceRgb(selected, lit), glowRgb, glow);
        float warmed = selected
                ? Math.max(0.0F, Math.min(1.0F, lifted)) : 0.0F;
        int tipRgb;
        if (selected) {
            leftPiece = LostTalesUiSheet.TAB_SELECTED_LEFT;
            rightPiece = LostTalesUiSheet.TAB_SELECTED_RIGHT;
            tipRgb = TIP_LIT_RGB;
        } else {
            leftPiece = LostTalesUiSheet.TAB_LEFT;
            rightPiece = LostTalesUiSheet.TAB_RIGHT;
            leftLit = LostTalesUiSheet.TAB_HOVER_LEFT;
            rightLit = LostTalesUiSheet.TAB_HOVER_RIGHT;
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
        float shapeTop = top - raised;
        LostTalesUiInk.fillRect(left + 1, shapeTop, right - 1,
                shapeTop + 1,
                LostTalesChatVisualStyle.argb(surfaceRgb, interiorAlpha));
        LostTalesUiInk.fillRect(left, shapeTop + 1, right,
                top + height,
                LostTalesChatVisualStyle.argb(surfaceRgb, interiorAlpha));
        if (spanRight > spanLeft) {
            // The tips are the pieces' innermost lit columns, one pixel
            // outside the span, so the line meets both without a gap.
            LostTalesUiInk.fillRect(spanLeft,
                    shapeTop + TIP_ROW, spanRight, shapeTop + TIP_ROW + 1,
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
            // The selected pieces are their feet wider: the left one is
            // drawn its foot's width further left, so its ink line stands
            // where a resting piece's does and the foot reaches out past
            // the tab; the right one starts on the border and reaches
            // out the other way.
            float leftX = selected ? left - SELECTED_FOOT : left;
            drawRisenPiece(leftPiece, leftX, top, raised, 0, spriteAlpha);
            drawRisenPiece(rightPiece, spanRight, top, raised, 0,
                    spriteAlpha);
            int over = Math.round(spriteAlpha * lit);
            if (leftLit != null
                    && over >= LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
                GL11.glAlphaFunc(GL11.GL_GREATER,
                        TAB_INK_THRESHOLD * over / 255.0F);
                drawRisenPiece(leftLit, left, top, raised, 0, over);
                drawRisenPiece(rightLit, spanRight, top, raised, 0, over);
            }
            // The lifted pair is the selected shape a row taller, so over
            // the stretched selected pieces it covers exactly their ink.
            int warm = Math.round(spriteAlpha * warmed);
            if (warm >= LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
                GL11.glAlphaFunc(GL11.GL_GREATER,
                        TAB_INK_THRESHOLD * warm / 255.0F);
                drawRisenPiece(LostTalesUiSheet.TAB_LIFTED_LEFT, leftX, top,
                        raised, 1, warm);
                drawRisenPiece(LostTalesUiSheet.TAB_LIFTED_RIGHT, spanRight,
                        top, raised, 1, warm);
            }
        } finally {
            // The threshold vanilla's GUI runs under, as the item
            // renderer also leaves it.
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        }
    }

    /**
     * A border piece whose tab stands on {@code top} with its top risen
     * {@code raised}: the piece's rows above {@link #LIFT_SEAM_ROW} carried
     * up with the top, the rows from it on where they stood, and the
     * seam row stretched over the gap between. {@code extraRows} is how
     * many rows taller than the shape it is drawn over the piece is —
     * the lifted pair's one — left out at the seam.
     */
    private static void drawRisenPiece(LostTalesUiSheet piece, float x,
                                       int top, float raised, int extraRows,
                                       int alpha) {
        int u = piece.getTextureU();
        int v = piece.getTextureV();
        int width = piece.getWidth();
        LostTalesUiSheet.draw(u, v, width, LIFT_SEAM_ROW, x, top - raised,
                alpha);
        if (raised > 0.0F) {
            LostTalesUiSheet.drawStretched(u, v + LIFT_SEAM_ROW, width, 1, x,
                    top + LIFT_SEAM_ROW - raised, width, raised, alpha);
        }
        int below = LIFT_SEAM_ROW + extraRows;
        LostTalesUiSheet.draw(u, v + below, width, piece.getHeight() - below,
                x, top + LIFT_SEAM_ROW, alpha);
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

    /* ---- A small window's title strip: one tab ---- */

    /**
     * Clear space between a small window's frame edge and its one tab, as
     * between a chat window's frame edge and its search button.
     */
    static final int LONE_TAB_MARGIN = SEARCH_MARGIN;

    /**
     * Where the one tab of a small window's title strip stands and what
     * it shows: the name cut to the room the strip leaves it, and its
     * cross, which closes the window.
     */
    static final class LoneTab {
        final float left;
        final float right;
        final int top;
        final String label;
        final int labelWidth;
        final boolean icon;
        final LostTalesUiHitBox closeBox;

        LoneTab(float left, float right, int top, String label,
                int labelWidth, boolean icon, LostTalesUiHitBox closeBox) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.label = label;
            this.labelWidth = labelWidth;
            this.icon = icon;
            this.closeBox = closeBox;
        }

        /**
         * The bottom of the tab's surface: the selected tab's pieces reach
         * the strip's rule row. Its top row is a pixel narrower each side,
         * as every tab's is.
         */
        int bottom() {
            return this.top + LostTalesUiSheet.TAB_SELECTED_LEFT.getHeight();
        }
    }

    /**
     * Lays out a small window's one tab in the strip from {@code stripLeft}
     * to {@code stripRight} ending on {@code rowBottom}: the tab in front
     * of a chat window's row, holding an icon when {@code icon}, the
     * name and its cross, as wide as they need and no wider than the
     * strip leaves it.
     */
    static LoneTab layOutLoneTab(FontRenderer font, float stripLeft,
                                 float stripRight, int rowBottom,
                                 String label, boolean icon) {
        int iconWidth = icon ? ChatChannelIcons.SLOT + ChatChannelIcons.GAP
                : 0;
        int controls = CONTROL_GAP + CONTROL_SIZE;
        float left = stripLeft + LONE_TAB_MARGIN;
        float room = Math.max(0.0F, stripRight - LONE_TAB_MARGIN - left
                - PADDING_X * 2 - iconWidth - controls);
        String shown = font.getStringWidth(label) <= room ? label
                : LostTalesSkyrimUiStyle.trimToWidth(font, label,
                        (int)Math.floor(room));
        int labelWidth = font.getStringWidth(shown);
        float right = left + PADDING_X * 2 + iconWidth + labelWidth + controls;
        int top = tabTop(rowBottom, true);
        double closeLeft = snappedLeft(right - PADDING_X - CONTROL_SIZE,
                displayStep());
        return new LoneTab(left, right, top, shown, labelWidth, icon,
                tabControlBox(closeLeft, top));
    }

    /**
     * The strip a small window's one tab needs to show {@code label}
     * whole beside its cross, and its icon when {@code icon}: what a
     * window opening at its content's own size is at least as wide as.
     */
    static int loneTabWidth(FontRenderer font, String label, boolean icon) {
        int iconWidth = icon ? ChatChannelIcons.SLOT + ChatChannelIcons.GAP
                : 0;
        return LONE_TAB_MARGIN * 2 + PADDING_X * 2 + iconWidth
                + font.getStringWidth(label) + CONTROL_GAP + CONTROL_SIZE;
    }

    /**
     * Draws a small window's one tab, laid out by {@link #layOutLoneTab},
     * and the strip's rule under it: the selected tab's shape, the
     * accent in {@code accentRgb} across its face, the {@code icon}
     * glyph centred in the icon's slot, the name in the accent's colour
     * and the cross, stepped by {@code closeMotion}. The rule runs the
     * strip's width, hanging from the tab's feet as a chat window's does.
     */
    static void drawLoneTab(FontRenderer font, LoneTab tab, float stripLeft,
                            float stripRight, int rowBottom,
                            LostTalesUiSheet icon, int accentRgb,
                            LostTalesUiButtonMotion closeMotion, int alpha) {
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        LostTalesChatOverlayRenderer.drawRuleAround(stripLeft, stripRight,
                rowBottom - 1, rowBottom, alpha, tab.left - SELECTED_FOOT,
                tab.right + SELECTED_FOOT);
        drawTabShape(tab.left, tab.right, tab.top, 0.0F, true, 1.0F, 0.0F,
                accentRgb, 0.0F, alpha,
                Math.round(TAB_SURFACE_ALPHA * alpha / 255.0F));
        drawAccent(tab.left + BORDER_WIDTH, tab.right - BORDER_WIDTH,
                tab.top + ACCENT_ROW, accentRgb, alpha);
        int wholeLeft = (int)Math.floor(tab.left);
        float fraction = tab.left - wholeLeft;
        int interiorTop = tab.top + INTERIOR_TOP;
        int textX = wholeLeft + PADDING_X;
        LostTalesChatVisualStyle.beginContent();
        if (tab.icon && icon != null) {
            GL11.glPushMatrix();
            GL11.glTranslatef(fraction, 0.0F, 0.0F);
            try {
                icon.drawWithShadow(textX + LostTalesUiInk.centredStart(
                                ChatChannelIcons.SIZE, icon.getWidth()),
                        centredInInterior(interiorTop, icon.getHeight()),
                        alpha);
            } finally {
                GL11.glPopMatrix();
            }
            textX += ChatChannelIcons.SLOT + ChatChannelIcons.GAP;
        }
        drawWords(font, tab.label, textX, fraction,
                centredInInterior(interiorTop, CAP_HEIGHT), accentRgb, alpha);
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.CLOSE,
                LostTalesUiSheet.CLOSE_HOVER, closeMotion,
                (float)tab.closeBox.left
                        + (CONTROL_SIZE - LostTalesUiSheet.CLOSE.getWidth()) / 2,
                (int)tab.closeBox.top
                        + (CONTROL_SIZE - LostTalesUiSheet.CLOSE.getHeight()) / 2,
                alpha);
    }

    /**
     * Where a tab is drawn: under the pointer while it is one of those
     * the hand carries, and otherwise where its glide has brought it on
     * its way to the place the layout gave it; laid on display pixels
     * either way ({@link #place}).
     */
    private static float drawnX(Row row, Tab tab) {
        return row.left + tab.drawnLeftOffset;
    }

    /**
     * How wide a tab is drawn: the width its glide has brought it to. A
     * tab under the hand keeps its own — it is being carried, not
     * resized.
     */
    private static float drawnWidth(Row row, Tab tab) {
        return tab.drawnWidthSnapped;
    }

    /**
     * How fine a step the row may move in: one display pixel, in the
     * GUI's own units. The finest the screen has, and what everything
     * else in the chat lays its pixel art on.
     */
    static double displayStep() {
        return 1.0D / ChatWindowFrame.displayScaleFactor();
    }

    /** {@code value} laid on the nearest whole display pixel. */
    static double snapped(double value, double step) {
        return Math.round(value / step) * step;
    }

    /**
     * {@code value} laid on the whole display pixel at or left of it: a
     * place centred between two pixels takes the left one, as the one
     * centring rule has it.
     */
    static double snappedLeft(double value, double step) {
        return LostTalesDisplayPixels.floor(value, 1.0D / step);
    }

    /**
     * Advances the row's tabs along their glides. A tab sets out on a
     * glide of its own whenever the row gives it another place or width
     * ({@link #layout}): from where it is drawn toward where it is now
     * bound, over the chat's animation time, fast away and gently in, as
     * a browser's tabs make room. A tab the row does not move goes on with
     * the glide it is on, so a run carried past one tab never slows the
     * others down; tabs set going together travel together, so two that
     * meet at both ends of a change meet all along it. Each edge is laid
     * on a display pixel. Places are measured from the row's own left, so
     * a window moved takes its strip with it in one piece. A tab under the
     * hand follows the pointer rigidly; put down, it travels home from
     * where the hand left it and glows once, the tool strip with it in the
     * colour of the one in front among the tabs put down together. A
     * window being resized, or
     * gliding itself, takes its layout at once: the tabs are part of the
     * geometry the hand or the glide is moving.
     */
    private void advanceRow(List<Tab> tabs, Row row) {
        long now = System.nanoTime();
        double elapsed = this.lastFrameNanos == 0L ? 0.0D
                : (now - this.lastFrameNanos) / 1.0E9D;
        this.lastFrameNanos = now;
        this.frameElapsed = elapsed;
        this.frameNanos = now;
        measureRun(tabs, row);
        List<ChatTab> carried = carriedTabs(tabs, row);
        boolean immediate = !Motions.enabled() || row.resizing
                || row.gliding;
        double step = displayStep();
        boolean landed = false;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            boolean held = carried.contains(tab.tab);
            if (!held && this.carriedLastFrame.contains(tab.tab)) {
                // Put down: it travels home from where the hand left it,
                // and glows once where it lands, the tool strip with it.
                tab.glow = 1.0F;
                tab.setOut();
                if (!landed || tab.tab.equals(row.selected)) {
                    this.toolGlowRgb =
                            ClientChatChannelState.displayColor(tab.tab);
                }
                landed = true;
            }
            place(tab, row, held, immediate, now, step);
            advanceBeats(tab, held, elapsed);
        }
        // The tool strip fades on the tab's own beat, so the two stay one.
        if (landed) {
            this.toolGlow = 1.0F;
        }
        this.toolGlow = Motions.enabled()
                ? Math.max(0.0F, this.toolGlow - (float)(elapsed / GLOW_SECONDS))
                : 0.0F;
        this.carriedLastFrame = carried;
        for (int index = this.leavingTabs.size() - 1; index >= 0; index--) {
            Tab tab = this.leavingTabs.get(index);
            place(tab, row, false, immediate, now, step);
            advanceBeats(tab, false, elapsed);
            if (tab.leg.isSettled()) {
                // Shrunk to nothing: the closed tab is gone.
                this.leavingTabs.remove(index);
            }
        }
        if (immediate) {
            this.sharedLeg.settle(true);
        }
        this.sharedDrawn = this.sharedFrom + (this.sharedTo - this.sharedFrom)
                * this.sharedLeg.advance(now, true);
        advanceButtons(now);
        // Last, so the controls after the tabs stand where the tabs have
        // just been drawn to.
        this.drawnTabsRight = drawnTabsRight(tabs, row);
        placeLeftRun();
    }

    /**
     * Where a tab stands this frame: along its own glide, or under the
     * hand; its two edges laid on display pixels, so neighbours that
     * meet share an edge exactly.
     */
    private void place(Tab tab, Row row, boolean carried, boolean immediate,
                       long now, double step) {
        if (carried) {
            tab.leftExact = drawnRunLeft(row) + tab.runOffset - row.left;
            tab.widthExact = tab.exactWidth;
            snapEdges(tab, step);
            return;
        }
        if (immediate) {
            tab.leg.settle(true);
        }
        glide(tab, tab.leg.advance(now, true), step);
        if (tab.leg.isSettled()) {
            tab.joining = false;
        }
    }

    /**
     * A tab {@code share} of the way along its glide: both its edges the
     * same share of the way from where they set out to where they are
     * bound, each then laid on a display pixel.
     */
    static void glide(Tab tab, float share, double step) {
        tab.leftExact = tab.fromLeft + (tab.toLeft - tab.fromLeft) * share;
        tab.widthExact = tab.fromWidth
                + (tab.exactWidth - tab.fromWidth) * share;
        snapEdges(tab, step);
    }

    /** Lays a tab's two edges, as it stands exactly, on display pixels. */
    private static void snapEdges(Tab tab, double step) {
        double leftEdge = snapped(tab.leftExact, step);
        double rightEdge = snapped(tab.leftExact + tab.widthExact, step);
        tab.drawnLeftOffset = (float)leftEdge;
        tab.drawnWidthSnapped = (float)Math.max(0.0D, rightEdge - leftEdge);
    }

    /**
     * A tab's own short beats: rising a pixel off the row while the hand
     * carries it and settling back after, and the glow of a tab put down
     * fading.
     */
    private static void advanceBeats(Tab tab, boolean carried,
                                     double elapsed) {
        float rise = Motions.enabled()
                ? (float)(elapsed / LIFT_SECONDS) : 1.0F;
        tab.lift = carried ? Math.min(1.0F, tab.lift + rise)
                : Math.max(0.0F, tab.lift - rise);
        tab.glow = Motions.enabled()
                ? Math.max(0.0F, tab.glow - (float)(elapsed / GLOW_SECONDS))
                : 0.0F;
    }

    /**
     * Moves the buttons every tab gives up by width toward the row's
     * width as drawn: each crosses in or out on one short beat as the
     * width passes its share.
     */
    private void advanceButtons(long now) {
        this.draftShown.advance(now, draftStands(this.sharedDrawn));
        this.cogShown.advance(now, cogStands(this.sharedDrawn));
        this.closeShown.advance(now, closeStands(this.sharedDrawn));
    }

    /**
     * How much of its room a button's fade gives it: all of it through
     * the first half of a fade in and the last half of a fade out, so the
     * room is there before the button shows and after it has gone.
     */
    static float roomPhase(float fade) {
        return Math.max(0.0F, Math.min(1.0F, fade * 2.0F));
    }

    /** How much of its ink a button's fade shows: none until its room is whole. */
    static float inkPhase(float fade) {
        return Math.max(0.0F, Math.min(1.0F, fade * 2.0F - 1.0F));
    }

    /** Whether every tab of a row this wide shows its draft mark. */
    static boolean draftStands(double width) {
        return width >= DEFAULT_TAB_WIDTH * DRAFT_SHARE - 1.0E-6D;
    }

    /** Whether every tab of a row this wide shows its cog. */
    static boolean cogStands(double width) {
        return width >= DEFAULT_TAB_WIDTH * COG_SHARE - 1.0E-6D;
    }

    /**
     * Whether every tab of a row this wide shows its cross; the tab in
     * front shows it whatever its width.
     */
    static boolean closeStands(double width) {
        return width >= DEFAULT_TAB_WIDTH * CLOSE_SHARE - 1.0E-6D;
    }

    /** How far a tab of this row shows its cross right now. */
    private float closeShare(Row row, boolean selected) {
        if (!row.closable) {
            return 0.0F;
        }
        return selected ? 1.0F : this.closeShown.clamped();
    }

    /** How much room a tab's draft mark takes as it is shown right now. */
    private static double drawnDraftWidth(Tab tab, float draftShare) {
        return tab.draft ? (COUNTER_GAP + DRAFT_WIDTH) * draftShare : 0.0D;
    }

    /** The row's tabs the hand is carrying, in row order. */
    private static List<ChatTab> carriedTabs(List<Tab> tabs, Row row) {
        List<ChatTab> carried = null;
        for (int index = 0; index < tabs.size(); index++) {
            if (isCarried(row, tabs.get(index).tab)) {
                if (carried == null) {
                    carried = new ArrayList<ChatTab>(2);
                }
                carried.add(tabs.get(index).tab);
            }
        }
        return carried == null ? Collections.<ChatTab>emptyList() : carried;
    }

    /** The row's tabs with the closed ones still shrinking away, those first. */
    private List<Tab> withLeaving(List<Tab> tabs) {
        if (this.leavingTabs.isEmpty()) {
            return tabs;
        }
        List<Tab> all = new ArrayList<Tab>(this.leavingTabs.size()
                + tabs.size());
        all.addAll(this.leavingTabs);
        all.addAll(tabs);
        return all;
    }

    /**
     * Whether the pointer is on the row's own band, between its ends: a
     * row keeps the width a closed tab left it only while it is.
     */
    private static boolean pointerOnRow(Row row, double mouseX,
                                        double mouseY) {
        if (Double.isNaN(mouseX) || Double.isNaN(mouseY)
                || !inRowBand(row, mouseY)) {
            return false;
        }
        double localX = mouseX - row.offsetX - row.fractionX;
        return localX >= row.left - STRIP_INSET && localX < row.right;
    }

    /**
     * Keeps the tabs at the width they have while a tab closed by its
     * cross leaves the row, until the pointer leaves the row: closing
     * several tabs one after another is then several clicks on one spot,
     * each next cross coming to stand under the pointer, as a browser
     * lets its tabs be closed.
     */
    void holdWidthsForClose() {
        if (!this.cachedTabs.isEmpty()) {
            this.heldWidth = this.sharedTo;
        }
    }

    /**
     * The edge the row's tabs are drawn to: past the furthest of them,
     * the closed ones still shrinking away included. A run the hand
     * pushes the controls with drives it directly. An empty row ends
     * where its tabs would have begun.
     */
    private float drawnTabsRight(List<Tab> tabs, Row row) {
        float right = row.left + SEARCH_RUN;
        boolean pushing = isPushingControls(tabs, row);
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (isCarried(row, tab.tab)) {
                // The run under the hand pushes the controls on ahead of
                // it and never draws them back past the place it holds,
                // so leaning left from its place moves nothing.
                float slotRight = (float)(row.left + tab.toLeft
                        + tab.exactWidth);
                right = Math.max(right, pushing ? Math.max(slotRight,
                        drawnX(row, tab) + drawnWidth(row, tab)) : slotRight);
                continue;
            }
            right = Math.max(right,
                    drawnX(row, tab) + drawnWidth(row, tab));
        }
        for (int index = 0; index < this.leavingTabs.size(); index++) {
            Tab tab = this.leavingTabs.get(index);
            right = Math.max(right, row.left + tab.drawnLeftOffset
                    + tab.drawnWidthSnapped);
        }
        return right;
    }

    /**
     * Whether the run under the hand is driving the controls after the
     * tabs: it is, whenever it is the last of them. They give way in
     * front of it the way a browser's add-tab control does, and come
     * back behind it as it glides into its slot on release.
     */
    private boolean isPushingControls(List<Tab> tabs, Row row) {
        return !tabs.isEmpty()
                && isCarried(row, tabs.get(tabs.size() - 1).tab);
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
    static float eased(float current, float target, double elapsed) {
        float value = (float)Motions.followTravel(MotionIds.CHAT_MARQUEE_RETURN,
                current, target, elapsed);
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

    /**
     * Where the carried run's left edge is drawn: under the pointer's
     * exact position, fractions included, so the run follows the hand as
     * finely as the screen allows rather than a GUI pixel at a time, and
     * kept inside the row as {@link #draggedRunLeft} keeps it. The row's
     * places are measured by that one, in whole pixels.
     */
    private double drawnRunLeft(Row row) {
        double pointer = Double.isNaN(row.draggedLeftExact)
                ? row.draggedLeft : row.draggedLeftExact;
        return Math.max(row.left + SEARCH_RUN, Math.min(
                carryLimit(row) - this.draggedRunWidth,
                pointer - this.draggedPressedOffset));
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
        // The row may lay out fewer tabs than it holds when it cannot
        // fit them all; the place answered is counted among the row's
        // visible tabs, as the layout's caller counts, so the tabs left
        // out before the first laid-out one are counted past.
        int leading = 0;
        for (int index = 0; index < tabs.get(0).rowIndex
                && index < row.tabs.size(); index++) {
            if (!group.contains(row.tabs.get(index))) {
                leading++;
            }
        }
        return leading + reorderSlot(draggedRunLeft(row),
                heldBefore(tabs, group), restLeft, widths, TAB_GAP,
                this.reorderLatch);
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

    private void drawTabControl(LostTalesUiSheet resting,
                                LostTalesUiSheet hovered,
                                LostTalesUiButtonMotion motion, float x,
                                int y, int alpha) {
        LostTalesUiButton.drawGlyph(resting, hovered, motion,
                x + (CONTROL_SIZE - resting.getWidth()) / 2,
                y + (CONTROL_SIZE - resting.getHeight()) / 2, alpha);
    }

    /**
     * Where a control of {@code height} pixels stands to sit in the
     * middle of the strip. The strip's last row is the window's top
     * rule, so the rows a control may use are one fewer; an odd
     * remainder is spent below, which puts every control the strip
     * carries on one centre row whatever each of them measures.
     */
    static int centredInStrip(int rowBottom, int height) {
        return rowTop(rowBottom) + (ROW_HEIGHT - 1 - height) / 2;
    }

    /**
     * The top of a tab's rows in a row ending at {@code rowBottom}: a
     * resting tab's, or the selected tab's, lifted by {@link #LIFT}.
     */
    static int tabTop(int rowBottom, boolean selected) {
        return rowBottom - HEIGHT - (selected ? LIFT : 0);
    }

    /* ---- The boxes the strip's controls are drawn in and answer on ---- */

    /**
     * The search button's frame, where it is drawn: a framed button
     * answers on its frame and nowhere else.
     */
    static LostTalesUiHitBox searchBox(int rowLeft, int rowBottom) {
        return new LostTalesUiHitBox(rowLeft + SEARCH_LEFT,
                centredInStrip(rowBottom, SEARCH_SIZE), SEARCH_SIZE,
                SEARCH_SIZE);
    }

    /**
     * The tab search's left edge in row space, as the row draws it: the
     * tool strip stands its own first control under it.
     */
    static int searchButtonLeft(Row row) {
        return row.offsetX + (int)searchBox(row.left, row.rowBottom).left;
    }

    /** The tab search's square. */
    static int searchButtonSize() {
        return SEARCH_SIZE;
    }

    /** An end control's ink, centred in the strip where it is drawn. */
    static LostTalesUiHitBox endControlInk(int x, int width, int height,
                                    int rowBottom) {
        return new LostTalesUiHitBox(x, centredInStrip(rowBottom, height), width,
                height);
    }

    /**
     * What an end control answers on: its ink with
     * {@link #END_CONTROL_SLACK} clear pixels on every side, which makes
     * a five-pixel glyph the {@link #END_CONTROL_SIZE} square the chat
     * draws its small controls in elsewhere.
     */
    static LostTalesUiHitBox endControlBox(int x, int width, int height,
                                    int rowBottom) {
        return endControlInk(x, width, height, rowBottom)
                .grown(END_CONTROL_SLACK);
    }

    /**
     * The top of the lock's artwork: its resting body centred in the
     * strip like its neighbours, the room the swing needs above it.
     */
    private static int lockTop(int rowBottom) {
        return centredInStrip(rowBottom, ChatLockAnimation.SHUT_HEIGHT)
                - (ChatLockAnimation.HEIGHT - ChatLockAnimation.SHUT_HEIGHT);
    }

    /**
     * What the lock answers on: its whole artwork, shackle included,
     * with the end controls' clearing.
     */
    static LostTalesUiHitBox lockBox(int x, int rowBottom) {
        return new LostTalesUiHitBox(x, lockTop(rowBottom), LOCK_WIDTH,
                ChatLockAnimation.HEIGHT).grown(END_CONTROL_SLACK);
    }

    /**
     * A tab's cog or cross: its {@link #CONTROL_SIZE} square, centred in
     * the interior of a tab whose rows start at {@code tabTop}, as it is
     * drawn.
     */
    static LostTalesUiHitBox tabControlBox(double x, int tabTop) {
        return new LostTalesUiHitBox(x,
                centredInInterior(tabTop + INTERIOR_TOP, CONTROL_SIZE),
                CONTROL_SIZE, CONTROL_SIZE);
    }

    /** The grip's glyph, where it is drawn against the row's right edge. */
    static LostTalesUiHitBox gripGlyphBox(int rowRight, int rowBottom) {
        return new LostTalesUiHitBox(rowRight - GRIP_INSET - GRIP_WIDTH,
                centredInStrip(rowBottom, LostTalesUiSheet.GRIP.getHeight()),
                GRIP_WIDTH, LostTalesUiSheet.GRIP.getHeight());
    }

    /**
     * The lock's body keeps its place whichever way the shackle goes:
     * every frame starts at the same left edge and stands on the same
     * row, the open shackle reaching past the square into the gap.
     */
    private void drawLock(int x, int rowBottom, boolean locked,
                          boolean hovered, LostTalesUiButtonMotion motion) {
        // The frames stand on the floor of the box the swing needs, and
        // that box is taller than the padlock at rest: centring the box
        // would leave the resting lock a row below its neighbours, so
        // the resting shape is what is centred and the swing reaches up
        // out of the strip's middle.
        //
        // The button's own beat lifts and springs the whole padlock on
        // the matrix, so the turn of its shackle stays the padlock's and
        // the two read as one movement.
        LostTalesUiButton.beginPose(motion, x, lockTop(rowBottom),
                LOCK_WIDTH, ChatLockAnimation.HEIGHT);
        try {
            this.lockAnimation.draw(x, lockTop(rowBottom), locked, hovered,
                    scaled(0xFF));
        } finally {
            LostTalesUiButton.endPose();
        }
    }

    /**
     * One of the row's end controls — the restore {@code +}, the
     * window's cog, the window's cross — drawn where it was laid out
     * and centred in the strip, the way the lock beside them is.
     */
    private void drawEndControl(LostTalesUiSheet resting, LostTalesUiSheet hovered,
                                LostTalesUiButtonMotion motion, int x,
                                int rowBottom) {
        LostTalesUiHitBox ink = endControlInk(x, resting.getWidth(),
                resting.getHeight(), rowBottom);
        LostTalesUiButton.drawGlyph(resting, hovered, motion,
                (float)ink.left, (float)ink.top, scaled(0xFF));
    }

    /**
     * The window's fullscreen control, centred in the strip like the cog
     * and the cross beside it: four corners pointing out while the
     * window keeps its own size, pointing in while it fills the screen.
     * The two glyphs cross over exactly as far as the window has
     * travelled between its two boxes, so the control turns with the
     * window rather than swapping in a frame, and each crosses to its
     * lit artwork under the pointer as every end control does.
     */
    private void drawFullscreenControl(float share, LostTalesUiButtonMotion motion,
                                       int x, int rowBottom) {
        float y = (float)endControlInk(x, FULLSCREEN_WIDTH,
                LostTalesUiSheet.FULLSCREEN.getHeight(), rowBottom).top;
        LostTalesUiButton.drawCrossingGlyphs(LostTalesUiSheet.FULLSCREEN,
                LostTalesUiSheet.FULLSCREEN_HOVER,
                LostTalesUiSheet.FULLSCREEN_EXIT,
                LostTalesUiSheet.FULLSCREEN_EXIT_HOVER, motion, share, x, y,
                scaled(0xFF));
    }

    /** Whether the pointer is on that control of that very tab. */
    private static boolean onControl(Hit hovered, Tab tab, HitKind kind) {
        return hovered != null && hovered.kind == kind
                && hovered.tab != null && hovered.tab.equals(tab.tab);
    }

    /**
     * One step of a button's motion, by what the row is hovering. The
     * pointer's button is read straight from the mouse: the row already
     * answers nothing while a tab or an edge is under the hand, so a
     * drag cannot press a control, and a press this misses costs a
     * flourish rather than an action.
     */
    private LostTalesUiButtonMotion step(LostTalesUiButtonMotion motion, Hit hovered,
                                  HitKind kind) {
        return step(motion, hovered, kind, false);
    }

    /** The same, for a button lit by its own state as well as the pointer. */
    private LostTalesUiButtonMotion step(LostTalesUiButtonMotion motion, Hit hovered,
                                  HitKind kind, boolean litByState) {
        boolean on = hovered != null && hovered.kind == kind;
        motion.advance(this.frameNanos, on || litByState, on,
                on && Mouse.isButtonDown(0));
        return motion;
    }

    /** One step of a control of a single tab, by what the row is hovering. */
    private LostTalesUiButtonMotion step(LostTalesUiButtonMotion motion,
                                         Hit hovered, Tab tab, HitKind kind) {
        motion.advance(this.frameNanos, onControl(hovered, tab, kind));
        return motion;
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
     * The tab search control: a framed button in the hole the strip
     * leaves for it, lit while the pointer is on it or its panel is out —
     * the way a main-menu button lights while it is hovered or chosen —
     * with the chevron whose run says whether the panel is out centred in
     * the frame, lit with it.
     */
    private void drawSearch(int left, int top, boolean open,
                            boolean hovered) {
        float lit = this.searchMotion.lit();
        LostTalesUiFramedButton.drawSurface(left, top, SEARCH_SIZE, SEARCH_SIZE,
                lit, scaled(TAB_SURFACE_ALPHA));
        this.searchChevron.advance(open, hovered || open);
        // The frame keeps its place and the chevron moves inside it, so
        // the button reads as a socket holding something rather than one
        // piece sliding about the strip.
        LostTalesUiButton.beginPose(this.searchMotion, left, top, SEARCH_SIZE,
                SEARCH_SIZE);
        try {
            this.searchChevron.draw(left, top, SEARCH_SIZE, SEARCH_SIZE,
                    scaled(0xFF));
        } finally {
            LostTalesUiButton.endPose();
        }
        LostTalesUiFramedButton.drawInk(left, top, SEARCH_SIZE, SEARCH_SIZE,
                lit, scaled(0xFF));
    }

    /** The drag handle at the strip's right end, where a title bar keeps it. */
    private void drawGrip(int left, int right, int rowBottom, float fade) {
        if (right - left < MIN_GRIP_WIDTH) {
            return;
        }
        LostTalesUiHitBox glyph = gripGlyphBox(right, rowBottom);
        LostTalesUiSheet.drawPairWithShadow(LostTalesUiSheet.GRIP,
                LostTalesUiSheet.GRIP_HOVER, fade, (int)glyph.left,
                (int)glyph.top, scaled(0xFF));
    }

    /**
     * Resting geometry for the row, cached until the font, limits, tab
     * list, selection, a label, a counter, a mute or the control set
     * changes. The end controls keep their room at the right; the tabs
     * share what is left at one width each: the row's default while
     * that fits, else the widest that does, the counters going when
     * even that leaves a tab no room for its own. A tab that still
     * finds no room is left out of the row rather than crossing the
     * limit; it stays open and reachable by cycling. Which controls a
     * tab shows inside its room is the draw's to decide, from the width
     * it is drawn at.
     */
    List<Tab> layout(FontRenderer font, Row row) {
        if (font == null || row == null || row.tabs == null
                || row.tabs.isEmpty()) {
            this.cachedTabs = Collections.emptyList();
            this.lockX = -1;
            this.restoreX = -1;
            this.windowSettingsX = -1;
            this.windowFullscreenX = -1;
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
        int count = channels.size();
        ChatIconMark restoreMark = row.showRestore ? row.closedMark
                : ChatIconMark.NONE;
        // The restore control's run is reserved whether or not the + is
        // showing, and with its mark: the very room the narrowest window
        // is bounded by, so a window dragged to that bound shows every
        // tab at its narrowest and nothing less, and the tabs never
        // reflow as the + comes and goes.
        int restoreRun = restoreRunWidth();
        int windowRun = windowControlsWidth(row.windowControls);
        int endControls = restoreRun + END_CONTROL_GAP + windowRun
                + MIN_GRIP_WIDTH;
        // The row's right edge as it really stands, fractions included:
        // the end controls hang from it, and the tabs share what is
        // left up to them, so a resize moves both as smoothly as the
        // window; everything is laid on display pixels when drawn.
        double edge = row.rightExact > 0.0D ? row.rightExact : row.right;
        this.endEdge = (int)Math.floor(edge);
        this.endFraction = (float)snapped(edge - this.endEdge, displayStep());
        int limit = this.endEdge - endControls;
        this.tabsLimit = limit;
        double rowRoom = edge - endControls - row.left - SEARCH_RUN;
        int[] labelWidths = new int[count];
        for (int index = 0; index < count; index++) {
            labelWidths[index] = font.getStringWidth(
                    this.cachedLabels.get(channels.get(index)));
        }
        int selectedIndex = 0;
        for (int index = 0; index < count; index++) {
            if (channels.get(index).equals(row.selected)) {
                selectedIndex = index;
            }
        }
        // Every tab is one width: the row's default while the row holds
        // them all at it, else the widest width they can all share — the
        // tab in front included. Only a row that cannot hold even the
        // narrowest tabs shows fewer of them, and then a run around the
        // tab in front rather than whichever happen to be leftmost, so a
        // tab does not come and go as the selection moves.
        int first = 0;
        int last = count - 1;
        double width = uniformTabWidth(rowRoom, last - first + 1);
        while (width < narrowestTabWidth(channels, first, last)
                && first < last) {
            if (last > selectedIndex) {
                last--;
            } else if (first < selectedIndex) {
                first++;
            } else {
                break;
            }
            width = uniformTabWidth(rowRoom, last - first + 1);
        }
        if (!Double.isNaN(this.heldWidth)) {
            if (!this.cachedChannels.containsAll(channels)) {
                // A tab joining lets the hold go: the row takes the
                // width it gives its tabs now.
                this.heldWidth = Double.NaN;
            } else {
                // A tab was closed by its cross and the pointer is still
                // on the row: the others keep their width.
                width = Math.min(width, this.heldWidth);
            }
        }
        boolean firstSight = this.cachedChannels.isEmpty();
        List<Tab> tabs = new ArrayList<Tab>(count);
        Tab previous = null;
        // The tabs are laid down from one exact running total, so the
        // row's end is the total rounded once rather than every width
        // rounded apart — which could overrun the room by a pixel a tab
        // and push the end controls off their edge.
        double cursor = row.left + SEARCH_RUN;
        for (int index = first; index <= last; index++) {
            int x = (int)Math.round(cursor);
            ChatTab channel = channels.get(index);
            boolean selected = channel.equals(row.selected);
            // The whole name, drawn into the room the row gives it and
            // cut where that room ends: a narrowing tab shows a little
            // less of its name with every pixel, never a letter less
            // every few.
            String label = this.cachedLabels.get(channel);
            int labelWidth = labelWidths[index];
            ChatEmoji icon = ChatChannelIcons.iconOf(channel);
            int tabWidth = (int)Math.round(cursor + width) - x;
            boolean draft = isTrue(this.cachedDraft.get(channel));
            // The buttons the tab shows once settled, for the hit test,
            // which answers for the places the tabs settle in: those the
            // width the row gives its tabs holds, the tab in front's
            // cross always.
            float closeShare = !showClose ? 0.0F
                    : selected || closeStands(width) ? 1.0F : 0.0F;
            float cogShare = cogStands(width) ? 1.0F : 0.0F;
            boolean draftShown = draft && draftStands(width);
            TabRoom settled = roomFor(tabWidth, iconWidth(channel), labelWidth,
                    draftShown ? COUNTER_GAP + DRAFT_WIDTH : 0, closeShare,
                    cogShare);
            int closeX = closeShare > 0.0F
                    ? x + (int)Math.floor(settled.closeLeft) : -1;
            int settingsX = cogShare > 0.0F
                    ? x + (int)Math.floor(settled.cogLeft) : -1;
            int draftX = draftShown ? draftLeft(x, icon != null,
                    (int)Math.floor(settled.labelRoom)) : -1;
            Tab built = new Tab(channel, index, icon, label, labelWidth,
                    (int)Math.floor(settled.labelRoom), draft, x, tabWidth,
                    settingsX, closeX, draftX,
                    isTrue(this.cachedMuted.get(channel)));
            built.toLeft = cursor - row.left;
            built.exactWidth = width;
            Tab was = find(this.cachedTabs, channel);
            if (was == null) {
                // Opened again while it was still shrinking away: it
                // turns round where it is rather than growing anew.
                was = find(this.leavingTabs, channel);
                this.leavingTabs.remove(was);
            }
            if (was != null) {
                // A tab the row already held carries on from where it is
                // drawn, on whatever glide it is on; given another place
                // or width, it sets out for it from there on a glide of
                // its own, and the tabs the row leaves where they were
                // keep theirs.
                built.carryOn(was);
                if (Math.abs(was.toLeft - built.toLeft) > 1.0E-6D
                        || Math.abs(was.exactWidth - built.exactWidth)
                                > 1.0E-6D) {
                    built.setOut();
                }
            } else if (firstSight) {
                // The row seen for the first time stands where it is laid
                // out rather than growing in from nothing.
                built.standAt(built.toLeft, width, displayStep());
                built.leg.settle(true);
            } else {
                // A tab joining grows from nothing where it joins: its
                // left edge where the tab before it ends as drawn now,
                // its width a seam short of nothing, so its neighbours
                // meet it all the way along the glide.
                double start = previous == null ? SEARCH_RUN
                        : previous.leftExact + previous.widthExact + TAB_GAP;
                built.standAt(start, -TAB_GAP, displayStep());
                built.joining = true;
                built.leg.settle(false);
            }
            tabs.add(built);
            previous = built;
            cursor += width + TAB_GAP;
        }
        // A tab closed out of the row shrinks away where it stood, toward
        // where the tab before it will end; one carried off to another
        // window leaves nothing behind, its neighbours closing its gap.
        for (int at = 0; at < this.cachedTabs.size(); at++) {
            Tab was = this.cachedTabs.get(at);
            if (find(tabs, was.tab) != null
                    || ChatWindowLayout.windowOf(was.tab) != null) {
                continue;
            }
            Tab before = null;
            for (int back = at - 1; back >= 0 && before == null; back--) {
                before = find(tabs, this.cachedTabs.get(back).tab);
            }
            was.toLeft = before == null ? SEARCH_RUN
                    : before.toLeft + before.exactWidth + TAB_GAP;
            was.exactWidth = -TAB_GAP;
            was.leavingWidth = Math.max(0.0D, was.widthExact);
            was.setOut();
            this.leavingTabs.add(was);
        }
        if (firstSight) {
            this.sharedFrom = width;
            this.sharedTo = width;
            this.sharedDrawn = width;
            this.sharedLeg.settle(true);
            this.drawnTabsRight = drawnTabsRight(tabs, row);
        } else if (Math.abs(width - this.sharedTo) > 1.0E-6D) {
            // The width the tabs share glides to the new one on the
            // tabs' own curve, and the buttons they give up by it with it.
            this.sharedFrom = this.sharedDrawn;
            this.sharedTo = width;
            this.sharedLeg.settle(false);
        }
        this.tabsRight = tabs.isEmpty()
                ? row.left + SEARCH_RUN : (int)Math.round(cursor - TAB_GAP);
        // The + opens a channel into this row, so it stands with the
        // tabs; everything that acts on the window itself stands with
        // the grip, against the row's right edge.
        this.showRestore = row.showRestore;
        this.restoreMark = restoreMark;
        this.restoreWidth = !row.showRestore ? 0 : PLUS_WIDTH
                + (restoreMark.isNone() ? 0 : COUNTER_GAP + RESTORE_MARK_WIDTH);
        placeLeftRun();
        // The window's own controls hang from the row's right edge, past
        // the room the tabs and the restore run were given; the tabs'
        // end is the exact total rounded, so it can stand at most half
        // a pixel past that room and never pushes them off the edge.
        int leftX = this.tabsRight + END_CONTROL_GAP + restoreRun;
        int controlX = Math.max(leftX - 1,
                this.endEdge - MIN_GRIP_WIDTH - windowRun);
        this.lockX = controlX;
        controlX += LOCK_WIDTH + END_CONTROL_GAP;
        this.firstDividerX = controlX;
        controlX += DIVIDER_WIDTH + END_CONTROL_GAP;
        if (row.windowControls) {
            this.windowSettingsX = controlX;
            controlX += COG_WIDTH + END_CONTROL_GAP;
            this.windowFullscreenX = controlX;
            controlX += FULLSCREEN_WIDTH + END_CONTROL_GAP;
            this.windowCloseX = controlX;
            controlX += CLOSE_WIDTH + END_CONTROL_GAP;
            this.secondDividerX = controlX;
            controlX += DIVIDER_WIDTH + END_CONTROL_GAP;
        } else {
            // A locked window keeps its tabs, its size and its place, so
            // it offers none of its controls; its lock alone divides off
            // the grip.
            this.windowSettingsX = -1;
            this.windowFullscreenX = -1;
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
        this.cachedRightExact = row.rightExact;
        this.cachedShowClose = showClose;
        this.cachedWindowControls = row.windowControls;
        this.cachedShowRestore = row.showRestore;
        return this.cachedTabs;
    }

    /**
     * The one width every tab of a run of {@code shown} tabs is drawn
     * at inside {@code rowRoom} pixels: the row's default while the run
     * fits at it with a seam between each pair, else the widest width
     * the run can share. Never negative.
     */
    static double uniformTabWidth(double rowRoom, int shown) {
        if (shown <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(DEFAULT_TAB_WIDTH,
                (rowRoom - TAB_GAP * (shown - 1)) / shown));
    }

    /** The widest {@link #minimumTabWidth} among the run's tabs. */
    private static int narrowestTabWidth(List<ChatTab> channels, int first,
                                         int last) {
        int narrowest = 0;
        for (int index = first; index <= last; index++) {
            narrowest = Math.max(narrowest,
                    minimumTabWidth(channels.get(index)));
        }
        return narrowest;
    }

    /**
     * Where the parts of a tab stand as it is drawn, measured from its
     * left edge: its cross against the right padding, its cog before the
     * cross, the edge everything before the buttons is cut at, and how
     * much of the name shows.
     */
    static final class TabRoom {
        /** The cross's left edge, whether or not the cross stands. */
        final double closeLeft;
        /** The cog's left edge: before the cross as far as the cross stands. */
        final double cogLeft;
        /** Where the icon, the name and the counters are cut. */
        final double contentRight;
        /** The name's room, never past the name itself. */
        final double labelRoom;

        TabRoom(double closeLeft, double cogLeft, double contentRight,
                double labelRoom) {
            this.closeLeft = closeLeft;
            this.cogLeft = cogLeft;
            this.contentRight = contentRight;
            this.labelRoom = labelRoom;
        }
    }

    /**
     * What a tab {@code width} wide holds: its buttons, shown as far as
     * {@code closeShare} and {@code cogShare} say — each a share from
     * nothing to whole as it fades — and before them, cut where they
     * begin, its padding, its icon ({@code iconWidth} with its gap, 0 for
     * none), its name and its draft mark ({@code draftWidth} as shown).
     * A button fading hands its room to the name as it goes, so the name
     * glides into it. Fractions are kept: the name's cut and everything
     * after it move with the tab's edge by the fraction the edge moves.
     */
    static TabRoom roomFor(double width, int iconWidth, int labelWidth,
                           double draftWidth, float closeShare,
                           float cogShare) {
        double control = CONTROL_GAP + CONTROL_SIZE;
        double closeLeft = width - PADDING_X - CONTROL_SIZE;
        double cogLeft = closeLeft - control * closeShare;
        double contentRight = width - PADDING_X
                - control * (closeShare + cogShare);
        double labelRoom = Math.max(0.0D, Math.min(labelWidth,
                contentRight - PADDING_X - iconWidth - draftWidth));
        return new TabRoom(closeLeft, cogLeft, contentRight, labelRoom);
    }

    /**
     * Where a tab's draft mark stands once the tab has settled: after
     * the padding, the icon's slot when it has one and the name's room,
     * exactly as the draw lays it down. Whole pixels, for the hit test.
     */
    static int draftLeft(int tabLeft, boolean iconShown, int labelRoom) {
        return tabLeft + PADDING_X
                + (iconShown ? ChatChannelIcons.SLOT + ChatChannelIcons.GAP
                        : 0)
                + labelRoom + COUNTER_GAP;
    }

    /**
     * What the draft mark answers on: a {@link #CONTROL_SIZE} square
     * centred in the interior like the cog and the cross, round the
     * mark's own four columns — a small glyph is a small thing to hit.
     */
    static LostTalesUiHitBox draftBox(int draftX, int tabTop) {
        return tabControlBox(draftX - (CONTROL_SIZE - DRAFT_WIDTH) / 2,
                tabTop);
    }

    /**
     * The narrowest a tab may be drawn: its padding around its icon and
     * the room its mark reaches past it, or around the cross the tab in
     * front keeps, whichever is wider; the name and the other controls
     * gone. What a row counts each tab at when asked whether one more
     * fits.
     */
    static int minimumTabWidth(ChatTab tab) {
        int icon = ChatChannelIcons.iconOf(tab) == null ? 0
                : ChatChannelIcons.SLOT;
        return PADDING_X * 2 + Math.max(icon, CONTROL_SIZE);
    }

    /**
     * What a row of these tabs needs at the least: every one at its
     * minimum. No tab is reserved more — the tab in front is the width
     * of the rest, whichever it is.
     */
    static int reservedRowWidth(int[] minimumWidths) {
        int minimums = 0;
        for (int index = 0; index < minimumWidths.length; index++) {
            minimums += minimumWidths[index];
        }
        return minimums;
    }

    /** The tab of a list that stands for {@code channel}, or null. */
    private static Tab find(List<Tab> tabs, ChatTab channel) {
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index).tab.equals(channel)) {
                return tabs.get(index);
            }
        }
        return null;
    }

    /** Room the tab's icon and its mark take before the label, with its gap. */
    private static int iconWidth(ChatTab tab) {
        return ChatChannelIcons.iconOf(tab) == null ? 0
                : ChatChannelIcons.SLOT + ChatChannelIcons.GAP;
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
                + (unlocked ? COG_WIDTH + END_CONTROL_GAP
                        + FULLSCREEN_WIDTH + END_CONTROL_GAP + CLOSE_WIDTH
                        + END_CONTROL_GAP + DIVIDER_WIDTH + END_CONTROL_GAP
                        : 0);
    }

    /**
     * Room the hairline, the {@code +} and its mark take after the tabs,
     * the mark's room kept whether or not one shows.
     */
    private static int restoreRunWidth() {
        return END_CONTROL_GAP + DIVIDER_WIDTH + END_CONTROL_GAP
                + PLUS_WIDTH + COUNTER_GAP + RESTORE_MARK_WIDTH;
    }

    /**
     * Room a row keeps clear of its tabs however many there are: the
     * restore control after the last tab with the room its mark takes,
     * the window's own controls and the grip at the right end, so the
     * tabs do not reflow as something behind the {@code +} goes unread.
     */
    private static int endControlsWidth(ChatWindow window) {
        return restoreRunWidth() + END_CONTROL_GAP
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
        // Every tab is one width, so the row shows every name whole at
        // the widest tab's natural width — never past the default, at
        // which a longer name is cut and read by hovering.
        int widest = 0;
        for (int index = 0; index < tabs.size(); index++) {
            widest = Math.max(widest, naturalWidth(font, tabs.get(index)));
        }
        int rowWidth = SEARCH_RUN + endControlsWidth(window)
                + TAB_GAP * (tabs.size() - 1)
                + Math.min(DEFAULT_TAB_WIDTH, widest) * tabs.size();
        // The screen lays the row out two pixels inside the window's box
        // on either side, and a chat width describes the box.
        return Math.max(ChatWindowPlacement.minChatWidth(minecraft),
                ChatWindowPlacement.chatWidthForBox(rowWidth + 4,
                        minecraft));
    }

    /**
     * A tab's width with nothing given up: its padding, icon, whole
     * name, draft mark and both controls. What every tab shows while the
     * row has room, up to the default width.
     */
    private static int naturalWidth(FontRenderer font, ChatTab tab) {
        return PADDING_X * 2 + iconWidth(tab) + controlsWidth(true)
                + font.getStringWidth(ClientChatChannelState.displayName(tab))
                + (hasDraft(tab) ? COUNTER_GAP + DRAFT_WIDTH : 0);
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
        List<ChatTab> tabs = ChatWindowFrame.visibleTabs(window);
        if (tabs.isEmpty()) {
            return 0;
        }
        int rowWidth = SEARCH_RUN + endControlsWidth(window)
                + TAB_GAP * (tabs.size() - 1) + reservedRowWidth(tabs);
        return Math.max(ChatWindowPlacement.minChatWidth(minecraft),
                ChatWindowPlacement.chatWidthForBox(
                        rowWidth + STRIP_INSET * 2, minecraft));
    }

    /** {@link #reservedRowWidth(int[])} over the tabs themselves. */
    private static int reservedRowWidth(List<ChatTab> tabs) {
        int[] minimum = new int[tabs.size()];
        for (int index = 0; index < tabs.size(); index++) {
            minimum[index] = minimumTabWidth(tabs.get(index));
        }
        return reservedRowWidth(minimum);
    }

    /** Refreshes the cache keys and reports whether the layout still holds. */
    private boolean isLayoutCurrent(FontRenderer font, Row row,
                                    boolean showClose) {
        boolean current = font == this.cachedFont
                && row.left == this.cachedLeft
                && row.right == this.cachedRight
                && row.rightExact == this.cachedRightExact
                && (row.selected == null ? this.cachedSelected == null
                        : row.selected.equals(this.cachedSelected))
                && showClose == this.cachedShowClose
                && row.windowControls == this.cachedWindowControls
                && row.showRestore == this.cachedShowRestore
                && row.closedMark.equals(this.restoreMark)
                && row.tabs.equals(this.cachedChannels);
        for (int index = 0; index < row.tabs.size(); index++) {
            ChatTab tab = row.tabs.get(index);
            String label = ClientChatChannelState.displayName(tab);
            boolean muted = ChatWindowLayout.isMuted(tab);
            boolean draft = hasDraft(tab);
            Boolean cachedMute = this.cachedMuted.get(tab);
            Boolean cachedDraftMark = this.cachedDraft.get(tab);
            if (!label.equals(this.cachedLabels.get(tab))
                    || cachedMute == null
                    || muted != cachedMute.booleanValue()
                    || cachedDraftMark == null
                    || draft != cachedDraftMark.booleanValue()) {
                this.cachedLabels.put(tab, label);
                this.cachedMuted.put(tab, Boolean.valueOf(muted));
                this.cachedDraft.put(tab, Boolean.valueOf(draft));
                current = false;
            }
        }
        return current;
    }

    private static boolean isTrue(Boolean value) {
        return value != null && value.booleanValue();
    }

    /**
     * Whether the tab wears the draft mark: it holds unsent text and
     * is not the tab being typed in, whose draft is in the field.
     */
    private static boolean hasDraft(ChatTab tab) {
        return !tab.equals(ClientChatChannelState.getSelected())
                && ClientChatChannelState.getDraft(tab).length() > 0;
    }

    /**
     * Whether the window's row has room for {@code candidates} besides
     * the tabs it shows: the same measure that bounds a resize — every
     * tab at its narrowest, the seams between
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
        // Measured in the window's own box: a window filling the screen
        // goes back to it, and must still hold every tab it took there.
        ChatWindowPlacement.Box box = ChatWindowPlacement.restingBounds(
                window, minecraft, resolution.getScaledWidth(),
                resolution.getScaledHeight());
        // The row spans the window minus the two-pixel insets the screen
        // lays it out with.
        int rowWidth = box.width - 4 - SEARCH_RUN;
        int available = rowWidth - endControlsWidth(window)
                - TAB_GAP * (tabs.size() - 1);
        return reservedRowWidth(tabs) <= available;
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
         * beside whichever buttons that width holds; less than the name
         * is wide when the row is crowded. The draw reads its own from
         * the width the tab is drawn at.
         */
        final int labelRoom;
        /**
         * How far the name is shifted left to show its hidden end while
         * the tab is hovered, and how long the pointer has rested on it;
         * both carried on when the row is laid out again.
         */
        float marqueeOffset;
        double hoverSeconds;
        /** Whether the tab holds unsent text and is not being typed in. */
        final boolean draft;
        /** Resting left edge before the row's horizontal motion. */
        final int x;
        /**
         * The place and width the row has given the tab, measured from
         * the row's left, fractions included: where its glide is bound.
         * {@link #x} and {@link #width} are their whole pixels. A closed
         * tab shrinking away is bound for nothing, a seam short of it.
         */
        double toLeft;
        double exactWidth;
        /** Where and how wide the tab's glide set out from. */
        double fromLeft;
        double fromWidth;
        /** Where and how wide the tab is this frame, exactly. */
        double leftExact;
        double widthExact;
        /**
         * The same, each edge laid on a whole <em>display</em> pixel: the
         * finest step the screen has — a third of a GUI pixel at GUI scale
         * three — so the row moves in steps that small, and the border
         * artwork still lands exactly on its own texels. Two tabs that
         * meet share their edge exactly.
         */
        float drawnWidthSnapped;
        float drawnLeftOffset;
        /**
         * The tab's own glide toward the place and width the row gave it,
         * 0 where it set out and 1 arrived; set going afresh only when
         * the row gives it another place or width.
         */
        MotionTransition leg = new MotionTransition(MotionIds.CHAT_TAB_MOVE,
                true);
        /** Whether the tab is still growing in from nothing after it opened. */
        boolean joining;
        /** The width a closed tab had as it began to shrink away; 0 while the row holds it. */
        double leavingWidth;
        /** How far the tab has risen off the row as the hand carries it, 0..1. */
        float lift;
        /** How brightly the tab glows after it was put down, 0..1. */
        float glow;
        /** Where this tab stands inside the run being carried, if it is
         *  one of them; measured each frame by {@code measureRun}. */
        int runOffset;
        /** How far the tab and its controls have crossed to their lit
         *  artwork; carried on when the row is laid out again. */
        float hoverFade;
        /**
         * The tab's own two buttons, each keeping its own beat: the cog
         * rises, the cross answers like a switch, exactly as their
         * larger counterparts on the strip do.
         */
        LostTalesUiButtonMotion cogMotion =
                new LostTalesUiButtonMotion(
                        LostTalesUiButtonMotion.Character.LIFT);
        LostTalesUiButtonMotion closeMotion =
                new LostTalesUiButtonMotion(
                        LostTalesUiButtonMotion.Character.SNAP);
        /** The draft mark's beat: it rises like any glyph that is pressed. */
        LostTalesUiButtonMotion draftMotion =
                new LostTalesUiButtonMotion(
                        LostTalesUiButtonMotion.Character.LIFT);
        final int width;
        /** Resting left edge of the cog once settled, or -1 when the tab shows none. */
        final int settingsX;
        /** Resting left edge of the close cross once settled, or -1. */
        final int closeX;
        /** Resting left edge of the draft mark once settled, or -1. */
        final int draftX;
        /** Whether the tab's lines are kept out of the closed feed. */
        final boolean muted;

        Tab(ChatTab tab, int rowIndex, ChatEmoji icon, String label,
            int labelWidth, int labelRoom, boolean draft, int x, int width,
            int settingsX, int closeX, int draftX, boolean muted) {
            this.tab = tab;
            this.rowIndex = rowIndex;
            this.icon = icon;
            this.label = label;
            this.labelWidth = labelWidth;
            this.labelRoom = labelRoom;
            this.draft = draft;
            this.x = x;
            this.width = width;
            this.exactWidth = width;
            this.settingsX = settingsX;
            this.closeX = closeX;
            this.draftX = draftX;
            this.muted = muted;
        }

        /**
         * Carries on from the tab the row held before: where it is drawn,
         * the glide it is on, and its own beats, which are carried over
         * whole so a button caught mid-beat by a re-layout carries on.
         */
        void carryOn(Tab was) {
            this.leg = was.leg;
            this.joining = was.joining;
            this.fromLeft = was.fromLeft;
            this.fromWidth = was.fromWidth;
            this.leftExact = was.leftExact;
            this.widthExact = was.widthExact;
            this.drawnLeftOffset = was.drawnLeftOffset;
            this.drawnWidthSnapped = was.drawnWidthSnapped;
            this.lift = was.lift;
            this.glow = was.glow;
            this.hoverFade = was.hoverFade;
            this.cogMotion = was.cogMotion;
            this.closeMotion = was.closeMotion;
            this.draftMotion = was.draftMotion;
            this.hoverSeconds = was.hoverSeconds;
            this.marqueeOffset = was.marqueeOffset;
        }

        /** Stands still at a place and width, which a glide sets out from. */
        void standAt(double left, double width, double step) {
            this.fromLeft = left;
            this.fromWidth = width;
            this.leftExact = left;
            this.widthExact = width;
            snapEdges(this, step);
        }

        /** Sets out on a new glide from where it is drawn now. */
        void setOut() {
            this.fromLeft = this.leftExact;
            this.fromWidth = this.widthExact;
            this.leg.settle(false);
        }

        /**
         * The width the tab's contents are laid out at: its full width
         * while it grows in or shrinks away, the moving edge cutting them
         * rather than their being squeezed, and the width it is drawn at
         * otherwise.
         */
        double laidWidth(double drawnWidth) {
            if (this.leavingWidth > 0.0D) {
                return this.leavingWidth;
            }
            return this.joining ? Math.max(drawnWidth, this.exactWidth)
                    : drawnWidth;
        }
    }
}
