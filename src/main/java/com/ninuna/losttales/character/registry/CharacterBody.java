package com.ninuna.losttales.character.registry;

/**
 * The six bodies a race stands on (CHAR-38): the model that draws it, with
 * its geometry and skin layout, and how its own race's figure is scaled at
 * its own height. A race of another height on the same body is drawn at the
 * same proportions, scaled by its height; its posed figure keeps the size
 * the body's own race shows. The bodies are code; a race file only names
 * one.
 */
public enum CharacterBody {
    HUMAN("human", CharacterBodyModelRegistry.LOTR_HUMAN, 1.80F, 1.0F, 1.0F, 0),
    ELF("elf", CharacterBodyModelRegistry.LOTR_ELF, 1.80F, 1.0F, 0.96F, 0),
    DWARF("dwarf", CharacterBodyModelRegistry.LOTR_DWARF, 1.50F, 0.8125F, 1.12F, -2),
    HOBBIT("hobbit", CharacterBodyModelRegistry.LOTR_HOBBIT, 1.20F, 0.75F, 1.34F, -5),
    ORC("orc", CharacterBodyModelRegistry.LOTR_ORC, 1.55F, 0.85F, 1.08F, -2),
    URUK("uruk", CharacterBodyModelRegistry.LOTR_URUK, 1.80F, 1.0F, 1.05F, -2);

    private final String word;
    private final String modelId;
    private final float height;
    private final float rendererScale;
    private final float previewScale;
    private final int previewOffset;

    CharacterBody(String word, String modelId, float height, float rendererScale,
                  float previewScale, int previewOffset) {
        this.word = word;
        this.modelId = modelId;
        this.height = height;
        this.rendererScale = rendererScale;
        this.previewScale = previewScale;
        this.previewOffset = previewOffset;
    }

    /** The word a race file names the body by. */
    public String getWord() {
        return this.word;
    }

    /** The bundled race the body is drawn for, whose size a race gone from the server takes. */
    public String getOwnRaceId() {
        return "losttales:" + this.word;
    }

    /** The {@link CharacterBodyModelRegistry} model the race's own skins are drawn with. */
    public String getModelId() {
        return this.modelId;
    }

    /**
     * The model an account skin is drawn with on this body: the plain
     * player body on the human body, else the body's own.
     */
    public String getAccountSkinModelId() {
        return this == HUMAN ? CharacterBodyModelRegistry.LOSTTALES_PLAYER : this.modelId;
    }

    /** The height the body's own race stands at. */
    public float getHeight() {
        return this.height;
    }

    /** The model's scale for a race {@code height} tall. */
    public float rendererScale(float height) {
        return height == this.height ? this.rendererScale
                : this.rendererScale * height / this.height;
    }

    /** The posed figure's scale for a race {@code height} tall: the body's own race's size. */
    public float previewScale(float height) {
        return height == this.height ? this.previewScale
                : this.previewScale * this.height / height;
    }

    public int getPreviewOffset() {
        return this.previewOffset;
    }

    /** The body a race file names; null for a word no body has. */
    public static CharacterBody fromWord(String word) {
        for (CharacterBody body : values()) {
            if (body.word.equals(word)) {
                return body;
            }
        }
        return null;
    }
}
