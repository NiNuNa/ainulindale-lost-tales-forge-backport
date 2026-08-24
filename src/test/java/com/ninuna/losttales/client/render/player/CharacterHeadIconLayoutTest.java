package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Regression coverage for the race-specific LOTR portrait texture layouts. */
public final class CharacterHeadIconLayoutTest {

    private static final float EPSILON = 0.0001F;

    /**
     * Every race that has LOTR's extended headwear cube shows the head's
     * own square of it. A portrait is eight pixels for eight texels: the
     * hair and beard rows the cube carries below the chin belong to the
     * model, and squeezing them into the square would stretch every
     * texel in it.
     */
    @Test
    public void everyExtendedRaceShowsTheHeadsOwnSquare() {
        assertExtendedLayout(CharacterRaceRegistry.HUMAN);
        assertExtendedLayout(CharacterRaceRegistry.ELF);
        assertExtendedLayout(CharacterRaceRegistry.DWARF);
        assertExtendedLayout(CharacterRaceRegistry.HOBBIT);
    }

    @Test
    public void orcAndUrukDoNotSampleVanillaHeadwearGeometry() {
        assertFeatureLayout(CharacterRaceRegistry.ORC, 8.0F, 32.0F,
                CharacterHeadIconLayout.OverlayKind.LOTR_ORC_FEATURES);
        assertFeatureLayout(CharacterRaceRegistry.URUK, 8.0F, 32.0F,
                CharacterHeadIconLayout.OverlayKind.LOTR_ORC_FEATURES);
    }

    @Test
    public void halfTrollUsesItsTenPixelHeadFace() {
        assertFeatureLayout(CharacterRaceRegistry.HALF_TROLL,
                10.0F, 64.0F,
                CharacterHeadIconLayout.OverlayKind
                        .LOTR_HALF_TROLL_FEATURES);
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

    private static void assertExtendedLayout(String raceId) {
        CharacterHeadIconLayout layout =
                CharacterHeadIconLayout.forConfiguredRace(raceId);
        assertEquals(
                CharacterHeadIconLayout.OverlayKind.LOTR_EXTENDED,
                layout.getOverlayKind());
        assertEquals(8.0F, layout.getFaceSize(), EPSILON);
        assertEquals(64.0F, layout.getImageHeight(), EPSILON);
    }

    private static void assertFeatureLayout(
            String raceId, float faceSize, float imageHeight,
            CharacterHeadIconLayout.OverlayKind featureKind) {
        CharacterHeadIconLayout layout =
                CharacterHeadIconLayout.forConfiguredRace(raceId);
        assertEquals(featureKind, layout.getOverlayKind());
        assertEquals(faceSize, layout.getFaceSize(), EPSILON);
        assertEquals(imageHeight, layout.getImageHeight(), EPSILON);
    }
}
