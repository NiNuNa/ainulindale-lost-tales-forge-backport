package com.ninuna.losttales.gui.screen.quest;

import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Where the journal's parts stand. The screen draws from these boxes and
 * asks the pointer with the same ones, so anything asserted here is what
 * a press actually lands on.
 */
public final class QuestJournalLayoutTest {

    private static final int WIDE = 480;
    private static final int TALL = 260;

    @Test
    public void thePartsStackWithoutOverlappingOrLeavingAGap() {
        QuestJournalLayout layout = new QuestJournalLayout(WIDE, TALL);

        assertEquals(0.0D, layout.header().top, 0.0D);
        assertEquals(QuestJournalLayout.HEADER_HEIGHT, layout.header().bottom(),
                0.0D);
        assertEquals("the rule sits directly under the header",
                layout.header().bottom(), layout.headerRule().top, 0.0D);
        assertEquals("the body starts under the rule and its margin",
                layout.headerRule().bottom() + QuestJournalLayout.MARGIN,
                layout.bodyTop(), 0.0D);
        assertEquals("the action strip ends at the bottom", TALL,
                (int)layout.actions().bottom());
        assertEquals("its rule sits directly over it",
                layout.actions().top, layout.actionRule().bottom(), 0.0D);
        assertEquals(layout.bodyBottom() + QuestJournalLayout.MARGIN,
                (int)layout.actionRule().top);
    }

    @Test
    public void theTwoHalvesAreDividedByOneRuleWithAGutterEitherSide() {
        QuestJournalLayout layout = new QuestJournalLayout(WIDE, TALL);
        LostTalesUiHitBox list = layout.list();
        LostTalesUiHitBox divider = layout.divider();
        LostTalesUiHitBox detail = layout.detail();

        assertTrue(layout.isSplit());
        assertEquals(QuestJournalLayout.MARGIN, (int)list.left);
        assertEquals("one rule, one pixel wide", 1.0D, divider.width, 0.0D);
        assertEquals(list.right() + QuestJournalLayout.GUTTER, divider.left,
                0.0D);
        assertEquals(divider.right() + QuestJournalLayout.GUTTER, detail.left,
                0.0D);
        assertEquals("the detail reaches the far margin",
                WIDE - QuestJournalLayout.MARGIN, (int)detail.right());
        assertEquals("both halves share the body's rows", list.top,
                detail.top, 0.0D);
        assertEquals(list.height, detail.height, 0.0D);
        // The rows leave room for the scrollbar without moving the box.
        assertEquals(list.width - QuestJournalLayout.SCROLLBAR_WIDTH - 2,
                layout.listRows().width, 0.0D);
    }

    @Test
    public void aNarrowScreenKeepsOnlyTheDetail() {
        QuestJournalLayout layout = new QuestJournalLayout(
                QuestJournalLayout.MIN_SPLIT_WIDTH - 1, TALL);

        assertFalse(layout.isSplit());
        assertEquals(0.0D, layout.list().width, 0.0D);
        assertEquals(0.0D, layout.divider().width, 0.0D);
        assertEquals("the detail takes the whole width",
                QuestJournalLayout.MARGIN, (int)layout.detail().left);
        assertTrue(layout.detail().width > 0.0D);
    }

    @Test
    public void theListNeverGrowsPastItsBoundsHoweverWideTheScreen() {
        assertEquals(QuestJournalLayout.LIST_MAX_WIDTH,
                (int)new QuestJournalLayout(1920, TALL).list().width);
        assertEquals(QuestJournalLayout.LIST_MIN_WIDTH,
                (int)new QuestJournalLayout(
                        QuestJournalLayout.MIN_SPLIT_WIDTH, TALL).list().width);
    }

    @Test
    public void aScrollbarAppearsOnlyWhereThereIsSomethingToScroll() {
        LostTalesUiHitBox area = new LostTalesUiHitBox(10, 20, 100, 60);
        assertEquals(0.0D,
                QuestJournalLayout.scrollbar(area, 60).width, 0.0D);
        LostTalesUiHitBox bar = QuestJournalLayout.scrollbar(area, 240);
        assertEquals(QuestJournalLayout.SCROLLBAR_WIDTH, (int)bar.width);
        assertEquals("inside the area's right edge", area.right(),
                bar.right(), 0.0D);

        LostTalesUiHitBox top = QuestJournalLayout.scrollHandle(bar, 240, 0.0D);
        assertEquals("at the top with nothing scrolled", bar.top, top.top,
                0.0D);
        LostTalesUiHitBox bottom =
                QuestJournalLayout.scrollHandle(bar, 240, 180.0D);
        assertEquals("at the bottom once scrolled all the way",
                bar.bottom(), bottom.bottom(), 0.001D);
        assertTrue("and never smaller than a square",
                bottom.height >= QuestJournalLayout.SCROLLBAR_WIDTH * 2);
    }

    @Test
    public void aRowOfButtonsIsLaidOutLeftToRightAndCentredInItsStrip() {
        LostTalesUiHitBox strip = new LostTalesUiHitBox(0, 200, 480, 26);
        int[] widths = {40, 60, 30};

        LostTalesUiHitBox first = QuestJournalLayout.buttonAt(strip, 8, widths, 0);
        LostTalesUiHitBox second = QuestJournalLayout.buttonAt(strip, 8, widths, 1);
        LostTalesUiHitBox third = QuestJournalLayout.buttonAt(strip, 8, widths, 2);

        assertEquals(8.0D, first.left, 0.0D);
        assertEquals(first.right() + QuestJournalLayout.CONTROL_GAP,
                second.left, 0.0D);
        assertEquals(second.right() + QuestJournalLayout.CONTROL_GAP,
                third.left, 0.0D);
        assertEquals(LostTalesUiFramedButton.HEIGHT, (int)first.height);
        assertEquals("every button stands on the same row", first.top,
                third.top, 0.0D);
        assertEquals("and the row is centred in the strip",
                Math.floor(strip.top
                        + (strip.height - LostTalesUiFramedButton.HEIGHT) / 2.0D),
                first.top, 0.0D);
        assertEquals(8 + QuestJournalLayout.buttonsWidth(widths) - 8,
                (int)(third.right() - first.left));
    }

    @Test
    public void aButtonIsAtLeastItsFramesSizeHoweverShortItsLabel() {
        assertEquals(LostTalesUiFramedButton.MIN_SIZE,
                QuestJournalLayout.buttonWidthFor(0));
        assertEquals(30 + 2 * LostTalesUiFramedButton.WIDE_INSET,
                QuestJournalLayout.buttonWidthFor(30));
        // A width under the frame's minimum is grown, not drawn clipped.
        int[] widths = {1};
        assertEquals(LostTalesUiFramedButton.MIN_SIZE,
                QuestJournalLayout.buttonsWidth(widths));
    }
}
