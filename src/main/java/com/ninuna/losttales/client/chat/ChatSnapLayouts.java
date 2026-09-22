package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiFlatLayers;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * The snap layouts: the ways a desktop shares its screen between
 * windows — two halves; two thirds and a third; a half and two quarters;
 * four quarters; three thirds; a half between two quarter columns, in
 * the order Windows 11 offers them —
 * each drawn as a small screen cut into its zones, a zone being a part
 * of the screen a window can fill. A window carried near the top of the
 * screen finds them on the snap bar there ({@link Bar}); the pointer
 * resting on a window's fullscreen control finds them in a panel under
 * it ({@link Flyout}). The zone under the pointer is where the window
 * goes. A layout is offered only while every one of its zones holds the
 * narrowest window the chat draws, as a desktop offers fewer layouts on
 * a smaller screen.
 *
 * <p>Ahead of the plain layouts stand the suggested ones, as a desktop
 * suggests them: the halves and the half with two quarters, already
 * holding the other windows in front, each drawn as its front tab's
 * icon ({@link Suggestion}). Anywhere on a suggestion lands the window in
 * hand in its zone and sends the others to theirs with it.</p>
 */
final class ChatSnapLayouts {
    /** Every layout, in the order it is offered. */
    private static final ChatWindow.ScreenFill[][] LAYOUTS = {
            {ChatWindow.ScreenFill.LEFT, ChatWindow.ScreenFill.RIGHT},
            {ChatWindow.ScreenFill.LEFT_TWO_THIRDS,
                    ChatWindow.ScreenFill.RIGHT_THIRD},
            {ChatWindow.ScreenFill.LEFT, ChatWindow.ScreenFill.TOP_RIGHT,
                    ChatWindow.ScreenFill.BOTTOM_RIGHT},
            {ChatWindow.ScreenFill.TOP_LEFT, ChatWindow.ScreenFill.TOP_RIGHT,
                    ChatWindow.ScreenFill.BOTTOM_LEFT,
                    ChatWindow.ScreenFill.BOTTOM_RIGHT},
            {ChatWindow.ScreenFill.LEFT_THIRD,
                    ChatWindow.ScreenFill.CENTRE_THIRD,
                    ChatWindow.ScreenFill.RIGHT_THIRD},
            {ChatWindow.ScreenFill.LEFT_QUARTER,
                    ChatWindow.ScreenFill.CENTRE_HALF,
                    ChatWindow.ScreenFill.RIGHT_QUARTER}};
    /**
     * A layout's small screen is this wide, in GUI pixels, and as tall
     * as the screen's own shape makes it between the two bounds below.
     * A quarter column of it is eleven pixels, the narrowest zone drawn,
     * and a quarter of the lowest one eleven pixels tall: either holds a
     * tab's icon and its shadow.
     */
    static final int THUMB_WIDTH = 48;
    static final int MIN_THUMB_HEIGHT = 24;
    static final int MAX_THUMB_HEIGHT = 48;
    /** Clear pixels between two zones of one layout. */
    static final int ZONE_GAP = 2;
    /** Clear pixels between two layouts. */
    static final int LAYOUT_GAP = 4;
    /** Clear pixels between the panel's frame and its layouts. */
    static final int PADDING = 4;
    /** The popups' one-pixel frame the panel wears. */
    private static final int FRAME = 1;

    private ChatSnapLayouts() {}

    /**
     * The layouts a screen this size has room for: every zone of each at
     * least {@code minWidth} wide and {@code minHeight} tall inside the
     * margin a window keeps in it, as {@link ChatWindowPlacement#fillBounds}
     * lays the window there.
     */
    static List<ChatWindow.ScreenFill[]> offered(int screenWidth,
                                                 int screenHeight,
                                                 double minWidth,
                                                 double minHeight) {
        List<ChatWindow.ScreenFill[]> offered =
                new ArrayList<ChatWindow.ScreenFill[]>(LAYOUTS.length);
        int margin = 2 * ChatWindowPlacement.EDGE_MARGIN;
        for (ChatWindow.ScreenFill[] layout : LAYOUTS) {
            boolean fits = true;
            for (ChatWindow.ScreenFill zone : layout) {
                if (zone.width(screenWidth) - margin < minWidth
                        || zone.height(screenHeight) - margin < minHeight) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                offered.add(layout);
            }
        }
        return offered;
    }

    /** As above for the running game: the readable narrowest window, one line tall. */
    static List<ChatWindow.ScreenFill[]> offered(Minecraft minecraft,
                                                 int screenWidth,
                                                 int screenHeight) {
        return offered(screenWidth, screenHeight,
                ChatWindowPlacement.minBoxWidth(minecraft),
                ChatWindowPlacement.minHeight(minecraft));
    }

