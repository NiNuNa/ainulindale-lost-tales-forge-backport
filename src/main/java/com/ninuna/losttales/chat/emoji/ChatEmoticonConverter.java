package com.ninuna.losttales.chat.emoji;

import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns classic text emoticons into canonical emoji shortcodes, the way
 * Discord's automatic conversion does. Applied once, on the sender's
 * client as the message goes out, so every recipient sees the same
 * canonical shortcode whatever their own settings are.
 *
 * <p>Only a whole whitespace-delimited token converts: {@code :)} alone
 * is a smile, while {@code hi:)}, {@code (:)} and any URL or share
 * token stay exactly as typed — a word can never half-match, and
 * nothing inside a share token ({@code [i:...]}) is touched, so a
 * shared name holding an emoticon still matches its item. Matching
 * is case-sensitive ({@code :D} converts, {@code :d} does not), the
 * whitespace between tokens is preserved verbatim, and commands are
 * never touched. Deterministic by construction: the same input always
 * gives the same output.</p>
 */
public final class ChatEmoticonConverter {
    /**
     * Emoticon token to emoji, in a stable order for tests and docs.
     * Mapping a new emoji is one {@code map(...)} line here; nothing
     * else in the mod knows the table.
     */
    private static final Map<String, ChatEmoji> BY_EMOTICON =
            new LinkedHashMap<String, ChatEmoji>();

    static {
        map(ChatEmoji.CUTESY, ":3");
        map(ChatEmoji.SLIGHT_SMILE, ":)", ":-)");
        map(ChatEmoji.FROWNING, ":(", ":-(");
        map(ChatEmoji.SMILE, ":D", ":-D");
        map(ChatEmoji.STUCK_OUT_TONGUE_CLOSED_EYES,
                ":P", ":-P", ":p", ":-p");
        map(ChatEmoji.LAUGHING, "xD", "XD");
        map(ChatEmoji.OPEN_MOUTH, ":O", ":-O", ":o", ":-o");
        map(ChatEmoji.SMILING_FACE_WITH_TEAR, ":')", ":'-)");
        map(ChatEmoji.SOB, ":'(", ":'-(");
        map(ChatEmoji.FEARFUL, "D:");
        map(ChatEmoji.HEART, "<3");
        map(ChatEmoji.BROKEN_HEART, "</3");
        map(ChatEmoji.KISSING_CLOSED_EYES, ":*", ":-*");
    }

    private static void map(ChatEmoji emoji, String... emoticons) {
        for (String emoticon : emoticons) {
            if (BY_EMOTICON.put(emoticon, emoji) != null) {
                throw new IllegalStateException(
                        "duplicate emoticon: " + emoticon);
            }
        }
    }

    private ChatEmoticonConverter() {}

    /** The emoji a whole token stands for, or null. */
    public static ChatEmoji emojiFor(String token) {
        return token == null ? null : BY_EMOTICON.get(token);
    }

    /**
     * The message with every whole-token emoticon replaced by its
     * canonical shortcode; commands and everything else are returned
     * exactly as given.
     */
    public static String convert(String message) {
        if (message == null || message.length() == 0
                || message.startsWith("/")) {
            return message == null ? "" : message;
        }
        List<ChatShareTokenParser.Token> shares =
                ChatShareTokenParser.parse(message);
        StringBuilder result = null;
        int index = 0;
        int length = message.length();
        while (index < length) {
            int start = index;
            while (index < length
                    && !Character.isWhitespace(message.charAt(index))) {
                index++;
            }
            if (index > start) {
                ChatEmoji emoji = insideShareToken(shares, start, index)
                        ? null
                        : BY_EMOTICON.get(message.substring(start, index));
                if (emoji != null && result == null) {
                    result = new StringBuilder(length + 16);
                    result.append(message, 0, start);
                }
                if (result != null) {
                    if (emoji != null) {
                        result.append(emoji.getShortcode());
                    } else {
                        result.append(message, start, index);
                    }
                }
            }
            int whitespaceStart = index;
            while (index < length
                    && Character.isWhitespace(message.charAt(index))) {
                index++;
            }
            if (result != null && index > whitespaceStart) {
                result.append(message, whitespaceStart, index);
            }
        }
        return result == null ? message : result.toString();
    }

    private static boolean insideShareToken(
            List<ChatShareTokenParser.Token> shares, int start, int end) {
        for (int index = 0; index < shares.size(); index++) {
            ChatShareTokenParser.Token token = shares.get(index);
            if (start < token.end && end > token.start) {
                return true;
            }
        }
        return false;
    }
}
