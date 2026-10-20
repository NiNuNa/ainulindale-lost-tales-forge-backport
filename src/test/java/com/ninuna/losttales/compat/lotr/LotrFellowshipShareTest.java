package com.ninuna.losttales.compat.lotr;

import lotr.common.fellowship.LOTRFellowship;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * LOTR's request to share a waypoint finds the fellowship it names among
 * the player's own, as LOTR does, but passes over one the account holds
 * for the record only while nobody plays a member (FEL-02).
 */
public final class LotrFellowshipShareTest {
    private static final UUID OWNER = UUID.fromString("a1000000-0000-0000-0000-0000000000a1");

    @Test
    public void aFellowshipHeldForTheRecordIsNoneToShareInto() {
        LOTRFellowship held = new LOTRFellowship(OWNER, "Rangers");
        List<LOTRFellowship> listed = Collections.singletonList(held);
        Set<UUID> record = Collections.singleton(held.getFellowshipID());

        assertNull(LostTalesLotrFellowshipRequestHook.firstToShareInto(listed, "Rangers", record));
        assertSame("played again, it is one to share into", held,
                LostTalesLotrFellowshipRequestHook.firstToShareInto(listed, "rangers",
                        Collections.<UUID>emptySet()));
    }

    /** Two of one name on the account's list: the one held for the record is passed over. */
    @Test
    public void theNextOfThatNameIsFound() {
        LOTRFellowship held = new LOTRFellowship(OWNER, "Rangers");
        LOTRFellowship played = new LOTRFellowship(OWNER, "Rangers");
        List<LOTRFellowship> listed = Arrays.asList(held, played);

        assertSame(played, LostTalesLotrFellowshipRequestHook.firstToShareInto(listed,
                "RANGERS", Collections.singleton(held.getFellowshipID())));
        assertNull(LostTalesLotrFellowshipRequestHook.firstToShareInto(listed,
                "Grey Company", Collections.<UUID>emptySet()));
        assertNull(LostTalesLotrFellowshipRequestHook.firstToShareInto(listed,
                null, Collections.<UUID>emptySet()));
    }
}
