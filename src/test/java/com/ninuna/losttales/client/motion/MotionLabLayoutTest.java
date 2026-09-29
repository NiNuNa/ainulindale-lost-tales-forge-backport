package com.ninuna.losttales.client.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

/**
 * The Motion Lab keeps its list and the picked motion side by side where
 * the page is wide enough and lets them take turns where it is not; the
 * motion's column stacks its name, what it is for and the sample over
 * the rows, and gives what it is for no more lines than
 * leave the rows room; each row's stepper stands at its right, a chevron
 * either side of the value.
 */
public final class MotionLabLayoutTest {
    private static final int MARGIN = MotionLabLayout.MARGIN;
    private static final double EXACT = 1.0E-9D;

    @Test
    public void aWidePageShowsTheListBesideTheMotion() {
        MotionLabLayout layout = new MotionLabLayout(480, 300, true, 2);
        assertTrue(layout.isSplit());
        LostTalesUiHitBox list = layout.list();
        LostTalesUiHitBox content = layout.content();
        assertEquals(MARGIN, list.left, EXACT);
        assertEquals(160, list.width, EXACT);
        assertEquals(list.right() + MotionLabLayout.GUTTER,
                layout.divider().left, EXACT);
        assertEquals(list.right() + 2 * MotionLabLayout.GUTTER + 1,
                content.left, EXACT);
        assertEquals(480 - MARGIN, content.right(), EXACT);
        assertEquals(300 - 2 * MARGIN, content.height, EXACT);
    }

    @Test
    public void theListFoldedGivesTheMotionTheWholePage() {
        MotionLabLayout layout = new MotionLabLayout(480, 300, false, 2);
        assertFalse(layout.isSplit());
        assertEquals(0, layout.list().width, EXACT);
        assertEquals(0, layout.divider().width, EXACT);
        assertEquals(480 - 2 * MARGIN, layout.content().width, EXACT);
    }

    @Test
    public void aNarrowPageShowsOneOfThemAtATime() {
        int narrow = MotionLabLayout.MIN_SPLIT_WIDTH - 1;
        MotionLabLayout out = new MotionLabLayout(narrow, 300, true, 2);
        assertFalse(out.isWide());
        assertEquals(narrow - 2 * MARGIN, out.list().width, EXACT);
        assertEquals("the motion waits while the list is out", 0,
                out.content().width, EXACT);
        assertEquals(0, out.rows().width, EXACT);
        assertEquals(0, out.sample().height, EXACT);
        MotionLabLayout folded = new MotionLabLayout(narrow, 300, false, 2);
        assertEquals(narrow - 2 * MARGIN, folded.content().width, EXACT);
    }

    @Test
    public void theColumnStacksNameAboutAndSampleOverTheRows() {
        MotionLabLayout layout = new MotionLabLayout(480, 166, true, 2);
        LostTalesUiHitBox content = layout.content();
        assertEquals(content.top, layout.name().top, EXACT);
        assertEquals(layout.name().bottom(), layout.about().top, EXACT);
        assertEquals(2 * MotionLabLayout.ABOUT_LINE, layout.about().height,
                EXACT);
        assertEquals(layout.about().bottom() + MotionLabLayout.GAP,
                layout.sample().top, EXACT);
        assertEquals("a third of the column", (int)content.height / 3,
                layout.sample().height, EXACT);
        assertEquals(layout.sample().bottom() + MotionLabLayout.GAP,
                layout.rows().top, EXACT);
        assertEquals(content.bottom(), layout.rows().bottom(), EXACT);
    }

    @Test
    public void theSampleKeepsWithinItsBounds() {
        assertEquals(MotionLabLayout.SAMPLE_MAX_HEIGHT,
                new MotionLabLayout(480, 900, false, 0).sample().height,
                EXACT);
        assertEquals(MotionLabLayout.SAMPLE_MIN_HEIGHT,
                new MotionLabLayout(480, 60, false, 0).sample().height,
                EXACT);
    }

    @Test
    public void whatAMotionIsForGetsNoMoreLinesThanLeaveTheRowsRoom() {
        MotionLabLayout tall = new MotionLabLayout(480, 600, false, 20);
        assertEquals("never more than the most", MotionLabLayout.MAX_ABOUT_LINES,
                tall.aboutLines());
        MotionLabLayout few = new MotionLabLayout(480, 600, false, 2);
        assertEquals(2, few.aboutLines());
        MotionLabLayout low = new MotionLabLayout(480, 200, false, 20);
        assertTrue("room for the rows first", low.rows().height
                >= MotionLabLayout.MIN_ROWS * MotionLabLayout.ROW_HEIGHT);
        assertTrue(low.aboutLines() < MotionLabLayout.MAX_ABOUT_LINES);
        MotionLabLayout cramped = new MotionLabLayout(480, 90, false, 20);
        assertEquals(0, cramped.aboutLines());
    }

    @Test
    public void aStepperStandsAtTheRowsRightWithAChevronEitherSide() {
        MotionLabLayout layout = new MotionLabLayout(480, 300, false, 0);
        LostTalesUiHitBox rows = layout.rows();
        double top = rows.top + 3 * MotionLabLayout.ROW_HEIGHT;
        LostTalesUiHitBox less = layout.less(top);
        LostTalesUiHitBox value = layout.value(top);
        LostTalesUiHitBox more = layout.more(top);
        assertEquals(rows.right(), more.right(), EXACT);
        assertEquals(rows.right() - layout.stepperWidth(), less.left, EXACT);
        assertEquals(less.right(), value.left, EXACT);
        assertEquals(value.right(), more.left, EXACT);
        assertEquals(MotionLabLayout.CHEVRON_BOX, less.width, EXACT);
        assertEquals(MotionLabLayout.CHEVRON_BOX, more.width, EXACT);
        assertEquals(top, value.top, EXACT);
        assertEquals(MotionLabLayout.ROW_HEIGHT, value.height, EXACT);
        assertTrue("the label keeps clear of the stepper",
                rows.left + layout.labelWidth() < less.left);
    }

    @Test
    public void theStepperWidthFollowsTheRowsWithinItsBounds() {
        assertEquals(MotionLabLayout.STEPPER_MAX_WIDTH,
                new MotionLabLayout(900, 300, false, 0).stepperWidth());
        assertEquals(MotionLabLayout.STEPPER_MIN_WIDTH,
                new MotionLabLayout(160, 300, false, 0).stepperWidth());
    }
}
