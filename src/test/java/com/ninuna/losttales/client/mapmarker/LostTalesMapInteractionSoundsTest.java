package com.ninuna.losttales.client.mapmarker;

import org.junit.Test;

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
}
