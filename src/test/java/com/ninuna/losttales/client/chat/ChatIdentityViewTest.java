package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.junit.After;
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
        ClientChatAppearances.clear();
        ClientCharacterRosterCache.clear();
        ClientChatChannelViews.clear();
    }

    private static final UUID CIRION =
            UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    /** Aldric is in Gondor and is being played; Beren is in Rohan. */
    private static void roster() {
        ClientCharacterRosterCache.acceptRoster(0, new CharacterRosterSnapshot(
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"), 2,
                ALDRIC, 1L, RoleplayCharacter.CURRENT_DATA_VERSION,
                Arrays.asList(summary(ALDRIC, "Aldric", GONDOR, 0),
                        summary(BEREN, "Beren", ROHAN, 1))));
    }

    /** As above, with a second character of the same faction as Aldric. */
    private static void rosterWithTwoInGondor() {
        ClientCharacterRosterCache.acceptRoster(0, new CharacterRosterSnapshot(
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"), 3,
                ALDRIC, 1L, RoleplayCharacter.CURRENT_DATA_VERSION,
                Arrays.asList(summary(ALDRIC, "Aldric", GONDOR, 0),
                        summary(BEREN, "Beren", ROHAN, 1),
                        summary(CIRION, "Cirion", GONDOR, 2))));
    }

    private static CharacterSummary summary(UUID id, String name, String faction,
                                            int slot) {
        return new CharacterSummary(id, slot, name, "human", "male",
                "human_male_0", 30, faction, 1, 0L, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION);
    }

    private static String keyOf(UUID characterId) {
        return characterId.toString().toLowerCase(Locale.ROOT);
    }

    private static ClientChatAppearances.Appearance appearanceOf(UUID characterId) {
        for (ClientChatAppearances.Appearance appearance
                : ClientChatAppearances.characterAppearances()) {
            if (characterId.equals(appearance.characterId)) {
                return appearance;
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
        assertEquals(keyOf(ALDRIC), ClientChatAppearances.activeIdentityKey());
        assertEquals(keyOf(ALDRIC), ClientChatAppearances.viewIdentityKey());

        ClientChatAppearances.select(appearanceOf(BEREN),
                ChatTab.of(ChatChannel.ALL));
        assertEquals("the chat is read as Beren", keyOf(BEREN),
                ClientChatAppearances.viewIdentityKey());
        assertEquals("Aldric is still the one being played", keyOf(ALDRIC),
                ClientChatAppearances.activeIdentityKey());
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
        ClientChatAppearances.select(appearanceOf(BEREN), row);
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
                ChatTab.of(ChatChannel.ALL), false);

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
                ChatTab.of(ChatChannel.ALL), false);
        assertEquals(1, ClientChatChannelViews.unreadCount(row));

        ClientChatAppearances.select(appearanceOf(BEREN), row);
        assertEquals("Rohan's conversation has heard nothing",
                0, ClientChatChannelViews.unreadCount(row));

        ClientChatAppearances.select(appearanceOf(ALDRIC), row);
        assertEquals("Gondor's is still waiting to be read",
                1, ClientChatChannelViews.unreadCount(row));
    }

    /** A channel that is one conversation is unmoved by any of it. */
    @Test
    public void anUnscopedChannelIsOneConversationWhoeverReadsIt() {
        roster();
        ChatTab global = ChatTab.of(ChatChannel.ALL);
        assertEquals(global, ChatTab.viewed(global));
        ClientChatAppearances.select(appearanceOf(BEREN), global);
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

        ClientChatAppearances.select(appearanceOf(BEREN), ChatTab.of(ChatChannel.ALL));
        assertFalse("Aldric's conversation is off screen while Beren reads",
                ClientChatChannelState.isAvailable(held));
        assertTrue(ClientChatChannelState.isAvailable(berens));

        ClientChatAppearances.followThePlayedIdentity();
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

        ClientChatAppearances.select(appearanceOf(CIRION), row);
        assertEquals("read as Cirion, the same conversation",
                gondor, ChatTab.viewed(row));
        assertTrue("Gondor's lines are still shown",
                ChatLineFilter.of(row).accepts(gondor));
    }

    /** The account is in no faction, so it is in no conversation. */
    @Test
    public void theAccountReadsNoFactionConversation() {
        roster();
        ChatTab row = ChatTab.of(ChatChannel.FACTION);
        ClientChatAppearances.select(ClientChatAppearances.accountAppearance(), row);
        assertEquals("the row stands for nothing to show",
                row, ChatTab.viewed(row));
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
            ClientChatAppearances.select(appearanceOf(BEREN), row);
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
                ChatTab.of(ChatChannel.ALL),
                ChatTab.row(ChatTab.of(ChatChannel.ALL)));
        ChatTab whisper = ChatTab.whisper("Steve", "Faramir", keyOf(ALDRIC));
        assertEquals("a whisper tab is its own row", whisper,
                ChatTab.row(whisper));
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
        ChatTab plain = ChatTab.of(ChatChannel.ALL);
        assertEquals("all", plain.id());
        assertEquals(plain, ChatTab.fromId(plain.id()));
        assertNull("a conversation named by a character names none now",
                ChatTab.fromId("faction|own:" + keyOf(BEREN)));
    }
}
