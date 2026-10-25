package com.ninuna.losttales.character.validation;

/** Canonical values produced by successful creation validation. */
public final class ValidatedCharacterCreation {

    private final int slotIndex;
    private final String name;
    private final String raceId;
    private final String bodyTypeId;
    private final String skinId;
    private final String armsId;
    private final String chestTypeId;
    private final String history;
    private final int age;
    private final String startingFactionId;
    private final String startingWaypointId;
    private final boolean unconventionalSettings;

    public ValidatedCharacterCreation(int slotIndex, String name,
                                      String raceId,
                                      String bodyTypeId, String skinId, int age,
                                      String startingFactionId,
                                      String startingWaypointId,
                                      boolean unconventionalSettings,
                                      String history, String armsId,
                                      String chestTypeId) {
        this.slotIndex = slotIndex;
        this.name = name;
        this.raceId = raceId;
        this.bodyTypeId = bodyTypeId;
        this.skinId = skinId;
        this.armsId = armsId;
        this.chestTypeId = chestTypeId;
        this.history = history;
        this.age = age;
        this.startingFactionId = startingFactionId;
        this.startingWaypointId = startingWaypointId;
        this.unconventionalSettings = unconventionalSettings;
    }

    public int getSlotIndex() { return this.slotIndex; }
    public String getName() { return this.name; }
    public String getRaceId() { return this.raceId; }
    public String getBodyTypeId() { return this.bodyTypeId; }
    public String getSkinId() { return this.skinId; }
    public String getArmsId() { return this.armsId; }
    public String getChestTypeId() { return this.chestTypeId; }
    public String getHistory() { return this.history; }
    public int getAge() { return this.age; }
    public String getStartingFactionId() { return this.startingFactionId; }
    public String getStartingWaypointId() { return this.startingWaypointId; }
    public boolean hasUnconventionalSettings() { return this.unconventionalSettings; }
}
