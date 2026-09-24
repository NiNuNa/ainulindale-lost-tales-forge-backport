package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.junit.After;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatTypingSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Reading the chat as one of the player's identities. The identity
 * picked decides which conversations are on screen — a faction's talk
 * and a whisper alike — and never decides which character is being
 * played: that is the character screen's alone.
 */
public final class ChatIdentityViewTest {

    private static final UUID ALDRIC =
            UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID BEREN =
            UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final String GONDOR = "lotr:gondor";
    private static final String ROHAN = "lotr:rohan";

    @After
    public void tearDown() {
        ClientChatIdentities.clear();
        ClientCharacterRosterCache.clear();
        ClientChatChannelViews.clear();
        ClientChatChannelState.clear();
        ClientChatTypingState.clear();
        ChatSpeechBubbles.clear();
        ChatWindowLayout.reset();
    }

    @Test
    public void partyMembershipFollowsTheChatIdentityAndIgnoresLateReplies() {
        roster();
        UUID firstParty = new UUID(1L, 1L);
        UUID secondParty = new UUID(2L, 2L);
        ClientChatIdentitySelection.accept(new LostTalesChatIdentitySyncPacket(ALDRIC, firstParty, 0x123456, "Aldric", false));
        assertEquals(firstParty.toString(), ClientChatChannelState.scopeKeyRead(ChatChannel.PARTY));
        ClientChatIdentities.select(identityOf(BEREN));
        assertEquals("", ClientChatChannelState.scopeKeyRead(ChatChannel.PARTY));
        assertFalse(ClientChatChannelState.canSend(ChatChannel.PARTY));
        ClientChatIdentitySelection.accept(new LostTalesChatIdentitySyncPacket(ALDRIC, firstParty, 0x123456, "Aldric", false));
        assertEquals("", ClientChatChannelState.scopeKeyRead(ChatChannel.PARTY));
        ClientChatIdentitySelection.accept(new LostTalesChatIdentitySyncPacket(BEREN, secondParty, 0xABCDEF, "Beren", false));
        assertEquals(secondParty.toString(), ClientChatChannelState.scopeKeyRead(ChatChannel.PARTY));
        assertEquals(0xABCDEF, ClientChatIdentitySelection.partyColor());
        assertTrue(ClientChatChannelState.canSend(ChatChannel.PARTY));
        assertFalse(ClientChatChannelState.isAvailable(ChatTab.of(ChatChannel.PARTY, firstParty.toString())));
        assertFalse(ClientChatChannelState.canSend(ChatTab.of(ChatChannel.PARTY, firstParty.toString())));
        assertTrue(ClientChatChannelState.isAvailable(ChatTab.of(ChatChannel.PARTY, secondParty.toString())));
        assertEquals(ALDRIC, ClientCharacterRosterCache.getSnapshot().getActiveCharacterId());
    }

