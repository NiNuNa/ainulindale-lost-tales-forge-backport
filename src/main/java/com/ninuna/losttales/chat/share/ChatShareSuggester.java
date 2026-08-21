package com.ninuna.losttales.chat.share;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Finds the unclosed share opener ({@code [i:prefix} or {@code [m:prefix})
 * being typed at the input cursor and the candidates that could complete
 * it. Pure text logic like {@link com.ninuna.losttales.chat.emoji.ChatEmojiSuggester};
 * the client GUI supplies the candidate labels in a deterministic order.
 */
public final class ChatShareSuggester {
    private ChatShareSuggester() {}

    /**
     * The unclosed opener nearest the cursor, or null. A bare opener lists
     * every candidate of its kind. Commands never produce a query.
     */
    public static Query findQuery(String text, int cursor) {
        if (text == null || text.startsWith("/")) {
            return null;
        }
        int clamped = Math.max(0, Math.min(text.length(), cursor));
        int open = -1;
        ChatShareKind kind = null;
        for (ChatShareKind candidate : ChatShareKind.values()) {
            int found = text.lastIndexOf(candidate.getOpener(), clamped);
            if (found > open) {
                open = found;
                kind = candidate;
            }
        }
        if (kind == null || open + kind.getOpener().length() > clamped) {
            return null;
        }
        String prefix = text.substring(
                open + kind.getOpener().length(), clamped);
        if (prefix.indexOf(ChatShareKind.TOKEN_CLOSE) >= 0
                || prefix.length() > ChatShareTokenParser.MAX_NAME_LENGTH) {
            return null;
        }
        return new Query(kind, open, prefix.toLowerCase(Locale.ROOT));
    }

    /**
     * Indices of candidate labels matching the prefix: prefix matches first
     * in candidate order, then substring matches, so the candidate order
     * stays predictable within each rank.
     */
    public static List<Integer> matches(String prefix, List<String> labels,
                                        int limit) {
        if (prefix == null || labels == null || limit <= 0) {
            return Collections.emptyList();
        }
        String query = ChatShareTokenParser.normalizeName(prefix);
        List<Integer> result = new ArrayList<Integer>();
        List<Integer> contained = new ArrayList<Integer>();
        for (int index = 0; index < labels.size(); index++) {
            String label = ChatShareTokenParser.normalizeName(
                    labels.get(index));
            if (label.length() == 0) {
                continue;
            }
            if (label.startsWith(query)) {
                result.add(Integer.valueOf(index));
            } else if (query.length() > 0 && label.contains(query)) {
                contained.add(Integer.valueOf(index));
            }
        }
        result.addAll(contained);
        return result.size() > limit
                ? new ArrayList<Integer>(result.subList(0, limit)) : result;
    }

    /** Which opener is open, where it starts, and what has been typed. */
    public static final class Query {
        public final ChatShareKind kind;
        /** Index of the opening bracket in the input text. */
        public final int openIndex;
        /** Lowercased text typed after the opener; may be empty. */
        public final String prefix;

        Query(ChatShareKind kind, int openIndex, String prefix) {
            this.kind = kind;
            this.openIndex = openIndex;
            this.prefix = prefix;
        }
    }
}
