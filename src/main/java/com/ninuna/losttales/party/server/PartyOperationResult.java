package com.ninuna.losttales.party.server;

import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;

/** Result of one atomic server-side party operation. */
public final class PartyOperationResult {

    private final boolean successful;
    private final boolean changed;
    private final boolean partyDisbanded;
    private final PartyErrorId errorId;
    private final Party party;
    private final PartyMember affectedMember;

    private PartyOperationResult(boolean successful, boolean changed,
                                 boolean partyDisbanded, PartyErrorId errorId,
                                 Party party, PartyMember affectedMember) {
        this.successful = successful;
        this.changed = changed;
        this.partyDisbanded = partyDisbanded;
        this.errorId = errorId == null ? PartyErrorId.INTERNAL_ERROR : errorId;
        this.party = party;
        this.affectedMember = affectedMember;
    }

    public static PartyOperationResult success(boolean changed, Party party,
                                               PartyMember affectedMember) {
        return new PartyOperationResult(true, changed, false,
                PartyErrorId.NONE, party, affectedMember);
    }

    public static PartyOperationResult disbanded(PartyMember affectedMember) {
        return new PartyOperationResult(true, true, true,
                PartyErrorId.NONE, null, affectedMember);
    }

    public static PartyOperationResult failure(PartyErrorId errorId, Party party) {
        if (errorId == null || errorId == PartyErrorId.NONE) {
            errorId = PartyErrorId.INTERNAL_ERROR;
        }
        return new PartyOperationResult(false, false, false,
                errorId, party, null);
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public boolean wasChanged() {
        return this.changed;
    }

    public boolean wasPartyDisbanded() {
        return this.partyDisbanded;
    }

    public PartyErrorId getErrorId() {
        return this.errorId;
    }

    public Party getParty() {
        return this.party;
    }

    public PartyMember getAffectedMember() {
        return this.affectedMember;
    }
}
