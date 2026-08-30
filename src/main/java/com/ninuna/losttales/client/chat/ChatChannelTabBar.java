package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
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
 * <p>Every tab carries a settings cog and a close cross while the row
 * has room for them; when it has not, only the selected tab keeps its
 * controls and the labels are shortened with an ellipsis, widest first,
 * until the row fits. The row's other controls stand at its two ends:
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
    static final int LIFT = 2;
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
    private static final int TAB_GAP = 1;
    private static final int PADDING_X = 6;
    /** Gap between the label and a counter, and between the counters. */
    private static final int COUNTER_GAP = 3;
    /** Hit square of a control inside the selected tab. */
    static final int CONTROL_SIZE = 7;
    private static final int CONTROL_GAP = 2;
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
     * How far onto a neighbour a dragged tab leans before it takes that
     * neighbour's place: half way over it, which is where a browser's
     * tabs change places and the only share that reads the same in both
     * directions. Half is the balance point — the two tabs have swapped
     * exactly as much as they have not — so it is also the one share
     * that cannot argue with itself: any smaller share would be reached
     * going right and reached again coming back from the place it just
     * won, and the tab would shake between the two.
     */
    private static final int SWAP_GUARD = 2;
    /** How long the tabs a dragged one passed take to close up behind it. */
    private static final double SLIDE_SECONDS = 0.10D;
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
    /** The tab search control at the row's left end. */
    private static final ChatIconSheet SEARCH_ICON =
            ChatIconSheet.CHEVRON_DOWN;
    private static final ChatIconSheet SEARCH_ICON_HOVER =
            ChatIconSheet.CHEVRON_DOWN_HOVER;
    private static final int SEARCH_WIDTH = SEARCH_ICON.getWidth();
    /** Where the row's tabs begin: past the search control and its gap. */
    private static final int SEARCH_RUN = SEARCH_WIDTH + END_CONTROL_GAP;
    /** Room the grip keeps at the row's right end: its glyph and inset. */
    static final int MIN_GRIP_WIDTH = GRIP_WIDTH + GRIP_INSET;
    /**
     * Opacity of a tab's interior. The border sprites carry that same
     * interior behind them at this alpha, so the span they enclose has
     * to be filled with it exactly or a tab reads as two tones.
     */
    private static final int TAB_SURFACE_ALPHA = 0xAB;
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
    /** Limit the tabs may not cross; the end controls begin here. */
    private int tabsLimit = Integer.MAX_VALUE;
    /**
     * Whether the last layout changed which tabs the row holds. Only
     * then is the row worth settling into its new shape: a window moved
     * or resized carries its whole strip with it, and a row easing after
     * one would swing along behind the window rather than belonging to
     * it.
     */
    private boolean tabsChanged;
    /**
     * How far each of the row's own controls has crossed to its hovered
     * artwork. A control the pointer leaves crosses back the same way,
     * so nothing in the strip ever swaps in one frame.
     */
    private float searchFade;
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
    private float drawnTabsRight;
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

        Hit(HitKind kind, ChatTab tab) {
            this.kind = kind;
            this.tab = tab;
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
        /** Where the dragged tab's left edge follows the pointer. */
        int draggedLeft = Integer.MIN_VALUE;
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
            return new Hit(HitKind.TAB, tab.tab);
        }
        if (hitsControl(localX, row.left, SEARCH_WIDTH)) {
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
     * Insertion index for a tab dropped at {@code mouseX}, into the
     * row's <em>full</em> tab list: before the first drawn tab whose
     * centre lies right of the pointer, and past everything — trimmed
     * trailing tabs included — right of the drawn run, so a crowded row
     * can still take a tab to its very end.
     */
    int dropIndexAt(FontRenderer font, Row row, int mouseX) {
        List<Tab> tabs = layout(font, row);
        int localX = mouseX - row.offsetX;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (localX < tab.x + tab.width / 2) {
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
        if (tabs.isEmpty()) {
            return;
        }
        advanceSlides(tabs, row);
        this.alphaScale = Math.max(0.0F, Math.min(1.0F, alphaScale));
        Hit hovered = hitAt(font, row, mouseX, mouseY);
        int bottom = row.rowBottom;
        // The window's title strip, ending exactly on the rule row:
        // drawn outside the clip below, so the inward-rounded cut can
        // never open a bright seam — anything a tab gives up to the cut
        // shows this same surface.
        Gui.drawRect(row.offsetX + row.left - 2, rowTop(bottom),
                row.offsetX + row.right + 2, bottom - 1,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_RGB,
                        scaled(LostTalesChatVisualStyle.SURFACE_ALPHA)));
        // Everything else the strip draws stops short of the rule it
        // ends on: a sprite's shadow falls a pixel down and right, and
        // the rule is a hairline the window is bounded by, not something
        // to cast onto. Inward, so not one display pixel of a tab or a
        // shadow ever lands on the rule row; the rule itself is drawn
        // after the clip.
        boolean clipped = LostTalesChatOverlayRenderer.beginVerticalClip(
                Minecraft.getMinecraft(), Double.NaN,
                row.rowBottomExact - 1.0D, true);
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
            Tab draggedTab = null;
            for (int index = 0; index < tabs.size(); index++) {
                Tab tab = tabs.get(index);
                if (tab.tab.equals(row.dragging)) {
                    draggedTab = tab;
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
            if (draggedTab != null) {
                drawTab(font, draggedTab, row, bottom, hovered,
                        draggedTab.tab.equals(row.selected));
            }
            // The end controls sit centred in the strip, like the selected
            // tab's label; the badge's caps share that centre.
            if (this.lockX >= 0) {
                drawLock(row.offsetX + this.lockX, bottom, row.locked,
                        hovered != null && hovered.kind == HitKind.LOCK);
            }
            if (this.tabDividerX >= 0) {
                drawDivider(row.offsetX + this.tabDividerX, bottom);
            }
            if (this.restoreX >= 0) {
                boolean restoreHovered = hovered != null
                        && hovered.kind == HitKind.RESTORE;
                this.restoreFade = fade(this.restoreFade, hovered,
                        HitKind.RESTORE);
                drawEndControl(ChatIconSheet.PLUS, ChatIconSheet.PLUS_HOVER,
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
            // The tab search sits at the row's left end, before the
            // first tab, where a browser keeps it.
            this.searchFade = fade(this.searchFade, hovered,
                    HitKind.SEARCH);
            drawSearch(row.offsetX + row.left, bottom, this.searchFade);
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
                    row.offsetX + row.left - 2, row.offsetX + row.right + 2,
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
                row.offsetX + row.left - 2, bottom - 1,
                row.offsetX + row.right + 2, bottom,
                scaled(LostTalesChatOverlayRenderer.backdropRowAlpha(
                        Minecraft.getMinecraft())));
        LostTalesChatOverlayRenderer.drawRule(row.offsetX + row.left - 2,
                row.offsetX + row.right + 2, bottom - 1, bottom,
                scaled(0xFF));
        regions.addWindow(row.offsetX + row.left - 2, rowTop(bottom),
                row.offsetX + row.right + 2, bottom);
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
        int left = row.offsetX + drawnX(row, tab);
        int right = left + tab.width;
        int shift = left - (row.offsetX + tab.x);
        float dim = 1.0F;
        // The border artwork is opaque and carries the interior with it;
        // the span between the pieces is filled to match.
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
        // Everything inside the tab stands in the interior, one clear
        // row under the accent: the icon at its own size, the label's
        // caps and the controls level with it. The selected tab's lift
        // carries them up with it rather than re-centring them.
        int interiorTop = top + INTERIOR_TOP;
        int textX = left + PADDING_X;
        int textY = centredInInterior(interiorTop, CAP_HEIGHT);
        if (tab.icon != null) {
            ChatChannelIcons.draw(Minecraft.getMinecraft(), tab.tab, textX,
                    centredInInterior(interiorTop, ChatChannelIcons.SIZE),
                    textAlpha);
            textX += ChatChannelIcons.SIZE + ChatChannelIcons.GAP;
        }
        LostTalesChatVisualStyle.drawPlain(font,
                tab.muted ? "§o" + tab.label : tab.label,
                textX, textY, textAlpha);
        textX += tab.labelWidth;
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
        // The controls are never dimmed with the label: only the
        // window's own fade and a dragged tab's faintness reach them.
        // Their hit squares are centred in the interior like the caps,
        // and each sprite is centred in its square in turn.
        int controlTop = centredInInterior(interiorTop, CONTROL_SIZE);
        int controlAlpha = scaled(Math.round(0xFF * dim));
        if (tab.settingsX >= 0) {
            tab.cogFade = LostTalesChatVisualStyle.hoverFade(tab.cogFade,
                    onControl(hovered, tab, HitKind.SETTINGS),
                    this.frameElapsed);
            drawTabControl(ChatIconSheet.COG, ChatIconSheet.COG_HOVER,
                    tab.cogFade, row.offsetX + tab.settingsX + shift,
                    controlTop, controlAlpha);
        }
        if (tab.closeX >= 0) {
            tab.closeFade = LostTalesChatVisualStyle.hoverFade(tab.closeFade,
                    onControl(hovered, tab, HitKind.CLOSE),
                    this.frameElapsed);
            drawTabControl(ChatIconSheet.CLOSE, ChatIconSheet.CLOSE_HOVER,
                    tab.closeFade, row.offsetX + tab.closeX + shift,
                    controlTop, controlAlpha);
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
    private static void drawTabShape(int left, int right, int top,
                                     boolean selected, float lit,
                                     int spriteAlpha, int interiorAlpha) {
        ChatIconSheet leftPiece;
        ChatIconSheet rightPiece;
        // The lit border artwork laid over the resting pair as far as the
        // tab has crossed to it; its tones cross by the same share, so
        // the whole tab lights together rather than in two steps.
        ChatIconSheet leftLit = null;
        ChatIconSheet rightLit = null;
        int interiorRgb;
        int tipRgb;
        if (selected) {
            leftPiece = ChatIconSheet.TAB_SELECTED_LEFT;
            rightPiece = ChatIconSheet.TAB_SELECTED_RIGHT;
            interiorRgb = LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB;
            tipRgb = TIP_LIT_RGB;
        } else {
            leftPiece = ChatIconSheet.TAB_LEFT;
            rightPiece = ChatIconSheet.TAB_RIGHT;
            leftLit = ChatIconSheet.TAB_HOVER_LEFT;
            rightLit = ChatIconSheet.TAB_HOVER_RIGHT;
            interiorRgb = LostTalesChatVisualStyle.blend(
                    LostTalesChatVisualStyle.SURFACE_RGB,
                    LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB, lit);
            tipRgb = LostTalesChatVisualStyle.blend(TIP_RGB, TIP_LIT_RGB,
                    lit);
        }
        int spanLeft = left + BORDER_WIDTH;
        int spanRight = right - BORDER_WIDTH;
        int height = leftPiece.getHeight();
        if (spanRight > spanLeft) {
            // As deep as the pieces themselves, so the span cannot end
            // short of the artwork it stands between.
            Gui.drawRect(spanLeft, top, spanRight, top + height,
                    LostTalesChatVisualStyle.argb(interiorRgb, interiorAlpha));
            // The tips are the pieces' innermost lit columns, one pixel
            // outside the span, so the line meets both without a gap.
            Gui.drawRect(spanLeft, top + TIP_ROW, spanRight,
                    top + TIP_ROW + 1,
                    LostTalesChatVisualStyle.argb(tipRgb, spriteAlpha));
        }
        leftPiece.draw(left, top, spriteAlpha);
        rightPiece.draw(spanRight, top, spriteAlpha);
        int over = Math.round(spriteAlpha * lit);
        if (leftLit != null
                && over >= LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            leftLit.draw(left, top, over);
            rightLit.draw(spanRight, top, over);
        }
    }

    /**
     * A one-pixel band of the channel's colour from {@code left} to
     * {@code right}: the given alpha at the centre, nothing at either
     * end, the same profile as the window's edge rules.
     */
    private static void drawAccent(int left, int right, int y, int rgb,
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
    private int drawnX(Row row, Tab tab) {
        return tab.tab.equals(row.dragging) ? draggedLeft(row, tab)
                : Math.round(tab.drawnX);
    }

    /**
     * Advances the row toward its own layout. Every tab keeps the place
     * it was drawn at and eases to the one it holds now, over
     * {@link #SLIDE_SECONDS}, and the controls standing after the tabs
     * travel with them, so a tab opening or closing carries the row
     * along instead of snapping it. The tab under the hand is the one
     * exception: it follows the pointer rigidly.
     */
    private void advanceSlides(List<Tab> tabs, Row row) {
        long now = System.nanoTime();
        double elapsed = this.slideNanos == 0L ? 0.0D
                : (now - this.slideNanos) / 1.0E9D;
        this.slideNanos = now;
        this.frameElapsed = elapsed;
        boolean animate = LostTalesConfig.enableChatAnimations
                && this.tabsChanged;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            float target = tab.tab.equals(row.dragging)
                    ? draggedLeft(row, tab) : tab.x;
            tab.drawnX = animate
                    ? eased(tab.drawnX, target, elapsed) : target;
        }
        // The controls after the tabs are attached to the last of them
        // rather than easing to where the tabs will end up: they stand
        // at the edge the tabs are drawn to this frame. A tab opening
        // puts them in their new place at once instead of flying in
        // from under the row, one closing carries them along as its
        // neighbours travel, and a tab dragged into the row cannot be
        // pushed out past a + still catching up. The dragged tab itself
        // is left out — it follows the pointer, not the row.
        this.drawnTabsRight = drawnTabsRight(tabs, row);
        placeLeftRun();
    }

    /**
     * The edge the row's tabs are drawn to: past the furthest of them.
     * An empty row ends where its tabs would have begun.
     */
    private float drawnTabsRight(List<Tab> tabs, Row row) {
        if (row.dragging != null) {
            // A tab under the hand is drawn at the pointer, so the row
            // has no drawn edge to stand at; the controls hold the one
            // the tabs will settle to, which a reorder never changes.
            return this.tabsRight;
        }
        float right = row.left + SEARCH_RUN;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            right = Math.max(right, tab.drawnX + tab.width);
        }
        return right;
    }

    /** One step toward {@code target}, arriving rather than creeping. */
    private static float eased(float current, float target,
                               double elapsed) {
        float value = (float)LostTalesChatMotion.approach(current, target,
                elapsed, SLIDE_SECONDS);
        return Math.abs(target - value) < 0.5F ? target : value;
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
            return;
        }
        this.tabDividerX = Math.round(this.drawnTabsRight) + END_CONTROL_GAP;
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
        // The end controls keep their room from a dragged tab as much as
        // from a resting one: a tab stops where the row's tabs stop.
        return Math.max(row.left + SEARCH_RUN,
                Math.min(this.tabsLimit - tab.width, row.draggedLeft));
    }

    /**
     * Which place in the row a tab being dragged along it has reached:
     * past every tab whose centre its own drawn centre has passed, the
     * ones travelling with it left out. An insert-before index into the
     * row's tab list, or -1 while the tab is not in this row.
     *
     * <p>Measured against the row with the tabs being dragged taken out
     * of it, so the places do not move as the run passes between them:
     * a threshold that moved with the run would be crossed again the
     * moment it was crossed, and the row would shake.</p>
     */
    int slideIndexAt(FontRenderer font, Row row, List<ChatTab> group) {
        List<Tab> tabs = layout(font, row);
        Tab dragged = null;
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index).tab.equals(row.dragging)) {
                dragged = tabs.get(index);
            }
        }
        if (dragged == null) {
            return -1;
        }
        // Every place the run could take, as the left edge the row would
        // give it once the tabs travelling with it are out of the way.
        // The furthest place the tab has actually leaned into wins, so
        // both ends of the row are reachable whatever the tab measures,
        // which comparing centres alone does not manage.
        int left = draggedLeft(row, dragged);
        int current = heldBefore(tabs, group);
        int previous = row.left + SEARCH_RUN;
        int slot = 0;
        int best = 0;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (group.contains(tab.tab)) {
                continue;
            }
            int next = previous + tab.width + TAB_GAP;
            slot++;
            // Half way onto the neighbour either way, with a couple of
            // pixels of slack around that so a tab left resting on the
            // balance point does not shake across it as the hand does.
            // The slack leans against the move, whichever way it goes.
            int middle = (previous + next) / 2;
            int threshold = slot > current ? middle + SWAP_GUARD
                    : middle - SWAP_GUARD;
            if (left >= threshold) {
                best = slot;
            }
            previous = next;
        }
        return best;
    }

    /**
     * How far past either end of the row a dragged tab has been carried:
     * the tab itself stops where the row's tabs stop, so this is the
     * pointer pulling on beyond that, and it is what asks for a window
     * of its own as plainly as carrying the tab above or below the row.
     * Zero while the tab is still somewhere the row can put it.
     */
    int draggedOverrun(FontRenderer font, Row row) {
        List<Tab> tabs = layout(font, row);
        if (row.draggedLeft == Integer.MIN_VALUE) {
            return 0;
        }
        Tab dragged = null;
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index).tab.equals(row.dragging)) {
                dragged = tabs.get(index);
            }
        }
        if (dragged == null) {
            return 0;
        }
        int lowest = row.left + SEARCH_RUN;
        int highest = this.tabsLimit - dragged.width;
        if (row.draggedLeft < lowest) {
            return lowest - row.draggedLeft;
        }
        return row.draggedLeft > highest ? row.draggedLeft - highest : 0;
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
                                ChatIconSheet hovered, float fade, int x,
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
     * The tab search control: the chevron a list opens below, centred in
     * the strip like every other control the row carries.
     */
    private void drawSearch(int x, int rowBottom, float fade) {
        drawEndControl(SEARCH_ICON, SEARCH_ICON_HOVER, fade, x, rowBottom);
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
     * share what is left: whole, with controls on every tab, while that
     * fits, else with controls on the selected tab only and labels
     * shortened, widest first, to one common width. A tab that still
     * finds no room is left out of the row rather than crossing the
     * limit; it stays open and reachable by cycling.
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
        this.tabsChanged = !channels.equals(this.cachedChannels);
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
        int[] labelRoom = labelWidths.clone();
        // Counters are the first thing a crowded row gives up, and only
        // if shortening every label was not enough.
        boolean showCounters = true;
        int first = 0;
        int last = count - 1;
        if (!controlsEverywhere) {
            int[] fixed = fixedWidths(channels, counters, controls,
                    selectedIndex, true);
            if (total(fixed, 0, count - 1) > available) {
                showCounters = false;
                fixed = fixedWidths(channels, counters, controls,
                        selectedIndex, false);
            }
            // Only when even bare tabs do not fit does the row show
            // fewer of them, and then it keeps a run around the tab in
            // front rather than whichever ones happen to be leftmost —
            // so a tab does not come and go as the selection moves.
            while (first <= last && total(fixed, first, last)
                    > available + TAB_GAP * (count - (last - first + 1))) {
                if (last > selectedIndex) {
                    last--;
                } else if (first < selectedIndex) {
                    first++;
                } else {
                    break;
                }
            }
            // The tab in front keeps its whole name — it is the one the
            // player is reading — and the rest share what is left, so a
            // crowded row shortens around it instead of hiding it.
            // Seams are only needed between the tabs the row shows.
            int room = available + TAB_GAP * (count - (last - first + 1))
                    - total(fixed, first, last);
            capLabelsAround(labelRoom, room, selectedIndex - first,
                    first, last);
        }
        List<Tab> tabs = new ArrayList<Tab>(count);
        int x = row.left + SEARCH_RUN;
        for (int index = first; index <= last; index++) {
            ChatTab channel = channels.get(index);
            boolean selected = channel.equals(row.selected);
            String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                    this.cachedLabels.get(channel), labelRoom[index]);
            String pingText = showCounters
                    ? ClientChatChannelViews.counterText(
                            count(this.cachedPings, channel)) : "";
            String otherText = showCounters
                    ? ClientChatChannelViews.counterText(
                            count(this.cachedOther, channel)) : "";
            int labelWidth = font.getStringWidth(label);
            int pingWidth = font.getStringWidth(pingText);
            int otherWidth = font.getStringWidth(otherText);
            ChatEmoji icon = ChatChannelIcons.iconOf(channel);
            int width = labelWidth + PADDING_X * 2 + iconWidth(channel)
                    + countersWidth(pingWidth, otherWidth);
            int settingsX = -1;
            int closeX = -1;
            if (controlsEverywhere || selected) {
                settingsX = x + width - PADDING_X + CONTROL_GAP;
                width += CONTROL_GAP + CONTROL_SIZE;
                if (showClose) {
                    closeX = x + width - PADDING_X + CONTROL_GAP;
                    width += CONTROL_GAP + CONTROL_SIZE;
                }
            }
            Boolean muted = this.cachedMuted.get(channel);
            Tab built = new Tab(channel, index, icon, label, labelWidth,
                    pingText, pingWidth, otherText, otherWidth, x, width,
                    settingsX, closeX, muted != null && muted.booleanValue());
            // A tab the row already held keeps where it was drawn, so a
            // change of places is travelled rather than jumped; one that
            // has just opened starts in its own place.
            for (int at = 0; at < this.cachedTabs.size(); at++) {
                if (this.cachedTabs.get(at).tab.equals(channel)) {
                    Tab was = this.cachedTabs.get(at);
                    built.drawnX = was.drawnX;
                    built.hoverFade = was.hoverFade;
                    built.cogFade = was.cogFade;
                    built.closeFade = was.closeFade;
                    break;
                }
            }
            tabs.add(built);
            x += width + TAB_GAP;
        }
        this.tabsRight = tabs.isEmpty()
                ? row.left + SEARCH_RUN : x - TAB_GAP;
        this.drawnTabsRight = drawnTabsRight(tabs, row);
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
     * What a tab takes before its label: its padding, its icon, its
     * counters while the row still shows them, and the controls of the
     * tab in front.
     */
    private static int[] fixedWidths(List<ChatTab> channels, int[] counters,
                                     int controls, int selectedIndex,
                                     boolean withCounters) {
        int[] fixed = new int[channels.size()];
        for (int index = 0; index < fixed.length; index++) {
            fixed[index] = PADDING_X * 2 + iconWidth(channels.get(index))
                    + (withCounters ? counters[index] : 0)
                    + (index == selectedIndex ? controls : 0);
        }
        return fixed;
    }

    private static int total(int[] widths, int first, int last) {
        int sum = 0;
        for (int index = Math.max(0, first);
             index <= Math.min(widths.length - 1, last); index++) {
            sum += widths[index];
        }
        return sum;
    }

    /**
     * Shortens the widest labels as {@link #capLabels} does, but gives
     * the label at {@code keptIndex} first claim on the room: it keeps
     * its whole width while any of it fits, and the others are capped
     * against what remains. A negative index caps every label alike.
     */
    static void capLabelsAround(int[] widths, int room, int keptIndex) {
        capLabelsAround(widths, room, keptIndex, 0, widths.length - 1);
    }

    /** As above, over the run of labels the row actually shows. */
    static void capLabelsAround(int[] widths, int room, int keptOffset,
                                int first, int last) {
        int from = Math.max(0, first);
        int to = Math.min(widths.length - 1, last);
        if (from > to) {
            return;
        }
        int span = to - from + 1;
        int[] shown = new int[span];
        for (int index = 0; index < span; index++) {
            shown[index] = widths[from + index];
        }
        capShownLabels(shown, room, keptOffset);
        for (int index = 0; index < span; index++) {
            widths[from + index] = shown[index];
        }
    }

    private static void capShownLabels(int[] widths, int room,
                                       int keptIndex) {
        if (keptIndex < 0 || keptIndex >= widths.length) {
            capLabels(widths, room);
            return;
        }
        int kept = Math.max(0, Math.min(widths[keptIndex], room));
        int[] others = new int[widths.length - 1];
        for (int index = 0, target = 0; index < widths.length; index++) {
            if (index != keptIndex) {
                others[target++] = widths[index];
            }
        }
        capLabels(others, room - kept);
        for (int index = 0, source = 0; index < widths.length; index++) {
            widths[index] = index == keptIndex ? kept : others[source++];
        }
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
            ChatTab tab = tabs.get(index);
            rowWidth += PADDING_X * 2 + iconWidth(tab) + controlsWidth(true)
                    + font.getStringWidth(
                            ClientChatChannelState.displayName(tab))
                    + countersWidth(
                            font.getStringWidth(
                                    ClientChatChannelViews.counterText(
                                            ClientChatChannelViews
                                                    .unreadPingCount(tab))),
                            font.getStringWidth(
                                    ClientChatChannelViews.counterText(
                                            ClientChatChannelViews
                                                    .unreadOtherCount(tab))));
        }
        // The screen lays the row out two pixels inside the window's box
        // on either side, and a chat width describes the box.
        return Math.max(ChatWindowPlacement.minChatWidth(minecraft),
                ChatWindowPlacement.chatWidthForBox(rowWidth + 4,
                        minecraft));
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
     * Whether the window's row is already shortening its tab names: the
     * same width model {@link #layout} draws with — the end controls'
     * reserved room, each tab's padding, icon and counters (given up
     * first, exactly as the layout gives them up), the controls the
     * crowded row keeps on the selected tab alone — measured against
     * the window's own width. This is what the auto-open policy asks
     * before filing a new tab into a window: a row whose names are
     * whole takes the tab, one already ellipsizing is full and the
     * channel opens elsewhere. The restore control's room is always
     * counted, so the answer does not flap as the {@code +} comes and
     * goes. Without a renderer to measure with the answer is "not
     * crowded", which keeps the layout usable headlessly.
     */
    static boolean rowIsCrowded(Minecraft minecraft, ChatWindow window) {
        if (minecraft == null || minecraft.fontRenderer == null
                || window == null) {
            return false;
        }
        FontRenderer font = minecraft.fontRenderer;
        List<ChatTab> tabs = ChatWindowFrame.visibleTabs(window);
        if (tabs.isEmpty()) {
            return false;
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
        int fixedWithCounters = controlsWidth(true);
        int fixedWithout = controlsWidth(true);
        int labels = 0;
        for (int index = 0; index < tabs.size(); index++) {
            ChatTab tab = tabs.get(index);
            int base = PADDING_X * 2 + iconWidth(tab);
            fixedWithCounters += base + countersWidth(
                    font.getStringWidth(
                            ClientChatChannelViews.counterText(
                                    ClientChatChannelViews
                                            .unreadPingCount(tab))),
                    font.getStringWidth(
                            ClientChatChannelViews.counterText(
                                    ClientChatChannelViews
                                            .unreadOtherCount(tab))));
            fixedWithout += base;
            labels += font.getStringWidth(
                    ClientChatChannelState.displayName(tab));
        }
        // Counters go before labels shorten, so a row that only had to
        // give them up still counts as whole-named.
        int fixed = fixedWithCounters > available
                ? fixedWithout : fixedWithCounters;
        return labels > available - fixed;
    }

    static final class Tab {
        final ChatTab tab;
        /** The tab's index in the row's full list; a trimmed run skips some. */
        final int rowIndex;
        /** The channel's emoji before the label, or null. */
        final ChatEmoji icon;
        final String label;
        final int labelWidth;
        /** {@code [p]} unread pings, or empty. */
        final String pingText;
        final int pingWidth;
        /** {@code [x]} other unread lines, or empty. */
        final String otherText;
        final int otherWidth;
        /** Resting left edge before the row's horizontal motion. */
        final int x;
        /**
         * Where the tab is actually drawn while the row settles into
         * that place. The one thing about a tab that changes without
         * the row being laid out again.
         */
        float drawnX;
        /** How far the tab and its controls have crossed to their lit
         *  artwork; carried on when the row is laid out again. */
        float hoverFade;
        float cogFade;
        float closeFade;
        final int width;
        /** Resting left edge of the cog, or -1 when the tab has none. */
        final int settingsX;
        /** Resting left edge of the close cross, or -1. */
        final int closeX;
        final boolean muted;

        Tab(ChatTab tab, int rowIndex, ChatEmoji icon, String label,
            int labelWidth, String pingText, int pingWidth, String otherText,
            int otherWidth, int x, int width, int settingsX, int closeX,
            boolean muted) {
            this.tab = tab;
            this.rowIndex = rowIndex;
            this.icon = icon;
            this.label = label;
            this.labelWidth = labelWidth;
            this.pingText = pingText;
            this.pingWidth = pingWidth;
            this.otherText = otherText;
            this.otherWidth = otherWidth;
            this.x = x;
            this.drawnX = x;
            this.width = width;
            this.settingsX = settingsX;
            this.closeX = closeX;
            this.muted = muted;
        }
    }
}
