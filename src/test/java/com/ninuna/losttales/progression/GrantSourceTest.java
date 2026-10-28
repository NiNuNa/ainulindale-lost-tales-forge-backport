package com.ninuna.losttales.progression;

import com.ninuna.losttales.progression.skill.SkillAct;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The hourly cap limits only what acts earn (PROG-08): a quest's skill pay
 * and an operator's give go past it and take nothing from it, and so do a
 * finished quest's experience and a first find while the server lets them.
 */
public final class GrantSourceTest {
    private static final String SMITHING = "losttales:smithing";
    private static final String LORE = "losttales:lore";
    private static final long NOW = 1000000L;
    /** The cap at 100, both passes on as shipped. */
    private static final HourlyCap CAP = new HourlyCap(100, true, true);
    /** The cap at 100, holding finished quests and first finds too. */
    private static final HourlyCap STRICT = new HourlyCap(100, false, false);

    @Test
    public void onlyAnActIsCappedAsShipped() {
        assertTrue(GrantSource.ACT.isCapped(CAP));
        assertFalse(GrantSource.FINISHED_QUEST.isCapped(CAP));
        assertFalse(GrantSource.FIRST_FIND.isCapped(CAP));
        assertFalse(GrantSource.QUEST.isCapped(CAP));
        assertFalse(GrantSource.GIVE.isCapped(CAP));
    }

    @Test
    public void eachPassIsTheServersToTakeAway() {
        assertTrue(GrantSource.FINISHED_QUEST.isCapped(STRICT));
        assertTrue(GrantSource.FIRST_FIND.isCapped(STRICT));
        assertFalse("a quest's named pay always passes", GrantSource.QUEST.isCapped(STRICT));
        assertFalse(GrantSource.GIVE.isCapped(STRICT));
        HourlyCap questsOnly = new HourlyCap(100, true, false);
        assertFalse(GrantSource.FINISHED_QUEST.isCapped(questsOnly));
        assertTrue(GrantSource.FIRST_FIND.isCapped(questsOnly));
    }

    @Test
    public void aFinishedQuestAndAFirstFindAreTheirOwnSources() {
        assertEquals(GrantSource.FINISHED_QUEST, GrantSource.of(SkillAct.QUEST));
        assertEquals(GrantSource.FIRST_FIND, GrantSource.of(SkillAct.DISCOVER));
        assertEquals(GrantSource.ACT, GrantSource.of(SkillAct.KILL));
        assertEquals(GrantSource.ACT, GrantSource.of(SkillAct.MINE));
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
        GrantSource.ACT.allow(memory, SMITHING, 100.0D, CAP, NOW);
        assertEquals(40.0D, GrantSource.QUEST.allow(memory, SMITHING, 40.0D, CAP, NOW + 1L), 0.0D);
        assertEquals(500.0D, GrantSource.QUEST.allow(memory, SMITHING, 500.0D, CAP, NOW + 2L),
                0.0D);
    }

    /** Four quests in an hour pay their Lore in full, and a first find made then counts too. */
    @Test
    public void aFinishedQuestAndAFirstFindGoPastASpentCap() {
        RepeatMemory memory = new RepeatMemory();
        GrantSource.ACT.allow(memory, LORE, 100.0D, CAP, NOW);
        assertEquals(30.0D, GrantSource.FINISHED_QUEST.allow(memory, LORE, 30.0D, CAP,
                NOW + 1L), 0.0D);
        assertEquals(15.0D, GrantSource.FIRST_FIND.allow(memory, LORE, 15.0D, CAP, NOW + 2L),
                0.0D);

        RepeatMemory strict = new RepeatMemory();
        GrantSource.ACT.allow(strict, LORE, 100.0D, STRICT, NOW);
        assertEquals("held where the server says so", 0.0D,
                GrantSource.FINISHED_QUEST.allow(strict, LORE, 30.0D, STRICT, NOW + 1L), 0.0D);
        assertEquals(0.0D, GrantSource.FIRST_FIND.allow(strict, LORE, 15.0D, STRICT, NOW + 2L),
                0.0D);
    }

    @Test
    public void anOperatorsGiveGoesPastTheCap() {
        RepeatMemory memory = new RepeatMemory();
        GrantSource.ACT.allow(memory, SMITHING, 100.0D, CAP, NOW);
        assertEquals(1000.0D, GrantSource.GIVE.allow(memory, SMITHING, 1000.0D, CAP, NOW + 1L),
                0.0D);
    }

    @Test
    public void whatGoesPastTakesNothingFromTheHour() {
        RepeatMemory memory = new RepeatMemory();
        GrantSource.QUEST.allow(memory, SMITHING, 500.0D, CAP, NOW);
        GrantSource.GIVE.allow(memory, SMITHING, 500.0D, CAP, NOW + 1L);
        GrantSource.FINISHED_QUEST.allow(memory, SMITHING, 500.0D, CAP, NOW + 2L);
        GrantSource.FIRST_FIND.allow(memory, SMITHING, 500.0D, CAP, NOW + 3L);
        assertEquals("acts still earn the whole cap", 100.0D,
                GrantSource.ACT.allow(memory, SMITHING, 150.0D, CAP, NOW + 4L), 0.0D);
    }

    @Test
    public void noCapHoldsNothingBack() {
        RepeatMemory memory = new RepeatMemory();
        assertEquals(5000.0D, GrantSource.ACT.allow(memory, SMITHING, 5000.0D,
                new HourlyCap(0, false, false), NOW), 0.0D);
    }
}
