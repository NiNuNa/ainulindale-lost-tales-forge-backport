package com.ninuna.losttales.compat.lotr;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Who LOTR hides from its map, for a player who is not an operator: an
 * admin hidden from the map always; everyone at *Force map locations* 1;
 * nobody else at 2; otherwise whoever hides their own place.
 */
public final class LotrMapLocationsTest {
    private static final int AS_PLAYERS_CHOOSE = 0;

    @Test
    public void aPlayerHidesTheirOwnPlace() {
        assertFalse(LotrMapLocations.hidden(AS_PLAYERS_CHOOSE, false, false));
        assertTrue(LotrMapLocations.hidden(AS_PLAYERS_CHOOSE, true, false));
    }

    @Test
    public void anAdminHiddenFromTheMapIsAlwaysHidden() {
        assertTrue(LotrMapLocations.hidden(AS_PLAYERS_CHOOSE, false, true));
        assertTrue(LotrMapLocations.hidden(LotrMapLocations.FORCE_SHOWN, false, true));
        assertTrue(LotrMapLocations.hidden(LotrMapLocations.FORCE_HIDDEN, false, true));
    }

    @Test
    public void forcedHiddenHidesEveryone() {
        assertTrue(LotrMapLocations.hidden(LotrMapLocations.FORCE_HIDDEN, false, false));
        assertTrue(LotrMapLocations.hidden(LotrMapLocations.FORCE_HIDDEN, true, false));
    }

    @Test
    public void forcedShownShowsEveryoneButAnAdminHidden() {
        assertFalse(LotrMapLocations.hidden(LotrMapLocations.FORCE_SHOWN, false, false));
        assertFalse(LotrMapLocations.hidden(LotrMapLocations.FORCE_SHOWN, true, false));
    }

    @Test
    public void anUnknownForceValueLeavesItToThePlayer() {
        assertFalse(LotrMapLocations.hidden(7, false, false));
        assertTrue(LotrMapLocations.hidden(7, true, false));
    }

    @Test
    public void noPlayerCountsAsHidden() {
        assertTrue(LotrMapLocations.hides(null));
    }
}
