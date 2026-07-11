package com.ninuna.losttales.character.validation;

/** Creation validation result carrying canonical values on success. */
public final class CharacterCreationValidationResult {

    private final CharacterValidationResult validation;
    private final ValidatedCharacterCreation creation;

    private CharacterCreationValidationResult(CharacterValidationResult validation,
                                               ValidatedCharacterCreation creation) {
        this.validation = validation;
        this.creation = creation;
    }

    public static CharacterCreationValidationResult success(
            ValidatedCharacterCreation creation) {
        if (creation == null) {
            throw new IllegalArgumentException("creation must not be null");
        }
        return new CharacterCreationValidationResult(
                CharacterValidationResult.success(), creation);
    }

    public static CharacterCreationValidationResult failure(CharacterErrorId errorId) {
        return new CharacterCreationValidationResult(
                CharacterValidationResult.failure(errorId), null);
    }

    public boolean isValid() {
        return this.validation.isValid();
    }

    public CharacterErrorId getErrorId() {
        return this.validation.getErrorId();
    }

    public ValidatedCharacterCreation getCreation() {
        return this.creation;
    }
}
