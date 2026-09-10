package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Channels named in a message the way Discord names them: {@code #}
 * and the channel, {@code #ooc}, {@code #Global}. A channel is written
 * by its id or by its shown name without spaces, either way it reads
 * as a link to the channel. What is typed behind a {@code #} is the
 * query the completion list answers, exactly as {@code @} opens the
 * mention list. No client dependency: the rules are the same on both
 * sides and testable without a game.
 */
public final class ChatChannelSuggester {
    private static final int MAX_PREFIX_LENGTH = 24;

    private ChatChannelSuggester() {}

    /**
     * The {@code #prefix} the cursor stands at the end of, or null when
     * the cursor is not completing a channel: not behind a {@code #}
     * that opens a word, or in a command.
     */
    public static Query findQuery(String text, int cursor) {
        if (text == null || text.startsWith("/")) {
            return null;
        }
        int clamped = Math.max(0, Math.min(text.length(), cursor));
        int index = clamped - 1;
        while (index >= 0 && isNameCharacter(text.charAt(index))) {
            index--;
        }
        if (index < 0 || text.charAt(index) != '#'
                || clamped - index - 1 > MAX_PREFIX_LENGTH) {
            return null;
        }
        if (index > 0 && !Character.isWhitespace(text.charAt(index - 1))) {
            return null;
        }
        return new Query(index, text.substring(index + 1, clamped)
                .toLowerCase(Locale.ROOT));
    }

    /**
     * The channels the prefix opens, in the order given: a channel
     * whose id or shown name starts with it, an empty prefix every one.
     */
    public static List<ChatChannel> matches(String prefix,
                                            List<ChatChannel> channels,
                                            int limit) {
        if (prefix == null || channels == null || limit <= 0) {
            return Collections.emptyList();
        }
        String query = prefix.toLowerCase(Locale.ROOT);
        List<ChatChannel> result = new ArrayList<ChatChannel>();
        for (ChatChannel channel : channels) {
            if (channel == null) {
                continue;
            }
            if (channel.getId().toLowerCase(Locale.ROOT).startsWith(query)
                    || shownKey(channel).startsWith(query)) {
                result.add(channel);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * The channel a word behind a {@code #} names — by id, or by its
     * shown name without spaces, case aside — or null for a word that
     * names none.
     */
    public static ChatChannel resolve(String word) {
        if (word == null || word.length() == 0
                || word.length() > MAX_PREFIX_LENGTH) {
            return null;
        }
        String key = word.toLowerCase(Locale.ROOT);
        ChatChannel byId = ChatChannel.fromId(key);
        if (byId != null && byId != ChatChannel.WHISPER) {
            return byId;
        }
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (shownKey(channel).equals(key)) {
                return channel;
            }
        }
        return null;
    }

    /** What is written after the {@code #} for a channel: its id. */
    public static String token(ChatChannel channel) {
        return "#" + channel.getId();
    }

    /**
     * How far a channel word reaches from {@code start}, the index just
     * past its {@code #}: over the name characters, and no further.
     */
    public static int wordEnd(String text, int start) {
        int end = start;
        while (end < text.length() && isNameCharacter(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static String shownKey(ChatChannel channel) {
        return channel.getDisplayName().toLowerCase(Locale.ROOT)
                .replace(" ", "");
    }

    /** The characters a channel word is made of; {@code &} for OOC & Discord's shown name. */
    public static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_'
                || character == '&';
    }

    public static final class Query {
        /** Where the {@code #} stands. */
        public final int hashIndex;
        /** What follows it, lower-cased. */
        public final String prefix;

        Query(int hashIndex, String prefix) {
            this.hashIndex = hashIndex;
            this.prefix = prefix;
        }
    }
}
