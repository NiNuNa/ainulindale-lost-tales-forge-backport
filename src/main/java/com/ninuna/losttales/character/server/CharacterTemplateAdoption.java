package com.ninuna.losttales.character.server;

/**
 * What a client says its account's default character should start as, the
 * one time a world asks.
 *
 * <p>An account keeps a template on its own installation. The world that
 * makes its default character reads that template once and then owns the
 * character; what the player does to the template afterwards is about the
 * next world. This is that one reading, and the server checks every field
 * of it against its own content exactly as it checks a character somebody
 * is making.</p>
 *
 * <p>It carries no starting faction and no starting waypoint. The
 * account's own identity belongs to no faction — it did not before it was
 * a character either — and its alignment is whatever the account has
 * already earned in that world, which is not a thing a template may
 * decide. It carries no slot: the default character's place is the one
 * outside the nine and is never chosen.</p>
 *
 * <p>{@link #isOffered()} is false when the account has no template. The
 * request is still sent and still spends the world's one reading, so a
 * template written later is for the next world rather than this one.</p>
 */
public final class CharacterTemplateAdoption {

    private final long expectedRosterRevision;
    private final boolean offered;
    private final String name;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final String bodyTypeId;
    private final String chestTypeId;
    private final String description;
    private final int age;
    private final boolean showMinecraftCape;
    private final int cosmeticCapeId;

    /** An account with no template: the reading is spent, nothing changes. */
    public static CharacterTemplateAdoption none(long expectedRosterRevision) {
        return new CharacterTemplateAdoption(expectedRosterRevision, false,
                "", "", "", "", "", "", "", 0, true, 0);
    }

    public CharacterTemplateAdoption(long expectedRosterRevision, boolean offered,
                                     String name, String raceId, String genderId,
                                     String skinId, String bodyTypeId,
                                     String chestTypeId, String description,
                                     int age, boolean showMinecraftCape,
                                     int cosmeticCapeId) {
        this.expectedRosterRevision = expectedRosterRevision;
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = cosmeticCapeId;
        this.offered = offered;
        this.name = text(name);
        this.raceId = text(raceId);
        this.genderId = text(genderId);
        this.skinId = text(skinId);
        this.bodyTypeId = text(bodyTypeId);
        this.chestTypeId = text(chestTypeId);
        this.description = text(description);
        this.age = age;
    }

    public long getExpectedRosterRevision() { return this.expectedRosterRevision; }
    public boolean isOffered() { return this.offered; }
    public String getName() { return this.name; }
    public String getRaceId() { return this.raceId; }
    public String getGenderId() { return this.genderId; }
    public String getSkinId() { return this.skinId; }
    public String getBodyTypeId() { return this.bodyTypeId; }
    public String getChestTypeId() { return this.chestTypeId; }
    public String getDescription() { return this.description; }
    public int getAge() { return this.age; }
    /** Whether the account's own Minecraft cape is worn when no cosmetic cape is. */
    public boolean isMinecraftCapeVisible() { return this.showMinecraftCape; }
    /** The cosmetic cape's catalogue id; zero for none. The server checks it. */
    public int getCosmeticCapeId() { return this.cosmeticCapeId; }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
