package com.ninuna.losttales.character.lore;

import com.ninuna.losttales.character.model.CharacterProfile;

/**
 * Minimal immutable identity, an optional validated visual appearance,
 * and the profile the character is claimed with.
 */
public final class LoreCharacterDefinition {

    public static final int CURRENT_DATA_VERSION = 1;
    /** The age of a lore character whose file gives none. */
    public static final int DEFAULT_AGE = 18;

    private final int dataVersion;
    private final String id;
    private final String name;
    private final String description;
    private final int age;
    private final Appearance appearance;
    private final CharacterProfile profile;

    public LoreCharacterDefinition(int dataVersion, String id, String name,
                                   String description, int age,
                                   Appearance appearance,
                                   CharacterProfile profile) {
        this.dataVersion = dataVersion;
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.age = age;
        this.appearance = appearance;
        this.profile = profile == null ? CharacterProfile.EMPTY : profile;
    }

    public int getDataVersion() {
        return this.dataVersion;
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

    public boolean hasAppearance() {
        return this.appearance != null;
    }

    public Appearance getAppearance() {
        return this.appearance;
    }

    /**
     * What the character says about itself as it is claimed: the profile
     * the file gives, its History the description where the file names
     * none. It belongs to the character's story, so nobody edits it.
     */
    public CharacterProfile getProfile() {
        return this.profile;
    }

    /** Fixed visual selection authored by the server, never by a client. */
    public static final class Appearance {
        public static final String RACE_DEFAULT_MODEL = "losttales:race_default";

        private final String raceId;
        private final String genderId;
        private final String modelId;
        private final String skinId;

        public Appearance(String raceId, String genderId,
                          String modelId, String skinId) {
            this.raceId = raceId == null ? "" : raceId;
            this.genderId = genderId == null ? "" : genderId;
            this.modelId = modelId == null ? "" : modelId;
            this.skinId = skinId == null ? "" : skinId;
        }

        public String getRaceId() {
            return this.raceId;
        }

        public String getGenderId() {
            return this.genderId;
        }

        public String getModelId() {
            return this.modelId;
        }

        public String getSkinId() {
            return this.skinId;
        }
    }
}
