package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds {@code @Name} mentions in conversation text. Matching is
 * case-insensitive and requires word boundaries on both sides so
 * {@code mail@Nameson} or {@code @Namesake} cannot ping {@code Name}.
 * The server finds whom a line's mentions reach among the conversation's
 * members and keeps them with the line ({@link #reached}); every client
 * shows and pings from that record, never from the sender's word. A
 * role's mention, and text a client writes itself, are matched by name
 * where they are shown ({@link #mentionsAny}).
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

    /** A name found after an at-sign: whom it names, and where the name ends. */
    public static final class Hit {
        public final ChatNamedPlayer player;
        /** The index just past the name in the text it was found in. */
        public final int end;

        Hit(ChatNamedPlayer player, int end) {
            this.player = player;
            this.end = end;
        }
    }

    /**
     * The person whose name follows the at-sign at {@code at} whole, among
     * {@code candidates}: by their account or the identity they are named
     * as, whatever the case. The longest name wins, so
     * {@code @Aldric of Bree} names Aldric of Bree before Aldric, and of
     * two sharing one name the one listed first. Null for an at-sign that
     * does not open a word or names nobody listed.
     */
    public static Hit nameAt(String text, int at, List<ChatNamedPlayer> candidates) {
        if (text == null || candidates == null || at < 0
                || at >= text.length() || text.charAt(at) != '@'
                || (at > 0 && isNameCharacter(text.charAt(at - 1)))) {
            return null;
        }
        ChatNamedPlayer best = null;
        int bestLength = 0;
        for (int index = 0; index < candidates.size(); index++) {
            ChatNamedPlayer candidate = candidates.get(index);
            if (candidate == null) {
                continue;
            }
            int length = Math.max(nameLength(text, at + 1,
                    candidate.getIdentityName()), nameLength(text, at + 1,
                    candidate.getAccount()));
            if (length > bestLength) {
                best = candidate;
                bestLength = length;
            }
        }
        return best == null ? null : new Hit(best, at + 1 + bestLength);
    }

    /**
     * The people a message's {@code @names} reach among {@code candidates},
     * each found as {@link #nameAt} finds them: at most {@code most}, each
     * once, in the order the message names them.
     */
    public static List<ChatNamedPlayer> reached(String message,
                                                List<ChatNamedPlayer> candidates,
                                                int most) {
        List<ChatNamedPlayer> named = new ArrayList<ChatNamedPlayer>();
        if (message == null || candidates == null || candidates.isEmpty()) {
            return named;
        }
        int at = message.indexOf('@');
        while (at >= 0 && named.size() < most) {
            Hit hit = nameAt(message, at, candidates);
            if (hit != null && !named.contains(hit.player)) {
                named.add(hit.player);
            }
            at = message.indexOf('@', hit == null ? at + 1 : hit.end);
        }
        return named;
    }

    /**
     * How long {@code name} is where the text, from {@code from}, reads
     * it whole, with no name's character right after it; 0 where it does
     * not.
     */
    private static int nameLength(String text, int from, String name) {
        String wanted = name == null ? "" : name.trim();
        if (wanted.length() == 0
                || !text.regionMatches(true, from, wanted, 0, wanted.length())) {
            return 0;
        }
        int end = from + wanted.length();
        return end < text.length() && isNameCharacter(text.charAt(end))
                ? 0 : wanted.length();
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
