package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.client.window.MenuWindow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatMenusTest {
    @Before
    public void setUp() {
        ChatLayout.reset();
        ClientChatChannelState.clear();
    }

    @After
    public void tearDown() {
        ChatLayout.reset();
        ClientChatChannelState.clear();
    }

    @Test
    public void whisperCandidatesLeaveOutOneselfAndDuplicatesAndSort() {
        List<String> accounts = Arrays.asList("zed", "Steve", null, "  ",
                "alex", "STEVE", "Alex ", "me");
        assertEquals(Arrays.asList("alex", "Steve", "zed"),
                ChatMenus.whisperCandidates("Me", accounts));
        assertTrue(ChatMenus.whisperCandidates("me",
                new ArrayList<String>()).isEmpty());
    }

    @Test
    public void restorableChannelsAreTheClosedOnesThePlayerCouldSee() {
        List<ChatChannel> expected = new ArrayList<ChatChannel>();
        for (ChatChannel channel : ChatLayout.closedChannels()) {
            if (ClientChatChannelState.isAvailable(channel)) {
                expected.add(channel);
            }
        }
        assertEquals(expected, ChatMenus.restorableChannels());
        for (ChatChannel channel : ChatMenus.restorableChannels()) {
            assertFalse(ChatLayout.isOpen(ChatTab.of(channel)));
        }
    }

    @Test
    public void closedUnreadCountIsCappedJustPastTheCounterLimit() {
        int total = 0;
        for (ChatChannel channel : ChatMenus.restorableChannels()) {
            total += ClientChatChannelViews.unreadCount(channel);
        }
        assertEquals(Math.min(ClientChatChannelViews.MAX_UNREAD + 1, total),
                ChatMenus.closedUnreadCount());
    }

    /**
     * The chat's part of the {@code +} and the tab search: its closed
     * channels, each row's id its tab's, and nothing else while no player
     * list can be read; a filter matching nothing leaves no section.
     */
    @Test
    public void theChatOffersItsClosedChannelsByTheirTabs() {
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        ChatMenus.addOpenable(null, entries, "", true);
        int rows = 0;
        for (MenuWindow.Entry entry : entries) {
            if (entry.header) {
                continue;
            }
            rows++;
            ChatTab tab = ChatTab.fromId(entry.id);
            assertTrue(tab != null);
            assertFalse(ChatLayout.isOpen(tab));
        }
        assertEquals(ChatMenus.restorableChannels().size(), rows);
        List<MenuWindow.Entry> none = new ArrayList<MenuWindow.Entry>();
        ChatMenus.addOpenable(null, none, "zzzz-nothing", true);
        assertTrue(none.isEmpty());
    }

    @Test
    public void aMuteTargetNamesADiscordMemberThroughTheBridge() {
        assertEquals("Steve", ChatMenus.muteTarget(false, "Steve"));
        assertEquals("discord:Nils", ChatMenus.muteTarget(true, "Nils"));
    }

    @Test
    public void onlyAServerNamedLineOfOnesOwnIsOnesOwn() {
        long serverId = 42L;
        assertTrue(ChatMenus.isOwnMessage(serverId, false, "Steve",
                "steve"));
        assertFalse(ChatMenus.isOwnMessage(serverId, true, "Steve",
                "Steve"));
        assertFalse(ChatMenus.isOwnMessage(ChatMessageIds.NONE, false,
                "Steve", "Steve"));
        assertFalse(ChatMenus.isOwnMessage(serverId, false, "Steve",
                "Alex"));
        assertFalse(ChatMenus.isOwnMessage(serverId, false, "Steve",
                null));
    }

    @Test
    public void aReplySuggestionNamesTheAccountBehindIt() {
        assertEquals("Steve", ChatMenus.replyAccount("/msg Steve "));
        assertEquals("", ChatMenus.replyAccount("/tell Steve "));
        assertEquals("", ChatMenus.replyAccount(null));
    }

    /**
     * A message menu's control is a switch for the message it was pressed
     * for: the message is the same one by its line, whatever was read off
     * it.
     */
    @Test
    public void aMessageIsTheSameOneByItsLine() {
        ChatMenus.MessageAim one = new ChatMenus.MessageAim(42, 7L, "hello",
                "Steve", "", false, null, null, null);
        ChatMenus.MessageAim again = new ChatMenus.MessageAim(42, 7L,
                "hello there", "Steve", "Aragorn", false, null, null, "w1");
        ChatMenus.MessageAim other = new ChatMenus.MessageAim(43, 8L, "hello",
                "Steve", "", false, null, null, null);
        assertEquals(one, again);
        assertEquals(one.hashCode(), again.hashCode());
        assertFalse(one.equals(other));
    }
}
