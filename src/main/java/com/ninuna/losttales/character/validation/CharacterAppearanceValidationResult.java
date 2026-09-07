package com.ninuna.losttales.character.validation;

/** What validating a character's appearance and profile answered. */
public final class CharacterAppearanceValidationResult {

    private final CharacterErrorId errorId;
    private final ValidatedCharacterAppearance appearance;

    private CharacterAppearanceValidationResult(
            CharacterErrorId errorId, ValidatedCharacterAppearance appearance) {
        this.errorId = errorId;
        this.appearance = appearance;
    }

    public static CharacterAppearanceValidationResult success(
            ValidatedCharacterAppearance appearance) {
        if (appearance == null) {
            throw new IllegalArgumentException("appearance must not be null");
        }
        return new CharacterAppearanceValidationResult(
                CharacterErrorId.NONE, appearance);
    }

    public static CharacterAppearanceValidationResult failure(
            CharacterErrorId errorId) {
        if (errorId == null || errorId == CharacterErrorId.NONE) {
            throw new IllegalArgumentException("errorId must name a failure");
        }
        return new CharacterAppearanceValidationResult(errorId, null);
    }

    public boolean isValid() {
        return this.errorId == CharacterErrorId.NONE;
    }

    public CharacterErrorId getErrorId() {
        return this.errorId;
    }

    public ValidatedCharacterAppearance getAppearance() {
        return this.appearance;
    }
}
