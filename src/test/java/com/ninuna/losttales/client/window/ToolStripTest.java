package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A window's tool strip: the panel button under the tab search; the help
 * button at the strip's right end, the search's well a third of the strip
 * before it, and before the well the member list's button where the strip
 * has one, the full window button, the cog and the options. A standing
 * search's count stands inside the well, with the chevrons over a
 * conversation; a narrow strip keeps no well, and its buttons stand
 * before the help button. A page's strip is the same strip with the
 * page's panel button and no member list button.
 */
public final class ToolStripTest {
    private static final int COUNT_WIDTH = 20;
    private static final int END_GAP = 5;
    private static final int AREA_WIDTH = LostTalesUiSheet.AREA.getWidth();
    private static final int AREA_HEIGHT = LostTalesUiSheet.AREA.getHeight();
    private static final int HELP_WIDTH = LostTalesUiSheet.QUESTION.getWidth();

    /** A conversation's strip, the timestamp area's person at its left. */
    private static ToolStrip.Layout conversation(int right,
                                                     ToolStrip.Count count) {
        return ToolStrip.layOut(0, right, 30, 3, 15, AREA_WIDTH,
                AREA_HEIGHT, true, count, COUNT_WIDTH);
    }

    @Test
    public void theHelpEndsTheStripAndTheWellStandsBeforeIt() {
        ToolStrip.Layout laid = conversation(400, ToolStrip.Count.NONE);
        assertTrue(laid.hasWell);
        assertFalse(laid.counting);
        assertEquals(400 - 3 - HELP_WIDTH, laid.helpX);
        assertEquals(laid.helpX - END_GAP, laid.wellRight);
        assertEquals(400 / 3, laid.wellRight - laid.wellLeft);
        // The icon at the well's right end, two clear pixels inside it.
        assertEquals(laid.wellRight - 2 - LostTalesUiSheet.SEARCH.getWidth(),
                laid.iconSlotLeft);
        // The area's person stands centred under the tab search, the odd
        // pixel left.
        assertEquals(3 + Math.floorDiv(15 - AREA_WIDTH, 2), laid.panelX);
    }

    @Test
    public void theMemberListStandsRightOfTheFullWindowButton() {
        ToolStrip.Layout laid = conversation(400, ToolStrip.Count.NONE);
        assertEquals(laid.wellLeft - END_GAP
                - LostTalesUiSheet.MEMBERS.getWidth(), laid.membersX);
        assertEquals(laid.membersX - END_GAP
                - LostTalesUiSheet.FULLSCREEN.getWidth(), laid.viewX);
        assertEquals(laid.viewX - END_GAP - LostTalesUiSheet.COG.getWidth(),
                laid.settingsX);
    }

    /**
     * A page's options stand after the panel button, a group's gap apart
     * from it and between two groups; those the strip has no room for
     * before the cog are left to the tab's options.
     */
    @Test
    public void thePagesOptionsStandAfterThePanelAsFarAsTheyFit() {
        ToolStrip.Layout laid = conversation(400, ToolStrip.Count.NONE);
        OptionGlyph glyph = OptionGlyph.pattern("#####", "#####");
        List<PageOption> options = Arrays.asList(
                PageOption.action("a", "A", glyph).inGroup("one", ""),
                PageOption.action("b", "B", glyph).inGroup("one", ""),
                PageOption.action("c", "C", glyph).inGroup("two", ""));
        int after = laid.panelX + laid.panelWidth;
        ToolStrip.layOptions(laid, options, after);
        assertEquals(3, laid.options.length);
        assertEquals(after + 2 * END_GAP, laid.optionX[0]);
        assertEquals(laid.optionX[0] + 5 + END_GAP, laid.optionX[1]);
        // A group's gap between the two groups.
        assertEquals(laid.optionX[1] + 5 + 2 * END_GAP, laid.optionX[2]);
        // A narrow strip keeps only what stands whole before the cog.
        ToolStrip.Layout narrow = conversation(80, ToolStrip.Count.NONE);
        ToolStrip.layOptions(narrow, options,
                narrow.panelX + narrow.panelWidth);
        for (int index = 0; index < narrow.options.length; index++) {
            assertTrue(narrow.optionX[index] + 5
                    <= narrow.settingsX - 2 * END_GAP);
        }
        assertEquals(1, narrow.options.length);
    }

