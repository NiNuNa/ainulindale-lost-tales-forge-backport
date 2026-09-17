package com.ninuna.losttales.gui.screen.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What the journal's search is looking for, and whether a quest answers
 * it.
 *
 * <p>Every word typed has to appear somewhere in what a quest says — its
 * title, its category, its giver, what it asks — so a half-remembered
 * objective finds its quest and the order the words are typed in does
 * not matter. Case is ignored. This is the chat's search rule, asked of
 * a quest instead of a line.</p>
 *
 * <p>Free of Minecraft: the screen hands it words, and a test can ask it
 * the same questions the screen does.</p>
 */
public final class QuestSearchQuery {

    /** A search for nothing, which every quest answers. */
    public static final QuestSearchQuery NONE = new QuestSearchQuery("");

    private final List<String> words;

    private QuestSearchQuery(String raw) {
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
    public static QuestSearchQuery of(String raw) {
        QuestSearchQuery query = new QuestSearchQuery(raw);
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
     * Whether everything a quest says answers the search. The parts are
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