    /** With no character held the chat falls back to the account, whose party is its own. */
    @Test
    public void accountPartyIsSeparateAndDisconnectDropsMembership() {
        UUID party = new UUID(3L, 3L);
        ClientChatIdentitySelection.accept(new LostTalesChatIdentitySyncPacket(null, party, 0x123456, "Steve", false));
        assertEquals(party.toString(), ClientChatChannelState.scopeOfIdentity(ChatChannel.PARTY, ""));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.PARTY));
        ClientChatIdentitySelection.clear();
        assertFalse(ClientChatChannelState.canSend(ChatChannel.PARTY));
    }

    /**
     * A party has no name of its own, so its tab wears its leader's:
     * "Aldric's Party" while the selected identity is in Aldric's party,
     * the plain channel name otherwise, and the name goes with the
     * membership when another identity is selected.
     */
    @Test
    public void thePartyTabIsNamedAfterItsLeader() {
        roster();
        String plain = ChatChannel.PARTY.getDisplayName();
        assertEquals(plain, ClientChatChannelState.displayName(ChatChannel.PARTY));
        ClientChatIdentitySelection.accept(new LostTalesChatIdentitySyncPacket(
                ALDRIC, new UUID(4L, 4L), 0x123456, "Aldric", false));
        assertEquals("Aldric", ClientChatIdentitySelection.partyLeader());
        assertNotEquals(plain, ClientChatChannelState.displayName(ChatChannel.PARTY));
        ClientChatIdentities.select(identityOf(BEREN));
        assertEquals("", ClientChatIdentitySelection.partyLeader());
        assertEquals(plain, ClientChatChannelState.displayName(ChatChannel.PARTY));
    }

    @Test
    public void factionMessagesAndTypingOnlyAppearForTheSelectedIdentity() {
        roster();
        ChatTab gondor = ChatTab.of(ChatChannel.FACTION, GONDOR);
        ChatTab rohan = ChatTab.of(ChatChannel.FACTION, ROHAN);
        assertTrue(ClientChatChannelState.isAvailable(gondor));
        assertFalse(ClientChatChannelState.isAvailable(rohan));
        ClientChatIdentities.select(identityOf(BEREN));
        assertFalse(ClientChatChannelState.isAvailable(gondor));
        assertTrue(ClientChatChannelState.isAvailable(rohan));
        ClientChatTypingState.accept(new LostTalesChatTypingSyncPacket(ChatChannel.FACTION, "", "A friend", true,
                        GONDOR, ALDRIC.toString()));
        assertTrue(ClientChatTypingState.namesTyping(ChatTab.of(ChatChannel.FACTION)).isEmpty());
        ClientChatTypingState.accept(new LostTalesChatTypingSyncPacket(ChatChannel.FACTION, "", "Beren's friend", true,
                        ROHAN, BEREN.toString()));
        assertEquals(java.util.Collections.singletonList("Beren's friend"),
                ClientChatTypingState.namesTyping(ChatTab.of(ChatChannel.FACTION)));
    }

    /**
     * One tab per person, as one Faction tab: read as Aldric it shows the
     * conversation Steve has with Aldric, read as Beren the one with
     * Beren, and Aldric's lines stay out of Beren's view.
     */
    @Test
    public void aWhisperTabFollowsTheIdentityBeingRead() {
        roster();
        ChatTab row = ChatTab.whisper("Steve", "Steve");
        ChatTab withAldric = ChatTab.whisper("Steve", "Steve", keyOf(ALDRIC));
        ChatTab withBeren = ChatTab.whisper("Steve", "Steve", keyOf(BEREN));
        assertEquals(withAldric, ChatTab.viewed(row));
        assertTrue(ChatLineFilter.of(row).accepts(withAldric));
        assertFalse(ChatLineFilter.of(row).accepts(withBeren));
        ClientChatIdentities.select(identityOf(BEREN));
        assertEquals(withBeren, ChatTab.viewed(row));
        assertTrue(ChatLineFilter.of(row).accepts(withBeren));
        assertFalse(ChatLineFilter.of(row).accepts(withAldric));
        assertTrue(ClientChatChannelState.isAvailable(row));
        assertFalse(ClientChatChannelState.isAvailable(withAldric));
    }

    @Test
    public void whisperTypingBelongsToBothCharactersInThatConversation() {
        roster();
        ClientChatIdentities.select(identityOf(BEREN));
        ClientChatTypingState.accept(new LostTalesChatTypingSyncPacket(ChatChannel.WHISPER,
                "Steve", "Friend", true, "", ALDRIC.toString()));
        ChatTab beren = ChatTab.whisper("Steve", "Friend", BEREN.toString());
        assertTrue(ClientChatTypingState.namesTyping(beren).isEmpty());
        ClientChatTypingState.accept(new LostTalesChatTypingSyncPacket(ChatChannel.WHISPER,
                "Steve", "Friend", true, "", BEREN.toString()));
        assertEquals(java.util.Collections.singletonList("Friend"),
                ClientChatTypingState.namesTyping(beren));
        assertTrue(ClientChatTypingState.namesTyping(
                ChatTab.whisper("Steve", "Another character", BEREN.toString())).isEmpty());
    }

    /**
     * The last channel stays selected across an identity switch: Faction
     * follows the new identity's faction rather than moving, while the
     * Party tab, gone when the new identity is in no party, hands the
     * selection on as a closed tab does.
     */
    @Test
    public void keepingTheLastChannelDoesNotDependOnMembership() {
        roster();
        for (ChatChannel channel : new ChatChannel[] {ChatChannel.OOC, ChatChannel.PROXIMITY,
                ChatChannel.FACTION}) {
            ClientChatChannelState.select(channel);
            // Opening and resizing the screen both use this availability check.
            ClientChatChannelState.ensureAvailable();
            assertEquals(channel, ClientChatChannelState.getSelectedChannel());
            ClientChatIdentities.select(identityOf(BEREN));
            ClientChatChannelState.ensureAvailable();
            assertEquals(channel, ClientChatChannelState.getSelectedChannel());
            ClientChatIdentities.select(identityOf(ALDRIC));
        }
        ClientChatIdentitySelection.accept(new LostTalesChatIdentitySyncPacket(
                ALDRIC, new UUID(5L, 5L), 0x123456, "Aldric", false));
        ClientChatChannelState.select(ChatChannel.PARTY);
        assertEquals(ChatChannel.PARTY, ClientChatChannelState.getSelectedChannel());
        ClientChatIdentities.select(identityOf(BEREN));
        ClientChatChannelState.ensureAvailable();
        assertNotEquals(ChatChannel.PARTY, ClientChatChannelState.getSelectedChannel());
    }

    @Test
    public void bubblesRespectTheSelectedFactionAndIgnoreAccountChannels() {
        roster();
        UUID speaker = new UUID(10L, 20L);
        LostTalesChatMessagePacket packet =
                new LostTalesChatMessagePacket(
                        ChatChannel.FACTION, speaker, "Friend", "Steve", "", 0, 0,
                        "Hello", 1L, "").withScope(ROHAN);
        ChatSpeechBubbles.receive(packet);
        assertTrue(ChatSpeechBubbles.isEmpty());
        ClientChatIdentities.select(identityOf(BEREN));
        ChatSpeechBubbles.receive(packet);
        assertFalse(ChatSpeechBubbles.isEmpty());
        ChatSpeechBubbles.clear();
        for (ChatChannel channel : new ChatChannel[] {ChatChannel.OOC, ChatChannel.OPERATOR,
                ChatChannel.CLIENT_CONSOLE}) {
            ChatSpeechBubbles.receive(new LostTalesChatMessagePacket(
                    channel, speaker, "Steve", "Steve", "", 0, 0, "Hello", 1L, ""));
        }
        assertTrue(ChatSpeechBubbles.isEmpty());
    }

    @Test
    public void everyRoleplayingChannelProducesBubblesForItsRecipient() {
        roster();
        UUID party = new UUID(1L, 2L);
        ClientChatIdentitySelection.accept(new LostTalesChatIdentitySyncPacket(ALDRIC, party, 0, "Aldric", false));
        for (ChatChannel channel : new ChatChannel[] {ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.PARTY, ChatChannel.WHISPER}) {
            ChatSpeechBubbles.clear();
            LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                    channel, new UUID(10L, 20L), "Friend", "Steve", "", 0, 0,
                    "Hello", 1L, "", null, "", channel == ChatChannel.WHISPER ? "Steve" : "")
                    .withScope(channel == ChatChannel.FACTION ? GONDOR
                            : channel == ChatChannel.PARTY ? party.toString() : "")
                    .withConversation(ALDRIC, null);
            ChatSpeechBubbles.receive(packet);
            assertFalse(channel.getId(), ChatSpeechBubbles.isEmpty());
        }
        ClientChatIdentities.select(identityOf(BEREN));
        assertTrue(ChatSpeechBubbles.isEmpty());
    }

    private static final UUID CIRION =
            UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    /** Aldric is in Gondor and is being played; Beren is in Rohan. */
    private static void roster() {
        ClientCharacterRosterCache.acceptRoster(0, new CharacterRosterSnapshot(
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"), 2,
                ALDRIC, 1L, RoleplayCharacter.CURRENT_DATA_VERSION,
                Arrays.asList(summary(ALDRIC, "Aldric", GONDOR, 0),
                        summary(BEREN, "Beren", ROHAN, 1)),
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, true));
    }

    /** As above, with a second character of the same faction as Aldric. */
    private static void rosterWithTwoInGondor() {
        ClientCharacterRosterCache.acceptRoster(0, new CharacterRosterSnapshot(
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"), 3,
                ALDRIC, 1L, RoleplayCharacter.CURRENT_DATA_VERSION,
                Arrays.asList(summary(ALDRIC, "Aldric", GONDOR, 0),
                        summary(BEREN, "Beren", ROHAN, 1),
                        summary(CIRION, "Cirion", GONDOR, 2)),
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, true));
    }

    private static CharacterSummary summary(UUID id, String name, String faction,
                                            int slot) {
        return new CharacterSummary(id, slot, name, "human", "male",
                "human_male_0", RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, 30, faction, 1, 0L, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION, "", "", "");
    }

    private static String keyOf(UUID characterId) {
        return characterId.toString().toLowerCase(Locale.ROOT);
    }

    private static ClientChatIdentities.Identity identityOf(UUID characterId) {
        for (ClientChatIdentities.Identity identity
                : ClientChatIdentities.characterIdentities()) {
            if (characterId.equals(identity.characterId)) {
                return identity;
            }
        }
        throw new IllegalStateException("no such character on the roster");
    }

    /**
     * Reading as another identity leaves the character being played
     * exactly where it was. This is the whole promise: the chat never
     * moves anyone in the world.
     */
    @Test
    public void readingAsAnotherIdentityNeverChangesWhoIsPlayed() {
        roster();
        assertEquals(keyOf(ALDRIC), ClientChatIdentities.activeIdentityKey());
        assertEquals(keyOf(ALDRIC), ClientChatIdentities.viewIdentityKey());

        ClientChatIdentities.select(identityOf(BEREN));
        assertEquals("the chat is read as Beren", keyOf(BEREN),
                ClientChatIdentities.viewIdentityKey());
        assertEquals("Aldric is still the one being played", keyOf(ALDRIC),
                ClientChatIdentities.activeIdentityKey());
        assertEquals(ALDRIC, ClientCharacterRosterCache.getSnapshot()
                .getActiveCharacter().getCharacterId());
    }

    /**
     * A faction's talk is one conversation per identity. The row keeps
     * one Faction tab, and the lines under it are the ones said to the
     * faction of the identity being read — never the other's.
     */
    @Test
    public void theFactionTabShowsTheFactionOfTheIdentityBeingRead() {
        roster();
        ChatTab row = ChatTab.of(ChatChannel.FACTION);
        ChatTab gondor = ChatTab.of(ChatChannel.FACTION, GONDOR);
        ChatTab rohan = ChatTab.of(ChatChannel.FACTION, ROHAN);
        assertNotEquals("the two conversations are two tabs", gondor, rohan);

        // Read as Aldric: the row stands for Gondor's talk.
        assertEquals(gondor, ChatTab.viewed(row));
        assertTrue(ChatLineFilter.of(row).accepts(gondor));
        assertFalse("Rohan's lines are not shown under it",
                ChatLineFilter.of(row).accepts(rohan));

        // Read as Beren: the same row stands for Rohan's.
        ClientChatIdentities.select(identityOf(BEREN));
        assertEquals(rohan, ChatTab.viewed(row));
        assertTrue(ChatLineFilter.of(row).accepts(rohan));
        assertFalse("Gondor's lines are no longer shown under it",
                ChatLineFilter.of(row).accepts(gondor));
    }

    /**
     * A conversation's unread state is found by the row that stands for
     * it. The line arrives under its own conversation tab while the
     * window, the tab bar and the renderer all ask about the row, so the
     * two have to be one key or the divider is written where nothing
     * reads it.
     */
    @Test
    public void aScopedChannelsUnreadStateIsFoundByItsRow() {
        roster();
        ChatTab row = ChatTab.of(ChatChannel.FACTION);
        ChatTab gondor = ChatTab.of(ChatChannel.FACTION, GONDOR);
        assertNotEquals("the line's tab is not the row's", gondor, row);

        // A faction line arrives while another tab is in front.
        ClientChatChannelViews.record(41, gondor,
                ChatTab.of(ChatChannel.GLOBAL), false);

        assertEquals(Integer.valueOf(41),
                ClientChatChannelViews.unreadDividerLine(row));
        assertEquals(1, ClientChatChannelViews.unreadCount(row));
        assertTrue(ClientChatChannelViews.hasUnread(row));
    }

    /**
     * The same for a line that arrives while the conversation is open but
     * scrolled back: it is counted on the jump-to-present button, which
     * asks by the row.
     */
    @Test
    public void aScopedChannelCountsWhatArrivedWhileItWasScrolledBack() {
        roster();
        ChatTab row = ChatTab.of(ChatChannel.FACTION);
        ChatTab gondor = ChatTab.of(ChatChannel.FACTION, GONDOR);
        ClientChatChannelViews.scroll(row, 5, 100, 10.0D);
        assertTrue("the view is scrolled back",
                ClientChatChannelViews.getScroll(row, 100, 10.0D) > 0.0D);

        ClientChatChannelViews.record(42, gondor, row, false);

        assertEquals(1, ClientChatChannelViews.waitingBelow(row));
        assertEquals(Integer.valueOf(42),
                ClientChatChannelViews.unreadDividerLine(row));
        assertEquals("it is not unread; it is waiting below",
                0, ClientChatChannelViews.unreadCount(row));
    }

    /**
     * Reading as the other identity is reading another conversation, and
     * its unread state is that conversation's own.
     */
    @Test
    public void eachConversationKeepsItsOwnUnreadState() {
        roster();
        ChatTab row = ChatTab.of(ChatChannel.FACTION);
        ClientChatChannelViews.record(43,
                ChatTab.of(ChatChannel.FACTION, GONDOR),
                ChatTab.of(ChatChannel.GLOBAL), false);
        assertEquals(1, ClientChatChannelViews.unreadCount(row));

        ClientChatIdentities.select(identityOf(BEREN));
        assertEquals("Rohan's conversation has heard nothing",
                0, ClientChatChannelViews.unreadCount(row));

        ClientChatIdentities.select(identityOf(ALDRIC));
        assertEquals("Gondor's is still waiting to be read",
                1, ClientChatChannelViews.unreadCount(row));
    }

    /** A channel that is one conversation is unmoved by any of it. */
    @Test
    public void anUnscopedChannelIsOneConversationWhoeverReadsIt() {
        roster();
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        assertEquals(global, ChatTab.viewed(global));
        ClientChatIdentities.select(identityOf(BEREN));
        assertEquals("still the one tab, still its one conversation",
                global, ChatTab.viewed(global));
        assertTrue(ChatLineFilter.of(global).accepts(global));
    }

    /**
     * A conversation is shown only while the chat is read as the
     * identity holding it: what was said as Aldric is not on screen
     * while the player reads as Beren, and comes back when they read as
     * Aldric again.
     */
    @Test
    public void aConversationIsShownOnlyToTheIdentityHoldingIt() {
        roster();
        ChatTab held = ChatTab.whisper("Steve", "Faramir", keyOf(ALDRIC));
        ChatTab berens = ChatTab.whisper("Steve", "Faramir", keyOf(BEREN));
        assertTrue(ClientChatChannelState.isAvailable(held));
        assertFalse(ClientChatChannelState.isAvailable(berens));

        ClientChatIdentities.select(identityOf(BEREN));
        assertFalse("Aldric's conversation is off screen while Beren reads",
                ClientChatChannelState.isAvailable(held));
        assertTrue(ClientChatChannelState.isAvailable(berens));

        ClientChatIdentities.select(identityOf(ALDRIC));
        assertTrue("and back the moment Aldric is read as again",
                ClientChatChannelState.isAvailable(held));
    }

    /**
     * Every character of this player in a faction reads the same
     * conversation. The tab is named by the faction, so a second Gondor
     * character sees Gondor's talk rather than an empty tab that keeps
     * counting unread.
     */
    @Test
    public void twoCharactersInOneFactionReadTheSameConversation() {
        rosterWithTwoInGondor();
        ChatTab row = ChatTab.of(ChatChannel.FACTION);
        ChatTab gondor = ChatTab.of(ChatChannel.FACTION, GONDOR);

        assertEquals("read as Aldric, the row is Gondor's talk",
                gondor, ChatTab.viewed(row));
        assertTrue(ChatLineFilter.of(row).accepts(gondor));

        ClientChatIdentities.select(identityOf(CIRION));
        assertEquals("read as Cirion, the same conversation",
                gondor, ChatTab.viewed(row));
        assertTrue("Gondor's lines are still shown",
                ChatLineFilter.of(row).accepts(gondor));
    }

    /**
     * The account is in no faction of its own, so it is in Unaligned: its
     * row is Unaligned's talk, and Gondor's lines stay out of it.
     */
    @Test
    public void theAccountReadsTheUnalignedConversation() {
        // No roster held: the roleplaying channels fall back to the account.
        ChatTab row = ChatTab.of(ChatChannel.FACTION);
        ChatTab unaligned = ChatTab.of(ChatChannel.FACTION,
                LotrCharacterAdapter.UNALIGNED_FACTION_ID);
        assertEquals("the row stands for Unaligned's talk",
                unaligned, ChatTab.viewed(row));
        assertTrue(ChatLineFilter.of(row).accepts(unaligned));
        assertFalse(ChatLineFilter.of(row).accepts(
                ChatTab.of(ChatChannel.FACTION, GONDOR)));
    }

    /**
     * A line arriving in the conversation on screen is one the player is
     * looking at, so it opens no unread count. The line carries its own
     * conversation while the selection is the row entry, and the two are
     * compared as the same conversation rather than as different values.
     */
    @Test
    public void aLineInTheConversationOnScreenIsNotCountedUnread() {
        roster();
        ChatTab row = ChatTab.of(ChatChannel.FACTION);
        ChatTab gondor = ChatTab.of(ChatChannel.FACTION, GONDOR);
        ChatTab rohan = ChatTab.of(ChatChannel.FACTION, ROHAN);
        try {
            ClientChatChannelViews.record(-501, gondor, row, false);
            assertEquals("read as Aldric, Gondor's talk is on screen",
                    0, ClientChatChannelViews.unreadCount(row));

            // The other faction's talk is not on screen and is counted.
            ClientChatChannelViews.record(-502, rohan, row, false);
            assertEquals(0, ClientChatChannelViews.unreadCount(row));
            ClientChatIdentities.select(identityOf(BEREN));
            assertEquals("and is waiting when it is read as",
                    1, ClientChatChannelViews.unreadCount(row));
        } finally {
            ClientChatChannelViews.clear();
        }
    }

    /**
     * A conversation is not a tab a window holds: the row entry is, and
     * that is what the layout is asked about.
     */
    @Test
    public void aConversationBelongsToItsChannelsRowEntry() {
        ChatTab row = ChatTab.of(ChatChannel.FACTION);
        assertEquals(row, ChatTab.row(ChatTab.of(ChatChannel.FACTION, GONDOR)));
        assertEquals("an unscoped tab is its own row",
                ChatTab.of(ChatChannel.GLOBAL),
                ChatTab.row(ChatTab.of(ChatChannel.GLOBAL)));
        ChatTab whisper = ChatTab.whisper("Steve", "Faramir", keyOf(ALDRIC));
        assertEquals("a whisper conversation belongs to the person's row entry",
                ChatTab.whisper("Steve", "Faramir"), ChatTab.row(whisper));
        assertEquals("an NPC tab is its own row", ChatTab.npc("Gandalf"),
                ChatTab.row(ChatTab.npc("Gandalf")));
    }

    /**
     * A conversation round-trips through its id, and an id written when
     * a conversation was named by a character names none now, so a
     * layout file holding one drops it rather than restoring a second
     * Faction tab beside the row entry.
     */
    @Test
    public void aScopedTabRoundTripsThroughItsIdAndACharacterKeyedOneDoesNot() {
        ChatTab rohan = ChatTab.of(ChatChannel.FACTION, ROHAN);
        assertEquals("faction|in:" + ROHAN, rohan.id());
        assertEquals(rohan, ChatTab.fromId(rohan.id()));
        ChatTab plain = ChatTab.of(ChatChannel.GLOBAL);
        assertEquals("global", plain.id());
        assertEquals(plain, ChatTab.fromId(plain.id()));
        assertNull("a conversation named by a character names none now",
                ChatTab.fromId("faction|own:" + keyOf(BEREN)));
    }
}
