package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Characters page keeps its roster and its profile side by side where
 * the page is wide enough, and lets them take turns where it is not.
 */
public final class CharactersLayoutTest {
    private static final int MARGIN_X = CharactersLayout.MARGIN_X;
    private static final int MARGIN_Y = CharactersLayout.MARGIN_Y;

    @Test
    public void aWidePageShowsTheRosterBesideTheProfile() {
        CharactersLayout layout = new CharactersLayout(480, 240, true);
        assertTrue(layout.isSplit());
        LostTalesUiHitBox roster = layout.roster();
        LostTalesUiHitBox profile = layout.profile();
        assertEquals(MARGIN_X, roster.left, 1.0E-9D);
        assertEquals(160, roster.width, 1.0E-9D);
        assertEquals(roster.right() + 2 * CharactersLayout.GUTTER + 1,
                profile.left, 1.0E-9D);
        assertEquals(480 - MARGIN_X, profile.right(), 1.0E-9D);
        assertEquals(240 - 2 * MARGIN_Y, profile.height, 1.0E-9D);
    }

    @Test
    public void theRosterFoldedGivesTheProfileTheWholePage() {
        CharactersLayout layout = new CharactersLayout(480, 240, false);
        assertFalse(layout.isSplit());
        assertEquals(0, layout.roster().width, 1.0E-9D);
        assertEquals(0, layout.divider().width, 1.0E-9D);
        assertEquals(480 - 2 * MARGIN_X, layout.profile().width, 1.0E-9D);
    }

    @Test
    public void aNarrowPageShowsOneOfThemAtATime() {
        int narrow = CharactersLayout.MIN_SPLIT_WIDTH - 1;
        CharactersLayout out = new CharactersLayout(narrow, 240, true);
        assertFalse(out.isSplit());
        assertEquals(narrow - 2 * MARGIN_X, out.roster().width, 1.0E-9D);
        assertEquals("the profile waits while the roster is out", 0,
                out.profile().width, 1.0E-9D);
        CharactersLayout folded = new CharactersLayout(narrow, 240, false);
        assertEquals(narrow - 2 * MARGIN_X, folded.profile().width, 1.0E-9D);
    }
}
