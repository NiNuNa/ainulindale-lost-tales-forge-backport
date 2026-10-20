package com.ninuna.losttales.gui.hud.quest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The quest tracker's box, which its two numbers place (MAP-10). */
public final class LostTalesQuestHudRendererTest {

    @Test
    public void theBoxHoldsTwoQuestsOfOneLine() {
        // Padding 6 above and below, two entries of 18 + one 10-pixel line, a 5-pixel gap.
        assertEquals(73, LostTalesQuestHudRenderer.boxHeight(4));
        assertEquals(73, LostTalesQuestHudRenderer.boxHeight(8));
        assertEquals(73, LostTalesQuestHudRenderer.boxHeight(
                LostTalesQuestHudRenderer.BOX_QUESTS));
    }

    @Test
    public void oneQuestTrackedHasABoxOfOne() {
        assertEquals(40, LostTalesQuestHudRenderer.boxHeight(1));
        assertEquals(40, LostTalesQuestHudRenderer.boxHeight(0));
    }
}
