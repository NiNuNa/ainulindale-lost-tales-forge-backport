package com.ninuna.losttales.fellowship.server;

/** Stable server-side result identifiers for fellowship operations. */
public enum FellowshipErrorId {
    NONE("none"),
    INVALID_PLAYER("invalid_player"),
    CLIENT_SIDE_REQUEST("client_side_request"),
    CHARACTER_STORAGE_READ_ONLY("character_storage_read_only"),
    FELLOWSHIP_STORAGE_READ_ONLY("fellowship_storage_read_only"),
    INVITATION_STORAGE_READ_ONLY("invitation_storage_read_only"),
    MARKER_STORAGE_READ_ONLY("marker_storage_read_only"),
    CHARACTER_NOT_FOUND("character_not_found"),
    CHARACTER_ID_AMBIGUOUS("character_id_ambiguous"),
    ACTIVE_CHARACTER_CHANGED("active_character_changed"),
    TOO_MANY_FELLOWSHIPS("too_many_fellowships"),
    LEAD_LIMIT_REACHED("lead_limit_reached"),
    CREATION_DISABLED("creation_disabled"),
    NOT_IN_FELLOWSHIP("not_in_fellowship"),
    NOT_LEADER("not_leader"),
    NOT_LEADER_OR_GUIDE("not_leader_or_guide"),
    FELLOWSHIP_FULL("fellowship_full"),
    TARGET_NOT_MEMBER("target_not_member"),
    CANNOT_REMOVE_LEADER("cannot_remove_leader"),
    LEADER_MUST_HAND_OVER("leader_must_hand_over"),
    INVALID_TARGET("invalid_target"),
    TARGET_OFFLINE("target_offline"),
    TARGET_ALREADY_MEMBER("target_already_member"),
    TARGET_TOO_MANY_FELLOWSHIPS("target_too_many_fellowships"),
    ACCOUNT_ALREADY_IN_FELLOWSHIP("account_already_in_fellowship"),
    CANNOT_INVITE_SELF("cannot_invite_self"),
    INVITATION_NOT_FOUND("invitation_not_found"),
    INVITATION_EXPIRED("invitation_expired"),
    INVITATION_ALREADY_EXISTS("invitation_already_exists"),
    RECENTLY_DECLINED("recently_declined"),
    INVITATION_TARGET_MISMATCH("invitation_target_mismatch"),
    INVITATION_INVALID("invitation_invalid"),
    INVALID_REVISION("invalid_revision"),
    STALE_FELLOWSHIP_REVISION("stale_fellowship_revision"),
    STALE_FELLOWSHIP_CONTEXT("stale_fellowship_context"),
    INVALID_COLOR("invalid_color"),
    COLOR_IN_USE("color_in_use"),
    NAME_MISSING("name_missing"),
    NAME_TOO_LONG("name_too_long"),
    NAME_NOT_ALLOWED("name_not_allowed"),
    NAME_IN_USE("name_in_use"),
    INVALID_MARKER_POSITION("invalid_marker_position"),
    TOO_MANY_MARKS("too_many_marks"),
    MARK_NOT_FOUND("mark_not_found"),
    RATE_LIMITED("rate_limited"),
    MALFORMED_REQUEST("malformed_request"),
    INTERNAL_ERROR("internal_error");

    private final String id;

    FellowshipErrorId(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public static FellowshipErrorId fromId(String id) {
        if (id != null) {
            for (FellowshipErrorId value : values()) {
                if (value.id.equalsIgnoreCase(id.trim())) {
                    return value;
                }
            }
        }
        return INTERNAL_ERROR;
    }
}
