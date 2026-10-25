package com.ninuna.losttales.client.window;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.camera.StageShot;
import java.util.UUID;

/**
 * What a page shows on a stage while it is in front there (WIN-50): the
 * look the player's character wears, the armour and held item with it,
 * the camera's shot of it, and the nameplate under it. The Characters
 * page shows the character picked, the Character Editor the choices as
 * they stand.
 */
public interface StageSubject {
    /**
     * The look the character of {@code playerId} wears on the stage, as a
     * preview the world never sees; null for the character as it is.
     */
    CharacterAppearance stageLook(UUID playerId);

    /**
     * The armour and held item worn with the look (CHAR-42). Asked only
     * while {@link #stageLook} gives one; the character as it is wears the
     * player's own.
     */
    Gear stageGear();

    /** How the camera frames the character before the player moves it. */
    StageShot stageShot();

    /** The name on the nameplate; empty for no nameplate. */
    String stageName();

    /** What stands at the nameplate's other end: race, body type and age. */
    String stageFacts();

    /**
     * Whose armour and held item the stage shows: the player's own, none,
     * or one of the player's characters' from its saved life, which the
     * stage asks the server for.
     */
    final class Gear {
        /** The player's own, as they stand. */
        public static final Gear OWN = new Gear(true, null);
        /** Nothing: no armour and an empty hand. */
        public static final Gear NONE = new Gear(false, null);

        private final boolean own;
        private final UUID characterId;

        private Gear(boolean own, UUID characterId) {
            this.own = own;
            this.characterId = characterId;
        }

        /** What that character of the player's own wears in its saved life; none for no character. */
        public static Gear of(UUID characterId) {
            return characterId == null ? NONE : new Gear(false, characterId);
        }

        public boolean isOwn() {
            return this.own;
        }

        /** The character whose saved life the gear comes from; null for the player's own or none. */
        public UUID getCharacterId() {
            return this.characterId;
        }
    }
}
