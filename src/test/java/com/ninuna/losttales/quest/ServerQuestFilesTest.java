package com.ninuna.losttales.quest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A server's own quest files: each one read as a bundled quest is, and one
 * left out, with its reason, when it takes a bundled quest's id, an earlier
 * file's or a missive's, when it is too large, or when it raises any
 * warning; the others still load.
 */
public final class ServerQuestFilesTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private static final String GOOD = "{\"id\":\"losttales:server/%s\","
            + "\"title\":\"%s\",\"startMode\":\"locked\",\"stages\":[{\"id\":"
            + "\"one\",\"objectives\":[{\"id\":\"wood\",\"type\":\"gather\","
            + "\"params\":{\"item\":\"minecraft:log\",\"count\":\"4\"}}]}]}";

    private File write(String name, String text) throws IOException {
        File file = new File(this.folder.getRoot(), name);
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
        return file;
    }

    @Test
    public void aWellWrittenFileLoadsAsItsQuest() throws IOException {
        write("woodcutter.json", String.format(GOOD, "woodcutter",
                "The Woodcutter"));
        write("notes.txt", "not a quest");
        ServerQuestFiles.Result result = ServerQuestFiles.read(
                this.folder.getRoot(), Collections.<String>emptySet());
        assertEquals(1, result.quests.size());
        assertEquals("losttales:server/woodcutter",
                result.quests.get(0).getId());
        assertEquals("The Woodcutter", result.quests.get(0).getTitle());
        assertTrue(result.problems.isEmpty());
    }

    @Test
    public void takenIdsAreLeftOutWithTheirReason() throws IOException {
        write("a.json", String.format(GOOD, "bundled", "Taken"));
        write("b.json", String.format(GOOD, "twice", "First"));
        write("c.json", String.format(GOOD, "twice", "Second"));
        write("d.json", String.format(GOOD, "x", "Missive").replace(
                "server/x", "missive/x"));
        ServerQuestFiles.Result result = ServerQuestFiles.read(
                this.folder.getRoot(),
                Collections.singleton("losttales:server/bundled"));
        assertEquals(1, result.quests.size());
        assertEquals("First", result.quests.get(0).getTitle());
        assertEquals(3, result.problems.size());
    }

    @Test
    public void aFileWithAWarningOrTooLargeIsLeftOut() throws IOException {
        write("warned.json", "{\"id\":\"losttales:server/warned\",\"stages\":"
                + "[{\"id\":\"one\",\"objectives\":[{\"id\":\"x\",\"type\":"
                + "\"gather\"}]}]}");
        write("empty.json", "{\"id\":\"losttales:server/empty\"}");
        StringBuilder large = new StringBuilder(String.format(GOOD, "large",
                "Large"));
        while (large.length() <= ServerQuestFiles.MAX_FILE_BYTES) {
            large.append(' ');
        }
        write("large.json", large.toString());
        write("broken.json", "{ not json");
        ServerQuestFiles.Result result = ServerQuestFiles.read(
                this.folder.getRoot(), Collections.<String>emptySet());
        assertTrue(result.quests.isEmpty());
        assertEquals(4, result.problems.size());
    }

    /** A quest a player could not be sent whole is left out, so nobody is ever sent half of one. */
    @Test
    public void aQuestThatCannotBeSentIsLeftOut() throws IOException {
        StringBuilder title = new StringBuilder();
        while (title.length() <= 1024) {
            title.append('T');
        }
        write("long.json", String.format(GOOD, "long", title));
        write("short.json", String.format(GOOD, "short", "Short"));
        ServerQuestFiles.Result result = ServerQuestFiles.read(
                this.folder.getRoot(), Collections.<String>emptySet());
        assertEquals(1, result.quests.size());
        assertEquals("Short", result.quests.get(0).getTitle());
        assertEquals(1, result.problems.size());
        assertTrue(result.problems.get(0).contains("cannot be sent"));
    }
}
