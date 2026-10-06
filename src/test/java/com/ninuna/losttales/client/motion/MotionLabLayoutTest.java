package com.ninuna.losttales.client.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

/**
 * The Motion Lab keeps its list and the picked motion side by side where
 * the page is wide enough and lets them take turns where it is not; each
 * stands in a band as a menu's rows do, a menu's padding clear above and
 * below and inside its edges, every row a menu row high; the motion's
 * column stacks its name, what it is for and the sample over the rows,
 * and gives what it is for no more lines than leave the rows room; each
 * row's stepper ends at its right, a chevron either side of the value.
 */
public final class MotionLabLayoutTest {
    private static final int ROW = MenuWindow.ROW_HEIGHT;
    private static final int PADDING_X = MenuWindow.PADDING_X;
    private static final int PADDING_Y = MenuWindow.PADDING_Y;
    private static final double EXACT = 1.0E-9D;

    private static MotionLabLayout layout(int width, int height,
                                          boolean listOut, int aboutLines) {
        return new MotionLabLayout(width, height, ROW, listOut, aboutLines);
    }

    @Test
    public void aWidePageShowsTheListBesideTheMotion() {
        MotionLabLayout layout = layout(480, 300, true, 2);
        assertTrue(layout.isSplit());
        LostTalesUiHitBox list = layout.list();
        LostTalesUiHitBox content = layout.content();
        assertEquals("the list's band runs to the page's side", 0, list.left,
                EXACT);
        assertEquals(PADDING_Y, list.top, EXACT);
        assertEquals(160, list.width, EXACT);
        assertEquals(300 - 2 * PADDING_Y, list.height, EXACT);
        assertEquals(list.right() + MotionLabLayout.GUTTER,
                layout.divider().left, EXACT);
        assertEquals(list.right() + 2 * MotionLabLayout.GUTTER + 1,
                content.left, EXACT);
        assertEquals("the motion's band runs to the page's side", 480,
                content.right(), EXACT);
        assertEquals(300 - 2 * PADDING_Y, content.height, EXACT);
        assertEquals("the motion's words stand a menu's padding in",
                content.left + PADDING_X, layout.column().left, EXACT);
        assertEquals(content.right() - PADDING_X, layout.column().right(),
                EXACT);
    }

    @Test
    public void theListFoldedGivesTheMotionTheWholePage() {
        MotionLabLayout layout = layout(480, 300, false, 2);
        assertFalse(layout.isSplit());
        assertEquals(0, layout.list().width, EXACT);
        assertEquals(0, layout.divider().width, EXACT);
        assertEquals(0, layout.content().left, EXACT);
        assertEquals(480, layout.content().width, EXACT);
        assertEquals(480 - 2 * PADDING_X, layout.column().width, EXACT);
    }

    @Test
    public void aNarrowPageShowsOneOfThemAtATime() {
        int narrow = MotionLabLayout.MIN_SPLIT_WIDTH - 1;
        MotionLabLayout out = layout(narrow, 300, true, 2);
        assertFalse(out.isWide());
        assertEquals(narrow, out.list().width, EXACT);
        assertEquals("the motion waits while the list is out", 0,
                out.content().width, EXACT);
        assertEquals(0, out.rows().width, EXACT);
        assertEquals(0, out.sample().height, EXACT);
        MotionLabLayout folded = layout(narrow, 300, false, 2);
        assertEquals(narrow, folded.content().width, EXACT);
    }

