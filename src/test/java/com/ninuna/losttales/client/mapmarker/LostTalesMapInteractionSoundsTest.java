package com.ninuna.losttales.client.mapmarker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapInteractionSoundsTest {
    @Test
    public void enteringOrChangingAMeaningfulTargetIsAudible() {
        assertTrue(LostTalesMapInteractionSounds
                .isAudibleHoverTransition("", "marker:a"));
        assertTrue(LostTalesMapInteractionSounds
                .isAudibleHoverTransition("marker:a", "waypoint:b"));
    }

    @Test
    public void HoldingOrLeavingATargetIsSilent() {
        assertFalse(LostTalesMapInteractionSounds
                .isAudibleHoverTransition("marker:a", "marker:a"));
        assertFalse(LostTalesMapInteractionSounds
                .isAudibleHoverTransition("marker:a", ""));
        assertFalse(LostTalesMapInteractionSounds
                .isAudibleHoverTransition("", null));
    }

    @Test
    public void hoverVariationIsSubtleAndClamped() {
        assertEquals(0.17F,
                LostTalesMapInteractionSounds.hoverVolume(-1.0F),
                0.0001F);
        assertEquals(0.19F,
                LostTalesMapInteractionSounds.hoverVolume(0.5F),
                0.0001F);
        assertEquals(0.21F,
                LostTalesMapInteractionSounds.hoverVolume(2.0F),
                0.0001F);
        assertEquals(0.96F,
                LostTalesMapInteractionSounds.hoverPitch(-1.0F),
                0.0001F);
        assertEquals(1.0F,
                LostTalesMapInteractionSounds.hoverPitch(0.5F),
                0.0001F);
        assertEquals(1.04F,
                LostTalesMapInteractionSounds.hoverPitch(2.0F),
                0.0001F);
    }
}
