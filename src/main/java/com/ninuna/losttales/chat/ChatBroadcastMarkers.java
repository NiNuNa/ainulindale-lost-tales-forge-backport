package com.ninuna.losttales.chat;

import java.nio.charset.Charset;
import java.util.UUID;

/**
 * The marks a server line carries its facts in: empty runs appended to
 * the component, each with a click event whose value holds one fact
 * behind its own prefix. The server stamps them as the line is sent and
 * the client takes them off the line as it arrives, so they are never
 * shown. One mark carries the line's message id, which the server records
 * the line under, so a reply to an achievement, a death or a join names
 * the same message on every client. The others each carry a player the
 * line names, as the server knew them when the line was said, so a
 * mention in the line reaches the right person and opens their card on
 * every client, whether or not that client can place the name, and after
 * the player has gone. Shared by both sides, so it has no Minecraft
 * dependency: the runs themselves are built where a component can be.
 */
public final class ChatBroadcastMarkers {
    /** What every id mark starts with. */
    public static final String PREFIX = "losttales-chat-msgid:";
    /** What every named-player mark starts with. */
    public static final String NAMED_PREFIX = "losttales-chat-named:";
    /**
     * Parts a named player's fields. An id, an account name and a skin id
     * never hold it; the identity's name, which might, comes last.
     */
    private static final char SEPARATOR = '|';
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ChatBroadcastMarkers() {}

    /** The mark for a message id. */
    public static String value(long messageId) {
        return PREFIX + messageId;
    }

    /**
     * The message id a mark names, or {@link ChatMessageIds#NONE} for a
     * value that is not an id mark or names no server id.
     */
    public static long decode(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            return ChatMessageIds.NONE;
        }
        try {
            long id = Long.parseLong(value.substring(PREFIX.length()));
            return ChatMessageIds.isServerId(id) ? id : ChatMessageIds.NONE;
        } catch (NumberFormatException ignored) {
            return ChatMessageIds.NONE;
        }
    }

    /**
     * The mark for a player a line names: their account's id and name,
     * the character they were playing and its skin, and the name their
     * lines were signed with. Null for a player nobody could place.
     */
    public static String namedValue(ChatNamedPlayer player) {
        if (player == null || player.getPlayerId() == null
                || !player.isValid()) {
            return null;
        }
        return NAMED_PREFIX + player.getPlayerId() + SEPARATOR
                + player.getAccount() + SEPARATOR
                + (player.getCharacterId() == null ? ""
                        : player.getCharacterId().toString()) + SEPARATOR
                + player.getSkinId().replace(SEPARATOR, ' ') + SEPARATOR
                + player.getIdentityName();
    }

    /**
     * The player a named mark carries, or null for a value that is not
     * one, or whose fields do not hold together or run past the bounds a
     * line's named players keep.
     */
    public static ChatNamedPlayer decodeNamed(String value) {
        if (value == null || !value.startsWith(NAMED_PREFIX)) {
            return null;
        }
        String[] fields = value.substring(NAMED_PREFIX.length())
                .split("\\|", 5);
        if (fields.length != 5
                || fields[1].length() == 0
                || bytes(fields[1]) > ChatNamedPlayer.MAX_ACCOUNT_BYTES
                || bytes(fields[3]) > ChatNamedPlayer.MAX_SKIN_ID_BYTES
                || bytes(fields[4]) > ChatNamedPlayer.MAX_IDENTITY_BYTES) {
            return null;
        }
        try {
            UUID playerId = UUID.fromString(fields[0]);
            UUID characterId = fields[2].length() == 0 ? null
                    : UUID.fromString(fields[2]);
            ChatNamedPlayer player = new ChatNamedPlayer(playerId, fields[1],
                    characterId, fields[4], fields[3]);
            return player.isValid() ? player : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static int bytes(String text) {
        return text.getBytes(UTF_8).length;
    }
}
