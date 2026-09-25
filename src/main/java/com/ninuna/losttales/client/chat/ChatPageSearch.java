package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What a page's search is looking for — the words typed in the well of
 * a page window's tool strip — and whether an entry answers it: a quest
 * in the journal, a member or an invitation in the party.
 *
 * <p>Every word typed has to appear somewhere in what the entry says,
 * so a half-remembered objective finds its quest and the order the words
 * are typed in does not matter. Case is ignored. This is the chat's
 * search rule, asked of an entry instead of a line.</p>
 *
 * <p>Free of Minecraft: the page hands it words, and a test can ask it
 * the same questions the page does.</p>
 */
public final class ChatPageSearch {

    /** A search for nothing, which every entry answers. */
    public static final ChatPageSearch NONE = new ChatPageSearch("");

    private final List<String> words;

    private ChatPageSearch(String raw) {
        List<String> found = new ArrayList<String>();
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (text.length() > 0) {
            for (String word : text.split("\\s+")) {
                if (word.length() > 0) {
                    found.add(word);
                }
            }
        }
        this.words = Collections.unmodifiableList(found);
    }

    /** The search a typed line stands for. */
    public static ChatPageSearch of(String raw) {
        ChatPageSearch query = new ChatPageSearch(raw);
        return query.isEmpty() ? NONE : query;
    }

    /** Whether nothing is being searched for, so nothing is narrowed. */
    public boolean isEmpty() {
        return this.words.isEmpty();
    }

    /** The words that must all appear, lower case. */
    public List<String> words() {
        return this.words;
    }

    /**
     * Whether everything an entry says answers the search. The parts are
     * joined with a space between them, so a word never matches across
     * the join.
     */
    public boolean matches(String... parts) {
        if (isEmpty()) {
            return true;
        }
        StringBuilder haystack = new StringBuilder();
        if (parts != null) {
            for (String part : parts) {
                if (part != null && part.length() > 0) {
                    haystack.append(part).append(' ');
                }
            }
        }
        String text = haystack.toString().toLowerCase(Locale.ROOT);
        for (int index = 0; index < this.words.size(); index++) {
            if (text.indexOf(this.words.get(index)) < 0) {
                return false;
            }
        }
        return true;
    }
}
