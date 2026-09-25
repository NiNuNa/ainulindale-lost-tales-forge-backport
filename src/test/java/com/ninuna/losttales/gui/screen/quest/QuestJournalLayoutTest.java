package com.ninuna.losttales.gui.screen.quest;

import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Where the journal's parts stand. The page draws from these boxes and
 * asks the pointer with the same ones, so anything asserted here is what
 * a press actually lands on.
 */
public final class QuestJournalLayoutTest {

    private static final int WIDE = 480;
    private static final int TALL = 260;

    @Test
    public void thePartsStackWithoutOverlappingOrLeavingAGap() {
        QuestJournalLayout layout = new QuestJournalLayout(WIDE, TALL, true);

        assertEquals("the body starts a margin under the window's strip",
                QuestJournalLayout.MARGIN, layout.bodyTop());
        assertEquals("the action strip ends at the bottom", TALL,
                (int)layout.actions().bottom());
        assertEquals("its rule sits directly over it",
                layout.actions().top, layout.actionRule().bottom(), 0.0D);
        assertEquals(layout.bodyBottom() + QuestJournalLayout.MARGIN,
                (int)layout.actionRule().top);
    }

    @Test
    public void theTwoHalvesAreDividedByOneRuleWithAGutterEitherSide() {
        QuestJournalLayout layout = new QuestJournalLayout(WIDE, TALL, true);
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
    public void aFoldedListLeavesTheDetailsTheWholeBody() {
        QuestJournalLayout folded = new QuestJournalLayout(WIDE, TALL, false);

        assertTrue(folded.isWide());
        assertFalse(folded.isSplit());
        assertEquals("folded, the list is gone", 0.0D, folded.list().width,
                0.0D);
        assertEquals("and so is the rule", 0.0D, folded.divider().width,
                0.0D);
        assertEquals(QuestJournalLayout.MARGIN, (int)folded.detail().left);
        assertEquals(WIDE - QuestJournalLayout.MARGIN,
                (int)folded.detail().right());
    }

    @Test
    public void aNarrowPageShowsTheListOrTheDetailOverTheWholeBody() {
        int narrow = QuestJournalLayout.MIN_SPLIT_WIDTH - 1;
        QuestJournalLayout folded = new QuestJournalLayout(narrow, TALL, false);
        QuestJournalLayout out = new QuestJournalLayout(narrow, TALL, true);

        assertFalse(out.isWide());
        assertFalse("too narrow to stand side by side", out.isSplit());
        assertEquals(0.0D, out.divider().width, 0.0D);
        assertEquals("folded, the detail takes the whole body",
                QuestJournalLayout.MARGIN, (int)folded.detail().left);
        assertEquals(narrow - QuestJournalLayout.MARGIN,
                (int)folded.detail().right());

        assertEquals("out, the detail is gone", 0.0D, out.detail().width,
                0.0D);
        assertEquals("and the list takes the very same box",
                folded.detail().left, out.list().left, 0.0D);
        assertEquals(folded.detail().width, out.list().width, 0.0D);
        assertEquals(folded.detail().top, out.list().top, 0.0D);
        assertEquals(folded.detail().height, out.list().height, 0.0D);
    }

    @Test
    public void theListNeverGrowsPastItsBoundsHoweverWideThePage() {
        assertEquals(QuestJournalLayout.LIST_MAX_WIDTH,
                (int)new QuestJournalLayout(1920, TALL, true).list().width);
        assertEquals(QuestJournalLayout.LIST_MIN_WIDTH,
                (int)new QuestJournalLayout(QuestJournalLayout.MIN_SPLIT_WIDTH,
                        TALL, true).list().width);
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
    }

    @Test
    public void aButtonIsAtLeastItsFramesSizeHoweverShortItsLabel() {
        assertEquals(LostTalesUiFramedButton.MIN_SIZE,
                QuestJournalLayout.buttonWidthFor(0));
        assertEquals(30 + 2 * LostTalesUiFramedButton.WIDE_INSET,
                QuestJournalLayout.buttonWidthFor(30));
        // A width under the frame's minimum is grown, not drawn clipped.
        LostTalesUiHitBox strip = new LostTalesUiHitBox(0, 0, 100, 26);
        assertEquals(LostTalesUiFramedButton.MIN_SIZE,
                (int)QuestJournalLayout.buttonAt(strip, 0, new int[] {1}, 0)
                        .width);
    }
}
