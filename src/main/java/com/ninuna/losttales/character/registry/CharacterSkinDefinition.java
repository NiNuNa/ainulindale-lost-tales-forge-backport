package com.ninuna.losttales.character.registry;

/**
 * One server-valid character skin.
 *
 * The texture is either provided by LOTR Legacy, in which case only a stable
 * identifier and a resource location are stored and no LOTR asset is copied,
 * or bundled with Lost Tales under {@code assets/losttales/textures/skins/}.
 */
public final class CharacterSkinDefinition {

    private final String id;
    private final String raceId;
    private final String genderId;
    private final String displayGroupId;
    private final int variantIndex;
    private final String textureLocation;

    CharacterSkinDefinition(String id, String raceId, String genderId,
                            String displayGroupId, int variantIndex,
                            String textureLocation) {
        if (isBlank(id) || isBlank(raceId) || isBlank(displayGroupId)
                || isBlank(textureLocation) || variantIndex < 0) {
            throw new IllegalArgumentException("character skin fields must be valid");
        }
        this.id = id;
        this.raceId = raceId;
        this.genderId = genderId == null ? "" : genderId;
        this.displayGroupId = displayGroupId;
        this.variantIndex = variantIndex;
        this.textureLocation = textureLocation;
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

    public String getTextureLocation() {
        return this.textureLocation;
    }

    /** True when the texture ships inside the Lost Tales jar. */
    public boolean isBundled() {
        return this.textureLocation.startsWith(CharacterSkinRegistry.BUNDLED_TEXTURE_ROOT);
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
