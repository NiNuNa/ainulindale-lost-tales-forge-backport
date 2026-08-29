package com.ninuna.losttales.chat;

/**
 * What a message id means. The ids themselves are the server's to hand
 * out ({@code ChatMessageIdAllocator}); what the numbers stand for is
 * shared, because both sides read them.
 *
 * <p>A chat line id is each client's own, reused as its history trims,
 * and no two clients mean the same thing by one. A message id is the
 * server's word on which message is which, so anything naming a message
 * afterwards — a reply, an edit, a moderator's removal —
 * names it by this.</p>
 */
public final class ChatMessageIds {
    /** Not a message: a line nobody can name — a notice, a stray print. */
    public static final long NONE = 0L;

    private ChatMessageIds() {}

    /** Whether the id is one a server handed out, and so can be sent. */
    public static boolean isServerId(long messageId) {
        return messageId > NONE;
    }

    /**
     * Whether the id belongs to the client that made it, for a line it
     * wrote itself and never sends — its half of an NPC conversation,
     * and the NPC's speech. Kept apart from the server's by its sign, so
     * one can never be mistaken for the other.
     */
    public static boolean isLocalId(long messageId) {
        return messageId < NONE;
    }
}
