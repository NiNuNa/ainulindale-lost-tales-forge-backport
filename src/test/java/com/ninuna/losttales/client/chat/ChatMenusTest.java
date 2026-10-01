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

    /** A closed NPC conversation's unread lines count after the {@code +} too. */
    @Test
    public void theClosedCountTakesInClosedNpcConversations() {
        ClientChatChannelViews.clear();
        try {
            ChatTab npc = ChatTab.npc("Grey Wanderer");
            ChatLayout.noteNpcSpoke(npc);
            assertFalse(ChatLayout.isOpen(npc));
            int before = ChatMenus.closedUnreadCount();
            ChatTab selected = ChatTab.of(ChatChannel.OOC);
            ClientChatChannelViews.record(-1, npc, selected, false,
                    ChatMessageIds.NONE, System.currentTimeMillis(), false);
            ClientChatChannelViews.record(-2, npc, selected, false,
                    ChatMessageIds.NONE, System.currentTimeMillis(), false);
            assertEquals(Math.min(ClientChatChannelViews.MAX_UNREAD + 1,
                    before + 2), ChatMenus.closedUnreadCount());
            assertFalse(ChatMenus.closedMark().isNone());
        } finally {
            ClientChatChannelViews.clear();
        }
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

    /**
     * The NPC conversations of the session in no window are offered
     * after the players, the one that spoke last first, each row's id
     * its tab's and the tab its icon; one standing open is not, and the
     * filter narrows them by name.
     */
    @Test
    public void theChatOffersItsClosedNpcConversations() {
        ChatTab bilbo = ChatTab.npc("Bilbo");
        ChatTab frodo = ChatTab.npc("Frodo");
        ChatTab sam = ChatTab.npc("Sam");
        ChatLayout.noteNpcSpoke(bilbo);
        ChatLayout.noteNpcSpoke(frodo);
        ChatLayout.noteNpcSpoke(sam);
        assertTrue(ChatLayout.openTab(sam, null) != null);
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        ChatMenus.addOpenable(null, entries, "", false);
        List<String> ids = new ArrayList<String>();
        for (MenuWindow.Entry entry : entries) {
            if (entry.id.startsWith("npc:")) {
                ids.add(entry.id);
                assertEquals(ChatTab.fromId(entry.id), entry.icon);
            }
        }
        assertEquals(Arrays.asList(frodo.id(), bilbo.id()), ids);
        assertTrue(ChatLayout.hasClosedNpcConversation());
        List<MenuWindow.Entry> narrowed = new ArrayList<MenuWindow.Entry>();
        ChatMenus.addOpenable(null, narrowed, "bil", true);
        int npcRows = 0;
        for (MenuWindow.Entry entry : narrowed) {
            if (entry.id.startsWith("npc:")) {
                npcRows++;
                assertEquals(bilbo.id(), entry.id);
            }
        }
        assertEquals(1, npcRows);
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
