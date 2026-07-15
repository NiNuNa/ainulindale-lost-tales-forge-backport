package com.ninuna.losttales.character.lore;

/** Minimal immutable identity and optional validated visual appearance. */
public final class LoreCharacterDefinition {

    public static final int CURRENT_DATA_VERSION = 1;

    private final int dataVersion;
    private final String id;
    private final String name;
    private final String description;
    private final Appearance appearance;

    public LoreCharacterDefinition(int dataVersion, String id, String name,
                                   String description, Appearance appearance) {
        this.dataVersion = dataVersion;
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.appearance = appearance;
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

    public boolean hasAppearance() {
        return this.appearance != null;
    }

    public Appearance getAppearance() {
        return this.appearance;
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
