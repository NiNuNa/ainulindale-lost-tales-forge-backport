package com.ninuna.losttales.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The client's small text files: lines written and read back as they
 * were, and a failed write said once until the file is written again.
 * FML's logger is not set up here, so the warning itself goes nowhere.
 */
public final class LostTalesTextFilesTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void linesAreWrittenEachEndedByANewlineAndReadBack() throws IOException {
        File file = new File(temporaryFolder.newFolder("client"),
                "choices/store.txt");
        // An accented letter, which only UTF-8 writes as two bytes.
        String accented = "count star 3 " + (char) 0xE9;

        assertTrue(LostTalesTextFiles.writeLines(file,
                Arrays.asList("favorite star", null, accented)));

        assertArrayEquals(("favorite star\n\n" + accented + "\n").getBytes("UTF-8"),
                Files.readAllBytes(file.toPath()));
        assertEquals(Arrays.asList("favorite star", "", accented),
                LostTalesTextFiles.readLines(file, Integer.MAX_VALUE));
    }

    @Test
    public void aReadStopsAtItsLimitAndAMissingFileReadsAsNone() throws IOException {
        File file = temporaryFolder.newFile("layout.txt");
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write("one\ntwo\nthree\n".getBytes("UTF-8"));
        } finally {
            output.close();
        }

        List<String> lines = LostTalesTextFiles.readLines(file, 2);

        assertEquals(Arrays.asList("one", "two"), lines);
        assertNull(LostTalesTextFiles.readLines(
                new File(temporaryFolder.getRoot(), "missing.txt"), 10));
        assertNull(LostTalesTextFiles.readLines(null, 10));
    }

    @Test
    public void aFailedWriteIsSaidOnceUntilTheFileIsWrittenAgain() throws IOException {
        // A folder stands where the file should be, so it cannot be opened.
        File file = temporaryFolder.newFolder("blocked.txt");

        assertFalse(LostTalesTextFiles.writeLines(file,
                Arrays.asList("ignore someone")));
        assertFalse("already said", LostTalesTextFiles.writeFailed(file,
                "the same failure again"));

        assertTrue(file.delete());
        assertTrue(LostTalesTextFiles.writeLines(file,
                Arrays.asList("ignore someone")));
        assertTrue("said again after a good write",
                LostTalesTextFiles.writeFailed(file, "a later failure"));
    }

    @Test
    public void aFolderThatCannotBeMadeIsAFailedWrite() throws IOException {
        File notAFolder = temporaryFolder.newFile("plain.txt");
        File file = new File(notAFolder, "store.txt");

        assertFalse(LostTalesTextFiles.writeLines(file,
                Arrays.asList("recent waystone")));
        assertFalse("already said", LostTalesTextFiles.writeFailed(file,
                "again"));
    }

    @Test
    public void noFileIsNeverWrittenAndNeverSaid() {
        assertFalse(LostTalesTextFiles.writeLines(null,
                Arrays.asList("line")));
        assertFalse(LostTalesTextFiles.writeFailed(null, "nothing"));
        LostTalesTextFiles.writeSucceeded(null);
    }
}
