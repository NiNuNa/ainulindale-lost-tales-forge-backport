package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.network.packet.LostTalesMapMarkerSnapshotPacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The parts of each player's snapshot go out over ticks, round the players (WAY-12). */
public final class LostTalesMapMarkerSnapshotOutboxTest {
    private static final UUID ALDRIC =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TOBIN =
            UUID.fromString("10000000-0000-0000-0000-000000000002");

    private final LostTalesMapMarkerSnapshotOutbox outbox =
            new LostTalesMapMarkerSnapshotOutbox();

    /**
     * Fifty thousand markers for one player go out a few parts a tick, so
     * no tick writes much more than its bytes, and they arrive in order.
     */
    @Test
    public void manyMarkersAreSpreadOverTicks() {
        List<LostTalesMapMarkerSnapshotPacket> parts =
                LostTalesMapMarkerSnapshotPacket.split(1, markers(50000));
        this.outbox.queue(ALDRIC, parts);
        Recorder recorder = new Recorder();

        int ticks = 0;
        int budget = LostTalesMapMarkerSyncManager.BYTES_PER_TICK;
        while (!this.outbox.isEmpty()) {
            int before = recorder.bytes;
            int sent = this.outbox.send(budget, recorder);
            assertTrue(sent >= 1);
            assertTrue("a tick sends its bytes and at most one part more",
                    recorder.bytes - before
                            < budget + LostTalesMapMarkerSnapshotPacket.MAX_PART_BYTES);
            ticks++;
        }

        assertEquals(parts.size(), recorder.parts.size());
        for (int at = 0; at < parts.size(); at++) {
            assertEquals(at, recorder.parts.get(at).getIndex());
        }
        assertTrue("spread over several ticks: " + ticks, ticks > 1);
        assertTrue("and done within a second or so: " + ticks, ticks <= 25);
    }

    /** A newer snapshot takes the place of the parts of an older one still waiting. */
    @Test
    public void aNewerSnapshotReplacesTheWaitingParts() {
        this.outbox.queue(ALDRIC,
                LostTalesMapMarkerSnapshotPacket.split(1, markers(6000)));
        Recorder recorder = new Recorder();
        this.outbox.send(1, recorder);
        assertEquals(2, this.outbox.waitingParts(ALDRIC));

        this.outbox.queue(ALDRIC,
                LostTalesMapMarkerSnapshotPacket.split(2, markers(10)));

        assertEquals(1, this.outbox.waitingParts(ALDRIC));
        this.outbox.send(Integer.MAX_VALUE, recorder);
        assertEquals(2, recorder.parts.size());
        assertEquals(2, recorder.parts.get(1).getSequence());
        assertTrue(this.outbox.isEmpty());
    }

    /** Players take turns, a part each, so a small snapshot never waits behind a large one. */
    @Test
    public void playersTakeTurns() {
        this.outbox.queue(ALDRIC,
                LostTalesMapMarkerSnapshotPacket.split(1, markers(6000)));
        this.outbox.queue(TOBIN,
                LostTalesMapMarkerSnapshotPacket.split(2, markers(10)));
        Recorder recorder = new Recorder();

        this.outbox.send(1, recorder);
        this.outbox.send(1, recorder);

        assertEquals(ALDRIC, recorder.players.get(0));
        assertEquals("Tobin's one part goes second", TOBIN,
                recorder.players.get(1));
        assertEquals(0, this.outbox.waitingParts(TOBIN));
        assertEquals(2, this.outbox.waitingParts(ALDRIC));
    }

    /** A player no longer online is forgotten with every part waiting for them. */
    @Test
    public void aPlayerWhoLeftIsForgotten() {
        this.outbox.queue(ALDRIC,
                LostTalesMapMarkerSnapshotPacket.split(1, markers(6000)));
        this.outbox.queue(TOBIN,
                LostTalesMapMarkerSnapshotPacket.split(2, markers(10)));
        Recorder recorder = new Recorder();
        recorder.gone.add(ALDRIC);

        this.outbox.send(Integer.MAX_VALUE, recorder);

        assertEquals(Collections.singletonList(TOBIN), recorder.players);
        assertEquals(0, this.outbox.waitingParts(ALDRIC));
        assertTrue(this.outbox.isEmpty());
    }

    @Test
    public void clearingForgetsEveryPart() {
        this.outbox.queue(ALDRIC,
                LostTalesMapMarkerSnapshotPacket.split(1, markers(10)));
        this.outbox.clear();

        assertTrue(this.outbox.isEmpty());
        assertEquals(0, this.outbox.send(Integer.MAX_VALUE, new Recorder()));
    }

    private static final class Recorder
            implements LostTalesMapMarkerSnapshotOutbox.Sender {
        private final List<LostTalesMapMarkerSnapshotPacket> parts =
                new ArrayList<LostTalesMapMarkerSnapshotPacket>();
        private final List<UUID> players = new ArrayList<UUID>();
        private final Set<UUID> gone = new LinkedHashSet<UUID>();
        private int bytes;

        @Override
        public boolean send(UUID playerId,
                            LostTalesMapMarkerSnapshotPacket part) {
            if (this.gone.contains(playerId)) {
                return false;
            }
            this.parts.add(part);
            this.players.add(playerId);
            this.bytes += part.measuredBytes();
            return true;
        }
    }

    private static List<LostTalesMapMarkerDefinition> markers(int count) {
        List<LostTalesMapMarkerDefinition> markers =
                new ArrayList<LostTalesMapMarkerDefinition>(count);
        for (int at = 0; at < count; at++) {
            markers.add(new LostTalesMapMarkerDefinition(
                    "losttales:player/" + at, "Waystone", "shack", "white",
                    "", "A place by the road where travellers rest.", true,
                    100, at, 64.0D, -at, 128.0D, 8.0D, true, true, false,
                    LostTalesMapMarkerSource.PLAYER_CREATED, true,
                    "losttales:player_placed", 0));
        }
        return markers;
    }
}
