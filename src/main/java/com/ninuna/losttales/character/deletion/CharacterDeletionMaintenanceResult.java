package com.ninuna.losttales.character.deletion;

/** Stable outcomes for operator-only deletion maintenance operations. */
public enum CharacterDeletionMaintenanceResult {
    SUCCESS,
    RECONCILED,
    NOT_FOUND,
    STORAGE_READ_ONLY,
    PLAYER_STATE_UNAVAILABLE,
    SLOT_OCCUPIED,
    CHARACTER_ID_CONFLICT,
    CHARACTER_ACTIVE,
    PREVIOUS_GENERATION_UNAVAILABLE,
    RETENTION_ACTIVE,
    NOT_COMMITTED,
    INTERNAL_ERROR
}
