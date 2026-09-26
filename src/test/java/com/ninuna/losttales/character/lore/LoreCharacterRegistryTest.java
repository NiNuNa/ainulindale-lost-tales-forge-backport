package com.ninuna.losttales.character.lore;

import com.ninuna.losttales.character.model.CharacterProfile;
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
        assertEquals("a file without an age is eighteen",
                LoreCharacterDefinition.DEFAULT_AGE,
                result.getDefinition().getAge());
    }

    /**
     * A definition may give the whole profile the character is claimed
     * with; its History is the description where the profile names none.
     */
    @Test
    public void aProfileIsReadAndTheDescriptionIsTheHistoryOtherwise() {
        LoreCharacterDefinitionJsonParser.ParseResult given = parse(
                "{\"dataVersion\":1,\"id\":\"losttales:profiled\","
                        + "\"name\":\"Profiled\",\"description\":\"A grey wanderer.\","
                        + "\"appearance\":null,\"profile\":{"
                        + "\"appearance\":\"Tall,  grey.\","
                        + "\"personality\":\"Patient.\\n\\n\\nKind.\","
                        + "\"facts\":{\"eyes\":\"Grey\",\"home\":\"Nowhere long\"},"
                        + "\"glances\":[{\"emoji\":\"smiley\",\"title\":\"Pipe\","
                        + "\"line\":\"Always smoking.\"},"
                        + "{\"emoji\":\"grinning\",\"title\":\"Staff\"}]}}");
        assertTrue(given.getErrors().toString(), given.isValid());
        CharacterProfile profile = given.getDefinition().getProfile();
        assertEquals("Tall, grey.",
                profile.section(CharacterProfile.Section.APPEARANCE));
        assertEquals("Patient.\n\nKind.",
                profile.section(CharacterProfile.Section.PERSONALITY));
        assertEquals("A grey wanderer.",
                profile.section(CharacterProfile.Section.HISTORY));
        assertEquals("Grey", profile.fact(CharacterProfile.Fact.EYES));
        assertEquals(2, profile.glances().size());
        assertEquals("", profile.glances().get(1).getLine());

        LoreCharacterDefinitionJsonParser.ParseResult plain = parse(
                "{\"dataVersion\":1,\"id\":\"losttales:plain\","
                        + "\"name\":\"Plain\",\"description\":\"Of Bree.\","
                        + "\"appearance\":null}");
        assertTrue(plain.getErrors().toString(), plain.isValid());
        assertEquals(CharacterProfile.EMPTY.withSection(
                CharacterProfile.Section.HISTORY, "Of Bree."),
                plain.getDefinition().getProfile());
    }

    @Test
    public void aProfileOutsideItsBoundsIsRefused() {
        String head = "{\"dataVersion\":1,\"id\":\"losttales:bad\","
                + "\"name\":\"Bad\",\"description\":\"\",\"appearance\":null,"
                + "\"profile\":";
        String[] profiles = {
                "{\"mood\":\"Grim\"}",
                "{\"facts\":{\"weight\":\"Heavy\"}}",
                "{\"facts\":{\"eyes\":\"Grey and green and blue and more\"}}",
                "{\"glances\":[{\"emoji\":\"not_an_emoji\",\"title\":\"Pipe\"}]}",
                "{\"glances\":[{\"emoji\":\"smiley\"}]}",
                "{\"glances\":[{},{},{},{},{},{}]}",
                "\"A string\""};
        for (String profile : profiles) {
            assertFalse(profile, parse(head + profile + "}").isValid());
        }
    }

    @Test
    public void anAgeIsReadWhenGivenAndHeldToACharactersBounds() {
        LoreCharacterDefinitionJsonParser.ParseResult aged = parse(
                "{\"dataVersion\":1,\"id\":\"losttales:aged\","
                        + "\"name\":\"Aged\",\"description\":\"\","
                        + "\"age\":2931,\"appearance\":null}");
        LoreCharacterDefinitionJsonParser.ParseResult unborn = parse(
                "{\"dataVersion\":1,\"id\":\"losttales:unborn\","
                        + "\"name\":\"Unborn\",\"description\":\"\","
                        + "\"age\":0,\"appearance\":null}");

        assertTrue(aged.getErrors().toString(), aged.isValid());
        assertEquals(2931, aged.getDefinition().getAge());
        assertFalse(unborn.isValid());
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
        assertTrue(missing.getErrors().get(0).contains("must contain dataVersion, id, name, description, and appearance"));
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
        for (LoreCharacterDefinition definition : LoreCharacterRegistry.getAll()) {
            assertTrue(definition.getId(), definition.hasAppearance());
        }
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
