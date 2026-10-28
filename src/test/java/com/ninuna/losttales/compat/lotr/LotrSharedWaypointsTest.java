package com.ninuna.losttales.compat.lotr;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A copy that reaches an account through two fellowships stays while either still shares it. */
public final class LotrSharedWaypointsTest {

    private static final UUID OWNER = UUID.fromString(
            "a1000000-0000-0000-0000-0000000000a1");
    private static final UUID READER = UUID.fromString(
            "a2000000-0000-0000-0000-0000000000a2");
    private static final UUID ENDING = UUID.fromString(
            "a3000000-0000-0000-0000-0000000000a3");
    private static final UUID STAYING = UUID.fromString(
            "a4000000-0000-0000-0000-0000000000a4");

    @Test
    public void anotherFellowshipStillHandingTheCopyKeepsIt() {
        LotrSharedWaypoints.Key key = new LotrSharedWaypoints.Key(OWNER, 7);
        Map<UUID, LotrSharedWaypoints.Handed> handed = handed(key, READER);

        assertTrue(LotrSharedWaypoints.handedElsewhere(handed, READER, key, ENDING));
    }

    @Test
    public void theEndingFellowshipAloneDoesNotKeepIt() {
        LotrSharedWaypoints.Key key = new LotrSharedWaypoints.Key(OWNER, 7);
        Map<UUID, LotrSharedWaypoints.Handed> handed =
                new HashMap<UUID, LotrSharedWaypoints.Handed>();
        handed.put(ENDING, new LotrSharedWaypoints.Handed(
                set(READER), Collections.singleton(key)));

        assertFalse(LotrSharedWaypoints.handedElsewhere(handed, READER, key, ENDING));
    }

    @Test
    public void anotherCopyOrAnotherReaderDoesNotKeepIt() {
        LotrSharedWaypoints.Key key = new LotrSharedWaypoints.Key(OWNER, 7);
        LotrSharedWaypoints.Key other = new LotrSharedWaypoints.Key(OWNER, 8);
        Map<UUID, LotrSharedWaypoints.Handed> handed = handed(other, READER);

        assertFalse(LotrSharedWaypoints.handedElsewhere(handed, READER, key, ENDING));
        assertFalse(LotrSharedWaypoints.handedElsewhere(
                handed(key, OWNER), READER, key, ENDING));
    }

    private static Map<UUID, LotrSharedWaypoints.Handed> handed(
            LotrSharedWaypoints.Key key, UUID recipient) {
        Map<UUID, LotrSharedWaypoints.Handed> handed =
                new HashMap<UUID, LotrSharedWaypoints.Handed>();
        handed.put(ENDING, new LotrSharedWaypoints.Handed(
                set(READER), Collections.singleton(key)));
        handed.put(STAYING, new LotrSharedWaypoints.Handed(
                set(recipient), Collections.singleton(key)));
        return handed;
    }

    private static Set<UUID> set(UUID account) {
        Set<UUID> accounts = new HashSet<UUID>();
        accounts.add(account);
        return accounts;
    }
}
