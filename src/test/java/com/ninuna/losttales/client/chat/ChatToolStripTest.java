package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A window's tool strip: the area's button under the tab search, the
 * member list's button at the right end, and the search's short well
 * before it, which gives the count and the chevrons room while a search
 * stands and leaves narrow windows altogether.
 */
public final class ChatToolStripTest {
    private static final int COUNT_WIDTH = 40;

    @Test
    public void theWellStandsShortAtTheStripsRightBeforeTheListsButton() {
        ChatToolStrip.Layout laid = ChatToolStrip.layOut(0, 400, 30,
                3, 15, false, COUNT_WIDTH);
        assertTrue(laid.hasWell);
        assertFalse(laid.walking);
        assertEquals(400 - 3 - LostTalesUiSheet.MEMBERS.getWidth(),
                laid.membersX);
        assertEquals(laid.membersX - 5, laid.wellRight);
        assertEquals(ChatToolStrip.WELL_WIDTH, laid.wellRight - laid.wellLeft);
        // The area's person stands centred under the tab search, the odd
        // pixel left.
        assertEquals(3 + Math.floorDiv(15 - LostTalesUiSheet.AREA.getWidth(), 2),
                laid.areaX);
        // The icon at the well's right end, two clear pixels inside it.
        assertEquals(laid.wellRight - 2 - LostTalesUiSheet.SEARCH.getWidth(),
                laid.iconSlotLeft);
    }

    @Test
    public void aStandingSearchMakesRoomForItsCountAndChevrons() {
        ChatToolStrip.Layout resting = ChatToolStrip.layOut(0, 150, 30,
                3, 15, false, COUNT_WIDTH);
        ChatToolStrip.Layout walking = ChatToolStrip.layOut(0, 150, 30,
                3, 15, true, COUNT_WIDTH);
        assertTrue(walking.walking);
        assertTrue(walking.wellLeft > resting.wellLeft);
        assertEquals(walking.wellLeft - ChatToolStrip.GAP
                - LostTalesUiSheet.CHEVRON_1.getWidth(), walking.nextX);
        assertTrue(walking.countRight > walking.areaX);
    }

    @Test
    public void aNarrowStripKeepsNoWell() {
        ChatToolStrip.Layout laid = ChatToolStrip.layOut(0, 60, 30, 3,
                15, true, COUNT_WIDTH);
        assertFalse(laid.hasWell);
        assertFalse(laid.walking);
    }
}
