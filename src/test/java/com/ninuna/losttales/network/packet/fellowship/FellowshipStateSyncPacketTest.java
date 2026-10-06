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
 * The fellowship state carries the server's member limit and every
 * fellowship of the character whole: its name, icon, switches, guides,
 * and each member's presence. A fellowship is offered nobody an account
 * of whose is already in it.
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
    private static final UUID MARK = new UUID(9L, 9L);

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

    /** A fellowship's marks travel with it, each as its guide placed it. */
    @Test
    public void aFellowshipsMarksTravelWithIt() {
        FellowshipStateSnapshot decoded = roundTrip(state(10,
                Collections.<FellowshipInviteTargetSnapshot>emptyList()));
        List<FellowshipMark> marks = decoded.getFellowships().get(0).getMarks();
        assertEquals(1, marks.size());
        FellowshipMark mark = marks.get(0);
        assertEquals(MARK, mark.getMarkId());
        assertEquals(FELLOWSHIP, mark.getFellowshipId());
        assertEquals("Weathertop", mark.getName());
        assertEquals(FRIEND, mark.getPlacedBy());
        assertEquals(100, mark.getDimensionId());
        assertEquals(1200.25D, mark.getX(), 0.0D);
        assertEquals(-340.5D, mark.getZ(), 0.0D);
        assertEquals(9L, mark.getPlacedAt());
    }

    private static FellowshipStateSnapshot state(int limit,
                                                 List<FellowshipInviteTargetSnapshot> targets) {
        List<FellowshipMemberSnapshot> members = new ArrayList<FellowshipMemberSnapshot>();
        members.add(new FellowshipMemberSnapshot(LEADER, OWNER, "Aldric", 1L,
                FellowshipColor.GREEN, FellowshipMemberPresence.HERE, ""));
        members.add(new FellowshipMemberSnapshot(FRIEND, FRIEND_OWNER, "Beleg", 2L,
                FellowshipColor.ORANGE, FellowshipMemberPresence.ELSEWHERE, "Hurin"));
        FellowshipSnapshot fellowship = new FellowshipSnapshot(FELLOWSHIP, LEADER,
                "The Grey Company", new FellowshipIcon("minecraft:compass", 0),
                EnumSet.of(FellowshipSwitch.NO_FIGHTING, FellowshipSwitch.NO_HIRED_HARM),
                Collections.singletonList(FRIEND), 1L, 4L, 2, members,
                Collections.singletonList(new FellowshipMark(MARK, FELLOWSHIP,
                        "Weathertop", FRIEND, 100, 1200.25D, -340.5D, 9L)));
        return new FellowshipStateSnapshot(OWNER, 2L, FellowshipErrorId.NONE, limit,
                LEADER, Collections.singletonList(fellowship), FELLOWSHIP,
                FellowshipErrorId.NONE,
                Collections.<FellowshipInvitationSnapshot>emptyList(),
                Collections.<FellowshipInvitationSnapshot>emptyList(),
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