    @Test
    public void aStandingSearchWalksInsideTheWell() {
        ToolStrip.Layout resting = conversation(300, ToolStrip.Count.NONE);
        ToolStrip.Layout walking = conversation(300, ToolStrip.Count.WALK);
        assertTrue(walking.counting);
        assertTrue(walking.walking);
        // Nothing before the well moves; the field makes the room.
        assertEquals(resting.wellLeft, walking.wellLeft);
        assertEquals(resting.settingsX, walking.settingsX);
        assertEquals(walking.iconSlotLeft - ToolStrip.GAP
                - LostTalesUiSheet.CHEVRON_1.getWidth(), walking.nextX);
        assertTrue(walking.countRight - COUNT_WIDTH > walking.fieldX);
        assertTrue(walking.fieldWidth < resting.fieldWidth);
    }

    @Test
    public void aTightWellKeepsItsFieldAndLeavesTheCountOut() {
        ToolStrip.Layout laid = conversation(170, ToolStrip.Count.WALK);
        assertTrue(laid.hasWell);
        assertFalse(laid.counting);
        assertFalse(laid.walking);
    }

    @Test
    public void aNarrowStripKeepsNoWellAndItsButtonsBeforeTheHelp() {
        ToolStrip.Layout laid = conversation(120, ToolStrip.Count.WALK);
        assertFalse(laid.hasWell);
        assertFalse(laid.counting);
        assertEquals(120 - 3 - HELP_WIDTH, laid.helpX);
        assertEquals(laid.helpX - END_GAP
                - LostTalesUiSheet.MEMBERS.getWidth(), laid.membersX);
        assertEquals(laid.membersX - END_GAP
                - LostTalesUiSheet.FULLSCREEN.getWidth(), laid.viewX);
    }

    @Test
    public void aPagesStripHasItsOwnPanelButtonAndNoMemberList() {
        int questWidth = LostTalesUiSheet.QUEST.getWidth();
        ToolStrip.Layout page = ToolStrip.layOut(0, 400, 30, 3, 15,
                questWidth, LostTalesUiSheet.QUEST.getHeight(), false,
                ToolStrip.Count.NONE, COUNT_WIDTH);
        ToolStrip.Layout chat = conversation(400, ToolStrip.Count.NONE);
        assertFalse(page.hasMembers);
        // The same well and help; the full window button stands where the
        // member list's button would, and the cog and options follow it.
        assertEquals(chat.wellLeft, page.wellLeft);
        assertEquals(chat.wellRight, page.wellRight);
        assertEquals(chat.helpX, page.helpX);
        assertEquals(page.wellLeft - END_GAP
                - LostTalesUiSheet.FULLSCREEN.getWidth(), page.viewX);
        assertEquals(page.viewX - END_GAP - LostTalesUiSheet.COG.getWidth(),
                page.settingsX);
        assertEquals(3 + Math.floorDiv(15 - questWidth, 2), page.panelX);
    }

    @Test
    public void aPagesCountStandsAgainstTheIconWithoutChevrons() {
        ToolStrip.Layout found = ToolStrip.layOut(0, 400, 30, 3, 15,
                0, 0, false, ToolStrip.Count.FOUND, COUNT_WIDTH);
        assertTrue(found.counting);
        assertFalse(found.walking);
        assertEquals(found.iconSlotLeft - ToolStrip.GAP, found.countRight);
        assertEquals("no panel button: a width of none",
                0, found.panelWidth);
    }
}
