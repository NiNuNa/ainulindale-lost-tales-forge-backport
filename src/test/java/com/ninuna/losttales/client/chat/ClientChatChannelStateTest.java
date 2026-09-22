package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
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
        ClientChatIdentities.clear();
        ClientChatIdentitySelection.clear();
        ClientCharacterRosterCache.clear();
        ChatWindowLayout.reset();
    }

    @Test
    public void closingTheSelectedTabStaysInItsWindow() {
        // Proximity and OOC in a window of their own, Global elsewhere.
        ChatWindow own = ChatWindowLayout.detach(ChatChannel.PROXIMITY, 0.0D, 0.0D);
        assertTrue(ChatWindowLayout.moveTab(ChatChannel.OOC, own.getId(), 1));
        ClientChatChannelState.select(ChatChannel.OOC);
        assertTrue(ClientChatChannelState.close(ChatTab.of(ChatChannel.OOC)));
        assertEquals("the neighbour in the same window, not Global elsewhere",
                ChatChannel.PROXIMITY, ClientChatChannelState.getSelectedChannel());
        // Closing the window's last tab is the one case that leaves it.
        assertTrue(ClientChatChannelState.close(ChatTab.of(ChatChannel.PROXIMITY)));
        assertEquals(ChatChannel.ALL, ClientChatChannelState.getSelectedChannel());
    }

    @Test
    public void sentHistoryIsKeptPerTabAndClearedWithTheState() {
        ChatTab global = ChatTab.of(ChatChannel.ALL);
        ChatTab ooc = ChatTab.of(ChatChannel.OOC);
        ClientChatChannelState.recordSent(global, "Hi");
        assertEquals("Hi", ClientChatChannelState.recallSent(global, -1, ""));
        assertEquals(null, ClientChatChannelState.recallSent(ooc, -1, ""));
        ClientChatChannelState.endSentBrowse();
        ClientChatChannelState.clear();
        assertEquals(null, ClientChatChannelState.recallSent(global, -1, ""));
    }

    @Test
    public void closedChannelsAreNeverSelectedAndCycleFollowsTheLayout() {
        joinParty(acceptRoster("lotr:gondor"));
        ChatWindowLayout.detach(ChatChannel.PROXIMITY, 0.0D, 0.0D);
        assertEquals(java.util.Arrays.asList(ChatChannel.CONSOLE,
                ChatChannel.ALL, ChatChannel.FACTION, ChatChannel.OOC, ChatChannel.PARTY,
                ChatChannel.PROXIMITY),
                ClientChatChannelState.getOpenChannels());
        // Cycling stays within the window: Proximity is alone in its.
        ClientChatChannelState.select(ChatChannel.PROXIMITY);
        assertEquals(ChatChannel.PROXIMITY, ClientChatChannelState.cycle().getChannel());
        ClientChatChannelState.select(ChatChannel.OOC);
        assertEquals(ChatChannel.PARTY, ClientChatChannelState.cycle().getChannel());
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
    public void accountOnlyPlayersTalkAnywhereWithTheAccount() {
        // The Party tab is not there until the identity is in a party;
        // Faction is open to the account, which speaks in Unaligned.
        assertTrue(ClientChatChannelState.isAvailable(ChatChannel.ALL));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.ALL));
        assertTrue(ClientChatChannelState.isAvailable(
                ChatChannel.PROXIMITY));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.PROXIMITY));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.FACTION));
        assertFalse(ClientChatChannelState.isAvailable(ChatChannel.PARTY));
        assertFalse(ClientChatChannelState.canSend(ChatChannel.PARTY));
        assertTrue(ClientChatChannelState.isAvailable(ChatChannel.OOC));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.OOC));
        // The console is always there; Admin only once the server says so.
        assertEquals(java.util.Arrays.asList(ChatChannel.ALL,
                ChatChannel.PROXIMITY, ChatChannel.FACTION, ChatChannel.OOC,
                ChatChannel.CONSOLE),
                ClientChatChannelState.getAvailableChannels());
        // TAB cycles inside the selected channel's own window: Global,
        // OOC and Proximity share the conversation window, the console
        // lives elsewhere; the Party tab joins the round once joined.
        ClientChatChannelState.select(ChatChannel.OOC);
        assertEquals(ChatChannel.ALL, ClientChatChannelState.cycle().getChannel());
        assertEquals(ChatChannel.PROXIMITY,
                ClientChatChannelState.cycle().getChannel());
        assertEquals(ChatChannel.FACTION, ClientChatChannelState.cycle().getChannel());
        assertEquals(ChatChannel.OOC, ClientChatChannelState.cycle().getChannel());
        joinParty(null);
        assertTrue(ClientChatChannelState.isAvailable(ChatChannel.PARTY));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.PARTY));
        assertEquals(ChatChannel.PARTY, ClientChatChannelState.cycle().getChannel());
        ClientChatChannelState.select(ChatChannel.CONSOLE);
        assertEquals(ChatChannel.CONSOLE, ClientChatChannelState.cycle().getChannel());
        assertFalse(ClientChatChannelState.canSend(ChatChannel.ADMIN));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.CONSOLE));
        // The server's word arrives as channel ids: every channel open here.
        ClientChatChannelState.setChannelGates(allChannelIds(), allChannelIds());
        ClientChatChannelState.setAdminAccess(true);
        assertEquals(java.util.Arrays.asList(ChatChannel.ALL,
                ChatChannel.PROXIMITY, ChatChannel.FACTION, ChatChannel.OOC,
                ChatChannel.PARTY, ChatChannel.ADMIN,
                ChatChannel.CONSOLE, ChatChannel.SERVER_CONSOLE),
                ClientChatChannelState.getAvailableChannels());
        ClientChatChannelState.select(ChatChannel.ADMIN);
        ClientChatChannelState.setAdminAccess(false);
        ClientChatChannelState.setChannelGates(
                everyChannelExcept(ChatChannel.ADMIN),
                everyChannelExcept(ChatChannel.ADMIN));
        // Losing op status drops the selection back to a channel the
        // player can talk in: Global, sendable with the account now.
        assertEquals(ChatChannel.ALL, ClientChatChannelState.getSelectedChannel());
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
        assertEquals(ChatChannel.ALL, ClientChatChannelState.getSelectedChannel());
        ClientChatChannelState.select(ChatChannel.OOC);
        assertEquals(ChatChannel.OOC, ClientChatChannelState.getSelectedChannel());
        // Proximity is selectable without a character now.
        ClientChatChannelState.select(ChatChannel.PROXIMITY);
        assertEquals(ChatChannel.PROXIMITY,
                ClientChatChannelState.getSelectedChannel());
    }

    /**
     * No identity is in no faction: the account and a character created
     * without one speak in Unaligned, so the Faction tab is always open
     * and follows the faction of whoever is selected.
     */
    @Test
    public void theAccountAndAFactionlessCharacterSpeakInUnaligned() {
        assertTrue(ClientChatChannelState.isAvailable(ChatChannel.FACTION));
        assertEquals(LotrCharacterAdapter.UNALIGNED_FACTION_ID,
                ClientChatChannelState.wornFactionId(ChatChannel.FACTION));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.FACTION));
        acceptRoster("lotr:gondor");
        ClientChatChannelState.select(ChatChannel.FACTION);
        assertEquals("lotr:gondor",
                ClientChatChannelState.wornFactionId(ChatChannel.FACTION));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.FACTION));
        acceptRoster("");
        ClientChatChannelState.ensureAvailable();
        assertEquals(ChatChannel.FACTION, ClientChatChannelState.getSelectedChannel());
        assertEquals(LotrCharacterAdapter.UNALIGNED_FACTION_ID,
                ClientChatChannelState.wornFactionId(ChatChannel.FACTION));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.FACTION));
    }

    /**
     * Every tab the player can see closes, the last one included, and
     * the state that leaves — no visible window at all — is a state the
     * chat reports rather than one it prevents. Without operator status
     * the Admin tab is hidden, so a window holding only Admin is a
     * window with nothing to show.
     */
    @Test
    public void everyVisibleTabClosesAndTheEmptyStateIsReported() {
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (channel != ChatChannel.ALL && channel != ChatChannel.ADMIN) {
                assertTrue(ClientChatChannelState.close(ChatTab.of(channel)));
            }
        }
        assertEquals(2, ChatWindowLayout.openTabCount());
        assertEquals(Collections.singletonList(ChatChannel.ALL),
                ClientChatChannelState.getOpenChannels());
        assertTrue(ClientChatChannelState.isClosable(ChatTab.of(ChatChannel.ALL)));
        assertTrue(ClientChatChannelState.close(ChatTab.of(ChatChannel.ALL)));
        assertFalse(ChatWindowLayout.isOpen(ChatChannel.ALL));
        assertFalse(ClientChatChannelState.hasVisibleWindow());
        // Reopening one makes the chat visible again, and closing the
        // selected tab moves the selection to what is left.
        assertTrue(ChatWindowLayout.restore(ChatChannel.ALL));
        assertTrue(ChatWindowLayout.restore(ChatChannel.OOC));
        assertTrue(ClientChatChannelState.hasVisibleWindow());
        ClientChatChannelState.select(ChatChannel.OOC);
        assertTrue(ClientChatChannelState.close(ChatTab.of(ChatChannel.OOC)));
        assertEquals(ChatChannel.ALL, ClientChatChannelState.getSelectedChannel());
    }

    /**
     * The feed reads every unmuted channel the player can see, closed
     * ones included; only muting removes a channel from it.
     */
    @Test
    public void theFeedShowsClosedChannelsUntilTheyAreMuted() {
        ChatTab ooc = ChatTab.of(ChatChannel.OOC);
        ChatTab faction = ChatTab.of(ChatChannel.FACTION, "lotr:gondor");
        assertTrue(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(ooc));
        assertTrue(ChatWindowLayout.close(ChatChannel.OOC));
        assertTrue(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(ooc));
        ChatWindowLayout.setMuted(ChatChannel.OOC, true);
        assertFalse(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(ooc));
        assertTrue(ChatWindowLayout.restore(ChatChannel.OOC));
        assertFalse(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(ooc));
        ChatWindowLayout.setMuted(ChatChannel.OOC, false);
        assertTrue(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(ooc));
        // A channel the player cannot see is not in the feed, open or not.
        assertFalse(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(faction));
        acceptRoster("lotr:gondor");
        assertTrue(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(faction));
        assertTrue(ChatWindowLayout.close(ChatChannel.FACTION));
        assertTrue(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(faction));
        // Untracked lines ride with the console wherever, or whether, it
        // is placed.
        assertTrue(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(null));
        assertTrue(ChatWindowLayout.close(ChatChannel.CONSOLE));
        assertTrue(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(null));
        // Conversations are read from their open tabs only.
        ChatTab whisper = ChatWindowLayout.openWhisper("Bilbo", null);
        assertTrue(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(whisper));
        ChatWindowLayout.setMuted(whisper, true);
        assertFalse(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(whisper));
        // Muting mentions alone never touches the feed.
        ChatWindowLayout.setPingsMuted(ooc, true);
        assertTrue(ChatLineFilter.of(ChatWindowFrame.feedTabs()).accepts(ooc));
    }

    /**
     * Ctrl+1 to Ctrl+8 pick the selected window's tabs by place, Ctrl+9
     * its last; a number past the row changes nothing, and a window of
     * one tab answers every number with that tab.
     */
    @Test
    public void aDigitPicksTheSelectedWindowsTabByPlace() {
        joinParty(null);
        ClientChatChannelState.select(ChatChannel.OOC);
        assertEquals(ChatChannel.ALL,
                ClientChatChannelState.selectOrdinal(1).getChannel());
        assertEquals(ChatChannel.PROXIMITY,
                ClientChatChannelState.selectOrdinal(2).getChannel());
        assertEquals(ChatChannel.FACTION,
                ClientChatChannelState.selectOrdinal(3).getChannel());
        assertEquals(ChatChannel.ALL,
                ClientChatChannelState.selectOrdinal(1).getChannel());
        // Past the row: nothing moves. Nine: the last, whatever the row holds.
        assertEquals(ChatChannel.ALL,
                ClientChatChannelState.selectOrdinal(6).getChannel());
        assertEquals(ChatChannel.PARTY,
                ClientChatChannelState.selectOrdinal(9).getChannel());
        assertEquals(ChatChannel.PARTY,
                ClientChatChannelState.selectOrdinal(0).getChannel());
        ClientChatChannelState.select(ChatChannel.CONSOLE);
        assertEquals(ChatChannel.CONSOLE,
                ClientChatChannelState.selectOrdinal(1).getChannel());
        assertEquals(ChatChannel.CONSOLE,
                ClientChatChannelState.selectOrdinal(9).getChannel());
        assertEquals(ChatChannel.CONSOLE,
                ClientChatChannelState.selectOrdinal(3).getChannel());
    }

    /**
     * Ctrl+Tab walks every open tab across windows, both ways, wrapping
     * at the ends; Tab on its own stays inside the selected window.
     */
    @Test
    public void cyclingAcrossWindowsFollowsTheLayoutOrder() {
        joinParty(null);
        ClientChatChannelState.select(ChatChannel.CONSOLE);
        assertEquals(ChatChannel.CONSOLE,
                ClientChatChannelState.cycle().getChannel());
        assertEquals(ChatChannel.ALL,
                ClientChatChannelState.cycleAll(false).getChannel());
        assertEquals(ChatChannel.PROXIMITY,
                ClientChatChannelState.cycleAll(false).getChannel());
        assertEquals(ChatChannel.FACTION,
                ClientChatChannelState.cycleAll(false).getChannel());
        assertEquals(ChatChannel.OOC,
                ClientChatChannelState.cycleAll(false).getChannel());
        assertEquals(ChatChannel.PARTY,
                ClientChatChannelState.cycleAll(false).getChannel());
        assertEquals(ChatChannel.CONSOLE,
                ClientChatChannelState.cycleAll(false).getChannel());
        assertEquals(ChatChannel.PARTY,
                ClientChatChannelState.cycleAll(true).getChannel());
        assertEquals(ChatChannel.OOC,
                ClientChatChannelState.cycleAll(true).getChannel());
        assertEquals(ChatChannel.FACTION,
                ClientChatChannelState.cycleAll(true).getChannel());
        assertEquals(ChatChannel.PROXIMITY,
                ClientChatChannelState.cycleAll(true).getChannel());
        assertEquals(ChatChannel.ALL,
                ClientChatChannelState.cycleAll(true).getChannel());
        assertEquals(ChatChannel.CONSOLE,
                ClientChatChannelState.cycleAll(true).getChannel());
    }

    /**
     * A conversation held as one character is on screen only while that
     * character is played; the person's row entry is always there and
     * shows that conversation, the account's while no character is.
     * Its partner's character is remembered for the reply, and
     * forgotten with the world.
     */
    @Test
    public void conversationsShowForTheIdentityTheyAreHeldAs() {
        UUID played = acceptRoster("GONDOR");
        ChatTab asPlayed = ChatTab.whisper("Steve", "Aldric", ChatTab.ownerKeyOf(played));
        ChatTab asOther = ChatTab.whisper("Steve", "Aldric",
                ChatTab.ownerKeyOf(UUID.randomUUID()));
        ChatTab asAccount = ChatTab.whisper("Steve", "Aldric");
        assertTrue(ClientChatChannelState.isAvailable(asPlayed));
        assertFalse(ClientChatChannelState.isAvailable(asOther));
        // The person's row entry is always there; what it shows is the
        // conversation held as the identity being read.
        assertTrue(ClientChatChannelState.isAvailable(asAccount));
        assertEquals(asPlayed, ChatTab.viewed(asAccount));
        assertEquals(asAccount, ChatTab.row(asPlayed));
        assertTrue(ClientChatChannelState.isAvailable(ChatTab.npc("Steve")));
        ClientCharacterRosterCache.clear();
        assertTrue(ClientChatChannelState.isAvailable(asAccount));
        assertEquals(asAccount, ChatTab.viewed(asAccount));
        assertFalse(ClientChatChannelState.isAvailable(asPlayed));

        UUID aldric = UUID.randomUUID();
        assertEquals(null, ClientChatChannelState.partnerCharacterIdOf(asAccount));
        ClientChatChannelState.rememberPartnerCharacterId(asAccount, aldric);
        assertEquals(aldric, ClientChatChannelState.partnerCharacterIdOf(asAccount));
        // The partner's character is the person's, whichever identity the
        // conversation is held as.
        assertEquals(aldric, ClientChatChannelState.partnerCharacterIdOf(asPlayed));
        ClientChatChannelState.rememberPartnerCharacterId(asAccount, null);
        assertEquals(null, ClientChatChannelState.partnerCharacterIdOf(asAccount));
        ClientChatChannelState.rememberPartnerCharacterId(asAccount, aldric);
        ClientChatChannelState.rememberPartnerCharacterId(ChatTab.of(ChatChannel.ALL), aldric);
        assertEquals(null, ClientChatChannelState.partnerCharacterIdOf(
                ChatTab.of(ChatChannel.ALL)));
        ClientChatChannelState.clear();
        assertEquals(null, ClientChatChannelState.partnerCharacterIdOf(asAccount));
    }

    /** The server's word that the selected identity is in a party. */
    private static void joinParty(UUID characterId) {
        ClientChatIdentitySelection.accept(new LostTalesChatIdentitySyncPacket(
                characterId, new UUID(9L, 9L), 0x123456, "Aldric", false));
    }

    private static UUID acceptRoster(String factionId) {
        UUID ownerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        CharacterSummary character = new CharacterSummary(
                characterId, 0, "Arathorn", "human", "male",
                "human_male_0", RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, 30, factionId, 1,
                0L, 1L, RoleplayCharacter.CURRENT_DATA_VERSION, "", "", "");
        CharacterRosterSnapshot snapshot = new CharacterRosterSnapshot(
                ownerId, 1, characterId, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION,
                Collections.singletonList(character),
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, true);
        ClientCharacterRosterCache.acceptRoster(0, snapshot);
        return characterId;
    }

    /** Every channel this build knows, as the access packet states them. */
    private static java.util.List<String> allChannelIds() {
        java.util.List<String> ids = new java.util.ArrayList<String>();
        for (ChatChannel channel : ChatChannel.values()) {
            ids.add(channel.getId());
        }
        return ids;
    }

    /** Every channel but the one named, for a gate that closes just it. */
    private static java.util.List<String> everyChannelExcept(ChatChannel closed) {
        java.util.List<String> ids = allChannelIds();
        ids.remove(closed.getId());
        return ids;
    }
}
