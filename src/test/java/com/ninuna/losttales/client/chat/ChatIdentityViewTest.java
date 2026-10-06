package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatFellowship;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;
import org.junit.After;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatTypingSyncPacket;
import com.ninuna.losttales.network.packet.ChatPacketFixtures;
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
        TwoWindowLayout.reset();
    }

    @Test
    public void fellowshipMembershipFollowsTheChatIdentityAndIgnoresLateReplies() {
        roster();
        UUID firstFellowship = new UUID(1L, 1L);
        UUID secondFellowship = new UUID(2L, 2L);
        ClientChatIdentitySelection.accept(sync(ALDRIC, firstFellowship, 0x123456));
        assertEquals(firstFellowship.toString(), ClientChatChannelState.scopeKeyRead(ChatChannel.FELLOWSHIP));
        ClientChatIdentities.select(identityOf(BEREN));
        assertEquals("", ClientChatChannelState.scopeKeyRead(ChatChannel.FELLOWSHIP));
        assertFalse(ClientChatChannelState.canSend(ChatChannel.FELLOWSHIP));
        ClientChatIdentitySelection.accept(sync(ALDRIC, firstFellowship, 0x123456));
        assertEquals("", ClientChatChannelState.scopeKeyRead(ChatChannel.FELLOWSHIP));
        ClientChatIdentitySelection.accept(sync(BEREN, secondFellowship, 0xABCDEF));
        assertEquals(secondFellowship.toString(), ClientChatChannelState.scopeKeyRead(ChatChannel.FELLOWSHIP));
        assertEquals(0xABCDEF, ClientChatChannelState.displayColor(
                ConversationPage.of(ChatChannel.FELLOWSHIP, secondFellowship.toString())));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.FELLOWSHIP));
        assertFalse(ClientChatChannelState.isAvailable(ConversationPage.of(ChatChannel.FELLOWSHIP, firstFellowship.toString())));
        assertFalse(ClientChatChannelState.canSend(ConversationPage.of(ChatChannel.FELLOWSHIP, firstFellowship.toString())));
        assertTrue(ClientChatChannelState.isAvailable(ConversationPage.of(ChatChannel.FELLOWSHIP, secondFellowship.toString())));
        assertEquals(ALDRIC, ClientCharacterRosterCache.getSnapshot().getActiveCharacterId());
    }

    /** With no character held the chat falls back to the account, whose fellowship is its own. */
    @Test
    public void accountFellowshipIsSeparateAndDisconnectDropsMembership() {
        UUID fellowship = new UUID(3L, 3L);
        ClientChatIdentitySelection.accept(sync(null, fellowship, 0x123456));
        assertEquals(fellowship.toString(), ClientChatChannelState.scopeOfIdentity(ChatChannel.FELLOWSHIP, ""));
        assertTrue(ClientChatChannelState.canSend(ChatChannel.FELLOWSHIP));
        ClientChatIdentitySelection.clear();
        assertFalse(ClientChatChannelState.canSend(ChatChannel.FELLOWSHIP));
    }

    /**
     * Each fellowship's conversation is named after the fellowship ("The
     * Grey Company Chat") and shown while its character is read; the
     * channel itself keeps its plain name.
     */
    @Test
    public void eachFellowshipsConversationIsNamedAfterIt() {
        roster();
        String plain = ChatChannel.FELLOWSHIP.getDisplayName();
        UUID grey = new UUID(4L, 4L);
        ConversationPage tab = ConversationPage.of(ChatChannel.FELLOWSHIP, grey.toString());
        ClientChatIdentitySelection.accept(sync(ALDRIC, grey, 0x123456));
        assertEquals(plain, ClientChatChannelState.displayName(ChatChannel.FELLOWSHIP));
        assertNotEquals(plain, ClientChatChannelState.displayName(tab));
        assertTrue(ClientChatChannelState.isAvailable(tab));
        assertFalse("the channel has no plain tab",
                ClientChatChannelState.isAvailable(ConversationPage.of(ChatChannel.FELLOWSHIP)));
        ClientChatIdentities.select(identityOf(BEREN));
        assertFalse(ClientChatChannelState.isAvailable(tab));
        assertEquals(plain, ClientChatChannelState.displayName(tab));
    }

    @Test
    public void factionMessagesAndTypingOnlyAppearForTheSelectedIdentity() {
        roster();
        ConversationPage gondor = ConversationPage.of(ChatChannel.FACTION, GONDOR);
        ConversationPage rohan = ConversationPage.of(ChatChannel.FACTION, ROHAN);
        assertTrue(ClientChatChannelState.isAvailable(gondor));
        assertFalse(ClientChatChannelState.isAvailable(rohan));
        ClientChatIdentities.select(identityOf(BEREN));
        assertFalse(ClientChatChannelState.isAvailable(gondor));
        assertTrue(ClientChatChannelState.isAvailable(rohan));
        ClientChatTypingState.accept(new LostTalesChatTypingSyncPacket(ChatChannel.FACTION, "", "A friend", false, true,
                        GONDOR, ALDRIC.toString()));
        assertTrue(ClientChatTypingState.namesTyping(ConversationPage.of(ChatChannel.FACTION)).isEmpty());
        ClientChatTypingState.accept(new LostTalesChatTypingSyncPacket(ChatChannel.FACTION, "", "Beren's friend", false, true,
                        ROHAN, BEREN.toString()));
        assertEquals(java.util.Collections.singletonList("Beren's friend"),
                ClientChatTypingState.namesTyping(ConversationPage.of(ChatChannel.FACTION)));
    }

    /**
     * One tab per person, as one Faction tab: read as Aldric it shows the
     * conversation Steve has with Aldric, read as Beren the one with
     * Beren, and Aldric's lines stay out of Beren's view.
     */
    @Test
    public void aWhisperTabFollowsTheIdentityBeingRead() {
        roster();
        ConversationPage row = ConversationPage.whisper("Steve", "Steve");
        ConversationPage withAldric = ConversationPage.whisper("Steve", "Steve", keyOf(ALDRIC));
        ConversationPage withBeren = ConversationPage.whisper("Steve", "Steve", keyOf(BEREN));
        assertEquals(withAldric, ConversationPage.viewed(row));
        assertTrue(ChatLineFilter.of(row).accepts(withAldric));
        assertFalse(ChatLineFilter.of(row).accepts(withBeren));
        ClientChatIdentities.select(identityOf(BEREN));
        assertEquals(withBeren, ConversationPage.viewed(row));
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
                "Steve", "Friend", false, true, "", ALDRIC.toString()));
        ConversationPage beren = ConversationPage.whisper("Steve", "Friend", BEREN.toString());
        assertTrue(ClientChatTypingState.namesTyping(beren).isEmpty());
        ClientChatTypingState.accept(new LostTalesChatTypingSyncPacket(ChatChannel.WHISPER,
                "Steve", "Friend", false, true, "", BEREN.toString()));
        assertEquals(java.util.Collections.singletonList("Friend"),
                ClientChatTypingState.namesTyping(beren));
        assertTrue(ClientChatTypingState.namesTyping(
                ConversationPage.whisper("Steve", "Another character", BEREN.toString())).isEmpty());
    }

    /** The Narrator types under the name this game gives it, and stops under it too. */
    @Test
    public void theNarratorTypesUnderThisGamesName() {
        roster();
        ClientChatIdentities.select(identityOf(BEREN));
        ConversationPage told = ConversationPage.whisper("Steve",
                com.ninuna.losttales.chat.ChatNarrator.NAME, BEREN.toString());
        ClientChatTypingState.accept(new LostTalesChatTypingSyncPacket(ChatChannel.WHISPER,
                "Steve", com.ninuna.losttales.chat.ChatNarrator.NAME, true, true, "",
                BEREN.toString()));
        assertEquals(java.util.Collections.singletonList(
                com.ninuna.losttales.chat.ChatNames.narrator(
                        com.ninuna.losttales.util.LostTalesWords.LANG)),
                ClientChatTypingState.namesTyping(told));
        ClientChatTypingState.accept(new LostTalesChatTypingSyncPacket(ChatChannel.WHISPER,
                "Steve", com.ninuna.losttales.chat.ChatNarrator.NAME, true, false, "",
                BEREN.toString()));
        assertTrue(ClientChatTypingState.namesTyping(told).isEmpty());
    }

    /**
     * The last channel stays selected across an identity switch: Faction
     * follows the new identity's faction rather than moving, while the
     * Fellowship tab, gone when the new identity is in no fellowship, hands the
     * selection on as a closed tab does.
     */
    @Test
    public void keepingTheLastChannelDoesNotDependOnMembership() {
        roster();
        for (ChatChannel channel : new ChatChannel[] {ChatChannel.OOC, ChatChannel.PROXIMITY,
                ChatChannel.FACTION}) {
            ClientChatChannelState.select(ConversationPage.of(channel));
            // Opening and resizing the screen both use this availability check.
            ClientChatChannelState.ensureAvailable();
            assertEquals(channel, ClientChatChannelState.getSelected().getChannel());
            ClientChatIdentities.select(identityOf(BEREN));
            ClientChatChannelState.ensureAvailable();
            assertEquals(channel, ClientChatChannelState.getSelected().getChannel());
            ClientChatIdentities.select(identityOf(ALDRIC));
        }
        UUID fellowship = new UUID(5L, 5L);
        ClientChatIdentitySelection.accept(sync(ALDRIC, fellowship, 0x123456));
        ConversationPage conversation = ConversationPage.of(ChatChannel.FELLOWSHIP, fellowship.toString());
        ChatLayout.openTab(conversation, null);
        ClientChatChannelState.select(conversation);
        assertEquals(ChatChannel.FELLOWSHIP, ClientChatChannelState.getSelected().getChannel());
        ClientChatIdentities.select(identityOf(BEREN));
        ClientChatChannelState.ensureAvailable();
        assertNotEquals(ChatChannel.FELLOWSHIP, ClientChatChannelState.getSelected().getChannel());
    }

    @Test
    public void bubblesRespectTheSelectedFactionAndIgnoreAccountChannels() {
        roster();
        UUID speaker = new UUID(10L, 20L);
        LostTalesChatMessagePacket packet =
                ChatPacketFixtures.line(ChatChannel.FACTION, "Friend", "Steve", "Hello")
                        .sender(speaker).colors(0, 0).build().withScope(ROHAN);
        ChatSpeechBubbles.receive(packet);
        assertTrue(ChatSpeechBubbles.isEmpty());
        ClientChatIdentities.select(identityOf(BEREN));
        ChatSpeechBubbles.receive(packet);
        assertFalse(ChatSpeechBubbles.isEmpty());
        ChatSpeechBubbles.clear();
        for (ChatChannel channel : new ChatChannel[] {ChatChannel.OOC, ChatChannel.OPERATOR,
                ChatChannel.CLIENT_CONSOLE}) {
            ChatSpeechBubbles.receive(ChatPacketFixtures.line(channel, "Steve", "Steve", "Hello")
                    .sender(speaker).colors(0, 0).build());
        }
        assertTrue(ChatSpeechBubbles.isEmpty());
    }

    @Test
    public void everyRoleplayingChannelProducesBubblesForItsRecipient() {
        roster();
        UUID fellowship = new UUID(1L, 2L);
        ClientChatIdentitySelection.accept(sync(ALDRIC, fellowship, 0));
        for (ChatChannel channel : new ChatChannel[] {ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.FELLOWSHIP, ChatChannel.WHISPER}) {
            ChatSpeechBubbles.clear();
            LostTalesChatMessagePacket packet = ChatPacketFixtures.line(
                    channel, "Friend", "Steve", "Hello").sender(new UUID(10L, 20L)).colors(0, 0)
                    .partner(channel == ChatChannel.WHISPER ? "Steve" : "").build()
                    .withScope(channel == ChatChannel.FACTION ? GONDOR
                            : channel == ChatChannel.FELLOWSHIP ? fellowship.toString() : "")
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
                ALDRIC, 1L,
                Arrays.asList(summary(ALDRIC, "Aldric", GONDOR, 0),
                        summary(BEREN, "Beren", ROHAN, 1)),
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, true,
                java.util.Collections.<com.ninuna.losttales.character.sync.DeletedCharacterSummary>emptyList()));
    }

    /** As above, with a second character of the same faction as Aldric. */
    private static void rosterWithTwoInGondor() {
        ClientCharacterRosterCache.acceptRoster(0, new CharacterRosterSnapshot(
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"), 3,
                ALDRIC, 1L,
                Arrays.asList(summary(ALDRIC, "Aldric", GONDOR, 0),
                        summary(BEREN, "Beren", ROHAN, 1),
                        summary(CIRION, "Cirion", GONDOR, 2)),
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, true,
                java.util.Collections.<com.ninuna.losttales.character.sync.DeletedCharacterSummary>emptyList()));
    }

    private static CharacterSummary summary(UUID id, String name, String faction,
                                            int slot) {
        return new CharacterSummary(id, slot, name, "human", "male",
                "human_male_0", RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, 30, faction, "", "");
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
        ConversationPage row = ConversationPage.of(ChatChannel.FACTION);
        ConversationPage gondor = ConversationPage.of(ChatChannel.FACTION, GONDOR);
        ConversationPage rohan = ConversationPage.of(ChatChannel.FACTION, ROHAN);
        assertNotEquals("the two conversations are two tabs", gondor, rohan);

        // Read as Aldric: the row stands for Gondor's talk.
        assertEquals(gondor, ConversationPage.viewed(row));
        assertTrue(ChatLineFilter.of(row).accepts(gondor));
        assertFalse("Rohan's lines are not shown under it",
                ChatLineFilter.of(row).accepts(rohan));

        // Read as Beren: the same row stands for Rohan's.
        ClientChatIdentities.select(identityOf(BEREN));
        assertEquals(rohan, ConversationPage.viewed(row));
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
        ConversationPage row = ConversationPage.of(ChatChannel.FACTION);
        ConversationPage gondor = ConversationPage.of(ChatChannel.FACTION, GONDOR);
        assertNotEquals("the line's tab is not the row's", gondor, row);

        // A faction line arrives while another tab is in front.
        ClientChatChannelViews.record(41, gondor,
                ConversationPage.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);

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
        ConversationPage row = ConversationPage.of(ChatChannel.FACTION);
        ConversationPage gondor = ConversationPage.of(ChatChannel.FACTION, GONDOR);
        scroll(row, 5, 100, 10.0D);
        assertTrue("the view is scrolled back",
                ClientChatChannelViews.getScroll(row, 100, 10.0D) > 0.0D);

        ClientChatChannelViews.record(42, gondor, row, false, ChatMessageIds.NONE, System.currentTimeMillis(), false);

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
        ConversationPage row = ConversationPage.of(ChatChannel.FACTION);
        ClientChatChannelViews.record(43,
                ConversationPage.of(ChatChannel.FACTION, GONDOR),
                ConversationPage.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
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
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        assertEquals(global, ConversationPage.viewed(global));
        ClientChatIdentities.select(identityOf(BEREN));
        assertEquals("still the one tab, still its one conversation",
                global, ConversationPage.viewed(global));
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
        ConversationPage held = ConversationPage.whisper("Steve", "Faramir", keyOf(ALDRIC));
        ConversationPage berens = ConversationPage.whisper("Steve", "Faramir", keyOf(BEREN));
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
        ConversationPage row = ConversationPage.of(ChatChannel.FACTION);
        ConversationPage gondor = ConversationPage.of(ChatChannel.FACTION, GONDOR);

        assertEquals("read as Aldric, the row is Gondor's talk",
                gondor, ConversationPage.viewed(row));
        assertTrue(ChatLineFilter.of(row).accepts(gondor));

        ClientChatIdentities.select(identityOf(CIRION));
        assertEquals("read as Cirion, the same conversation",
                gondor, ConversationPage.viewed(row));
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
        ConversationPage row = ConversationPage.of(ChatChannel.FACTION);
        ConversationPage unaligned = ConversationPage.of(ChatChannel.FACTION,
                LotrCharacterAdapter.UNALIGNED_FACTION_ID);
        assertEquals("the row stands for Unaligned's talk",
                unaligned, ConversationPage.viewed(row));
        assertTrue(ChatLineFilter.of(row).accepts(unaligned));
        assertFalse(ChatLineFilter.of(row).accepts(
                ConversationPage.of(ChatChannel.FACTION, GONDOR)));
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
        ConversationPage row = ConversationPage.of(ChatChannel.FACTION);
        ConversationPage gondor = ConversationPage.of(ChatChannel.FACTION, GONDOR);
        ConversationPage rohan = ConversationPage.of(ChatChannel.FACTION, ROHAN);
        try {
            ClientChatChannelViews.record(-501, gondor, row, false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
            assertEquals("read as Aldric, Gondor's talk is on screen",
                    0, ClientChatChannelViews.unreadCount(row));

            // The other faction's talk is not on screen and is counted.
            ClientChatChannelViews.record(-502, rohan, row, false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
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
        ConversationPage row = ConversationPage.of(ChatChannel.FACTION);
        assertEquals(row, ConversationPage.row(ConversationPage.of(ChatChannel.FACTION, GONDOR)));
        assertEquals("an unscoped tab is its own row",
                ConversationPage.of(ChatChannel.GLOBAL),
                ConversationPage.row(ConversationPage.of(ChatChannel.GLOBAL)));
        ConversationPage whisper = ConversationPage.whisper("Steve", "Faramir", keyOf(ALDRIC));
        assertEquals("a whisper conversation belongs to the person's row entry",
                ConversationPage.whisper("Steve", "Faramir"), ConversationPage.row(whisper));
        assertEquals("an NPC tab is its own row", ConversationPage.npc("Gandalf"),
                ConversationPage.row(ConversationPage.npc("Gandalf")));
    }

    /**
     * A conversation round-trips through its id, and an id written when
     * a conversation was named by a character names none now, so a
     * layout file holding one drops it rather than restoring a second
     * Faction tab beside the row entry.
     */
    @Test
    public void aScopedTabRoundTripsThroughItsIdAndACharacterKeyedOneDoesNot() {
        ConversationPage rohan = ConversationPage.of(ChatChannel.FACTION, ROHAN);
        assertEquals("faction|in:" + ROHAN, rohan.id());
        assertEquals(rohan, ConversationPage.fromId(rohan.id()));
        ConversationPage plain = ConversationPage.of(ChatChannel.GLOBAL);
        assertEquals("global", plain.id());
        assertEquals(plain, ConversationPage.fromId(plain.id()));
        assertNull("a conversation named by a character names none now",
                ConversationPage.fromId("faction|own:" + keyOf(BEREN)));
    }

    /** Scrolls a view by whole lines the way the wheel does: from where it stands. */
    private static void scroll(ConversationPage tab, int lines, int totalLines, double roomLines) {
        double current = ClientChatChannelViews.getScroll(tab, totalLines, roomLines);
        ClientChatChannelViews.scrollTo(tab, current + lines, totalLines, roomLines);
    }

    /** The server's answer: the identity, in one fellowship whose name follows its id. */
    private static LostTalesChatIdentitySyncPacket sync(UUID identity, UUID fellowship, int color) {
        return new LostTalesChatIdentitySyncPacket(identity, Collections.singletonList(
                new ChatFellowship(fellowship, "Company " + fellowship.getLeastSignificantBits(),
                        color)), false);
    }
}
