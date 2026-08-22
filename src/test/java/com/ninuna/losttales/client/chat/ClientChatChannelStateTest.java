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
