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
 * button where the strip has one, the borderless button, the split view
 * button, the cog, a hairline and the options, and a hairline before them
 * where the panel button stands: the groups the page's options menu
 * parts. Options a narrow strip has no room for go behind its overflow
 * button. A standing
 * search's count stands inside the well, with the chevrons over a
 * conversation; a narrow strip keeps no well, and its buttons stand
 * before the help button. A page's strip is the same strip with the
 * page's panel button and no member list button.
 */
public final class ToolStripTest {
    private static final int COUNT_WIDTH = 20;
    /** Between two buttons. */
    private static final int END_GAP = 5;
    /** Between a button and the frame or a hairline. */
    private static final int EDGE_GAP = 7;
    private static final int AREA_WIDTH = LostTalesUiSheet.AREA.getWidth();
    private static final int AREA_HEIGHT = LostTalesUiSheet.AREA.getHeight();
    private static final int HELP_WIDTH = LostTalesUiSheet.QUESTION.getWidth();
    private static final int SPLIT_WIDTH = LostTalesUiSheet.SPLIT.getWidth();
    private static final int DUPLICATE_WIDTH = LostTalesUiSheet.COPY.getWidth();

    /** A conversation's strip, the timestamp area's person at its left. */
    private static ToolStrip.Layout conversation(int right,
                                                     ToolStrip.Count count) {
        return ToolStrip.layOut(0, right, 30, AREA_WIDTH,
                AREA_HEIGHT, true, count, COUNT_WIDTH);
    }

    @Test
    public void theHelpEndsTheStripAndTheWellStandsBeforeIt() {
        ToolStrip.Layout laid = conversation(400, ToolStrip.Count.NONE);
        assertTrue(laid.hasWell);
        assertFalse(laid.counting);
        assertEquals(400 - EDGE_GAP - HELP_WIDTH, laid.helpX);
        assertEquals(laid.helpX - END_GAP, laid.wellRight);
        assertEquals(400 / 3, laid.wellRight - laid.wellLeft);
        // The icon at the well's right end, two clear pixels inside it.
        assertEquals(laid.wellRight - 2 - LostTalesUiSheet.SEARCH.getWidth(),
                laid.iconSlotLeft);
        // The area's person stands an edge's gap in from the frame.
        assertEquals(EDGE_GAP, laid.panelX);
    }

    @Test
    public void theMemberListStandsRightOfTheBorderlessButton() {
        ToolStrip.Layout laid = conversation(400, ToolStrip.Count.NONE);
        assertEquals(laid.wellLeft - END_GAP
                - LostTalesUiSheet.MEMBERS.getWidth(), laid.membersX);
        assertEquals(laid.membersX - END_GAP
                - LostTalesUiSheet.FULLSCREEN.getWidth(), laid.viewX);
        assertEquals(laid.viewX - END_GAP - DUPLICATE_WIDTH, laid.duplicateX);
        assertEquals(laid.duplicateX - END_GAP - SPLIT_WIDTH, laid.splitX);
        assertEquals(laid.splitX - END_GAP - LostTalesUiSheet.COG.getWidth(),
                laid.settingsX);
    }

    /**
     * A page's options stand against the cog, a hairline between the last
     * of them and the cog, between two groups and between the panel
     * button's group and the first of them, a gap either side of each
     * hairline; only the panel button stays at the left.
     */
    @Test
    public void thePagesOptionsStandAgainstTheCog() {
        ToolStrip.Layout laid = conversation(400, ToolStrip.Count.NONE);
        ToolStrip.layOptions(laid, threeOptions(),
                laid.panelX + laid.panelWidth, true);
        assertEquals(3, laid.options.length);
        assertEquals(-1, laid.overflowX);
        assertEquals(3, laid.dividerX.length);
        assertEquals(laid.settingsX - EDGE_GAP - 1, laid.dividerX[2]);
        assertEquals(laid.dividerX[2] - EDGE_GAP - 5, laid.optionX[2]);
        assertEquals(laid.optionX[2] - EDGE_GAP - 1, laid.dividerX[1]);
        assertEquals(laid.dividerX[1] - EDGE_GAP - 5, laid.optionX[1]);
        assertEquals(laid.optionX[1] - END_GAP - 5, laid.optionX[0]);
        assertEquals(laid.optionX[0] - EDGE_GAP - 1, laid.dividerX[0]);
    }

