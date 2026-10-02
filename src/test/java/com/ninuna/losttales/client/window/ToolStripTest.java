package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A window's tool strip: the panel button under the tab search, alone at
 * the left; the help button at the strip's right end, the search's well a
 * third of the strip before it, and before the well the member list's
 * button where the strip has one, the full window button, the split view
 * button, the cog, a hairline and the options. A standing
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
    private static final int SPLIT_WIDTH = LostTalesUiSheet.SPLIT.getWidth();

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
        assertEquals(laid.viewX - END_GAP - SPLIT_WIDTH, laid.splitX);
        assertEquals(laid.splitX - END_GAP - LostTalesUiSheet.COG.getWidth(),
                laid.settingsX);
    }

    /**
     * A page's options stand against the cog, a hairline between the last
     * of them and the cog and between two groups, a gap either side of
     * each hairline; only the panel button stays at the left.
     */
    @Test
    public void thePagesOptionsStandAgainstTheCog() {
        ToolStrip.Layout laid = conversation(400, ToolStrip.Count.NONE);
        ToolStrip.layOptions(laid, threeOptions(),
                laid.panelX + laid.panelWidth);
        assertEquals(3, laid.options.length);
        assertEquals(2, laid.dividerX.length);
        assertEquals(laid.settingsX - END_GAP - 1, laid.dividerX[1]);
        assertEquals(laid.dividerX[1] - END_GAP - 5, laid.optionX[2]);
        assertEquals(laid.optionX[2] - END_GAP - 1, laid.dividerX[0]);
        assertEquals(laid.dividerX[0] - END_GAP - 5, laid.optionX[1]);
        assertEquals(laid.optionX[1] - END_GAP - 5, laid.optionX[0]);
    }

    /**
     * A narrow strip keeps the first options that stand whole clear of
     * the panel button; the last ones stay in the tab's options. A page
     * with no option keeps no hairline before its cog.
     */
    @Test
    public void aNarrowStripLeavesTheLastOptionsToTheMenu() {
        ToolStrip.Layout narrow = conversation(100, ToolStrip.Count.NONE);
        int after = narrow.panelX + narrow.panelWidth;
        ToolStrip.layOptions(narrow, threeOptions(), after);
        assertEquals(2, narrow.options.length);
        assertEquals("a", narrow.options[0].id);
        assertEquals("b", narrow.options[1].id);
        assertTrue(narrow.optionX[0] >= after + 2 * END_GAP);
        assertEquals(1, narrow.dividerX.length);
        ToolStrip.Layout none = conversation(400, ToolStrip.Count.NONE);
        ToolStrip.layOptions(none, java.util.Collections.<PageOption>emptyList(),
                none.panelX + none.panelWidth);
        assertEquals(0, none.options.length);
        assertEquals(0, none.dividerX.length);
    }

    private static List<PageOption> threeOptions() {
        OptionGlyph glyph = OptionGlyph.pattern("#####", "#####");
        return Arrays.asList(
                PageOption.action("a", "A", glyph).inGroup("one", ""),
                PageOption.action("b", "B", glyph).inGroup("one", ""),
                PageOption.action("c", "C", glyph).inGroup("two", ""));
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
        // member list's button would, and the split view button, the cog
        // and the options follow it.
        assertEquals(chat.wellLeft, page.wellLeft);
        assertEquals(chat.wellRight, page.wellRight);
        assertEquals(chat.helpX, page.helpX);
        assertEquals(page.wellLeft - END_GAP
                - LostTalesUiSheet.FULLSCREEN.getWidth(), page.viewX);
        assertEquals(page.viewX - END_GAP - SPLIT_WIDTH, page.splitX);
        assertEquals(page.splitX - END_GAP - LostTalesUiSheet.COG.getWidth(),
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
