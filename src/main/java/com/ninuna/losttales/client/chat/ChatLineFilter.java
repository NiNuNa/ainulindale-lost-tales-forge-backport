package com.ninuna.losttales.client.chat;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Which lines of the shared history a view shows: a set of pages whose
 * every line it shows, a set whose lines it shows only when they are
 * addressed to the player, and whether untracked lines — those Lost
 * Tales did not route, which belong to the console — are included. A page
 * standing for a channel that is more than one conversation is taken as
 * the conversation being read ({@link ConversationPage#viewed}), so one Faction
 * row shows one faction, and every copy of a conversation shows its lines. One page is its open view; the feed
 * shows what each conversation's feed choice lets through
 * ({@link ChatLayout#feedFilter}). Value semantics, so
 * {@link ClientChatChannelViews} can cache per filter.
 */
final class ChatLineFilter {
    private final Set<ConversationPage> pages;
    /** Pages whose lines pass only when they mention or answer the player. */
    private final Set<ConversationPage> mentionPages;
    private final boolean includeUntracked;

    private ChatLineFilter(Set<ConversationPage> pages, Set<ConversationPage> mentionPages,
                           boolean includeUntracked) {
        this.pages = pages;
        this.mentionPages = mentionPages;
        this.includeUntracked = includeUntracked;
    }

    /** A single page; the console page also carries untracked lines. */
    static ChatLineFilter of(ConversationPage page) {
        if (page == null) {
            return new ChatLineFilter(Collections.<ConversationPage>emptySet(),
                    Collections.<ConversationPage>emptySet(), false);
        }
        return new ChatLineFilter(Collections.singleton(ConversationPage.viewedConversation(page)),
                Collections.<ConversationPage>emptySet(),
                page.getChannel() == ClientChatChannelViews.SYSTEM_LINE_VIEW);
    }

    /**
     * Several pages whose every line passes, and {@code mentionPages}, whose
     * lines pass only when they are addressed to the player. Untracked
     * lines ride with the console page when every line of it passes.
     */
    static ChatLineFilter of(Collection<ConversationPage> pages,
                             Collection<ConversationPage> mentionPages) {
        Set<ConversationPage> set = new HashSet<ConversationPage>();
        boolean untracked = false;
        if (pages != null) {
            for (ConversationPage page : pages) {
                if (page != null) {
                    set.add(ConversationPage.viewedConversation(page));
                    untracked |= page.getChannel()
                            == ClientChatChannelViews.SYSTEM_LINE_VIEW;
                }
            }
        }
        Set<ConversationPage> mentions = new HashSet<ConversationPage>();
        if (mentionPages != null) {
            for (ConversationPage page : mentionPages) {
                if (page != null) {
                    mentions.add(ConversationPage.viewedConversation(page));
                }
            }
        }
        return new ChatLineFilter(set, mentions, untracked);
    }

    boolean isEmpty() {
        return this.pages.isEmpty() && this.mentionPages.isEmpty()
                && !this.includeUntracked;
    }

    /**
     * The one conversation the view shows every line of and nothing
     * besides, or null for a view of several, of mentions, or of the
     * console's untracked lines.
     */
    ConversationPage only() {
        return this.pages.size() == 1 && this.mentionPages.isEmpty()
                && !this.includeUntracked ? this.pages.iterator().next() : null;
    }

    /** Whether every line of the page passes. */
    boolean accepts(ConversationPage page) {
        // Asked with a line's own page, which already names the
        // conversation, or with a channel's row entry, which stands for
        // whichever conversation is being read: both answer the same.
        return page == null ? this.includeUntracked
                : this.pages.contains(ConversationPage.viewedConversation(page));
    }

    /**
     * Whether a line of the page passes: any line of a page whose every
     * line does, and one {@code addressed} to the player, a mention of
     * them or a reply to them, of a page whose mentions alone do.
     */
    boolean accepts(ConversationPage page, boolean addressed) {
        return accepts(page) || (addressed && page != null
                && this.mentionPages.contains(ConversationPage.viewedConversation(page)));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatLineFilter)) {
            return false;
        }
        ChatLineFilter filter = (ChatLineFilter)other;
        return filter.pages.equals(this.pages)
                && filter.mentionPages.equals(this.mentionPages)
                && filter.includeUntracked == this.includeUntracked;
    }

    @Override
    public int hashCode() {
        return (this.pages.hashCode() * 31 + this.mentionPages.hashCode()) * 31
                + (this.includeUntracked ? 1 : 0);
    }
}
