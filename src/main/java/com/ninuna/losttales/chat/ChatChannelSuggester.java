package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Channels named in a message the way Discord names them: {@code #} and
 * the channel's code name ({@link ChatCodeNames}), {@code #ooc},
 * {@code #global}, {@code #gondor} for Gondor's chat. A channel is written
 * one way only, in links to one message too ({@code #ooc/1234}); the chat
 * draws it with the channel's shown name. What is typed behind a {@code #}
 * is the query the completion list answers, exactly as {@code @} opens
 * the mention list. No client dependency: the rules are the same on both
 * sides and testable without a game.
 */
public final class ChatChannelSuggester {
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
                || clamped - index - 1 > ChatCodeNames.MAX_LENGTH) {
            return null;
        }
        if (index > 0 && !Character.isWhitespace(text.charAt(index - 1))) {
            return null;
        }
        return new Query(index, text.substring(index + 1, clamped)
                .toLowerCase(Locale.ROOT));
    }

    /**
     * The channels the prefix opens, in the order given: a channel whose
     * code name or shown name starts with it, an empty prefix every one.
     * The Faction channel stands for the faction {@code factionScope}
     * names, and is left out while it names none.
     */
    public static List<ChatChannel> matches(String prefix,
                                            List<ChatChannel> channels,
                                            String factionScope, int limit) {
        if (prefix == null || channels == null || limit <= 0) {
            return Collections.emptyList();
        }
        String query = prefix.toLowerCase(Locale.ROOT);
        List<ChatChannel> result = new ArrayList<ChatChannel>();
        for (ChatChannel channel : channels) {
            String word = channel == null ? null
                    : ChatCodeNames.of(channel, scopeFor(channel, factionScope));
            if (word == null || channel == ChatChannel.WHISPER) {
                continue;
            }
            if (word.startsWith(query) || shownKey(channel).startsWith(query)) {
                result.add(channel);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * What is written for a channel: {@code #} and its code name, the
     * faction {@code factionScope} names for the Faction channel. Null
     * where the channel has no code name.
     */
    public static String token(ChatChannel channel, String factionScope) {
        String word = ChatCodeNames.of(channel, scopeFor(channel, factionScope));
        return word == null ? null : "#" + word;
    }

    /** A channel link read out of text: the conversation, where its word ends, where the link ends, and the message it names. */
    public static final class Link {
        public final ChatChannel channel;
        /** The faction's id for a link to a faction's chat; empty otherwise. */
        public final String scope;
        /** The index just past the channel's word. */
        public final int wordEnd;
        /** The index just past the link: past the message id where one follows. */
        public final int end;
        /** The message the link names, or {@link ChatMessageIds#NONE} for the channel alone. */
        public final long messageId;

        Link(ChatChannel channel, String scope, int wordEnd, int end,
             long messageId) {
            this.channel = channel;
            this.scope = scope;
            this.wordEnd = wordEnd;
            this.end = end;
            this.messageId = messageId;
        }
    }

    /**
     * The channel link whose {@code #} stands at {@code hash}: the
     * conversation its word names and, where a slash and digits follow,
     * the message they name. A whisper is named only by a link to one of
     * its messages, {@code #whisper/1234}: whispers are one conversation
     * per person, so the word alone names none of them. Null where the
     * word names no channel.
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
        ChatCodeNames.Named named = ChatCodeNames.parse(
                text.substring(hash + 1, end));
        int linkEnd = messageIdEnd(text, end);
        if (named == null
                || (named.channel == ChatChannel.WHISPER && linkEnd == end)) {
            return null;
        }
        return new Link(named.channel, named.scope, end, linkEnd,
                linkEnd > end ? Long.parseLong(text.substring(end + 1, linkEnd))
                        : ChatMessageIds.NONE);
    }

    /**
     * A link to one message as it is typed and pasted: {@code #ooc/1234},
     * the conversation's code name and the server's id of the message.
     * Null for a conversation without a code name, or an id the server
     * never gave.
     */
    public static String messageLink(ChatChannel channel, String scope,
                                     long messageId) {
        String word = ChatCodeNames.of(channel, scope);
        if (word == null || !ChatMessageIds.isServerId(messageId)) {
            return null;
        }
        return "#" + word + "/" + messageId;
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

    /** The characters a channel word is made of. */
    public static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    /** The scope a channel's code name needs: the faction for the Faction channel alone. */
    private static String scopeFor(ChatChannel channel, String factionScope) {
        return channel == ChatChannel.FACTION ? factionScope : "";
    }

    /** The shown name without its spaces: what the completion list also matches. */
    private static String shownKey(ChatChannel channel) {
        return channel.getDisplayName().toLowerCase(Locale.ROOT)
                .replace(" ", "");
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
