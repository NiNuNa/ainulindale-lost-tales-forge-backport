package com.ninuna.losttales.character.state;

/** Raised when persisted or captured character state is not safe to apply. */
public final class CharacterStateValidationException extends Exception {

    private static final long serialVersionUID = 1L;

    public CharacterStateValidationException(String message) {
        super(message);
    }

    public CharacterStateValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
