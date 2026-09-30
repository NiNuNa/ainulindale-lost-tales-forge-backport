package com.ninuna.losttales.quest;

import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The bundled quest files as both sides read them: the index's files in
 * its order, with no list to fall back on, a missing or broken file said
 * rather than skipped, and a repeated id refused rather than winning.
 */
public final class BundledQuestFilesTest {
    private static final String QUEST = "{\"id\":\"%s\",\"title\":\"%s\","
            + "\"stages\":[{\"id\":\"10\",\"objectives\":[{\"id\":\"a\","
            + "\"type\":\"gather\",\"params\":{\"item\":\"minecraft:log\"}}]}]}";

    private static BundledQuestFiles.Source files(final Map<String, String> files) {
        return new BundledQuestFiles.Source() {
            @Override
            public Reader open(String path) {
                String text = files.get(path);
                return text == null ? null : new StringReader(text);
            }
        };
    }

    @Test
    public void theIndexedFilesLoadInItsOrder() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put(BundledQuestFiles.INDEX_FILE,
                "{\"quests\":[\"quests/b.json\",\"quests/a\"]}");
        files.put("quests/a.json", String.format(QUEST, "losttales:a", "A"));
        files.put("quests/b.json", String.format(QUEST, "losttales:b", "B"));
        BundledQuestFiles.Result read = BundledQuestFiles.read(files(files));
        assertTrue(read.problems.toString(), read.problems.isEmpty());
        assertEquals(2, read.quests.size());
        assertEquals("losttales:b", read.quests.get(0).getId());
    }

    @Test
    public void aMissingOrBrokenFileIsSaidAndTheOthersLoad() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put(BundledQuestFiles.INDEX_FILE,
                "{\"quests\":[\"quests/gone.json\",\"quests/broken.json\","
                        + "\"quests/good.json\"]}");
        files.put("quests/broken.json", "{ not json");
        files.put("quests/good.json", String.format(QUEST, "losttales:good", "Good"));
        BundledQuestFiles.Result read = BundledQuestFiles.read(files(files));
        assertEquals(1, read.quests.size());
        assertEquals(2, read.problems.size());
        assertTrue(read.problems.get(0), read.problems.get(0).contains("gone.json"));
        assertTrue(read.problems.get(1), read.problems.get(1).contains("broken.json"));
    }

    @Test
    public void aRepeatedIdIsRefusedAndTheFirstKept() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put(BundledQuestFiles.INDEX_FILE,
                "{\"quests\":[\"quests/first.json\",\"quests/second.json\"]}");
        files.put("quests/first.json", String.format(QUEST, "losttales:same", "First"));
        files.put("quests/second.json", String.format(QUEST, "losttales:same", "Second"));
        BundledQuestFiles.Result read = BundledQuestFiles.read(files(files));
        assertEquals(1, read.quests.size());
        assertEquals("First", read.quests.get(0).getTitle());
        assertEquals(1, read.problems.size());
        assertTrue(read.problems.get(0), read.problems.get(0).contains("second.json"));
    }

    @Test
    public void noIndexOrABrokenOneLoadsNothingAndSaysSo() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("quests/a.json", String.format(QUEST, "losttales:a", "A"));
        BundledQuestFiles.Result read = BundledQuestFiles.read(files(files));
        assertTrue(read.quests.isEmpty());
        assertEquals(1, read.problems.size());

        files.put(BundledQuestFiles.INDEX_FILE, "{\"quests\":[\"quests/a.json\",7]}");
        read = BundledQuestFiles.read(files(files));
        assertTrue(read.quests.isEmpty());
        assertEquals(1, read.problems.size());
    }

    @Test
    public void anIdPastTheBoundIsLeftOut() {
        StringBuilder id = new StringBuilder("losttales:");
        while (id.length() <= LostTalesQuestIds.MAX_BYTES) {
            id.append('x');
        }
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put(BundledQuestFiles.INDEX_FILE, "{\"quests\":[\"quests/long.json\"]}");
        files.put("quests/long.json", String.format(QUEST, id, "Long"));
        BundledQuestFiles.Result read = BundledQuestFiles.read(files(files));
        assertTrue(read.quests.isEmpty());
        assertEquals(1, read.problems.size());
    }

    @Test
    public void aQuestIdIsMeasuredInBytes() {
        assertTrue(LostTalesQuestIds.fits("losttales:tutorial/meet_nia"));
        StringBuilder id = new StringBuilder();
        for (int index = 0; index < 128; index++) {
            id.append('é');
        }
        assertTrue("128 two-byte letters fit", LostTalesQuestIds.fits(id.toString()));
        id.append('x');
        assertTrue("257 bytes do not", !LostTalesQuestIds.fits(id.toString()));
        assertTrue(!LostTalesQuestIds.fits(""));
        assertTrue(!LostTalesQuestIds.fits(null));
    }
}
