package com.ninuna.losttales.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** A server's own content files as the one reader takes them. */
public final class DataFilesTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** A file the parser cannot get to the bottom of is left out, never the whole read. */
    @Test
    public void aFileNestedTooDeepIsLeftOutWithAReason() throws IOException {
        File folder = temporaryFolder.newFolder("quests");
        FileOutputStream out = new FileOutputStream(new File(folder, "deep.json"));
        try {
            out.write("[[[[]]]]".getBytes("UTF-8"));
        } finally {
            out.close();
        }
        DataFiles.Kind<String> kind = new DataFiles.Kind<String>() {
            @Override
            public String parse(Reader reader, String file, List<String> said) {
                throw new StackOverflowError();
            }

            @Override
            public String idOf(String value) {
                return value;
            }

            @Override
            public List<String> warnings(String value) {
                return Collections.<String>emptyList();
            }
        };

        DataFiles.Result<String> read = DataFiles.readFolder(folder, kind);

        assertTrue(read.values.isEmpty());
        assertEquals(Collections.singletonList("deep.json nests too deep to be read"),
                read.problems);
    }

    /** A file's whole text as its id, the reader's fault turned into a read's. */
    private static final DataFiles.Kind<String> WHOLE_TEXT = new DataFiles.Kind<String>() {
        @Override
        public String parse(Reader reader, String file, List<String> said) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            try {
                for (int read = reader.read(buffer); read >= 0; read = reader.read(buffer)) {
                    text.append(buffer, 0, read);
                }
            } catch (IOException unreadable) {
                throw new IllegalStateException(unreadable.getMessage(), unreadable);
            }
            return file + ":" + text.length();
        }

        @Override
        public String idOf(String value) {
            return value;
        }

        @Override
        public List<String> warnings(String value) {
            return Collections.<String>emptyList();
        }
    };

    /** Bundled files out of a map, as a resource pack might hold them. */
    private static DataFiles.Source source(final Map<String, String> files) {
        return new DataFiles.Source() {
            @Override
            public Reader open(String path) {
                String text = files.get(path);
                return text == null ? null : new StringReader(text);
            }
        };
    }

    /** An index entry that would leave the mod's assets is left out; the others load. */
    @Test
    public void anIndexPathLeavingTheAssetsIsLeftOut() {
        assertTrue(DataFiles.staysInAssets("quests/tutorial/meet_nia.json"));
        assertTrue(DataFiles.staysInAssets("a..b.json"));
        assertFalse(DataFiles.staysInAssets("../secret.json"));
        assertFalse(DataFiles.staysInAssets("quests/../../secret.json"));
        assertFalse(DataFiles.staysInAssets("quests\\..\\secret.json"));
        assertFalse(DataFiles.staysInAssets("/etc/secret.json"));
        assertFalse(DataFiles.staysInAssets("\\secret.json"));
        assertFalse(DataFiles.staysInAssets(null));

        Map<String, String> files = new HashMap<String, String>();
        files.put("things/index.json",
                "{\"things\": [\"things/a.json\", \"../b.json\", \"/c.json\"]}");
        files.put("things/a.json", "{}");
        files.put("../b.json", "{}");
        files.put("/c.json", "{}");
        DataFiles.Result<String> read = DataFiles.readBundled(source(files),
                "things/index.json", "things", WHOLE_TEXT);
        assertEquals(Collections.singletonList("things/a.json:2"), read.values);
        assertEquals(2, read.problems.size());
        assertTrue(read.problems.get(0), read.problems.get(0).contains("../b.json"));
        assertTrue(read.problems.get(1), read.problems.get(1).contains("/c.json"));
    }

    /** A bundled file longer than the bound is left out with a reason, never read whole. */
    @Test
    public void aBundledFileLongerThanTheBoundIsLeftOut() {
        StringBuilder huge = new StringBuilder();
        while (huge.length() <= DataFiles.MAX_BUNDLED_FILE_BYTES) {
            huge.append("                                                                ");
        }
        Map<String, String> files = new HashMap<String, String>();
        files.put("things/index.json", "{\"things\": [\"things/huge.json\", \"things/a.json\"]}");
        files.put("things/huge.json", huge.toString());
        files.put("things/a.json", "{}");
        DataFiles.Result<String> read = DataFiles.readBundled(source(files),
                "things/index.json", "things", WHOLE_TEXT);
        assertEquals(Collections.singletonList("things/a.json:2"), read.values);
        assertEquals(1, read.problems.size());
        assertTrue(read.problems.get(0), read.problems.get(0).startsWith("things/huge.json"));
    }

    /** The bounded reader lets its limit through and refuses the next character. */
    @Test
    public void theBoundedReaderStopsPastItsLimit() throws IOException {
        Reader reader = DataFiles.bounded(new StringReader("abc"), 3L);
        assertEquals('a', reader.read());
        char[] rest = new char[8];
        assertEquals(2, reader.read(rest, 0, rest.length));
        assertEquals(-1, reader.read());
        Reader past = DataFiles.bounded(new StringReader("abcd"), 3L);
        try {
            past.read(new char[8], 0, 8);
            fail("a fourth character is past the limit");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("3"));
        }
        assertNull(DataFiles.bounded(null, 3L));
    }

    /** An index naming more files than the bound reads only the first of them. */
    @Test
    public void anIndexReadsAtMostTheFileBound() {
        Map<String, String> files = new HashMap<String, String>();
        StringBuilder index = new StringBuilder("{\"things\": [");
        for (int number = 0; number <= DataFiles.MAX_FILES; number++) {
            String name = "things/" + number + ".json";
            index.append(number == 0 ? "" : ", ").append('"').append(name).append('"');
            files.put(name, "{}");
        }
        files.put("things/index.json", index.append("]}").toString());
        DataFiles.Result<String> read = DataFiles.readBundled(source(files),
                "things/index.json", "things", WHOLE_TEXT);
        assertEquals(DataFiles.MAX_FILES, read.values.size());
        assertEquals(1, read.problems.size());
        assertFalse(read.values.contains("things/" + DataFiles.MAX_FILES + ".json:2"));
    }
}
