package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One chat window in the client layout: an ordered row of tabs, the tab
 * currently in front, a lock, a position, how many message lines it may
 * show, and optionally a link to another window it sits directly above
 * or below, keeping its gap as that window grows, shrinks or moves.
 * Instances are owned and mutated only by {@link ChatWindowLayout};
 * everyone else reads them.
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
