package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Discord members' reactions of one Discord channel put right by what
 * Discord lists there once the bridge's live connection is back: those
 * missing added, those Discord no longer lists taken back, the players'
 * own and another channel's left standing.
 */
public final class ChatReactionsReturnTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID FIRST =
            LostTalesChatMessagePacket.discordSenderId("123456789012345678");
    private static final UUID SECOND =
            LostTalesChatMessagePacket.discordSenderId("223456789012345678");
    private static final UUID THIRD =
            LostTalesChatMessagePacket.discordSenderId("323456789012345678");
    private static final String CHANNEL = "900000000000000001";
    private static final String OTHER_CHANNEL = "900000000000000002";
    private static final String PARROT = "partyparrot:556";

    private static Map<UUID, String> listed(Object... membersAndNames) {
        Map<UUID, String> listed = new LinkedHashMap<UUID, String>();
        for (int index = 0; index < membersAndNames.length; index += 2) {
            listed.put((UUID)membersAndNames[index], (String)membersAndNames[index + 1]);
        }
        return listed;
    }

    @Test
    public void whatDiscordListsIsWhatTheChannelHolds() {
        ChatReactions reactions = new ChatReactions();
        assertTrue(reactions.set("smile", ALICE, "Alice", true));
        assertTrue(reactions.set("smile", FIRST, "First", CHANNEL, true));
        assertTrue(reactions.set("smile", SECOND, "Second", OTHER_CHANNEL, true));
        // Meanwhile in CHANNEL: First took theirs back, Third reacted.
        assertTrue(reactions.setDiscord("smile", "", CHANNEL,
                listed(THIRD, "Third"), Collections.<UUID>emptySet(), true));
        assertEquals(3, reactions.summaryFor(null).find("smile").count);
        assertEquals("", reactions.originOf("smile", FIRST));
        assertEquals(CHANNEL, reactions.originOf("smile", THIRD));
        assertEquals("another channel's member stands", OTHER_CHANNEL,
                reactions.originOf("smile", SECOND));
        assertEquals(Collections.singletonMap("smile", Integer.valueOf(1)),
                reactions.discordCountsIn(CHANNEL));
        assertFalse("put right already", reactions.setDiscord("smile", "", CHANNEL,
                listed(THIRD, "Third"), Collections.<UUID>emptySet(), true));
    }

    /** A list that may leave members out only adds; a refused member is not added. */
    @Test
    public void aPartListOnlyAddsAndARefusedMemberIsNotAdded() {
        ChatReactions reactions = new ChatReactions();
        reactions.set("smile", FIRST, "First", CHANNEL, true);
        assertTrue(reactions.setDiscord("smile", "", CHANNEL,
                listed(SECOND, "Second", THIRD, "Third"),
                Collections.singleton(THIRD), false));
        assertEquals(Integer.valueOf(2), reactions.discordCountsIn(CHANNEL).get("smile"));
        assertEquals("", reactions.originOf("smile", THIRD));
        assertEquals(CHANNEL, reactions.originOf("smile", FIRST));
        // Listed, the refused member's reaction is not taken back either.
        reactions.set("smile", THIRD, "Third", CHANNEL, true);
        assertFalse(reactions.setDiscord("smile", "", CHANNEL,
                listed(FIRST, "First", SECOND, "Second", THIRD, "Third"),
                Collections.singleton(THIRD), true));
    }

    /** A custom emoji is found by its id, whatever it is called on Discord now. */
    @Test
    public void aCustomEmojiIsPutRightByItsId() {
        ChatReactions reactions = new ChatReactions();
        reactions.set(PARROT, FIRST, "First", CHANNEL, true);
        assertTrue(reactions.setDiscord("parrot_dance:556", "556", CHANNEL,
                listed(SECOND, "Second"), Collections.<UUID>emptySet(), true));
        assertEquals(Collections.singletonMap(PARROT, Integer.valueOf(1)),
                reactions.discordCountsIn(CHANNEL));
        assertEquals(CHANNEL, reactions.originOf(PARROT, SECOND));
        // Discord holds none of it any more: taken away by its key.
        assertTrue(reactions.setDiscord(PARROT, "556", CHANNEL,
                Collections.<UUID, String>emptyMap(), Collections.<UUID>emptySet(), true));
        assertTrue(reactions.isEmpty());
    }

    /** A player is never added or taken away by Discord's list. */
    @Test
    public void playersAreLeftAlone() {
        ChatReactions reactions = new ChatReactions();
        reactions.set("smile", ALICE, "Alice", true);
        assertFalse(reactions.setDiscord("smile", "", CHANNEL,
                listed(ALICE, "Alice"), Collections.<UUID>emptySet(), true));
        assertFalse(reactions.setDiscord("smile", "", CHANNEL,
                Collections.<UUID, String>emptyMap(), Collections.<UUID>emptySet(), true));
        assertEquals(1, reactions.summaryFor(null).find("smile").count);
        assertTrue(reactions.discordCountsIn(CHANNEL).isEmpty());
        assertFalse("no channel, nothing to put right", reactions.setDiscord("smile",
                "", "", listed(FIRST, "First"), null, true));
    }
}
