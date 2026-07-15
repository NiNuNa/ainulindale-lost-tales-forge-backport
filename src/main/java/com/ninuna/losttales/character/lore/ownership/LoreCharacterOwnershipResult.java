package com.ninuna.losttales.character.lore.ownership;

/** Result of one atomic low-level lore-character ownership mutation. */
public final class LoreCharacterOwnershipResult {

    public enum Status {
        CLAIMED,
        RELEASED,
        ALREADY_OWNED_BY_REQUESTER,
        ALREADY_CLAIMED,
        ALREADY_RELEASED,
        NOT_CLAIMED,
        NOT_OWNER,
        STALE_REVISION,
        UNKNOWN_LORE_CHARACTER,
        APPEARANCE_NOT_CONFIGURED,
        DEFINITION_REGISTRY_INVALID,
        CHARACTER_ID_CONFLICT,
        RECORD_LIMIT_REACHED,
        STORAGE_READ_ONLY,
        INVALID_REQUEST
    }

    private final Status status;
    private final LoreCharacterOwnershipRecord record;

    private LoreCharacterOwnershipResult(
            Status status, LoreCharacterOwnershipRecord record) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        this.record = record;
    }

    public static LoreCharacterOwnershipResult of(
            Status status, LoreCharacterOwnershipRecord record) {
        return new LoreCharacterOwnershipResult(status, record);
    }

    public Status getStatus() {
        return this.status;
    }

    public LoreCharacterOwnershipRecord getRecord() {
        return this.record;
    }

    public boolean changed() {
        return this.status == Status.CLAIMED || this.status == Status.RELEASED;
    }

    public boolean isSuccessfulOrIdempotent() {
        return changed()
                || this.status == Status.ALREADY_OWNED_BY_REQUESTER;
    }
}
