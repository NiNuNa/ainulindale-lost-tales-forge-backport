package com.ninuna.losttales.party.server;

import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyInvitation;
import com.ninuna.losttales.party.model.PartyMember;

/** Result of one atomic server-side invitation operation. */
public final class PartyInvitationOperationResult {

    private final boolean successful;
    private final boolean changed;
    private final PartyErrorId errorId;
    private final Party party;
    private final PartyInvitation invitation;
    private final PartyMember affectedMember;

    private PartyInvitationOperationResult(boolean successful,
                                           boolean changed,
                                           PartyErrorId errorId,
                                           Party party,
                                           PartyInvitation invitation,
                                           PartyMember affectedMember) {
        this.successful = successful;
        this.changed = changed;
        this.errorId = errorId == null
                ? PartyErrorId.INTERNAL_ERROR : errorId;
        this.party = party;
        this.invitation = invitation;
        this.affectedMember = affectedMember;
    }

    public static PartyInvitationOperationResult success(
            boolean changed,
            Party party,
            PartyInvitation invitation,
            PartyMember affectedMember) {
        return new PartyInvitationOperationResult(
                true,
                changed,
                PartyErrorId.NONE,
                party,
                invitation,
                affectedMember);
    }

    public static PartyInvitationOperationResult failure(
            PartyErrorId errorId,
            boolean changed,
            Party party,
            PartyInvitation invitation) {
        if (errorId == null || errorId == PartyErrorId.NONE) {
            errorId = PartyErrorId.INTERNAL_ERROR;
        }
        return new PartyInvitationOperationResult(
                false,
                changed,
                errorId,
                party,
                invitation,
                null);
    }

    public static PartyInvitationOperationResult failure(
            PartyErrorId errorId,
            Party party,
            PartyInvitation invitation) {
        return failure(errorId, false, party, invitation);
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public boolean wasChanged() {
        return this.changed;
    }

    public PartyErrorId getErrorId() {
        return this.errorId;
    }

    public Party getParty() {
        return this.party;
    }

    public PartyInvitation getInvitation() {
        return this.invitation;
    }

    public PartyMember getAffectedMember() {
        return this.affectedMember;
    }
}
