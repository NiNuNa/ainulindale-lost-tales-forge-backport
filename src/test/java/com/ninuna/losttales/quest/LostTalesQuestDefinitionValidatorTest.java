package com.ninuna.losttales.quest;

import java.io.StringReader;
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

    private static List<String> warnings(String json) {
        LostTalesQuestDefinition quest = LostTalesQuestDefinitionJsonParser
                .parseQuest(new StringReader(json), "quests/test/q.json");
        return LostTalesQuestDefinitionValidator.describeWarnings(
                Collections.singletonList(quest));
    }

    private static String stage(String id, String... objectives) {
        StringBuilder text = new StringBuilder("{\"id\":\"" + id + "\",\"objectives\":[");
        for (int index = 0; index < objectives.length; index++) {
            text.append(index == 0 ? "" : ",").append(objectives[index]);
        }
        return text.append("]}").toString();
    }

    private static void assertOneWarning(String json, String about) {
        List<String> found = warnings(json);
        assertEquals(found.toString(), 1, found.size());
        assertTrue(found.get(0), found.get(0).contains(about));
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
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                "{\"id\":\"a\",\"type\":\"talk\",\"params\":{\"tag\":"
                        + "\"npc\"}}") + "]}", "names no 'entity' or 'group'");
        assertOneWarning(HEAD + "\"interaction\":{\"block\":\"minecraft:chest\","
                + "\"metadata\":\"2\"},\"stages\":[" + stage("10",
                String.format(GATHER, "a")) + "]}", "interaction names 'metadata'");
        assertOneWarning(HEAD + "\"dialogue\":{\"HandIn\":\"Thanks.\"},"
                + "\"stages\":[" + stage("10", String.format(GATHER, "a"))
                + "]}", "dialogue names 'HandIn'");
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

    @Test
    public void aKillThatNamesNobodyIsRefused() {
        assertOneWarning(HEAD + "\"stages\":[" + stage("10",
                "{\"id\":\"a\",\"type\":\"kill\",\"params\":{\"count\":\"3\"}}")
                + "]}", "nobody is there to defeat");
    }
}
