package com.ninuna.losttales.character.sync;

/**
 * Which playable identity an appearance describes. The wire code is one
 * byte; a code this build does not know names no kind, and the packet
 * holding it is malformed.
 */
public enum CharacterAppearanceKind {
    /** No identity: the player left, or has nothing to show yet. */
    NONE(0),
    /** A roleplay character from the player's roster. */
    CHARACTER(1),
    /** The ghost a player is while their first character is being made, shown as the account. */
    ACCOUNT(2);

    private final int code;

    CharacterAppearanceKind(int code) {
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }

    /** The kind a wire code names; null for a code not known here. */
    public static CharacterAppearanceKind fromCode(int code) {
        for (CharacterAppearanceKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        return null;
    }
}
