package com.ninuna.losttales.chat;

/**
 * What an identity is doing in the role-play, as Total RP 3's status
 * says it: in character, out of it, or looking for a scene to join (P4 a).
 * Every identity has its own, kept as its status line is: a character
 * starts In Character and the account Out of Character. Every status is
 * one byte on the wire, by its place here, so the order is permanent.
 */
public enum ChatRoleplayStatus {
    IN_CHARACTER("in_character"),
    OUT_OF_CHARACTER("out_of_character"),
    LOOKING_FOR_SCENE("looking_for_scene");

    private final String id;

    ChatRoleplayStatus(String id) {
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
    public static ChatRoleplayStatus fromCode(int code) {
        ChatRoleplayStatus[] all = values();
        return code < 0 || code >= all.length ? null : all[code];
    }

    /** The status with that id; null for one this build does not know. */
    public static ChatRoleplayStatus fromId(String id) {
        for (ChatRoleplayStatus status : values()) {
            if (status.id.equals(id)) {
                return status;
            }
        }
        return null;
    }

    /** What an identity is until it says otherwise: a character in character, the account out of it. */
    public static ChatRoleplayStatus defaultFor(ChatPresenceIdentity identity) {
        return identity == null || identity.isAccount() ? OUT_OF_CHARACTER
                : IN_CHARACTER;
    }

    /** The lang key of the status's name. */
    public String labelKey() {
        return "gui.losttales.chat.roleplay." + this.id;
    }
}
