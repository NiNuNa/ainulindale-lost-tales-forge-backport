package com.ninuna.losttales.chat.share;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Finds share tokens in player conversation text: {@code [i:Name]} for an
 * item from the sender's inventory and {@code [m:Name]} for a map marker.
 * When the sender holds several stacks with the same display name the
 * completion list distinguishes them as {@code [i:Name#2]}, and so on. A
 * token never carries a slot or marker id: the sending client resolves the
 * name against its own inventory or marker list at send time, and the
 * server re-resolves the reference it is given against its own state and
 * checks the name before anything is distributed. Anything that is not a
 * complete, well-formed token stays literal text, so malformed input can
 * never fail here.
 *
 * <p>This class is free of Minecraft imports so it is loadable on a
 * dedicated server and testable without a game runtime.</p>
 */
public final class ChatShareTokenParser {
    /** Upper bound on shared things per message; later tokens stay text. */
    public static final int MAX_TOKENS = 3;
    /** Item names are bounded by vanilla's anvil (35); marker names are longer. */
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_ORDINAL = 99;
    private static final char ORDINAL_SEPARATOR = '#';
    private static final char FORMATTING_ESCAPE = 167;

    private ChatShareTokenParser() {}

    /** Tokens in text order, at most {@link #MAX_TOKENS}; never null. */
    public static List<Token> parse(String message) {
        if (message == null || message.indexOf('[') < 0) {
            return Collections.emptyList();
        }
        List<Token> tokens = new ArrayList<Token>(MAX_TOKENS);
        int index = 0;
        while (tokens.size() < MAX_TOKENS) {
            int start = -1;
            ChatShareKind kind = null;
            for (ChatShareKind candidate : ChatShareKind.values()) {
                int found = message.indexOf(candidate.getOpener(), index);
                if (found >= 0 && (start < 0 || found < start)) {
                    start = found;
                    kind = candidate;
                }
            }
            if (start < 0) {
                break;
            }
            int end = message.indexOf(ChatShareKind.TOKEN_CLOSE, start);
            if (end < 0) {
                break;
            }
            Token token = parseBody(message, kind, start, end);
            if (token == null) {
                // A later opener before the close may still be a valid token.
                index = start + kind.getOpener().length();
                continue;
            }
            tokens.add(token);
            index = end + 1;
        }
        return tokens;
    }

    private static Token parseBody(String message, ChatShareKind kind,
                                   int start, int end) {
        String body = message.substring(
                start + kind.getOpener().length(), end).trim();
        if (body.indexOf('[') >= 0) {
            return null;
        }
        int ordinal = 1;
        int separator = body.lastIndexOf(ORDINAL_SEPARATOR);
        if (separator >= 0) {
            String digits = body.substring(separator + 1).trim();
            if (digits.length() > 0 && isDigits(digits)) {
                // A numeric suffix is always an ordinal; beyond two digits
                // it can only be out of range.
                if (digits.length() > 2) {
                    return null;
                }
                ordinal = Integer.parseInt(digits);
                body = body.substring(0, separator);
            }
        }
        String name = body.trim();
        if (name.length() == 0 || name.length() > MAX_NAME_LENGTH
                || ordinal < 1 || ordinal > MAX_ORDINAL) {
            return null;
        }
        return new Token(kind, start, end + 1, name, ordinal);
    }

    /** True when the colon at {@code colonIndex} closes a share opener. */
    public static boolean opensShareToken(String text, int colonIndex) {
        if (text == null) {
            return false;
        }
        for (ChatShareKind kind : ChatShareKind.values()) {
            int openStart = colonIndex + 1 - kind.getOpener().length();
            if (openStart >= 0
                    && text.startsWith(kind.getOpener(), openStart)) {
                return true;
            }
        }
        return false;
    }

    /** Player-facing token for the given kind, name and duplicate ordinal. */
    public static String buildToken(ChatShareKind kind, String name,
                                    int ordinal) {
        String safeName = name == null ? "" : name.trim();
        String suffix = ordinal > 1
                ? String.valueOf(ORDINAL_SEPARATOR) + ordinal : "";
        return kind.getOpener() + safeName + suffix
                + ChatShareKind.TOKEN_CLOSE;
    }

    /**
     * Comparison key for display names: formatting codes removed, runs of
     * whitespace collapsed, lowercased. Both the sending client and the
     * server compare names through this so a formatted name and its typed
     * plain form agree.
     */
    public static String normalizeName(String displayName) {
        if (displayName == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(displayName.length());
        boolean pendingSpace = false;
        for (int index = 0; index < displayName.length(); index++) {
            char character = displayName.charAt(index);
            if (character == FORMATTING_ESCAPE) {
                index++;
                continue;
            }
            if (Character.isWhitespace(character)) {
                pendingSpace = result.length() > 0;
                continue;
            }
            if (pendingSpace) {
                result.append(' ');
                pendingSpace = false;
            }
            result.append(character);
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }

    /** Visible display name with formatting codes removed and trimmed. */
    public static String plainName(String displayName) {
        if (displayName == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(displayName.length());
        for (int index = 0; index < displayName.length(); index++) {
            char character = displayName.charAt(index);
            if (character == FORMATTING_ESCAPE) {
                index++;
                continue;
            }
            result.append(character);
        }
        return result.toString().trim();
    }

    private static boolean isDigits(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) < '0' || value.charAt(index) > '9') {
                return false;
            }
        }
        return true;
    }

    /** One share token: its kind and span in the message plus the name. */
    public static final class Token {
        public final ChatShareKind kind;
        /** Index of the opening bracket. */
        public final int start;
        /** Index just past the closing bracket. */
        public final int end;
        /** Trimmed name as typed, formatting untouched. */
        public final String name;
        /** One-based duplicate ordinal; 1 when absent. */
        public final int ordinal;

        Token(ChatShareKind kind, int start, int end, String name,
              int ordinal) {
            this.kind = kind;
            this.start = start;
            this.end = end;
            this.name = name;
            this.ordinal = ordinal;
        }

        public String normalizedName() {
            return normalizeName(this.name);
        }
    }
}
