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
 * the conversation being read ({@link ConversationPage#viewed}), so one Faction
 * row shows one faction. One tab is its open view; the closed-chat feed
 * shows what each conversation's feed choice lets through
 * ({@link ChatLayout#feedFilter}). Value semantics, so
 * {@link ClientChatChannelViews} can cache per filter.
 */
final class ChatLineFilter {
    private final Set<ConversationPage> tabs;
    /** Tabs whose lines pass only when they mention or answer the player. */
    private final Set<ConversationPage> mentionTabs;
    private final boolean includeUntracked;

    private ChatLineFilter(Set<ConversationPage> tabs, Set<ConversationPage> mentionTabs,
                           boolean includeUntracked) {
        this.tabs = tabs;
        this.mentionTabs = mentionTabs;
        this.includeUntracked = includeUntracked;
    }

    /** A single tab; the console tab also carries untracked lines. */
    static ChatLineFilter of(ConversationPage tab) {
        if (tab == null) {
            return new ChatLineFilter(Collections.<ConversationPage>emptySet(),
                    Collections.<ConversationPage>emptySet(), false);
        }
        return new ChatLineFilter(Collections.singleton(ConversationPage.viewed(tab)),
                Collections.<ConversationPage>emptySet(),
                tab.getChannel() == ClientChatChannelViews.SYSTEM_LINE_VIEW);
    }

    /**
     * Several tabs whose every line passes, and {@code mentionTabs}, whose
     * lines pass only when they are addressed to the player. Untracked
     * lines ride with the console tab when every line of it passes.
     */
    static ChatLineFilter of(Collection<ConversationPage> tabs,
                             Collection<ConversationPage> mentionTabs) {
        Set<ConversationPage> set = new HashSet<ConversationPage>();
        boolean untracked = false;
        if (tabs != null) {
            for (ConversationPage tab : tabs) {
                if (tab != null) {
                    set.add(ConversationPage.viewed(tab));
                    untracked |= tab.getChannel()
                            == ClientChatChannelViews.SYSTEM_LINE_VIEW;
                }
            }
        }
        Set<ConversationPage> mentions = new HashSet<ConversationPage>();
        if (mentionTabs != null) {
            for (ConversationPage tab : mentionTabs) {
                if (tab != null) {
                    mentions.add(ConversationPage.viewed(tab));
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
    boolean accepts(ConversationPage tab) {
        // Asked with a line's own tab, which already names the
        // conversation, or with a channel's row entry, which stands for
        // whichever conversation is being read: both answer the same.
        return tab == null ? this.includeUntracked
                : this.tabs.contains(ConversationPage.viewed(tab));
    }

    /**
     * Whether a line of the tab passes: any line of a tab whose every
     * line does, and one {@code addressed} to the player, a mention of
     * them or a reply to them, of a tab whose mentions alone do.
     */
    boolean accepts(ConversationPage tab, boolean addressed) {
        return accepts(tab) || (addressed && tab != null
                && this.mentionTabs.contains(ConversationPage.viewed(tab)));
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
