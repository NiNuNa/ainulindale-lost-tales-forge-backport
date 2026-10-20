package com.ninuna.losttales.progression;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Repeats count less, recover over time, and an hour holds its cap (PROG-08). */
public final class RepeatRuleTest {
    private static final RepeatRule RULE = new RepeatRule(2, 50.0D, 10.0D, 60);

    @Test
    public void theFirstRepeatsCountInFullThenEachLess() {
        RepeatMemory memory = new RepeatMemory();
        assertEquals(1.0D, memory.countRepeat("troll", RULE, 0L), 0.0D);
        assertEquals(1.0D, memory.countRepeat("troll", RULE, 0L), 0.0D);
        assertEquals(0.5D, memory.countRepeat("troll", RULE, 0L), 1.0E-9D);
        assertEquals(0.25D, memory.countRepeat("troll", RULE, 0L), 1.0E-9D);
        assertEquals(0.125D, memory.countRepeat("troll", RULE, 0L), 1.0E-9D);
        assertEquals("never below the floor", 0.1D, memory.countRepeat("troll", RULE, 0L), 1.0E-9D);
        assertEquals("another thing counts in full", 1.0D,
                memory.countRepeat("orc", RULE, 0L), 0.0D);
    }

    @Test
    public void aRepeatIsForgottenEachRecoveryTime() {
        RepeatMemory memory = new RepeatMemory();
        for (int index = 0; index < 4; index++) {
            memory.countRepeat("troll", RULE, 0L);
        }
        assertEquals("four remembered, two forgotten after two minutes", 0.5D,
                memory.countRepeat("troll", RULE, 120000L), 1.0E-9D);
        assertEquals("all forgotten after long enough", 1.0D,
                memory.countRepeat("troll", RULE, 10000000L), 0.0D);
    }

    @Test
    public void anHoursCapHoldsAndANewHourOpensIt() {
        RepeatMemory memory = new RepeatMemory();
        assertEquals(80.0D, memory.allowWithinHour("losttales:mining", 80.0D, 100, 0L), 0.0D);
        assertEquals(20.0D, memory.allowWithinHour("losttales:mining", 80.0D, 100, 1000L), 0.0D);
        assertEquals(0.0D, memory.allowWithinHour("losttales:mining", 5.0D, 100, 2000L), 0.0D);
        assertEquals("another skill has its own cap", 5.0D,
                memory.allowWithinHour("losttales:lore", 5.0D, 100, 2000L), 0.0D);
        assertEquals(50.0D, memory.allowWithinHour("losttales:mining", 50.0D, 100,
                60L * 60L * 1000L), 0.0D);
        assertEquals("no cap at 0", 1.0E6D,
                memory.allowWithinHour("losttales:mining", 1.0E6D, 0, 0L), 0.0D);
    }
}
