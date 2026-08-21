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
