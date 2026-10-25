package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.client.window.MenuWindow;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The Characters page's profile heads its sections with every list's
 * heading (LOOK-24): a menu row high, its hairline on the row's foot,
 * and the words under it a clear pixel below that hairline, as under the
 * journal's headings.
 */
public final class CharacterProfileColumnTest {

    @Test
    public void aHeadingIsAMenuRowAndAClearPixel() {
        int row = MenuWindow.ROW_HEIGHT;
        int height = CharacterProfileColumn.headingHeight(row);
        // The hairline is the row's last pixel row; the next line's words
        // stand at that line's top.
        int hairline = row - 1;
        int wordsUnder = height;
        assertEquals(1, wordsUnder - hairline - 1);
        assertEquals(row + CharacterProfileColumn.HEADING_CLEARANCE, height);
    }

    @Test
    public void itGrowsWithTheMenusRows() {
        assertEquals(MenuWindow.ROW_HEIGHT + 4,
                CharacterProfileColumn.headingHeight(MenuWindow.ROW_HEIGHT + 3));
    }
}
