package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * While the server keeps a log of whispers, a whisper says so (CHAT-85):
 * in its help, and on a faint row standing over its oldest line, a
 * filler like a day's rule. A channel, an NPC's conversation and the
 * feed have no such row, and none stands while the log is off.
 */
public final class ChatWhisperLogNoticeTest {

    @Before
    @After
    public void clear() {
        ClientChatChannelState.setWhispersLogged(false);
    }

    @Test
    public void onlyAWhisperWithAPlayerSaysItIsLogged() {
        ConversationPage whisper = ConversationPage.whisper("Bilbo", "");
        assertFalse("the log is off", whisper.showsWhisperLog());
        ClientChatChannelState.setWhispersLogged(true);
        assertTrue(whisper.showsWhisperLog());
        assertFalse(ConversationPage.of(ChatChannel.GLOBAL).showsWhisperLog());
        assertFalse(ConversationPage.of(ChatChannel.PROXIMITY).showsWhisperLog());
    }

    @Test
    public void aWhispersViewOpensWithTheNotice() {
        ConversationPage whisper = ConversationPage.whisper("Bilbo", "");
        ClientChatChannelState.setWhispersLogged(true);
        assertEquals(ConversationPage.WHISPERS_LOGGED_KEY,
                ChatWindowLines.noticeFor(ChatLineFilter.of(whisper), false));
        assertNull("the feed has none",
                ChatWindowLines.noticeFor(ChatLineFilter.of(whisper), true));
        assertNull("a view of several conversations has none",
                ChatWindowLines.noticeFor(ChatLineFilter.of(Arrays.asList(whisper,
                        ConversationPage.of(ChatChannel.GLOBAL)),
                        new ArrayList<ConversationPage>()), false));
        assertNull(ChatWindowLines.noticeFor(
                ChatLineFilter.of(ConversationPage.of(ChatChannel.GLOBAL)), false));
        ClientChatChannelState.setWhispersLogged(false);
        assertNull(ChatWindowLines.noticeFor(ChatLineFilter.of(whisper), false));
    }

    @Test
    public void theNoticeStandsOverTheOldestRowAsAFiller() {
        List<ChatLine> rows = new ArrayList<ChatLine>();
        rows.add(new ChatLine(7, new ChatComponentText("newest"), 2));
        rows.add(new ChatLine(5, new ChatComponentText("oldest"), 1));
        List<ChatLine> noticed = ChatWindowLines.withNotice(rows,
                ConversationPage.WHISPERS_LOGGED_KEY);
        assertEquals(4, noticed.size());
        assertTrue(ChatWindowLines.isSpacer(noticed.get(2)));
        ChatLine notice = noticed.get(3);
        assertEquals(ConversationPage.WHISPERS_LOGGED_KEY,
                ChatWindowLines.noticeKey(notice));
        assertTrue(ChatWindowLines.isNotice(notice));
        assertTrue(ChatWindowLines.isFiller(notice));
        assertFalse(ChatWindowLines.isDateDivider(notice));
        assertEquals("it ages with the oldest row", 5, notice.getUpdatedCounter());
        assertNull(ChatWindowLines.noticeKey(rows.get(0)));
        assertSame("no notice, no change", rows, ChatWindowLines.withNotice(rows, null));
        List<ChatLine> none = new ArrayList<ChatLine>();
        assertSame("no row to stand over", none,
                ChatWindowLines.withNotice(none, ConversationPage.WHISPERS_LOGGED_KEY));
    }
}
