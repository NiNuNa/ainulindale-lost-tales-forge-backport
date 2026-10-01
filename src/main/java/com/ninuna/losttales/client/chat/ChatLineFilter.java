package com.ninuna.losttales.client.chat;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Which lines of the shared history a view shows: a set of tabs whose
 * every line it shows, a set whose lines it shows only when they are
 * addressed to the player, and whether untracked lines — those Lost
 * Tales did not route, which belong to the console — are included. A tab
 * standing for a channel that is more than one conversation is taken as
 * the conversation being read ({@link ChatTab#viewed}), so one Faction
 * row shows one faction. One tab is its open view; the closed-chat feed
 * shows what each conversation's Show in Feed choice lets through
 * ({@link ChatLayout#feedFilter}). Value semantics, so
 * {@link ClientChatChannelViews} can cache per filter.
 */
final class ChatLineFilter {
    private final Set<ChatTab> tabs;
    /** Tabs whose lines pass only when they mention or answer the player. */
    private final Set<ChatTab> mentionTabs;
    private final boolean includeUntracked;

    private ChatLineFilter(Set<ChatTab> tabs, Set<ChatTab> mentionTabs,
                           boolean includeUntracked) {
        this.tabs = tabs;
        this.mentionTabs = mentionTabs;
        this.includeUntracked = includeUntracked;
    }

    /** A single tab; the console tab also carries untracked lines. */
    static ChatLineFilter of(ChatTab tab) {
        if (tab == null) {
            return new ChatLineFilter(Collections.<ChatTab>emptySet(),
                    Collections.<ChatTab>emptySet(), false);
        }
        return new ChatLineFilter(Collections.singleton(ChatTab.viewed(tab)),
                Collections.<ChatTab>emptySet(),
                tab.getChannel() == ClientChatChannelViews.SYSTEM_LINE_VIEW);
    }

    /**
     * Several tabs whose every line passes, and {@code mentionTabs}, whose
     * lines pass only when they are addressed to the player. Untracked
     * lines ride with the console tab when every line of it passes.
     */
    static ChatLineFilter of(Collection<ChatTab> tabs,
                             Collection<ChatTab> mentionTabs) {
        Set<ChatTab> set = new HashSet<ChatTab>();
        boolean untracked = false;
        if (tabs != null) {
            for (ChatTab tab : tabs) {
                if (tab != null) {
                    set.add(ChatTab.viewed(tab));
                    untracked |= tab.getChannel()
                            == ClientChatChannelViews.SYSTEM_LINE_VIEW;
                }
            }
        }
        Set<ChatTab> mentions = new HashSet<ChatTab>();
        if (mentionTabs != null) {
            for (ChatTab tab : mentionTabs) {
                if (tab != null) {
                    mentions.add(ChatTab.viewed(tab));
                }
            }
        }
        return new ChatLineFilter(set, mentions, untracked);
    }

    boolean isEmpty() {
        return this.tabs.isEmpty() && this.mentionTabs.isEmpty()
                && !this.includeUntracked;
    }

    /** Whether every line of the tab passes. */
    boolean accepts(ChatTab tab) {
        // Asked with a line's own tab, which already names the
        // conversation, or with a channel's row entry, which stands for
        // whichever conversation is being read: both answer the same.
        return tab == null ? this.includeUntracked
                : this.tabs.contains(ChatTab.viewed(tab));
    }

    /**
     * Whether a line of the tab passes: any line of a tab whose every
     * line does, and one {@code addressed} to the player, a mention of
     * them or a reply to them, of a tab whose mentions alone do.
     */
    boolean accepts(ChatTab tab, boolean addressed) {
        return accepts(tab) || (addressed && tab != null
                && this.mentionTabs.contains(ChatTab.viewed(tab)));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatLineFilter)) {
            return false;
        }
        ChatLineFilter filter = (ChatLineFilter)other;
        return filter.tabs.equals(this.tabs)
                && filter.mentionTabs.equals(this.mentionTabs)
                && filter.includeUntracked == this.includeUntracked;
    }

    @Override
    public int hashCode() {
        return (this.tabs.hashCode() * 31 + this.mentionTabs.hashCode()) * 31
                + (this.includeUntracked ? 1 : 0);
    }
}
