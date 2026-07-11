package com.ninuna.losttales.character.server;

/**
 * Narrow server input for character creation. Authoritative fields such as
 * owner UUID, character UUID, level, progression, and timestamps are absent.
 */
public final class CharacterCreationRequest {

    public static final long REVISION_NOT_CHECKED = -1L;

    private final long expectedRosterRevision;
    private final int slotIndex;
    private final String name;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final int age;
    private final String startingFactionId;

    public CharacterCreationRequest(long expectedRosterRevision, int slotIndex,
                                    String name, String raceId, String genderId,
                                    String skinId, int age,
                                    String startingFactionId) {
        this.expectedRosterRevision = expectedRosterRevision;
        this.slotIndex = slotIndex;
        this.name = name;
        this.raceId = raceId;
        this.genderId = genderId;
        this.skinId = skinId;
        this.age = age;
        this.startingFactionId = startingFactionId;
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

    public String getGenderId() {
        return this.genderId;
    }

    public String getSkinId() {
        return this.skinId;
    }

    public int getAge() {
        return this.age;
    }

    public String getStartingFactionId() {
        return this.startingFactionId;
    }
}
