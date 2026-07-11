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
    INVALID_AGE("invalid_age"),
    INVALID_STARTING_FACTION("invalid_starting_faction"),
    LOTR_INTEGRATION_UNAVAILABLE("lotr_integration_unavailable"),
    STARTING_FACTION_UNAVAILABLE("starting_faction_unavailable"),
    INCOMPATIBLE_RACE_FACTION("incompatible_race_faction"),
    INVALID_CHARACTER_ID("invalid_character_id"),
    CHARACTER_NOT_FOUND("character_not_found"),
    SWITCH_NOT_ALLOWED("switch_not_allowed"),
    DELETE_NOT_ALLOWED("delete_not_allowed"),
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
