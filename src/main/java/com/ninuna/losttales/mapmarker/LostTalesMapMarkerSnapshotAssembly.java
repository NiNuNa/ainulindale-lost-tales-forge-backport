package com.ninuna.losttales.mapmarker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Puts a snapshot of markers back together from its parts, as a client
 * receives them (WAY-12). Parts come in order: the first part of a newer
 * snapshot starts it and drops one not finished; each next part must be
 * the next of the snapshot being collected, with the same part count. The
 * whole snapshot comes out only with its last part, so the markers shown
 * change all at once or not at all. A part that does not fit drops the
 * snapshot it claims to belong to; a part of an older snapshot is ignored.
 * Cleared as the client leaves the server.
 */
public final class LostTalesMapMarkerSnapshotAssembly {
    private boolean seen;
    private int sequence;
    private int total;
    private int received;
    private List<LostTalesMapMarkerDefinition> collected;

    /**
     * Takes one part. Returns every marker of its snapshot when it is that
     * snapshot's last part, and null otherwise.
     */
    public synchronized List<LostTalesMapMarkerDefinition> accept(
            int partSequence, int index, int partTotal,
            Collection<LostTalesMapMarkerDefinition> markers) {
        if (this.seen && isOlder(partSequence, this.sequence)) {
            return null;
        }
        if (index < 0 || partTotal < 1 || index >= partTotal) {
            drop(partSequence);
            return null;
        }
        if (index == 0) {
            if (this.seen && partSequence == this.sequence) {
                drop(partSequence);
                return null;
            }
            this.seen = true;
            this.sequence = partSequence;
            this.total = partTotal;
            this.received = 0;
            this.collected = new ArrayList<LostTalesMapMarkerDefinition>();
        } else if (this.collected == null || partSequence != this.sequence
                || partTotal != this.total || index != this.received) {
            drop(partSequence);
            return null;
        }
        if (markers != null) {
            this.collected.addAll(markers);
        }
        this.received++;
        if (this.received < this.total) {
            return null;
        }
        List<LostTalesMapMarkerDefinition> whole =
                Collections.unmodifiableList(this.collected);
        this.collected = null;
        return whole;
    }

    /** Whether parts of a snapshot are waiting for the rest. */
    public synchronized boolean isCollecting() {
        return this.collected != null;
    }

    /** Forgets every part and every snapshot seen, as a new connection starts. */
    public synchronized void clear() {
        this.seen = false;
        this.sequence = 0;
        this.total = 0;
        this.received = 0;
        this.collected = null;
    }

    /**
     * The snapshot being collected is broken or overtaken: it goes, and
     * {@code partSequence} counts as seen, so the rest of its parts are
     * ignored too.
     */
    private void drop(int partSequence) {
        this.collected = null;
        this.seen = true;
        this.sequence = partSequence;
    }

    /** Whether {@code candidate} came before {@code reference}, counting round past the largest number. */
    private static boolean isOlder(int candidate, int reference) {
        return candidate - reference < 0;
    }
}
