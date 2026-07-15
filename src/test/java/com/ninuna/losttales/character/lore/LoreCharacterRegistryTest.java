package com.ninuna.losttales.character.lore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class LoreCharacterRegistryTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void parserAcceptsFoundationIdentityAndAppearance() {
        LoreCharacterDefinitionJsonParser.ParseResult result = parse(
                "{\"dataVersion\":1,"
                        + "\"id\":\"losttales:test_character\","
                        + "\"name\":\"Test Character\","
                        + "\"description\":\"A test identity.\","
                        + "\"appearance\":{"
                        + "\"raceId\":\"losttales:human\","
                        + "\"genderId\":\"losttales:male\","
                        + "\"modelId\":\"lotr:human\","
                        + "\"skinId\":\"losttales:human_bree_male_0\"}}"
        );

        assertTrue(result.getErrors().toString(), result.isValid());
        assertEquals("losttales:test_character",
                result.getDefinition().getId());
        assertEquals("Test Character", result.getDefinition().getName());
        assertEquals("A test identity.",
                result.getDefinition().getDescription());
        assertTrue(result.getDefinition().hasAppearance());
        assertEquals("lotr:human",
                result.getDefinition().getAppearance().getModelId());
    }

    @Test
    public void parserRejectsUnknownOrMissingFields() {
        LoreCharacterDefinitionJsonParser.ParseResult unknown = parse(
                "{\"dataVersion\":1,"
                        + "\"id\":\"losttales:too_much\","
                        + "\"name\":\"Too Much\","
                        + "\"description\":\"\","
                        + "\"appearance\":null,"
                        + "\"unsupported\":true}"
        );
        LoreCharacterDefinitionJsonParser.ParseResult missing = parse(
                "{\"dataVersion\":1,\"id\":\"losttales:missing\"}"
        );

        assertFalse(unknown.isValid());
        assertTrue(unknown.getErrors().get(0).contains("unknown field unsupported"));
        assertFalse(missing.isValid());
        assertTrue(missing.getErrors().get(0).contains("exactly dataVersion, id, name, description, and appearance"));
    }

    @Test
    public void appearanceMustUseCompatibleRaceModelAndSkin() {
        LoreCharacterDefinitionJsonParser.ParseResult result = parse(
                "{\"dataVersion\":1,"
                        + "\"id\":\"losttales:bad_appearance\","
                        + "\"name\":\"Bad Appearance\","
                        + "\"description\":\"\","
                        + "\"appearance\":{"
                        + "\"raceId\":\"losttales:human\","
                        + "\"genderId\":\"losttales:male\","
                        + "\"modelId\":\"lotr:hobbit\","
                        + "\"skinId\":\"losttales:hobbit_shire_male_0\"}}"
        );

        assertFalse(result.isValid());
        assertTrue(result.getErrors().get(0).contains("modelId is incompatible"));
    }

    @Test
    public void bundledRegistryContainsUniqueFoundationEntries() {
        LoreCharacterRegistry.load(null);

        assertTrue(LoreCharacterRegistry.getLoadErrors().toString(),
                LoreCharacterRegistry.getLoadErrors().isEmpty());
        assertEquals(82, LoreCharacterRegistry.getAll().size());
        assertNotNull(LoreCharacterRegistry.get("losttales:gandalf"));
        assertNotNull(LoreCharacterRegistry.get("losttales:frodo"));
        assertNotNull(LoreCharacterRegistry.get("losttales:eomer"));
        assertNotNull(LoreCharacterRegistry.getByName("Éomer"));
        assertNotNull(LoreCharacterRegistry.getByName("Nazgûl I"));
        assertNotNull(LoreCharacterRegistry.getByName("Nazgûl II"));
        assertTrue(LoreCharacterRegistry.get("losttales:gandalf").hasAppearance());
        assertFalse(LoreCharacterRegistry.get("losttales:sauron").hasAppearance());
    }

    @Test
    public void normalizedNamesCannotCreateDuplicateCharacters() throws Exception {
        File configRoot = createExternalDefinition(
                "duplicate_name.json",
                "myserver:duplicate_eomer",
                "Eomer");

        LoreCharacterRegistry.load(configRoot);

        assertEquals(82, LoreCharacterRegistry.getAll().size());
        assertEquals(1, LoreCharacterRegistry.getLoadErrors().size());
        assertTrue(LoreCharacterRegistry.getLoadErrors().get(0)
                .contains("duplicates display name"));
    }

    @Test
    public void stableIdsCannotReplaceRegisteredCharacters() throws Exception {
        File configRoot = createExternalDefinition(
                "duplicate_id.json",
                "losttales:gandalf",
                "Different Name");

        LoreCharacterRegistry.load(configRoot);

        assertEquals(82, LoreCharacterRegistry.getAll().size());
        assertEquals("Gandalf",
                LoreCharacterRegistry.get("losttales:gandalf").getName());
        assertEquals(1, LoreCharacterRegistry.getLoadErrors().size());
        assertTrue(LoreCharacterRegistry.getLoadErrors().get(0)
                .contains("duplicates id"));
    }

    @Test
    public void uniqueServerLocalFileAddsOneIdentity() throws Exception {
        File configRoot = createExternalDefinition(
                "server_entry.json",
                "myserver:server_entry",
                "Server Entry");

        LoreCharacterRegistry.load(configRoot);

        assertTrue(LoreCharacterRegistry.getLoadErrors().toString(),
                LoreCharacterRegistry.getLoadErrors().isEmpty());
        assertNotNull(LoreCharacterRegistry.get("myserver:server_entry"));
        assertEquals(83, LoreCharacterRegistry.getAll().size());
    }

    private File createExternalDefinition(String filename, String id,
                                          String name) throws Exception {
        File configRoot = this.temporaryFolder.newFolder(filename + "_config");
        File directory = new File(
                configRoot, LoreCharacterRegistry.EXTERNAL_DIRECTORY);
        assertTrue(directory.mkdirs());
        File definition = new File(directory, filename);
        Writer writer = new OutputStreamWriter(
                new FileOutputStream(definition), StandardCharsets.UTF_8);
        try {
            writer.write("{\n"
                    + "  \"dataVersion\": 1,\n"
                    + "  \"id\": \"" + id + "\",\n"
                    + "  \"name\": \"" + name + "\",\n"
                    + "  \"description\": \"\",\n"
                    + "  \"appearance\": null\n"
                    + "}\n");
        } finally {
            writer.close();
        }
        return configRoot;
    }

    private static LoreCharacterDefinitionJsonParser.ParseResult parse(String json) {
        return LoreCharacterDefinitionJsonParser.parseDefinition(
                new StringReader(json), "test.json");
    }
}
