package com.ninuna.losttales.character.registry;

/**
 * One server-valid character skin.
 *
 * The texture is either provided by LOTR Legacy, in which case only a stable
 * identifier and a resource location are stored and no LOTR asset is copied,
 * bundled with Lost Tales under {@code assets/losttales/textures/skins/}, or
 * the player's own account skin, which every client fetches for itself.
 * Every skin names the body model that draws it and the layout its texels
 * follow, so the client never has to guess either from the race.
 */
public final class CharacterSkinDefinition {

    private final String id;
    private final String raceId;
    private final String genderId;
    private final String displayGroupId;
    private final int variantIndex;
    private final String textureLocation;
    private final String modelId;
    private final CharacterSkinLayout layout;
    private final boolean accountSkin;

    CharacterSkinDefinition(String id, String raceId, String genderId,
                            String displayGroupId, int variantIndex,
                            String textureLocation, String modelId,
                            CharacterSkinLayout layout) {
        this(id, raceId, genderId, displayGroupId, variantIndex,
                textureLocation, modelId, layout, false);
    }

    CharacterSkinDefinition(String id, String raceId, String genderId,
                            String displayGroupId, int variantIndex,
                            String textureLocation, String modelId,
                            CharacterSkinLayout layout, boolean accountSkin) {
        if (isBlank(id) || isBlank(raceId) || isBlank(displayGroupId)
                || isBlank(textureLocation) || isBlank(modelId)
                || layout == null || variantIndex < 0) {
            throw new IllegalArgumentException("character skin fields must be valid");
        }
        this.id = id;
        this.raceId = raceId;
        this.genderId = genderId == null ? "" : genderId;
        this.displayGroupId = displayGroupId;
        this.variantIndex = variantIndex;
        this.textureLocation = textureLocation;
        this.modelId = modelId;
        this.layout = layout;
        this.accountSkin = accountSkin;
    }

    public String getId() {
        return this.id;
    }

    public String getRaceId() {
        return this.raceId;
    }

    /** Empty for a unisex skin. */
    public String getGenderId() {
        return this.genderId;
    }

    public String getDisplayGroupId() {
        return this.displayGroupId;
    }

    public int getVariantIndex() {
        return this.variantIndex;
    }

    /** Resource location of the texture; a placeholder for an account skin. */
    public String getTextureLocation() {
        return this.textureLocation;
    }

    /** Identifier of the {@link CharacterBodyModelRegistry} model that draws this skin. */
    public String getModelId() {
        return this.modelId;
    }

    public CharacterSkinLayout getLayout() {
        return this.layout;
    }

    /** True when the texture ships inside the Lost Tales jar. */
    public boolean isBundled() {
        return this.textureLocation.startsWith(CharacterSkinRegistry.BUNDLED_TEXTURE_ROOT);
    }

    /** True when the texture is the player's own Minecraft account skin. */
    public boolean isAccountSkin() {
        return this.accountSkin;
    }

    public boolean isCompatibleWith(String raceId, String genderId) {
        if (!this.raceId.equals(CharacterRaceRegistry.canonicalizeIdentifier(raceId))) {
            return false;
        }
        return this.genderId.length() == 0
                || this.genderId.equals(CharacterGenderRegistry.appearanceGender(genderId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.length() == 0;
    }
}
