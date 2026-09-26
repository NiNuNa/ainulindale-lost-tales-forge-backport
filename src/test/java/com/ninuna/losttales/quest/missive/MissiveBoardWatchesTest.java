package com.ninuna.losttales.quest.missive;

import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Who watches which missive board, and how long a notice has left on it:
 * the server's side of keeping every page at a board in step. The sweep
 * itself needs a running server; this proves the store and the clock.
 */
public final class MissiveBoardWatchesTest {

    @Before
    @After
    public void startEmpty() {
        MissiveBoardWatches.clear();
    }

    @Test
    public void aPlayerWatchesTheOneBoardLastSentToThem() {
        UUID player = UUID.randomUUID();
        MissiveBoardWatches.watch(player, 0, 4, 65, 9, 11L);
        MissiveBoardWatches.watch(player, -1, 7, 70, 2, 12L);
        assertEquals(1, MissiveBoardWatches.size());
        MissiveBoardWatches.Watch watch = MissiveBoardWatches.watchOf(player);
        assertEquals(-1, watch.dimensionId);
        assertEquals(7, watch.x);
        assertEquals(12L, watch.fingerprint);
    }

    @Test
    public void aCrowdPushesTheOldestWatchOut() {
        UUID first = UUID.randomUUID();
        MissiveBoardWatches.watch(first, 0, 0, 64, 0, 1L);
        for (int index = 0; index < MissiveBoardWatches.MAX_WATCHES; index++) {
            MissiveBoardWatches.watch(UUID.randomUUID(), 0, index, 64, 0, 1L);
        }
        assertEquals(MissiveBoardWatches.MAX_WATCHES, MissiveBoardWatches.size());
        assertNull(MissiveBoardWatches.watchOf(first));
    }

    @Test
    public void forgettingAndClearingEndWatches() {
        UUID player = UUID.randomUUID();
        MissiveBoardWatches.watch(player, 0, 4, 65, 9, 1L);
        MissiveBoardWatches.forget(player);
        assertNull(MissiveBoardWatches.watchOf(player));
        MissiveBoardWatches.watch(player, 0, 4, 65, 9, 1L);
        MissiveBoardWatches.markChanged();
        MissiveBoardWatches.clear();
        assertEquals(0, MissiveBoardWatches.size());
        MissiveBoardWatches.watch(null, 0, 4, 65, 9, 1L);
        assertEquals("nobody watches for no player", 0,
                MissiveBoardWatches.size());
    }

    @Test
    public void aNoticesTimeLeftIsTheBoardsOwnExpiry() {
        LostTalesMissiveData posted = LostTalesMissiveData.builder("q", "kill")
                .title("Posted").generationWorldTime(1000L)
                .objective(new LostTalesMissiveObjectiveData("o", "kill", "",
                        false, null))
                .build();
        assertEquals(6000L, MissiveBoardService.ticksLeft(posted, 8000L, 3000L));
        assertEquals("due already", 0L,
                MissiveBoardService.ticksLeft(posted, 8000L, 20000L));
        assertEquals("expiry off", MissiveNotice.STAYS_UP,
                MissiveBoardService.ticksLeft(posted, 0L, 3000L));
        assertEquals("a letter that cannot be read", MissiveNotice.STAYS_UP,
                MissiveBoardService.ticksLeft(null, 8000L, 3000L));
        assertEquals("posted after the clock", MissiveNotice.STAYS_UP,
                MissiveBoardService.ticksLeft(posted, 8000L, 500L));
        LostTalesMissiveData undated = LostTalesMissiveData.builder("q", "kill")
                .title("Undated")
                .objective(new LostTalesMissiveObjectiveData("o", "kill", "",
                        false, null))
                .build();
        assertEquals("no time it was posted at", MissiveNotice.STAYS_UP,
                MissiveBoardService.ticksLeft(undated, 8000L, 3000L));
    }
}
