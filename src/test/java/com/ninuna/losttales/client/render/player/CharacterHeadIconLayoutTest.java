package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Regression coverage for the race-specific LOTR portrait texture layouts. */
public final class CharacterHeadIconLayoutTest {

    private static final float EPSILON = 0.0001F;

    @Test
    public void humanAndElfUseFullLengthHairAndBeardLayer() {
        assertExtendedLayout(CharacterRaceRegistry.HUMAN, 16.0F);
        assertExtendedLayout(CharacterRaceRegistry.ELF, 16.0F);
    }

    @Test
    public void dwarfAndHobbitUseShorterHairAndBeardLayer() {
        assertExtendedLayout(CharacterRaceRegistry.DWARF, 12.0F);
        assertExtendedLayout(CharacterRaceRegistry.HOBBIT, 12.0F);
    }

    @Test
    public void orcAndUrukDoNotSampleVanillaHeadwearGeometry() {
        assertBaseOnlyLayout(CharacterRaceRegistry.ORC, 8.0F, 32.0F);
        assertBaseOnlyLayout(CharacterRaceRegistry.URUK, 8.0F, 32.0F);
    }

    @Test
    public void halfTrollUsesItsTenPixelHeadFace() {
        assertBaseOnlyLayout(
                CharacterRaceRegistry.HALF_TROLL, 10.0F, 64.0F);
    }

    @Test
    public void accountFallbackRetainsMinecraftHatLayer() {
        CharacterHeadIconLayout layout =
                CharacterHeadIconLayout.minecraftSkin();
        assertEquals(
                CharacterHeadIconLayout.OverlayKind.MINECRAFT,
                layout.getOverlayKind());
        assertEquals(8.0F, layout.getFaceSize(), EPSILON);
        assertEquals(32.0F, layout.getImageHeight(), EPSILON);
    }

    private static void assertExtendedLayout(
            String raceId, float overlayHeight) {
        CharacterHeadIconLayout layout =
                CharacterHeadIconLayout.forConfiguredRace(raceId);
        assertEquals(
                CharacterHeadIconLayout.OverlayKind.LOTR_EXTENDED,
                layout.getOverlayKind());
        assertEquals(8.0F, layout.getFaceSize(), EPSILON);
        assertEquals(64.0F, layout.getImageHeight(), EPSILON);
        assertEquals(
                overlayHeight,
                layout.getExtendedOverlayHeight(), EPSILON);
    }

    private static void assertBaseOnlyLayout(
            String raceId, float faceSize, float imageHeight) {
        CharacterHeadIconLayout layout =
                CharacterHeadIconLayout.forConfiguredRace(raceId);
        assertEquals(
                CharacterHeadIconLayout.OverlayKind.NONE,
                layout.getOverlayKind());
        assertEquals(faceSize, layout.getFaceSize(), EPSILON);
        assertEquals(imageHeight, layout.getImageHeight(), EPSILON);
        assertEquals(0.0F, layout.getExtendedOverlayHeight(), EPSILON);
    }
}
