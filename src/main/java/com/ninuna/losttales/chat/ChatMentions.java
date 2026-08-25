package com.ninuna.losttales.chat;

import java.util.List;
import java.util.Locale;

/**
 * Detects {@code @Name} mentions in conversation text. Matching is
 * case-insensitive and requires word boundaries on both sides so
 * {@code mail@Nameson} or {@code @Namesake} cannot ping {@code Name}.
 * Detection is purely client-side presentation: every client checks its
 * own names, so nothing is synchronized or trusted from the sender.
 */
public final class ChatMentions {
    private ChatMentions() {}

    public static boolean mentionsAny(String message, List<String> names) {
        if (message == null || names == null || names.isEmpty()
                || message.indexOf('@') < 0) {
            return false;
        }
        String haystack = message.toLowerCase(Locale.ROOT);
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            if (name != null && name.trim().length() > 0
                    && mentions(haystack,
                            name.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The text with an {@code @} put before every bare occurrence of one
     * of the names — an NPC speaking the player's name reads as the
     * mention it is. Word boundaries on both sides; a name already
     * carrying an {@code @} keeps its one.
     */
    public static String mentionNames(String message, List<String> names) {
        if (message == null) {
            return "";
        }
        if (names == null || names.isEmpty()) {
            return message;
        }
        String result = message;
        for (int nameIndex = 0; nameIndex < names.size(); nameIndex++) {
            String name = names.get(nameIndex);
            String wanted = name == null ? "" : name.trim();
            if (wanted.length() == 0) {
                continue;
            }
            String haystack = result.toLowerCase(Locale.ROOT);
            String needle = wanted.toLowerCase(Locale.ROOT);
            StringBuilder built = null;
            int copied = 0;
            int from = 0;
            int at;
            while ((at = haystack.indexOf(needle, from)) >= 0) {
                from = at + 1;
                char before = at == 0 ? ' ' : haystack.charAt(at - 1);
                int end = at + needle.length();
                if (isNameCharacter(before) || before == '@'
                        || (end < haystack.length()
                                && isNameCharacter(haystack.charAt(end)))) {
                    continue;
                }
                if (built == null) {
                    built = new StringBuilder(result.length() + 4);
                }
                built.append(result, copied, at).append('@')
                        .append(result, at, end);
                copied = end;
                from = end;
            }
            if (built != null) {
                built.append(result.substring(copied));
                result = built.toString();
            }
        }
        return result;
    }

    private static boolean mentions(String haystack, String name) {
        int from = 0;
        int at;
        while ((at = haystack.indexOf('@', from)) >= 0) {
            from = at + 1;
            if (at > 0 && isNameCharacter(haystack.charAt(at - 1))) {
                continue;
            }
            if (!haystack.startsWith(name, at + 1)) {
                continue;
            }
            int end = at + 1 + name.length();
            if (end >= haystack.length()
                    || !isNameCharacter(haystack.charAt(end))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}
