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

    /**
     * A world quest may ask for deliveries, to someone or at any board,
     * and name a podium; neither is a quest a player takes alone's
     * (QUEST-36).
     */
    @Test
    public void deliveriesAndAPodiumAreAWorldQuests() {
        String world = "{\"id\":\"losttales:server/stores\",\"title\":\"Stores\","
                + "\"world\":{\"days\":\"3\"},"
                + "\"podium\":{\"coins\":\"200\",\"alignment\":\"lotr:bree*30\"},"
                + "\"stages\":[{\"id\":\"1\",\"objectives\":["
                + "{\"id\":\"grain\",\"type\":\"deliver\",\"params\":{\"item\":"
                + "\"minecraft:wheat\",\"count\":\"500\",\"board\":\"true\"}},"
                + "{\"id\":\"arrows\",\"type\":\"deliver\",\"params\":{\"item\":"
                + "\"minecraft:arrow\",\"count\":\"200\",\"entity\":\"lotr.BreeGuard\"}},"
                + "{\"id\":\"wolves\",\"type\":\"kill\",\"params\":{\"group\":"
                + "\"enemy\",\"count\":\"50\"}}]}]}";
        assertEquals(Collections.<String>emptyList(), warnings(world));
        LostTalesQuestDefinition quest = parse(world);
        assertEquals("200", com.ninuna.losttales.quest.LostTalesQuestRules
                .podiumRewards(quest).get("coins"));
        assertTrue(WorldQuestRules.adds(WorldQuestRules.objectives(quest).get(0)));

        String personal = "{\"id\":\"losttales:server/own\",\"startMode\":\"locked\","
                + "\"podium\":{\"coins\":\"5\"},\"stages\":[{\"id\":\"1\","
                + "\"objectives\":[{\"id\":\"grain\",\"type\":\"deliver\","
                + "\"params\":{\"item\":\"minecraft:wheat\",\"board\":\"true\"}}]}]}";
        List<String> found = warnings(personal);
        assertEquals(found.toString(), 2, found.size());
        assertTrue(found.toString().contains("only a world quest pays"));
        assertTrue(found.toString().contains("only a world quest's delivery is"));
    }

    private static LostTalesQuestDefinition parse(String json) {
        return com.ninuna.losttales.quest.LostTalesQuestDefinitionJsonParser.parseQuest(
                new java.io.StringReader(json), "quests/test.json",
                new java.util.ArrayList<String>());
    }

    private static List<String> warnings(String json) {
        List<String> unread = new java.util.ArrayList<String>();
        LostTalesQuestDefinition quest = com.ninuna.losttales.quest
                .LostTalesQuestDefinitionJsonParser.parseQuest(
                        new java.io.StringReader(json), "quests/test.json", unread);
        return LostTalesQuestDefinitionValidator.warningsOf(quest, unread);
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
