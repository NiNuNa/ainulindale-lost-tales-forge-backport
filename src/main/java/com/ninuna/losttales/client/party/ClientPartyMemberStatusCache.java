package com.ninuna.losttales.client.party;

import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import com.ninuna.losttales.party.sync.PartyStatusSnapshot;

/** Client-only runtime party status cache. It is never authoritative. */
public final class ClientPartyMemberStatusCache {

    public static final long STALE_AFTER_MILLIS = 30000L;

    private static PartyStatusSnapshot snapshot;
    private static long receivedAtMillis;

    private ClientPartyMemberStatusCache() {}

    public static synchronized void accept(PartyStatusSnapshot incoming) {
        if (incoming == null) {
            clear();
            return;
        }
        if (snapshot == null
                || incoming.getSynchronizationSequence()
                > snapshot.getSynchronizationSequence()) {
            snapshot = incoming;
            receivedAtMillis = System.currentTimeMillis();
        }
    }

    public static synchronized PartyStatusSnapshot getMatching(
            PartyStateSnapshot partyState) {
        if (!matchesPartyState(snapshot, partyState)) {
            return null;
        }
        return snapshot;
    }

    public static synchronized boolean isStale(
            PartyStateSnapshot partyState) {
        return matchesPartyState(snapshot, partyState)
                && System.currentTimeMillis() - receivedAtMillis
                > STALE_AFTER_MILLIS;
    }

    /**
     * Status and structural state use independent sequence streams. Keep a
     * non-matching status as a possible future/out-of-order packet; renderers
     * expose it only after the structural party context matches exactly.
     */
    public static synchronized void validatePartyState(
            PartyStateSnapshot partyState) {
        // Intentionally no destructive action. getMatching performs the gate.
    }

    public static synchronized PartyStatusSnapshot getSnapshot() {
        return snapshot;
    }

    public static synchronized void clear() {
        snapshot = null;
        receivedAtMillis = 0L;
    }

    private static boolean matchesPartyState(
            PartyStatusSnapshot status,
            PartyStateSnapshot partyState) {
        if (status == null || partyState == null
                || !partyState.isAvailable()
                || partyState.getActiveCharacterId() == null
                || !status.getActiveCharacterId().equals(
                partyState.getActiveCharacterId())) {
            return false;
        }
        if (partyState.getParty() == null) {
            return !status.hasParty();
        }
        return status.hasParty()
                && partyState.getParty().getPartyId().equals(status.getPartyId())
                && partyState.getParty().getRevision()
                == status.getPartyRevision();
    }
}
