package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMentions;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * What a game line's mentions become on Discord. A player cannot write a
 * Discord mention themselves: every mention or channel code typed into a
 * line — {@code <@id>}, {@code <@!id>}, {@code <@&id>}, {@code <#id>},
 * {@code @everyone}, {@code @here} — is broken with a zero-width space,
 * so Discord shows it as the text it is and pings nobody. A mention the
 * server resolved to a Discord member becomes that member's own mention
 * where they can see the channel the post goes to, at most
 * {@link #MOST_PINGS} a post, and pings them; never a role,
 * {@code @everyone} or {@code @here}. A player has no Discord account
 * behind them, so through the bridge they may do no more than a member
 * with no role at all may.
 */
final class DiscordMentions {
    /** The most Discord members one post pings. */
    static final int MOST_PINGS = 5;
    /** What breaks a code so Discord reads it as text. */
    static final char BREAK = '​';

    /** A game line as one Discord channel is to read it, and whom it pings there. */
    static final class Post {
        final String content;
        /** The Discord ids the post may ping, each written as a mention in the content. */
        final List<String> pinged;

        Post(String content, List<String> pinged) {
            this.content = content;
            this.pinged = Collections.unmodifiableList(pinged);
        }
    }

    private DiscordMentions() {}

    /**
     * The post for a line's {@code text}: every code a player could type
     * broken, and each Discord member of {@code named} whose id is in
     * {@code visible} — who can see the channel the post goes to —
     * written as their own mention, the first {@link #MOST_PINGS} of
     * them; everyone else it names stays plain text.
     */
    static Post rewrite(String text, List<ChatNamedPlayer> named,
                        Set<String> visible) {
        String line = text == null ? "" : text;
        List<ChatNamedPlayer> members = discordMembers(named, visible);
        List<String> pinged = new ArrayList<String>();
        if (members.isEmpty()) {
            return new Post(defused(line), pinged);
        }
        StringBuilder out = new StringBuilder(line.length() + 16);
        int copied = 0;
        int at = line.indexOf('@');
        while (at >= 0) {
            ChatMentions.Hit hit = pinged.size() < MOST_PINGS
                    ? ChatMentions.nameAt(line, at, members) : null;
            if (hit == null) {
                at = line.indexOf('@', at + 1);
                continue;
            }
            String userId = LostTalesChatMessagePacket.discordUserIdOf(
                    hit.player.getPlayerId());
            out.append(defused(line.substring(copied, at)))
                    .append("<@").append(userId).append('>');
            if (!pinged.contains(userId)) {
                pinged.add(userId);
            }
            copied = hit.end;
            at = line.indexOf('@', hit.end);
        }
        out.append(defused(line.substring(copied)));
        return new Post(out.toString(), pinged);
    }

    /**
     * The text with every Discord code a player could type broken: a
     * zero-width space after the {@code <} of a mention or channel code,
     * and after the {@code @} of {@code @everyone} and {@code @here}.
     */
    static String defused(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = null;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            char next = index + 1 < text.length() ? text.charAt(index + 1) : 0;
            boolean breaks = (character == '<' && (next == '@' || next == '#'))
                    || (character == '@' && (text.regionMatches(true,
                            index + 1, "everyone", 0, 8)
                            || text.regionMatches(true, index + 1, "here", 0, 4)));
            if (breaks && out == null) {
                out = new StringBuilder(text.length() + 8);
                out.append(text, 0, index);
            }
            if (out != null) {
                out.append(character);
                if (breaks) {
                    out.append(BREAK);
                }
            }
        }
        return out == null ? text : out.toString();
    }

    /** The Discord members of {@code named} who can see the post's channel. */
    private static List<ChatNamedPlayer> discordMembers(List<ChatNamedPlayer> named,
                                                        Set<String> visible) {
        List<ChatNamedPlayer> members = new ArrayList<ChatNamedPlayer>();
        if (named == null || visible == null || visible.isEmpty()) {
            return members;
        }
        for (ChatNamedPlayer player : named) {
            String userId = player == null ? ""
                    : LostTalesChatMessagePacket.discordUserIdOf(player.getPlayerId());
            if (userId.length() > 0 && visible.contains(userId)) {
                members.add(player);
            }
        }
        return members;
    }
}
