package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
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
 * one, the 6px speech bubble on it, and the 12px reaction chip and
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
    public void aReactionChipIsAFramedButtonCentredInItsRow() {
        int height = ChatReactionMarker.HEIGHT;
        // The emoji's box with the frame's inset above and below it.
        assertEquals(BOX + 2 * LostTalesUiFramedButton.INSET, height);
        // A reaction row grows to hold its chips only where the small
        // size cannot shrink them into a line: GUI scale 1 draws them
        // whole and 4 at three quarters; 2 and 3 fit a line as they are.
        assertEquals(18, ChatStackRows.reactionRowHeight(1.0F));
        assertEquals(LINE, ChatStackRows.reactionRowHeight(0.5F));
        assertEquals(LINE, ChatStackRows.reactionRowHeight(2.0F / 3.0F));
        assertEquals(14, ChatStackRows.reactionRowHeight(0.75F));
        // At full size a chip fills its row exactly.
        float textTop = LostTalesChatOverlayRenderer.reactionTextTop(100, 18,
                1.0F);
        float chipTop = textTop - ChatReactionMarker.TEXT_DROP;
        assertEquals(82.0F, chipTop, 0.0F);
        assertEquals(100.0F, chipTop + height, 0.0F);
        // Its emoji has the inset above and below it, and the count's
        // capitals stand half a pixel above the emoji's middle.
        int emojiTop = LostTalesChatVisualStyle.chipEmojiTop(0);
        assertEquals(LostTalesUiFramedButton.INSET, emojiTop);
        assertEquals(LostTalesUiFramedButton.INSET, height - (emojiTop + BOX));
        float capsMiddle = ChatReactionMarker.TEXT_DROP + CAPS / 2.0F;
        float emojiMiddle = emojiTop + BOX / 2.0F;
        assertEquals(0.5F, emojiMiddle - capsMiddle, 0.0F);
        // At half size, GUI scale 2, it is centred in a line and lands on
        // whole display pixels.
        float half = LostTalesChatOverlayRenderer.reactionTextTop(100, LINE,
                0.5F) - ChatReactionMarker.TEXT_DROP * 0.5F;
        assertEquals(89.5F, half, 0.0F);
        assertEquals(0.0F, (half * 2.0F) % 1.0F, 0.0F);
    }

    @Test
    public void theToolbarStandsOnItsRowsCapitals() {
        int size = LostTalesChatOverlayRenderer.TOOLBAR_BUTTON_SIZE;
        // A framed button: an emoji's box, then two clear pixels, the ink
        // and a ring of surface either side of it.
        assertEquals(BOX + 8, size);
        int rowBottom = 100;
        int top = LostTalesChatOverlayRenderer.toolbarTop(rowBottom, LINE);
        // Taller than the row, so centred on its capitals and half a pixel
        // up: four rows above the row, two below it.
        assertEquals(84, top);
        assertEquals(4, rowBottom - LINE - top);
        assertEquals(2, top + size - rowBottom);
        // Its react button's emoji stands exactly where a row's does.
        assertEquals(rowBottom - LINE + TEXT_TOP
                        + LostTalesChatOverlayRenderer.centredBoxTop(BOX),
                top + (size - BOX) / 2);
    }

    @Test
    public void theSpeechBubbleTakesOneRowWhereverItIsDrawn() {
        int height = LostTalesUiSheet.SPEECH_BUBBLE.getHeight();
        assertEquals(6, height);
        // A reply's quote and the typing line place it as a box of its
        // own: six rows, the pill and its tail, half a pixel above the
        // capitals' middle — on their top row, a row above their foot.
        int top = LostTalesChatOverlayRenderer.centredBoxTop(height);
        assertEquals(2, TEXT_TOP + top);
        assertEquals(4, LINE - (TEXT_TOP + top + height));
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
        // The row under the descenders, where their shadow falls, and
        // the rule's own shadow on the row's last pixel, still inside the
        // row: under every glyph and glyph shadow of the line.
        int rule = TEXT_TOP + LostTalesChatVisualStyle.UNDERLINE_ROW;
        assertEquals(TEXT_TOP + CAPS + 1, rule);
        assertEquals(LINE - 2, rule);
        assertEquals(LINE - 1, rule + LostTalesChatVisualStyle.SHADOW_OFFSET);
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
