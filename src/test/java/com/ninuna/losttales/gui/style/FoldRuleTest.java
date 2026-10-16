package com.ninuna.losttales.gui.style;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The one fold rule the chat's long messages and a profile's History
 * share: past five rows a text keeps four and says Read more; opened it
 * shows every row and says Show less. One row past the four stays whole.
 */
public final class FoldRuleTest {

    @Test
    public void aTextFoldsOnlyPastFiveRows() {
        assertFalse(FoldRule.folds(0));
        assertFalse(FoldRule.folds(FoldRule.KEPT_ROWS));
        assertFalse("folded it would be no shorter",
                FoldRule.folds(FoldRule.KEPT_ROWS + 1));
        assertTrue(FoldRule.folds(FoldRule.KEPT_ROWS + 2));
        assertEquals(4, FoldRule.KEPT_ROWS);
    }

    @Test
    public void aFoldedTextKeepsFourRowsAndAnOpenOneShowsEvery() {
        assertEquals(FoldRule.KEPT_ROWS, FoldRule.shownRows(20, false));
        assertEquals(20, FoldRule.shownRows(20, true));
        assertEquals("a text that does not fold shows every row", 5,
                FoldRule.shownRows(5, false));
        assertEquals(0, FoldRule.shownRows(0, false));
        assertEquals(0, FoldRule.shownRows(-3, true));
    }

    @Test
    public void theWordsUnderItSayWhatAPressDoes() {
        assertEquals("gui.losttales.read_more", FoldRule.toggleKey(false));
        assertEquals("gui.losttales.show_less", FoldRule.toggleKey(true));
    }
}
