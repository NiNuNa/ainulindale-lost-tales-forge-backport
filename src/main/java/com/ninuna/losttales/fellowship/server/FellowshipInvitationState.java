package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipInvitation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the server lets the identity a player plays see of the fellowships:
 * the ones it is in, the one it travels with, whether it may make another,
 * the invitations to it, and the invitations sent by the fellowships it
 * leads or guides, each with its fellowship's name.
 */
public final class FellowshipInvitationState {

    private final boolean successful;
    private final FellowshipErrorId errorId;
    private final UUID activeIdentityId;
    private final List<Fellowship> fellowships;
    private final UUID travellingFellowshipId;
    private final FellowshipErrorId createRefusal;
    private final List<FellowshipInvitation> incomingInvitations;
    private final List<FellowshipInvitation> outgoingInvitations;
    private final Map<UUID, String> fellowshipNames;

    private FellowshipInvitationState(boolean successful, FellowshipErrorId errorId,
                                      UUID activeIdentityId, List<Fellowship> fellowships,
                                      UUID travellingFellowshipId,
                                      FellowshipErrorId createRefusal,
                                      List<FellowshipInvitation> incomingInvitations,
                                      List<FellowshipInvitation> outgoingInvitations,
                                      Map<UUID, String> fellowshipNames) {
        this.successful = successful;
        this.errorId = errorId == null ? FellowshipErrorId.INTERNAL_ERROR : errorId;
        this.activeIdentityId = activeIdentityId;
        this.fellowships = fellowships == null || fellowships.isEmpty()
                ? Collections.<Fellowship>emptyList()
                : Collections.unmodifiableList(new ArrayList<Fellowship>(fellowships));
        this.travellingFellowshipId = travellingFellowshipId;
        this.createRefusal = createRefusal == null ? FellowshipErrorId.NONE : createRefusal;
        this.incomingInvitations = immutableCopy(incomingInvitations);
        this.outgoingInvitations = immutableCopy(outgoingInvitations);
        this.fellowshipNames = fellowshipNames == null || fellowshipNames.isEmpty()
                ? Collections.<UUID, String>emptyMap()
                : Collections.unmodifiableMap(new HashMap<UUID, String>(fellowshipNames));
    }

    public static FellowshipInvitationState success(
            UUID activeIdentityId, List<Fellowship> fellowships,
            UUID travellingFellowshipId, FellowshipErrorId createRefusal,
            List<FellowshipInvitation> incomingInvitations,
            List<FellowshipInvitation> outgoingInvitations,
            Map<UUID, String> fellowshipNames) {
        return new FellowshipInvitationState(true, FellowshipErrorId.NONE,
                activeIdentityId, fellowships, travellingFellowshipId, createRefusal,
                incomingInvitations, outgoingInvitations, fellowshipNames);
    }

    public static FellowshipInvitationState failure(FellowshipErrorId errorId) {
        if (errorId == null || errorId == FellowshipErrorId.NONE) {
            errorId = FellowshipErrorId.INTERNAL_ERROR;
        }
        return new FellowshipInvitationState(false, errorId, null, null, null, null,
                null, null, null);
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public FellowshipErrorId getErrorId() {
        return this.errorId;
    }

    public UUID getActiveIdentityId() {
        return this.activeIdentityId;
    }

    /** The fellowships the identity is in, in the order it joined them. */
    public List<Fellowship> getFellowships() {
        return this.fellowships;
    }

    /** The fellowship the identity travels with; null while it is in none. */
    public UUID getTravellingFellowshipId() {
        return this.travellingFellowshipId;
    }

    /** Why the identity may not make a fellowship now; NONE when it may. */
    public FellowshipErrorId getCreateRefusal() {
        return this.createRefusal;
    }

    public List<FellowshipInvitation> getIncomingInvitations() {
        return this.incomingInvitations;
    }

    public List<FellowshipInvitation> getOutgoingInvitations() {
        return this.outgoingInvitations;
    }

    /** The name of a fellowship an invitation here names; null for none. */
    public String getFellowshipName(UUID fellowshipId) {
        return fellowshipId == null ? null : this.fellowshipNames.get(fellowshipId);
    }

    private static List<FellowshipInvitation> immutableCopy(
            List<FellowshipInvitation> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<FellowshipInvitation>(source));
    }
}
