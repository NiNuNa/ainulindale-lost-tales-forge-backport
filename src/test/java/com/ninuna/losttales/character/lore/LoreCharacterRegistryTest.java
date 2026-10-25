package com.ninuna.losttales.character.lore;

import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.progression.ProgressionFiles;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The lore characters as the server reads them (CHAR-43): the 80 bundled
 * files through the shared reader, a server's own beside them, one of a
 * bundled id in that figure's place, and no two figures of one name.
 */
public final class LoreCharacterRegistryTest {

    private static final String LOOK = "\"race\":\"losttales:human\","
            + "\"body_type\":\"losttales:masculine\","
            + "\"skin\":\"losttales:human_bree_masculine_0\"";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void forget() {
        LoreCharacterRegistry.clear();
    }

    @Test
    public void theEightyBundledFilesLoadWithNoWarning() {
        List<String> problems = load(null);

        assertEquals(new ArrayList<String>(), problems);
        assertEquals(80, LoreCharacterRegistry.getAll().size());
        assertNotNull(LoreCharacterRegistry.get("losttales:gandalf"));
        assertNotNull(LoreCharacterRegistry.get("losttales:frodo"));
        assertNotNull(LoreCharacterRegistry.get("losttales:eomer"));
        assertNotNull(LoreCharacterRegistry.getByName("Éomer"));
        assertNotNull(LoreCharacterRegistry.getByName("Nazgûl I"));
        assertNotNull(LoreCharacterRegistry.getByName("Nazgûl II"));
        for (LoreCharacterDefinition definition : LoreCharacterRegistry.getAll()) {
            assertTrue(definition.getId(), definition.hasAppearance());
        }
    }

    /** Each of the 80 names a faction LOTR or the mod has, one its race may start in (CHAR-44). */
    @Test
    public void everyBundledFigureStartsWhereItsRaceMay() {
        LoreTestGame game = new LoreTestGame();
        load(null);
        for (LoreCharacterDefinition definition : LoreCharacterRegistry.getAll()) {
            CharacterFactionDefinition faction = game.faction(definition.getFactionId());
            assertNotNull(definition.getId() + " starts in " + definition.getFactionId(), faction);
            CharacterRaceDefinition race = game.race(definition.getAppearance().getRaceId());
            assertTrue(definition.getId(), LoreCharacterFiles.raceTakes(race, faction));
        }
        assertEquals("lotr:hobbit", LoreCharacterRegistry.get("losttales:frodo").getFactionId());
        assertEquals("lotr:mordor", LoreCharacterRegistry.get("losttales:sauron").getFactionId());
        assertEquals("lotr:lothlorien",
                LoreCharacterRegistry.get("losttales:galadriel").getFactionId());
    }

    @Test
    public void aServersFileOfABundledIdTakesItsPlace() throws Exception {
        File folder = folder("replace", "gandalf.json", "{\"id\":\"losttales:gandalf\","
                + "\"name\":\"Gandalf the White\"," + LOOK + ",\"faction\":\"lotr:gondor\"}");

        assertEquals(new ArrayList<String>(), load(folder));
        assertEquals(80, LoreCharacterRegistry.getAll().size());
        assertEquals("Gandalf the White",
                LoreCharacterRegistry.get("losttales:gandalf").getName());
        assertEquals("lotr:gondor", LoreCharacterRegistry.get("losttales:gandalf").getFactionId());
        assertNull("the bundled name goes with the bundled file",
                LoreCharacterRegistry.getByName("Gandalf"));
        assertNotNull(LoreCharacterRegistry.getByName("Gandalf the White"));
    }

    @Test
    public void aServersNewFigureJoinsTheBundledOnes() throws Exception {
        File folder = folder("new", "aldor.json", "{\"id\":\"myserver:aldor\","
                + "\"name\":\"Aldor\"," + LOOK + ",\"faction\":\"lotr:bree\"}");

        assertEquals(new ArrayList<String>(), load(folder));
        assertEquals(81, LoreCharacterRegistry.getAll().size());
        assertNotNull(LoreCharacterRegistry.get("myserver:aldor"));
        assertNotNull(LoreCharacterRegistry.getByName("aldor"));
    }

