package com.ninuna.losttales.progression.deed;

import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.ProgressionFiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * What a kill and a finished quest count for the bundled deeds (PROG-11):
 * a kill deed only a creature that fights back, so a named chicken is no
 * named foe; a deed of any quests every completion, a repeatable quest's
 * each time.
 */
public final class DeedCountingTest {
    private static final String CHEESE_CACHE = "losttales:tutorial/cheese_cache";

    @Test
    public void aNamedChickenIsNoNamedFoe() {
        DeedDefinition foeOfNote = deeds().get("losttales:a_foe_of_note");
        assertFalse(DeedService.countsKill(foeOfNote, "Chicken", true, false));
        assertTrue(DeedService.countsKill(foeOfNote, "Zombie", true, true));
        assertFalse("a foe that bears no name", DeedService.countsKill(foeOfNote, "Zombie",
                false, true));
    }

    @Test
    public void aKillDeedCountsOnlyItsCreaturesAndOnlyFoes() {
        DeedDefinition trollSlayer = deeds().get("losttales:troll_slayer");
        assertTrue(DeedService.countsKill(trollSlayer, "lotr.Troll", false, true));
        assertFalse(DeedService.countsKill(trollSlayer, "Zombie", false, true));
        assertFalse(DeedService.countsKill(trollSlayer, "lotr.Troll", false, false));
    }

    @Test
    public void noOtherKindCountsAKill() {
        for (DeedDefinition deed : deeds().values()) {
            if (deed.getKind() != DeedDefinition.Kind.KILL) {
                assertFalse(deed.getId(), DeedService.countsKill(deed, "lotr.Troll", true, true));
            }
        }
    }

    @Test
    public void everyCompletionOfARepeatableQuestCounts() {
        DeedDefinition teller = deeds().get("losttales:teller_of_tales");
        long counted = 0L;
        for (int completion = 0; completion < teller.getCount(); completion++) {
            if (DeedService.questStep(teller, CHEESE_CACHE, false)
                    == DeedService.QuestStep.COUNT) {
                counted++;
            }
        }
        assertEquals("ten Cheese Caches earn Teller of Tales", teller.target(), counted);
    }

    @Test
    public void everyBundledDeedOfAnyQuestsCountsEachCompletion() {
        int found = 0;
        for (DeedDefinition deed : deeds().values()) {
            if (deed.getKind() == DeedDefinition.Kind.QUESTS && deed.getQuests().isEmpty()) {
                assertEquals(deed.getId(), DeedService.QuestStep.COUNT,
                        DeedService.questStep(deed, CHEESE_CACHE, false));
                assertEquals(deed.getId() + " counts a world quest too",
                        DeedService.QuestStep.COUNT,
                        DeedService.questStep(deed, "losttales:world/any", true));
                found++;
            }
        }
        assertEquals("A Tale Begins, Teller of Tales and Keeper of Tales", 3, found);
    }

    @Test
    public void aDeedOfListedQuestsLooksAtItsOwnQuestsOnly() {
        DeedDefinition path = deeds().get("losttales:the_survivalists_path");
        assertEquals(DeedService.QuestStep.LISTED, DeedService.questStep(path,
                "losttales:path/survivalist/first_sparks", false));
        assertEquals(DeedService.QuestStep.NONE, DeedService.questStep(path, CHEESE_CACHE,
                false));
    }

    @Test
    public void aWorldQuestDeedCountsOnlyWorldQuests() {
        DeedDefinition stood = deeds().get("losttales:stood_with_many");
        assertEquals(DeedService.QuestStep.NONE, DeedService.questStep(stood, CHEESE_CACHE,
                false));
        assertEquals(DeedService.QuestStep.COUNT, DeedService.questStep(stood,
                "losttales:world/any", true));
    }

    private static Map<String, DeedDefinition> deeds() {
        DataFiles.Result<DeedDefinition> read = DataFiles.readBundled(ProgressionFiles.CLASSPATH,
                DeedFiles.INDEX, "deeds", DeedFiles.KIND);
        assertEquals(new ArrayList<String>(), read.problems);
        Map<String, DeedDefinition> byId = new HashMap<String, DeedDefinition>();
        for (DeedDefinition deed : read.values) {
            byId.put(deed.getId(), deed);
        }
        for (String id : new String[] { "losttales:a_foe_of_note", "losttales:troll_slayer",
                "losttales:teller_of_tales", "losttales:the_survivalists_path",
                "losttales:stood_with_many" }) {
            assertNotNull(id, byId.get(id));
        }
        return byId;
    }
}
