package com.ninuna.losttales.gui.hud.quest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Each quest switch hides only what it names (QUEST-46): the tracker's
 * the tracker, the banners' the banners, the world labels' the world
 * labels; the HUD's own switch hides all three.
 */
public final class QuestHudSwitchesTest {

    @Test
    public void eachSwitchDecidesOnlyWhatItNames() {
        for (int combination = 0; combination < 8; combination++) {
            boolean tracker = (combination & 1) != 0;
            boolean banners = (combination & 2) != 0;
            boolean labels = (combination & 4) != 0;
            QuestHudSwitches shown = QuestHudSwitches.of(true, tracker,
                    banners, labels);
            assertEquals("tracker " + combination, tracker, shown.tracker);
            assertEquals("banners " + combination, banners, shown.banners);
            assertEquals("labels " + combination, labels, shown.worldLabels);
        }
    }

    @Test
    public void theTrackerOffLeavesTheBannersAndTheLabels() {
        QuestHudSwitches shown = QuestHudSwitches.of(true, false, true, true);
        assertFalse(shown.tracker);
        assertTrue(shown.banners);
        assertTrue(shown.worldLabels);
    }

    @Test
    public void theHudOffHidesAllThree() {
        QuestHudSwitches shown = QuestHudSwitches.of(false, true, true, true);
        assertFalse(shown.tracker);
        assertFalse(shown.banners);
        assertFalse(shown.worldLabels);
    }
}
