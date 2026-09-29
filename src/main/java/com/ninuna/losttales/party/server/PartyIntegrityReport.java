package com.ninuna.losttales.party.server;

/**
 * What the party stores hold that no longer stands: members whose character
 * is gone or belongs to another account, invitations that ran out or no
 * longer stand, and go-here markers whose owner or dimension is gone. A
 * check counts what a repair would remove; a repair counts what it removed.
 */
public final class PartyIntegrityReport {

    private final int members;
    private final int invitations;
    private final int markers;

    PartyIntegrityReport(int members, int invitations, int markers) {
        this.members = members;
        this.invitations = invitations;
        this.markers = markers;
    }

    public int getMembers() {
        return this.members;
    }

    public int getInvitations() {
        return this.invitations;
    }

    public int getMarkers() {
        return this.markers;
    }

    public boolean isClean() {
        return this.members == 0 && this.invitations == 0 && this.markers == 0;
    }
}
