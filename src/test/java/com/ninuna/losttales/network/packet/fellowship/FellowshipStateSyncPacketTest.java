package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipIcon;
import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.server.FellowshipErrorId;
import com.ninuna.losttales.fellowship.sync.FellowshipInvitationSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipInviteTargetSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberPresence;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The fellowship state carries the server's member limit, every
 * fellowship of the character whole (its name, icon, switches, guides,
 * and each member's presence) and every mark the character sees. A
 * fellowship is offered nobody an account of whose is already in it.
 */
public final class FellowshipStateSyncPacketTest {

    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LEADER =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID FRIEND_OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID FRIEND =
            UUID.fromString("20000000-0000-0000-0000-000000000004");
    private static final UUID FELLOWSHIP =
            UUID.fromString("30000000-0000-0000-0000-000000000008");
    private static final UUID LOTR_FELLOWSHIP =
            UUID.fromString("40000000-0000-0000-0000-00000000000a");
    private static final UUID MARK = new UUID(9L, 9L);
    private static final UUID GO_HERE = new UUID(10L, 10L);

    @Test
    public void theStateCarriesEveryFellowshipWhole() {
        FellowshipStateSnapshot decoded = roundTrip(state(6,
                Collections.<FellowshipInviteTargetSnapshot>emptyList()));
        assertEquals(6, decoded.getMemberLimit());
        assertEquals(FellowshipErrorId.NONE, decoded.getCreateRefusal());
        FellowshipSnapshot fellowship = decoded.getTravellingFellowship();
        assertEquals("The Grey Company", fellowship.getName());
        assertEquals(new FellowshipIcon("minecraft:compass", 0), fellowship.getIcon());
        assertTrue(fellowship.isOn(FellowshipSwitch.NO_FIGHTING));
        assertFalse(fellowship.isOn(FellowshipSwitch.SHOWN_ON_MAP));
        assertTrue(fellowship.isGuide(FRIEND));
        FellowshipMemberSnapshot friend = fellowship.getMember(FRIEND);
        assertEquals(FellowshipMemberPresence.ELSEWHERE, friend.getPresence());
        assertEquals("Hurin", friend.getElsewhereName());
        assertFalse(decoded.isFull(fellowship));
        assertEquals("LOTR's fellowship behind it, to share waypoints into",
                LOTR_FELLOWSHIP, fellowship.getLotrFellowshipId());
    }

    @Test
    public void theLimitDecidesWhenAFellowshipIsFull() {
        FellowshipStateSnapshot full = roundTrip(state(2,
                Collections.<FellowshipInviteTargetSnapshot>emptyList()));
        assertTrue(full.isFull(full.getTravellingFellowship()));
    }

    @Test
    public void anAccountAlreadyInTheFellowshipIsNotOffered() {
        List<FellowshipInviteTargetSnapshot> targets =
                new ArrayList<FellowshipInviteTargetSnapshot>();
        // The friend's other character: the server refuses it anyway.
        targets.add(new FellowshipInviteTargetSnapshot(FRIEND_OWNER,
                UUID.fromString("20000000-0000-0000-0000-000000000005"),
                "Friend", "Beren"));
        UUID strangerOwner = UUID.fromString("10000000-0000-0000-0000-000000000006");
        targets.add(new FellowshipInviteTargetSnapshot(strangerOwner,
                UUID.fromString("20000000-0000-0000-0000-000000000007"),
                "Stranger", "Hurin"));
        FellowshipStateSnapshot snapshot = roundTrip(state(4, targets));
        List<FellowshipInviteTargetSnapshot> offered =
                snapshot.getInviteTargets(snapshot.getTravellingFellowship());
        assertEquals(1, offered.size());
        assertEquals(strangerOwner, offered.get(0).getOwnerId());
    }

    /**
     * An offer that names no account went to a reader not shown accounts:
     * leaving the friend's other character out would say whose it is, so
     * it is offered and the server's neutral refusal answers it.
     */
    @Test
    public void aReaderNotShownAccountsIsOfferedEveryCharacter() {
        List<FellowshipInviteTargetSnapshot> targets =
                new ArrayList<FellowshipInviteTargetSnapshot>();
        targets.add(new FellowshipInviteTargetSnapshot(FRIEND_OWNER,
                UUID.fromString("20000000-0000-0000-0000-000000000005"),
                "", "Beren"));
        FellowshipStateSnapshot snapshot = roundTrip(state(4, targets));
        List<FellowshipInviteTargetSnapshot> offered =
                snapshot.getInviteTargets(snapshot.getTravellingFellowship());
        assertEquals(1, offered.size());
        assertEquals("Beren", offered.get(0).getCharacterName());
    }

    @Test
    public void anUnavailableStateCarriesTheLimitToo() {
        FellowshipStateSnapshot decoded = roundTrip(FellowshipStateSnapshot.failure(
                OWNER, 3L, 5, FellowshipErrorId.CHARACTER_STORAGE_READ_ONLY));
        assertFalse(decoded.isAvailable());
        assertEquals(5, decoded.getMemberLimit());
    }

    @Test
    public void aLimitOutsideTwoToFiftyIsMalformed() {
        assertTrue(decodeFailureWithLimit(51).isMalformed());
        assertTrue(decodeFailureWithLimit(1).isMalformed());
        assertFalse(decodeFailureWithLimit(50).isMalformed());
    }

    private static FellowshipStateSyncPacket decodeFailureWithLimit(int limit) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(0);
            LostTalesPacketCodec.writeUuid(buffer, OWNER);
            buffer.writeLong(1L);
            LostTalesPacketCodec.writeUtf8String(buffer,
                    FellowshipErrorId.INTERNAL_ERROR.getId(), 64);
            buffer.writeByte(limit);
            FellowshipStateSyncPacket decoded = new FellowshipStateSyncPacket();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    /** The marks travel with the state, each whole: a fellowship's as its guide placed it, a go-here mark as its owner did. */
    @Test
    public void theMarksTravelWithTheState() {
        FellowshipStateSnapshot decoded = roundTrip(state(10,
                Collections.<FellowshipInviteTargetSnapshot>emptyList()));
        List<FellowshipMark> marks = decoded.getFellowshipMarks(FELLOWSHIP);
        assertEquals(1, marks.size());
        assertEquals(2, decoded.getMarks().size());
        FellowshipMark goHere = decoded.getGoHereOf(FRIEND);
        assertEquals(GO_HERE, goHere.getMarkId());
        assertEquals(-22000.25D, goHere.getX(), 0.0D);
        assertEquals("", goHere.getName());
        FellowshipMark mark = marks.get(0);
        assertEquals(MARK, mark.getMarkId());
        assertEquals(FELLOWSHIP, mark.getFellowshipId());
        assertEquals("Weathertop", mark.getName());
        assertEquals(FRIEND, mark.getPlacedBy());
        assertEquals(100, mark.getDimensionId());
        assertEquals(1200.25D, mark.getX(), 0.0D);
        assertEquals(-340.5D, mark.getZ(), 0.0D);
    }

    /** An invitation travels with what the page shows of it: who sent it, to whom, and until when. */
    @Test
    public void anInvitationTravelsWithWhatThePageShows() {
        List<FellowshipInvitationSnapshot> sent = new ArrayList<FellowshipInvitationSnapshot>();
        UUID invitationId = new UUID(11L, 11L);
        UUID target = UUID.fromString("20000000-0000-0000-0000-000000000009");
        sent.add(new FellowshipInvitationSnapshot(invitationId, FELLOWSHIP, "The Grey Company",
                LEADER, OWNER, "Aldric", target, "Hurin", 86400000L));
        FellowshipStateSnapshot decoded = roundTrip(state(10,
                Collections.<FellowshipInviteTargetSnapshot>emptyList(), sent));
        assertEquals(1, decoded.getOutgoingInvitations().size());
        FellowshipInvitationSnapshot invitation = decoded.getOutgoingInvitations().get(0);
        assertEquals(invitationId, invitation.getInvitationId());
        assertEquals(OWNER, invitation.getInvitingOwnerId());
        assertEquals("Aldric", invitation.getInvitingCharacterName());
        assertEquals(target, invitation.getTargetIdentityId());
        assertEquals("Hurin", invitation.getTargetCharacterName());
        assertEquals(86400000L, invitation.getExpiresAt());
    }

    private static FellowshipStateSnapshot state(int limit,
                                                 List<FellowshipInviteTargetSnapshot> targets) {
        return state(limit, targets,
                Collections.<FellowshipInvitationSnapshot>emptyList());
    }

    private static FellowshipStateSnapshot state(int limit,
                                                 List<FellowshipInviteTargetSnapshot> targets,
                                                 List<FellowshipInvitationSnapshot> sent) {
        List<FellowshipMemberSnapshot> members = new ArrayList<FellowshipMemberSnapshot>();
        members.add(new FellowshipMemberSnapshot(LEADER, OWNER, "Aldric",
                FellowshipColor.GREEN, FellowshipMemberPresence.HERE, ""));
        members.add(new FellowshipMemberSnapshot(FRIEND, FRIEND_OWNER, "Beleg",
                FellowshipColor.ORANGE, FellowshipMemberPresence.ELSEWHERE, "Hurin"));
        FellowshipSnapshot fellowship = new FellowshipSnapshot(FELLOWSHIP, LEADER,
                "The Grey Company", new FellowshipIcon("minecraft:compass", 0),
                EnumSet.of(FellowshipSwitch.NO_FIGHTING, FellowshipSwitch.NO_HIRED_HARM),
                Collections.singletonList(FRIEND), 4L, LOTR_FELLOWSHIP, members);
        List<FellowshipMark> marks = new ArrayList<FellowshipMark>();
        marks.add(FellowshipMark.ofFellowship(MARK, FELLOWSHIP,
                "Weathertop", FRIEND, 100, 1200.25D, -340.5D, 9L));
        marks.add(FellowshipMark.goHere(GO_HERE, FRIEND, 100, -22000.25D, -7278.5D, 12L));
        return new FellowshipStateSnapshot(OWNER, 2L, FellowshipErrorId.NONE, limit,
                LEADER, Collections.singletonList(fellowship), FELLOWSHIP, marks,
                FellowshipErrorId.NONE,
                Collections.<FellowshipInvitationSnapshot>emptyList(), sent,
                false, false, targets, false);
    }

    private static FellowshipStateSnapshot roundTrip(FellowshipStateSnapshot snapshot) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new FellowshipStateSyncPacket(1, snapshot).toBytes(buffer);
            FellowshipStateSyncPacket decoded = new FellowshipStateSyncPacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            return decoded.getSnapshot();
        } finally {
            buffer.release();
        }
    }
}
