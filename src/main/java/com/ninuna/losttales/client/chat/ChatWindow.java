package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One chat window in the client layout: an ordered row of tabs, the tab
 * currently in front, a lock, a position, how many message lines it may
 * show, whether it fills the screen, and optionally a link to another
 * window it sits directly above or below, keeping its gap as that window
 * grows, shrinks or moves. Instances are owned and mutated only by
 * {@link ChatWindowLayout}; everyone else reads them.
 */
public final class ChatWindow {
    private final String id;
    private final List<ChatTab> tabs = new ArrayList<ChatTab>();
    private ChatTab activeTab;
    private boolean locked;
    /** Percent of the available screen travel, see HudPlacementLayout. */
    private double offsetX;
    private double offsetY;
    /**
     * Message lines the window may show, fractional so its height is
     * continuous in pixels; 0 follows the game's setting.
     */
    private double maxLines;
    /** Chat width the window is drawn and wrapped at; 0 follows the game. */
    private int width;
    /** Id of the window this one is linked to, or null. */
    private String linkTarget;
    /** Which side of its target this window sits on. */
    private LinkSide linkSide = LinkSide.BELOW;
    /**
     * The part of the screen the window fills, or none while it stands
     * in its own box. Its own position and size stay as they were
     * throughout, and are what it goes back to when it lets the screen
     * go.
     */
    private ScreenFill fill = ScreenFill.NONE;
    /**
     * Whether the window's timestamp area is driven out, so the words
     * stand at the window's edge with neither avatars nor hover times.
     */
    private boolean areaHidden;
    /** Whether the window's member list is put away. */
    private boolean membersHidden;
    /**
     * How wide the player made the window's member list, in the chat's
     * pixels and fractions of one, so its edge follows the pointer
     * smoothly; 0 while it keeps the list's own width.
     */
    private double membersWidth;

    /**
     * A part of the screen a window can fill, as a desktop window snaps:
     * the whole screen from the top edge, a half from a side, a quarter
     * from a corner, and the zones of the snap layouts — thirds, two
     * thirds, and a half between two quarter columns. Each named part is
     * a box on a grid twelve columns across and two rows down: its first
     * column and row, and how many of each it spans.
     *
     * <p>A part can also be free: its edges anywhere, in shares of the
     * screen. A window filling a part has edges of its own there once
     * the player drags one — two windows sharing an edge move it
     * together — and a window stretched to the screen's full height in
     * its own column fills one too. There is one instance of each named
     * part, so they compare by identity; free parts compare by their
     * edges.</p>
     */
    public static final class ScreenFill {
        /** The grid the named parts are laid on: columns across, rows down. */
        public static final int COLUMNS = 12;
        public static final int ROWS = 2;
        private static final List<ScreenFill> NAMED = new ArrayList<ScreenFill>();
        /** How a free part is written: its four edges after this. */
        private static final String FREE_PREFIX = "free:";

        public static final ScreenFill NONE = named("none", 0, 0, 0, 0);
        public static final ScreenFill FULL = named("full", 0, 0, 12, 2);
        public static final ScreenFill LEFT = named("left", 0, 0, 6, 2);
        public static final ScreenFill RIGHT = named("right", 6, 0, 6, 2);
        public static final ScreenFill TOP_LEFT = named("top_left", 0, 0, 6, 1);
        public static final ScreenFill TOP_RIGHT = named("top_right", 6, 0, 6, 1);
        public static final ScreenFill BOTTOM_LEFT =
                named("bottom_left", 0, 1, 6, 1);
        public static final ScreenFill BOTTOM_RIGHT =
                named("bottom_right", 6, 1, 6, 1);
        public static final ScreenFill LEFT_TWO_THIRDS =
                named("left_two_thirds", 0, 0, 8, 2);
        public static final ScreenFill RIGHT_TWO_THIRDS =
                named("right_two_thirds", 4, 0, 8, 2);
        public static final ScreenFill LEFT_THIRD =
                named("left_third", 0, 0, 4, 2);
        public static final ScreenFill CENTRE_THIRD =
                named("centre_third", 4, 0, 4, 2);
        public static final ScreenFill RIGHT_THIRD =
                named("right_third", 8, 0, 4, 2);
        public static final ScreenFill LEFT_QUARTER =
                named("left_quarter", 0, 0, 3, 2);
        public static final ScreenFill CENTRE_HALF =
                named("centre_half", 3, 0, 6, 2);
        public static final ScreenFill RIGHT_QUARTER =
                named("right_quarter", 9, 0, 3, 2);

        /** The named part's id; empty for a free one. */
        private final String id;
        private final int column;
        private final int row;
        private final int columns;
        private final int rows;
        /** The edges, in shares of the screen's width and height. */
        private final double left;
        private final double top;
        private final double right;
        private final double bottom;

