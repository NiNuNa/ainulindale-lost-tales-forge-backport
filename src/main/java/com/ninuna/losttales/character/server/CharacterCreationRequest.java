package com.ninuna.losttales.character.server;

/**
 * What a player asks for when making a character. The owner, the
 * character's id and when it was made are the server's to set, and are
 * not here.
 */
public final class CharacterCreationRequest {

    private final long expectedRosterRevision;
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
    private final boolean showMinecraftCape;
    private final String cosmeticCapeId;

    /** Empty arms and chest let validation pick the body type's own. */
    public CharacterCreationRequest(long expectedRosterRevision, int slotIndex,
                                    String name, String raceId, String bodyTypeId,
                                    String skinId, int age,
                                    String startingFactionId,
                                    String startingWaypointId,
                                    boolean unconventionalSettings,
                                    String history, String armsId,
                                    String chestTypeId, boolean showMinecraftCape,
                                    String cosmeticCapeId) {
        this.expectedRosterRevision = expectedRosterRevision;
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = cosmeticCapeId == null ? "" : cosmeticCapeId;
        this.slotIndex = slotIndex;
        this.name = name;
        this.raceId = raceId;
        this.bodyTypeId = bodyTypeId;
        this.skinId = skinId;
        this.armsId = armsId == null ? "" : armsId;
        this.chestTypeId = chestTypeId == null ? "" : chestTypeId;
        this.history = history;
        this.age = age;
        this.startingFactionId = startingFactionId;
        this.startingWaypointId = startingWaypointId;
        this.unconventionalSettings = unconventionalSettings;
    }

    public long getExpectedRosterRevision() {
        return this.expectedRosterRevision;
    }

    public int getSlotIndex() {
        return this.slotIndex;
    }

    public String getName() {
        return this.name;
    }

    public String getRaceId() {
        return this.raceId;
    }

    public String getBodyTypeId() {
        return this.bodyTypeId;
    }

    public String getSkinId() {
        return this.skinId;
    }

    /** Requested arms; empty means the default for the body type. */
    public String getArmsId() {
        return this.armsId;
    }

    /** Requested chest type; empty means the default for the body type. */
    public String getChestTypeId() {
        return this.chestTypeId;
    }

    public String getHistory() {
        return this.history;
    }

    public int getAge() {
        return this.age;
    }

    public String getStartingFactionId() {
        return this.startingFactionId;
    }

    public String getStartingWaypointId() {
        return this.startingWaypointId;
    }

    public boolean hasUnconventionalSettings() {
        return this.unconventionalSettings;
    }

    /** Whether the account's own Minecraft cape is worn when no cosmetic cape is. */
    public boolean isMinecraftCapeVisible() {
        return this.showMinecraftCape;
    }

    /** The cape file's id; empty for none. The server checks it. */
    public String getCosmeticCapeId() {
        return this.cosmeticCapeId;
    }
}
