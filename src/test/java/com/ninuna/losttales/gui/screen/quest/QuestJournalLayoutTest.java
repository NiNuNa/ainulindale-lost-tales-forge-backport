package com.ninuna.losttales.gui.screen.quest;

import com.ninuna.losttales.client.window.MenuWindow;
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
                QuestJournalLayout.MARGIN_Y, layout.bodyTop());
        assertEquals("and ends a margin over the window's bar",
                TALL - QuestJournalLayout.MARGIN_Y, layout.bodyBottom());
    }

    @Test
    public void theTwoHalvesAreDividedByOneRuleWithAGutterEitherSide() {
        QuestJournalLayout layout = new QuestJournalLayout(WIDE, TALL, true);
        LostTalesUiHitBox list = layout.list();
        LostTalesUiHitBox divider = layout.divider();
        LostTalesUiHitBox detail = layout.detail();

        assertTrue(layout.isSplit());
        assertEquals("the list stands at the page's edge, so a lit row"
                + " reaches the frame", 0, (int)list.left);
        assertEquals("one rule, one pixel wide", 1.0D, divider.width, 0.0D);
        assertEquals(list.right() + QuestJournalLayout.GUTTER, divider.left,
                0.0D);
        assertEquals(divider.right() + QuestJournalLayout.GUTTER, detail.left,
                0.0D);
        assertEquals("the detail reaches the far margin",
                WIDE - QuestJournalLayout.MARGIN_X, (int)detail.right());
        assertEquals("the rule runs the detail's height", detail.top,
                divider.top, 0.0D);
        assertEquals(detail.height, divider.height, 0.0D);
    }

    @Test
    public void theListsRowsStandAsAMenusDo() {
        QuestJournalLayout layout = new QuestJournalLayout(WIDE, TALL, true);
        LostTalesUiHitBox list = layout.list();
        LostTalesUiHitBox rows = layout.listRows();

        assertEquals("a menu's padding over the rows", MenuWindow.PADDING_Y,
                (int)list.top);
        assertEquals("and under them", TALL - MenuWindow.PADDING_Y,
                (int)list.bottom());
        assertEquals("what a row holds stands a menu's padding in",
                list.left + MenuWindow.PADDING_X, rows.left, 0.0D);
        assertEquals(list.right() - MenuWindow.PADDING_X, rows.right(), 0.0D);
        assertEquals("on the list's own rows", list.top, rows.top, 0.0D);
        assertEquals(list.height, rows.height, 0.0D);
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
        assertEquals(QuestJournalLayout.MARGIN_X, (int)folded.detail().left);
        assertEquals(WIDE - QuestJournalLayout.MARGIN_X,
                (int)folded.detail().right());
    }

    @Test
    public void aNarrowPageShowsTheListOrTheDetailOverTheWholePage() {
        int narrow = QuestJournalLayout.MIN_SPLIT_WIDTH - 1;
        QuestJournalLayout folded = new QuestJournalLayout(narrow, TALL, false);
        QuestJournalLayout out = new QuestJournalLayout(narrow, TALL, true);

        assertFalse(out.isWide());
        assertFalse("too narrow to stand side by side", out.isSplit());
        assertEquals(0.0D, out.divider().width, 0.0D);
        assertEquals("folded, the detail takes the whole body",
                QuestJournalLayout.MARGIN_X, (int)folded.detail().left);
        assertEquals(narrow - QuestJournalLayout.MARGIN_X,
                (int)folded.detail().right());

        assertEquals("out, the detail is gone", 0.0D, out.detail().width,
                0.0D);
        assertEquals("and the list takes the page's whole width", 0.0D,
                out.list().left, 0.0D);
        assertEquals(narrow, (int)out.list().width);
        assertEquals("between a menu's padding at the top and the foot",
                MenuWindow.PADDING_Y, (int)out.list().top);
        assertEquals(TALL - MenuWindow.PADDING_Y, (int)out.list().bottom());
    }

    @Test
    public void theListNeverGrowsPastItsBoundsHoweverWideThePage() {
        assertEquals(QuestJournalLayout.LIST_MAX_WIDTH,
                (int)new QuestJournalLayout(1920, TALL, true).list().width);
        assertEquals(QuestJournalLayout.LIST_MIN_WIDTH,
                (int)new QuestJournalLayout(QuestJournalLayout.MIN_SPLIT_WIDTH,
                        TALL, true).list().width);
    }
}
