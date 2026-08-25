package com.ninuna.losttales.chat.emoji;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits player conversation text into literal-text runs and recognized
 * emoji shortcodes. Anything that is not a complete {@code :name:} match for
 * a registered {@link ChatEmoji} stays literal text, so unknown or truncated
 * shortcodes remain readable and nothing here can fail on malformed input.
 */
public final class ChatEmojiParser {
    private ChatEmojiParser() {}

    /**
     * Tokenizes once per message, not per frame; the result is either a
     * single literal segment or an ordered mix of text and emoji segments
     * whose concatenated shortcodes/text reproduce the input exactly.
     */
    public static List<Segment> split(String message) {
        if (message == null || message.length() == 0
                || message.indexOf(':') < 0) {
            return Collections.singletonList(Segment.text(
                    message == null ? "" : message));
        }
        List<Segment> segments = new ArrayList<Segment>();
        int literalStart = 0;
        int index = 0;
        int length = message.length();
        while (index < length) {
            if (message.charAt(index) != ':') {
                index++;
                continue;
            }
            int nameEnd = scanName(message, index + 1);
            ChatEmoji emoji = nameEnd > index + 1 && nameEnd < length
                    && message.charAt(nameEnd) == ':'
                    ? ChatEmoji.fromName(
                            message.substring(index + 1, nameEnd))
                    : null;
            if (emoji == null) {
                index++;
                continue;
            }
            if (literalStart < index) {
                segments.add(Segment.text(
                        message.substring(literalStart, index)));
            }
            segments.add(Segment.emoji(emoji));
            index = nameEnd + 1;
            literalStart = index;
        }
        if (literalStart < length) {
            segments.add(Segment.text(message.substring(literalStart)));
        }
        if (segments.isEmpty()) {
            segments.add(Segment.text(message));
        }
        return segments;
    }

    /**
     * Replaces every complete {@code :name:} whose name is a registered
     * alias with the canonical shortcode; canonical shortcodes and
     * everything else stay exactly as typed. Input normalization only:
     * the wire, copies, and the renderer never see an alias.
     */
    public static String normalizeAliases(String message) {
        if (message == null || message.indexOf(':') < 0) {
            return message == null ? "" : message;
        }
        StringBuilder result = null;
        int literalStart = 0;
        int index = 0;
        int length = message.length();
        while (index < length) {
            if (message.charAt(index) != ':') {
                index++;
                continue;
            }
            int nameEnd = scanName(message, index + 1);
            ChatEmoji emoji = nameEnd > index + 1 && nameEnd < length
                    && message.charAt(nameEnd) == ':'
                    ? ChatEmoji.fromInputName(
                            message.substring(index + 1, nameEnd))
                    : null;
            if (emoji == null) {
                index++;
                continue;
            }
            if (result == null) {
                result = new StringBuilder(length);
            }
            result.append(message, literalStart, index);
            result.append(emoji.getShortcode());
            index = nameEnd + 1;
            literalStart = index;
        }
        if (result == null) {
            return message;
        }
        result.append(message, literalStart, length);
        return result.toString();
    }

    private static int scanName(String message, int start) {
        int limit = Math.min(message.length(),
                start + ChatEmoji.longestName());
        int index = start;
        while (index < limit && isNameCharacter(message.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isNameCharacter(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }

    /** One run of the message: exactly one of {@code text}/{@code emoji}. */
    public static final class Segment {
        private final String literalText;
        private final ChatEmoji emoji;

        private Segment(String literalText, ChatEmoji emoji) {
            this.literalText = literalText;
            this.emoji = emoji;
        }

        static Segment text(String literalText) {
            return new Segment(literalText, null);
        }

        static Segment emoji(ChatEmoji emoji) {
            return new Segment(null, emoji);
        }

        public boolean isEmoji() {
            return this.emoji != null;
        }

        public ChatEmoji getEmoji() {
            return this.emoji;
        }

        public String getText() {
            return this.literalText == null ? "" : this.literalText;
        }
    }
}
