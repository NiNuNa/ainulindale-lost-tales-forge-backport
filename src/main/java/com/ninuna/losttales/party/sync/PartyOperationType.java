package com.ninuna.losttales.party.sync;

/** Stable network identifiers for party requests and operation feedback. */
public enum PartyOperationType {
    REQUEST_STATE(0, "request_state"),
    CREATE(1, "create"),
    LEAVE(2, "leave"),
    REMOVE_MEMBER(3, "remove_member"),
    DISBAND(4, "disband"),
    TRANSFER_LEADERSHIP(5, "transfer_leadership"),
    SET_COLOR(6, "set_color"),
    INVITE_PLAYER(7, "invite_player"),
    ACCEPT_INVITATION(8, "accept_invitation"),
    DECLINE_INVITATION(9, "decline_invitation"),
    CANCEL_INVITATION(10, "cancel_invitation"),
    SET_GO_HERE_MARKER(11, "set_go_here_marker"),
    REMOVE_GO_HERE_MARKER(12, "remove_go_here_marker"),
    UNKNOWN(255, "unknown");

    private final int networkId;
    private final String id;

    PartyOperationType(int networkId, String id) {
        this.networkId = networkId;
        this.id = id;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public String getId() {
        return this.id;
    }

    public boolean requiresPartyRevision() {
        return this == LEAVE
                || this == REMOVE_MEMBER
                || this == DISBAND
                || this == TRANSFER_LEADERSHIP
                || this == SET_COLOR
                || this == INVITE_PLAYER
                || this == CANCEL_INVITATION
                || this == SET_GO_HERE_MARKER
                || this == REMOVE_GO_HERE_MARKER;
    }

    public boolean requiresTargetId() {
        return this == REMOVE_MEMBER
                || this == TRANSFER_LEADERSHIP
                || this == INVITE_PLAYER
                || this == ACCEPT_INVITATION
                || this == DECLINE_INVITATION
                || this == CANCEL_INVITATION;
    }

    public boolean requiresColor() {
        return this == SET_COLOR;
    }

    public static PartyOperationType fromNetworkId(int networkId) {
        for (PartyOperationType value : values()) {
            if (value.networkId == networkId) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
