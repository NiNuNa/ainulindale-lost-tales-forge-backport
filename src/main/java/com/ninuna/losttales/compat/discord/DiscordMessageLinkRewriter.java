package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Links to messages, translated at the bridge. In the game a message is
 * linked to as {@code #Channel/<id>}, the server's own id; on Discord a
 * message is linked to by its jump URL,
 * {@code https://discord.com/channels/<guild>/<channel>/<message>}. A
 * line crossing the bridge has each link spelled the way the other side
 * reads it, as far as the bridge knows both names of the message: a
 * link it cannot translate is left as it was typed, so a reader still
 * sees that a message was pointed at. What the bridge knows is the
 * {@link Resolver}'s: this class only finds the links and asks.
 */
final class DiscordMessageLinkRewriter {
    /**
     * A Discord jump URL: the guild, the channel and the message, on
     * any of Discord's hosts.
     */
    static final Pattern JUMP_URL = Pattern.compile(
            "https?://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/channels/"
                    + "(\\d{1,24})/(\\d{1,24})/(\\d{1,24})(?![\\w/])");

    private DiscordMessageLinkRewriter() {}

    /** What the bridge knows about a message's two names. */
    interface Resolver {
        /**
         * The Discord jump URL of the game message {@code messageId},
         * said in {@code channel}, for a post going where the caller is
         * posting; empty when the bridge holds no Discord copy of it.
         */
        String jumpUrl(ChatChannel channel, long messageId);

        /**
         * The game's {@code #Channel/<id>} for the Discord message, or
         * empty when the bridge never carried it, or the channel is not
         * bound to any of the game's.
         */
        String gameLink(String guildId, String channelId, String discordMessageId);
    }

    /**
     * A game line for Discord: every {@code #Channel/<id>} the bridge
     * can place becomes the message's bare jump URL, which Discord draws
     * as its own link to a message — the channel's name and a bubble,
     * as a link pasted on Discord is drawn; a masked link would read as
     * plain text. Every other stays as typed.
     */
    static String outbound(String text, Resolver resolver) {
        if (text == null || resolver == null || text.indexOf('#') < 0) {
            return text == null ? "" : text;
        }
        StringBuilder result = new StringBuilder(text.length() + 64);
        int literalStart = 0;
        int cursor = 0;
        while (cursor < text.length()) {
            int hash = text.indexOf('#', cursor);
            if (hash < 0) {
                break;
            }
            boolean opensWord = hash == 0
                    || Character.isWhitespace(text.charAt(hash - 1));
            ChatChannelSuggester.Link link = opensWord
                    ? ChatChannelSuggester.linkAt(text, hash) : null;
            if (link == null || link.messageId == ChatMessageIds.NONE) {
                cursor = hash + 1;
                continue;
            }
            ChatChannel named = link.channel;
            int linkEnd = link.end;
            long messageId = link.messageId;
            String url = ChatMessageIds.isServerId(messageId)
                    ? resolver.jumpUrl(named, messageId) : "";
            if (url == null || url.length() == 0) {
                cursor = linkEnd;
                continue;
            }
            result.append(text, literalStart, hash);
            result.append(url);
            literalStart = linkEnd;
            cursor = linkEnd;
        }
        if (literalStart == 0) {
            return text;
        }
        result.append(text, literalStart, text.length());
        return result.toString();
    }

    /**
     * A Discord line for the game: every jump URL the bridge carried
     * the message of becomes {@code #Channel/<id>}; every other stays
     * the URL it is, which the chat shows as a link.
     */
    static String inbound(String text, Resolver resolver) {
        if (text == null || resolver == null
                || text.indexOf("/channels/") < 0) {
            return text == null ? "" : text;
        }
        Matcher matcher = JUMP_URL.matcher(text);
        StringBuffer result = new StringBuffer(text.length());
        boolean changed = false;
        while (matcher.find()) {
            String link = resolver.gameLink(matcher.group(1), matcher.group(2),
                    matcher.group(3));
            if (link == null || link.length() == 0) {
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(link));
                changed = true;
            }
        }
        if (!changed) {
            return text;
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** The jump URL of a Discord message, as Discord itself writes one. */
    static String jumpUrl(String guildId, String channelId, String discordMessageId) {
        return "https://discord.com/channels/" + guildId + "/" + channelId
                + "/" + discordMessageId;
    }
}
