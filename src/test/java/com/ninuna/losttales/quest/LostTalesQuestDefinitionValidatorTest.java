package com.ninuna.losttales.quest;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What the checker every quest file meets says: stages that ask for
 * nothing, objectives that would share a count, a stage named twice or
 * not at all, and every second spelling a file might still use.
 */
public final class LostTalesQuestDefinitionValidatorTest {

    private static final String HEAD = "{\"id\":\"losttales:test/q\",\"title\":\"Q\","
            + "\"startMode\":\"locked\",";
    private static final String GATHER = "{\"id\":\"%s\",\"type\":\"gather\","
            + "\"params\":{\"item\":\"minecraft:log\",\"count\":\"4\"}}";

    /** Every warning the file raises, what reading it found nothing reads first. */
    private static List<String> warnings(String json) {
        List<String> unread = new ArrayList<String>();
        LostTalesQuestDefinition quest = LostTalesQuestDefinitionJsonParser
                .parseQuest(new StringReader(json), "quests/test/q.json", unread);
        return LostTalesQuestDefinitionValidator.warningsOf(quest, unread);
    }

    private static String stage(String id, String... objectives) {
        StringBuilder text = new StringBuilder("{\"id\":\"" + id + "\",\"objectives\":[");
        for (int index = 0; index < objectives.length; index++) {
            text.append(index == 0 ? "" : ",").append(objectives[index]);
        }
        return text.append("]}").toString();
    }

    private static void assertOneWarning(String json, String about) {
        assertWarnings(json, about);
    }

    /** The file raises exactly these warnings, in this order, each found by a part of it. */
    private static void assertWarnings(String json, String... about) {
        List<String> found = warnings(json);
        assertEquals(found.toString(), about.length, found.size());
        for (int index = 0; index < about.length; index++) {
            assertTrue(found.get(index), found.get(index).contains(about[index]));
        }
    }

