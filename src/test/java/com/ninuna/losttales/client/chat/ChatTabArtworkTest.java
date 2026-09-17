package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The tab row's geometry against the artwork it is cut from. The sheet
 * is the mod's, so a re-export at another size moves the row with it
 * rather than leaving the two to drift.
 */
public final class ChatTabArtworkTest {

    @Test
    public void theTabRowFitsTheSheetsTabPieces() {
        assertSameSize(LostTalesUiSheet.TAB_LEFT, LostTalesUiSheet.TAB_RIGHT);
        assertSameSize(LostTalesUiSheet.TAB_HOVER_LEFT,
                LostTalesUiSheet.TAB_HOVER_RIGHT);
        assertSameSize(LostTalesUiSheet.TAB_SELECTED_LEFT,
                LostTalesUiSheet.TAB_SELECTED_RIGHT);
        assertSameSize(LostTalesUiSheet.TAB_LEFT, LostTalesUiSheet.TAB_HOVER_LEFT);
        // The selected pieces are their feet wider than the resting
        // ones: the feet reach past the tab on the rule's row.
        assertEquals(LostTalesUiSheet.TAB_LEFT.getWidth()
                        + ChatChannelTabBar.SELECTED_FOOT,
                LostTalesUiSheet.TAB_SELECTED_LEFT.getWidth());
        assertEquals(1, ChatChannelTabBar.SELECTED_FOOT);
        // The selected pieces are one row taller: the row they stand on
        // the rule with. Every tab stands at one top, so there is no
        // lift; a sheet with taller selected pieces would lift the tab.
        assertEquals(ChatChannelTabBar.LIFT + 1,
                LostTalesUiSheet.TAB_SELECTED_LEFT.getHeight()
                        - LostTalesUiSheet.TAB_LEFT.getHeight());
        assertEquals(0, ChatChannelTabBar.LIFT);
        // A tab draws its pieces whole and stands on the window's top
        // rule, so the row is one row taller than the artwork; a
        // re-export at another height moves the row with it.
        assertEquals("A tab is its pieces whole, plus the rule they stand on",
                ChatChannelTabBar.HEIGHT,
                LostTalesUiSheet.TAB_LEFT.getHeight() + 1);
    }

    private static void assertSameSize(LostTalesUiSheet a, LostTalesUiSheet b) {
        assertEquals(a + " and " + b + " differ in width",
                a.getWidth(), b.getWidth());
        assertEquals(a + " and " + b + " differ in height",
                a.getHeight(), b.getHeight());
    }
}
