package com.ninuna.losttales.quest.missive;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * How long a notice has left on its board: the clock every page at a
 * board shows. The watches' sweep needs a running server.
 */
public final class MissiveBoardWatchesTest {

    @Before
    @After
    public void startEmpty() {
        MissiveBoardWatches.clear();
    }

    @Test
    public void aNoticesTimeLeftIsTheBoardsOwnExpiry() {
        LostTalesMissiveData posted = LostTalesMissiveData.builder("q", "kill")
                .titleId("dangerous_work").generationWorldTime(1000L)
                .objective(new LostTalesMissiveObjectiveData("o", "kill", false, null))
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
                .titleId("dangerous_work")
                .objective(new LostTalesMissiveObjectiveData("o", "kill", false, null))
                .build();
        assertEquals("no time it was posted at", MissiveNotice.STAYS_UP,
                MissiveBoardService.ticksLeft(undated, 8000L, 3000L));
    }
}
