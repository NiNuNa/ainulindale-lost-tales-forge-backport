package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One chat window in the client layout: an ordered row of tabs, the tab
 * currently in front, a lock, a position, and optionally a link to
 * another window it sits directly above or below, keeping its gap as
 * that window grows, shrinks or moves. Instances are owned and mutated
 * only by {@link ChatWindowLayout}; everyone else reads them.
 */
public final class ChatWindow {
    private final String id;
    private final List<ChatTab> tabs = new ArrayList<ChatTab>();
    private ChatTab activeTab;
    private boolean locked;
    /** Percent of the available screen travel, see HudPlacementLayout. */
    private double offsetX;
    private double offsetY;
    /** Id of the window this one is linked to, or null. */
    private String linkTarget;
    /** Whether this window sits above its link target (else below). */
    private boolean linkedAbove;

    ChatWindow(String id) {
        this.id = id;
    }

    public String getId() { return this.id; }
    public boolean isLocked() { return this.locked; }
    public double getOffsetX() { return this.offsetX; }
    public double getOffsetY() { return this.offsetY; }
    public String getLinkTarget() { return this.linkTarget; }
    public boolean isLinkedAbove() { return this.linkedAbove; }
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

    void setOffsets(double offsetX, double offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    void setLink(String target, boolean above) {
        this.linkTarget = target;
        this.linkedAbove = target != null && above;
    }
}
