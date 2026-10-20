package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
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
 * of the screen a window can fill. The Snap Layouts sub-window holds
 * them ({@link SnapLayoutsWindow}); a press on a zone sends the window
 * there. A layout is offered only while every one of its zones holds the
 * narrowest window there may be, as a desktop offers fewer layouts on a
 * smaller screen.
 *
 * <p>Ahead of the plain layouts stand the suggested ones, as a desktop
 * suggests them: the halves and the half with two quarters, already
 * holding the other windows in front, each drawn as its front tab's
 * icon ({@link Suggestion}). Anywhere on a suggestion lands the window in
 * hand in its zone and sends the others to theirs with it.</p>
 */
public final class SnapLayouts {
    /** Every layout, in the order it is offered. */
    private static final Window.ScreenFill[][] LAYOUTS = {
            {Window.ScreenFill.LEFT, Window.ScreenFill.RIGHT},
            {Window.ScreenFill.LEFT_TWO_THIRDS,
                    Window.ScreenFill.RIGHT_THIRD},
            {Window.ScreenFill.LEFT, Window.ScreenFill.TOP_RIGHT,
                    Window.ScreenFill.BOTTOM_RIGHT},
            {Window.ScreenFill.TOP_LEFT, Window.ScreenFill.TOP_RIGHT,
                    Window.ScreenFill.BOTTOM_LEFT,
                    Window.ScreenFill.BOTTOM_RIGHT},
            {Window.ScreenFill.LEFT_THIRD,
                    Window.ScreenFill.CENTRE_THIRD,
                    Window.ScreenFill.RIGHT_THIRD},
            {Window.ScreenFill.LEFT_QUARTER,
                    Window.ScreenFill.CENTRE_HALF,
                    Window.ScreenFill.RIGHT_QUARTER}};
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
    /** Clear pixels between the panel's edge and its layouts. */
    static final int PADDING = 4;

    private SnapLayouts() {}

