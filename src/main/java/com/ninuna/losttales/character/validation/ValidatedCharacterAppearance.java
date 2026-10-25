package com.ninuna.losttales.character.validation;

/**
 * The canonical form of everything a character looks like and says about
 * itself: what validation turns a request's text into before anything is
 * written.
 *
 * <p>It is what a character has in common with itself over time. Making
 * one and changing one ask the same questions of these fields, and where
 * they differ — a slot, a starting faction, a starting waypoint — is
 * about the world rather than the character.</p>
 */
public final class ValidatedCharacterAppearance {

    private final String name;
    private final String raceId;
    private final String bodyTypeId;
    private final String skinId;
    private final String armsId;
    private final String chestTypeId;
    private final String history;
    private final int age;

    public ValidatedCharacterAppearance(String name,
                                        String raceId, String bodyTypeId,
                                        String skinId, String armsId,
                                        String chestTypeId, String history,
                                        int age) {
        this.name = name;
        this.raceId = raceId;
        this.bodyTypeId = bodyTypeId;
        this.skinId = skinId;
        this.armsId = armsId;
        this.chestTypeId = chestTypeId;
        this.history = history;
        this.age = age;
    }

    public String getName() { return this.name; }
    public String getRaceId() { return this.raceId; }
    public String getBodyTypeId() { return this.bodyTypeId; }
    public String getSkinId() { return this.skinId; }
    public String getArmsId() { return this.armsId; }
    public String getChestTypeId() { return this.chestTypeId; }
    public String getHistory() { return this.history; }
    public int getAge() { return this.age; }
}
