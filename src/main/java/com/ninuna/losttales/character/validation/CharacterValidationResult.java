package com.ninuna.losttales.character.validation;

/** Result of a reusable server-side validation check. */
public final class CharacterValidationResult {

    private static final CharacterValidationResult SUCCESS =
            new CharacterValidationResult(true, CharacterErrorId.NONE);

    private final boolean valid;
    private final CharacterErrorId errorId;

    private CharacterValidationResult(boolean valid, CharacterErrorId errorId) {
        this.valid = valid;
        this.errorId = errorId == null ? CharacterErrorId.INTERNAL_ERROR : errorId;
    }

    public static CharacterValidationResult success() {
        return SUCCESS;
    }

    public static CharacterValidationResult failure(CharacterErrorId errorId) {
        if (errorId == null || errorId == CharacterErrorId.NONE) {
            throw new IllegalArgumentException("failure requires a non-success error id");
        }
        return new CharacterValidationResult(false, errorId);
    }

    public boolean isValid() {
        return this.valid;
    }

    public CharacterErrorId getErrorId() {
        return this.errorId;
    }
}
