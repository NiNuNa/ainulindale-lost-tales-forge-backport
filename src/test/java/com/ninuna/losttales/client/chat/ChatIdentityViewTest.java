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
    }

    /** Aldric is in Gondor and is being played; Beren is in Rohan. */
    private static void roster() {
        ClientCharacterRosterCache.acceptRoster(0, new CharacterRosterSnapshot(
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"), 2,
                ALDRIC, 1L, RoleplayCharacter.CURRENT_DATA_VERSION,
                Arrays.asList(summary(ALDRIC, "Aldric", GONDOR, 0),
                        summary(BEREN, "Beren", ROHAN, 1))));
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
        ChatTab gondor = ChatTab.of(ChatChannel.FACTION, keyOf(ALDRIC));
        ChatTab rohan = ChatTab.of(ChatChannel.FACTION, keyOf(BEREN));
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
     * A tab a scoped channel's line is filed under round-trips through
     * the id the layout writes, so a stored tab still names the same
     * conversation after a restart.
     */
    @Test
    public void aScopedTabRoundTripsThroughItsId() {
        ChatTab rohan = ChatTab.of(ChatChannel.FACTION, keyOf(BEREN));
        assertEquals("faction|own:" + keyOf(BEREN), rohan.id());
        assertEquals(rohan, ChatTab.fromId(rohan.id()));
        ChatTab plain = ChatTab.of(ChatChannel.ALL);
        assertEquals("all", plain.id());
        assertEquals(plain, ChatTab.fromId(plain.id()));
    }
}
