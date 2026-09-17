package com.ninuna.losttales.gui.screen.quest;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Where a conversation's parts stand. The rule that matters most is the
 * one the screenshot shows: whichever reply is being chosen is always on
 * the middle row, and the column moves past it.
 */
public final class QuestDialogueLayoutTest {

    private static final int WIDE = 480;
    private static final int TALL = 270;

    private static QuestDialogueLayout layout(int replies) {
        return new QuestDialogueLayout(WIDE, TALL, replies);
    }

    @Test
    public void theChosenReplyIsAlwaysOnTheMiddleRow() {
        QuestDialogueLayout layout = layout(3);
        int middle = layout.centreY() - QuestDialogueLayout.ROW_HEIGHT / 2;

        for (int chosen = 0; chosen < 3; chosen++) {
            assertEquals("reply " + chosen + " chosen sits in the middle",
                    middle, (int)layout.replyAt(chosen, chosen).top);
        }
    }

    @Test
    public void theOthersStandAboveAndBelowInOrder() {
        QuestDialogueLayout layout = layout(3);
        LostTalesUiHitBox first = layout.replyAt(0, 1);
        LostTalesUiHitBox middle = layout.replyAt(1, 1);
        LostTalesUiHitBox last = layout.replyAt(2, 1);

        assertEquals(middle.top - QuestDialogueLayout.ROW_HEIGHT, first.top,
                0.0D);
        assertEquals(middle.top + QuestDialogueLayout.ROW_HEIGHT, last.top,
                0.0D);
        assertEquals("every reply shares one left edge", first.left,
                last.left, 0.0D);
    }

    @Test
    public void theColumnGlidesRatherThanStepping() {
        QuestDialogueLayout layout = layout(3);
        double resting = layout.replyAt(0, 0.0D).top;
        double halfway = layout.replyAt(0, 0.5D).top;
        double arrived = layout.replyAt(0, 1.0D).top;

        assertTrue("part way through it is between the two",
                halfway < resting && halfway > arrived);
        assertEquals(resting - QuestDialogueLayout.ROW_HEIGHT / 2.0D, halfway,
                0.001D);
    }

    @Test
    public void thePartsSitEitherSideOfTheRule() {
        QuestDialogueLayout layout = layout(3);
        LostTalesUiHitBox rule = layout.rule();

        assertEquals(layout.ruleX(), (int)rule.left);
        assertEquals(QuestDialogueLayout.RULE_WIDTH, (int)rule.width);
        assertTrue("the replies begin right of the rule",
                layout.replyAt(0, 0).left >= rule.right()
                        + QuestDialogueLayout.RULE_GAP - 1);
        assertTrue("the name ends left of it",
                layout.nameRight() < rule.left);
        assertTrue("and has room to be written in",
                layout.nameWidth() > 0);
        assertEquals("the name is level with the chosen reply",
                layout.centreY() - 4, layout.nameY());
        // The quiet lines stack under the name, one row apart.
        assertEquals(layout.nameY() + QuestDialogueLayout.ROW_HEIGHT,
                layout.noteY(0));
        assertEquals(layout.noteY(0) + QuestDialogueLayout.ROW_HEIGHT,
                layout.noteY(1));
        assertEquals("a line nobody gave leaves no gap", layout.noteY(0),
                layout.noteY(-1));
    }

    @Test
    public void theMarkStandsOnTheRuleBesideTheChosenReply() {
        QuestDialogueLayout layout = layout(3);
        LostTalesUiHitBox mark = layout.marker(6, 8);

        assertTrue("just left of the rule",
                mark.right() <= layout.ruleX());
        assertEquals("centred on the middle row",
                layout.centreY(), (int)Math.round(mark.top + mark.height / 2.0D));
    }

    @Test
    public void aReplyNobodyOffersTakesNoRoom() {
        QuestDialogueLayout layout = layout(2);
        assertEquals(0.0D, layout.replyAt(-1, 0).width, 0.0D);
        assertEquals(0.0D, layout.replyAt(2, 0).width, 0.0D);
        assertEquals(0.0D, layout.replyAt(2, 0).height, 0.0D);
    }

    @Test
    public void theRuleReachesPastTheRepliesAtBothEnds() {
        QuestDialogueLayout layout = layout(3);
        LostTalesUiHitBox rule = layout.rule();
        LostTalesUiHitBox top = layout.replyAt(0, 1);
        LostTalesUiHitBox bottom = layout.replyAt(2, 1);

        assertTrue("clear rule above the first reply", rule.top < top.top);
        assertTrue("and below the last", rule.bottom() > bottom.bottom());
    }
}
