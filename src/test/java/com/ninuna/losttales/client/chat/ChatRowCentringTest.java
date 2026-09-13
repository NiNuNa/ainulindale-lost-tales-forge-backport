package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Everything drawn in a message row is centred on the row's text by one
 * rule. The text's seven rows of capitals stand two rows below the row's
 * top, two clear rows above them and three below, and a box h rows tall
 * is centred on those capitals — exactly when h is odd, and half a pixel
 * above their middle when it is even — without ever reaching past the
 * row. The 10px content box starts two rows above the text, an 8px head
 * one, the 7px speech bubble on it, and the 12px reaction chip and
 * toolbar button fill the row. A divider's rule and its date share the
 * capitals' middle row, and the hover rule stands on the descenders'
 * shadow row.
 */
public final class ChatRowCentringTest {

    private static final int LINE = LostTalesChatOverlayRenderer.LINE_HEIGHT;
    private static final int TEXT_TOP =
            LostTalesChatOverlayRenderer.ROW_TEXT_TOP;
    private static final int TEXT_OFFSET =
            LostTalesChatOverlayRenderer.TEXT_OFFSET;
    private static final int CAPS =
            LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT;
    private static final int BOX =
            LostTalesChatOverlayRenderer.CONTENT_BOX_HEIGHT;

    @Test
    public void aRowIsTwelvePixelsAndTheGapTwoThirdsOfOne() {
        assertEquals(12, LINE);
        assertEquals(2, TEXT_TOP);
        assertEquals(10, TEXT_OFFSET);
        assertEquals(LINE, TEXT_TOP + TEXT_OFFSET);
        // Two thirds of twelve is a whole pixel already.
        assertEquals(8, ChatStackRows.SPACER_HEIGHT);
        assertEquals(Math.round(LINE * 2.0D / 3.0D),
                ChatStackRows.SPACER_HEIGHT);
    }

    @Test
    public void theCapitalsHaveTwoClearRowsAboveAndThreeBelow() {
        assertEquals(7, CAPS);
        // Seven rows of capitals in twelve cannot be centred: the odd
        // clear row goes below them.
        assertEquals(2, TEXT_TOP);
        assertEquals(3, LINE - TEXT_TOP - CAPS);
        assertEquals(3, TEXT_OFFSET - CAPS);
        // Middles doubled so they stay whole: the capitals' stands half a
        // pixel above the row's own.
        assertEquals(LINE - 1, 2 * TEXT_TOP + CAPS);
        // The descender takes the row under the capitals and its shadow
        // the next; the row's last pixel stays clear of the text.
        int shadowRow = TEXT_TOP + CAPS + 1;
        assertEquals(LINE - 2, shadowRow);
    }

    @Test
    public void aBoxIsCentredOnTheCapitalsOrHalfAPixelAboveThem() {
        int capsMiddle = 2 * TEXT_TOP + CAPS;
        for (int height = 1; height < LINE; height++) {
            int top = TEXT_TOP
                    + LostTalesChatOverlayRenderer.centredBoxTop(height);
            // Middles doubled: equal, or the box's one less, which is
            // half a pixel higher on the screen.
            int boxMiddle = 2 * top + height;
            assertTrue("height " + height, boxMiddle == capsMiddle
                    || boxMiddle == capsMiddle - 1);
            assertEquals("height " + height, height % 2 == 1,
                    boxMiddle == capsMiddle);
            assertTrue(top >= 0);
            assertTrue(top + height <= LINE);
        }
        // A box as tall as the row fills it.
        assertEquals(0, TEXT_TOP + LostTalesChatOverlayRenderer.centredBoxTop(
                LINE));
        // A one-row rule is the capitals' middle row, where a divider's
        // rule runs.
        assertEquals(LostTalesChatOverlayRenderer.DIVIDER_RULE_OFFSET,
                TEXT_TOP + LostTalesChatOverlayRenderer.centredBoxTop(1));
    }

    @Test
    public void theContentBoxStandsTwoRowsAboveTheText() {
        assertEquals(10, BOX);
        int top = TEXT_TOP + LostTalesChatOverlayRenderer.centredBoxTop(BOX);
        assertEquals(0, top);
        assertEquals(2, LINE - top - BOX);
        // Emoji, item and marker glyphs and a mark standing for a head are
        // all drawn in this one box, placed from the text's top edge the
        // same way in a message row, the input field, the pickers and the
        // completion lists.
        assertEquals(LostTalesChatOverlayRenderer.centredBoxTop(BOX),
                ChatInlineIcons.CONTENT_TOP_OFFSET);
        assertEquals(ChatInlineIcons.CONTENT_TOP_OFFSET,
                ChatInlineIcons.boxTop(0.0F, ChatInlineIcons.SLOT_WIDTH),
                0.0D);
        assertEquals(BOX, ChatReactionMarker.ICON);
    }

