package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Where the missive board page's parts stand: the notices beside the
 * letter on a wide page, one of them at a time on a narrow one, and the
 * rows the pointer finds exactly where they are drawn.
 */
public final class MissiveBoardLayoutTest {

    @Test
    public void aWidePageStandsTheListBesideTheLetter() {
        MissiveBoardLayout layout = new MissiveBoardLayout(420, 240, true);
        assertTrue(layout.isSplit());
        LostTalesUiHitBox list = layout.list();
        LostTalesUiHitBox divider = layout.divider();
        LostTalesUiHitBox letter = layout.letter();
        assertEquals(MissiveBoardLayout.MARGIN, list.left, 0.0D);
        assertEquals(140.0D, list.width, 0.0D);
        assertEquals(list.right() + MissiveBoardLayout.GUTTER, divider.left, 0.0D);
        assertEquals(divider.right() + MissiveBoardLayout.GUTTER, letter.left, 0.0D);
        assertEquals(420 - MissiveBoardLayout.MARGIN, letter.right(), 0.0D);
        assertEquals("the letter reaches the page's foot; answers stand over the bar",
                240 - MissiveBoardLayout.MARGIN, letter.bottom(), 0.0D);
    }

    @Test
    public void theListFoldedGivesTheLetterTheWholePage() {
        MissiveBoardLayout layout = new MissiveBoardLayout(420, 240, false);
        assertFalse(layout.isSplit());
        assertEquals(0.0D, layout.list().width, 0.0D);
        assertEquals(0.0D, layout.divider().width, 0.0D);
        assertEquals(MissiveBoardLayout.MARGIN, layout.letter().left, 0.0D);
    }

    @Test
    public void aNarrowPageShowsOneHalfAtATime() {
        int narrow = MissiveBoardLayout.MIN_SPLIT_WIDTH - 1;
        MissiveBoardLayout listed = new MissiveBoardLayout(narrow, 240, true);
        assertFalse(listed.isWide());
        assertEquals(narrow - 2 * MissiveBoardLayout.MARGIN,
                listed.list().width, 0.0D);
        assertEquals(0.0D, listed.letter().width, 0.0D);
        MissiveBoardLayout reading = new MissiveBoardLayout(narrow, 240, false);
        assertEquals(0.0D, reading.list().width, 0.0D);
        assertEquals(narrow - 2 * MissiveBoardLayout.MARGIN,
                reading.letter().width, 0.0D);
    }

    @Test
    public void theRowUnderThePointerIsTheRowDrawnThere() {
        MissiveBoardLayout layout = new MissiveBoardLayout(420, 240, true);
        LostTalesUiHitBox second = layout.row(1, 0.0D);
        assertEquals(1, layout.rowAt(second.left + 3, second.top, 0.0D, 4));
        assertEquals(1, layout.rowAt(second.left + 3, second.bottom() - 0.5D,
                0.0D, 4));
        assertEquals(2, layout.rowAt(second.left + 3, second.bottom(), 0.0D, 4));
        LostTalesUiHitBox scrolled = layout.row(3, 10.5D);
        assertEquals(3, layout.rowAt(scrolled.left + 3, scrolled.top + 1,
                10.5D, 4));
        assertEquals("past the last notice", -1, layout.rowAt(
                second.left + 3, layout.row(4, 0.0D).top + 1, 0.0D, 4));
        assertEquals("beside the list", -1, layout.rowAt(
                layout.letter().left + 1, second.top, 0.0D, 4));
    }

    @Test
    public void theListScrollsOnlyAsFarAsItsNotices() {
        MissiveBoardLayout layout = new MissiveBoardLayout(420, 120, true);
        assertEquals(0, layout.maxListScroll(4));
        assertEquals(9 * MissiveBoardLayout.ROW_HEIGHT - (120 - 2
                        * MissiveBoardLayout.MARGIN), layout.maxListScroll(9));
    }
}
