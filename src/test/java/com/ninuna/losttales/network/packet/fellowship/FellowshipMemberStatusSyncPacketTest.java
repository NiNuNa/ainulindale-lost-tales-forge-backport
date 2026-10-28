package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.fellowship.model.Fellowship;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The members' status carries each helmet and held item held to a bound,
 * so a fellowship at its most stays far inside one payload however its
 * members' stacks are tagged.
 */
public final class FellowshipMemberStatusSyncPacketTest {

    @Test
    public void noStackStaysNoStack() {
        assertNull(FellowshipMemberStatusSyncPacket.bounded(null));
    }

    @Test
    public void everyMembersStacksStayInsideOnePayload() {
        long longest = (long) Fellowship.MAX_MEMBERS
                * (2L * FellowshipMemberStatusSyncPacket.MAX_STACK_BYTES + 64L);
        assertTrue(longest < 1048576L);
    }
}
