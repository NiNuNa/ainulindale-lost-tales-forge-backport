package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ClientChatChannelStateTest {

    @After
    public void cleanUp() {
        ClientChatChannelState.clear();
        ClientCharacterRosterCache.clear();
        ChatWindowLayout.reset();
    }

    @Test
    public void closedChannelsAreNeverSelectedAndCycleFollowsTheLayout() {
        acceptRoster("lotr:gondor");
        ChatWindowLayout.detach(ChatChannel.PROXIMITY, 0.0D, 0.0D);
        assertEquals(java.util.Arrays.asList(ChatChannel.CONSOLE,
                ChatChannel.ALL, ChatChannel.FACTION, ChatChannel.OOC,
                ChatChannel.PROXIMITY),
                ClientChatChannelState.getOpenChannels());
        // Cycling stays within the window: Proximity is alone in its.
        ClientChatChannelState.select(ChatChannel.PROXIMITY);
        assertEquals(ChatChannel.PROXIMITY, ClientChatChannelState.cycle().getChannel());
        ClientChatChannelState.select(ChatChannel.OOC);
        assertEquals(ChatChannel.ALL, ClientChatChannelState.cycle().getChannel());
        assertEquals(ChatChannel.FACTION, ClientChatChannelState.cycle().getChannel());
        // A closed channel stays available (readable) but not selectable.
        ClientChatChannelState.select(ChatChannel.OOC);
        ChatWindowLayout.close(ChatChannel.OOC);
        assertTrue(ClientChatChannelState.isAvailable(ChatChannel.OOC));
        assertFalse(ClientChatChannelState.isSelectable(ChatChannel.OOC));
        assertEquals(ChatChannel.ALL, ClientChatChannelState.getSelectedChannel());
        ClientChatChannelState.select(ChatChannel.OOC);
        assertEquals(ChatChannel.ALL, ClientChatChannelState.getSelectedChannel());
        // Without a character and with Global closed, the fallback is OOC
        // (account conversation) even though it now sits after Console.
        ChatWindowLayout.restore(ChatChannel.OOC);
        ChatWindowLayout.close(ChatChannel.ALL);
        acceptRoster("");
        ClientChatChannelState.ensureAvailable();
        assertEquals(ChatChannel.OOC, ClientChatChannelState.getSelectedChannel());
    }

    @Test
    public void accountOnlyPlayersReadGlobalButOnlyTalkInOoc() {
        // Global stays readable (achievements live there) but not sendable.
        assertTrue(ClientChatChannelState.isAvailable(ChatChannel.ALL));
        assertFalse(ClientChatChannelState.canSend(ChatChannel.ALL));
        assertFalse(ClientChatChannelState.isAvailable(
                ChatChannel.PROXIMITY));
        assertTrue(ClientChatChannelState.isAvailable(ChatChannel.OOC));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.OOC));
        // The console is always there; Admin only once the server says so.
        assertEquals(java.util.Arrays.asList(ChatChannel.ALL, ChatChannel.OOC,
                ChatChannel.CONSOLE),
                ClientChatChannelState.getAvailableChannels());
        // TAB cycles inside the selected channel's own window: Global and
        // OOC share the conversation window, the console lives elsewhere.
        ClientChatChannelState.select(ChatChannel.OOC);
        assertEquals(ChatChannel.ALL, ClientChatChannelState.cycle().getChannel());
        assertEquals(ChatChannel.OOC, ClientChatChannelState.cycle().getChannel());
        ClientChatChannelState.select(ChatChannel.CONSOLE);
        assertEquals(ChatChannel.CONSOLE, ClientChatChannelState.cycle().getChannel());
        assertFalse(ClientChatChannelState.canSend(ChatChannel.ADMIN));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.CONSOLE));
        ClientChatChannelState.setAdminAccess(true);
        assertEquals(java.util.Arrays.asList(ChatChannel.ALL, ChatChannel.OOC,
                ChatChannel.ADMIN, ChatChannel.CONSOLE),
                ClientChatChannelState.getAvailableChannels());
        ClientChatChannelState.select(ChatChannel.ADMIN);
        ClientChatChannelState.setAdminAccess(false);
        // Losing op status drops the selection back to a channel the
        // player can talk in: OOC, since there is no character here.
        assertEquals(ChatChannel.OOC, ClientChatChannelState.getSelectedChannel());
        ClientChatChannelState.setDraft("unsent text");
        assertEquals("unsent text", ClientChatChannelState.getDraft());
        // Drafts belong to the tab they were typed in.
        ChatTab alex = ChatTab.whisper("Alex");
        ClientChatChannelState.setDraft(alex, "for alex");
        assertEquals("unsent text", ClientChatChannelState.getDraft());
        assertEquals("for alex", ClientChatChannelState.getDraft(alex));
        assertEquals("for alex",
                ClientChatChannelState.getDraft(ChatTab.whisper("alex")));
        ClientChatChannelState.setDraft(alex, "");
        assertEquals("", ClientChatChannelState.getDraft(alex));
        assertEquals("unsent text", ClientChatChannelState.getDraft());
        ClientChatChannelState.clear();
        assertEquals("", ClientChatChannelState.getDraft());
        // Global is the readable default; OOC is where the player can talk.
        assertEquals(ChatChannel.ALL, ClientChatChannelState.getSelectedChannel());
        ClientChatChannelState.select(ChatChannel.OOC);
        assertEquals(ChatChannel.OOC, ClientChatChannelState.getSelectedChannel());
        ClientChatChannelState.select(ChatChannel.PROXIMITY);
        assertEquals(ChatChannel.OOC, ClientChatChannelState.getSelectedChannel());
    }

    @Test
    public void factionAvailabilityTracksTheActiveCharacter() {
        assertFalse(ClientChatChannelState.isAvailable(
                ChatChannel.FACTION));

        acceptRoster("lotr:gondor");
        assertTrue(ClientChatChannelState.isAvailable(
                ChatChannel.FACTION));
        ClientChatChannelState.select(ChatChannel.FACTION);

        acceptRoster("");
        assertEquals(ChatChannel.ALL,
                ClientChatChannelState.getSelectedChannel());
    }

    /**
     * The player keeps one tab they can see: without operator status
     * the Admin tab is hidden, so with only Global and Admin open,
     * Global is the last visible tab and cannot be closed even though
     * the layout alone would allow it.
     */
    @Test
    public void theLastVisibleTabCannotBeClosedEvenWhenHiddenTabsRemain() {
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (channel != ChatChannel.ALL && channel != ChatChannel.ADMIN) {
                assertTrue(ClientChatChannelState.close(ChatTab.of(channel)));
            }
        }
        assertEquals(2, ChatWindowLayout.openTabCount());
        assertEquals(Collections.singletonList(ChatChannel.ALL),
                ClientChatChannelState.getOpenChannels());
        assertTrue(ChatWindowLayout.isClosable(ChatChannel.ALL));
        assertFalse(ClientChatChannelState.isClosable(ChatTab.of(ChatChannel.ALL)));
        assertFalse(ClientChatChannelState.close(ChatTab.of(ChatChannel.ALL)));
        assertTrue(ChatWindowLayout.isOpen(ChatChannel.ALL));
        // Closing the selected tab moves the selection to what is left.
        ChatWindowLayout.restore(ChatChannel.OOC);
        ClientChatChannelState.select(ChatChannel.OOC);
        assertTrue(ClientChatChannelState.isClosable(ChatTab.of(ChatChannel.OOC)));
        assertTrue(ClientChatChannelState.close(ChatTab.of(ChatChannel.OOC)));
        assertEquals(ChatChannel.ALL, ClientChatChannelState.getSelectedChannel());
        // With operator status Admin is a tab the player could keep.
        ClientChatChannelState.setAdminAccess(true);
        assertTrue(ClientChatChannelState.isClosable(ChatTab.of(ChatChannel.ALL)));
    }

    /**
     * The feed reads every unmuted channel the player can see, closed
     * ones included; only muting removes a channel from it.
     */
    @Test
    public void theFeedShowsClosedChannelsUntilTheyAreMuted() {
        ChatTab ooc = ChatTab.of(ChatChannel.OOC);
        ChatTab faction = ChatTab.of(ChatChannel.FACTION);
        assertTrue(ChatWindowFrame.feedFilter().accepts(ooc));
        assertTrue(ChatWindowLayout.close(ChatChannel.OOC));
        assertTrue(ChatWindowFrame.feedFilter().accepts(ooc));
        ChatWindowLayout.setMuted(ChatChannel.OOC, true);
        assertFalse(ChatWindowFrame.feedFilter().accepts(ooc));
        assertTrue(ChatWindowLayout.restore(ChatChannel.OOC));
        assertFalse(ChatWindowFrame.feedFilter().accepts(ooc));
        ChatWindowLayout.setMuted(ChatChannel.OOC, false);
        assertTrue(ChatWindowFrame.feedFilter().accepts(ooc));
        // A channel the player cannot see is not in the feed, open or not.
        assertFalse(ChatWindowFrame.feedFilter().accepts(faction));
        acceptRoster("lotr:gondor");
        assertTrue(ChatWindowFrame.feedFilter().accepts(faction));
        assertTrue(ChatWindowLayout.close(ChatChannel.FACTION));
        assertTrue(ChatWindowFrame.feedFilter().accepts(faction));
        // Untracked lines ride with the console wherever, or whether, it
        // is placed.
        assertTrue(ChatWindowFrame.feedFilter().accepts(null));
        assertTrue(ChatWindowLayout.close(ChatChannel.CONSOLE));
        assertTrue(ChatWindowFrame.feedFilter().accepts(null));
        // Conversations are read from their open tabs only.
        ChatTab whisper = ChatWindowLayout.openWhisper("Bilbo", null);
        assertTrue(ChatWindowFrame.feedFilter().accepts(whisper));
        ChatWindowLayout.setMuted(whisper, true);
        assertFalse(ChatWindowFrame.feedFilter().accepts(whisper));
        // Muting mentions alone never touches the feed.
        ChatWindowLayout.setPingsMuted(ooc, true);
        assertTrue(ChatWindowFrame.feedFilter().accepts(ooc));
    }

    /**
     * Ctrl+Tab walks every open tab across windows, both ways, wrapping
     * at the ends; Tab on its own stays inside the selected window.
     */
    @Test
    public void cyclingAcrossWindowsFollowsTheLayoutOrder() {
        ClientChatChannelState.select(ChatChannel.CONSOLE);
        assertEquals(ChatChannel.CONSOLE,
                ClientChatChannelState.cycle().getChannel());
        assertEquals(ChatChannel.ALL,
                ClientChatChannelState.cycleAll(false).getChannel());
        assertEquals(ChatChannel.OOC,
                ClientChatChannelState.cycleAll(false).getChannel());
        assertEquals(ChatChannel.CONSOLE,
                ClientChatChannelState.cycleAll(false).getChannel());
        assertEquals(ChatChannel.OOC,
                ClientChatChannelState.cycleAll(true).getChannel());
        assertEquals(ChatChannel.ALL,
                ClientChatChannelState.cycleAll(true).getChannel());
        assertEquals(ChatChannel.CONSOLE,
                ClientChatChannelState.cycleAll(true).getChannel());
    }

    private static void acceptRoster(String factionId) {
        UUID ownerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        CharacterSummary character = new CharacterSummary(
                characterId, 0, "Arathorn", "human", "male",
                "human_male_0", 30, factionId, 1,
                0L, 1L, RoleplayCharacter.CURRENT_DATA_VERSION);
        CharacterRosterSnapshot snapshot = new CharacterRosterSnapshot(
                ownerId, 1, characterId, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION,
                Collections.singletonList(character));
        ClientCharacterRosterCache.acceptRoster(0, snapshot);
    }
}
