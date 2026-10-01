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
    INVALID_NAME_PROFANE("invalid_name_profane"),
    DUPLICATE_NAME("duplicate_name"),
    NAME_RESERVED("name_reserved"),
    /** A new character's name is the name of an account the server has seen. */
    ACCOUNT_NAME("account_name"),
    INVALID_RACE("invalid_race"),
    INVALID_GENDER("invalid_gender"),
    INVALID_SKIN("invalid_skin"),
    INVALID_BODY_TYPE("invalid_body_type"),
    INVALID_CHEST_TYPE("invalid_chest_type"),
    INVALID_PROFILE_TEXT("invalid_profile_text"),
    INVALID_PROFILE_TEXT_PROFANE("invalid_profile_text_profane"),
    INVALID_GLANCE("invalid_glance"),
    INVALID_CAPE("invalid_cape"),
    CAPE_NOT_ELIGIBLE("cape_not_eligible"),
    INVALID_AGE("invalid_age"),
    INVALID_STARTING_FACTION("invalid_starting_faction"),
    LOTR_INTEGRATION_UNAVAILABLE("lotr_integration_unavailable"),
    STARTING_FACTION_UNAVAILABLE("starting_faction_unavailable"),
    INCOMPATIBLE_RACE_FACTION("incompatible_race_faction"),
    INVALID_STARTING_WAYPOINT("invalid_starting_waypoint"),
    STARTING_WAYPOINT_UNAVAILABLE("starting_waypoint_unavailable"),
    INVALID_CHARACTER_ID("invalid_character_id"),
    CHARACTER_NOT_FOUND("character_not_found"),
    SWITCH_STORAGE_READ_ONLY("switch_storage_read_only"),
    SWITCH_PLAYER_STATE_STORAGE_READ_ONLY("switch_player_state_storage_read_only"),
    SWITCH_PLAYER_STATE_INVALID("switch_player_state_invalid"),
    /** The player's server session cannot switch: not ready yet, logging out, or the server is stopping. */
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
    DELETE_DEFAULT_CHARACTER("delete_default_character"),
    DELETE_RECOVERY_STORAGE_READ_ONLY("delete_recovery_storage_read_only"),
    DELETE_PLAYER_STATE_STORAGE_READ_ONLY("delete_player_state_storage_read_only"),
    DELETE_PLAYER_STATE_INVALID("delete_player_state_invalid"),
    DELETE_RECOVERY_LIMIT("delete_recovery_limit"),
    DELETE_RECOVERY_REQUIRED("delete_recovery_required"),
    RESTORE_NOT_ALLOWED("restore_not_allowed"),
    RESTORE_NOT_FOUND("restore_not_found"),
    RESTORE_NO_SLOT("restore_no_slot"),
    RESTORE_STATE_UNAVAILABLE("restore_state_unavailable"),
    RESTORE_STORAGE_READ_ONLY("restore_storage_read_only"),
    LORE_CHARACTER_UNKNOWN("lore_character_unknown"),
    LORE_CHARACTER_UNAVAILABLE("lore_character_unavailable"),
    LORE_CHARACTER_ALREADY_OWNED("lore_character_already_owned"),
    LORE_CHARACTER_NOT_OWNED("lore_character_not_owned"),
    LORE_CHARACTER_ACTIVE("lore_character_active"),
    LORE_CHARACTER_CANNOT_DELETE("lore_character_cannot_delete"),
    LORE_CHARACTER_CANNOT_EDIT("lore_character_cannot_edit"),
    LORE_CHARACTER_KEEPS_LOOK("lore_character_keeps_look"),
    LORE_CHARACTER_STALE_OWNERSHIP("lore_character_stale_ownership"),
    LORE_CHARACTER_TRANSFER_IN_PROGRESS("lore_character_transfer_in_progress"),
    LORE_CHARACTER_TRANSFER_STORAGE_READ_ONLY("lore_character_transfer_storage_read_only"),
    LORE_CHARACTER_OWNERSHIP_STORAGE_READ_ONLY("lore_character_ownership_storage_read_only"),
    LORE_CHARACTER_STATE_UNAVAILABLE("lore_character_state_unavailable"),
    LORE_CHARACTER_DEFINITION_INCOMPLETE("lore_character_definition_incomplete"),
    FELLOWSHIP_STORAGE_READ_ONLY("fellowship_storage_read_only"),
    FELLOWSHIP_INVITATION_STORAGE_READ_ONLY("fellowship_invitation_storage_read_only"),
    FELLOWSHIP_CLEANUP_FAILED("fellowship_cleanup_failed"),
    CAPE_UPDATE_NOT_ALLOWED("cape_update_not_allowed"),
    PROFILE_UPDATE_NOT_ALLOWED("profile_update_not_allowed"),
    LOOK_UPDATE_NOT_ALLOWED("look_update_not_allowed"),
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
