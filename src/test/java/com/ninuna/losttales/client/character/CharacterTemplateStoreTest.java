package com.ninuna.losttales.client.character;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The account's default-character template on disk. It is a convenience,
 * so every failure here answers with an empty template rather than
 * raising: a client that cannot read one still opens the form.
 */
public final class CharacterTemplateStoreTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final UUID ACCOUNT =
            UUID.fromString("c0000000-0000-0000-0000-00000000000c");
    private static final UUID OTHER_ACCOUNT =
            UUID.fromString("c1000000-0000-0000-0000-00000000001c");

    private File clientFolder;

    @Before
    public void setUp() throws IOException {
        this.clientFolder = File.createTempFile("losttales-templates", "");
        assertTrue(this.clientFolder.delete());
        assertTrue(this.clientFolder.mkdirs());
        CharacterTemplateStore.initialize(this.clientFolder);
    }

    @After
    public void tearDown() {
        CharacterTemplateStore.initialize(null);
        delete(this.clientFolder);
    }

    @Test
    public void anAccountWithNoTemplateReadsAnEmptyOne() {
        assertSame(CharacterTemplate.EMPTY,
                CharacterTemplateStore.load(ACCOUNT));
        assertFalse(CharacterTemplateStore.has(ACCOUNT));
    }

    @Test
    public void aSavedTemplateReadsBackExactly() {
        CharacterTemplate saved = template("Aldric", "A knight of Gondor.");
        assertTrue(CharacterTemplateStore.save(ACCOUNT, saved));

        assertEquals(saved, CharacterTemplateStore.load(ACCOUNT));
        assertTrue(CharacterTemplateStore.has(ACCOUNT));
    }

    /**
     * The template belongs to the account, not to the installation: two
     * people sharing a machine keep their own, and neither can read the
     * other's by being signed in.
     */
    @Test
    public void oneAccountsTemplateIsNotAnothers() {
        CharacterTemplateStore.save(ACCOUNT, template("Aldric", ""));
        CharacterTemplateStore.save(OTHER_ACCOUNT, template("Beren", ""));

        assertEquals("Aldric", CharacterTemplateStore.load(ACCOUNT).getName());
        assertEquals("Beren",
                CharacterTemplateStore.load(OTHER_ACCOUNT).getName());
    }

    /** An installation that cannot say which account it is reads nothing. */
    @Test
    public void anUnknownAccountHasNoTemplate() {
        CharacterTemplateStore.save(ACCOUNT, template("Aldric", ""));

        assertSame(CharacterTemplate.EMPTY, CharacterTemplateStore.load(null));
        assertFalse(CharacterTemplateStore.save(null, template("Nobody", "")));
        assertFalse(CharacterTemplateStore.has(null));
    }

    /** Before the client says where its folder is, nothing is read or written. */
    @Test
    public void anUninitialisedStoreReadsAndWritesNothing() {
        CharacterTemplateStore.initialize(null);

        assertSame(CharacterTemplate.EMPTY, CharacterTemplateStore.load(ACCOUNT));
        assertFalse(CharacterTemplateStore.save(ACCOUNT, template("Aldric", "")));
    }

    /** A file of nonsense is a template nobody has, not a failure. */
    @Test
    public void anUnreadableFileReadsAsNoTemplate() throws IOException {
        writeRaw(ACCOUNT, "this is not a template\nnor is this\n");

        assertSame(CharacterTemplate.EMPTY, CharacterTemplateStore.load(ACCOUNT));
    }

    /** A line this build does not understand is skipped, the rest is kept. */
    @Test
    public void unknownLinesAreSkippedAndTheRestIsRead() throws IOException {
        writeRaw(ACCOUNT, "name=Aldric\ngibberish\n=novalue\nage=41\n");

        CharacterTemplate loaded = CharacterTemplateStore.load(ACCOUNT);

        assertEquals("Aldric", loaded.getName());
        assertEquals(41, loaded.getAge());
    }

    /**
     * A key a later build wrote is put back untouched when this one
     * saves, so an older client does not thin out a newer file.
     */
    @Test
    public void aKeyFromALaterBuildSurvivesASave() throws IOException {
        writeRaw(ACCOUNT, "name=Aldric\nsomething_later=kept\n");

        CharacterTemplateStore.save(ACCOUNT, template("Beren", ""));

        String written = readRaw(ACCOUNT);
        assertTrue("the unknown key is still there",
                written.contains("something_later=kept"));
        assertTrue("and the known one was replaced",
                written.contains("name=Beren"));
    }

    /** An age that is not a number reads as none rather than refusing the file. */
    @Test
    public void anAgeThatIsNotANumberReadsAsNone() throws IOException {
        writeRaw(ACCOUNT, "name=Aldric\nage=ancient\n");

        assertEquals(0, CharacterTemplateStore.load(ACCOUNT).getAge());
    }

    /** A newline inside a value would read back as another entry. */
    @Test
    public void aValueCarryingANewlineIsFlattened() throws IOException {
        CharacterTemplateStore.save(ACCOUNT, new CharacterTemplate(
                "Aldric", "", "", "", "", "", "",
                "First line\nname=Injected", 0, false));

        assertEquals("Aldric", CharacterTemplateStore.load(ACCOUNT).getName());
    }

    @Test
    public void aClearedTemplateIsGone() {
        CharacterTemplateStore.save(ACCOUNT, template("Aldric", ""));
        assertTrue(CharacterTemplateStore.clear(ACCOUNT));

        assertFalse(CharacterTemplateStore.has(ACCOUNT));
        assertFalse("clearing twice is not an error",
                CharacterTemplateStore.clear(ACCOUNT));
    }

    /** Saving twice leaves one file, not a file and a half-written one. */
    @Test
    public void savingTwiceLeavesNoTemporaryBehind() {
        CharacterTemplateStore.save(ACCOUNT, template("Aldric", ""));
        CharacterTemplateStore.save(ACCOUNT, template("Beren", ""));

        File folder = new File(this.clientFolder, CharacterTemplateStore.FOLDER);
        String[] names = folder.list();
        assertNotNull(names);
        assertEquals("one file for the account and nothing beside it",
                1, names.length);
        assertEquals("Beren", CharacterTemplateStore.load(ACCOUNT).getName());
    }

    private static CharacterTemplate template(String name, String description) {
        return new CharacterTemplate(name, "losttales:human",
                "losttales:male", "losttales:account_skin",
                "losttales:wide", "losttales:none", "lotr:gondor",
                description, 30, false);
    }

    private void writeRaw(UUID accountId, String contents) throws IOException {
        File file = CharacterTemplateStore.fileFor(accountId);
        assertNotNull(file);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            assertTrue(parent.mkdirs());
        }
        Writer writer = new OutputStreamWriter(
                new FileOutputStream(file), UTF_8);
        try {
            writer.write(contents);
        } finally {
            writer.close();
        }
    }

    private String readRaw(UUID accountId) throws IOException {
        File file = CharacterTemplateStore.fileFor(accountId);
        assertNotNull(file);
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(
                        new java.io.FileInputStream(file), UTF_8));
        StringBuilder contents = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                contents.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        return contents.toString();
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        file.delete();
    }
}
