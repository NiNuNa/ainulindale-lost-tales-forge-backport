package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Where the missive board page's parts stand: the notices beside the
 * letter on a wide page, one of them at a time on a narrow one, the rows
 * laid out as a menu's, and the rows the pointer finds exactly where they
 * are drawn.
 */
public final class MissiveBoardLayoutTest {
    /** A menu row's height at the usual GUI scales. */
    private static final int LINE = MenuWindow.ROW_HEIGHT;

    @Test
    public void aWidePageStandsTheListBesideTheLetter() {
        MissiveBoardLayout layout = new MissiveBoardLayout(420, 240, true, LINE);
        assertTrue(layout.isSplit());
        LostTalesUiHitBox list = layout.list();
        LostTalesUiHitBox divider = layout.divider();
        LostTalesUiHitBox letter = layout.letter();
        assertEquals("the list stands from the page's side", 0.0D, list.left,
                0.0D);
        assertEquals(140.0D + 2 * MenuWindow.PADDING_X, list.width, 0.0D);
        assertEquals(MenuWindow.PADDING_X, layout.textLeft());
        assertEquals(list.right() - MenuWindow.PADDING_X, layout.textRight(),
                0.0D);
        assertEquals("the rule stands as far from the list's words as from the letter",
                layout.textRight() + MissiveBoardLayout.GUTTER, divider.left,
                0.0D);
        assertEquals(divider.right() + MissiveBoardLayout.GUTTER, letter.left, 0.0D);
        assertEquals(420 - MissiveBoardLayout.MARGIN_X, letter.right(), 0.0D);
        assertEquals("the letter reaches the page's foot; answers stand over the bar",
                240 - MissiveBoardLayout.MARGIN_Y, letter.bottom(), 0.0D);
    }

    @Test
    public void theListFoldedGivesTheLetterTheWholePage() {
        MissiveBoardLayout layout = new MissiveBoardLayout(420, 240, false, LINE);
        assertFalse(layout.isSplit());
        assertEquals(0.0D, layout.list().width, 0.0D);
        assertEquals(0.0D, layout.divider().width, 0.0D);
        assertEquals(MissiveBoardLayout.MARGIN_X, layout.letter().left, 0.0D);
    }

    @Test
    public void aNarrowPageShowsOneHalfAtATime() {
        int narrow = MissiveBoardLayout.MIN_SPLIT_WIDTH - 1;
        MissiveBoardLayout listed = new MissiveBoardLayout(narrow, 240, true, LINE);
        assertFalse(listed.isWide());
        assertEquals("the list takes the whole page, as a menu's rows do",
                narrow, listed.list().width, 0.0D);
        assertEquals(narrow - MenuWindow.PADDING_X, listed.textRight());
        assertEquals(0.0D, listed.letter().width, 0.0D);
        MissiveBoardLayout reading = new MissiveBoardLayout(narrow, 240, false, LINE);
        assertEquals(0.0D, reading.list().width, 0.0D);
        assertEquals(narrow - 2 * MissiveBoardLayout.MARGIN_X,
                reading.letter().width, 0.0D);
    }

    @Test
    public void aNoticeIsTwoMenuRowsHighUnderTheMenusPadding() {
        MissiveBoardLayout layout = new MissiveBoardLayout(420, 240, true, LINE);
        assertEquals(2 * MenuWindow.ROW_HEIGHT, layout.rowHeight());
        assertEquals(MenuWindow.PADDING_Y, layout.row(0, 0.0D).top, 0.0D);
        LostTalesUiHitBox band = layout.band();
        assertEquals(MenuWindow.PADDING_Y, band.top, 0.0D);
        assertEquals(240 - MenuWindow.PADDING_Y, band.bottom(), 0.0D);
    }

    @Test
    public void theRowUnderThePointerIsTheRowDrawnThere() {
        MissiveBoardLayout layout = new MissiveBoardLayout(420, 240, true, LINE);
        LostTalesUiHitBox second = layout.row(1, 0.0D);
        assertEquals(1, layout.rowAt(second.left + 3, second.top, 0.0D, 4));
        assertEquals(1, layout.rowAt(second.left + 3, second.bottom() - 0.5D,
                0.0D, 4));
        assertEquals(2, layout.rowAt(second.left + 3, second.bottom(), 0.0D, 4));
        assertEquals("the row answers across its whole width", 1,
                layout.rowAt(second.left, second.top, 0.0D, 4));
        assertEquals(1, layout.rowAt(second.right() - 0.5D, second.top, 0.0D, 4));
        LostTalesUiHitBox scrolled = layout.row(3, 10.5D);
        assertEquals(3, layout.rowAt(scrolled.left + 3, scrolled.top + 1,
                10.5D, 4));
        assertEquals("past the last notice", -1, layout.rowAt(
                second.left + 3, layout.row(4, 0.0D).top + 1, 0.0D, 4));
        assertEquals("beside the list", -1, layout.rowAt(
                layout.letter().left + 1, second.top, 0.0D, 4));
        assertEquals("in the padding over the rows, where a row scrolled up is cut",
                -1, layout.rowAt(second.left + 3,
                        layout.band().top - 1, 30.0D, 4));
    }

    @Test
    public void theListScrollsOnlyAsFarAsItsNotices() {
        MissiveBoardLayout layout = new MissiveBoardLayout(420, 120, true, LINE);
        assertEquals(0, layout.maxListScroll(4));
        assertEquals(9 * layout.rowHeight() - (120 - 2
                        * MenuWindow.PADDING_Y), layout.maxListScroll(9));
    }
}