        private ScreenFill(String id, int column, int row, int columns,
                           int rows, double left, double top, double right,
                           double bottom) {
            this.id = id;
            this.column = column;
            this.row = row;
            this.columns = columns;
            this.rows = rows;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private static ScreenFill named(String id, int column, int row,
                                        int columns, int rows) {
            ScreenFill fill = new ScreenFill(id, column, row, columns, rows,
                    column / (double)COLUMNS, row / (double)ROWS,
                    (column + columns) / (double)COLUMNS,
                    (row + rows) / (double)ROWS);
            NAMED.add(fill);
            return fill;
        }

        /**
         * A free part with its edges where they are asked for, in shares
         * of the screen, kept on it and in order.
         */
        public static ScreenFill free(double left, double top, double right,
                                      double bottom) {
            double l = clampShare(Math.min(left, right));
            double r = clampShare(Math.max(left, right));
            double t = clampShare(Math.min(top, bottom));
            double b = clampShare(Math.max(top, bottom));
            return new ScreenFill("", (int)Math.round(l * COLUMNS),
                    (int)Math.round(t * ROWS),
                    (int)Math.round((r - l) * COLUMNS),
                    (int)Math.round((b - t) * ROWS), l, t, r, b);
        }

        private static double clampShare(double share) {
            return Double.isNaN(share) ? 0.0D
                    : Math.max(0.0D, Math.min(1.0D, share));
        }

        /** Whether the part's edges are the player's rather than a named part's. */
        public boolean isFree() { return this.id.length() == 0; }

        /** What the layout file calls the part. */
        public String id() {
            if (!isFree()) {
                return this.id;
            }
            return FREE_PREFIX + share(this.left) + "," + share(this.top)
                    + "," + share(this.right) + "," + share(this.bottom);
        }

        private static String share(double value) {
            return String.format(java.util.Locale.ROOT, "%.5f", value);
        }

        /** The part's first column on the grid; a free part's nearest. */
        public int column() { return this.column; }

        /** The part's first row on the grid; a free part's nearest. */
        public int row() { return this.row; }

        /** How many of the grid's columns the part spans; a free part's nearest. */
        public int columns() { return this.columns; }

        /** How many of the grid's rows the part spans; a free part's nearest. */
        public int rows() { return this.rows; }

        /** The part's edges, in shares of the screen. */
        public double leftShare() { return this.left; }
        public double topShare() { return this.top; }
        public double rightShare() { return this.right; }
        public double bottomShare() { return this.bottom; }

        /** The part's left edge on a screen {@code screenWidth} wide. */
        public int left(int screenWidth) {
            return isFree() ? shareEdge(this.left, screenWidth)
                    : edge(this.column, COLUMNS, screenWidth);
        }

        /**
         * The part's width on a screen {@code screenWidth} wide. Two
         * parts side by side share the edge between them to the pixel.
         */
        public int width(int screenWidth) {
            int right = isFree() ? shareEdge(this.right, screenWidth)
                    : edge(this.column + this.columns, COLUMNS, screenWidth);
            return right - left(screenWidth);
        }

        /** The part's top edge on a screen {@code screenHeight} tall. */
        public int top(int screenHeight) {
            return isFree() ? shareEdge(this.top, screenHeight)
                    : edge(this.row, ROWS, screenHeight);
        }

        /** The part's height on a screen {@code screenHeight} tall. */
        public int height(int screenHeight) {
            int bottom = isFree() ? shareEdge(this.bottom, screenHeight)
                    : edge(this.row + this.rows, ROWS, screenHeight);
            return bottom - top(screenHeight);
        }

        /** Where grid line {@code line} of {@code lines} falls on {@code size}. */
        private static int edge(int line, int lines, int size) {
            return (int)((long)size * line / lines);
        }

        /**
         * Where a free edge falls on {@code size}: the pixel a grid line
         * at the same share falls on, so a free part matches a named one
         * beside it, and the same for both parts sharing it. The share is
         * a double, so a third of the screen lands a hair short of its
         * pixel; the tolerance puts it back on it.
         */
        private static int shareEdge(double share, int size) {
            return (int)Math.floor(share * size + 1.0E-6D);
        }

        /** The part of that name, a free part as written, or none. */
        public static ScreenFill fromId(String id) {
            if (id == null) {
                return NONE;
            }
            for (ScreenFill fill : NAMED) {
                if (fill.id.equalsIgnoreCase(id)) {
                    return fill;
                }
            }
            if (id.regionMatches(true, 0, FREE_PREFIX, 0,
                    FREE_PREFIX.length())) {
                String[] edges = id.substring(FREE_PREFIX.length()).split(",");
                if (edges.length == 4) {
                    try {
                        ScreenFill fill = free(Double.parseDouble(edges[0]),
                                Double.parseDouble(edges[1]),
                                Double.parseDouble(edges[2]),
                                Double.parseDouble(edges[3]));
                        if (fill.right > fill.left && fill.bottom > fill.top) {
                            return fill;
                        }
                    } catch (NumberFormatException unreadable) {
                        // Not a part: the window keeps its own box.
                    }
                }
            }
            return NONE;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ScreenFill)) {
                return false;
            }
            ScreenFill that = (ScreenFill)other;
            return isFree() && that.isFree()
                    && this.left == that.left && this.top == that.top
                    && this.right == that.right && this.bottom == that.bottom;
        }