    /**
     * The layouts a screen this size has room for: every zone of each at
     * least {@code minWidth} wide and {@code minHeight} tall inside the
     * margin a window keeps in it, as {@link WindowPlacement#fillBounds}
     * lays the window there.
     */
    static List<Window.ScreenFill[]> offered(int screenWidth,
                                                 int screenHeight,
                                                 double minWidth,
                                                 double minHeight) {
        List<Window.ScreenFill[]> offered =
                new ArrayList<Window.ScreenFill[]>(LAYOUTS.length);
        int margin = 2 * WindowPlacement.EDGE_MARGIN;
        for (Window.ScreenFill[] layout : LAYOUTS) {
            boolean fits = true;
            for (Window.ScreenFill zone : layout) {
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

    /** As above for the running game: the narrowest window, one line tall. */
    static List<Window.ScreenFill[]> offered(Minecraft minecraft,
                                                 int screenWidth,
                                                 int screenHeight) {
        return offered(screenWidth, screenHeight,
                WindowPlacement.MIN_BOX_WIDTH,
                WindowPlacement.minHeight(minecraft));
    }

    /**
     * The layout a window just sent to {@code fill} shares the screen by:
     * the zones snap assist offers the other windows. None for the whole
     * screen, its own box or a part the player shaped.
     */
    public static Window.ScreenFill[] layoutFor(Window.ScreenFill fill) {
        if (fill == Window.ScreenFill.LEFT
                || fill == Window.ScreenFill.RIGHT) {
            return new Window.ScreenFill[] {Window.ScreenFill.LEFT,
                    Window.ScreenFill.RIGHT};
        }
        if (fill == Window.ScreenFill.TOP_LEFT
                || fill == Window.ScreenFill.TOP_RIGHT
                || fill == Window.ScreenFill.BOTTOM_LEFT
                || fill == Window.ScreenFill.BOTTOM_RIGHT) {
            return new Window.ScreenFill[] {Window.ScreenFill.TOP_LEFT,
                    Window.ScreenFill.TOP_RIGHT,
                    Window.ScreenFill.BOTTOM_LEFT,
                    Window.ScreenFill.BOTTOM_RIGHT};
        }
        if (fill == Window.ScreenFill.LEFT_THIRD
                || fill == Window.ScreenFill.CENTRE_THIRD
                || fill == Window.ScreenFill.RIGHT_THIRD) {
            return new Window.ScreenFill[] {
                    Window.ScreenFill.LEFT_THIRD,
                    Window.ScreenFill.CENTRE_THIRD,
                    Window.ScreenFill.RIGHT_THIRD};
        }
        if (fill == Window.ScreenFill.LEFT_TWO_THIRDS) {
            return new Window.ScreenFill[] {
                    Window.ScreenFill.LEFT_TWO_THIRDS,
                    Window.ScreenFill.RIGHT_THIRD};
        }
        if (fill == Window.ScreenFill.RIGHT_TWO_THIRDS) {
            return new Window.ScreenFill[] {
                    Window.ScreenFill.LEFT_THIRD,
                    Window.ScreenFill.RIGHT_TWO_THIRDS};
        }
        if (fill == Window.ScreenFill.LEFT_QUARTER
                || fill == Window.ScreenFill.CENTRE_HALF
                || fill == Window.ScreenFill.RIGHT_QUARTER) {
            return new Window.ScreenFill[] {
                    Window.ScreenFill.LEFT_QUARTER,
                    Window.ScreenFill.CENTRE_HALF,
                    Window.ScreenFill.RIGHT_QUARTER};
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
        final Window.ScreenFill[] layout;
        /** The window for each zone of {@link #layout}, the one in hand first. */
        final String[] windows;

        Suggestion(Window.ScreenFill[] layout, String[] windows) {
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
    static List<Suggestion> suggested(List<Window.ScreenFill[]> offered,
                                      String windowId, List<String> others) {
        List<Suggestion> suggested = new ArrayList<Suggestion>(2);
        if (windowId == null || others == null) {
            return suggested;
        }
        for (Window.ScreenFill[] layout : offered) {
            boolean halves = layout.length == 2
                    && layout[0] == Window.ScreenFill.LEFT
                    && layout[1] == Window.ScreenFill.RIGHT;
            boolean stacked = layout.length == 3
                    && layout[0] == Window.ScreenFill.LEFT
                    && layout[1] == Window.ScreenFill.TOP_RIGHT;
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
    static List<Suggestion> suggestedFor(
            List<Window.ScreenFill[]> offered, String windowId) {
        List<String> others = new ArrayList<String>();
        if (windowId != null) {
            for (Window window : SnapAssist.otherWindows(windowId)) {
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
        for (Window.ScreenFill[] layout : offered(minecraft, screenWidth,
                screenHeight)) {
            if (layout.length == 3
                    && layout[0] == Window.ScreenFill.LEFT_THIRD) {
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
        return 2 * PADDING + across * THUMB_WIDTH
                + (across - 1) * LAYOUT_GAP;
    }

    /** A panel's height for {@code count} layouts laid in rows of {@code columns}. */
    static int panelHeight(int count, int columns, int thumbHeight) {
        int rows = Math.max(1, (count + Math.max(1, columns) - 1)
                / Math.max(1, columns));
        return 2 * PADDING + rows * thumbHeight
                + (rows - 1) * LAYOUT_GAP;
    }

    /**
     * One zone as a panel lays it out: the part of the screen, the layout
     * it is one of, and where it is drawn.
     */
    static final class Zone {
        final Window.ScreenFill fill;
        final Window.ScreenFill[] layout;
        final LostTalesUiHitBox box;
        /** The window a suggestion puts here, drawn as its icon; null in a plain layout. */
        final String windowId;
        /**
         * The zone a pointer on this one lands the window in hand in: this
         * one, or its suggestion's first.
         */
        final int target;

        Zone(Window.ScreenFill fill, Window.ScreenFill[] layout,
             LostTalesUiHitBox box, String windowId, int target) {
            this.fill = fill;
            this.layout = layout;
            this.box = box;
            this.windowId = windowId;
            this.target = target;
        }
    }

    /** A panel of layouts laid out in one place: every zone in it. */
    static final class Panel {
        final List<Zone> zones;

        Panel(List<Zone> zones) {
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

        /** The part of the screen zone {@code index} stands for, or none for -1. */
        Window.ScreenFill fillOf(int index) {
            return index < 0 || index >= this.zones.size()
                    ? Window.ScreenFill.NONE : this.zones.get(index).fill;
        }

        /** The layout zone {@code index} is one of, or null for -1. */
        Window.ScreenFill[] layoutOf(int index) {
            return index < 0 || index >= this.zones.size() ? null
                    : this.zones.get(index).layout;
        }

        /**
         * The other windows a suggestion sends to its zones along with
         * the window in hand taking zone {@code index}, each with its
         * zone; none for a plain layout's zone.
         */
        Map<String, Window.ScreenFill> companionsOf(int index) {
            Map<String, Window.ScreenFill> companions =
                    new LinkedHashMap<String, Window.ScreenFill>();
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
        int step(int from, SnapKeys.Direction direction) {
            if (from < 0 || from >= this.zones.size() || direction == null) {
                return this.zones.isEmpty() ? -1 : 0;
            }
            LostTalesUiHitBox at = this.zones.get(from).box;
            double fromX = at.left + at.width / 2.0D;
            double fromY = at.top + at.height / 2.0D;
            int best = from;
            double bestCost = Double.MAX_VALUE;
            boolean across = direction == SnapKeys.Direction.LEFT
                    || direction == SnapKeys.Direction.RIGHT;
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
    }

    /**
     * Lays {@code suggestions} and then {@code layouts} out in rows of
     * {@code columns} in a panel whose top-left corner is ({@code left},
     * {@code top}), {@link #PADDING} in from its edges, each a small
     * screen of the shape of one {@code screenWidth} by
     * {@code screenHeight}.
     */
    static Panel lay(List<Suggestion> suggestions,
                     List<Window.ScreenFill[]> layouts, int columns,
                     double left, double top, int screenWidth,
                     int screenHeight) {
        int across = Math.max(1, columns);
        int thumbHeight = thumbHeight(screenWidth, screenHeight);
        int count = suggestions.size() + layouts.size();
        List<Zone> zones = new ArrayList<Zone>();
        for (int index = 0; index < count; index++) {
            double thumbLeft = left + PADDING
                    + (index % across) * (THUMB_WIDTH + LAYOUT_GAP);
            double thumbTop = top + PADDING
                    + (index / across) * (thumbHeight + LAYOUT_GAP);
            Suggestion suggestion = index < suggestions.size()
                    ? suggestions.get(index) : null;
            Window.ScreenFill[] layout = suggestion != null
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
        return new Panel(Collections.unmodifiableList(zones));
    }

    /**
     * Where one zone stands in a small screen: the part of it the fill
     * takes of a real screen, measured the same way, less half the gap
     * on every side it shares with a neighbour, so two zones stand
     * {@link #ZONE_GAP} apart and the outer ones reach the small
     * screen's edge.
     */
    static LostTalesUiHitBox zoneBox(Window.ScreenFill fill,
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
        if (fill.column() + fill.columns() < Window.ScreenFill.COLUMNS) {
            right -= half;
        }
        if (fill.row() > 0) {
            top += half;
        }
        if (fill.row() + fill.rows() < Window.ScreenFill.ROWS) {
            bottom -= half;
        }
        return new LostTalesUiHitBox(left, top, right - left, bottom - top);
    }

    /**
     * A zone's colour on the theme's primary surface: the secondary, as a
     * strip or a lit row wears it, and the zone under the pointer or the
     * arrows two thirds of the way to the accent.
     */
    static int zoneRgb(boolean lit) {
        return lit ? LostTalesUiInk.blend(LostTalesUiTheme.primaryRgb(),
                LostTalesUiTheme.accentRgb(), 2.0F / 3.0F)
                : LostTalesUiTheme.secondaryRgb();
    }

    /**
     * Draws a panel's zones on the surface of the sub-window holding it,
     * at {@code alpha}: each zone the surface recoloured in place
     * ({@link #zoneRgb}), zone {@code lit} lit (-1 for none), and on every
     * zone of a suggestion its window's icon. The surface is recoloured
     * rather than covered, so no zone lies over it as a second background.
     */
    static void drawZones(Minecraft minecraft, Panel panel, int lit,
                          int alpha, int surfaceAlpha) {
        if (panel == null || minecraft == null) {
            return;
        }
        int surface = LostTalesUiTheme.primaryRgb();
        int resting = zoneRgb(false);
        int landing = zoneRgb(true);
        for (int index = 0; index < panel.zones.size(); index++) {
            recolourRounded(panel.zones.get(index).box, surfaceAlpha, surface,
                    index == lit ? landing : resting);
        }
        LostTalesUiInk.beginContent();
        for (Zone zone : panel.zones) {
            if (zone.windowId != null) {
                drawWindowIcon(minecraft, zone, alpha);
            }
        }
    }

    /** A block of a surface recoloured in place, its four corner pixels left out. */
    private static void recolourRounded(LostTalesUiHitBox box, int alpha,
                                        int fromRgb, int toRgb) {
        float left = (float)box.left;
        float top = (float)box.top;
        float right = (float)box.right();
        float bottom = (float)box.bottom();
        if (right - left < 3.0F || bottom - top < 3.0F) {
            WindowStyle.recolourFlat(left, top, right, bottom, alpha, fromRgb,
                    toRgb);
            return;
        }
        WindowStyle.recolourFlat(left + 1.0F, top, right - 1.0F, top + 1.0F,
                alpha, fromRgb, toRgb);
        WindowStyle.recolourFlat(left, top + 1.0F, right, bottom - 1.0F,
                alpha, fromRgb, toRgb);
        WindowStyle.recolourFlat(left + 1.0F, bottom - 1.0F, right - 1.0F,
                bottom, alpha, fromRgb, toRgb);
    }

    /**
     * A suggested window's front tab icon in the middle of its zone, by
     * the one centring rule and on the display's grid; left out where the
     * zone cannot hold it and its shadow.
     */
    private static void drawWindowIcon(Minecraft minecraft, Zone zone,
                                       int alpha) {
        Window window = WindowLayout.window(zone.windowId);
        WindowPage front = window == null ? null : WindowFrame.activeTab(
                window, WindowFrame.visibleTabs(window));
        int size = LostTalesUiInk.ICON_SIZE;
        int width = (int)Math.floor(zone.box.width);
        int height = (int)Math.floor(zone.box.height);
        if (front == null || width < size + LostTalesUiInk.SHADOW_OFFSET
                || height < size + LostTalesUiInk.SHADOW_OFFSET) {
            return;
        }
        front.drawIcon(minecraft,
                (float)LostTalesDisplayPixels.snap(zone.box.left
                        + LostTalesUiInk.centredStart(width, size)),
                (float)LostTalesDisplayPixels.snap(zone.box.top
                        + LostTalesUiInk.centredStart(height, size)),
                alpha, TabMark.NONE);
    }
}
