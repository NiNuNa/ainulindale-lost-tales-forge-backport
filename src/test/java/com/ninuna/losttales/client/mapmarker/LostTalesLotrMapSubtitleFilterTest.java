package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesLotrMapSubtitleFilterTest {
    /** LOTR's own template for the operator teleport hint. */
    private static final String TELEPORT = "Press '%s' to /tp";

    @Test
    public void aRenderedTemplateIsRecognisedWhateverKeyItNames() {
        assertTrue(LostTalesLotrMapLayout.matchesTemplate(
                "Press 'M' to /tp", TELEPORT));
        assertTrue(LostTalesLotrMapLayout.matchesTemplate(
                "Press 'NUMPAD 0' to /tp", TELEPORT));
        // The placeholder may legitimately render as nothing at all.
        assertTrue(LostTalesLotrMapLayout.matchesTemplate(
                "Press '' to /tp", TELEPORT));
    }

    @Test
    public void otherSubtitlesAreLeftAlone() {
        assertFalse(LostTalesLotrMapLayout.matchesTemplate(
                "Press 'M' to fast travel to the selected waypoint",
                TELEPORT));
        assertFalse(LostTalesLotrMapLayout.matchesTemplate(
                "x: 100, z: -200", TELEPORT));
        assertFalse(LostTalesLotrMapLayout.matchesTemplate("", TELEPORT));
        assertFalse(LostTalesLotrMapLayout.matchesTemplate(
                null, TELEPORT));
    }

    @Test
    public void aLineTooShortToHoldBothEndsIsNotAMatch() {
        // "Press ' to /tp" has the prefix and the suffix overlapping, which
        // is not the same sentence with an empty key.
        assertFalse(LostTalesLotrMapLayout.matchesTemplate(
                "Press '/tp", TELEPORT));
    }

    @Test
    public void aTemplateWithoutAPlaceholderMatchesExactly() {
        assertTrue(LostTalesLotrMapLayout.matchesTemplate(
                "Teleport", "Teleport"));
        assertFalse(LostTalesLotrMapLayout.matchesTemplate(
                "Teleport now", "Teleport"));
        assertFalse(LostTalesLotrMapLayout.matchesTemplate(
                "Press 'M' to /tp", null));
    }
}
