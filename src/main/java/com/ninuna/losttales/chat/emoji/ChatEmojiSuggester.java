package com.ninuna.losttales.chat.emoji;

import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Finds the emoji shortcode being typed at the input cursor and the emojis
 * that could complete it. Pure text logic so the client GUI stays a thin
 * presentation layer and this behaviour is unit-testable.
 */
public final class ChatEmojiSuggester {
    private ChatEmojiSuggester() {}

    /**
     * The unclosed {@code :prefix} immediately before the cursor, or null.
     * Commands never produce a query, a bare colon needs at least one name
     * character, and a colon closing a completed shortcode does not open a
     * new query because its prefix is empty.
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
        if (index < 0 || text.charAt(index) != ':'
                || opensItemToken(text, index)) {
            return null;
        }
        int prefixLength = clamped - index - 1;
        if (prefixLength < 1 || prefixLength > ChatEmoji.longestName()
                || closesShortcode(text, index)) {
            return null;
        }
        return new Query(index, text.substring(index + 1, clamped)
                .toLowerCase(Locale.ROOT));
    }

    /**
     * True when the colon at {@code colonIndex} terminates a completed,
     * registered shortcode. Completing a query anchored on such a colon
     * would consume it and corrupt the already-typed emoji, so it must not
     * open a new query.
     */
    private static boolean closesShortcode(String text, int colonIndex) {
        int index = colonIndex - 1;
        while (index >= 0 && isPrefixCharacter(text.charAt(index))) {
            index--;
        }
        return index >= 0 && index < colonIndex - 1
                && text.charAt(index) == ':'
                && ChatEmoji.fromName(text.substring(index + 1, colonIndex)
                        .toLowerCase(Locale.ROOT)) != null;
    }

    /**
     * True when the colon belongs to an item-showcase opener ({@code {i:}),
     * whose completion list is the item suggester's, not this one's.
     */
    private static boolean opensItemToken(String text, int colonIndex) {
        return ChatShareTokenParser.opensShareToken(text, colonIndex);
    }

    /**
     * Registry-ordered emojis whose canonical name — or one of whose
     * aliases — starts with the prefix. Completion always inserts the
     * canonical shortcode, so an alias is a way in, never a way out.
     */
    public static List<ChatEmoji> matches(String prefix, int limit) {
        if (prefix == null || prefix.length() == 0 || limit <= 0) {
            return Collections.emptyList();
        }
        String query = prefix.toLowerCase(Locale.ROOT);
        List<ChatEmoji> result = new ArrayList<ChatEmoji>();
        for (ChatEmoji emoji : ChatEmoji.values()) {
            if (matchesPrefix(emoji, query)) {
                result.add(emoji);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private static boolean matchesPrefix(ChatEmoji emoji, String query) {
        if (emoji.getName().startsWith(query)) {
            return true;
        }
        List<String> aliases = emoji.getAliases();
        for (int index = 0; index < aliases.size(); index++) {
            if (aliases.get(index).startsWith(query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrefixCharacter(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }

    /** Where the query starts and what has been typed so far. */
    public static final class Query {
        /** Index of the opening colon in the input text. */
        public final int colonIndex;
        /** Lowercased name characters typed after the colon. */
        public final String prefix;

        Query(int colonIndex, String prefix) {
            this.colonIndex = colonIndex;
            this.prefix = prefix;
        }
    }
}