    @Test
    public void aHeadStandsOneRowAboveTheText() {
        int size = LostTalesChatOverlayRenderer.HEAD_SIZE;
        float offset = LostTalesChatOverlayRenderer.HEAD_TOP_OFFSET;
        assertEquals(8, size);
        // A whole pixel, so the face lands on its own texels.
        assertEquals(Math.floor(offset), offset, 0.0D);
        int top = TEXT_TOP + (int)offset;
        assertEquals(1, top);
        assertEquals(3, LINE - top - size);
        assertEquals(LostTalesChatOverlayRenderer.centredBoxTop(size),
                (int)offset);
    }

    @Test
    public void aReactionChipFillsItsRowAndCentresItsEmoji() {
        int height = ChatReactionMarker.HEIGHT;
        assertEquals(LINE, height);
        int chipTop = LostTalesChatOverlayRenderer.centredBoxTop(height);
        assertEquals(0, TEXT_TOP + chipTop);
        assertEquals(LINE, TEXT_TOP + chipTop + height);
        // Its emoji keeps one pixel of edge above and below it.
        int emojiTop = LostTalesChatVisualStyle.chipEmojiTop(chipTop);
        assertEquals(1, emojiTop - chipTop);
        assertEquals(1, chipTop + height - (emojiTop + BOX));
    }

    @Test
    public void theToolbarFillsItsRow() {
        int size = LostTalesChatOverlayRenderer.TOOLBAR_BUTTON_SIZE;
        assertEquals(LINE, size);
        int rowBottom = 100;
        int top = LostTalesChatOverlayRenderer.toolbarTop(rowBottom, LINE);
        assertEquals(88, top);
        assertEquals(rowBottom - LINE, top);
        assertEquals(rowBottom, top + size);
        // In a row one pixel taller the spare pixel goes below it.
        assertEquals(87, LostTalesChatOverlayRenderer.toolbarTop(100, 13));
    }

    @Test
    public void theSpeechBubbleTakesOneRowWhereverItIsDrawn() {
        int height = ChatIconSheet.SPEECH_BUBBLE.getHeight();
        assertEquals(7, height);
        // A reply's quote and the typing line place it as a box of its
        // own: as tall as the capitals, on them exactly.
        int top = LostTalesChatOverlayRenderer.centredBoxTop(height);
        assertEquals(2, TEXT_TOP + top);
        assertEquals(3, LINE - (TEXT_TOP + top + height));
        // A link to a message stands it in the content box of the slot
        // its two spaces reserve, and lands on the same row whatever
        // width the font gives those spaces.
        for (int slot = 8; slot <= ChatInlineIcons.SLOT_WIDTH; slot++) {
            assertEquals(top, ChatInlineIcons.spriteTop(
                    ChatInlineIcons.boxTop(0.0F, slot),
                    ChatInlineIcons.contentSize(slot), height), 0.0D);
        }
    }

    @Test
    public void aSpriteInTheContentBoxFollowsTheRowsRule() {
        for (int extent = 1; extent <= BOX; extent++) {
            assertEquals("extent " + extent,
                    LostTalesChatOverlayRenderer.centredBoxTop(extent),
                    ChatInlineIcons.spriteTop(
                            ChatInlineIcons.boxTop(0.0F,
                                    ChatInlineIcons.SLOT_WIDTH),
                            ChatInlineIcons.CONTENT_SIZE, extent), 0.0D);
        }
    }

    @Test
    public void aHoverRuleStandsOnTheDescendersShadowRow() {
        // The row under the descenders, where their shadow falls, with
        // the row's last pixel clear below it. The rule takes no shadow
        // of its own.
        int rule = TEXT_TOP + LostTalesChatVisualStyle.UNDERLINE_ROW;
        assertEquals(TEXT_TOP + CAPS + 1, rule);
        assertEquals(LINE - 2, rule);
    }

    @Test
    public void aDividerRuleAndItsDateShareTheCapitalsMiddle() {
        int rule = LostTalesChatOverlayRenderer.DIVIDER_RULE_OFFSET;
        // A one-pixel rule cannot stand on the middle of twelve rows: it
        // takes the upper middle row, five above it and six below, the
        // odd clear row below it as the capitals have theirs.
        assertEquals(5, rule);
        assertEquals(6, LINE - 1 - rule);
        // The date is drawn at the text's top edge, and its capitals are
        // centred on the rule exactly: three rows of them above it and
        // three below. Both middles are doubled so they stay whole.
        assertEquals(3, rule - TEXT_TOP);
        assertEquals(3, TEXT_TOP + CAPS - 1 - rule);
        int capsMiddle = 2 * TEXT_TOP + CAPS;
        int ruleMiddle = 2 * rule + 1;
        assertEquals(ruleMiddle, capsMiddle);
    }
}
