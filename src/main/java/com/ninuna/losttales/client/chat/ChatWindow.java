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
     * The parts of the screen a window can fill, as a desktop window
     * snaps to the edge it is dragged to: the whole screen from the
     * top edge, a half from a side, a quarter from a corner. Each is a
     * box of screen halves — its left and top in halves, and how many
     * halves across and down it spans.
     */
    public enum ScreenFill {
        NONE("none", 0, 0, 0, 0),
        FULL("full", 0, 0, 2, 2),
        LEFT("left", 0, 0, 1, 2),
        RIGHT("right", 1, 0, 1, 2),
        TOP_LEFT("top_left", 0, 0, 1, 1),
        TOP_RIGHT("top_right", 1, 0, 1, 1),
        BOTTOM_LEFT("bottom_left", 0, 1, 1, 1),
        BOTTOM_RIGHT("bottom_right", 1, 1, 1, 1);

        private final String id;
        private final int halfLeft;
        private final int halfTop;
        private final int halvesAcross;
        private final int halvesDown;

        ScreenFill(String id, int halfLeft, int halfTop, int halvesAcross,
                   int halvesDown) {
            this.id = id;
            this.halfLeft = halfLeft;
            this.halfTop = halfTop;
            this.halvesAcross = halvesAcross;
            this.halvesDown = halvesDown;
        }

        public String id() { return this.id; }

        /** The fill's left edge on a screen {@code screenWidth} wide. */
        public int left(int screenWidth) {
            return this.halfLeft == 0 ? 0 : screenWidth / 2;
        }

        /** The fill's width on a screen {@code screenWidth} wide. */
        public int width(int screenWidth) {
            return this.halvesAcross == 2 ? screenWidth
                    : this.halfLeft == 0 ? screenWidth / 2
                    : screenWidth - screenWidth / 2;
        }

        /** The fill's top edge on a screen {@code screenHeight} tall. */
        public int top(int screenHeight) {
            return this.halfTop == 0 ? 0 : screenHeight / 2;
        }

        /** The fill's height on a screen {@code screenHeight} tall. */
        public int height(int screenHeight) {
            return this.halvesDown == 2 ? screenHeight
                    : this.halfTop == 0 ? screenHeight / 2
                    : screenHeight - screenHeight / 2;
        }

        /** The fill of that name; none for anything else. */
        public static ScreenFill fromId(String id) {
            for (ScreenFill fill : values()) {
                if (fill.id.equalsIgnoreCase(id)) {
                    return fill;
                }
            }
            return NONE;
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
