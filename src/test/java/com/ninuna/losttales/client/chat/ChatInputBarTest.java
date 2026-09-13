package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ChatInputBarTest {

    @After
    public void reset() {
        ClientChatChannelState.clear();
    }

    @Test
    public void barSlotsCountFromTheRightEdgeInButtonSteps() {
        int step = ChatPickerPanel.BUTTON_SIZE + ChatPickerPanel.BUTTON_MARGIN;
        int first = ChatInputBar.barSlotLeft(200, 0);
        assertEquals(200 - ChatPickerPanel.BUTTON_MARGIN
                - ChatPickerPanel.BUTTON_SIZE, first);
        assertEquals(first - step, ChatInputBar.barSlotLeft(200, 1));
        assertEquals(first - 3 * step, ChatInputBar.barSlotLeft(200, 3));
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
     * The typing well is one message row, and everything in it stands
     * where a message row puts it: the text two rows down, the ten-row
     * content box — previews, caret, selection — a clear row above and
     * below. What is typed sits exactly as it will once it is sent.
     */
    @Test
    public void theWellLaysItsContentOutLikeAMessageRow() {
        int barTop = 100;
        int wellTop = ChatInputBar.wellTopFor(barTop);
        int wellBottom = wellTop + LostTalesChatOverlayRenderer.LINE_HEIGHT;
        int textTop = ChatInputBar.textTopFor(barTop);
        int contentTop = ChatInlineIcons.rowContentTop(textTop);
        int contentBottom = contentTop + (int)ChatInlineIcons.CONTENT_SIZE;
        assertEquals(LostTalesChatOverlayRenderer.ROW_TEXT_TOP,
                textTop - wellTop);
        assertEquals(1, contentTop - wellTop);
        assertEquals(1, wellBottom - contentBottom);
        assertEquals(contentTop, ChatInlineIcons.rowBoxTop(textTop,
                ChatInlineIcons.SLOT_WIDTH), 0.0F);
        // Level with the bar's buttons, which are as tall.
        assertEquals(ChatInputBar.controlTopFor(barTop), wellTop);
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

    /** The indicator names the channel as its tab does, its icon before it. */
    @Test
    public void theIndicatorNamesTheChannelAsItsTabDoes() {
        ClientChatChannelState.clear();
        assertEquals(ClientChatChannelState.displayName(
                ChatTab.of(ChatChannel.OOC)),
                ChatInputBar.indicatorLabel(ChatTab.of(ChatChannel.OOC)));
    }

    /**
     * The indicator gives the field what it lacks of its comfortable
     * width, the name first and then the gap after the icon, down to
     * the icon alone. The whole name's last column is spacing, so the
     * first pixel it gives up wins the field nothing and goes with the
     * second: each pixel the field lacks moves the divider by one.
     */
    @Test
    public void theIndicatorGivesItsNameUpForTheField() {
        int whole = ChatChannelIcons.GAP + 40;
        int comfortable = ChatInputBar.COMFORTABLE_FIELD_WIDTH;
        assertEquals(whole, ChatInputBar.indicatorShown(whole, comfortable));
        assertEquals(whole,
                ChatInputBar.indicatorShown(whole, comfortable + 30));
        assertEquals(whole - 2,
                ChatInputBar.indicatorShown(whole, comfortable - 1));
        assertEquals(whole - 11,
                ChatInputBar.indicatorShown(whole, comfortable - 10));
        assertEquals(0, ChatInputBar.indicatorShown(whole,
                comfortable - whole));
        assertEquals(0, ChatInputBar.indicatorShown(whole, 0));
    }
}