    /**
     * The layout a window just sent to {@code fill} shares the screen by:
     * the zones snap assist offers the other windows. None for the whole
     * screen, its own box or a part the player shaped.
     */
    static ChatWindow.ScreenFill[] layoutFor(ChatWindow.ScreenFill fill) {
        if (fill == ChatWindow.ScreenFill.LEFT
                || fill == ChatWindow.ScreenFill.RIGHT) {
            return new ChatWindow.ScreenFill[] {ChatWindow.ScreenFill.LEFT,
                    ChatWindow.ScreenFill.RIGHT};
        }
        if (fill == ChatWindow.ScreenFill.TOP_LEFT
                || fill == ChatWindow.ScreenFill.TOP_RIGHT
                || fill == ChatWindow.ScreenFill.BOTTOM_LEFT
                || fill == ChatWindow.ScreenFill.BOTTOM_RIGHT) {
            return new ChatWindow.ScreenFill[] {ChatWindow.ScreenFill.TOP_LEFT,
                    ChatWindow.ScreenFill.TOP_RIGHT,
                    ChatWindow.ScreenFill.BOTTOM_LEFT,
                    ChatWindow.ScreenFill.BOTTOM_RIGHT};
        }
        if (fill == ChatWindow.ScreenFill.LEFT_THIRD
                || fill == ChatWindow.ScreenFill.CENTRE_THIRD
                || fill == ChatWindow.ScreenFill.RIGHT_THIRD) {
            return new ChatWindow.ScreenFill[] {
                    ChatWindow.ScreenFill.LEFT_THIRD,
                    ChatWindow.ScreenFill.CENTRE_THIRD,
                    ChatWindow.ScreenFill.RIGHT_THIRD};
        }
        if (fill == ChatWindow.ScreenFill.LEFT_TWO_THIRDS) {
            return new ChatWindow.ScreenFill[] {
                    ChatWindow.ScreenFill.LEFT_TWO_THIRDS,
                    ChatWindow.ScreenFill.RIGHT_THIRD};
        }
        if (fill == ChatWindow.ScreenFill.RIGHT_TWO_THIRDS) {
            return new ChatWindow.ScreenFill[] {
                    ChatWindow.ScreenFill.LEFT_THIRD,
                    ChatWindow.ScreenFill.RIGHT_TWO_THIRDS};
        }
        if (fill == ChatWindow.ScreenFill.LEFT_QUARTER
                || fill == ChatWindow.ScreenFill.CENTRE_HALF
                || fill == ChatWindow.ScreenFill.RIGHT_QUARTER) {
            return new ChatWindow.ScreenFill[] {
                    ChatWindow.ScreenFill.LEFT_QUARTER,
                    ChatWindow.ScreenFill.CENTRE_HALF,
                    ChatWindow.ScreenFill.RIGHT_QUARTER};
        }
        return null;
    }

    /**
     * A layout offered with windows already in it, as a desktop suggests
     * one: the window in hand in its first zone and one of the others in
     * each zone after it, every one drawn as its front tab's icon. Taking
     * it sends each of them to its zone at once.
     */
    static final class Suggestion {
        final ChatWindow.ScreenFill[] layout;
        /** The window for each zone of {@link #layout}, the one in hand first. */
        final String[] windows;

        Suggestion(ChatWindow.ScreenFill[] layout, String[] windows) {
            this.layout = layout;
            this.windows = windows;
        }
    }

    /**
     * The layouts suggested for the window {@code windowId} beside
     * {@code others}, the one in front first: the two halves with the
     * window in front on the right, and a half with the two in front
     * stacked on the right, each where {@code offered} holds its layout
     * and there are windows enough for it.
     */
    static List<Suggestion> suggested(List<ChatWindow.ScreenFill[]> offered,
                                      String windowId, List<String> others) {
        List<Suggestion> suggested = new ArrayList<Suggestion>(2);
        if (windowId == null || others == null) {
            return suggested;
        }
        for (ChatWindow.ScreenFill[] layout : offered) {
            boolean halves = layout.length == 2
                    && layout[0] == ChatWindow.ScreenFill.LEFT
                    && layout[1] == ChatWindow.ScreenFill.RIGHT;
            boolean stacked = layout.length == 3
                    && layout[0] == ChatWindow.ScreenFill.LEFT
                    && layout[1] == ChatWindow.ScreenFill.TOP_RIGHT;
            if ((halves || stacked) && others.size() >= layout.length - 1) {
                String[] windows = new String[layout.length];
                windows[0] = windowId;
                for (int index = 1; index < layout.length; index++) {
                    windows[index] = others.get(index - 1);
                }
                suggested.add(new Suggestion(layout, windows));
            }
        }
        return suggested;
    }

