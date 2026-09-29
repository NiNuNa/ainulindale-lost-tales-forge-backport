package com.ninuna.losttales.network.packet.party;

import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.server.PartyErrorId;
import com.ninuna.losttales.party.sync.PartyInvitationSnapshot;
import com.ninuna.losttales.party.sync.PartyInviteTargetSnapshot;
import com.ninuna.losttales.party.sync.PartyMemberSnapshot;
import com.ninuna.losttales.party.sync.PartySnapshot;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The party state carries the server's member limit and the party's name,
 * and the leader is offered nobody an account of whose is already in the
 * party.
 */
public final class PartyStateSyncPacketTest {

    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LEADER =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID FRIEND_OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID FRIEND =
            UUID.fromString("20000000-0000-0000-0000-000000000004");

    @Test
    public void theStateCarriesTheNameAndTheLimit() {
        PartyStateSnapshot decoded = roundTrip(state("The Grey Company", 6,
                Collections.<PartyInviteTargetSnapshot>emptyList()));
        assertEquals(6, decoded.getMemberLimit());
        assertEquals("The Grey Company", decoded.getParty().getName());
        assertFalse(decoded.isPartyFull());
    }

    @Test
    public void anUnnamedPartyStaysUnnamed() {
        assertEquals("", roundTrip(state("", 4,
                Collections.<PartyInviteTargetSnapshot>emptyList()))
                .getParty().getName());
    }

    @Test
    public void theLimitDecidesWhenThePartyIsFull() {
        PartyStateSnapshot full = roundTrip(state("", 2,
                Collections.<PartyInviteTargetSnapshot>emptyList()));
        assertTrue(full.isPartyFull());
    }

    @Test
    public void anAccountAlreadyInThePartyIsNotOffered() {
        List<PartyInviteTargetSnapshot> targets =
                new ArrayList<PartyInviteTargetSnapshot>();
        // The friend's other character: the server refuses it anyway.
        targets.add(new PartyInviteTargetSnapshot(FRIEND_OWNER,
                UUID.fromString("20000000-0000-0000-0000-000000000005"),
                "Friend", "Beren"));
        UUID strangerOwner = UUID.fromString("10000000-0000-0000-0000-000000000006");
        targets.add(new PartyInviteTargetSnapshot(strangerOwner,
                UUID.fromString("20000000-0000-0000-0000-000000000007"),
                "Stranger", "Hurin"));
        PartyStateSnapshot snapshot = state("", 4, targets);
        assertEquals(1, snapshot.getInviteTargets().size());
        assertEquals(strangerOwner, snapshot.getInviteTargets().get(0).getOwnerId());
    }

    @Test
    public void anUnavailableStateCarriesTheLimitToo() {
        PartyStateSnapshot decoded = roundTrip(PartyStateSnapshot.failure(
                OWNER, 3L, 5, PartyErrorId.CHARACTER_STORAGE_READ_ONLY));
        assertFalse(decoded.isAvailable());
        assertEquals(5, decoded.getMemberLimit());
    }

    @Test
    public void aLimitOutsideTwoToEightIsMalformed() {
        assertTrue(decodeFailureWithLimit(9).isMalformed());
        assertTrue(decodeFailureWithLimit(1).isMalformed());
        assertFalse(decodeFailureWithLimit(8).isMalformed());
    }

    private static PartyStateSyncPacket decodeFailureWithLimit(int limit) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(0);
            LostTalesPacketCodec.writeUuid(buffer, OWNER);
            buffer.writeLong(1L);
            LostTalesPacketCodec.writeUtf8String(buffer,
                    PartyErrorId.INTERNAL_ERROR.getId(), 64);
            buffer.writeByte(limit);
            PartyStateSyncPacket decoded = new PartyStateSyncPacket();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static PartyStateSnapshot state(String name, int limit,
                                            List<PartyInviteTargetSnapshot> targets) {
        List<PartyMemberSnapshot> members = new ArrayList<PartyMemberSnapshot>();
        members.add(new PartyMemberSnapshot(LEADER, OWNER, "Aldric", 1L,
                PartyColor.GREEN));
        members.add(new PartyMemberSnapshot(FRIEND, FRIEND_OWNER, "Beleg", 2L,
                PartyColor.ORANGE));
        PartySnapshot party = new PartySnapshot(
                UUID.fromString("30000000-0000-0000-0000-000000000008"),
                LEADER, name, 1L, 4L, 1, members);
        return new PartyStateSnapshot(OWNER, 2L, PartyErrorId.NONE, limit,
                LEADER, party,
                Collections.<PartyInvitationSnapshot>emptyList(),
                Collections.<PartyInvitationSnapshot>emptyList(),
                false, false, targets, false);
    }

    private static PartyStateSnapshot roundTrip(PartyStateSnapshot snapshot) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new PartyStateSyncPacket(1, snapshot).toBytes(buffer);
            PartyStateSyncPacket decoded = new PartyStateSyncPacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            return decoded.getSnapshot();
        } finally {
            buffer.release();
        }
    }
}