    /**
     * A narrow strip keeps the first options that stand whole clear of
     * the panel button, and the rest behind its overflow button, which
     * stands after them and before the hairline at the cog: every option
     * is still on the strip.
     */
    @Test
    public void aNarrowStripPutsTheLastOptionsBehindItsOverflow() {
        // As narrow as the strip was before the duplicate button, plus its room.
        ToolStrip.Layout narrow = conversation(100 + DUPLICATE_WIDTH + END_GAP,
                ToolStrip.Count.NONE);
        int after = narrow.panelX + narrow.panelWidth;
        ToolStrip.layOptions(narrow, threeOptions(), after, true);
        assertTrue(narrow.options.length < 3);
        assertTrue(narrow.overflowX >= 0);
        int last = narrow.dividerX.length - 1;
        assertEquals(narrow.settingsX - EDGE_GAP - 1, narrow.dividerX[last]);
        assertEquals(narrow.dividerX[last] - EDGE_GAP
                - ToolStrip.OVERFLOW_WIDTH, narrow.overflowX);
        assertTrue(narrow.dividerX[0] >= after + EDGE_GAP);
        for (int index = 0; index < narrow.options.length; index++) {
            assertEquals(threeOptions().get(index).id, narrow.options[index].id);
            assertTrue(narrow.optionX[index] < narrow.overflowX);
        }
    }

    /**
     * A page with no option keeps a hairline before its cog only where a
     * panel button stands left of it; with neither, the strip has none.
     */
    @Test
    public void aPageWithNoOptionPartsOnlyThePanelButton() {
        ToolStrip.Layout panelled = conversation(400, ToolStrip.Count.NONE);
        ToolStrip.layOptions(panelled,
                java.util.Collections.<PageOption>emptyList(),
                panelled.panelX + panelled.panelWidth, true);
        assertEquals(0, panelled.options.length);
        assertEquals(1, panelled.dividerX.length);
        assertEquals(panelled.settingsX - EDGE_GAP - 1, panelled.dividerX[0]);
        ToolStrip.Layout bare = conversation(400, ToolStrip.Count.NONE);
        ToolStrip.layOptions(bare, java.util.Collections.<PageOption>emptyList(),
                bare.panelX + bare.panelWidth, false);
        assertEquals(0, bare.dividerX.length);
        assertEquals(-1, bare.overflowX);
    }

    /**
     * An option marked with what waits behind it (the inbox) stands its
     * mark beside its glyph, as the {@code +} does, and the strip makes
     * room for both; with nothing waiting it is its glyph alone.
     */
    @Test
    public void aMarkedOptionStandsItsMarkBesideIt() {
        OptionGlyph inbox = OptionGlyph.sprite(LostTalesUiSheet.INBOX,
                LostTalesUiSheet.INBOX_LIT);
        TabMark three = TabMark.pings(3);
        OptionGlyph marked = OptionGlyph.withMark(inbox, three);
        assertEquals(inbox.width() + OptionGlyph.MARK_GAP + three.width(),
                marked.width());
        assertEquals(inbox.height(), marked.height());
        assertTrue(OptionGlyph.withMark(inbox, TabMark.NONE) == inbox);
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
        assertEquals(120 - EDGE_GAP - HELP_WIDTH, laid.helpX);
        assertEquals(laid.helpX - END_GAP
                - LostTalesUiSheet.MEMBERS.getWidth(), laid.membersX);
        assertEquals(laid.membersX - END_GAP
                - LostTalesUiSheet.FULLSCREEN.getWidth(), laid.viewX);
    }

    @Test
    public void aPagesStripHasItsOwnPanelButtonAndNoMemberList() {
        int questWidth = LostTalesUiSheet.QUEST.getWidth();
        ToolStrip.Layout page = ToolStrip.layOut(0, 400, 30,
                questWidth, LostTalesUiSheet.QUEST.getHeight(), false,
                ToolStrip.Count.NONE, COUNT_WIDTH);
        ToolStrip.Layout chat = conversation(400, ToolStrip.Count.NONE);
        assertFalse(page.hasMembers);
        // The same help and a well as wide against it; the borderless
        // button stands where the member list's button would, and the
        // duplicate button, the split view button, the cog and the options
        // follow it.
        assertEquals(chat.helpX, page.helpX);
        assertEquals(page.helpX - END_GAP, page.wellRight);
        assertEquals(chat.wellRight, page.wellRight);
        assertEquals(chat.wellRight - chat.wellLeft,
                page.wellRight - page.wellLeft);
        assertEquals(page.wellLeft - END_GAP
                - LostTalesUiSheet.FULLSCREEN.getWidth(), page.viewX);
        assertEquals(page.viewX - END_GAP - DUPLICATE_WIDTH, page.duplicateX);
        assertEquals(page.duplicateX - END_GAP - SPLIT_WIDTH, page.splitX);
        assertEquals(page.splitX - END_GAP - LostTalesUiSheet.COG.getWidth(),
                page.settingsX);
        assertEquals(EDGE_GAP, page.panelX);
    }

    @Test
    public void aPagesCountStandsAgainstTheIconWithoutChevrons() {
        ToolStrip.Layout found = ToolStrip.layOut(0, 400, 30,
                0, 0, false, ToolStrip.Count.FOUND, COUNT_WIDTH);
        assertTrue(found.counting);
        assertFalse(found.walking);
        assertEquals(found.iconSlotLeft - ToolStrip.GAP, found.countRight);
        assertEquals("no panel button: a width of none",
                0, found.panelWidth);
    }
}
