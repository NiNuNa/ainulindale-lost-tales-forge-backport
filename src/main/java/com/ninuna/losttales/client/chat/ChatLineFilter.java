package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Which lines of the shared history a view shows: a set of tabs plus
 * whether untracked lines — those Lost Tales did not route, which belong
 * to the console — are included. One tab is its open view; a window's
 * tabs minus the muted ones are its closed-chat feed. Value semantics,
 * so {@link ClientChatChannelViews} can cache per filter.
 */
final class ChatLineFilter {
    private final Set<ChatTab> tabs;
    private final boolean includeUntracked;

    private ChatLineFilter(Set<ChatTab> tabs, boolean includeUntracked) {
        this.tabs = tabs;
        this.includeUntracked = includeUntracked;
    }

    /** A single tab; the console tab also carries untracked lines. */
    static ChatLineFilter of(ChatTab tab) {
        if (tab == null) {
            return new ChatLineFilter(Collections.<ChatTab>emptySet(), false);
        }
        return new ChatLineFilter(Collections.singleton(tab),
                tab.getChannel() == ClientChatChannelViews.SYSTEM_LINE_VIEW);
    }

    static ChatLineFilter of(ChatChannel channel) {
        return of(ChatTab.of(channel));
    }

    /** Several tabs; untracked lines ride with the console tab. */
    static ChatLineFilter of(Collection<ChatTab> tabs) {
        Set<ChatTab> set = new HashSet<ChatTab>();
        boolean untracked = false;
        if (tabs != null) {
            for (ChatTab tab : tabs) {
                if (tab != null) {
                    set.add(tab);
                    untracked |= tab.getChannel()
                            == ClientChatChannelViews.SYSTEM_LINE_VIEW;
                }
            }
        }
        return new ChatLineFilter(set, untracked);
    }

    /** Several plain channels. */
    static ChatLineFilter ofChannels(Collection<ChatChannel> channels) {
        Set<ChatTab> set = new HashSet<ChatTab>();
        if (channels != null) {
            for (ChatChannel channel : channels) {
                if (channel != null) {
                    set.add(ChatTab.of(channel));
                }
            }
        }
        return of(set);
    }

    boolean isEmpty() {
        return this.tabs.isEmpty() && !this.includeUntracked;
    }

    boolean accepts(ChatTab tab) {
        return tab == null ? this.includeUntracked : this.tabs.contains(tab);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatLineFilter)) {
            return false;
        }
        ChatLineFilter filter = (ChatLineFilter)other;
        return filter.tabs.equals(this.tabs)
                && filter.includeUntracked == this.includeUntracked;
    }

    @Override
    public int hashCode() {
        return this.tabs.hashCode() * 31 + (this.includeUntracked ? 1 : 0);
    }
}
