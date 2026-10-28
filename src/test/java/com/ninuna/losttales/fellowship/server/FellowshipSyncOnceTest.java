package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.fellowship.sync.FellowshipInviteTargetSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A sync to many players reads whom the invite field offers once and
 * leaves each receiver out of their own list; an account whose saved
 * state cannot be read is logged once, not at every count.
 */
public final class FellowshipSyncOnceTest {

    private static final UUID ALICE = new UUID(1L, 1L);
    private static final UUID ALDRIC = new UUID(2L, 2L);

    @After
    public void tearDown() {
        FellowshipLeadLimits.clear();
    }

    @Test
    public void eachReceiverIsLeftOutOfTheirOwnList() {
        List<FellowshipInviteTargetSnapshot> everyone =
                new ArrayList<FellowshipInviteTargetSnapshot>();
        everyone.add(target(ALICE, ALDRIC, "Alice", "Aldric"));
        UUID bob = new UUID(3L, 3L);
        UUID beren = new UUID(4L, 4L);
        everyone.add(target(bob, beren, "Bob", "Beren"));

        FellowshipSyncManager.InviteTargetCollection offered =
                FellowshipSyncManager.InviteTargetCollection.offeredTo(everyone, ALICE, ALDRIC);
        assertEquals(1, offered.targets.size());
        assertEquals(beren, offered.targets.get(0).getIdentityId());
        assertFalse(offered.truncated);
    }

    @Test
    public void aLongListIsCutAtItsBound() {
        List<FellowshipInviteTargetSnapshot> everyone =
                new ArrayList<FellowshipInviteTargetSnapshot>();
        everyone.add(target(ALICE, ALDRIC, "Alice", "Aldric"));
        for (int index = 0; index <= FellowshipStateSnapshot.MAX_INVITE_TARGETS; index++) {
            everyone.add(target(new UUID(100L, index), new UUID(200L, index),
                    "Player" + index, "Character" + index));
        }

        FellowshipSyncManager.InviteTargetCollection offered =
                FellowshipSyncManager.InviteTargetCollection.offeredTo(everyone, ALICE, ALDRIC);
        assertEquals(FellowshipStateSnapshot.MAX_INVITE_TARGETS, offered.targets.size());
        assertTrue(offered.truncated);
    }

    @Test
    public void anUnreadableStateIsLoggedOncePerAccount() {
        assertTrue(FellowshipLeadLimits.firstUnreadable(ALICE));
        assertFalse(FellowshipLeadLimits.firstUnreadable(ALICE));
        assertTrue(FellowshipLeadLimits.firstUnreadable(new UUID(9L, 9L)));
        FellowshipLeadLimits.clear();
        assertTrue("forgotten as the server stops", FellowshipLeadLimits.firstUnreadable(ALICE));
    }

    private static FellowshipInviteTargetSnapshot target(UUID owner, UUID identity,
                                                         String player, String character) {
        return new FellowshipInviteTargetSnapshot(owner, identity, player, character);
    }
}
