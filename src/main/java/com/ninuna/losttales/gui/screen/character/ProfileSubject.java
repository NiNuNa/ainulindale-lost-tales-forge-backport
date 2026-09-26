package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterSummary;
import java.util.UUID;

/**
 * Whose profile the Characters tab shows, as far as this client knows
 * them: a character of the player's own roster, the account while its
 * record is not made, or another person's character — with everything
 * their appearance tells when it is the one they play, and only its name
 * and head when it is the one they speak as in the chat. What is not
 * known is empty, or an age of 0, and the column leaves its line out.
 */
final class ProfileSubject {
    /** The character, or null for the account. */
    final UUID characterId;
    final String name;
    final String raceId;
    final String genderId;
    final String skinId;
    final String bodyTypeId;
    final String chestTypeId;
    final String factionId;
    final int age;
    /** Whether it is one of this player's own. */
    final boolean own;

    private ProfileSubject(UUID characterId, String name, String raceId,
                           String genderId, String skinId, String bodyTypeId,
                           String chestTypeId, String factionId, int age,
                           boolean own) {
        this.characterId = characterId;
        this.name = name == null ? "" : name;
        this.raceId = raceId == null ? "" : raceId;
        this.genderId = genderId == null ? "" : genderId;
        this.skinId = skinId == null ? "" : skinId;
        this.bodyTypeId = bodyTypeId == null ? "" : bodyTypeId;
        this.chestTypeId = chestTypeId == null ? "" : chestTypeId;
        this.factionId = factionId == null ? "" : factionId;
        this.age = Math.max(0, age);
        this.own = own;
    }

    static ProfileSubject of(CharacterSummary character) {
        return new ProfileSubject(character.getCharacterId(),
                character.getName(), character.getRaceId(),
                character.getGenderId(), character.getSkinId(),
                character.getBodyTypeId(), character.getChestTypeId(),
                character.getStartingFactionId(), character.getAge(), true);
    }

    static ProfileSubject account(String accountName) {
        return new ProfileSubject(null, accountName, "", "", "", "", "", "", 0,
                true);
    }

    /** Another person's character as their appearance shows it. */
    static ProfileSubject of(CharacterAppearance appearance) {
        return new ProfileSubject(appearance.getCharacterId(),
                appearance.getCharacterName(), appearance.getRaceId(),
                appearance.getGenderId(), appearance.getSkinId(),
                appearance.getBodyTypeId(), appearance.getChestTypeId(),
                appearance.getStartingFactionId(), appearance.getAge(), false);
    }

    /** Another person's character known only by its name and head. */
    static ProfileSubject named(UUID characterId, String name, String skinId) {
        return new ProfileSubject(characterId, name, "", "", skinId, "", "",
                "", 0, false);
    }
}
