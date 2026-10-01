package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipInvitation;
import com.ninuna.losttales.fellowship.model.FellowshipMember;

/** Result of one atomic server-side invitation operation. */
public final class FellowshipInvitationOperationResult {

    private final boolean successful;
    private final boolean changed;
    private final FellowshipErrorId errorId;
    private final Fellowship fellowship;
    private final FellowshipInvitation invitation;
    private final FellowshipMember affectedMember;

    private FellowshipInvitationOperationResult(boolean successful,
                                           boolean changed,
                                           FellowshipErrorId errorId,
                                           Fellowship fellowship,
                                           FellowshipInvitation invitation,
                                           FellowshipMember affectedMember) {
        this.successful = successful;
        this.changed = changed;
        this.errorId = errorId == null
                ? FellowshipErrorId.INTERNAL_ERROR : errorId;
        this.fellowship = fellowship;
        this.invitation = invitation;
        this.affectedMember = affectedMember;
    }

    public static FellowshipInvitationOperationResult success(
            boolean changed,
            Fellowship fellowship,
            FellowshipInvitation invitation,
            FellowshipMember affectedMember) {
        return new FellowshipInvitationOperationResult(
                true,
                changed,
                FellowshipErrorId.NONE,
                fellowship,
                invitation,
                affectedMember);
    }

    public static FellowshipInvitationOperationResult failure(
            FellowshipErrorId errorId,
            boolean changed,
            Fellowship fellowship,
            FellowshipInvitation invitation) {
        if (errorId == null || errorId == FellowshipErrorId.NONE) {
            errorId = FellowshipErrorId.INTERNAL_ERROR;
        }
        return new FellowshipInvitationOperationResult(
                false,
                changed,
                errorId,
                fellowship,
                invitation,
                null);
    }

    public static FellowshipInvitationOperationResult failure(
            FellowshipErrorId errorId,
            Fellowship fellowship,
            FellowshipInvitation invitation) {
        return failure(errorId, false, fellowship, invitation);
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public boolean wasChanged() {
        return this.changed;
    }

    public FellowshipErrorId getErrorId() {
        return this.errorId;
    }

    public Fellowship getFellowship() {
        return this.fellowship;
    }

    public FellowshipInvitation getInvitation() {
        return this.invitation;
    }

    public FellowshipMember getAffectedMember() {
        return this.affectedMember;
    }
}
