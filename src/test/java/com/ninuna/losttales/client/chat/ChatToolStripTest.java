package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A window's tool strip: the panel button under the tab search, the
 * search's well the strip's right third, and before it the member list's
 * button, where the strip has one, and the cog. A standing search's count
 * stands inside the well, with the chevrons over a conversation; a narrow
 * strip keeps no well, and its buttons stand at its right end. A page's
 * strip is the same strip with the page's panel button and no member
 * list button.
 */
public final class ChatToolStripTest {
    private static final int COUNT_WIDTH = 20;
    private static final int END_GAP = 5;
    private static final int AREA_WIDTH = LostTalesUiSheet.AREA.getWidth();
    private static final int AREA_HEIGHT = LostTalesUiSheet.AREA.getHeight();

    /** A conversation's strip, the timestamp area's person at its left. */
    private static ChatToolStrip.Layout conversation(int right,
                                                     ChatToolStrip.Count count) {
        return ChatToolStrip.layOut(0, right, 30, 3, 15, AREA_WIDTH,
                AREA_HEIGHT, true, count, COUNT_WIDTH);
    }

    @Test
    public void theWellIsTheStripsRightThirdWithTheButtonsBeforeIt() {
        ChatToolStrip.Layout laid = conversation(400, ChatToolStrip.Count.NONE);
        assertTrue(laid.hasWell);
        assertFalse(laid.counting);
        assertEquals(400 - 3, laid.wellRight);
        assertEquals(400 / 3, laid.wellRight - laid.wellLeft);
        assertEquals(laid.wellLeft - END_GAP - LostTalesUiSheet.MEMBERS.getWidth(),
                laid.membersX);
        assertEquals(laid.membersX - END_GAP - LostTalesUiSheet.COG.getWidth(),
                laid.settingsX);
        // The area's person stands centred under the tab search, the odd
        // pixel left.
        assertEquals(3 + Math.floorDiv(15 - AREA_WIDTH, 2), laid.panelX);
        // The icon at the well's right end, two clear pixels inside it.
        assertEquals(laid.wellRight - 2 - LostTalesUiSheet.SEARCH.getWidth(),
                laid.iconSlotLeft);
    }

    @Test
    public void aStandingSearchWalksInsideTheWell() {
        ChatToolStrip.Layout resting = conversation(300, ChatToolStrip.Count.NONE);
        ChatToolStrip.Layout walking = conversation(300, ChatToolStrip.Count.WALK);
        assertTrue(walking.counting);
        assertTrue(walking.walking);
        // Nothing before the well moves; the field makes the room.
        assertEquals(resting.wellLeft, walking.wellLeft);
        assertEquals(resting.settingsX, walking.settingsX);
        assertEquals(walking.iconSlotLeft - ChatToolStrip.GAP
                - LostTalesUiSheet.CHEVRON_1.getWidth(), walking.nextX);
        assertTrue(walking.countRight - COUNT_WIDTH > walking.fieldX);
        assertTrue(walking.fieldWidth < resting.fieldWidth);
    }

    @Test
    public void aTightWellKeepsItsFieldAndLeavesTheCountOut() {
        ChatToolStrip.Layout laid = conversation(150, ChatToolStrip.Count.WALK);
        assertTrue(laid.hasWell);
        assertFalse(laid.counting);
        assertFalse(laid.walking);
    }

    @Test
    public void aNarrowStripKeepsNoWellAndItsButtonsAtItsEnd() {
        ChatToolStrip.Layout laid = conversation(120, ChatToolStrip.Count.WALK);
        assertFalse(laid.hasWell);
        assertFalse(laid.counting);
        assertEquals(120 - 3 - LostTalesUiSheet.MEMBERS.getWidth(),
                laid.membersX);
    }

    @Test
    public void aPagesStripHasItsOwnPanelButtonAndNoMemberList() {
        int questWidth = LostTalesUiSheet.QUEST.getWidth();
        ChatToolStrip.Layout page = ChatToolStrip.layOut(0, 400, 30, 3, 15,
                questWidth, LostTalesUiSheet.QUEST.getHeight(), false,
                ChatToolStrip.Count.NONE, COUNT_WIDTH);
        ChatToolStrip.Layout chat = conversation(400, ChatToolStrip.Count.NONE);
        assertFalse(page.hasMembers);
        // The same well, and the cog stands where the member list's
        // button would.
        assertEquals(chat.wellLeft, page.wellLeft);
        assertEquals(chat.wellRight, page.wellRight);
        assertEquals(page.wellLeft - END_GAP - LostTalesUiSheet.COG.getWidth(),
                page.settingsX);
        assertEquals(3 + Math.floorDiv(15 - questWidth, 2), page.panelX);
    }

    @Test
    public void aPagesCountStandsAgainstTheIconWithoutChevrons() {
        ChatToolStrip.Layout found = ChatToolStrip.layOut(0, 400, 30, 3, 15,
                0, 0, false, ChatToolStrip.Count.FOUND, COUNT_WIDTH);
        assertTrue(found.counting);
        assertFalse(found.walking);
        assertEquals(found.iconSlotLeft - ChatToolStrip.GAP, found.countRight);
        assertEquals("no panel button: a width of none",
                0, found.panelWidth);
    }
}
