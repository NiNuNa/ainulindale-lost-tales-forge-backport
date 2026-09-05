package com.ninuna.losttales.compat.lotr.hired;

import com.ninuna.losttales.compat.lotr.hired.LotrHiredUnitCustodyRule.Action;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

/**
 * A unit is released to its owner while the identity that hired it is
 * active, parked under the owner's parking UUID otherwise, and left alone
 * when it already stands where the rule wants it.
 */
public final class LotrHiredUnitCustodyRuleTest {

    private static final UUID OWNER = UUID.fromString(
            "e4000000-0000-0000-0000-00000000004e");
    private static final UUID CHARACTER = UUID.fromString(
            "f4000000-0000-0000-0000-00000000004f");
    private static final UUID PARKED = LotrHiredUnitCustodyRule.parkedUuid(OWNER);
    private static final String CHARACTER_KEY = LotrHiredUnitTag.identityKey(CHARACTER);
    private static final String ACCOUNT_KEY = LotrHiredUnitTag.ACCOUNT_KEY;

    @Test
    public void theParkingUuidIsStableAndNobodys() {
        assertEquals(PARKED, LotrHiredUnitCustodyRule.parkedUuid(OWNER));
        assertNotEquals(OWNER, PARKED);
        assertNotEquals(PARKED, LotrHiredUnitCustodyRule.parkedUuid(CHARACTER));
        assertFalse(PARKED.equals(LotrHiredUnitCustodyRule.parkedUuid(
                UUID.fromString("e4000000-0000-0000-0000-00000000004d"))));
    }

    @Test
    public void theActiveIdentitysUnitIsReleasedOnceAndLeftAfter() {
        assertEquals(Action.RELEASE, LotrHiredUnitCustodyRule.decide(
                CHARACTER_KEY, CHARACTER_KEY, OWNER, PARKED));
        assertEquals(Action.LEAVE, LotrHiredUnitCustodyRule.decide(
                CHARACTER_KEY, CHARACTER_KEY, OWNER, OWNER));
        assertEquals(Action.RELEASE, LotrHiredUnitCustodyRule.decide(
                ACCOUNT_KEY, ACCOUNT_KEY, OWNER, PARKED));
    }

    @Test
    public void anotherIdentitysUnitIsParkedOnceAndLeftAfter() {
        assertEquals(Action.PARK, LotrHiredUnitCustodyRule.decide(
                CHARACTER_KEY, ACCOUNT_KEY, OWNER, OWNER));
        assertEquals(Action.LEAVE, LotrHiredUnitCustodyRule.decide(
                CHARACTER_KEY, ACCOUNT_KEY, OWNER, PARKED));
        assertEquals(Action.PARK, LotrHiredUnitCustodyRule.decide(
                ACCOUNT_KEY, CHARACTER_KEY, OWNER, OWNER));
    }

    @Test
    public void noActiveIdentityParksEverything() {
        assertEquals(Action.PARK, LotrHiredUnitCustodyRule.decide(
                CHARACTER_KEY, null, OWNER, OWNER));
        assertEquals(Action.PARK, LotrHiredUnitCustodyRule.decide(
                ACCOUNT_KEY, null, OWNER, OWNER));
        assertEquals(Action.LEAVE, LotrHiredUnitCustodyRule.decide(
                ACCOUNT_KEY, null, OWNER, PARKED));
    }

    @Test
    public void theActionNamesTheUuidTheUnitEndsUpUnder() {
        assertEquals(OWNER, LotrHiredUnitCustodyRule.hiringUuidAfter(
                Action.RELEASE, OWNER, PARKED));
        assertEquals(PARKED, LotrHiredUnitCustodyRule.hiringUuidAfter(
                Action.PARK, OWNER, OWNER));
        assertEquals(CHARACTER, LotrHiredUnitCustodyRule.hiringUuidAfter(
                Action.LEAVE, OWNER, CHARACTER));
    }
}
