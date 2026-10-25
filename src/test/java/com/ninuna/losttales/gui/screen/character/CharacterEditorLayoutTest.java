package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Character Editor keeps its sections and the section picked side by
 * side where the page is wide enough, and lets them take turns where it is
 * not, as the journal does.
 */
public final class CharacterEditorLayoutTest {
    private static final int MARGIN_X = CharacterEditorLayout.MARGIN_X;
    private static final int MARGIN_Y = CharacterEditorLayout.MARGIN_Y;

    @Test
    public void aWidePageShowsTheSectionsBesideTheSectionPicked() {
        CharacterEditorLayout layout = new CharacterEditorLayout(320, 400, true);
        assertTrue(layout.isSplit());
        LostTalesUiHitBox list = layout.list();
        LostTalesUiHitBox section = layout.section();
        assertEquals(0, list.left, 1.0E-9D);
        assertEquals(80, list.width, 1.0E-9D);
        assertEquals(list.right() + 2 * CharacterEditorLayout.GUTTER + 1,
                section.left, 1.0E-9D);
        assertEquals(320 - MARGIN_X, section.right(), 1.0E-9D);
        assertEquals(400 - 2 * MARGIN_Y, section.height, 1.0E-9D);
    }

    @Test
    public void theListStaysWithinItsBounds() {
        assertEquals(CharacterEditorLayout.LIST_MIN_WIDTH,
                new CharacterEditorLayout(240, 300, true).list().width, 1.0E-9D);
        assertEquals(CharacterEditorLayout.LIST_MAX_WIDTH,
                new CharacterEditorLayout(900, 300, true).list().width, 1.0E-9D);
    }

    @Test
    public void theListFoldedGivesTheSectionTheWholePage() {
        CharacterEditorLayout layout = new CharacterEditorLayout(320, 400, false);
        assertFalse(layout.isSplit());
        assertEquals(0, layout.list().width, 1.0E-9D);
        assertEquals(0, layout.divider().width, 1.0E-9D);
        assertEquals(320 - 2 * MARGIN_X, layout.section().width, 1.0E-9D);
    }

    @Test
    public void aNarrowPageShowsOneOfThemAtATime() {
        int narrow = CharacterEditorLayout.MIN_SPLIT_WIDTH - 1;
        CharacterEditorLayout out = new CharacterEditorLayout(narrow, 300, true);
        assertFalse(out.isSplit());
        assertEquals(narrow, out.list().width, 1.0E-9D);
        assertEquals("the section waits while the list is out", 0,
                out.section().width, 1.0E-9D);
        CharacterEditorLayout folded = new CharacterEditorLayout(narrow, 300, false);
        assertEquals(narrow - 2 * MARGIN_X, folded.section().width, 1.0E-9D);
    }
}
