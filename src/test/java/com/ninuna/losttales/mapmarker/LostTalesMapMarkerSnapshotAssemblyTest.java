package com.ninuna.losttales.mapmarker;

import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** A client puts a snapshot back together from its parts (WAY-12). */
public final class LostTalesMapMarkerSnapshotAssemblyTest {
    private final LostTalesMapMarkerSnapshotAssembly assembly =
            new LostTalesMapMarkerSnapshotAssembly();

    /** Nothing comes out before the last part; then every marker at once, in order. */
    @Test
    public void theSnapshotComesOutWholeWithItsLastPart() {
        assertNull(this.assembly.accept(5, 0, 3, part("a", "b")));
        assertTrue(this.assembly.isCollecting());
        assertNull(this.assembly.accept(5, 1, 3, part("c")));

        List<LostTalesMapMarkerDefinition> whole =
                this.assembly.accept(5, 2, 3, part("d", "e"));

        assertNotNull(whole);
        assertEquals("abcde", ids(whole));
        assertFalse(this.assembly.isCollecting());
    }

    @Test
    public void aSnapshotOfOnePartComesOutAtOnce() {
        assertEquals("a", ids(this.assembly.accept(1, 0, 1, part("a"))));
        assertEquals("", ids(this.assembly.accept(2, 0, 1,
                Collections.<LostTalesMapMarkerDefinition>emptyList())));
    }

    /** A newer snapshot's first part drops the one not finished. */
    @Test
    public void aNewerSnapshotDropsAnUnfinishedOne() {
        this.assembly.accept(5, 0, 3, part("a"));
        this.assembly.accept(5, 1, 3, part("b"));

        assertNull(this.assembly.accept(6, 0, 2, part("x")));
        assertNull("the older one's last part is too late",
                this.assembly.accept(5, 2, 3, part("c")));
        assertEquals("xy", ids(this.assembly.accept(6, 1, 2, part("y"))));
    }

    /** A part of a newer snapshot that is not its first drops the older one, and the rest of it is ignored. */
    @Test
    public void aNewerSnapshotMissingItsFirstPartDropsBoth() {
        this.assembly.accept(5, 0, 2, part("a"));

        assertNull(this.assembly.accept(6, 1, 3, part("x")));
        assertFalse(this.assembly.isCollecting());
        assertNull(this.assembly.accept(5, 1, 2, part("b")));
        assertNull(this.assembly.accept(6, 2, 3, part("y")));
        assertEquals("next", ids(this.assembly.accept(7, 0, 1, part("next"))));
    }

    /** A part out of turn, of another count, or a first part again drops the snapshot. */
    @Test
    public void aPartThatDoesNotFitDropsItsSnapshot() {
        this.assembly.accept(5, 0, 3, part("a"));
        assertNull("out of turn", this.assembly.accept(5, 2, 3, part("c")));
        assertFalse(this.assembly.isCollecting());
        assertNull(this.assembly.accept(5, 1, 3, part("b")));

        this.assembly.accept(8, 0, 3, part("a"));
        assertNull("another count", this.assembly.accept(8, 1, 2, part("b")));
        assertFalse(this.assembly.isCollecting());

        this.assembly.accept(9, 0, 2, part("a"));
        assertNull("its first part again",
                this.assembly.accept(9, 0, 2, part("a")));
        assertFalse(this.assembly.isCollecting());

        assertNull("an index past the count",
                this.assembly.accept(10, 2, 2, part("a")));
        assertEquals("ok", ids(this.assembly.accept(11, 0, 1, part("ok"))));
    }

    /** A part of an older snapshot is ignored and leaves the newer one collecting. */
    @Test
    public void anOlderPartIsIgnored() {
        this.assembly.accept(5, 0, 2, part("a"));

        assertNull(this.assembly.accept(4, 0, 1, part("old")));
        assertTrue(this.assembly.isCollecting());
        assertEquals("ab", ids(this.assembly.accept(5, 1, 2, part("b"))));
    }

    /** Sequences that went round past the largest number still count on. */
    @Test
    public void theSequenceGoesRound() {
        assertEquals("a", ids(this.assembly.accept(
                Integer.MAX_VALUE, 0, 1, part("a"))));
        assertEquals("b", ids(this.assembly.accept(
                Integer.MIN_VALUE, 0, 1, part("b"))));
        assertNull(this.assembly.accept(Integer.MAX_VALUE, 0, 1, part("c")));
    }

    /** A new connection starts over: the next server counts from the start. */
    @Test
    public void clearingForgetsEverySnapshot() {
        this.assembly.accept(40, 0, 2, part("a"));
        this.assembly.clear();

        assertFalse(this.assembly.isCollecting());
        assertEquals("b", ids(this.assembly.accept(1, 0, 1, part("b"))));
    }

    private static List<LostTalesMapMarkerDefinition> part(String... ids) {
        java.util.ArrayList<LostTalesMapMarkerDefinition> markers =
                new java.util.ArrayList<LostTalesMapMarkerDefinition>();
        for (String id : ids) {
            markers.add(new LostTalesMapMarkerDefinition(id, id, "fort",
                    "white", 0, 0.0D, 64.0D, 0.0D, false));
        }
        return markers;
    }

    private static String ids(List<LostTalesMapMarkerDefinition> markers) {
        assertNotNull(markers);
        StringBuilder ids = new StringBuilder();
        for (LostTalesMapMarkerDefinition marker : markers) {
            ids.append(marker.getId());
        }
        return ids.toString();
    }
}
