package com.ninuna.losttales.fellowship.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A refusal kept by account would tell an inviter that two characters
 * share an account; one not shown accounts hears one neutral refusal for
 * all of them, and every other refusal as it is.
 */
public final class FellowshipInviterRefusalTest {

    private static final FellowshipErrorId[] BY_ACCOUNT = {
            FellowshipErrorId.ACCOUNT_ALREADY_IN_FELLOWSHIP,
            FellowshipErrorId.RECENTLY_DECLINED,
            FellowshipErrorId.TARGET_INVITATIONS_FULL
    };

    @Test
    public void anInviterShownAccountsHearsThePreciseReason() {
        for (FellowshipErrorId refusal : FellowshipErrorId.values()) {
            assertEquals(refusal, FellowshipInvitationCoordinator.toInviter(refusal, true));
        }
    }

    @Test
    public void anInviterNotShownAccountsHearsOneRefusalForEveryAccountReason() {
        for (FellowshipErrorId refusal : BY_ACCOUNT) {
            assertEquals(refusal.name(), FellowshipErrorId.TARGET_CANNOT_BE_INVITED,
                    FellowshipInvitationCoordinator.toInviter(refusal, false));
        }
        assertEquals(FellowshipErrorId.TARGET_NOT_FOUND,
                FellowshipInvitationCoordinator.toInviter(
                        FellowshipErrorId.TARGET_NOT_FOUND, false));
        assertEquals(FellowshipErrorId.INVITATION_ALREADY_EXISTS,
                FellowshipInvitationCoordinator.toInviter(
                        FellowshipErrorId.INVITATION_ALREADY_EXISTS, false));
        assertEquals(FellowshipErrorId.TARGET_ALREADY_MEMBER,
                FellowshipInvitationCoordinator.toInviter(
                        FellowshipErrorId.TARGET_ALREADY_MEMBER, false));
    }

    @Test
    public void theNeutralRefusalTravelsByItsOwnId() {
        assertEquals(FellowshipErrorId.TARGET_CANNOT_BE_INVITED,
                FellowshipErrorId.fromId("target_cannot_be_invited"));
    }
}
