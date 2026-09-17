package com.ninuna.losttales.gui.screen.quest;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where the parts of a quest conversation stand.
 *
 * <p>There is no panel. The world stays where it is and the conversation
 * is laid over it: one upright rule right of centre, who is speaking to
 * its left, and what the player may say to its right — the reply being
 * chosen always on the rule's middle row, the others above and below it.
 * Moving the choice moves the column past that row rather than moving a
 * highlight down the list, which is what makes a conversation read as
 * one thing being said at a time. The speaker's own words are not here
 * at all: they are said into the chat, where every other word is read.</p>
 *
 * <p>Free of Minecraft: the screen draws from these boxes and asks the
 * pointer with the same ones.</p>
 */
public final class QuestDialogueLayout {

    /** How far across the screen the rule stands. */
    private static final double RULE_SHARE = 0.52D;
    /** One reply's row; the chat's line stride, so text sits the same. */
    public static final int ROW_HEIGHT = 12;
    /** Clear pixels between the rule and a reply's first letter. */
    public static final int RULE_GAP = 9;
    /** Clear pixels between the rule and the speaker's name. */
    public static final int NAME_GAP = 12;
    /** The rule's own width. */
    public static final int RULE_WIDTH = 1;
    /** Rows of clear rule above and below the replies. */
    public static final int RULE_MARGIN = ROW_HEIGHT;
    /** Clear pixels round anything at the screen's edge. */
    public static final int MARGIN = 8;

    private final int screenWidth;
    private final int screenHeight;
    private final int replies;

    public QuestDialogueLayout(int screenWidth, int screenHeight,
                               int replies) {
        this.screenWidth = Math.max(0, screenWidth);
        this.screenHeight = Math.max(0, screenHeight);
        this.replies = Math.max(0, replies);
    }

    /** The column the rule stands in. */
    public int ruleX() {
        return (int)Math.round(this.screenWidth * RULE_SHARE);
    }

    /**
     * The row the chosen reply always stands on, and the row the
     * speaker's name is level with.
     */
    public int centreY() {
        return this.screenHeight / 2;
    }

    /**
     * The upright rule: as tall as the replies reach either side of the
     * middle, with a row of clear rule beyond them at both ends.
     */
    public LostTalesUiHitBox rule() {
        int reach = Math.max(1, this.replies) * ROW_HEIGHT / 2 + RULE_MARGIN;
        return new LostTalesUiHitBox(ruleX(), centreY() - reach + ROW_HEIGHT / 2,
                RULE_WIDTH, reach * 2);
    }

    /**
     * One reply's row. {@code chosen} is the reply on the middle row, so
     * the whole column is placed by which one is being chosen; a
     * fractional {@code chosen} is the column part way through gliding
     * from one to the next.
     */
    public LostTalesUiHitBox replyAt(int index, double chosen) {
        if (index < 0 || index >= this.replies) {
            return new LostTalesUiHitBox(0, 0, 0, 0);
        }
        double left = ruleX() + RULE_WIDTH + RULE_GAP;
        return new LostTalesUiHitBox(left,
                centreY() - ROW_HEIGHT / 2.0D + (index - chosen) * ROW_HEIGHT,
                Math.max(0, this.screenWidth - MARGIN - left), ROW_HEIGHT);
    }

    /** The mark on the rule beside whichever reply is being chosen. */
    public LostTalesUiHitBox marker(int markWidth, int markHeight) {
        return new LostTalesUiHitBox(
                ruleX() - RULE_WIDTH - markWidth - 1,
                centreY() - markHeight / 2.0D, markWidth, markHeight);
    }

    /**
     * Where the speaker's name ends: it is written right up to the rule,
     * so the name and the reply being chosen read as one exchange.
     */
    public int nameRight() {
        return ruleX() - NAME_GAP;
    }

    /** The row the speaker's name is written on. */
    public int nameY() {
        return centreY() - 4;
    }

    /**
     * The row the {@code index}-th quiet line under the name is written
     * on: who the speaker is, then the quest, then what it asks.
     */
    public int noteY(int index) {
        return nameY() + (Math.max(0, index) + 1) * ROW_HEIGHT;
    }

    /** How wide anything written left of the rule may be. */
    public int nameWidth() {
        return Math.max(0, nameRight() - MARGIN);
    }

}
