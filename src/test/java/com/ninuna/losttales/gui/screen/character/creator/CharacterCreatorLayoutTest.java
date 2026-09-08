package com.ninuna.losttales.gui.screen.character.creator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The split holds at every size 1.7.10 can scale a window to: the column
 * never narrower than its rows, the stage never squeezed to nothing on a
 * screen wide enough for it, and nothing drawn over the control bar.
 */
public class CharacterCreatorLayoutTest {

    /** The scaled sizes 1.7.10 gives common windows at GUI Scale Auto. */
    private static final int[][] COMMON_SIZES = {
            {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540},
            {1280, 720}, {1920, 1080}, {320, 240}, {2560, 1440}};

    @Test
    public void theColumnIsAThirdWhereAThirdIsEnough() {
        CharacterCreatorLayout layout = new CharacterCreatorLayout(640, 360);
        assertEquals(640 / 3, layout.getPanelWidth());
    }

    @Test
    public void theColumnNeverShrinksBelowItsRows() {
        CharacterCreatorLayout layout = new CharacterCreatorLayout(427, 240);
        assertEquals(CharacterCreatorLayout.PANEL_MIN_WIDTH, layout.getPanelWidth());
        assertTrue(layout.getContentWidth() > 100);
    }

    @Test
    public void aLargeWindowKeepsMostOfItselfForTheStage() {
        CharacterCreatorLayout layout = new CharacterCreatorLayout(1920, 1080);
        assertEquals(CharacterCreatorLayout.PANEL_MAX_WIDTH, layout.getPanelWidth());
        assertTrue(layout.getStageWidth() > layout.getPanelWidth() * 2);
    }

    @Test
    public void everythingStaysAboveTheControlBarAndBelowTheHeader() {
        for (int[] size : COMMON_SIZES) {
            CharacterCreatorLayout layout = new CharacterCreatorLayout(size[0], size[1]);
            String at = size[0] + "x" + size[1];
            assertEquals(at, CharacterCreatorLayout.HEADER_HEIGHT, layout.getPanelTop());
            assertTrue(at, layout.getPanelBottom()
                    <= size[1] - CharacterCreatorLayout.FOOTER_HEIGHT);
            assertTrue(at, layout.getStageBottom()
                    <= size[1] - CharacterCreatorLayout.FOOTER_HEIGHT);
            assertTrue(at, layout.getContentBottom() > layout.getContentTop());
            assertTrue(at, layout.getButtonRowTop() >= layout.getContentBottom());
            assertTrue(at, layout.getFigureBaselineY() < layout.getStageBottom());
            assertTrue(at, layout.getFigureBaselineY()
                    - layout.getFigureFitHeight() >= layout.getStageTop() - 1);
        }
    }

    @Test
    public void theStageSitsRightOfTheColumnAndReachesTheMargin() {
        for (int[] size : COMMON_SIZES) {
            CharacterCreatorLayout layout = new CharacterCreatorLayout(size[0], size[1]);
            String at = size[0] + "x" + size[1];
            assertTrue(at, layout.getStageLeft() > layout.getPanelRight());
            assertEquals(at, size[0] - CharacterCreatorLayout.MARGIN,
                    layout.getStageRight());
            assertTrue(at, layout.hasStage());
        }
    }

    @Test
    public void hitTestsAgreeWithTheRectangles() {
        CharacterCreatorLayout layout = new CharacterCreatorLayout(854, 480);
        assertTrue(layout.isInPanel(layout.getPanelLeft(), layout.getPanelTop()));
        assertFalse(layout.isInPanel(layout.getPanelRight(), layout.getPanelTop()));
        assertTrue(layout.isInTabStrip(layout.getPanelLeft() + 5,
                layout.getTabStripTop() + 2));
        assertFalse(layout.isInContent(layout.getPanelLeft() + 5,
                layout.getTabStripTop() + 2));
        assertTrue(layout.isInContent(layout.getContentLeft(),
                layout.getContentTop()));
        assertTrue(layout.isOnStage(layout.getStageLeft(), layout.getStageTop()));
        assertFalse(layout.isOnStage(layout.getStageLeft() - 1, layout.getStageTop()));
        assertFalse(layout.isOnStage(layout.getStageLeft(), layout.getStageBottom()));
    }

    @Test
    public void aWindowTooNarrowForBothStillGivesTheColumnItsRows() {
        CharacterCreatorLayout layout = new CharacterCreatorLayout(200, 240);
        assertEquals(CharacterCreatorLayout.PANEL_MIN_WIDTH, layout.getPanelWidth());
        assertFalse(layout.hasStage());
    }
}
