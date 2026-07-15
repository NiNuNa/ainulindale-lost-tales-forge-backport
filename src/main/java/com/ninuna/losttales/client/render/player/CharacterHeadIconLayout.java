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
        LOTR_EXTENDED
    }

    private final float imageHeight;
    private final float faceX;
    private final float faceY;
    private final float faceSize;
    private final OverlayKind overlayKind;
    private final float extendedOverlayHeight;

    private CharacterHeadIconLayout(float imageHeight,
                                    float faceX,
                                    float faceY,
                                    float faceSize,
                                    OverlayKind overlayKind,
                                    float extendedOverlayHeight) {
        this.imageHeight = imageHeight;
        this.faceX = faceX;
        this.faceY = faceY;
        this.faceSize = faceSize;
        this.overlayKind = overlayKind;
        this.extendedOverlayHeight = extendedOverlayHeight;
    }

    static CharacterHeadIconLayout minecraftSkin() {
        return new CharacterHeadIconLayout(
                32.0F, 8.0F, 8.0F, 8.0F,
                OverlayKind.MINECRAFT, 0.0F);
    }

    static CharacterHeadIconLayout forConfiguredRace(String raceId) {
        String canonicalRace =
                CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        if (CharacterRaceRegistry.HUMAN.equals(canonicalRace)
                || CharacterRaceRegistry.ELF.equals(canonicalRace)) {
            return new CharacterHeadIconLayout(
                    64.0F, 8.0F, 8.0F, 8.0F,
                    OverlayKind.LOTR_EXTENDED, 16.0F);
        }
        if (CharacterRaceRegistry.DWARF.equals(canonicalRace)
                || CharacterRaceRegistry.HOBBIT.equals(canonicalRace)) {
            return new CharacterHeadIconLayout(
                    64.0F, 8.0F, 8.0F, 8.0F,
                    OverlayKind.LOTR_EXTENDED, 12.0F);
        }
        if (CharacterRaceRegistry.HALF_TROLL.equals(canonicalRace)) {
            // LOTRModelHalfTroll uses a 10x10 head instead of a biped 8x8
            // head. Its inherited headwear cube is intentionally hidden.
            return new CharacterHeadIconLayout(
                    64.0F, 10.0F, 10.0F, 10.0F,
                    OverlayKind.NONE, 0.0F);
        }
        // Orc and Uruk details are part of the 64x32 base face. Applying the
        // vanilla hat region would sample unrelated model geometry.
        return new CharacterHeadIconLayout(
                32.0F, 8.0F, 8.0F, 8.0F,
                OverlayKind.NONE, 0.0F);
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

    float getExtendedOverlayHeight() {
        return this.extendedOverlayHeight;
    }
}
