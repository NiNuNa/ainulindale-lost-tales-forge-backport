package com.ninuna.losttales.chat;

/**
 * The mark a server-wide line carries its message id in: an empty run
 * appended to the broadcast component, its click event's value the
 * id behind this prefix. The server stamps it as the line is sent and
 * records the line under that id; the client reads it off the line as
 * it arrives, so a reply to an achievement, a death or a join names
 * the same message on every client. Shared by both sides, so it has
 * no Minecraft dependency: the run itself is built where a component
 * can be.
 */
public final class ChatBroadcastIdMarkers {
    /** What every id mark starts with. */
    public static final String PREFIX = "losttales-chat-msgid:";

    private ChatBroadcastIdMarkers() {}

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
}
