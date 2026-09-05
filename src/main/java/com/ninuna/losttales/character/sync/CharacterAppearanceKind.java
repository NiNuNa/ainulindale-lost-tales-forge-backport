package com.ninuna.losttales.character.sync;

/**
 * Which playable identity an appearance describes. The wire code is
 * one byte and is never reused: a code the client does not know reads
 * as {@link #NONE}, which removes the entry the way a removal does.
 */
public enum CharacterAppearanceKind {
    /** No identity: the player left, or has nothing to show yet. */
    NONE(0),
    /** A roleplay character from the player's roster. */
    CHARACTER(1),
    /** The Minecraft account itself, played as an identity of its own. */
    ACCOUNT(2);

    private final int code;

    CharacterAppearanceKind(int code) {
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }

    /** The kind a wire code names; {@link #NONE} for a code not known here. */
    public static CharacterAppearanceKind fromCode(int code) {
        for (CharacterAppearanceKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        return NONE;
    }
}
