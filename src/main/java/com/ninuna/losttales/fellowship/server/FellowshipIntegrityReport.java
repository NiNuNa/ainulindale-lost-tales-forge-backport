package com.ninuna.losttales.fellowship.server;

/**
 * What the fellowship stores hold that no longer stands: members whose character
 * is gone or belongs to another account, invitations that ran out or no
 * longer stand, and marks whose world, owner or fellowship is gone. A check
 * counts what a repair would remove; a repair counts what it removed.
 */
public final class FellowshipIntegrityReport {

    private final int members;
    private final int invitations;
    private final int marks;

    FellowshipIntegrityReport(int members, int invitations, int marks) {
        this.members = members;
        this.invitations = invitations;
        this.marks = marks;
    }

    public int getMembers() {
        return this.members;
    }

    public int getInvitations() {
        return this.invitations;
    }

    public int getMarks() {
        return this.marks;
    }

    public boolean isClean() {
        return this.members == 0 && this.invitations == 0 && this.marks == 0;
    }
}
