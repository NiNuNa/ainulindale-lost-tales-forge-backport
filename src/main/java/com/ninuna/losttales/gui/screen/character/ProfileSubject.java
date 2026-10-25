package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterSummary;

/**
 * Whose profile the Characters page shows, as far as this client knows
 * them: a character of the player's own roster, or another person's
 * character — with everything their appearance tells when it is the one
 * they play, and only its name and head when it is the one they speak as
 * in the chat. What is not known is empty, or an age of 0, and the column
 * leaves its line out.
 */
final class ProfileSubject {
    final String name;
    final String raceId;
    final String bodyTypeId;
    final String skinId;
    final String armsId;
    final String chestTypeId;
    final String factionId;
    final int age;
    /** Whether it is one of this player's own. */
    final boolean own;

    private ProfileSubject(String name, String raceId, String bodyTypeId,
                           String skinId, String armsId,
                           String chestTypeId, String factionId, int age,
                           boolean own) {
        this.name = name == null ? "" : name;
        this.raceId = raceId == null ? "" : raceId;
        this.bodyTypeId = bodyTypeId == null ? "" : bodyTypeId;
        this.skinId = skinId == null ? "" : skinId;
        this.armsId = armsId == null ? "" : armsId;
        this.chestTypeId = chestTypeId == null ? "" : chestTypeId;
        this.factionId = factionId == null ? "" : factionId;
        this.age = Math.max(0, age);
        this.own = own;
    }

    static ProfileSubject of(CharacterSummary character) {
        return new ProfileSubject(character.getName(), character.getRaceId(),
                character.getBodyTypeId(), character.getSkinId(),
                character.getArmsId(), character.getChestTypeId(),
                character.getFactionId(), character.getAge(), true);
    }

    /** Another person's character as their appearance shows it. */
    static ProfileSubject of(CharacterAppearance appearance) {
        return new ProfileSubject(appearance.getCharacterName(),
                appearance.getRaceId(), appearance.getBodyTypeId(),
                appearance.getSkinId(), appearance.getArmsId(),
                appearance.getChestTypeId(), appearance.getFactionId(),
                appearance.getAge(), false);
    }

    /** Another person's character known only by its name and head. */
    static ProfileSubject named(String name, String skinId) {
        return new ProfileSubject(name, "", "", skinId, "", "", "", 0, false);
    }
}
