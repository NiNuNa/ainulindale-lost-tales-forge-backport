package com.ninuna.losttales.fellowship.sync;

/** Stable network identifiers for fellowship requests and operation feedback. */
public enum FellowshipOperationType {
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
    RENAME(13, "rename"),
    SET_GUIDE(14, "set_guide"),
    SET_ICON(15, "set_icon"),
    SET_SWITCH(16, "set_switch"),
    SET_TRAVELLING(17, "set_travelling"),
    PLACE_MARK(18, "place_mark"),
    MOVE_MARK(19, "move_mark"),
    REMOVE_MARK(20, "remove_mark"),
    UNKNOWN(255, "unknown");

    private final int networkId;
    private final String id;

    FellowshipOperationType(int networkId, String id) {
        this.networkId = networkId;
        this.id = id;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public String getId() {
        return this.id;
    }

    /** Whether the request acts on one fellowship, which it names with the revision it saw. */
    public boolean requiresFellowshipRevision() {
        return this == LEAVE
                || this == REMOVE_MEMBER
                || this == DISBAND
                || this == TRANSFER_LEADERSHIP
                || this == SET_COLOR
                || this == RENAME
                || this == INVITE_PLAYER
                || this == CANCEL_INVITATION
                || this == SET_GUIDE
                || this == SET_ICON
                || this == SET_SWITCH
                || this == SET_TRAVELLING
                || this == PLACE_MARK
                || this == MOVE_MARK
                || this == REMOVE_MARK;
    }

    public boolean requiresTargetId() {
        return this == REMOVE_MEMBER
                || this == TRANSFER_LEADERSHIP
                || this == SET_GUIDE
                || this == INVITE_PLAYER
                || this == ACCEPT_INVITATION
                || this == DECLINE_INVITATION
                || this == CANCEL_INVITATION
                || this == MOVE_MARK
                || this == REMOVE_MARK;
    }

    /** Whether the request carries a place on the map: a go-here marker set, a mark placed or moved. */
    public boolean requiresMapPosition() {
        return this == SET_GO_HERE_MARKER || this == PLACE_MARK
                || this == MOVE_MARK;
    }

    public boolean requiresColor() {
        return this == SET_COLOR;
    }

    /** A new fellowship, a rename and a mark placed carry a name. */
    public boolean requiresName() {
        return this == RENAME || this == CREATE || this == PLACE_MARK;
    }

    /**
     * Whether the request carries a value byte: a guide made or unmade, or
     * a switch and whether it goes on ({@link #switchValue}).
     */
    public boolean requiresValue() {
        return this == SET_GUIDE || this == SET_SWITCH;
    }

    /** The value byte a switch request carries: the switch, and whether it goes on. */
    public static int switchValue(int switchNetworkId, boolean on) {
        return (switchNetworkId << 1) | (on ? 1 : 0);
    }

    public static FellowshipOperationType fromNetworkId(int networkId) {
        for (FellowshipOperationType value : values()) {
            if (value.networkId == networkId) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
