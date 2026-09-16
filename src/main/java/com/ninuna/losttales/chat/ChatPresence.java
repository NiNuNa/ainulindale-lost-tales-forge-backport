package com.ninuna.losttales.chat;

/**
 * A player's presence as the chat shows it to everyone else: at the
 * keyboard, away from it, or not to be disturbed. The account's,
 * whichever character it plays. Online is the resting state and travels
 * as the absence of an entry; Away sets in on its own after idle time
 * on the player's client and lifts on their first move; Do Not Disturb
 * holds this player's own mention cues silent. Every status is one
 * byte on the wire, by its place here, so the order is permanent.
 */
public enum ChatPresence {
    ONLINE("online"),
    AWAY("away"),
    DO_NOT_DISTURB("do_not_disturb");

    private final String id;

    ChatPresence(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    /** The status's wire code: its place here. */
    public int code() {
        return ordinal();
    }

    /** The status a wire code names; null for one this build does not know. */
    public static ChatPresence fromCode(int code) {
        ChatPresence[] all = values();
        return code < 0 || code >= all.length ? null : all[code];
    }

    /** The lang key of the status's name. */
    public String labelKey() {
        return "gui.losttales.chat.status." + this.id;
    }
}
