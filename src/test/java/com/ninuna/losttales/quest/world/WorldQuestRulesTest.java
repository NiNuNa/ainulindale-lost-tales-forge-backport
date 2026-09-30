package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinitionValidator;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static com.ninuna.losttales.quest.world.WorldQuestFixtures.greenway;
import static com.ninuna.losttales.quest.world.WorldQuestFixtures.objective;
import static com.ninuna.losttales.quest.world.WorldQuestFixtures.world;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A world quest's file: seven days and a least to be paid, one stage of
 * kills and crafts, started by an operator alone; anything else keeps it
 * from running and is said the way every quest file's warnings are.
 */
public final class WorldQuestRulesTest {

    @Test
    public void aWellWrittenWorldQuestRuns() {
        LostTalesQuestDefinition quest = greenway();
        assertTrue(quest.isWorldQuest());
        assertTrue(WorldQuestRules.problems(quest).isEmpty());
        assertEquals(7, WorldQuestRules.days(quest));
        assertEquals("paid for adding at least one", 1,
                WorldQuestRules.least(quest));
        assertEquals(500, WorldQuestRules.goal(
                WorldQuestRules.objectives(quest).get(0)));
        assertTrue(LostTalesQuestDefinitionValidator.describeWarnings(
                Collections.singletonList(quest)).isEmpty());
    }

    @Test
    public void aWorldQuestNeedsItsDaysAndAWholeLeast() {
        assertFalse(WorldQuestRules.problems(greenway(world(null, "3"),
                LostTalesQuestDefinition.START_MODE_LOCKED,
                objective("orcs", "kill", "entity", "lotr.Orc", "5")))
                .isEmpty());
        assertFalse(WorldQuestRules.problems(greenway(world("366", null),
                LostTalesQuestDefinition.START_MODE_LOCKED,
                objective("orcs", "kill", "entity", "lotr.Orc", "5")))
                .isEmpty());
        assertFalse(WorldQuestRules.problems(greenway(world("7", "none"),
                LostTalesQuestDefinition.START_MODE_LOCKED,
                objective("orcs", "kill", "entity", "lotr.Orc", "5")))
                .isEmpty());
        assertEquals(12, WorldQuestRules.least(greenway(world("7", "12"),
                LostTalesQuestDefinition.START_MODE_LOCKED,
                objective("orcs", "kill", "entity", "lotr.Orc", "5"))));
    }

    @Test
    public void onlyKillsAndCraftsAddUpAndAnOperatorStartsIt() {
        List<String> gather = WorldQuestRules.problems(greenway(
                world("7", null), LostTalesQuestDefinition.START_MODE_LOCKED,
                objective("logs", "gather", "item", "minecraft:log", "5")));
        assertEquals(1, gather.size());
        assertFalse(WorldQuestRules.problems(greenway(world("7", null),
                LostTalesQuestDefinition.START_MODE_ITEM,
                objective("orcs", "kill", "entity", "lotr.Orc", "5")))
                .isEmpty());
    }

    @Test
    public void everyIdAndObjectiveFitsWhatPlayersAreSent() {
        LostTalesQuestObjectiveDefinition[] many =
                new LostTalesQuestObjectiveDefinition[
                        WorldQuestNbtCodec.MAX_COUNTS + 1];
        for (int index = 0; index < many.length; index++) {
            many[index] = objective("o" + index, "kill", "entity",
                    "lotr.Orc", "5");
        }
        assertFalse(WorldQuestRules.problems(greenway(world("7", null),
                LostTalesQuestDefinition.START_MODE_LOCKED, many)).isEmpty());
        StringBuilder longId = new StringBuilder();
        for (int index = 0; index <= WorldQuestRules.MAX_ID_BYTES; index++) {
            longId.append('x');
        }
        assertFalse("the checker every quest meets says so",
                LostTalesQuestDefinitionValidator.describeWarnings(
                        Collections.singletonList(greenway(world("7", null),
                                LostTalesQuestDefinition.START_MODE_LOCKED,
                                objective(longId.toString(), "kill", "entity",
                                        "lotr.Orc", "5")))).isEmpty());
    }

    @Test
    public void theValidatorSaysWhatKeepsItFromRunning() {
        LostTalesQuestDefinition quest = greenway(world(null, null),
                LostTalesQuestDefinition.START_MODE_LOCKED,
                objective("orcs", "kill", "entity", "lotr.Orc", "5"));
        assertFalse("an empty world block is no world quest",
                quest.isWorldQuest());
        LostTalesQuestDefinition broken = greenway(world("0", null),
                LostTalesQuestDefinition.START_MODE_LOCKED,
                objective("orcs", "kill", "entity", "lotr.Orc", "5"));
        assertFalse(LostTalesQuestDefinitionValidator.describeWarnings(
                Collections.singletonList(broken)).isEmpty());
    }
}
