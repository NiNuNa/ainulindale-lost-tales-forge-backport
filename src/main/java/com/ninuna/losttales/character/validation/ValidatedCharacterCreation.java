package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;

/** Canonical values produced by successful creation validation. */
public final class ValidatedCharacterCreation {

    private final int slotIndex;
    private final String name;
    private final String normalizedNameKey;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final String bodyTypeId;
    private final String chestTypeId;
    private final String description;
    private final int age;
    private final String startingFactionId;
    private final String startingWaypointId;
    private final boolean unconventionalSettings;

    public ValidatedCharacterCreation(int slotIndex, String name,
                                      String normalizedNameKey, String raceId,
                                      String genderId, String skinId, int age,
                                      String startingFactionId) {
        this(slotIndex, name, normalizedNameKey, raceId, genderId, skinId,
                age, startingFactionId, "", false,
                "");
    }

    public ValidatedCharacterCreation(int slotIndex, String name,
                                      String normalizedNameKey, String raceId,
                                      String genderId, String skinId, int age,
                                      String startingFactionId,
                                      String startingWaypointId,
                                      boolean unconventionalSettings) {
        this(slotIndex, name, normalizedNameKey, raceId, genderId, skinId,
                age, startingFactionId, startingWaypointId,
                unconventionalSettings, "");
    }

    public ValidatedCharacterCreation(int slotIndex, String name,
                                      String normalizedNameKey, String raceId,
                                      String genderId, String skinId, int age,
                                      String startingFactionId,
                                      String startingWaypointId,
                                      boolean unconventionalSettings,
                                      String description) {
        this(slotIndex, name, normalizedNameKey, raceId, genderId, skinId,
                age, startingFactionId, startingWaypointId,
                unconventionalSettings, description,
                CharacterBodyTypeRegistry.defaultFor(genderId));
    }

    public ValidatedCharacterCreation(int slotIndex, String name,
                                      String normalizedNameKey, String raceId,
                                      String genderId, String skinId, int age,
                                      String startingFactionId,
                                      String startingWaypointId,
                                      boolean unconventionalSettings,
                                      String description, String bodyTypeId) {
        this(slotIndex, name, normalizedNameKey, raceId, genderId, skinId,
                age, startingFactionId, startingWaypointId,
                unconventionalSettings, description, bodyTypeId,
                CharacterChestTypeRegistry.defaultFor(genderId));
    }

    public ValidatedCharacterCreation(int slotIndex, String name,
                                      String normalizedNameKey, String raceId,
                                      String genderId, String skinId, int age,
                                      String startingFactionId,
                                      String startingWaypointId,
                                      boolean unconventionalSettings,
                                      String description, String bodyTypeId,
                                      String chestTypeId) {
        this.slotIndex = slotIndex;
        this.name = name;
        this.normalizedNameKey = normalizedNameKey;
        this.raceId = raceId;
        this.genderId = genderId;
        this.skinId = skinId;
        this.bodyTypeId = bodyTypeId;
        this.chestTypeId = chestTypeId;
        this.description = description;
        this.age = age;
        this.startingFactionId = startingFactionId;
        this.startingWaypointId = startingWaypointId;
        this.unconventionalSettings = unconventionalSettings;
    }

    public int getSlotIndex() { return this.slotIndex; }
    public String getName() { return this.name; }
    public String getNormalizedNameKey() { return this.normalizedNameKey; }
    public String getRaceId() { return this.raceId; }
    public String getGenderId() { return this.genderId; }
    public String getSkinId() { return this.skinId; }
    public String getBodyTypeId() { return this.bodyTypeId; }
    public String getChestTypeId() { return this.chestTypeId; }
    public String getDescription() { return this.description; }
    public int getAge() { return this.age; }
    public String getStartingFactionId() { return this.startingFactionId; }
    public String getStartingWaypointId() { return this.startingWaypointId; }
    public boolean hasUnconventionalSettings() { return this.unconventionalSettings; }
}
