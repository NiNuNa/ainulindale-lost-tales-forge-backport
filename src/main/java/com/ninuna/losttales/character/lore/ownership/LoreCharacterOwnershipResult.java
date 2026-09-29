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
        CHARACTER_ID_CONFLICT,
        RECORD_LIMIT_REACHED,
        STORAGE_READ_ONLY,
        INVALID_REQUEST
    }

    private final Status status;

    private LoreCharacterOwnershipResult(Status status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
    }

    public static LoreCharacterOwnershipResult of(Status status) {
        return new LoreCharacterOwnershipResult(status);
    }

    public Status getStatus() {
        return this.status;
    }
}
