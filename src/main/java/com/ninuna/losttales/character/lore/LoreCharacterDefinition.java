package com.ninuna.losttales.character.lore;

import com.ninuna.losttales.character.model.CharacterProfile;

/**
 * A lore character as its file writes it ({@link LoreCharacterFiles}): its
 * id and name, the faction it starts in, an optional look, and the profile
 * it is claimed with.
 */
public final class LoreCharacterDefinition {

    /** The age of a lore character whose file gives none. */
    public static final int DEFAULT_AGE = 18;
    /** A description, in characters. */
    public static final int MAX_DESCRIPTION_LENGTH = 512;
    /** An id, in characters: the lore list every player is sent takes no longer. */
    public static final int MAX_ID_LENGTH = 128;
    /** A name, in characters. */
    public static final int MAX_NAME_LENGTH = 64;
    /** A name, in UTF-8 bytes: the lore list every player is sent takes no longer. */
    public static final int MAX_NAME_BYTES = 128;

    private final String id;
    private final String name;
    private final String description;
    private final int age;
    private final Appearance appearance;
    private final String factionId;
    private final CharacterProfile profile;

    public LoreCharacterDefinition(String id, String name,
                                   String description, int age,
                                   Appearance appearance, String factionId,
                                   CharacterProfile profile) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.age = age;
        this.appearance = appearance;
        this.factionId = factionId == null ? "" : factionId;
        this.profile = profile == null ? CharacterProfile.EMPTY : profile;
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public int getAge() {
        return this.age;
    }

    /** Whether the file gives a look; one without is listed but cannot be claimed. */
    public boolean hasAppearance() {
        return this.appearance != null;
    }

    public Appearance getAppearance() {
        return this.appearance;
    }

    /** The faction the character starts in at its first claim (CHAR-44). */
    public String getFactionId() {
        return this.factionId;
    }

    /**
     * What the character says about itself as it is claimed: the profile
     * the file gives, its History the description where the file names
     * none. It belongs to the character's story, so nobody edits it.
     */
    public CharacterProfile getProfile() {
        return this.profile;
    }

    /** The race, body type and skin the file fixes; a player never chooses them. */
    public static final class Appearance {
        private final String raceId;
        private final String bodyTypeId;
        private final String skinId;

        public Appearance(String raceId, String bodyTypeId, String skinId) {
            this.raceId = raceId == null ? "" : raceId;
            this.bodyTypeId = bodyTypeId == null ? "" : bodyTypeId;
            this.skinId = skinId == null ? "" : skinId;
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
    }
}
