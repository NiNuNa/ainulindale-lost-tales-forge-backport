package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.BarLead;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ChatInputBarTest {

    @After
    public void reset() {
        ClientChatChannelState.clear();
    }

    /**
     * The bar's buttons count from its right edge: the send button's
     * glyph an edge's gap (7) from the frame, the hairline after it an
     * edge's gap from both glyphs, the rest a button's gap (5) apart, ink
     * to ink, each glyph ten pixels in a twelve-pixel square.
     */
    @Test
    public void barSlotsCountFromTheRightEdgeInButtonSteps() {
        int send = ChatInputBar.barSlotLeft(200, 0);
        int first = ChatInputBar.barSlotLeft(200, 1);
        int second = ChatInputBar.barSlotLeft(200, 2);
        assertEquals("the send glyph 7 from the edge", 7,
                200 - inkEnd(send));
        assertEquals("7, the hairline, 7 between send and the next", 15,
                inkStart(send) - inkEnd(first));
        assertEquals("5 between the other glyphs", 5,
                inkStart(first) - inkEnd(second));
        int step = ChatPickerPanel.BUTTON_SIZE + ChatPickerPanel.BUTTON_MARGIN;
        assertEquals(first - 2 * step, ChatInputBar.barSlotLeft(200, 3));
    }

    /** Where a ten-pixel glyph's ink starts in the square at {@code left}. */
    private static int inkStart(int left) {
        return left + (ChatPickerPanel.BUTTON_SIZE - 10) / 2;
    }

    /** Just past a ten-pixel glyph's ink in the square at {@code left}. */
    private static int inkEnd(int left) {
        return inkStart(left) + 10;
    }

    @Test
    public void theCounterScaleSnapsToWholeDisplayPixels() {
        assertEquals(1.0F, ChatInputBar.counterScale(1), 0.0F);
        assertEquals(0.5F, ChatInputBar.counterScale(2), 0.0F);
        assertEquals(2.0F / 3.0F, ChatInputBar.counterScale(3), 0.0001F);
        assertEquals(0.75F, ChatInputBar.counterScale(4), 0.0F);
        assertEquals(5.0F / 6.0F, ChatInputBar.counterScale(6), 0.0001F);
    }

    /**
     * Small text's capitals are centred on the full-size capitals, the
     * odd display pixel below: three display pixels down wherever there
     * is a smaller step, nothing at GUI scale 1.
     */
    @Test
    public void smallTextIsCentredOnTheCapitals() {
        assertEquals(0.0F,
                LostTalesChatVisualStyle.smallTextTopOffset(1), 0.0F);
        assertEquals(1.5F,
                LostTalesChatVisualStyle.smallTextTopOffset(2), 0.0F);
        assertEquals(1.0F,
                LostTalesChatVisualStyle.smallTextTopOffset(3), 0.0F);
        assertEquals(0.75F,
                LostTalesChatVisualStyle.smallTextTopOffset(4), 0.0F);
    }

    /**
     * The slot is sized for a three-digit count, the slash and the limit
     * in the default font, where every digit is six pixels wide, less the
     * last glyph's spacing column; it rounds up so the count never
     * crosses its end.
     */
    @Test
    public void theCounterSlotHoldsTheWidestCountAtItsScale() {
        int widestInk = 3 * 6 + 6 + 18 - 1;
        assertEquals(41, ChatInputBar.counterSlotWidth(widestInk, 1.0F));
        assertEquals(21, ChatInputBar.counterSlotWidth(widestInk,
                ChatInputBar.counterScale(2)));
        assertEquals(28, ChatInputBar.counterSlotWidth(widestInk,
                ChatInputBar.counterScale(3)));
        assertEquals(31, ChatInputBar.counterSlotWidth(widestInk,
                ChatInputBar.counterScale(4)));
    }

    /**
     * Every framed button standing in a row of controls is one height:
     * an icon's box with the inset above and below it. The character
     * button is that square with the head centred, the wide inset on
     * every side, and the typing well is as tall as the bar's framed
     * buttons, so the three stand level.
     */
    @Test
    public void theFramedButtonsShareOneHeight() {
        assertEquals(18, LostTalesUiFramedButton.HEIGHT);
        assertEquals(TabIcons.SIZE + 2 * LostTalesUiFramedButton.INSET,
                LostTalesUiFramedButton.HEIGHT);
        assertEquals(LostTalesChatOverlayRenderer.CONTENT_BOX_HEIGHT
                + 2 * LostTalesUiFramedButton.INSET, LostTalesUiFramedButton.HEIGHT);
        assertEquals(LostTalesUiFramedButton.HEIGHT,
                LostTalesChatOverlayRenderer.TOOLBAR_HEIGHT);
        assertEquals(LostTalesUiFramedButton.HEIGHT, ChatReactionMarker.HEIGHT);
        assertEquals(ChatReactionMarker.ICON + 2 * ChatReactionMarker.PAD,
                ChatReactionMarker.HEIGHT);
        assertEquals(LostTalesUiFramedButton.HEIGHT,
                BarLead.IDENTITY_SIZE);
        assertEquals(LostTalesChatOverlayRenderer.HEAD_SIZE
                        + 2 * LostTalesUiFramedButton.WIDE_INSET,
                BarLead.IDENTITY_SIZE);
        assertEquals(LostTalesUiFramedButton.HEIGHT, ChatInputBar.CONTENT_HEIGHT);
    }

    /**
     * The bar's spacing: two clear rows between the rule and what stands
     * on the bar and two between it and the bar's foot, the window's
     * frame running just below; seven clear pixels before the tab button
     * and after the identity button, and five between the two framed
     * buttons.
     */
    @Test
    public void theBarKeepsTwoRowsAboveAndBelowAndFivePixelsBetweenItsFramedButtons() {
        assertEquals(2, ChatInputBar.CLEARANCE);
        assertEquals(1 + 2 + LostTalesUiFramedButton.HEIGHT + 2,
                WindowPlacement.BAR_STRIP_HEIGHT);
        assertEquals(7, WindowStyle.EDGE_GAP);
        assertEquals(5, BarLead.BUTTON_GAP);
    }

    /**
     * The typing well is one message row, and everything in it stands
     * where a message row puts it: the text two rows down, and an emoji
     * or item preview on the capitals, two rows above the text. The caret
     * and the selection wash take the well's middle ten rows, a clear row
     * short of it at both ends. What is typed sits exactly as it will
     * once it is sent.
     */
    @Test
    public void theWellLaysItsContentOutLikeAMessageRow() {
        int barTop = 100;
        int contentTop = ChatInputBar.contentTopFor(barTop);
        int wellTop = ChatInputBar.wellTopFor(barTop);
        int wellBottom = wellTop + ChatInputBar.WELL_HEIGHT;
        int textTop = ChatInputBar.textTopFor(barTop);
        int caretTop = ChatInputField.caretTop(textTop);
        int caretBottom = caretTop + (int)ChatInlineIcons.CONTENT_SIZE;
        // The framed buttons stand the clearance below the rule and as far
        // above the bar's bottom, the window's frame running just below it.
        assertEquals(1 + ChatInputBar.CLEARANCE, contentTop - barTop);
        assertEquals(ChatInputBar.CLEARANCE, barTop + WindowPlacement.BAR_STRIP_HEIGHT
                - (contentTop + ChatInputBar.CONTENT_HEIGHT));
        assertEquals(LostTalesUiFramedButton.HEIGHT, ChatInputBar.CONTENT_HEIGHT);
        // The well is one message row, as every chat input box is, centred
        // on the framed buttons: three rows clear above it and below it.
        assertEquals(LostTalesChatOverlayRenderer.LINE_HEIGHT,
                ChatInputBar.WELL_HEIGHT);
        assertEquals(3, wellTop - contentTop);
        assertEquals(3, contentTop + ChatInputBar.CONTENT_HEIGHT - wellBottom);
        // Its text stands where a message row puts it: the caret a row
        // inside the message row.
        int rowTop = wellTop;
        assertEquals(WindowStyle.ROW_TEXT_TOP,
                textTop - rowTop);
        assertEquals(1, caretTop - rowTop);
        assertEquals(1, rowTop + LostTalesChatOverlayRenderer.LINE_HEIGHT
                - caretBottom);
        // The caret's shadow fills the clear row under the caret, and
        // stays inside the message row.
        assertEquals(rowTop + LostTalesChatOverlayRenderer.LINE_HEIGHT,
                caretBottom + LostTalesUiInk.SHADOW_OFFSET);
        assertEquals(textTop + WindowStyle.centredBoxTop(
                        (int)ChatInlineIcons.CONTENT_SIZE),
                ChatInlineIcons.boxTop(textTop, ChatInlineIcons.SLOT_WIDTH),
                0.0F);
        assertEquals(rowTop, ChatInlineIcons.boxTop(textTop,
                ChatInlineIcons.SLOT_WIDTH), 0.0F);
        // The bar's buttons are centred on the well's middle: the
        // message row is exactly their height.
        assertEquals(ChatInputBar.controlTopFor(barTop), rowTop);
    }

    @Test
    public void theNoticeFadesInQuicklyAndOutSlowly() {
        assertEquals(0.0F, ChatInputBar.noticeOpacity(0.0F), 0.0F);
        assertEquals(0.5F, ChatInputBar.noticeOpacity(50.0F), 0.0001F);
        assertEquals(1.0F, ChatInputBar.noticeOpacity(100.0F), 0.0F);
        assertEquals(1.0F, ChatInputBar.noticeOpacity(
                ChatInputBar.NOTICE_LIFETIME_MILLIS - 250.0F), 0.0F);
        assertEquals(0.5F, ChatInputBar.noticeOpacity(
                ChatInputBar.NOTICE_LIFETIME_MILLIS - 125.0F), 0.0001F);
        assertTrue(ChatInputBar.noticeOpacity(
                ChatInputBar.NOTICE_LIFETIME_MILLIS - 1.0F) < 0.01F);
    }

    /** The tab button names the channel as its tab does. */
    @Test
    public void theTabButtonNamesTheChannelAsItsTabDoes() {
        ClientChatChannelState.clear();
        ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);
        assertEquals(ClientChatChannelState.displayName(ooc),
                BarLead.fit(ooc, 0, 1000, new WindowBar.Measure() {
                    @Override
                    public int width(String text) {
                        return text.length() * 6;
                    }
                }).label);
    }

    /**
     * The tab and identity buttons give the field what it lacks of its
     * comfortable width, the name first, down to the icon alone: each
     * pixel the field lacks moves the divider by one.
     */
    @Test
    public void theTabButtonGivesItsNameUpForTheField() {
        int whole = 80;
        int comfortable = ChatInputBar.COMFORTABLE_FIELD_WIDTH;
        assertEquals(whole, ChatInputBar.leadWidth(whole, comfortable));
        assertEquals(whole, ChatInputBar.leadWidth(whole, comfortable + 30));
        assertEquals(whole - 1, ChatInputBar.leadWidth(whole, comfortable - 1));
        assertEquals(whole - 10,
                ChatInputBar.leadWidth(whole, comfortable - 10));
    }
}
