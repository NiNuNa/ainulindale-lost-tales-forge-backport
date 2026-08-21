package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds the {@code @Name} mention being typed at the input cursor and the
 * candidate names that could complete it. Pure text logic, mirroring
 * {@link com.ninuna.losttales.chat.emoji.ChatEmojiSuggester}; the client
 * GUI supplies the candidate names (online players plus own identities).
 */
public final class ChatNameSuggester {
    /** Prefixes may contain spaces for multi-word character names. */
    private static final int MAX_PREFIX_LENGTH = 32;

    private ChatNameSuggester() {}

    /**
     * The {@code @prefix} immediately before the cursor, or null. A bare
     * {@code @} opens an empty query (listing everyone), and the character
     * before the {@code @} must not be a name character so addresses like
     * {@code mail@host} stay quiet. Commands never produce a query.
     */
    public static Query findQuery(String text, int cursor) {
        if (text == null || text.startsWith("/")) {
            return null;
        }
        int clamped = Math.max(0, Math.min(text.length(), cursor));
        int index = clamped - 1;
        while (index >= 0 && isPrefixCharacter(text.charAt(index))) {
            index--;
        }
        if (index < 0 || text.charAt(index) != '@'
                || clamped - index - 1 > MAX_PREFIX_LENGTH) {
            return null;
        }
        if (index > 0 && isNameCharacter(text.charAt(index - 1))) {
            return null;
        }
        return new Query(index, text.substring(index + 1, clamped)
                .toLowerCase(Locale.ROOT));
    }

    /** Candidate-ordered names starting with the prefix, deduplicated. */
    public static List<String> matches(String prefix,
                                       List<String> candidates,
                                       int limit) {
        if (prefix == null || candidates == null || limit <= 0) {
            return Collections.emptyList();
        }
        String query = prefix.toLowerCase(Locale.ROOT);
        Set<String> seen = new LinkedHashSet<String>();
        List<String> result = new ArrayList<String>();
        for (int index = 0; index < candidates.size(); index++) {
            String candidate = candidates.get(index);
            String trimmed = candidate == null ? "" : candidate.trim();
            if (trimmed.length() == 0
                    || !trimmed.toLowerCase(Locale.ROOT)
                            .startsWith(query)
                    || !seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(trimmed);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private static boolean isPrefixCharacter(char character) {
        return isNameCharacter(character) || character == ' ';
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    /** Where the query starts and what has been typed so far. */
    public static final class Query {
        /** Index of the {@code @} in the input text. */
        public final int atIndex;
        /** Lowercased prefix typed after the {@code @}; may be empty. */
        public final String prefix;

        Query(int atIndex, String prefix) {
            this.atIndex = atIndex;
            this.prefix = prefix;
        }
    }
}
