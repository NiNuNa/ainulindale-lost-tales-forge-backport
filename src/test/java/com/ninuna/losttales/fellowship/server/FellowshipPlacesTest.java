package com.ninuna.losttales.fellowship.server;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A member's place goes to the fellowship only while its map rule is on,
 * and never while the member is concealed or hides on LOTR's map.
 */
public final class FellowshipPlacesTest {

    @Test
    public void aPlaceIsSharedOnlyUnderTheRuleWithNothingHidingIt() {
        assertTrue(FellowshipPlaces.shared(true, false, false));
    }

    @Test
    public void withTheRuleOffNoPlaceIsShared() {
        assertFalse(FellowshipPlaces.shared(false, false, false));
        assertFalse(FellowshipPlaces.shared(false, true, false));
        assertFalse(FellowshipPlaces.shared(false, false, true));
    }

    @Test
    public void aConcealedOrHiddenMemberKeepsTheirPlace() {
        assertFalse(FellowshipPlaces.shared(true, true, false));
        assertFalse(FellowshipPlaces.shared(true, false, true));
        assertFalse(FellowshipPlaces.shared(true, true, true));
    }
}
