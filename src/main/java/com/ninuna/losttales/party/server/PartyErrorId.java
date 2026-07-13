package com.ninuna.losttales.party.server;

/** Stable server-side result identifiers for party operations. */
public enum PartyErrorId {
    NONE("none"),
    INVALID_PLAYER("invalid_player"),
    CLIENT_SIDE_REQUEST("client_side_request"),
    CHARACTER_STORAGE_READ_ONLY("character_storage_read_only"),
    PARTY_STORAGE_READ_ONLY("party_storage_read_only"),
    INVITATION_STORAGE_READ_ONLY("invitation_storage_read_only"),
    MARKER_STORAGE_READ_ONLY("marker_storage_read_only"),
    NO_ACTIVE_CHARACTER("no_active_character"),
    CHARACTER_NOT_FOUND("character_not_found"),
    CHARACTER_ID_AMBIGUOUS("character_id_ambiguous"),
    ACTIVE_CHARACTER_CHANGED("active_character_changed"),
    PARTY_NOT_FOUND("party_not_found"),
    ALREADY_IN_PARTY("already_in_party"),
    NOT_IN_PARTY("not_in_party"),
    NOT_LEADER("not_leader"),
    PARTY_FULL("party_full"),
    TARGET_NOT_MEMBER("target_not_member"),
    CANNOT_REMOVE_LEADER("cannot_remove_leader"),
    INVALID_TARGET("invalid_target"),
    TARGET_OFFLINE("target_offline"),
    TARGET_NO_ACTIVE_CHARACTER("target_no_active_character"),
    TARGET_ALREADY_IN_PARTY("target_already_in_party"),
    CANNOT_INVITE_SELF("cannot_invite_self"),
    INVITATION_NOT_FOUND("invitation_not_found"),
    INVITATION_EXPIRED("invitation_expired"),
    INVITATION_ALREADY_EXISTS("invitation_already_exists"),
    INVITATION_TARGET_MISMATCH("invitation_target_mismatch"),
    INVITATION_INVALID("invitation_invalid"),
    INVALID_REVISION("invalid_revision"),
    STALE_PARTY_REVISION("stale_party_revision"),
    STALE_PARTY_CONTEXT("stale_party_context"),
    INVALID_COLOR("invalid_color"),
    COLOR_IN_USE("color_in_use"),
    INVALID_MARKER_POSITION("invalid_marker_position"),
    RATE_LIMITED("rate_limited"),
    MALFORMED_REQUEST("malformed_request"),
    INTERNAL_ERROR("internal_error");

    private final String id;

    PartyErrorId(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public static PartyErrorId fromId(String id) {
        if (id != null) {
            for (PartyErrorId value : values()) {
                if (value.id.equalsIgnoreCase(id.trim())) {
                    return value;
                }
            }
        }
        return INTERNAL_ERROR;
    }
}
