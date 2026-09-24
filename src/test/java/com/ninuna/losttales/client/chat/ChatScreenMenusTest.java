package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatScreenMenusTest {
    @Before
    public void setUp() {
        ChatWindowLayout.reset();
        ClientChatChannelState.clear();
    }

    @After
    public void tearDown() {
        ChatWindowLayout.reset();
        ClientChatChannelState.clear();
    }

    @Test
    public void aFilterMatchesAnywhereInTheNameWhateverTheCase() {
        assertTrue(ChatScreenMenus.matchesFilter("Global", ""));
        assertTrue(ChatScreenMenus.matchesFilter("Global", "lob"));
        assertTrue(ChatScreenMenus.matchesFilter("Global", "GLO"));
        assertFalse(ChatScreenMenus.matchesFilter("Global", "party"));
    }

    @Test
    public void whisperCandidatesLeaveOutOneselfAndDuplicatesAndSort() {
        List<String> accounts = Arrays.asList("zed", "Steve", null, "  ",
                "alex", "STEVE", "Alex ", "me");
        assertEquals(Arrays.asList("alex", "Steve", "zed"),
                ChatScreenMenus.whisperCandidates("Me", accounts));
        assertTrue(ChatScreenMenus.whisperCandidates("me",
                new ArrayList<String>()).isEmpty());
    }

    @Test
    public void restorableChannelsAreTheClosedOnesThePlayerCouldSee() {
        List<ChatChannel> expected = new ArrayList<ChatChannel>();
        for (ChatChannel channel : ChatWindowLayout.closedChannels()) {
            if (ClientChatChannelState.isAvailable(channel)) {
                expected.add(channel);
            }
        }
        assertEquals(expected, ChatScreenMenus.restorableChannels());
        for (ChatChannel channel : ChatScreenMenus.restorableChannels()) {
            assertFalse(ChatWindowLayout.isOpen(ChatTab.of(channel)));
        }
    }

    @Test
    public void closedUnreadCountIsCappedJustPastTheCounterLimit() {
        int total = 0;
        for (ChatChannel channel : ChatScreenMenus.restorableChannels()) {
            total += ClientChatChannelViews.unreadCount(channel);
        }
        assertEquals(Math.min(ClientChatChannelViews.MAX_UNREAD + 1, total),
                ChatScreenMenus.closedUnreadCount());
    }

    @Test
    public void aMuteTargetNamesADiscordMemberThroughTheBridge() {
        assertEquals("Steve", ChatScreenMenus.muteTarget(false, "Steve"));
        assertEquals("discord:Nils", ChatScreenMenus.muteTarget(true, "Nils"));
    }

    @Test
    public void onlyAServerNamedLineOfOnesOwnIsOnesOwn() {
        long serverId = 42L;
        assertTrue(ChatScreenMenus.isOwnMessage(serverId, false, "Steve",
                "steve"));
        assertFalse(ChatScreenMenus.isOwnMessage(serverId, true, "Steve",
                "Steve"));
        assertFalse(ChatScreenMenus.isOwnMessage(ChatMessageIds.NONE, false,
                "Steve", "Steve"));
        assertFalse(ChatScreenMenus.isOwnMessage(serverId, false, "Steve",
                "Alex"));
        assertFalse(ChatScreenMenus.isOwnMessage(serverId, false, "Steve",
                null));
    }

    @Test
    public void aReplySuggestionNamesTheAccountBehindIt() {
        assertEquals("Steve", ChatScreenMenus.replyAccount("/msg Steve "));
        assertEquals("", ChatScreenMenus.replyAccount("/tell Steve "));
        assertEquals("", ChatScreenMenus.replyAccount(null));
    }

    @Test
    public void theSearchPanelListsOpenTabsAndClosedChannelsAndNothingElse() {
        ChatScreenMenus menus = new ChatScreenMenus(null, null, null);
        List<ChatMenu.Entry> entries = menus.searchEntries("");
        assertFalse(entries.isEmpty());
        int openTabs = 0;
        for (ChatWindow window : ChatWindowLayout.windows()) {
            openTabs += ChatWindowFrame.visibleTabs(window).size();
        }
        int rows = 0;
        for (ChatMenu.Entry entry : entries) {
            if (entry.header) {
                continue;
            }
            rows++;
            if (entry.id.startsWith("open:")) {
                assertTrue(ChatWindowLayout.isOpen(
                        ChatTab.fromId(entry.id.substring("open:".length()))));
            } else {
                assertFalse(ChatWindowLayout.isOpen(
                        ChatTab.of(ChatChannel.fromId(entry.id))));
            }
        }
        // No player list is reachable here, so the rows are the open tabs
        // and the restorable channels, and nothing else.
        assertEquals(openTabs + ChatScreenMenus.restorableChannels().size(),
                rows);
        // A filter matching nothing keeps the window open with one plain row.
        List<ChatMenu.Entry> none = menus.searchEntries("zzzz-nothing");
        assertEquals(1, none.size());
        assertFalse(none.get(0).header);
    }

    /**
     * A menu's control is a switch for the thing it was pressed for: a
     * message is the same one by its line whatever was read off it, a
     * window's search by the window, and the empty screen's by null.
     */
    @Test
    public void aMenuIsAboutTheSameThingByItsLineOrWhatItEquals() {
        ChatScreenMenus.MessageAim one = new ChatScreenMenus.MessageAim(42,
                7L, "hello", "Steve", "", false, null, null, null);
        ChatScreenMenus.MessageAim again = new ChatScreenMenus.MessageAim(42,
                7L, "hello there", "Steve", "Aragorn", false, null, null,
                "w1");
        ChatScreenMenus.MessageAim other = new ChatScreenMenus.MessageAim(43,
                8L, "hello", "Steve", "", false, null, null, null);
        assertTrue(ChatScreenMenus.sameAbout(one, again));
        assertFalse(ChatScreenMenus.sameAbout(one, other));
        assertTrue(ChatScreenMenus.sameAbout("w1", "w1"));
        assertFalse(ChatScreenMenus.sameAbout("w1", "w2"));
        assertTrue(ChatScreenMenus.sameAbout(null, null));
        assertFalse(ChatScreenMenus.sameAbout("w1", null));
    }
}
