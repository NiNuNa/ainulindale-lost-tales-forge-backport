package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.network.packet.LostTalesMapMarkerSnapshotPacket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * The parts of each player's newest snapshot not sent yet (WAY-12). A
 * player holds one snapshot at most: a newer one takes the place of the
 * parts still waiting. Each tick {@link #send} goes round the players, a
 * part each, until the tick's bytes are spent, so a snapshot of many
 * thousand markers is spread over ticks and nobody waits behind it.
 */
final class LostTalesMapMarkerSnapshotOutbox {
    /** Sends a part to a player; false for one no longer online. */
    interface Sender {
        boolean send(UUID playerId, LostTalesMapMarkerSnapshotPacket part);
    }

    private final LinkedHashMap<UUID, ArrayDeque<LostTalesMapMarkerSnapshotPacket>>
            waiting = new LinkedHashMap<UUID,
                    ArrayDeque<LostTalesMapMarkerSnapshotPacket>>();

    /** The player's snapshot, in its parts; whatever of an older one still waits goes. */
    synchronized void queue(UUID playerId,
                            Collection<LostTalesMapMarkerSnapshotPacket> parts) {
        if (playerId == null) {
            return;
        }
        this.waiting.remove(playerId);
        if (parts != null && !parts.isEmpty()) {
            this.waiting.put(playerId,
                    new ArrayDeque<LostTalesMapMarkerSnapshotPacket>(parts));
        }
    }

    /**
     * Sends waiting parts, one per player in turn, while fewer than
     * {@code byteBudget} bytes went this call; the first part always goes,
     * so every call moves on. A player the sender finds gone is forgotten.
     * Returns how many parts went.
     */
    synchronized int send(int byteBudget, Sender sender) {
        int sent = 0;
        int bytes = 0;
        while (!this.waiting.isEmpty() && (sent == 0 || bytes < byteBudget)) {
            List<UUID> round = new ArrayList<UUID>(this.waiting.keySet());
            for (UUID playerId : round) {
                if (sent > 0 && bytes >= byteBudget) {
                    break;
                }
                ArrayDeque<LostTalesMapMarkerSnapshotPacket> parts =
                        this.waiting.remove(playerId);
                LostTalesMapMarkerSnapshotPacket part = parts.poll();
                if (part == null || !sender.send(playerId, part)) {
                    continue;
                }
                sent++;
                bytes += part.measuredBytes();
                if (!parts.isEmpty()) {
                    // Back of the line, so the next part goes to another player.
                    this.waiting.put(playerId, parts);
                }
            }
        }
        return sent;
    }

    /** How many parts wait for the player. */
    synchronized int waitingParts(UUID playerId) {
        ArrayDeque<LostTalesMapMarkerSnapshotPacket> parts =
                this.waiting.get(playerId);
        return parts == null ? 0 : parts.size();
    }

    synchronized boolean isEmpty() {
        return this.waiting.isEmpty();
    }

    synchronized void clear() {
        this.waiting.clear();
    }
}
