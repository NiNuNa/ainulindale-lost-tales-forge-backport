package com.ninuna.losttales.mapmarker;

import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The server's own hold on a waystone page: Destinations opens a window
 * of two minutes to travel from that very waystone, one pick closes it,
 * and using a waystone alone opens none.
 */
public final class LostTalesWaystoneVisitsTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final String BREE = "losttales:player/bree";

    @After
    public void forgetEverything() {
        LostTalesWaystoneVisits.clear();
    }

    @Test
    public void destinationsOpensOneTravelWithinTwoMinutes() {
        LostTalesWaystoneVisits.destinations(PLAYER, BREE, 100, 1, 64, 2, 1000L);
        assertTrue(LostTalesWaystoneVisits.takeTravel(PLAYER, BREE, 100, 1,
                64, 2, 1000L + LostTalesWaystoneVisits.TRAVEL_WINDOW_MILLIS));
        assertFalse("one pick is all the window gives",
                LostTalesWaystoneVisits.takeTravel(PLAYER, BREE, 100, 1, 64,
                        2, 2000L));
        assertEquals("the visit stays for the page", BREE,
                LostTalesWaystoneVisits.get(PLAYER).getMarkerId());
    }

    @Test
    public void aLatePickOrAnotherWaystoneFindsTheWindowClosed() {
        LostTalesWaystoneVisits.destinations(PLAYER, BREE, 100, 1, 64, 2, 0L);
        assertFalse("past two minutes", LostTalesWaystoneVisits.takeTravel(
                PLAYER, BREE, 100, 1, 64, 2,
                LostTalesWaystoneVisits.TRAVEL_WINDOW_MILLIS + 1L));

        LostTalesWaystoneVisits.destinations(PLAYER, BREE, 100, 1, 64, 2, 0L);
        assertFalse("another block", LostTalesWaystoneVisits.takeTravel(
                PLAYER, BREE, 100, 1, 65, 2, 0L));
        assertFalse("the window went with the wrong pick",
                LostTalesWaystoneVisits.takeTravel(PLAYER, BREE, 100, 1, 64,
                        2, 0L));

        LostTalesWaystoneVisits.destinations(PLAYER, BREE, 100, 1, 64, 2, 0L);
        assertFalse("another player", LostTalesWaystoneVisits.takeTravel(
                UUID.randomUUID(), BREE, 100, 1, 64, 2, 0L));
    }

    @Test
    public void usingAWaystoneOpensNoWindow() {
        LostTalesWaystoneVisits.opened(PLAYER, BREE, 100, 1, 64, 2);
        assertFalse(LostTalesWaystoneVisits.takeTravel(PLAYER, BREE, 100, 1,
                64, 2, 0L));
        LostTalesWaystoneVisits.forget(PLAYER);
        assertNull(LostTalesWaystoneVisits.get(PLAYER));
    }
}
