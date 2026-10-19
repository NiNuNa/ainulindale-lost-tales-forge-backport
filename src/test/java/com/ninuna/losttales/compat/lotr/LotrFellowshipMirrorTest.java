package com.ninuna.losttales.compat.lotr;

import lotr.common.world.map.LOTRCustomWaypoint;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * As the leader's account goes off its own list of a fellowship it holds
 * for the record only, the copies that reach it only through that
 * fellowship are taken off; a copy another fellowship on its list brings
 * stays, and so do its own waypoints (FEL-02).
 */
public final class LotrFellowshipMirrorTest {
    private static final UUID COMPANY = UUID.fromString("d1000000-0000-0000-0000-00000000001d");
    private static final UUID RANGERS = UUID.fromString("d2000000-0000-0000-0000-00000000002d");
    private static final UUID SHARER = UUID.fromString("a1000000-0000-0000-0000-0000000000a1");

    @Test
    public void aCopySharedIntoThatFellowshipAloneIsTakenOff() {
        assertTrue(LotrFellowshipMirror.onlyThrough(copy(COMPANY), COMPANY,
                Collections.<UUID>emptyList()));
        assertTrue(LotrFellowshipMirror.onlyThrough(copy(COMPANY, RANGERS), COMPANY,
                Collections.<UUID>emptyList()));
    }

    @Test
    public void aCopyAnotherListedFellowshipBringsStays() {
        assertFalse(LotrFellowshipMirror.onlyThrough(copy(COMPANY, RANGERS), COMPANY,
                Collections.singletonList(RANGERS)));
        assertTrue(LotrFellowshipMirror.onlyThrough(copy(COMPANY, RANGERS), COMPANY,
                Arrays.asList(COMPANY)));
    }

    @Test
    public void aCopyOfAnotherFellowshipAndTheAccountsOwnWaypointsStay() {
        assertFalse(LotrFellowshipMirror.onlyThrough(copy(RANGERS), COMPANY,
                Collections.<UUID>emptyList()));
        LOTRCustomWaypoint own = waypoint();
        own.addSharedFellowship(COMPANY);
        assertFalse(LotrFellowshipMirror.onlyThrough(own, COMPANY,
                Collections.<UUID>emptyList()));
    }

    /** A copy of another account's waypoint, shared into these fellowships. */
    private static LOTRCustomWaypoint copy(UUID... fellowships) {
        LOTRCustomWaypoint copy = waypoint();
        copy.setSharingPlayerID(SHARER);
        copy.setSharedFellowshipIDs(Arrays.asList(fellowships));
        return copy;
    }

    private static LOTRCustomWaypoint waypoint() {
        return new LOTRCustomWaypoint("Bree Camp", 810.5D, 720.25D, 1200, 70, -340, 20000);
    }
}
