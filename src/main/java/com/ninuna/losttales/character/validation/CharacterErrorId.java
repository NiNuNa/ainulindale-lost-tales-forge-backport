package com.ninuna.losttales.character.validation;

/** Stable, non-localized identifiers returned by authoritative operations. */
public enum CharacterErrorId {
    NONE("none"),
    INVALID_PLAYER("invalid_player"),
    CLIENT_SIDE_REQUEST("client_side_request"),
    STORAGE_READ_ONLY("storage_read_only"),
    MALFORMED_REQUEST("malformed_request"),
    RATE_LIMITED("rate_limited"),
    STALE_ROSTER("stale_roster"),
    PLAYER_DEAD("player_dead"),
    PLAYER_SLEEPING("player_sleeping"),
    INVALID_SLOT("invalid_slot"),
    SLOT_HIDDEN("slot_hidden"),
    SLOT_OCCUPIED("slot_occupied"),
    MAX_CHARACTERS("max_characters"),
    INVALID_NAME_EMPTY("invalid_name_empty"),
    INVALID_NAME_LENGTH("invalid_name_length"),
    INVALID_NAME_CHARACTERS("invalid_name_characters"),
    DUPLICATE_NAME("duplicate_name"),
    INVALID_RACE("invalid_race"),
    INVALID_GENDER("invalid_gender"),
    INVALID_SKIN("invalid_skin"),
    INVALID_CAPE("invalid_cape"),
    CAPE_NOT_ELIGIBLE("cape_not_eligible"),
    INVALID_AGE("invalid_age"),
    INVALID_STARTING_FACTION("invalid_starting_faction"),
    LOTR_INTEGRATION_UNAVAILABLE("lotr_integration_unavailable"),
    STARTING_FACTION_UNAVAILABLE("starting_faction_unavailable"),
    INCOMPATIBLE_RACE_FACTION("incompatible_race_faction"),
    INVALID_CHARACTER_ID("invalid_character_id"),
    CHARACTER_NOT_FOUND("character_not_found"),
    SWITCH_NOT_ALLOWED("switch_not_allowed"),
    SWITCH_STORAGE_READ_ONLY("switch_storage_read_only"),
    SWITCH_PLAYER_STATE_STORAGE_READ_ONLY("switch_player_state_storage_read_only"),
    SWITCH_PLAYER_STATE_INVALID("switch_player_state_invalid"),
    SWITCH_STATE_IMPORT_REQUIRED("switch_state_import_required"),
    SWITCH_PLAYER_NOT_READY("switch_player_not_ready"),
    SWITCH_SESSION_CHANGED("switch_session_changed"),
    SWITCH_ALREADY_IN_PROGRESS("switch_already_in_progress"),
    SWITCH_RESPAWNING("switch_respawning"),
    SWITCH_DEATH_PENDING("switch_death_pending"),
    SWITCH_CHANGING_DIMENSION("switch_changing_dimension"),
    SWITCH_IN_COMBAT("switch_in_combat"),
    SWITCH_RIDING("switch_riding"),
    SWITCH_FAST_TRAVEL("switch_fast_travel"),
    SWITCH_TELEPORTING("switch_teleporting"),
    SWITCH_SWIMMING("switch_swimming"),
    SWITCH_UNSAFE_MOVEMENT("switch_unsafe_movement"),
    SWITCH_CONTAINER_OPEN("switch_container_open"),
    SWITCH_ITEM_IN_CURSOR("switch_item_in_cursor"),
    SWITCH_USING_ITEM("switch_using_item"),
    SWITCH_COOLDOWN("switch_cooldown"),
    SWITCH_ACCOUNT_FROZEN("switch_account_frozen"),
    SWITCH_RECOVERY_REQUIRED("switch_recovery_required"),
    DELETE_NOT_ALLOWED("delete_not_allowed"),
    DELETE_ACTIVE_CHARACTER("delete_active_character"),
    DELETE_RECOVERY_STORAGE_READ_ONLY("delete_recovery_storage_read_only"),
    DELETE_PLAYER_STATE_STORAGE_READ_ONLY("delete_player_state_storage_read_only"),
    DELETE_PLAYER_STATE_INVALID("delete_player_state_invalid"),
    DELETE_RECOVERY_LIMIT("delete_recovery_limit"),
    DELETE_RECOVERY_REQUIRED("delete_recovery_required"),
    PARTY_STORAGE_READ_ONLY("party_storage_read_only"),
    PARTY_INVITATION_STORAGE_READ_ONLY("party_invitation_storage_read_only"),
    PARTY_CLEANUP_FAILED("party_cleanup_failed"),
    CAPE_UPDATE_NOT_ALLOWED("cape_update_not_allowed"),
    INTERNAL_ERROR("internal_error");

    private final String id;

    CharacterErrorId(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public static CharacterErrorId fromId(String id) {
        if (id != null) {
            for (CharacterErrorId value : values()) {
                if (value.id.equals(id)) {
                    return value;
                }
            }
        }
        return INTERNAL_ERROR;
    }
}
