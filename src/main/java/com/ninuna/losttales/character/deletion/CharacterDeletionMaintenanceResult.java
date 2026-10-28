package com.ninuna.losttales.character.deletion;

/** What an operator's restore, rollback or purge of a character came to. */
public enum CharacterDeletionMaintenanceResult {
    SUCCESS,
    RECONCILED,
    NOT_FOUND,
    STORAGE_READ_ONLY,
    PLAYER_STATE_UNAVAILABLE,
    SLOT_OCCUPIED,
    NAME_TAKEN,
    CHARACTER_ID_CONFLICT,
    CHARACTER_ACTIVE,
    /** The account's switch journal is held; it names generations a rollback would replace. */
    JOURNAL_HELD,
    PREVIOUS_GENERATION_UNAVAILABLE,
    RETENTION_ACTIVE,
    RETENTION_OVER,
    NOT_COMMITTED,
    INTERNAL_ERROR
}
