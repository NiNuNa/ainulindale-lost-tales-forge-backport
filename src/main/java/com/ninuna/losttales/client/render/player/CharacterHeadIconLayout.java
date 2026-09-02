package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterRaceRegistry;

/**
 * Texture coordinates used when a character head is flattened into a GUI
 * portrait. LOTR player models do not all use Minecraft's normal hat layer.
 */
final class CharacterHeadIconLayout {

    enum OverlayKind {
        NONE,
        MINECRAFT,
        LOTR_EXTENDED,
        LOTR_ORC_FEATURES,
        LOTR_HALF_TROLL_FEATURES
    }

    private final float imageHeight;
    private final float faceX;
    private final float faceY;
    private final float faceSize;
    private final OverlayKind overlayKind;

    private CharacterHeadIconLayout(float imageHeight,
                                    float faceX,
                                    float faceY,
                                    float faceSize,
                                    OverlayKind overlayKind) {
        this.imageHeight = imageHeight;
        this.faceX = faceX;
        this.faceY = faceY;
        this.faceSize = faceSize;
        this.overlayKind = overlayKind;
    }

    /** Vanilla's own 64x32 copy of an account skin. */
    static CharacterHeadIconLayout minecraftSkin() {
        return new CharacterHeadIconLayout(
                32.0F, 8.0F, 8.0F, 8.0F, OverlayKind.MINECRAFT);
    }

    /** The 64x64 copy Lost Tales keeps of an account skin; the face and hat sit where vanilla's do. */
    static CharacterHeadIconLayout minecraftSkin64() {
        return new CharacterHeadIconLayout(
                64.0F, 8.0F, 8.0F, 8.0F, OverlayKind.MINECRAFT);
    }

    /**
     * Layout for an account skin texture by the namespace it lives in: Lost
     * Tales' copies are 64 rows tall, vanilla's are 32.
     */
    static CharacterHeadIconLayout forAccountTexture(String resourceDomain) {
        return "losttales".equals(resourceDomain) ? minecraftSkin64() : minecraftSkin();
    }

    static CharacterHeadIconLayout forConfiguredRace(String raceId) {
        String canonicalRace =
                CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        if (CharacterRaceRegistry.HUMAN.equals(canonicalRace)
                || CharacterRaceRegistry.ELF.equals(canonicalRace)
                || CharacterRaceRegistry.DWARF.equals(canonicalRace)
                || CharacterRaceRegistry.HOBBIT.equals(canonicalRace)) {
            // Their outer cuboid starts at the head crown and continues
            // below the chin. A square portrait is the head, so it takes
            // the head's own eight rows and leaves the hanging hair and
            // the beard on the model.
            return new CharacterHeadIconLayout(
                    64.0F, 8.0F, 8.0F, 8.0F, OverlayKind.LOTR_EXTENDED);
        }
        if (CharacterRaceRegistry.HALF_TROLL.equals(canonicalRace)) {
            // LOTRModelHalfTroll uses a 10x10 head instead of a biped 8x8
            // head. Its inherited headwear cube is intentionally hidden.
            return new CharacterHeadIconLayout(
                    64.0F, 10.0F, 10.0F, 10.0F,
                    OverlayKind.LOTR_HALF_TROLL_FEATURES);
        }
        if (CharacterRaceRegistry.ORC.equals(canonicalRace)
                || CharacterRaceRegistry.URUK.equals(canonicalRace)) {
            // Their ModelOrc nose is a separate child cuboid; the normal
            // Minecraft hat region contains unrelated body geometry.
            return new CharacterHeadIconLayout(
                    32.0F, 8.0F, 8.0F, 8.0F,
                    OverlayKind.LOTR_ORC_FEATURES);
        }
        return new CharacterHeadIconLayout(
                32.0F, 8.0F, 8.0F, 8.0F, OverlayKind.NONE);
    }

    float getImageHeight() {
        return this.imageHeight;
    }

    float getFaceX() {
        return this.faceX;
    }

    float getFaceY() {
        return this.faceY;
    }

    float getFaceSize() {
        return this.faceSize;
    }

    OverlayKind getOverlayKind() {
        return this.overlayKind;
    }
}
