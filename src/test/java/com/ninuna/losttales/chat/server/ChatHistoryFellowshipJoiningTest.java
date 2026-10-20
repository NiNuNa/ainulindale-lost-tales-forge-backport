package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A character reads a fellowship conversation's past from when it joined
 * (FEL-29): the history is asked with the joining of the character played,
 * and a line said before it stays closed, whoever else of the account was
 * a member then.
 */
public final class ChatHistoryFellowshipJoiningTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID FELLOWSHIP_ID = UUID.randomUUID();

    private final FellowshipMember aldric = new FellowshipMember(UUID.randomUUID(),
            ALICE, "Aldric", 1000L, FellowshipColor.GREEN);
    private Fellowship fellowship;

    @Before
    public void setUp() {
        ChatHistory.clear();
        ChatMessageIdAllocator.reset();
        ChatChannelGates.install(ChatChannelGates.defaults());
        this.fellowship = Fellowship.createNew(FELLOWSHIP_ID, "Grey Company",
                this.aldric, 1000L);
    }

    @After
    public void tearDown() {
        ChatHistory.clear();
        ChatMessageIdAllocator.reset();
        ChatChannelGates.install(ChatChannelGates.defaults());
    }

    @Test
    public void theFounderReadsFromTheFounding() {
        long atFounding = say("we ride at dawn", 1000L, ALICE);
        long later = say("to Bree", 1500L, ALICE);

        List<LostTalesChatMessagePacket> lines = ChatHistory.replayFor(
                playing(ALICE, this.aldric), ChatMessageIds.NONE);
        assertEquals(2, lines.size());
        assertEquals(atFounding, lines.get(0).getMessageId());
        assertEquals(later, lines.get(1).getMessageId());
    }

    @Test
    public void aNewcomerReadsNothingSaidBeforeItJoined() {
        long before = say("before Beren came", 1500L, ALICE);
        FellowshipMember beren = join(BOB, "Beren", 2000L);
        long atJoining = say("welcome, Beren", 2000L, ALICE, BOB);
        long after = say("to Bree", 2500L, ALICE, BOB);
        ChatHistory.Requester bob = playing(BOB, beren);

        List<LostTalesChatMessagePacket> lines =
                ChatHistory.replayFor(bob, ChatMessageIds.NONE);
        assertEquals(2, lines.size());
        assertEquals(atJoining, lines.get(0).getMessageId());
        assertEquals(after, lines.get(1).getMessageId());

        assertEquals("asking for the conversation alone answers the same", 2,
                ChatHistory.replayForContext(bob, ChatChannel.FELLOWSHIP,
                        FELLOWSHIP_ID.toString(), ChatMessageIds.NONE).size());
        List<LostTalesChatMessagePacket> older = ChatHistory.replayBefore(bob,
                ChatChannel.FELLOWSHIP, FELLOWSHIP_ID.toString(), after);
        assertEquals("scrolling up stops at the joining", 1, older.size());
        assertEquals(atJoining, older.get(0).getMessageId());
        assertFalse(ChatHistory.mayRead(before, bob));
    }

    /**
     * Celeb of Bob's account was a member, was sent the lines of its time
     * and left; Beren of the same account joins afterwards and reads none
     * of them.
     */
    @Test
    public void anAccountsEarlierCharacterKeepsItsLinesFromTheNewcomer() {
        FellowshipMember celeb = join(BOB, "Celeb", 1200L);
        long celebsTime = say("Celeb's watch is quiet", 1500L, ALICE, BOB);
        assertEquals(1, ChatHistory.replayFor(playing(BOB, celeb),
                ChatMessageIds.NONE).size());
        this.fellowship.removeMember(celeb.getIdentityId());
        long between = say("Celeb has gone", 1800L, ALICE);
        FellowshipMember beren = join(BOB, "Beren", 2000L);
        long berensTime = say("welcome, Beren", 2500L, ALICE, BOB);
        ChatHistory.Requester bob = playing(BOB, beren);

        List<LostTalesChatMessagePacket> lines =
                ChatHistory.replayFor(bob, ChatMessageIds.NONE);
        assertEquals(1, lines.size());
        assertEquals(berensTime, lines.get(0).getMessageId());

        // The account was sent Celeb's lines, but Beren may not use them.
        assertTrue(ChatHistory.recipientsOf(celebsTime).contains(BOB));
        assertFalse(ChatHistory.mayRead(celebsTime, bob));
        assertFalse(ChatHistory.mayRead(between, bob));
        assertFalse(ChatHistory.quoteFor(celebsTime, bob, ChatChannel.FELLOWSHIP,
                FELLOWSHIP_ID.toString()).exists());
        assertNull(ChatHistory.react(celebsTime, bob, beren.getIdentityId(),
                "Beren", "smile", true));
        assertNull(ChatHistory.forwardable(celebsTime, bob));
        assertNull(ChatHistory.reportable(celebsTime, bob));
        assertTrue(ChatHistory.mayRead(berensTime, bob));

        assertTrue("Celeb is no member any more",
                ChatHistory.replayFor(playing(BOB, celeb), ChatMessageIds.NONE).isEmpty());
    }

    @Test
    public void aMemberWhoLeftAndCameBackReadsFromItsLatestJoining() {
        FellowshipMember beren = join(BOB, "Beren", 2000L);
        long firstStay = say("on the road", 2500L, ALICE, BOB);
        this.fellowship.removeMember(beren.getIdentityId());
        long away = say("Beren is away", 3500L, ALICE);
        FellowshipMember back = new FellowshipMember(beren.getIdentityId(), BOB,
                "Beren", 4000L, this.fellowship.nextColor());
        assertTrue(this.fellowship.addMember(back));
        long secondStay = say("Beren is back", 4500L, ALICE, BOB);
        ChatHistory.Requester bob = playing(BOB, this.fellowship.getMember(
                beren.getIdentityId()));

        List<LostTalesChatMessagePacket> lines =
                ChatHistory.replayFor(bob, ChatMessageIds.NONE);
        assertEquals(1, lines.size());
        assertEquals(secondStay, lines.get(0).getMessageId());
        assertFalse(ChatHistory.mayRead(firstStay, bob));
        assertFalse(ChatHistory.mayRead(away, bob));
    }

    /**
     * A lore figure keeps its joining for its next holder (CHAR-36), and
     * the lines said while another account held it stay that account's.
     */
    @Test
    public void aFiguresNextHolderReadsFromWhenItsAccountHeldIt() {
        FellowshipMember figure = join(BOB, "Beren", 2000L);
        long bobsTime = say("Bob's Beren speaks", 2500L, ALICE, BOB);
        this.fellowship.refreshMemberIdentity(figure.getIdentityId(),
                FellowshipMember.UNHELD, "Beren");
        this.fellowship.refreshMemberIdentity(figure.getIdentityId(), CAROL, "Beren");
        long carolsTime = say("Carol's Beren speaks", 3500L, ALICE, CAROL);
        FellowshipMember taken = this.fellowship.getMember(figure.getIdentityId());
        assertEquals(2000L, taken.getJoinedAt());

        List<LostTalesChatMessagePacket> lines = ChatHistory.replayFor(
                playing(CAROL, taken), ChatMessageIds.NONE);
        assertEquals(1, lines.size());
        assertEquals(carolsTime, lines.get(0).getMessageId());
        assertFalse(ChatHistory.mayRead(bobsTime, playing(CAROL, taken)));
    }

    /** What {@link LostTalesChatService#requesterFor} builds for a player playing {@code member}. */
    private ChatHistory.Requester playing(UUID account, FellowshipMember member) {
        return new ChatHistory.Requester(account,
                Collections.<String, Long>emptyMap(),
                Fellowship.joinings(Collections.singletonList(this.fellowship),
                        member.getIdentityId()),
                Arrays.asList(ChatChannel.values()), null);
    }

    private FellowshipMember join(UUID account, String name, long at) {
        FellowshipMember member = new FellowshipMember(UUID.randomUUID(), account,
                name, at, this.fellowship.nextColor());
        assertTrue(this.fellowship.addMember(member));
        return member;
    }

    /**
     * A line said in the fellowship at {@code at}, sent to the accounts
     * online, recorded with the audience the chat service gives it.
     */
    private long say(String text, long at, UUID... online) {
        long id = ChatMessageIdAllocator.next();
        List<UUID> sentTo = new ArrayList<UUID>(Arrays.asList(online));
        LostTalesChatMessagePacket line = new LostTalesChatMessagePacket(
                ChatChannel.FELLOWSHIP, ALICE, "Aldric", "alice", "", 0, 0, text,
                at, "", null, "", "", 0, false, id, ChatReplyReference.NONE, "")
                .withScope(FELLOWSHIP_ID.toString());
        ChatHistory.record(id, ALICE, "Aldric", null, line, sentTo,
                ChatChannelPolicy.audienceFor(ChatChannel.FELLOWSHIP,
                        this.fellowship, "", sentTo));
        return id;
    }
}
