package com.ninuna.losttales.character.validation;

/** Canonical values produced by successful creation validation. */
public final class ValidatedCharacterCreation {

    private final int slotIndex;
    private final String name;
    private final String normalizedNameKey;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final int age;
    private final String startingFactionId;

    public ValidatedCharacterCreation(int slotIndex, String name,
                                      String normalizedNameKey, String raceId,
                                      String genderId, String skinId, int age,
                                      String startingFactionId) {
        this.slotIndex = slotIndex;
        this.name = name;
        this.normalizedNameKey = normalizedNameKey;
        this.raceId = raceId;
        this.genderId = genderId;
        this.skinId = skinId;
        this.age = age;
        this.startingFactionId = startingFactionId;
    }

    public int getSlotIndex() { return this.slotIndex; }
    public String getName() { return this.name; }
    public String getNormalizedNameKey() { return this.normalizedNameKey; }
    public String getRaceId() { return this.raceId; }
    public String getGenderId() { return this.genderId; }
    public String getSkinId() { return this.skinId; }
    public int getAge() { return this.age; }
    public String getStartingFactionId() { return this.startingFactionId; }
}
