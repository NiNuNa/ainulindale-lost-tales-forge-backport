package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Characters tab keeps its roster and its profile side by side where
 * the page is wide enough, and lets them take turns where it is not; the
 * figure stands beside the words in a wide profile and above them in a
 * narrow one, where it scrolls with them.
 */
public final class CharactersLayoutTest {
    private static final int MARGIN = CharactersLayout.MARGIN;

    @Test
    public void aWidePageShowsTheRosterBesideTheProfile() {
        CharactersLayout layout = new CharactersLayout(480, 240, true);
        assertTrue(layout.isSplit());
        LostTalesUiHitBox roster = layout.roster();
        LostTalesUiHitBox profile = layout.profile();
        assertEquals(MARGIN, roster.left, 1.0E-9D);
        assertEquals(160, roster.width, 1.0E-9D);
        assertEquals(roster.right() + 2 * CharactersLayout.GUTTER + 1,
                profile.left, 1.0E-9D);
        assertEquals(480 - MARGIN, profile.right(), 1.0E-9D);
        assertEquals(240 - 2 * MARGIN, profile.height, 1.0E-9D);
    }

    @Test
    public void theRosterFoldedGivesTheProfileTheWholePage() {
        CharactersLayout layout = new CharactersLayout(480, 240, false);
        assertFalse(layout.isSplit());
        assertEquals(0, layout.roster().width, 1.0E-9D);
        assertEquals(0, layout.divider().width, 1.0E-9D);
        assertEquals(480 - 2 * MARGIN, layout.profile().width, 1.0E-9D);
    }

    @Test
    public void aNarrowPageShowsOneOfThemAtATime() {
        int narrow = CharactersLayout.MIN_SPLIT_WIDTH - 1;
        CharactersLayout out = new CharactersLayout(narrow, 240, true);
        assertFalse(out.isSplit());
        assertEquals(narrow - 2 * MARGIN, out.roster().width, 1.0E-9D);
        assertEquals("the profile waits while the roster is out", 0,
                out.profile().width, 1.0E-9D);
        CharactersLayout folded = new CharactersLayout(narrow, 240, false);
        assertEquals(narrow - 2 * MARGIN, folded.profile().width, 1.0E-9D);
    }

    @Test
    public void theFigureStandsBesideTheWordsWhereTheProfileHasRoom() {
        CharactersLayout wide = new CharactersLayout(480, 240, false);
        assertTrue(wide.figureBeside());
        LostTalesUiHitBox figure = wide.figure(40);
        assertEquals("the figure stands still as the words scroll", MARGIN,
                figure.top, 1.0E-9D);
        assertEquals(CharactersLayout.FIGURE_WIDTH, figure.width, 1.0E-9D);
        assertEquals(figure.right() + CharactersLayout.GUTTER,
                wide.words().left, 1.0E-9D);
        assertEquals(0, wide.wordsOffset());
    }

    @Test
    public void inANarrowProfileTheFigureStandsAboveTheWordsAndScrolls() {
        CharactersLayout narrow = new CharactersLayout(250, 240, false);
        assertFalse(narrow.figureBeside());
        LostTalesUiHitBox figure = narrow.figure(30);
        assertEquals(MARGIN - 30, figure.top, 1.0E-9D);
        assertEquals(CharactersLayout.FIGURE_HEIGHT, figure.height, 1.0E-9D);
        assertEquals(narrow.profile().width, narrow.words().width, 1.0E-9D);
        assertEquals(CharactersLayout.FIGURE_HEIGHT + CharactersLayout.GUTTER,
                narrow.wordsOffset());
    }
}