    /** As above among the windows open now, the ones snap assist would offer. */
    private static List<Suggestion> suggestedFor(
            List<ChatWindow.ScreenFill[]> offered, String windowId) {
        List<String> others = new ArrayList<String>();
        if (windowId != null) {
            for (ChatWindow window : ChatSnapAssist.otherWindows(windowId)) {
                others.add(window.getId());
            }
        }
        return suggested(offered, windowId, others);
    }

    /**
     * Whether the screen offers the three thirds, which is what makes it
     * a large screen for snapping: its top edge then snaps to the thirds
     * as well.
     */
    static boolean offersThirds(Minecraft minecraft, int screenWidth,
                                int screenHeight) {
        for (ChatWindow.ScreenFill[] layout : offered(minecraft, screenWidth,
                screenHeight)) {
            if (layout.length == 3
                    && layout[0] == ChatWindow.ScreenFill.LEFT_THIRD) {
                return true;
            }
        }
        return false;
    }

    /** How tall a layout's small screen is on a screen this shape. */
    static int thumbHeight(int screenWidth, int screenHeight) {
        if (screenWidth <= 0) {
            return MIN_THUMB_HEIGHT;
        }
        int height = (int)Math.round(THUMB_WIDTH * (double)screenHeight
                / screenWidth);
        return Math.max(MIN_THUMB_HEIGHT, Math.min(MAX_THUMB_HEIGHT, height));
    }

    /** A panel's width for {@code count} layouts laid in rows of {@code columns}. */
    static int panelWidth(int count, int columns) {
        int across = Math.max(1, Math.min(count, columns));
        return 2 * (FRAME + PADDING) + across * THUMB_WIDTH
                + (across - 1) * LAYOUT_GAP;
    }

    /** A panel's height for {@code count} layouts laid in rows of {@code columns}. */
    static int panelHeight(int count, int columns, int thumbHeight) {
        int rows = Math.max(1, (count + Math.max(1, columns) - 1)
                / Math.max(1, columns));
        return 2 * (FRAME + PADDING) + rows * thumbHeight
                + (rows - 1) * LAYOUT_GAP;
    }

    /**
     * One zone as a panel lays it out: the part of the screen, the layout
     * it is one of, and where it is drawn.
     */
    static final class Zone {
        final ChatWindow.ScreenFill fill;
        final ChatWindow.ScreenFill[] layout;
        final LostTalesUiHitBox box;
        /** The window a suggestion puts here, drawn as its icon; null in a plain layout. */
        final String windowId;
        /**
         * The zone a pointer on this one lands the window in hand in: this
         * one, or its suggestion's first.
         */
        final int target;

        Zone(ChatWindow.ScreenFill fill, ChatWindow.ScreenFill[] layout,
             LostTalesUiHitBox box, String windowId, int target) {
            this.fill = fill;
            this.layout = layout;
            this.box = box;
            this.windowId = windowId;
            this.target = target;
        }
    }

    /** A panel of layouts laid out in one place: its frame, and every zone in it. */
    static final class Panel {
        final LostTalesUiHitBox box;
        final List<Zone> zones;

        Panel(LostTalesUiHitBox box, List<Zone> zones) {
            this.box = box;
            this.zones = zones;
        }

        /**
         * Which zone is under the point, counted through the panel, or -1:
         * between two zones, on the padding, or off the panel. A part of
         * the screen offered by two layouts is two zones, and only the
         * one under the pointer lights. A suggestion answers as one: any
         * zone of it is the zone the window in hand takes.
         */
        int zoneAt(double x, double y) {
            for (int index = 0; index < this.zones.size(); index++) {
                if (this.zones.get(index).box.contains(x, y)) {
                    return this.zones.get(index).target;
                }
            }
            return -1;
        }

        /**
         * The zone standing highest under {@code x}, or -1 where no zone
         * is: what a pointer pressed against the screen's top edge above
         * the panel points at.
         */
        int zoneInColumn(double x) {
            int best = -1;
            for (int index = 0; index < this.zones.size(); index++) {
                LostTalesUiHitBox zone = this.zones.get(index).box;
                if (x >= zone.left && x < zone.right() && (best < 0
                        || zone.top < this.zones.get(best).box.top)) {
                    best = index;
                }
            }
            return best < 0 ? -1 : this.zones.get(best).target;
        }

        /** The part of the screen zone {@code index} stands for, or none for -1. */
        ChatWindow.ScreenFill fillOf(int index) {
            return index < 0 || index >= this.zones.size()
                    ? ChatWindow.ScreenFill.NONE : this.zones.get(index).fill;
        }

        /** The layout zone {@code index} is one of, or null for -1. */
        ChatWindow.ScreenFill[] layoutOf(int index) {
            return index < 0 || index >= this.zones.size() ? null
                    : this.zones.get(index).layout;
        }

