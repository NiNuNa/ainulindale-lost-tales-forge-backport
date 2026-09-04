package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What was sent from each tab, for the Up and Down arrows to walk back
 * through: a conversation's own history, so recalling a line in OOC never
 * brings up something said in Global. Whisper tabs have histories of
 * their own like any other tab.
 *
 * <p>Walking starts from the text in the field, which is kept as the
 * pending line and given back when the walk comes down past the newest
 * entry. Sending, or moving to another tab, ends the walk. Bounded both
 * ways: the newest {@link #MAX_ENTRIES_PER_TAB} lines of each tab, and
 * the {@link #MAX_TABS} tabs most recently written to.</p>
 */
final class ChatSentHistory {
    /** Lines kept per tab: the reach vanilla's single history has. */
    static final int MAX_ENTRIES_PER_TAB = 100;
    /** Tabs remembered, oldest first; the bound the drafts have. */
    static final int MAX_TABS = 64;

    private final LinkedHashMap<ChatTab, ArrayList<String>> entries =
            new LinkedHashMap<ChatTab, ArrayList<String>>();
    /** The tab being walked, or null while no walk is under way. */
    private ChatTab browsing;
    /** Index into the browsed tab's lines; its size means the pending text. */
    private int cursor;
    private String pending = "";

    /** Remembers a sent line under its tab; empty text is nothing to recall. */
    void record(ChatTab tab, String text) {
        endBrowse();
        String line = text == null ? "" : text.trim();
        if (tab == null || line.length() == 0) {
            return;
        }
        ArrayList<String> lines = this.entries.remove(tab);
        if (lines == null) {
            lines = new ArrayList<String>();
            while (this.entries.size() >= MAX_TABS) {
                Iterator<ChatTab> oldest = this.entries.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
        }
        lines.add(line);
        while (lines.size() > MAX_ENTRIES_PER_TAB) {
            lines.remove(0);
        }
        // Touched last, so the tabs written to longest ago go first.
        this.entries.put(tab, lines);
    }

    /**
     * Walks the tab's history one step: Up is {@code -1}, Down {@code +1}.
     * Answers the text the field should now hold, or null when the walk
     * cannot move that way. The first step away from the field keeps its
     * text as the pending line; stepping down past the newest entry
     * gives it back and ends the walk.
     */
    String step(ChatTab tab, int direction, String fieldText) {
        if (tab == null || direction == 0) {
            return null;
        }
        List<String> lines = entries(tab);
        if (!tab.equals(this.browsing)) {
            if (lines.isEmpty()) {
                return null;
            }
            this.browsing = tab;
            this.cursor = lines.size();
            this.pending = fieldText == null ? "" : fieldText;
        }
        int next = Math.max(0, Math.min(lines.size(), this.cursor + direction));
        if (next == this.cursor) {
            return null;
        }
        if (next == lines.size()) {
            String restored = this.pending;
            endBrowse();
            return restored;
        }
        this.cursor = next;
        return lines.get(next);
    }

    /** Ends a walk: the next Up starts again from the newest line. */
    void endBrowse() {
        this.browsing = null;
        this.cursor = 0;
        this.pending = "";
    }

    boolean isBrowsing() {
        return this.browsing != null;
    }

    void forget(ChatTab tab) {
        if (tab != null) {
            this.entries.remove(tab);
            if (tab.equals(this.browsing)) {
                endBrowse();
            }
        }
    }

    /** Drops every conversation tab's history: what leaving a server does. */
    void forgetConversations() {
        Iterator<ChatTab> tabs = this.entries.keySet().iterator();
        while (tabs.hasNext()) {
            if (tabs.next().isWhisper()) {
                tabs.remove();
            }
        }
        if (this.browsing != null && this.browsing.isWhisper()) {
            endBrowse();
        }
    }

    void clear() {
        this.entries.clear();
        endBrowse();
    }

    /** A tab's lines, oldest first. */
    List<String> entries(ChatTab tab) {
        ArrayList<String> lines = tab == null ? null : this.entries.get(tab);
        return lines == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(lines);
    }

    /** Test hook: tabs remembered, oldest first. */
    List<ChatTab> tabs() {
        return new ArrayList<ChatTab>(this.entries.keySet());
    }

    /** Test hook: the pending line a walk will give back. */
    String pending() {
        return this.pending;
    }

    /** Test hook. */
    Map<ChatTab, ArrayList<String>> view() {
        return Collections.unmodifiableMap(this.entries);
    }
}