    /** Only a required gather hands its items over, and {@code take} says true or false. */
    @Test
    public void onlyARequiredGatherTakes() {
        String take = "{\"id\":\"logs\",\"type\":\"%s\",%s"
                + "\"params\":{\"item\":\"minecraft:log\",\"count\":\"4\","
                + "\"take\":\"%s\"}}";
        assertEquals(Collections.<String>emptyList(), warnings(HEAD
                + "\"stages\":[" + stage("10", String.format(take, "gather", "", "true"))
                + "]}"));
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                String.format(take, "craft", "", "true")) + "]}",
                "only a gather objective reads");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                String.format(take, "gather", "\"optional\":true,", "true"),
                String.format(GATHER, "more")) + "]}",
                "never hands anything over");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                String.format(take, "gather", "", "yes")) + "]}",
                "it is true or false");
    }

    @Test
    public void aWellWrittenQuestRaisesNothing() {
        assertEquals(Collections.<String>emptyList(), warnings(HEAD
                + "\"journalLog\":{\"10\":\"Wood.\"},\"stages\":["
                + stage("10", String.format(GATHER, "logs")) + "]}"));
    }

    @Test
    public void aQuestWithoutStagesOrWithAnEmptyStageIsRefused() {
        assertOneWarning(HEAD + "\"stages\":[]}", "has no stages");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10") + "]}",
                "asks for nothing");
    }

    @Test
    public void twoObjectivesSharingAnIdAreRefused() {
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                String.format(GATHER, "logs")) + "," + stage("20",
                String.format(GATHER, "logs")) + "]}", "share one count");
    }

    @Test
    public void everyStageHasAnIdOfItsOwnAndEveryJournalLineNamesOne() {
        assertOneWarning(HEAD + "\"stages\":[{\"objectives\":["
                + String.format(GATHER, "logs") + "]}]}", "without an id");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                String.format(GATHER, "a")) + "," + stage("10",
                String.format(GATHER, "b")) + "]}", "two stages");
        assertOneWarning(HEAD + "\"journalLog\":{\"one\":\"Wood.\"},"
                + "\"stages\":[" + stage("10", String.format(GATHER, "a"))
                + "]}", "journalLog names stage 'one'");
    }

    @Test
    public void aSecondSpellingIsNamed() {
        assertOneWarning("{\"id\":\"losttales:test/q\",\"startMode\":\"npc\","
                + "\"stages\":[" + stage("10", String.format(GATHER, "a"))
                + "]}", "unknown startMode");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                "{\"id\":\"a\",\"type\":\"collect\",\"params\":{\"item\":"
                        + "\"minecraft:log\"}}") + "]}", "unknown objective type");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                "{\"id\":\"a\",\"type\":\"kill\",\"params\":{\"group\":"
                        + "\"hostiles\"}}") + "]}", "the group 'hostiles'");
        assertWarnings(HEAD + "\"stages\":[" + stage("10",
                "{\"id\":\"a\",\"type\":\"talk\",\"params\":{\"tag\":"
                        + "\"npc\"}}") + "]}", "the parameter 'tag'",
                "names no 'entity' or 'group'");
        assertOneWarning(HEAD + "\"interaction\":{\"block\":\"minecraft:chest\","
                + "\"metadata\":\"2\"},\"stages\":[" + stage("10",
                String.format(GATHER, "a")) + "]}", "interaction names 'metadata'");
        assertOneWarning(HEAD + "\"conversation\":{\"HandIn\":\"Thanks.\"},"
                + "\"stages\":[" + stage("10", String.format(GATHER, "a"))
                + "]}", "conversation names 'HandIn'");
    }

    @Test
    public void aQuestIdPastTheBoundIsRefused() {
        StringBuilder id = new StringBuilder("losttales:");
        while (LostTalesQuestIds.utf8Bytes(id.toString()) <= LostTalesQuestIds.MAX_BYTES) {
            id.append('é');
        }
        assertOneWarning("{\"id\":\"" + id + "\",\"stages\":[" + stage("10",
                String.format(GATHER, "a")) + "]}", "longer than 256 bytes");
    }

    /** A key nothing reads is a warning wherever it stands (QUEST-04). */
    @Test
    public void aKeyNothingReadsIsAWarning() {
        assertOneWarning(HEAD + "\"repeatible\":true,\"stages\":["
                + stage("10", String.format(GATHER, "a")) + "]}",
                "writes 'repeatible', which nothing reads");
        assertOneWarning(HEAD + "\"stages\":[{\"id\":\"10\",\"name\":\"x\","
                + "\"objectives\":[" + String.format(GATHER, "a") + "]}]}",
                "stage=10 writes 'name'");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                "{\"id\":\"a\",\"type\":\"gather\",\"optinal\":true,"
                        + "\"params\":{\"item\":\"minecraft:log\"}}") + "]}",
                "objective=a writes 'optinal'");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                "{\"id\":\"a\",\"type\":\"gather\",\"params\":{"
                        + "\"item\":\"minecraft:log\",\"amount\":\"4\"}}") + "]}",
                "the parameter 'amount'");
        assertOneWarning(HEAD + "\"rewards\":{\"xp\":\"5\"},\"stages\":["
                + stage("10", String.format(GATHER, "a")) + "]}",
                "rewards names 'xp'");
        assertOneWarning(HEAD + "\"prerequisites\":{\"done\":\"x\"},"
                + "\"stages\":[" + stage("10", String.format(GATHER, "a"))
                + "]}", "prerequisites names 'done'");
    }

    /** A value of a shape its key never takes is a warning, never quietly dropped. */
    @Test
    public void aValueOfTheWrongShapeIsAWarning() {
        assertOneWarning(HEAD + "\"repeatable\":\"yes\",\"stages\":["
                + stage("10", String.format(GATHER, "a")) + "]}",
                "it is true or false");
        assertOneWarning(HEAD + "\"rewards\":{\"items\":[\"minecraft:log\"]},"
                + "\"stages\":[" + stage("10", String.format(GATHER, "a"))
                + "]}", "'rewards.items' as a list or a block");
        assertWarnings(HEAD + "\"stages\":{\"id\":\"10\"}}",
                "'stages' as something other than a list", "has no stages");
    }

    /** Numbers are numbers, flags are true or false, lists are parted by commas. */
    @Test
    public void numbersFlagsAndListsAreChecked() {
        assertOneWarning(HEAD + "\"prerequisites\":{\"minLevel\":\"ten\"},"
                + "\"stages\":[" + stage("10", String.format(GATHER, "a"))
                + "]}", "'minLevel' ten");
        assertOneWarning(HEAD + "\"prerequisites\":{\"completed\":"
                + "\"losttales:a;losttales:b\"},\"stages\":[" + stage("10",
                String.format(GATHER, "a")) + "]}", "parted by ';'");
        assertOneWarning(HEAD + "\"rewards\":{\"experience\":\"lots\"},"
                + "\"stages\":[" + stage("10", String.format(GATHER, "a"))
                + "]}", "'experience' lots");
        assertOneWarning(HEAD + "\"rewards\":{\"count\":\"2\"},"
                + "\"stages\":[" + stage("10", String.format(GATHER, "a"))
                + "]}", "but no 'item'");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                "{\"id\":\"a\",\"type\":\"kill\",\"params\":{\"group\":"
                        + "\"hostile\",\"fellowshipShared\":\"no\"}}") + "]}",
                "'fellowshipShared' no");
    }

    /** Only an optional objective writes an outcome or pays a reward of its own. */
    @Test
    public void anOutcomeOrRewardIsAnOptionalObjectives() {
        String optional = "{\"id\":\"b\",\"type\":\"gather\",\"optional\":%s,"
                + "\"params\":{\"item\":\"minecraft:log\",\"outcome\":\"Kind.\","
                + "\"reward.experience\":\"%s\"}}";
        assertEquals(Collections.<String>emptyList(), warnings(HEAD
                + "\"stages\":[" + stage("10", String.format(GATHER, "a"),
                String.format(optional, "true", "5")) + "]}"));
        List<String> found = warnings(HEAD + "\"stages\":[" + stage("10",
                String.format(optional, "false", "5")) + "]}");
        assertEquals(found.toString(), 2, found.size());
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                String.format(GATHER, "a"), String.format(optional, "true",
                        "many")) + "]}", "'experience' many");
    }

    /**
     * A kill may name a place (QUEST-40): a marker, or x, y and z, with a
     * radius above 0 to count within; incomplete coordinates, no radius
     * and a world quest's kill naming one are warnings.
     */
    @Test
    public void aKillsPlaceNeedsARadiusAndAllThreeCoordinates() {
        String kill = "{\"id\":\"a\",\"type\":\"kill\",\"params\":{\"group\":"
                + "\"hostile\",\"count\":\"5\",%s}}";
        assertEquals(Collections.<String>emptyList(), warnings(HEAD + "\"stages\":["
                + stage("10", String.format(kill, "\"x\":\"100\",\"y\":\"64\","
                        + "\"z\":\"-300\",\"dimension\":\"0\",\"radius\":\"24\"")) + "]}"));
        assertEquals(Collections.<String>emptyList(), warnings(HEAD + "\"stages\":["
                + stage("10", String.format(kill, "\"marker\":\"losttales:camp\","
                        + "\"radius\":\"24\"")) + "]}"));
        assertEquals("without a place the radius is the killer's",
                Collections.<String>emptyList(), warnings(HEAD + "\"stages\":["
                + stage("10", String.format(kill, "\"radius\":\"24\"")) + "]}"));
        assertOneWarning(HEAD + "\"stages\":[" + stage("10", String.format(kill,
                "\"x\":\"100\",\"y\":\"64\",\"z\":\"-300\"")) + "]}",
                "names a place but no 'radius'");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10", String.format(kill,
                "\"marker\":\"losttales:camp\",\"radius\":\"0\"")) + "]}",
                "names a place but no 'radius'");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10", String.format(kill,
                "\"x\":\"100\",\"z\":\"-300\",\"radius\":\"24\"")) + "]}",
                "incomplete coordinates for type 'kill'");
        assertOneWarning(HEAD + "\"stages\":[" + stage("10", String.format(kill,
                "\"x\":\"east\",\"y\":\"64\",\"z\":\"-300\",\"radius\":\"24\"")) + "]}",
                "non-numeric 'x'");
        assertOneWarning(HEAD + "\"world\":{\"days\":\"7\"},\"stages\":["
                + stage("hold", String.format(kill, "\"marker\":\"losttales:camp\""))
                + "]}", "a world quest counts its kills anywhere");
    }

    @Test
    public void aKillThatNamesNobodyIsRefused() {
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                "{\"id\":\"a\",\"type\":\"kill\",\"params\":{\"count\":\"3\"}}")
                + "]}", "nobody is there to defeat");
    }

    /**
     * A reward's {@code starter} names one quest by its id, a namespace
     * and a path (QUEST-43); whether that quest exists is asked as it is
     * paid, so any well-shaped id reads.
     */
    @Test
    public void aStarterRewardNamesOneQuest() {
        String stages = "\"stages\":[" + stage("10", String.format(GATHER, "logs")) + "]}";
        assertEquals(Collections.<String>emptyList(), warnings(HEAD
                + "\"rewards\":{\"starter\":\"myserver:chain/part_two\"}," + stages));
        LostTalesQuestDefinition quest = LostTalesQuestDefinitionJsonParser.parseQuest(
                new StringReader(HEAD + "\"rewards\":{\"starter\":"
                        + "\"losttales:path/survivalist/night_watch\"}," + stages),
                "quests/test/q.json", new java.util.ArrayList<String>());
        assertEquals("losttales:path/survivalist/night_watch",
                LostTalesQuestRewards.starter(quest.getRewards()));
        assertOneWarning(HEAD + "\"rewards\":{\"starter\":\"night_watch\"}," + stages,
                "it is one quest's id");
        assertOneWarning(HEAD + "\"rewards\":{\"starter\":"
                + "\"losttales:a,losttales:b\"}," + stages, "it is one quest's id");
        assertOneWarning(HEAD + "\"rewards\":{\"starter\":\"losttales: a\"},"
                + stages, "it is one quest's id");
        // An optional objective may pay one too.
        assertEquals(Collections.<String>emptyList(), warnings(HEAD
                + "\"stages\":[" + stage("10", String.format(GATHER, "logs"),
                "{\"id\":\"extra\",\"type\":\"gather\",\"optional\":true,"
                        + "\"params\":{\"item\":\"minecraft:log\",\"count\":\"8\","
                        + "\"reward.starter\":\"losttales:test/next\"}}") + "]}"));
    }

    /**
     * A reward's {@code relic} names relic pieces (CONTENT-11): a relic
     * set's id for all four, with a slot after a slash for one, parted by
     * commas; whether the set exists is asked as it is paid.
     */
    @Test
    public void aRelicRewardNamesASetOrASetAndASlot() {
        String stages = "\"stages\":[" + stage("10", String.format(GATHER, "logs")) + "]}";
        assertEquals(Collections.<String>emptyList(), warnings(HEAD
                + "\"rewards\":{\"relic\":\"losttales:striders_garb\"}," + stages));
        assertEquals(Collections.<String>emptyList(), warnings(HEAD
                + "\"rewards\":{\"relic\":\"losttales:striders_garb/helmet,"
                + "losttales:striders_garb/boots\"}," + stages));
        assertOneWarning(HEAD + "\"rewards\":{\"relic\":\"losttales:striders_garb/cloak\"},"
                + stages, "a relic set's id");
        assertOneWarning(HEAD + "\"rewards\":{\"relic\":\"Strider\"}," + stages,
                "a relic set's id");
    }

    /**
     * {@code repeatAfter} is a number of in-game days, 0.05 to 365, on a
     * repeatable quest (QUEST-45).
     */
    @Test
    public void aRepeatWaitIsDaysOnARepeatableQuest() {
        String stages = "\"stages\":[" + stage("10", String.format(GATHER, "logs")) + "]}";
        assertEquals(Collections.<String>emptyList(), warnings(HEAD
                + "\"repeatable\":true,\"repeatAfter\":\"1\"," + stages));
        LostTalesQuestDefinition quest = LostTalesQuestDefinitionJsonParser.parseQuest(
                new StringReader(HEAD + "\"repeatable\":true,\"repeatAfter\":\"0.5\","
                        + stages), "quests/test/q.json",
                new java.util.ArrayList<String>());
        assertEquals(12000L, LostTalesQuestRules.repeatAfterTicks(quest));
        assertOneWarning(HEAD + "\"repeatable\":true,\"repeatAfter\":\"soon\","
                + stages, "it is a number of in-game days");
        assertOneWarning(HEAD + "\"repeatable\":true,\"repeatAfter\":\"400\","
                + stages, "it is a number of in-game days");
        assertOneWarning(HEAD + "\"repeatAfter\":\"1\"," + stages,
                "but is not repeatable");
        assertOneWarning(HEAD + "\"repeatable\":true,\"repeatAfter\":[1],"
                + stages, "'repeatAfter' as a list or a block");
    }
}
