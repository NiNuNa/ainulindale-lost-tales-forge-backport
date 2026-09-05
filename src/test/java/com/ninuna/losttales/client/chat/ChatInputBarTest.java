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

    @Test
    public void theIndicatorNamesTheTabInBrackets() {
        ClientChatChannelState.clear();
        assertEquals("[" + ClientChatChannelState.displayName(
                ChatTab.of(ChatChannel.OOC)) + "]",
                ChatInputBar.indicatorLabel(ChatTab.of(ChatChannel.OOC)));
    }
}
