package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatRoleFixtures;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipFixtures;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * How a routed line's channel decides who may be shown it later: the
 * one mapping the chat service applies to every delivered message,
 * checked through the history it feeds.
 */
public final class LostTalesChatServiceAudienceTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID FELLOWSHIP_ID = UUID.randomUUID();
    private static final List<ChatChannel> EVERY_CHANNEL =
            Arrays.asList(ChatChannel.values());
    private static final List<ChatChannel> OPEN_CHANNELS = Arrays.asList(
            ChatChannel.GLOBAL, ChatChannel.PROXIMITY, ChatChannel.FACTION,
            ChatChannel.OOC, ChatChannel.FELLOWSHIP, ChatChannel.WHISPER);

    @Before
    public void setUp() {
        ChatHistory.clear();
        ChatMessageIdAllocator.reset();
        ChatRoleCatalog.resetToBuiltIn();
        ChatChannelGates.install(ChatChannelGates.defaults());
    }

    @After
    public void tearDown() {
        ChatHistory.clear();
        ChatMessageIdAllocator.reset();
        ChatRoleCatalog.resetToBuiltIn();
        ChatChannelGates.install(ChatChannelGates.defaults());
    }

    @Test
    public void openWorldWideChannelsReachEveryoneLater() {
        record(ChatChannel.GLOBAL, null, "", Arrays.asList(ALICE, BOB));
        record(ChatChannel.OOC, null, "", Arrays.asList(ALICE, BOB));
        assertEquals(2, replay(CAROL, "", 0L, null, OPEN_CHANNELS).size());
    }

    @Test
    public void theStaffChannelReachesWhoeverMayReadItNow() {
        record(ChatChannel.OPERATOR, null, "", Arrays.asList(ALICE, BOB));
        assertEquals(1, replay(BOB, "", 0L, null, EVERY_CHANNEL).size());
        // Bob lost the operator role since.
        assertTrue(replay(BOB, "", 0L, null, OPEN_CHANNELS).isEmpty());
        // Carol gained it since and was not sent the line: the channel's
        // past comes with the role, as on Discord.
        assertEquals(1, replay(CAROL, "", 0L, null, EVERY_CHANNEL).size());
    }

    /** A gate the config puts on an open channel's read side makes it staff-like. */
    @Test
    public void aConfiguredReadGateNarrowsAnOpenChannel() {
        Map<ChatChannel, ChatChannelGates.Gate> gates =
                new HashMap<ChatChannel, ChatChannelGates.Gate>();
        gates.put(ChatChannel.GLOBAL, new ChatChannelGates.Gate(
                Collections.singleton(ChatRoleFixtures.OPERATOR_ID), null));
        ChatChannelGates.install(ChatChannelGates.of(gates));
        record(ChatChannel.GLOBAL, null, "", Arrays.asList(ALICE, BOB));
        assertEquals(1, replay(BOB, "", 0L, null, EVERY_CHANNEL).size());
        // Carol was not sent it, but may read the channel now.
        assertEquals(1, replay(CAROL, "", 0L, null, EVERY_CHANNEL).size());
        // Bob may no longer read the gated channel.
        assertTrue(replay(BOB, "", 0L, null,
                Collections.singletonList(ChatChannel.OOC)).isEmpty());
    }

    @Test
    public void aFellowshipLineReachesItsMembersThenWhileTheyAreStillMembers() {
        FellowshipMember aldric = new FellowshipMember(UUID.randomUUID(), ALICE,
                "Aldric", 1L, FellowshipColor.values()[0]);
        Fellowship fellowship = FellowshipFixtures.of(FELLOWSHIP_ID, aldric.getIdentityId(), Arrays.asList(aldric,
                new FellowshipMember(UUID.randomUUID(), BOB, "Beren", 2L,
                        FellowshipColor.values()[1])));
        record(ChatChannel.FELLOWSHIP, fellowship, "", Collections.singletonList(ALICE));
        // Bob was a member but offline when it was said.
        assertEquals(1, replay(BOB, "", 0L, FELLOWSHIP_ID, EVERY_CHANNEL).size());
        assertTrue(replay(BOB, "", 0L, null, EVERY_CHANNEL).isEmpty());
        assertTrue(replay(CAROL, "", 0L, FELLOWSHIP_ID, EVERY_CHANNEL).isEmpty());
        // A fellowship line with no fellowship behind it reaches nobody later.
        record(ChatChannel.FELLOWSHIP, null, "", Collections.singletonList(ALICE));
        assertEquals(1, replay(ALICE, "", 0L, FELLOWSHIP_ID, EVERY_CHANNEL).size());
    }

    @Test
    public void aFactionLineReachesTheFactionThenAndNow() {
        record(ChatChannel.FACTION, null, "lotr:gondor", Collections.singletonList(ALICE));
        assertEquals(1, replay(CAROL, "lotr:gondor", 5L, null, EVERY_CHANNEL).size());
        assertTrue(replay(CAROL, "lotr:gondor", 5000L, null, EVERY_CHANNEL).isEmpty());
        assertTrue(replay(CAROL, "lotr:mordor", 5L, null, EVERY_CHANNEL).isEmpty());
    }

    @Test
    public void proximityWhispersAndConsoleNotesReachOnlyWhoWasSentThem() {
        record(ChatChannel.PROXIMITY, null, "", Arrays.asList(ALICE, BOB));
        record(ChatChannel.WHISPER, null, "", Arrays.asList(ALICE, BOB));
        record(ChatChannel.CLIENT_CONSOLE, null, "", Arrays.asList(ALICE, BOB));
        assertEquals(3, replay(BOB, "", 0L, null, EVERY_CHANNEL).size());
        // Carol was near nobody, whispered with nobody and was sent no
        // note: an operator's role opens none of them.
        assertTrue(replay(CAROL, "", 0L, null, EVERY_CHANNEL).isEmpty());
        // A note to oneself comes back whatever the console gate says.
        assertEquals(3, replay(BOB, "", 0L, null, OPEN_CHANNELS).size());
    }

    private static void record(ChatChannel channel, Fellowship fellowship, String factionId,
                               List<UUID> sentTo) {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatMessagePacket line = new LostTalesChatMessagePacket(channel, ALICE,
                "Aldric", "alice", "", 0, 0, "words", 1000L, "", null, "",
                channel == ChatChannel.WHISPER ? "bob" : "", 0, false, id,
                ChatReplyReference.NONE, "");
        ChatHistory.record(id, ALICE, "Aldric", null, line, sentTo,
                ChatChannelPolicy.audienceFor(channel, fellowship, factionId, sentTo));
    }

    private static List<LostTalesChatMessagePacket> replay(
            UUID account, String factionId, long createdAt, UUID fellowshipId,
            List<ChatChannel> readable) {
        return ChatHistory.replayFor(ChatHistoryRequesters.oneFaction(account, factionId,
                createdAt, fellowshipId, readable), ChatMessageIds.NONE);
    }
}
