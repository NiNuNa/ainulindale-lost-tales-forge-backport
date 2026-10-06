package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatFellowship;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import java.util.Collections;
import java.util.UUID;
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
    public void theChannelTabsAreTheOnesThePlayerCanRead() {
        for (ConversationPage tab : ChatMenus.channelTabs(false)) {
            assertTrue(ClientChatChannelState.isAvailable(tab));
        }
        for (ConversationPage tab : ChatMenus.channelTabs(true)) {
            assertTrue(ClientChatChannelState.isAvailable(tab));
            assertFalse(ChatLayout.isOpen(tab));
        }
    }

    /** The Fellowship channel is offered as one tab for each fellowship, never as its own. */
    @Test
    public void eachFellowshipIsOfferedByItsOwnTab() {
        UUID grey = new UUID(7L, 7L);
        ClientChatIdentitySelection.accept(new LostTalesChatIdentitySyncPacket(
                Collections.<UUID>emptyList(),
                Collections.singletonList(new ChatFellowship(grey, "Grey Company", 0x123456))));
        try {
            List<ConversationPage> offered = ChatMenus.channelTabs(false);
            assertTrue(offered.contains(ConversationPage.of(ChatChannel.FELLOWSHIP, grey.toString())));
            assertFalse(offered.contains(ConversationPage.of(ChatChannel.FELLOWSHIP)));
        } finally {
            ClientChatIdentitySelection.clear();
        }
    }

    @Test
    public void closedUnreadCountIsCappedJustPastTheCounterLimit() {
        int total = 0;
        for (ConversationPage tab : ChatMenus.channelTabs(true)) {
            total += ClientChatChannelViews.unreadCount(tab);
        }
        assertEquals(Math.min(ClientChatChannelViews.MAX_UNREAD + 1, total),
                ChatMenus.closedUnreadCount());
    }

    /** A closed NPC conversation's unread lines count on the {@code +}'s mark too. */
    @Test
    public void theClosedCountTakesInClosedNpcConversations() {
        ClientChatChannelViews.clear();
        try {
            ConversationPage npc = ConversationPage.npc("Grey Wanderer");
            ChatLayout.noteNpcSpoke(npc);
            assertFalse(ChatLayout.isOpen(npc));
            int before = ChatMenus.closedUnreadCount();
            ConversationPage selected = ConversationPage.of(ChatChannel.OOC);
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
     * The chat's part of the Lost Tales Menu and Page Search: every channel
     * the player can read, open or not, each row's id its tab's, and
     * nothing else while no player list can be read; a filter matching
     * nothing leaves no row.
     */
    @Test
    public void theChatOffersEveryChannelByItsTab() {
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        ChatMenus.addEveryConversation(null, entries, "");
        int rows = 0;
        for (MenuWindow.Entry entry : entries) {
            rows++;
            assertTrue(ConversationPage.fromId(entry.id) != null);
        }
        assertEquals(ChatMenus.channelTabs(false).size(), rows);
        List<MenuWindow.Entry> none = new ArrayList<MenuWindow.Entry>();
        ChatMenus.addEveryConversation(null, none, "zzzz-nothing");
        assertTrue(none.isEmpty());
    }

    /**
     * The NPC conversations of the session are offered after the players,
     * open or not, the one that spoke last first, each row's id its tab's
     * and the tab its icon; the filter narrows them by name.
     */
    @Test
    public void theChatOffersItsNpcConversations() {
        ConversationPage bilbo = ConversationPage.npc("Bilbo");
        ConversationPage frodo = ConversationPage.npc("Frodo");
        ConversationPage sam = ConversationPage.npc("Sam");
        ChatLayout.noteNpcSpoke(bilbo);
        ChatLayout.noteNpcSpoke(frodo);
        ChatLayout.noteNpcSpoke(sam);
        assertTrue(ChatLayout.openTab(sam, null) != null);
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        ChatMenus.addEveryConversation(null, entries, "");
        List<String> ids = new ArrayList<String>();
        for (MenuWindow.Entry entry : entries) {
            if (entry.id.startsWith("npc:")) {
                ids.add(entry.id);
                assertEquals(ConversationPage.fromId(entry.id), entry.icon);
            }
        }
        assertEquals(Arrays.asList(sam.id(), frodo.id(), bilbo.id()), ids);
        assertEquals(Arrays.asList(frodo, bilbo), ChatLayout.npcConversations(true));
        List<MenuWindow.Entry> narrowed = new ArrayList<MenuWindow.Entry>();
        ChatMenus.addEveryConversation(null, narrowed, "bil");
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
                "Steve", "", false, null, null, null, null);
        ChatMenus.MessageAim again = new ChatMenus.MessageAim(42, 7L,
                "hello there", "Steve", "Aragorn", false, null, null, "w1", null);
        ChatMenus.MessageAim other = new ChatMenus.MessageAim(43, 8L, "hello",
                "Steve", "", false, null, null, null, null);
        assertEquals(one, again);
        assertEquals(one.hashCode(), again.hashCode());
        assertFalse(one.equals(other));
    }
}