    @Test
    public void theColumnStacksNameAboutAndSampleOverTheRows() {
        MotionLabLayout layout = layout(480, 166, true, 2);
        LostTalesUiHitBox content = layout.content();
        LostTalesUiHitBox column = layout.column();
        assertEquals(column.top, layout.name().top, EXACT);
        assertEquals("the name is one row", ROW, layout.name().height, EXACT);
        assertEquals(layout.name().bottom(), layout.about().top, EXACT);
        assertEquals(2 * MotionLabLayout.ABOUT_LINE, layout.about().height,
                EXACT);
        assertEquals(layout.about().bottom() + MotionLabLayout.GAP,
                layout.sample().top, EXACT);
        assertEquals("a third of the column", (int)content.height / 3,
                layout.sample().height, EXACT);
        assertEquals("a menu's padding above the rows' band",
                layout.sample().bottom() + PADDING_Y, layout.rows().top,
                EXACT);
        assertEquals(content.left, layout.rows().left, EXACT);
        assertEquals(content.right(), layout.rows().right(), EXACT);
        assertEquals(content.bottom(), layout.rows().bottom(), EXACT);
    }

    @Test
    public void theSampleKeepsWithinItsBounds() {
        assertEquals(MotionLabLayout.SAMPLE_MAX_HEIGHT,
                layout(480, 900, false, 0).sample().height, EXACT);
        assertEquals(MotionLabLayout.SAMPLE_MIN_HEIGHT,
                layout(480, 60, false, 0).sample().height, EXACT);
    }

    @Test
    public void whatAMotionIsForGetsNoMoreLinesThanLeaveTheRowsRoom() {
        MotionLabLayout tall = layout(480, 600, false, 20);
        assertEquals("never more than the most", MotionLabLayout.MAX_ABOUT_LINES,
                tall.aboutLines());
        MotionLabLayout few = layout(480, 600, false, 2);
        assertEquals(2, few.aboutLines());
        MotionLabLayout low = layout(480, 180, false, 20);
        assertTrue("room for the rows first", low.rows().height
                >= MotionLabLayout.MIN_ROWS * ROW);
        assertTrue(low.aboutLines() < MotionLabLayout.MAX_ABOUT_LINES);
        MotionLabLayout cramped = layout(480, 90, false, 20);
        assertEquals(0, cramped.aboutLines());
    }

    @Test
    public void aStepperEndsAtTheRowsRightWithAChevronEitherSide() {
        MotionLabLayout layout = layout(480, 300, false, 0);
        LostTalesUiHitBox rows = layout.rows();
        double top = rows.top + 3 * ROW;
        LostTalesUiHitBox less = layout.less(top);
        LostTalesUiHitBox value = layout.value(top);
        LostTalesUiHitBox more = layout.more(top);
        assertEquals(rows.left + PADDING_X, layout.rowLeft(), EXACT);
        assertEquals(rows.right() - PADDING_X, more.right(), EXACT);
        assertEquals(rows.right() - PADDING_X - layout.stepperWidth(),
                less.left, EXACT);
        assertEquals(less.right(), value.left, EXACT);
        assertEquals(value.right(), more.left, EXACT);
        assertEquals(MenuWindow.STEPPER_CELL, less.width, EXACT);
        assertEquals(MenuWindow.STEPPER_CELL, more.width, EXACT);
        assertEquals(top, value.top, EXACT);
        assertEquals(ROW, value.height, EXACT);
        assertTrue("the label keeps clear of the stepper",
                layout.rowLeft() + layout.labelWidth()
                        + MenuWindow.VALUE_GAP <= less.left);
    }

    @Test
    public void everyRowIsTheMenusRowHigh() {
        MotionLabLayout layout = new MotionLabLayout(480, 300, 14, false, 0);
        assertEquals(14, layout.rowHeight());
        assertEquals(14, layout.name().height, EXACT);
        assertEquals(14, layout.less(0).height, EXACT);
        assertEquals(14, layout.value(0).height, EXACT);
        assertEquals(14, layout.more(0).height, EXACT);
    }

    @Test
    public void theStepperWidthFollowsTheRowsWithinItsBounds() {
        assertEquals(MotionLabLayout.STEPPER_MAX_WIDTH,
                layout(900, 300, false, 0).stepperWidth());
        assertEquals(MotionLabLayout.STEPPER_MIN_WIDTH,
                layout(160, 300, false, 0).stepperWidth());
    }
}
