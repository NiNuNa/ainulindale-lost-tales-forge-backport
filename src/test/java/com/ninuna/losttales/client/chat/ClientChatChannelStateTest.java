package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatFellowship;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.window.Tearing;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ClientChatChannelStateTest {

    @Before
    public void twoWindows() {
        TwoWindowLayout.reset();
    }

    @After
    public void cleanUp() {
        ClientChatChannelState.clear();
        ClientChatIdentities.clear();
        ClientChatIdentitySelection.clear();
        ClientCharacterRosterCache.clear();
        TwoWindowLayout.reset();
    }

    @Test
    public void closingTheSelectedTabStaysInItsWindow() {
        // Proximity and OOC in a window of their own, Global elsewhere.
        Window own = Tearing.off(ConversationPage.of(ChatChannel.PROXIMITY), 0.0D, 0.0D);
        assertTrue(WindowLayout.moveTab(ConversationPage.of(ChatChannel.OOC), own.getId(), 1));
        ClientChatChannelState.select(ConversationPage.of(ChatChannel.OOC));
        assertTrue(ClientChatChannelState.close(ConversationPage.of(ChatChannel.OOC)));
        assertEquals("the neighbour in the same window, not Global elsewhere",
                ChatChannel.PROXIMITY, ClientChatChannelState.getSelected().getChannel());
        // Closing the window's last tab is the one case that leaves it.
        assertTrue(ClientChatChannelState.close(ConversationPage.of(ChatChannel.PROXIMITY)));
        assertEquals(ChatChannel.GLOBAL, ClientChatChannelState.getSelected().getChannel());
    }

    /**
     * A console is never the conversation last used: the consoles have a
     * view of their own, and the chat's key comes back to the
     * conversation used before them, or to one picked since.
     */
    @Test
    public void theChatKeyComesBackToTheConversationUsedBeforeTheConsole() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);
        ConversationPage console = ConversationPage.of(ChatChannel.CLIENT_CONSOLE);
        ClientChatChannelState.select(global);
        ClientChatChannelState.select(console);
        assertEquals(console, ClientChatChannelState.getSelected());
        assertEquals(global, ClientChatChannelState.lastUsed());
        ClientChatChannelState.select(ooc);
        ClientChatChannelState.select(console);
        assertEquals(ooc, ClientChatChannelState.lastUsed());
        assertTrue(console.isConsole());
        assertFalse(ooc.isConsole());
    }

    @Test
    public void sentHistoryIsKeptPerTabAndClearedWithTheState() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);
        ClientChatChannelState.recordSent(global, "Hi");
        assertEquals("Hi", ClientChatChannelState.recallSent(global, -1, ""));
        assertEquals(null, ClientChatChannelState.recallSent(ooc, -1, ""));
        ClientChatChannelState.endSentBrowse();
        ClientChatChannelState.clear();
        assertEquals(null, ClientChatChannelState.recallSent(global, -1, ""));
    }

    @Test
    public void closedChannelsAreNeverSelected() {
        joinFellowship(acceptRoster("lotr:gondor"));
        Tearing.off(ConversationPage.of(ChatChannel.PROXIMITY), 0.0D, 0.0D);
        // The Fellowship channel has no plain tab: each fellowship's
        // conversation is a tab of its own.
        assertEquals(java.util.Arrays.asList(ChatChannel.CLIENT_CONSOLE,
                ChatChannel.GLOBAL, ChatChannel.FACTION, ChatChannel.OOC,
                ChatChannel.PROXIMITY),
                openChannels());
        // A closed channel stays available (readable) but not selectable.
        ClientChatChannelState.select(ConversationPage.of(ChatChannel.OOC));
        ChatLayout.close(ConversationPage.of(ChatChannel.OOC));
        assertTrue(ClientChatChannelState.isAvailable(ChatChannel.OOC));
        assertFalse(ClientChatChannelState.isSelectable(ConversationPage.of(ChatChannel.OOC)));
        assertEquals(ChatChannel.GLOBAL, ClientChatChannelState.getSelected().getChannel());
        ClientChatChannelState.select(ConversationPage.of(ChatChannel.OOC));
        assertEquals(ChatChannel.GLOBAL, ClientChatChannelState.getSelected().getChannel());
        // Without a character and with Global closed, the fallback is OOC
        // (account conversation) even though it now sits after Console.
        ChatLayoutViews.reopen(ChatChannel.OOC);
        ChatLayout.close(ConversationPage.of(ChatChannel.GLOBAL));
        acceptRoster("");
        ClientChatChannelState.ensureAvailable();
        assertEquals(ChatChannel.OOC, ClientChatChannelState.getSelected().getChannel());
    }

    @Test
    public void accountOnlyPlayersTalkAnywhereWithTheAccount() {
        // The Fellowship tab is not there until the identity is in a fellowship;
        // Faction is open to the account, which speaks in Unaligned.
        assertTrue(ClientChatChannelState.isAvailable(ChatChannel.GLOBAL));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.GLOBAL));
        assertTrue(ClientChatChannelState.isAvailable(
                ChatChannel.PROXIMITY));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.PROXIMITY));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.FACTION));
        assertFalse(ClientChatChannelState.isAvailable(ChatChannel.FELLOWSHIP));
        assertFalse(ClientChatChannelState.canSend(ChatChannel.FELLOWSHIP));
        assertTrue(ClientChatChannelState.isAvailable(ChatChannel.OOC));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.OOC));
        // The console is always there; Admin only once the server says so.
        assertEquals(java.util.Arrays.asList(ChatChannel.GLOBAL,
                ChatChannel.PROXIMITY, ChatChannel.FACTION, ChatChannel.OOC,
                ChatChannel.CLIENT_CONSOLE),
                availableChannels());
        // The Fellowship tab is there once the character played joins one.
        joinFellowship(null);
        assertTrue(ClientChatChannelState.isAvailable(ChatChannel.FELLOWSHIP));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.FELLOWSHIP));
        assertFalse(ClientChatChannelState.canSend(ChatChannel.OPERATOR));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.CLIENT_CONSOLE));
        // The server's word arrives as channel ids: every channel open here.
        ClientChatChannelState.setChannelGates(allChannelIds(), allChannelIds());
        assertEquals(java.util.Arrays.asList(ChatChannel.GLOBAL,
                ChatChannel.PROXIMITY, ChatChannel.FACTION, ChatChannel.OOC,
                ChatChannel.FELLOWSHIP, ChatChannel.OPERATOR,
                ChatChannel.CLIENT_CONSOLE, ChatChannel.SERVER_CONSOLE),
                availableChannels());
        ClientChatChannelState.select(ConversationPage.of(ChatChannel.OPERATOR));
        ClientChatChannelState.setChannelGates(
                everyChannelExcept(ChatChannel.OPERATOR),
                everyChannelExcept(ChatChannel.OPERATOR));
        // Losing op status drops the selection back to a channel the
        // player can talk in: Global, sendable with the account now.
        assertEquals(ChatChannel.GLOBAL, ClientChatChannelState.getSelected().getChannel());
        ClientChatChannelState.setDraft("unsent text");
        assertEquals("unsent text", ClientChatChannelState.getDraft());
        // Drafts belong to the tab they were typed in.
        ConversationPage alex = ConversationPage.whisper("Alex", "");
        ClientChatChannelState.setDraft(alex, "for alex");
        assertEquals("unsent text", ClientChatChannelState.getDraft());
        assertEquals("for alex", ClientChatChannelState.getDraft(alex));
        assertEquals("for alex",
                ClientChatChannelState.getDraft(ConversationPage.whisper("alex", "")));
        ClientChatChannelState.setDraft(alex, "");
        assertEquals("", ClientChatChannelState.getDraft(alex));
        assertEquals("unsent text", ClientChatChannelState.getDraft());
        ClientChatChannelState.clear();
        assertEquals("", ClientChatChannelState.getDraft());
        assertEquals(ChatChannel.GLOBAL, ClientChatChannelState.getSelected().getChannel());
        ClientChatChannelState.select(ConversationPage.of(ChatChannel.OOC));
        assertEquals(ChatChannel.OOC, ClientChatChannelState.getSelected().getChannel());
        // Proximity is selectable without a character now.
        ClientChatChannelState.select(ConversationPage.of(ChatChannel.PROXIMITY));
        assertEquals(ChatChannel.PROXIMITY,
                ClientChatChannelState.getSelected().getChannel());
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
        ClientChatChannelState.select(ConversationPage.of(ChatChannel.FACTION));
        assertEquals("lotr:gondor",
                ClientChatChannelState.wornFactionId(ChatChannel.FACTION));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.FACTION));
        acceptRoster("");
        ClientChatChannelState.ensureAvailable();
        assertEquals(ChatChannel.FACTION, ClientChatChannelState.getSelected().getChannel());
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
            if (channel != ChatChannel.GLOBAL && channel != ChatChannel.OPERATOR) {
                assertTrue(ClientChatChannelState.close(ConversationPage.of(channel)));
            }
        }
        assertEquals(2, WindowLayout.order().size());
        assertEquals(Collections.singletonList(ChatChannel.GLOBAL),
                openChannels());
        assertTrue(ClientChatChannelState.isClosable(ConversationPage.of(ChatChannel.GLOBAL)));
        assertTrue(ClientChatChannelState.close(ConversationPage.of(ChatChannel.GLOBAL)));
        assertFalse(ChatLayout.isOpen(ChatChannel.GLOBAL));
        // Reopening one makes the chat visible again, and closing the
        // selected tab moves the selection to what is left.
        assertTrue(ChatLayoutViews.reopen(ChatChannel.GLOBAL));
        assertTrue(ChatLayoutViews.reopen(ChatChannel.OOC));
        ClientChatChannelState.select(ConversationPage.of(ChatChannel.OOC));
        assertTrue(ClientChatChannelState.close(ConversationPage.of(ChatChannel.OOC)));
        assertEquals(ChatChannel.GLOBAL, ClientChatChannelState.getSelected().getChannel());
    }

    /**
     * The feed reads every channel the player can see whose feed choice
     * lets its lines through, closed ones included: every line where it
     * is Everything, a line addressed to the player where it is Only
     * Mentions, none where it is Nothing. Notifications has no say in it.
     */
    @Test
    public void theFeedShowsClosedChannelsAsShowInFeedAllows() {
        ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);
        ConversationPage faction = ConversationPage.of(ChatChannel.FACTION, "lotr:gondor");
        assertTrue(ChatLineFilter.of(ChatLayout.feedTabs(),
                java.util.Collections.<ConversationPage>emptySet()).accepts(ooc));
        assertTrue(ChatLayout.close(ConversationPage.of(ChatChannel.OOC)));
        assertTrue(ChatLineFilter.of(ChatLayout.feedTabs(),
                java.util.Collections.<ConversationPage>emptySet()).accepts(ooc));
        ChatLayout.setNotification(ConversationPage.of(ChatChannel.OOC), ChatLineChoice.NOTHING);
        assertTrue(ChatLayout.feedFilter().accepts(ooc, false));
        ChatLayout.setFeedChoice(ConversationPage.of(ChatChannel.OOC), ChatLineChoice.NOTHING);
        assertFalse(ChatLayout.feedFilter().accepts(ooc, false));
        assertFalse(ChatLayout.feedFilter().accepts(ooc, true));
        assertTrue(ChatLayoutViews.reopen(ChatChannel.OOC));
        assertFalse(ChatLayout.feedFilter().accepts(ooc, true));
        ChatLayout.setFeedChoice(ConversationPage.of(ChatChannel.OOC),
                ChatLineChoice.ONLY_MENTIONS);
        assertFalse(ChatLayout.feedFilter().accepts(ooc, false));
        assertTrue(ChatLayout.feedFilter().accepts(ooc, true));
        assertFalse(ChatLayout.feedTabs().contains(ooc));
        assertTrue(ChatLayout.mentionFeedTabs().contains(ooc));
        ChatLayout.setFeedChoice(ConversationPage.of(ChatChannel.OOC),
                ChatLineChoice.EVERYTHING);
        assertTrue(ChatLayout.feedFilter().accepts(ooc, false));
        assertTrue(ChatLineFilter.of(ChatLayout.feedTabs(),
                java.util.Collections.<ConversationPage>emptySet()).accepts(ooc));
        // A channel the player cannot see is not in the feed, open or not.
        assertFalse(ChatLineFilter.of(ChatLayout.feedTabs(),
                java.util.Collections.<ConversationPage>emptySet()).accepts(faction));
        acceptRoster("lotr:gondor");
        assertTrue(ChatLineFilter.of(ChatLayout.feedTabs(),
                java.util.Collections.<ConversationPage>emptySet()).accepts(faction));
        assertTrue(ChatLayout.close(ConversationPage.of(ChatChannel.FACTION)));
        assertTrue(ChatLineFilter.of(ChatLayout.feedTabs(),
                java.util.Collections.<ConversationPage>emptySet()).accepts(faction));
        // Untracked lines ride with the console wherever, or whether, it
        // is placed.
        assertTrue(ChatLineFilter.of(ChatLayout.feedTabs(),
                java.util.Collections.<ConversationPage>emptySet()).accepts(null));
        assertTrue(ChatLayout.close(ConversationPage.of(ChatChannel.CLIENT_CONSOLE)));
        assertTrue(ChatLineFilter.of(ChatLayout.feedTabs(),
                java.util.Collections.<ConversationPage>emptySet()).accepts(null));
        // Conversations are read from their open tabs only.
        ConversationPage whisper = ChatLayout.openWhisper("Bilbo", "", null);
        assertTrue(ChatLineFilter.of(ChatLayout.feedTabs(),
                java.util.Collections.<ConversationPage>emptySet()).accepts(whisper));
        ChatLayout.setFeedChoice(whisper, ChatLineChoice.ONLY_MENTIONS);
        assertFalse(ChatLayout.feedFilter().accepts(whisper, false));
        assertTrue(ChatLayout.feedFilter().accepts(whisper, true));
        ChatLayout.setFeedChoice(whisper, ChatLineChoice.NOTHING);
        assertFalse(ChatLayout.feedFilter().accepts(whisper, true));
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
        ConversationPage asPlayed = ConversationPage.whisper("Steve", "Aldric", ConversationPage.ownerKeyOf(played));
        ConversationPage asOther = ConversationPage.whisper("Steve", "Aldric",
                ConversationPage.ownerKeyOf(UUID.randomUUID()));
        ConversationPage asAccount = ConversationPage.whisper("Steve", "Aldric");
        assertTrue(ClientChatChannelState.isAvailable(asPlayed));
        assertFalse(ClientChatChannelState.isAvailable(asOther));
        // The person's row entry is always there; what it shows is the
        // conversation held as the identity being read.
        assertTrue(ClientChatChannelState.isAvailable(asAccount));
        assertEquals(asPlayed, ConversationPage.viewed(asAccount));
        assertEquals(asAccount, ConversationPage.row(asPlayed));
        assertTrue(ClientChatChannelState.isAvailable(ConversationPage.npc("Steve")));
        ClientCharacterRosterCache.clear();
        assertTrue(ClientChatChannelState.isAvailable(asAccount));
        assertEquals(asAccount, ConversationPage.viewed(asAccount));
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
        ClientChatChannelState.rememberPartnerCharacterId(ConversationPage.of(ChatChannel.GLOBAL), aldric);
        assertEquals(null, ClientChatChannelState.partnerCharacterIdOf(
                ConversationPage.of(ChatChannel.GLOBAL)));
        ClientChatChannelState.clear();
        assertEquals(null, ClientChatChannelState.partnerCharacterIdOf(asAccount));
    }

    /** The server's word that the selected identity is in a fellowship. */
    private static void joinFellowship(UUID characterId) {
        ClientChatIdentitySelection.accept(new LostTalesChatIdentitySyncPacket(
                characterId, Collections.singletonList(new ChatFellowship(
                        new UUID(9L, 9L), "Grey Company", 0x123456)), false));
    }

    private static UUID acceptRoster(String factionId) {
        UUID ownerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        CharacterSummary character = new CharacterSummary(
                characterId, 0, "Arathorn", "human", "male",
                "human_male_0", RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, 30, factionId,
                "", "");
        CharacterRosterSnapshot snapshot = new CharacterRosterSnapshot(
                ownerId, 1, characterId, 1L,
                Collections.singletonList(character),
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, true,
                Collections.<com.ninuna.losttales.character.sync.DeletedCharacterSummary>emptyList());
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

    /** Available channels in presentation order (plain tabs only). */
    private static List<ChatChannel> availableChannels() {
        List<ChatChannel> result = new java.util.ArrayList<ChatChannel>();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (ClientChatChannelState.isAvailable(channel)) {
                result.add(channel);
            }
        }
        return result;
    }

    /** The channels of the open, available tabs, in window and tab order. */
    private static List<ChatChannel> openChannels() {
        List<ChatChannel> result = new java.util.ArrayList<ChatChannel>();
        for (ConversationPage tab : ClientChatChannelState.getOpenTabs()) {
            result.add(tab.getChannel());
        }
        return result;
    }
}
