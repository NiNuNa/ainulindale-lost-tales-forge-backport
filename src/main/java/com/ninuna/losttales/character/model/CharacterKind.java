package com.ninuna.losttales.character.model;

import java.util.Locale;

/**
 * Which kind of playable identity a character record is.
 *
 * <p>Every account has exactly one {@link #DEFAULT} character once the
 * world has seen it: the account's own baseline identity, the one the
 * roster shows first and the one a player is on before they make anyone
 * else. It is a character like any other — the same switch, the same
 * saved state, the same chat identity — and differs only in that it is
 * always there and cannot be deleted.</p>
 *
 * <p>The id is save surface: it is written into every character record
 * and read back by name, so a constant may be added but none renamed.</p>
 */
public enum CharacterKind {
    /** The account's own identity, made when the world first sees it. */
    DEFAULT("default"),
    /** A character the player made. */
    ROLEPLAY("roleplay");

    private final String id;

    CharacterKind(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    /**
     * The kind an id names. Anything unknown — no kind at all, or one
     * from a build that knows a kind this one does not — reads as
     * {@link #ROLEPLAY}.
     */
    public static CharacterKind fromId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (CharacterKind kind : values()) {
            if (kind.id.equals(normalized)) {
                return kind;
            }
        }
        return ROLEPLAY;
    }
}
