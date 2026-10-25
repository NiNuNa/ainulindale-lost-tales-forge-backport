package com.ninuna.losttales.progression;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The hourly cap limits only what acts earn (PROG-08): a quest's skill pay
 * and an operator's give go past it and take nothing from it.
 */
public final class GrantSourceTest {
    private static final String SMITHING = "losttales:smithing";
    private static final int CAP = 100;
    private static final long NOW = 1000000L;

    @Test
    public void onlyAnActIsCapped() {
        assertTrue(GrantSource.ACT.isCapped());
        assertFalse(GrantSource.QUEST.isCapped());
        assertFalse(GrantSource.GIVE.isCapped());
    }

    @Test
    public void actsEarnUpToTheCapInAnHour() {
        RepeatMemory memory = new RepeatMemory();
        assertEquals(60.0D, GrantSource.ACT.allow(memory, SMITHING, 60.0D, CAP, NOW), 0.0D);
        assertEquals(40.0D, GrantSource.ACT.allow(memory, SMITHING, 60.0D, CAP, NOW + 1L), 0.0D);
        assertEquals(0.0D, GrantSource.ACT.allow(memory, SMITHING, 10.0D, CAP, NOW + 2L), 0.0D);
        assertEquals("an hour later the cap is whole again", 10.0D,
                GrantSource.ACT.allow(memory, SMITHING, 10.0D, CAP, NOW + 60L * 60L * 1000L),
                0.0D);
    }

    @Test
    public void aQuestsPayGoesPastTheCap() {
        RepeatMemory memory = new RepeatMemory();
        GrantSource.ACT.allow(memory, SMITHING, CAP, CAP, NOW);
        assertEquals(40.0D, GrantSource.QUEST.allow(memory, SMITHING, 40.0D, CAP, NOW + 1L), 0.0D);
        assertEquals(500.0D, GrantSource.QUEST.allow(memory, SMITHING, 500.0D, CAP, NOW + 2L),
                0.0D);
    }

    @Test
    public void anOperatorsGiveGoesPastTheCap() {
        RepeatMemory memory = new RepeatMemory();
        GrantSource.ACT.allow(memory, SMITHING, CAP, CAP, NOW);
        assertEquals(1000.0D, GrantSource.GIVE.allow(memory, SMITHING, 1000.0D, CAP, NOW + 1L),
                0.0D);
    }

    @Test
    public void questPayAndGiveTakeNothingFromTheHour() {
        RepeatMemory memory = new RepeatMemory();
        GrantSource.QUEST.allow(memory, SMITHING, 500.0D, CAP, NOW);
        GrantSource.GIVE.allow(memory, SMITHING, 500.0D, CAP, NOW + 1L);
        assertEquals("acts still earn the whole cap", 100.0D,
                GrantSource.ACT.allow(memory, SMITHING, 150.0D, CAP, NOW + 2L), 0.0D);
    }

    @Test
    public void noCapHoldsNothingBack() {
        RepeatMemory memory = new RepeatMemory();
        assertEquals(5000.0D, GrantSource.ACT.allow(memory, SMITHING, 5000.0D, 0, NOW), 0.0D);
    }
}