        @Override
        public int hashCode() {
            if (!isFree()) {
                return this.id.hashCode();
            }
            long bits = Double.doubleToLongBits(this.left)
                    ^ Double.doubleToLongBits(this.top) * 31L
                    ^ Double.doubleToLongBits(this.right) * 961L
                    ^ Double.doubleToLongBits(this.bottom) * 29791L;
            return (int)(bits ^ (bits >>> 32));
        }

        @Override
        public String toString() {
            return id();
        }
    }

    /**
     * Where a stuck window sits relative to the one it is stuck to. Two
     * stuck windows keep their gap and move as one, whichever of them is
     * dragged.
     */
    public enum LinkSide {
        ABOVE("above", false),
        BELOW("below", false),
        LEFT("left", true),
        RIGHT("right", true);

        private final String id;
        private final boolean horizontal;

        LinkSide(String id, boolean horizontal) {
            this.id = id;
            this.horizontal = horizontal;
        }

        public String id() { return this.id; }

        /** Whether the side is a left or right one, not a top or bottom. */
        public boolean isHorizontal() { return this.horizontal; }

        /** The side of that name; below for anything else, as files had. */
        public static LinkSide fromId(String id) {
            for (LinkSide side : values()) {
                if (side.id.equalsIgnoreCase(id)) {
                    return side;
                }
            }
            return BELOW;
        }
    }

    ChatWindow(String id) {
        this.id = id;
    }

    public String getId() { return this.id; }
    public boolean isLocked() { return this.locked; }
    public double getOffsetX() { return this.offsetX; }
    public double getOffsetY() { return this.offsetY; }
    /**
     * The height the player gave this window, in message lines and
     * fractions of one, or 0 while it follows the game's own chat-height
     * setting. A window is as tall as the player dragged it, not as tall
     * as the nearest whole line; the last line is clipped.
     */
    public double getMaxLines() { return this.maxLines; }

    /**
     * The width the player gave this window, in the chat's own pixels,
     * or 0 while it follows the game's chat-width setting.
     */
    public int getWidth() { return this.width; }
    public String getLinkTarget() { return this.linkTarget; }
    public boolean isLinkedAbove() { return this.linkSide == LinkSide.ABOVE; }

    /** Which side of its target this window is stuck to. */
    public LinkSide getLinkSide() { return this.linkSide; }
    public boolean isLinked() { return this.linkTarget != null; }

    /** The part of the screen the window fills; none in its own box. */
    public ScreenFill getFill() { return this.fill; }

    /** Whether the window fills the whole screen rather than its own box. */
    public boolean isFullscreen() { return this.fill == ScreenFill.FULL; }

    public boolean isAreaHidden() { return this.areaHidden; }

    public boolean isMembersHidden() { return this.membersHidden; }

    /** The member list's width the player chose, in the chat's pixels; 0 for none. */
    public double getMembersWidth() { return this.membersWidth; }

    /** Tabs in row order, including channels currently unavailable. */
    public List<ChatTab> getTabs() {
        return Collections.unmodifiableList(this.tabs);
    }

    /** The channels of the tabs, in row order; whispers as WHISPER. */
    public List<ChatChannel> getChannels() {
        List<ChatChannel> result = new ArrayList<ChatChannel>(this.tabs.size());
        for (int index = 0; index < this.tabs.size(); index++) {
            result.add(this.tabs.get(index).getChannel());
        }
        return result;
    }

    /** The tab in front, always one of {@link #getTabs()} when non-empty. */
    public ChatTab getActiveTab() {
        return this.activeTab;
    }

    /** The channel of the tab in front, or null. */
    public ChatChannel getActiveChannel() {
        return this.activeTab == null ? null : this.activeTab.getChannel();
    }

    public boolean contains(ChatTab tab) {
        return tab != null && this.tabs.contains(tab);
    }

    public boolean contains(ChatChannel channel) {
        return contains(ChatTab.of(channel));
    }

    List<ChatTab> tabs() { return this.tabs; }

    void setActiveTab(ChatTab tab) {
        this.activeTab = tab != null && this.tabs.contains(tab)
                ? tab : (this.tabs.isEmpty() ? null : this.tabs.get(0));
    }

    void setLocked(boolean locked) { this.locked = locked; }

    void setMaxLines(double maxLines) { this.maxLines = maxLines; }

    void setWidth(int width) { this.width = width; }

    void setFill(ScreenFill fill) {
        this.fill = fill == null ? ScreenFill.NONE : fill;
    }

    void setAreaHidden(boolean hidden) { this.areaHidden = hidden; }

    void setMembersHidden(boolean hidden) { this.membersHidden = hidden; }

    void setMembersWidth(double width) {
        this.membersWidth = Math.max(0.0D, width);
    }

    void setOffsets(double offsetX, double offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    void setLink(String target, boolean above) {
        setLink(target, above ? LinkSide.ABOVE : LinkSide.BELOW);
    }

    void setLink(String target, LinkSide side) {
        this.linkTarget = target;
        this.linkSide = target == null || side == null
                ? LinkSide.BELOW : side;
    }
}