        /**
         * The other windows a suggestion sends to its zones along with
         * the window in hand taking zone {@code index}, each with its
         * zone; none for a plain layout's zone.
         */
        Map<String, ChatWindow.ScreenFill> companionsOf(int index) {
            Map<String, ChatWindow.ScreenFill> companions =
                    new LinkedHashMap<String, ChatWindow.ScreenFill>();
            for (int other = 0; other < this.zones.size(); other++) {
                Zone zone = this.zones.get(other);
                if (other != index && zone.target == index
                        && zone.windowId != null) {
                    companions.put(zone.windowId, zone.fill);
                }
            }
            return companions;
        }

        /**
         * The zone an arrow walks to from zone {@code from}: the nearest
         * whose middle lies that way and whose box lines up with it
         * across the arrow — the next zone of the layout, or of the
         * layout beside, above or below — and {@code from} itself where
         * none does, so an arrow never jumps to a zone off to one side.
         * A suggestion is stood on at the zone the window in hand takes.
         */
        int step(int from, ChatSnapKeys.Direction direction) {
            if (from < 0 || from >= this.zones.size() || direction == null) {
                return this.zones.isEmpty() ? -1 : 0;
            }
            LostTalesUiHitBox at = this.zones.get(from).box;
            double fromX = at.left + at.width / 2.0D;
            double fromY = at.top + at.height / 2.0D;
            int best = from;
            double bestCost = Double.MAX_VALUE;
            boolean across = direction == ChatSnapKeys.Direction.LEFT
                    || direction == ChatSnapKeys.Direction.RIGHT;
            for (int index = 0; index < this.zones.size(); index++) {
                if (this.zones.get(index).target != index) {
                    continue;
                }
                LostTalesUiHitBox zone = this.zones.get(index).box;
                boolean linedUp = across
                        ? zone.top < at.bottom() && zone.bottom() > at.top
                        : zone.left < at.right() && zone.right() > at.left;
                if (!linedUp) {
                    continue;
                }
                double dx = zone.left + zone.width / 2.0D - fromX;
                double dy = zone.top + zone.height / 2.0D - fromY;
                double along;
                double aside;
                switch (direction) {
                    case LEFT:
                        along = -dx;
                        aside = dy;
                        break;
                    case RIGHT:
                        along = dx;
                        aside = dy;
                        break;
                    case UP:
                        along = -dy;
                        aside = dx;
                        break;
                    default:
                        along = dy;
                        aside = dx;
                        break;
                }
                if (along < 1.0D) {
                    continue;
                }
                double cost = along * along + 4.0D * aside * aside;
                if (cost < bestCost) {
                    bestCost = cost;
                    best = index;
                }
            }
            return best;
        }

        boolean contains(double x, double y) {
            return this.box.contains(x, y);
        }
    }

    /**
     * Lays {@code suggestions} and then {@code layouts} out in rows of
     * {@code columns} in a panel whose top-left corner is ({@code left},
     * {@code top}), each a small screen of the shape of one
     * {@code screenWidth} by {@code screenHeight}.
     */
    static Panel lay(List<Suggestion> suggestions,
                     List<ChatWindow.ScreenFill[]> layouts, int columns,
                     double left, double top, int screenWidth,
                     int screenHeight) {
        int across = Math.max(1, columns);
        int thumbHeight = thumbHeight(screenWidth, screenHeight);
        int count = suggestions.size() + layouts.size();
        List<Zone> zones = new ArrayList<Zone>();
        for (int index = 0; index < count; index++) {
            double thumbLeft = left + FRAME + PADDING
                    + (index % across) * (THUMB_WIDTH + LAYOUT_GAP);
            double thumbTop = top + FRAME + PADDING
                    + (index / across) * (thumbHeight + LAYOUT_GAP);
            Suggestion suggestion = index < suggestions.size()
                    ? suggestions.get(index) : null;
            ChatWindow.ScreenFill[] layout = suggestion != null
                    ? suggestion.layout
                    : layouts.get(index - suggestions.size());
            int first = zones.size();
            for (int zone = 0; zone < layout.length; zone++) {
                zones.add(new Zone(layout[zone], layout,
                        zoneBox(layout[zone], thumbLeft, thumbTop,
                                THUMB_WIDTH, thumbHeight),
                        suggestion == null ? null : suggestion.windows[zone],
                        suggestion == null ? zones.size() : first));
            }
        }
        return new Panel(new LostTalesUiHitBox(left, top,
                panelWidth(count, across),
                panelHeight(count, across, thumbHeight)),
                Collections.unmodifiableList(zones));
    }

