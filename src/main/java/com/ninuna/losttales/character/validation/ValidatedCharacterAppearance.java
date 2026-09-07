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
    private final String normalizedNameKey;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final String bodyTypeId;
    private final String chestTypeId;
    private final String description;
    private final int age;

    public ValidatedCharacterAppearance(String name, String normalizedNameKey,
                                        String raceId, String genderId,
                                        String skinId, String bodyTypeId,
                                        String chestTypeId, String description,
                                        int age) {
        this.name = name;
        this.normalizedNameKey = normalizedNameKey;
        this.raceId = raceId;
        this.genderId = genderId;
        this.skinId = skinId;
        this.bodyTypeId = bodyTypeId;
        this.chestTypeId = chestTypeId;
        this.description = description;
        this.age = age;
    }

    public String getName() { return this.name; }
    public String getNormalizedNameKey() { return this.normalizedNameKey; }
    public String getRaceId() { return this.raceId; }
    public String getGenderId() { return this.genderId; }
    public String getSkinId() { return this.skinId; }
    public String getBodyTypeId() { return this.bodyTypeId; }
    public String getChestTypeId() { return this.chestTypeId; }
    public String getDescription() { return this.description; }
    public int getAge() { return this.age; }
}
