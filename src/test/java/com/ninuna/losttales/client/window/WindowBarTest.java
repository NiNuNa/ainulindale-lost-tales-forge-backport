package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Every page's bar reads as the chat's does: the tab and identity buttons
 * first, then buttons from the left, the field in the room left over,
 * words and glyphs from the right, and an ending button last; a short bar
 * gives up the tab's name first, then words, before anything else.
 */
public final class WindowBarTest {
    /** Six pixels a letter: enough to lay a bar out without a font. */
    private static final WindowBar.Measure SIX = new WindowBar.Measure() {
        @Override
        public int width(String text) {
            return text.length() * 6;
        }
    };

    private static BarItem button(String id) {
        return BarItem.button(id, id, LostTalesUiSheet.SEND,
                LostTalesUiSheet.SEND_HOVER);
    }

    private static WindowBar.Placed of(List<WindowBar.Placed> placed,
                                       String id) {
        for (WindowBar.Placed each : placed) {
            if (each.item.id.equals(id)) {
                return each;
            }
        }
        throw new AssertionError("no " + id);
    }

    @Test
    public void buttonsStandFromTheLeftAndTheEndingOneLastOnTheRight() {
        List<WindowBar.Placed> placed = WindowBar.layOut(Arrays.asList(
                button("track"), button("share"), button("abandon").ending()),
                0, 400, SIX);
        WindowBar.Placed track = of(placed, "track");
        WindowBar.Placed share = of(placed, "share");
        WindowBar.Placed abandon = of(placed, "abandon");
        assertEquals(WindowBar.GAP, track.left);
        assertEquals("framed buttons stand close", track.right
                + WindowBar.BUTTON_GAP, share.left);
        assertEquals(400 - WindowBar.GAP, abandon.right);
        assertFalse(track.compact);
    }

    @Test
    public void aButtonHoldsItsIconAndWordInTheWideInset() {
        List<WindowBar.Placed> placed = WindowBar.layOut(Arrays.asList(
                button("abc")), 0, 400, SIX);
        WindowBar.Placed abc = of(placed, "abc");
        assertEquals(2 * LostTalesUiFramedButton.WIDE_INSET + TabIcons.SIZE
                + TabIcons.GAP + 3 * 6 - 1, abc.right - abc.left);
    }

    @Test
    public void theFieldTakesTheRoomBetweenTheGroupsInsideItsDividers() {
        List<WindowBar.Placed> placed = WindowBar.layOut(Arrays.asList(
                BarItem.field("invite", null, "Invite"), button("leave")
                        .ending()), 0, 300, SIX);
        WindowBar.Placed field = of(placed, "invite");
        WindowBar.Placed leave = of(placed, "leave");
        assertEquals("at the bar's own edge, no divider", WindowBar.GAP,
                field.left);
        assertEquals(leave.left - WindowBar.GAP
                - WindowStyle.DIVIDER_WIDTH - WindowBar.WELL_GAP, field.right);
    }

    @Test
    public void wordsAndGlyphsStandAtTheRightEnd() {
        List<WindowBar.Placed> placed = WindowBar.layOut(Arrays.asList(
                button("here"), BarItem.words("date"),
                BarItem.glyph("out", LostTalesUiSheet.MINUS,
                        LostTalesUiSheet.MINUS_HOVER, "Zoom out"),
                BarItem.glyph("in", LostTalesUiSheet.PLUS,
                        LostTalesUiSheet.PLUS_HOVER, "Zoom in")), 0, 400, SIX);
        WindowBar.Placed out = of(placed, "out");
        WindowBar.Placed in = of(placed, "in");
        assertEquals(400 - WindowBar.GAP, in.right);
        assertEquals(in.left - WindowBar.GLYPH_MARGIN, out.right);
        assertTrue("the words before the glyphs",
                of(placed, "").right <= out.left - WindowBar.GAP);
    }

    @Test
    public void aShortBarGivesUpWordsBeforeIcons() {
        List<BarItem> items = Arrays.asList(button("current location"),
                button("create waypoint"), BarItem.words("a long date"));
        List<WindowBar.Placed> placed = WindowBar.layOut(items, 0, 80, SIX);
        for (WindowBar.Placed each : placed) {
            assertTrue(each.item.kind != BarItem.Kind.WORDS);
            assertTrue("the icon alone", each.compact);
            assertEquals(LostTalesUiFramedButton.HEIGHT,
                    each.right - each.left);
        }
        assertEquals(2, placed.size());
    }

    /** The tab and identity buttons, as the bar puts them before a page's items. */
    private static List<BarItem> withLead(String tab, BarItem... items) {
        List<BarItem> all = new java.util.ArrayList<BarItem>();
        all.add(BarItem.tabButton(new BarLeadTest.Tab(tab, true), ""));
        all.add(BarItem.identityButton());
        all.addAll(Arrays.asList(items));
        return all;
    }

    @Test
    public void theTabAndIdentityButtonsComeFirstThenADivider() {
        List<WindowBar.Placed> placed = WindowBar.layOut(withLead("Journal",
                button("track"), button("abandon").ending()), 0, 400, SIX);
        WindowBar.Placed tab = of(placed, BarItem.TAB_ID);
        WindowBar.Placed identity = of(placed, BarItem.IDENTITY_ID);
        WindowBar.Placed track = of(placed, "track");
        assertEquals(WindowBar.GAP, tab.left);
        assertEquals(tab.right + BarLead.BUTTON_GAP, identity.left);
        assertEquals(BarLead.IDENTITY_SIZE, identity.right - identity.left);
        assertEquals("the page's own start past a divider", identity.right
                + WindowBar.GAP + WindowStyle.DIVIDER_WIDTH + WindowBar.GAP,
                track.left);
        assertTrue(track.afterLead);
        assertFalse(of(placed, "abandon").afterLead);
        assertEquals(400 - WindowBar.GAP, of(placed, "abandon").right);
    }

    @Test
    public void aFieldAfterTheLeadStandsPastItsOwnDivider() {
        List<WindowBar.Placed> placed = WindowBar.layOut(withLead("Fellowship",
                BarItem.field("invite", null, "Invite")), 0, 300, SIX);
        WindowBar.Placed identity = of(placed, BarItem.IDENTITY_ID);
        WindowBar.Placed field = of(placed, "invite");
        assertEquals(identity.right + WindowBar.GAP + WindowStyle.DIVIDER_WIDTH
                + WindowBar.WELL_GAP, field.left);
        assertFalse(field.afterLead);
    }

    @Test
    public void aShortBarGivesUpTheTabsNameBeforeAnyWord() {
        List<BarItem> items = withLead("Motion Lab", button("replay"),
                button("save"));
        List<WindowBar.Placed> roomy = WindowBar.layOut(items, 0, 400, SIX);
        // The least room everything fits whole in: the last button's edge.
        int wide = of(roomy, "save").right;
        List<WindowBar.Placed> tight = WindowBar.layOut(items, 0, wide - 7,
                SIX);
        WindowBar.Placed tab = of(tight, BarItem.TAB_ID);
        assertEquals(of(roomy, BarItem.TAB_ID).right - 7, tab.right);
        assertFalse("the buttons keep their words",
                of(tight, "replay").compact);
    }

    @Test
    public void aGreyedItemSaysWhyInItsTip() {
        BarItem track = button("track").tip("Track (Space)");
        assertEquals("Track (Space)", track.tipText());
        assertTrue(track.isAvailable());
        track.unavailable("Pick a quest first.");
        assertFalse(track.isAvailable());
        assertEquals("Pick a quest first.", track.tipText());
    }
}
