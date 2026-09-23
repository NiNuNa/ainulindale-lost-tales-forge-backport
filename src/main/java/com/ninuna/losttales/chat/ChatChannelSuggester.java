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

    /** A channel link read out of text: the channel, where its word ends, where the link ends, and the message it names. */
    public static final class Link {
        public final ChatChannel channel;
        /** The index just past the channel's word. */
        public final int wordEnd;
        /** The index just past the link: past the message id where one follows. */
        public final int end;
        /** The message the link names, or {@link ChatMessageIds#NONE} for the channel alone. */
        public final long messageId;

        Link(ChatChannel channel, int wordEnd, int end, long messageId) {
            this.channel = channel;
            this.wordEnd = wordEnd;
            this.end = end;
            this.messageId = messageId;
        }
    }

    /**
     * The channel link whose {@code #} stands at {@code hash}: the channel
     * its word names ({@link #resolve}) and, where a slash and digits
     * follow, the message they name. A whisper is named only by a link to
     * one of its messages, {@code #Whisper/1234}: whispers are one
     * conversation per person, so the word alone names none of them. Null
     * where the word names no channel.
     */
    public static Link linkAt(String text, int hash) {
        if (text == null || hash < 0 || hash >= text.length()
                || text.charAt(hash) != '#') {
            return null;
        }
        int end = wordEnd(text, hash + 1);
        if (end <= hash + 1) {
            return null;
        }
        String word = text.substring(hash + 1, end);
        int linkEnd = messageIdEnd(text, end);
        ChatChannel named = resolve(word);
        if (named == null && linkEnd > end
                && shownKey(ChatChannel.WHISPER).equals(word.toLowerCase(Locale.ROOT))) {
            named = ChatChannel.WHISPER;
        }
        if (named == null) {
            return null;
        }
        return new Link(named, end, linkEnd, linkEnd > end
                ? Long.parseLong(text.substring(end + 1, linkEnd))
                : ChatMessageIds.NONE);
    }

    /**
     * A link to one message of a channel as it is typed and pasted:
     * {@code #Global/1234}, the channel by its shown name without its
     * spaces — which {@link #linkAt} reads back — and the server's id of
     * the message. Null for a channel that cannot be named this way, or
     * an id the server never gave.
     */
    public static String messageLink(ChatChannel channel, long messageId) {
        if (channel == null || !ChatMessageIds.isServerId(messageId)) {
            return null;
        }
        String name = channel.getDisplayName().replaceAll("\\s+", "");
        String link = "#" + name + "/" + messageId;
        Link read = name.length() == 0 ? null : linkAt(link, 0);
        return read == null || read.channel != channel ? null : link;
    }

    /**
     * How far a message id reaches from {@code start}, the index of the
     * {@code /} after a channel word: over the digits of a server id,
     * or {@code start} itself when no id follows. An id is at most
     * eighteen digits, so it always fits a long.
     */
    public static int messageIdEnd(String text, int start) {
        if (start >= text.length() || text.charAt(start) != '/') {
            return start;
        }
        int end = start + 1;
        while (end < text.length() && end - start - 1 < 18
                && Character.isDigit(text.charAt(end))) {
            end++;
        }
        return end == start + 1 ? start : end;
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

    /** The characters a channel word is made of; {@code &} for a server's own channel name holding one. */
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
