package com.ninuna.losttales.chat;

/**
 * A chat identity's presence: at the keyboard, away from it, not to be
 * disturbed, hidden, or gone. Every identity has its own — the account
 * that speaks in the out-of-character channels and each of its
 * characters — and only the identities a player is using show one at
 * all: the account, the character they play and the character they
 * speak as. Every other identity reads as Offline.
 *
 * <p>Online, Away, Do Not Disturb and Invisible are what a player
 * chooses; Offline is only ever shown. Invisible is shown to everyone
 * else as Offline. Away also sets in on its own while nobody is at the
 * keyboard, over an identity whose choice is Online. Do Not Disturb
 * holds this player's mention cues silent where they speak as that
 * identity. Every status is one byte on the wire, by its place here, so
 * the order is permanent.</p>
 */
public enum ChatPresence {
    ONLINE("online"),
    AWAY("away"),
    DO_NOT_DISTURB("do_not_disturb"),
    INVISIBLE("invisible"),
    OFFLINE("offline");

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

    /** The status with that id; null for one this build does not know. */
    public static ChatPresence fromId(String id) {
        for (ChatPresence presence : values()) {
            if (presence.id.equals(id)) {
                return presence;
            }
        }
        return null;
    }

    /** Whether a player may choose it: every status but Offline, which is only shown. */
    public boolean isChoosable() {
        return this != OFFLINE;
    }

    /** What everyone else is shown of this choice: Invisible reads as Offline. */
    public ChatPresence shownToOthers() {
        return this == INVISIBLE ? OFFLINE : this;
    }

    /** The lang key of the status's name. */
    public String labelKey() {
        return "gui.losttales.chat.status." + this.id;
    }
}
