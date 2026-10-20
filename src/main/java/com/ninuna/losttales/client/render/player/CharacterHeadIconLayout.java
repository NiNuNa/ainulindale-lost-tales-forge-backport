package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterBody;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.util.LostTalesIdentifiers;

/**
 * Texture coordinates used when a character head is flattened into a GUI
 * portrait. LOTR player models do not all use Minecraft's normal hat layer.
 */
final class CharacterHeadIconLayout {

    enum OverlayKind {
        NONE,
        MINECRAFT,
        LOTR_EXTENDED,
        LOTR_ORC_FEATURES
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

    /** The layout of a skin painted for the race's body (CHAR-38). */
    static CharacterHeadIconLayout forConfiguredRace(String raceId) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(
                LostTalesIdentifiers.normalize(raceId));
        CharacterBody body = race == null ? null : race.getBody();
        if (body == CharacterBody.HUMAN || body == CharacterBody.ELF
                || body == CharacterBody.DWARF || body == CharacterBody.HOBBIT) {
            // Their outer cuboid starts at the head crown and continues
            // below the chin. A square portrait is the head, so it takes
            // the head's own eight rows and leaves the hanging hair and
            // the beard on the model.
            return new CharacterHeadIconLayout(
                    64.0F, 8.0F, 8.0F, 8.0F, OverlayKind.LOTR_EXTENDED);
        }
        if (body == CharacterBody.ORC || body == CharacterBody.URUK) {
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