    /**
     * Where one zone stands in a small screen: the part of it the fill
     * takes of a real screen, measured the same way, less half the gap
     * on every side it shares with a neighbour, so two zones stand
     * {@link #ZONE_GAP} apart and the outer ones reach the small
     * screen's edge.
     */
    static LostTalesUiHitBox zoneBox(ChatWindow.ScreenFill fill,
                                     double thumbLeft, double thumbTop,
                                     int thumbWidth, int thumbHeight) {
        double half = ZONE_GAP / 2.0D;
        double left = thumbLeft + fill.left(thumbWidth);
        double right = left + fill.width(thumbWidth);
        double top = thumbTop + fill.top(thumbHeight);
        double bottom = top + fill.height(thumbHeight);
        if (fill.column() > 0) {
            left += half;
        }
        if (fill.column() + fill.columns() < ChatWindow.ScreenFill.COLUMNS) {
            right -= half;
        }
        if (fill.row() > 0) {
            top += half;
        }
        if (fill.row() + fill.rows() < ChatWindow.ScreenFill.ROWS) {
            bottom -= half;
        }
        return new LostTalesUiHitBox(left, top, right - left, bottom - top);
    }

    /**
     * Draws a panel at {@code opacity}: the popups' surface and frame,
     * and each zone a block in the frame's tone with its corners
     * rounded off, zone {@code lit} in the landing's honey (-1 for none),
     * and on every zone of a suggestion its window's icon. Every pixel is
     * painted by one layer only, so the panel fades as one picture and no
     * zone lies over the surface as a second background.
     */
    static void draw(final Minecraft minecraft, final Panel panel,
                     final int lit, final float opacity) {
        final float share = Math.max(0.0F, Math.min(1.0F, opacity));
        final int alpha = Math.round(LostTalesChatVisualStyle.POPUP_ALPHA
                * share);
        if (panel == null
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        final float left = (float)panel.box.left;
        final float top = (float)panel.box.top;
        final float right = (float)panel.box.right();
        final float bottom = (float)panel.box.bottom();
        LostTalesUiFlatLayers.draw(alpha, left, top, right, bottom,
                new LostTalesUiFlatLayers.Layers() {
                    @Override
                    public void draw() {
                        LostTalesChatVisualStyle.drawPopup(left, top, right,
                                bottom, share);
                        LostTalesUiFlatLayers.nextLayer();
                        int resting = LostTalesChatVisualStyle.argb(
                                LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                                alpha);
                        int landing = LostTalesChatVisualStyle.argb(
                                LostTalesChatVisualStyle.LANDING_RGB, alpha);
                        for (int index = 0; index < panel.zones.size();
                                index++) {
                            fillRounded(panel.zones.get(index).box,
                                    index == lit ? landing : resting);
                        }
                        LostTalesUiFlatLayers.nextLayer();
                        LostTalesChatVisualStyle.beginContent();
                        for (Zone zone : panel.zones) {
                            if (zone.windowId != null) {
                                drawWindowIcon(minecraft, zone, alpha);
                            }
                        }
                    }
                });
    }

    /**
     * A suggested window's front tab icon in the middle of its zone, by
     * the one centring rule and on the display's grid; left out where the
     * zone cannot hold it and its shadow.
     */
    private static void drawWindowIcon(Minecraft minecraft, Zone zone,
                                       int alpha) {
        ChatWindow window = ChatWindowLayout.window(zone.windowId);
        ChatTab front = window == null ? null : ChatWindowFrame.activeTab(
                window, ChatWindowFrame.visibleTabs(window));
        int size = LostTalesUiInk.ICON_SIZE;
        int width = (int)Math.floor(zone.box.width);
        int height = (int)Math.floor(zone.box.height);
        if (front == null || width < size + LostTalesUiInk.SHADOW_OFFSET
                || height < size + LostTalesUiInk.SHADOW_OFFSET) {
            return;
        }
        ChatChannelIcons.draw(minecraft, front,
                (float)ChatWindowFrame.snapToDisplayPixels(zone.box.left
                        + LostTalesUiInk.centredStart(width, size)),
                (float)ChatWindowFrame.snapToDisplayPixels(zone.box.top
                        + LostTalesUiInk.centredStart(height, size)),
                alpha);
    }

    /** A block with its four corner pixels left out. */
    private static void fillRounded(LostTalesUiHitBox box, int argb) {
        float left = (float)box.left;
        float top = (float)box.top;
        float right = (float)box.right();
        float bottom = (float)box.bottom();
        if (right - left < 3.0F || bottom - top < 3.0F) {
            LostTalesChatOverlayRenderer.fillRect(left, top, right, bottom,
                    argb);
            return;
        }
        LostTalesChatOverlayRenderer.fillRect(left + 1.0F, top, right - 1.0F,
                top + 1.0F, argb);
        LostTalesChatOverlayRenderer.fillRect(left, top + 1.0F, right,
                bottom - 1.0F, argb);
        LostTalesChatOverlayRenderer.fillRect(left + 1.0F, bottom - 1.0F,
                right - 1.0F, bottom, argb);
    }

    /**
     * The snap bar: every layout the screen has room for in one row at
     * the top of the screen, dropping in while a window is carried within
     * reach of it and going back up when the pointer leaves or the carry
     * ends. The zone under the pointer is where the window lands; the bar
     * around the zones lands it nowhere, and keeps the top edge's own
     * snap off while the pointer is on it. The bar reaches up to the
     * screen's top edge: a pointer pressed against the edge above it
     * points at the zone standing below it, so a window thrown at the top
     * of the screen over the bar lands in a zone there, as on the
     * desktop, and the edge only fills the whole screen beside the bar.
     * The suggestions for the carried window lead it.
     */
    static final class Bar {
        /** How far below the bar the pointer brings it down. */
        static final int REACH = 24;
        /** The bar's clearing from the top of the screen. */
        static final int TOP_MARGIN = 2;

        private final MotionTransition shown =
                new MotionTransition(MotionIds.CHAT_SNAP_LAYOUTS, true);
        private boolean wanted;
        private Panel panel;
        /** The zone the pointer was last on, or -1. */
        private int lit = -1;
        /** The window being carried, whose suggestions lead the bar. */
        private String windowId;

        Bar() {
            // Up from the start, so the bar drops in the first time too.
            this.shown.settle(false);
        }

        /**
         * One frame of the carry of the window {@code windowId} with the
         * pointer at ({@code x}, {@code y}): the bar shows while the
         * pointer is within reach of it. Answers the part of the screen
         * whose zone is under the pointer, none on the bar between zones,
         * and null off the bar.
         */
        ChatWindow.ScreenFill follow(Minecraft minecraft, String windowId,
                                     double x, double y, int screenWidth,
                                     int screenHeight) {
            this.windowId = windowId;
            List<ChatWindow.ScreenFill[]> layouts = offered(minecraft,
                    screenWidth, screenHeight);
            List<Suggestion> suggestions = suggestedFor(layouts, windowId);
            int count = suggestions.size() + layouts.size();
            int height = panelHeight(count, count,
                    thumbHeight(screenWidth, screenHeight));
            this.wanted = !layouts.isEmpty()
                    && y < TOP_MARGIN + height + REACH;
            place(suggestions, layouts, screenWidth, screenHeight);
            // Above where the bar settles, even while it is still
            // dropping in, so a pointer held at the edge never lights a
            // lower zone on the way.
            boolean above = this.wanted && this.panel != null
                    && y < Math.max(this.panel.box.top, TOP_MARGIN)
                    && x >= this.panel.box.left
                    && x < this.panel.box.right();
            boolean onBar = above || (this.wanted && this.panel != null
                    && this.panel.contains(x, y));
            this.lit = !onBar ? -1 : above ? this.panel.zoneInColumn(x)
                    : this.panel.zoneAt(x, y);
            return onBar ? this.panel.fillOf(this.lit) : null;
        }

        /** The layout the zone under the pointer is one of, or null. */
        ChatWindow.ScreenFill[] litLayout() {
            return this.panel == null ? null : this.panel.layoutOf(this.lit);
        }

        /** The other windows the suggestion under the pointer sends with the carried one. */
        Map<String, ChatWindow.ScreenFill> litCompanions() {
            return this.panel == null
                    ? Collections.<String, ChatWindow.ScreenFill>emptyMap()
                    : this.panel.companionsOf(this.lit);
        }

        /** The carry is over: the bar goes back up. */
        void hide() {
            this.wanted = false;
            this.lit = -1;
        }

        /** Draws the bar where its motion has brought it, at {@code opacity}. */
        void draw(Minecraft minecraft, int screenWidth, int screenHeight,
                  float opacity) {
            if (!this.wanted && this.panel == null) {
                return;
            }
            List<ChatWindow.ScreenFill[]> layouts = offered(minecraft,
                    screenWidth, screenHeight);
            place(suggestedFor(layouts, this.windowId), layouts, screenWidth,
                    screenHeight);
            if (this.panel != null) {
                ChatSnapLayouts.draw(minecraft, this.panel, this.lit,
                        opacity * this.shown.clamped());
            }
        }

        /**
         * Moves the bar's motion on to this instant and lays it out
         * there: centred, dropping from above the screen to its margin.
         * Nothing is laid out once it is all the way up.
         */
        private void place(List<Suggestion> suggestions,
                           List<ChatWindow.ScreenFill[]> layouts,
                           int screenWidth, int screenHeight) {
            float share = this.shown.advance(System.nanoTime(),
                    this.wanted && !layouts.isEmpty());
            if (layouts.isEmpty() || (!this.wanted && share <= 0.0F)) {
                this.panel = null;
                return;
            }
            int count = suggestions.size() + layouts.size();
            int width = panelWidth(count, count);
            int height = panelHeight(count, count,
                    thumbHeight(screenWidth, screenHeight));
            double top = TOP_MARGIN - (1.0D - share) * (TOP_MARGIN + height);
            this.panel = lay(suggestions, layouts, count,
                    Math.floor((screenWidth - width) / 2.0D), top,
                    screenWidth, screenHeight);
        }
    }

    /**
     * The layouts in a panel hanging from a window's fullscreen control:
     * opened by the pointer resting on the control, kept while the
     * pointer is on the control, on the panel or crossing between them,
     * and put away a moment after it leaves all three. A press on a zone
     * lets the window fill that part of the screen; the suggestions for
     * the window lead the panel.
     */
    static final class Flyout {
        /** How long the pointer rests on the control before the panel opens. */
        static final long OPEN_NANOS = 400L * 1000000L;
        /** How long the pointer may be away before the panel goes. */
        static final long CLOSE_NANOS = 200L * 1000000L;
        /** Layouts in a row of the panel. */
        static final int COLUMNS = 3;

        private final MotionTransition shown =
                new MotionTransition(MotionIds.CHAT_SNAP_LAYOUTS);
        /** The window the panel belongs to while it is open or going. */
        private String windowId;
        private boolean open;
        private Panel panel;
        /** The control the pointer is resting on, and since when. */
        private String restingOn;
        private long restingSince;
        /** When the pointer last left the control and the panel; 0 while on them. */
        private long awaySince;
        /**
         * Whether the panel was opened from the keyboard, and the zone
         * its arrows stand on: it stays open wherever the pointer goes
         * until a zone is taken or it is put away.
         */
        private boolean keyboard;
        private int keyZone = -1;

        Flyout() {
            // Closed from the start, so the panel fades in the first time too.
            this.shown.settle(false);
        }

        /** Whether the panel is open for a window: it answers the pointer. */
        boolean isOpen() {
            return this.open && this.panel != null;
        }

        /** Whether any of the panel shows, opening, open or going. */
        boolean isShown() {
            return this.panel != null && this.shown.clamped() > 0.0F;
        }

        /** The window the panel belongs to, or null. */
        String windowId() {
            return this.windowId;
        }

        /** Whether the open panel is under the point. */
        boolean contains(double x, double y) {
            return isOpen() && this.panel.contains(x, y);
        }

        /** Which zone of the open panel is under the point, or -1. */
        int zoneAt(double x, double y) {
            return isOpen() ? this.panel.zoneAt(x, y) : -1;
        }

        /** The part of the screen zone {@code index} of the open panel stands for. */
        ChatWindow.ScreenFill fillOf(int index) {
            return isOpen() ? this.panel.fillOf(index)
                    : ChatWindow.ScreenFill.NONE;
        }

        /** The layout zone {@code index} of the open panel is one of, or null. */
        ChatWindow.ScreenFill[] layoutOf(int index) {
            return isOpen() ? this.panel.layoutOf(index) : null;
        }

        /** The other windows a suggestion sends along with the window taking zone {@code index}. */
        Map<String, ChatWindow.ScreenFill> companionsOf(int index) {
            return isOpen() ? this.panel.companionsOf(index)
                    : Collections.<String, ChatWindow.ScreenFill>emptyMap();
        }

        /**
         * One frame of the pointer. {@code controlWindow} is the window
         * whose fullscreen control the pointer is on, with {@code control}
         * that control's box, or null; {@code onPanel} whether it is on
         * the open panel; {@code allowed} false while something else owns
         * the screen — a menu, a drag — which puts the panel away.
         */
        void follow(ChatWindow controlWindow, LostTalesUiHitBox control,
                    ChatWindowFrame frame, boolean onPanel, boolean allowed,
                    Minecraft minecraft, int screenWidth, int screenHeight) {
            long now = System.nanoTime();
            if (!allowed) {
                close();
                this.restingOn = null;
                return;
            }
            if (this.keyboard && this.open) {
                return;
            }
            String id = controlWindow == null || control == null ? null
                    : controlWindow.getId();
            if (id == null) {
                this.restingOn = null;
            } else if (!id.equals(this.restingOn)) {
                this.restingOn = id;
                this.restingSince = now;
            }
            if (this.open) {
                boolean near = onPanel
                        || (id != null && id.equals(this.windowId));
                if (near) {
                    this.awaySince = 0L;
                } else if (this.awaySince == 0L) {
                    this.awaySince = now;
                } else if (now - this.awaySince >= CLOSE_NANOS) {
                    close();
                }
            }
            if (!this.open && id != null
                    && now - this.restingSince >= OPEN_NANOS) {
                List<ChatWindow.ScreenFill[]> layouts = offered(minecraft,
                        screenWidth, screenHeight);
                if (!layouts.isEmpty()) {
                    this.windowId = id;
                    this.open = true;
                    this.awaySince = 0L;
                    this.panel = hangFrom(suggestedFor(layouts, id), layouts,
                            control, frame, screenWidth, screenHeight);
                }
            }
        }

        /** Puts the panel away: it fades where it stands. */
        void close() {
            this.open = false;
            this.awaySince = 0L;
            this.keyboard = false;
            this.keyZone = -1;
        }

        /**
         * Opens the panel from the keyboard for a window, hanging from
         * its fullscreen control's box {@code control}, its first zone
         * under the arrows; answers whether it opened.
         */
        boolean openFromKeyboard(ChatWindow window, LostTalesUiHitBox control,
                                 ChatWindowFrame frame, Minecraft minecraft,
                                 int screenWidth, int screenHeight) {
            List<ChatWindow.ScreenFill[]> layouts = offered(minecraft,
                    screenWidth, screenHeight);
            if (window == null || control == null || layouts.isEmpty()) {
                return false;
            }
            this.windowId = window.getId();
            this.open = true;
            this.keyboard = true;
            this.keyZone = 0;
            this.awaySince = 0L;
            this.panel = hangFrom(suggestedFor(layouts, window.getId()),
                    layouts, control, frame, screenWidth, screenHeight);
            return true;
        }

        /** Whether the panel was opened from the keyboard and is open. */
        boolean isKeyboardOpen() {
            return this.keyboard && isOpen();
        }

        /** The zone the arrows stand on, or -1. */
        int keyZone() {
            return isKeyboardOpen() ? this.keyZone : -1;
        }

        /** Walks the arrows' zone one step. */
        void step(ChatSnapKeys.Direction direction) {
            if (isKeyboardOpen()) {
                this.keyZone = this.panel.step(this.keyZone, direction);
            }
        }

        /**
         * Draws the panel at {@code opacity}, zone {@code lit} lit (-1 for
         * none), and keeps its rectangle in {@code regions} while it is
         * open.
         */
        void draw(Minecraft minecraft, ChatPointerRegions regions, int lit,
                  float opacity) {
            float share = this.shown.advance(System.nanoTime(), this.open);
            if (this.panel == null) {
                return;
            }
            if (!this.open && share <= 0.0F) {
                this.panel = null;
                this.windowId = null;
                return;
            }
            ChatSnapLayouts.draw(minecraft, this.panel,
                    this.open ? lit : -1, opacity * share);
            if (this.open && regions != null) {
                regions.addScreen((int)Math.floor(this.panel.box.left),
                        (int)Math.floor(this.panel.box.top),
                        (int)Math.ceil(this.panel.box.right()),
                        (int)Math.ceil(this.panel.box.bottom()));
            }
        }

        /**
         * The panel laid out from the control it hangs from, the way a
         * menu opens from its button: toward the middle of the window,
         * turned round only where that side of the screen has less room
         * than the other, and kept on the screen.
         */
        static Panel hangFrom(List<Suggestion> suggestions,
                              List<ChatWindow.ScreenFill[]> layouts,
                              LostTalesUiHitBox control,
                              ChatWindowFrame frame, int screenWidth,
                              int screenHeight) {
            int count = suggestions.size() + layouts.size();
            int width = panelWidth(count, COLUMNS);
            int height = panelHeight(count, COLUMNS,
                    thumbHeight(screenWidth, screenHeight));
            ChatPopupMenu.Anchor anchor = ChatPopupMenu.Anchor.inward(
                    (int)Math.floor(control.left),
                    (int)Math.floor(control.top),
                    (int)Math.ceil(control.right()),
                    (int)Math.ceil(control.bottom()), frame, screenWidth,
                    screenHeight);
            int gap = ChatPopupMenu.Anchor.GAP;
            int roomBelow = screenHeight - (anchor.bottom + gap);
            int roomAbove = anchor.top - gap;
            boolean below = anchor.below;
            if ((below ? roomBelow : roomAbove) < height
                    && (below ? roomAbove : roomBelow)
                            > (below ? roomBelow : roomAbove)) {
                below = !below;
            }
            int x = anchor.fromRight ? anchor.right - width : anchor.left;
            x = Math.max(0, Math.min(screenWidth - width, x));
            int y = below ? anchor.bottom + gap : anchor.top - gap - height;
            y = Math.max(0, Math.min(screenHeight - height, y));
            return lay(suggestions, layouts, COLUMNS, x, y, screenWidth,
                    screenHeight);
        }
    }
}
