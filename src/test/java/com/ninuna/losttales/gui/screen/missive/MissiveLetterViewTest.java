package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A missive letter reads on the window's own surface (LOOK-22): no edge
 * of its own, so its words stand in its whole box but for the
 * scrollbar's room, its headings are every list's, a row high, and on
 * the board's page it lines up with the notices beside it.
 */
public final class MissiveLetterViewTest {
    /** A menu row's height at the usual GUI scales. */
    private static final int ROW = MenuWindow.ROW_HEIGHT;

    @Test
    public void theLetterHasNoEdgeOfItsOwn() {
        LostTalesUiHitBox box = new LostTalesUiHitBox(40.5D, 12.25D, 200.0D,
                120.0D);
        LostTalesUiHitBox inner = MissiveLetterView.inner(box);
        assertEquals("the words start at the box's own side", 40.0D,
                inner.left, 0.0D);
        assertEquals("and at its own top", 12.0D, inner.top, 0.0D);
        assertEquals("only the scrollbar's room is kept at the right",
                200.0D - WindowLists.SCROLLBAR_ROOM, inner.width, 0.0D);
        assertEquals(120.0D, inner.height, 0.0D);
    }

    @Test
    public void onTheBoardTheLetterLinesUpWithTheNotices() {
        MissiveBoardLayout layout = new MissiveBoardLayout(420, 240, true, ROW);
        LostTalesUiHitBox letter = layout.letter();
        LostTalesUiHitBox inner = MissiveLetterView.inner(letter);
        assertEquals("the words stand as far from the rule as the notices' words",
                layout.divider().right() + MissiveBoardLayout.GUTTER,
                inner.left, 0.0D);
        assertEquals(layout.textRight() + MissiveBoardLayout.GUTTER,
                layout.divider().left, 0.0D);
        assertEquals("the letter's words are cut where the notices' rows are",
                layout.band().top, inner.top, 0.0D);
        assertEquals(layout.band().bottom(), inner.bottom(), 0.0D);
        assertEquals("its fades hang from the page's top, as the notices' do",
                0.0D, MissiveLetterView.fadeEdgeTop(letter), 0.0D);
        assertEquals("and from its foot", 240.0D,
                MissiveLetterView.fadeEdgeBottom(letter), 0.0D);
    }

    @Test
    public void aHeadingIsEveryListsRowHigh() {
        assertEquals(ROW, MissiveLetterView.lineHeight(
                MissiveLetterView.Kind.HEADING, ROW));
        assertEquals("at GUI scale 1 it grows with the menus' rows", ROW + 3,
                MissiveLetterView.lineHeight(MissiveLetterView.Kind.HEADING,
                        ROW + 3));
        assertEquals("a separator takes a menu separator's room",
                WindowLists.SEPARATOR_HEIGHT, MissiveLetterView.lineHeight(
                        MissiveLetterView.Kind.RULE, ROW));
        assertEquals(MissiveLetterView.GAP, MissiveLetterView.lineHeight(
                MissiveLetterView.Kind.GAP, ROW));
        for (MissiveLetterView.Kind kind : new MissiveLetterView.Kind[] {
                MissiveLetterView.Kind.TITLE, MissiveLetterView.Kind.ASIDE,
                MissiveLetterView.Kind.TEXT, MissiveLetterView.Kind.REWARD,
                MissiveLetterView.Kind.TIME}) {
            assertEquals(kind.name(), MissiveLetterView.LINE_HEIGHT,
                    MissiveLetterView.lineHeight(kind, ROW));
        }
    }

    @Test
    public void aBoxTooSmallForALineShowsNothing() {
        assertTrue(MissiveLetterView.fits(new LostTalesUiHitBox(0, 0, 160, 40)));
        assertFalse(MissiveLetterView.fits(new LostTalesUiHitBox(0, 0,
                WindowLists.SCROLLBAR_ROOM + 2, 40)));
        assertFalse(MissiveLetterView.fits(new LostTalesUiHitBox(0, 0, 160,
                MissiveLetterView.LINE_HEIGHT)));
    }
}