    /** A server's figure under another's name is left out, whichever comes first by order. */
    @Test
    public void aServersFigureTakingAnothersNameIsLeftOut() throws Exception {
        File folder = folder("names", "duplicate_eomer.json", "{\"id\":\"myserver:duplicate\","
                + "\"name\":\"Eomer\"," + LOOK + ",\"faction\":\"lotr:rohan\"}");
        write(folder, "alatar.json", "{\"id\":\"losttales:alatar\",\"name\":\"Frodo\","
                + LOOK + ",\"faction\":\"lotr:bree\"}");

        List<String> problems = load(folder);

        assertEquals(problems.toString(), 2, problems.size());
        assertTrue(problems.toString(), problems.get(0).contains("losttales:alatar: its name"
                + " Frodo is the lore character losttales:frodo's"));
        assertTrue(problems.toString(), problems.get(1).contains("is the lore character"
                + " losttales:eomer's"));
        assertEquals(80, LoreCharacterRegistry.getAll().size());
        assertEquals("the bundled Alatar stays", "Alatar",
                LoreCharacterRegistry.get("losttales:alatar").getName());
        assertEquals("Frodo", LoreCharacterRegistry.getByName("Frodo").getName());
    }

    /** A server's file with a warning leaves the bundled figure of its id in place. */
    @Test
    public void aServersFileWithAWarningLeavesTheBundledFigure() throws Exception {
        File folder = folder("warned", "gandalf.json", "{\"id\":\"losttales:gandalf\","
                + "\"name\":\"Gandalf the White\"," + LOOK + "}");

        List<String> problems = load(folder);

        assertEquals(problems.toString(), 1, problems.size());
        assertTrue(problems.get(0), problems.get(0).startsWith("gandalf.json: names no faction"));
        assertEquals("Gandalf", LoreCharacterRegistry.get("losttales:gandalf").getName());
    }

    @Test
    public void aBrokenFileIsLeftOutAndTheOthersLoad() throws Exception {
        File folder = folder("broken", "broken.json", "{ this is not a lore character");
        write(folder, "aldor.json", "{\"id\":\"myserver:aldor\",\"name\":\"Aldor\","
                + LOOK + ",\"faction\":\"lotr:bree\"}");

        List<String> problems = load(folder);

        assertEquals(problems.toString(), 1, problems.size());
        assertTrue(problems.get(0), problems.get(0).startsWith("broken.json cannot be read"));
        assertEquals(81, LoreCharacterRegistry.getAll().size());
    }

    /** Nothing is known before the server reads the files, nor after it stops. */
    @Test
    public void theFiguresAreKnownFromTheReadToTheStop() {
        LoreCharacterRegistry.clear();
        assertFalse(LoreCharacterRegistry.isLoaded());
        assertTrue(LoreCharacterRegistry.getAll().isEmpty());
        assertNull(LoreCharacterRegistry.getByName("Gandalf"));

        load(null);
        assertTrue(LoreCharacterRegistry.isLoaded());
        assertNotNull(LoreCharacterRegistry.getByName("Gandalf"));

        LoreCharacterRegistry.clear();
        assertFalse(LoreCharacterRegistry.isLoaded());
        assertNull(LoreCharacterRegistry.get("losttales:gandalf"));
        assertTrue(LoreCharacterRegistry.getLoadErrors().isEmpty());
    }

    private static List<String> load(File folder) {
        return LoreCharacterRegistry.load(ProgressionFiles.CLASSPATH, folder, new LoreTestGame());
    }

    private File folder(String name, String file, String json) throws Exception {
        File folder = this.temporaryFolder.newFolder(name);
        write(folder, file, json);
        return folder;
    }

    private static void write(File folder, String file, String json) throws Exception {
        Writer writer = new OutputStreamWriter(new FileOutputStream(new File(folder, file)),
                StandardCharsets.UTF_8);
        try {
            writer.write(json);
        } finally {
            writer.close();
        }
    }
}
