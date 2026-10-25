package com.ninuna.losttales.character.lore;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.registry.CharacterBody;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterFactionCategory;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceSkins;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A lore file as the shared reader reads it (CHAR-43): snake_case keys, an
 * id, a name, the faction it starts in (CHAR-44) and its look, and a file
 * with any warning left out.
 */
public final class LoreCharacterFilesTest {

    private static final String HUMAN = "\"race\":\"losttales:human\","
            + "\"body_type\":\"losttales:masculine\","
            + "\"skin\":\"losttales:human_bree_masculine_0\"";

    @Test
    public void aFileWritesItsLookAndTheFactionItStartsIn() {
        Read read = read("{\"id\":\"losttales:aldor\",\"name\":\"Aldor\","
                + HUMAN + ",\"faction\":\"lotr:bree\"}");

        assertEquals(new ArrayList<String>(), read.warnings);
        LoreCharacterDefinition aldor = read.definition;
        assertEquals("losttales:aldor", aldor.getId());
        assertEquals("Aldor", aldor.getName());
        assertEquals("", aldor.getDescription());
        assertEquals("lotr:bree", aldor.getFactionId());
        assertEquals("losttales:human", aldor.getAppearance().getRaceId());
        assertEquals(CharacterBodyTypeRegistry.MASCULINE, aldor.getAppearance().getBodyTypeId());
        assertEquals("losttales:human_bree_masculine_0", aldor.getAppearance().getSkinId());
        assertEquals("a file without an age is eighteen",
                LoreCharacterDefinition.DEFAULT_AGE, aldor.getAge());
    }

    /** The old layout's keys are read by nothing, so a file written in it is left out. */
    @Test
    public void aKeyNothingReadsIsSaid() {
        Read read = read("{\"dataVersion\":1,\"id\":\"losttales:aldor\",\"name\":\"Aldor\","
                + "\"description\":\"\",\"appearance\":{\"raceId\":\"losttales:human\","
                + "\"bodyTypeId\":\"losttales:masculine\",\"modelId\":\"lotr:human\","
                + "\"skinId\":\"losttales:human_bree_masculine_0\"}}");

        assertTrue(read.warnings.toString(), contains(read.warnings,
                "writes 'dataVersion', which nothing reads"));
        assertTrue(read.warnings.toString(), contains(read.warnings,
                "writes 'appearance', which nothing reads"));
        assertTrue(read.warnings.toString(), contains(read.warnings, "names no faction"));
    }

    @Test
    public void everyFileNamesAFactionLotrHas() {
        assertTrue(contains(read("{\"id\":\"losttales:a\",\"name\":\"Aldor\"," + HUMAN + "}")
                .warnings, "names no faction"));
        assertTrue(contains(read("{\"id\":\"losttales:a\",\"name\":\"Aldor\"," + HUMAN
                + ",\"faction\":\"Gondor\"}").warnings, "is not written as a faction's id"));
        assertTrue(contains(read("{\"id\":\"losttales:a\",\"name\":\"Aldor\"," + HUMAN
                + ",\"faction\":\"lotr:arnor\"}").warnings, "which LOTR does not have"));
    }

    /** The race's own file judges the faction: its categories and the factions it names. */
    @Test
    public void aFactionItsRaceMayNotStartInIsSaid() {
        assertTrue(contains(read("{\"id\":\"losttales:a\",\"name\":\"Aldor\"," + HUMAN
                        + ",\"faction\":\"lotr:mordor\"}").warnings,
                "starts in the faction lotr:mordor, which the race losttales:human may not start in"));
        assertTrue(contains(read("{\"id\":\"losttales:b\",\"name\":\"Bungo\","
                        + "\"race\":\"losttales:hobbit\",\"body_type\":\"losttales:masculine\","
                        + "\"skin\":\"losttales:hobbit_shire_masculine_0\","
                        + "\"faction\":\"lotr:gondor\"}").warnings,
                "which the race losttales:hobbit may not start in"));
        assertEquals(new ArrayList<String>(), read("{\"id\":\"losttales:c\",\"name\":\"Ghash\","
                + "\"race\":\"losttales:orc\",\"body_type\":\"losttales:neutral\","
                + "\"skin\":\"losttales:orc_0\",\"faction\":\"lotr:half_troll\"}").warnings);
    }

    /** The server's allow and deny lists are asked as the figure is claimed, not as its file is read. */
    @Test
    public void theServersListsAreNotAskedAsTheFileIsRead() {
        LoreCharacterFiles.Game denying = new LoreTestGame() {
            @Override
            public CharacterFactionDefinition faction(String factionId) {
                CharacterFactionDefinition known = super.faction(factionId);
                return known == null ? null : new CharacterFactionDefinition(known.getId(),
                        false, known.getCategories());
            }
        };
        assertEquals(new ArrayList<String>(), read("{\"id\":\"losttales:a\",\"name\":\"Aldor\","
                + HUMAN + ",\"faction\":\"lotr:gondor\"}", denying).warnings);
    }

    @Test
    public void withLotrOutOfReachTheFactionIsOnlyHeldToItsShape() {
        LoreCharacterFiles.Game away = new LoreTestGame().withoutFactions();
        assertEquals(new ArrayList<String>(), read("{\"id\":\"losttales:a\",\"name\":\"Aldor\","
                + HUMAN + ",\"faction\":\"lotr:arnor\"}", away).warnings);
        assertTrue(contains(read("{\"id\":\"losttales:a\",\"name\":\"Aldor\"," + HUMAN + "}",
                away).warnings, "names no faction"));
    }

    /** A file with no look is listed but cannot be claimed; one with part of a look is left out. */
    @Test
    public void aLookIsWholeOrNotThere() {
        Read unlooked = read("{\"id\":\"myserver:unfinished\",\"name\":\"Unfinished\","
                + "\"faction\":\"lotr:gondor\"}");
        assertEquals(new ArrayList<String>(), unlooked.warnings);
        assertFalse(unlooked.definition.hasAppearance());

        List<String> half = read("{\"id\":\"myserver:half\",\"name\":\"Half\","
                + "\"race\":\"losttales:human\",\"faction\":\"lotr:gondor\"}").warnings;
        assertTrue(half.toString(), contains(half, "names no body type"));
        assertTrue(half.toString(), contains(half, "names no skin"));
    }

    @Test
    public void aLookFitsItsRace() {
        assertTrue(contains(read("{\"id\":\"losttales:a\",\"name\":\"Aldor\","
                        + "\"race\":\"losttales:human\",\"body_type\":\"losttales:masculine\","
                        + "\"skin\":\"losttales:hobbit_shire_masculine_0\","
                        + "\"faction\":\"lotr:gondor\"}").warnings,
                "wears the skin losttales:hobbit_shire_masculine_0"));
        assertTrue(contains(read("{\"id\":\"losttales:a\",\"name\":\"Aldor\","
                        + "\"race\":\"losttales:human\",\"body_type\":\"losttales:neutral\","
                        + "\"skin\":\"losttales:human_bree_masculine_0\","
                        + "\"faction\":\"lotr:gondor\"}").warnings,
                "has the body type losttales:neutral"));
        assertTrue(contains(read("{\"id\":\"losttales:a\",\"name\":\"Aldor\","
                        + "\"race\":\"LostTales:Human\",\"body_type\":\"losttales:masculine\","
                        + "\"skin\":\"losttales:human_bree_masculine_0\","
                        + "\"faction\":\"lotr:gondor\"}").warnings,
                "the race 'LostTales:Human' is not written as an id"));
    }

    /** There is no half-troll: a lore file naming one is left out (CHAR-09). */
    @Test
    public void aLoreFileNamingAHalfTrollIsLeftOut() {
        assertTrue(contains(read("{\"id\":\"losttales:bogdal\",\"name\":\"Bogdal\","
                        + "\"race\":\"losttales:half_troll\",\"body_type\":\"losttales:neutral\","
                        + "\"skin\":\"losttales:half_troll_0\",\"faction\":\"lotr:half_troll\"}")
                        .warnings,
                "is of the race losttales:half_troll, which the server does not have"));
    }

    /** A figure may stand on one of the server's own races, and start where that race may (CHAR-38). */
    @Test
    public void aFigureMayStandOnAServersOwnRace() {
        CharacterRaceDefinition beorning = new CharacterRaceDefinition("myserver:beorning", "Beorning",
                CharacterBody.HUMAN, "", EnumSet.noneOf(CharacterFactionCategory.class),
                new LinkedHashSet<String>(Arrays.asList("lotr:dale")),
                new LinkedHashSet<String>(Arrays.asList(CharacterBodyTypeRegistry.MASCULINE)),
                0.6F, 1.9F, 24.0D, 1.0D, 3.0D, Collections.<CharacterRaceSkins>emptyList(),
                Collections.<String, Integer>emptyMap(), "");
        LoreTestGame game = new LoreTestGame().withRace(beorning, "myserver:beorning_masculine_0");
        String look = "\"race\":\"myserver:beorning\",\"body_type\":\"losttales:masculine\","
                + "\"skin\":\"myserver:beorning_masculine_0\"";

        assertEquals(new ArrayList<String>(), read("{\"id\":\"myserver:beorn\",\"name\":\"Beorn\","
                + look + ",\"faction\":\"lotr:dale\"}", game).warnings);
        assertTrue(contains(read("{\"id\":\"myserver:beorn\",\"name\":\"Beorn\","
                        + look + ",\"faction\":\"lotr:rohan\"}", game).warnings,
                "which the race myserver:beorning may not start in"));
        assertTrue("the six alone know no Beorning", contains(read("{\"id\":\"myserver:beorn\","
                        + "\"name\":\"Beorn\"," + look + ",\"faction\":\"lotr:dale\"}").warnings,
                "which the server does not have"));
    }

    @Test
    public void anIdAndANameAreHeldToTheirShape() {
        assertTrue(contains(read("{\"name\":\"Aldor\",\"faction\":\"lotr:gondor\"}").warnings,
                "names no id"));
        assertTrue(contains(read("{\"id\":\"Losttales:Aldor\",\"name\":\"Aldor\","
                + "\"faction\":\"lotr:gondor\"}").warnings, "is not a namespace and a path"));
        assertTrue(contains(read("{\"id\":\"losttales:a\",\"faction\":\"lotr:gondor\"}")
                .warnings, "names no name"));
        assertTrue(contains(read("{\"id\":\"losttales:a\",\"name\":\"!!\","
                + "\"faction\":\"lotr:gondor\"}").warnings, "holds no letter or digit"));
        assertTrue(contains(read("{\"id\":\"losttales:a\",\"name\":\"§cAldor\","
                + "\"faction\":\"lotr:gondor\"}").warnings, "formatting code"));
    }

    /**
     * A definition may give the whole profile the character is claimed
     * with; its History is the description where the profile names none.
     */
    @Test
    public void aProfileIsReadAndTheDescriptionIsTheHistoryOtherwise() {
        Read given = read("{\"id\":\"losttales:profiled\",\"name\":\"Profiled\","
                + "\"description\":\"A grey wanderer.\",\"faction\":\"lotr:gondor\","
                + "\"profile\":{\"appearance\":\"Tall,  grey.\","
                + "\"personality\":\"Patient.\\n\\n\\nKind.\","
                + "\"facts\":{\"eyes\":\"Grey\",\"home\":\"Nowhere long\"},"
                + "\"glances\":[{\"emoji\":\"smiley\",\"title\":\"Pipe\","
                + "\"line\":\"Always smoking.\"},"
                + "{\"emoji\":\"grinning\",\"title\":\"Staff\"}]}}");
        assertEquals(new ArrayList<String>(), given.warnings);
        CharacterProfile profile = given.definition.getProfile();
        assertEquals("Tall, grey.", profile.section(CharacterProfile.Section.APPEARANCE));
        assertEquals("Patient.\n\nKind.", profile.section(CharacterProfile.Section.PERSONALITY));
        assertEquals("A grey wanderer.", profile.section(CharacterProfile.Section.HISTORY));
        assertEquals("Grey", profile.fact(CharacterProfile.Fact.EYES));
        assertEquals(2, profile.glances().size());
        assertEquals("", profile.glances().get(1).getLine());

        Read plain = read("{\"id\":\"losttales:plain\",\"name\":\"Plain\","
                + "\"description\":\"Of Bree.\",\"faction\":\"lotr:bree\"}");
        assertEquals(new ArrayList<String>(), plain.warnings);
        assertEquals(CharacterProfile.EMPTY.withSection(CharacterProfile.Section.HISTORY,
                "Of Bree."), plain.definition.getProfile());
    }

    @Test
    public void aProfileOutsideItsBoundsIsSaid() {
        String head = "{\"id\":\"losttales:bad\",\"name\":\"Bad\",\"faction\":\"lotr:gondor\","
                + "\"profile\":";
        String[] profiles = {
                "{\"mood\":\"Grim\"}",
                "{\"facts\":{\"weight\":\"Heavy\"}}",
                "{\"facts\":{\"eyes\":\"Grey and green and blue and more\"}}",
                "{\"facts\":\"Grey\"}",
                "{\"glances\":[{\"emoji\":\"not_an_emoji\",\"title\":\"Pipe\"}]}",
                "{\"glances\":[{\"emoji\":\"smiley\"}]}",
                "{\"glances\":[{\"emoji\":\"smiley\",\"title\":\"Pipe\",\"mood\":\"x\"}]}",
                "{\"glances\":[{},{},{},{},{},{}]}",
                "\"A string\""};
        for (String profile : profiles) {
            assertFalse(profile, read(head + profile + "}").warnings.isEmpty());
        }
    }

    /** A file's History holds 2,048 characters; its Appearance, its Personality and its description 512. */
    @Test
    public void eachTextIsHeldToItsOwnLength() {
        String head = "{\"id\":\"losttales:long\",\"name\":\"Long\",\"faction\":\"lotr:gondor\",";
        String history = repeat(CharacterProfile.Section.HISTORY.getMaxLength());
        Read whole = read(head + "\"profile\":{\"history\":\"" + history + "\"}}");
        assertEquals(new ArrayList<String>(), whole.warnings);
        assertEquals(history, whole.definition.getProfile()
                .section(CharacterProfile.Section.HISTORY));
        assertFalse("past the History's length", read(head + "\"profile\":{\"history\":\""
                + history + "a\"}}").warnings.isEmpty());
        assertFalse("an Appearance is not as long as a History", read(head
                + "\"profile\":{\"appearance\":\""
                + repeat(CharacterProfile.Section.APPEARANCE.getMaxLength() + 1)
                + "\"}}").warnings.isEmpty());

        String description = repeat(LoreCharacterDefinition.MAX_DESCRIPTION_LENGTH);
        Read described = read(head + "\"description\":\"" + description + "\"}");
        assertEquals(new ArrayList<String>(), described.warnings);
        assertEquals("the description stands in for the History", description,
                described.definition.getProfile().section(CharacterProfile.Section.HISTORY));
        assertFalse(read(head + "\"description\":\"" + description + "a\"}").warnings.isEmpty());
    }

    @Test
    public void anAgeIsReadWhenGivenAndHeldToACharactersBounds() {
        Read aged = read("{\"id\":\"losttales:aged\",\"name\":\"Aged\",\"age\":2931,"
                + "\"faction\":\"lotr:gondor\"}");
        assertEquals(new ArrayList<String>(), aged.warnings);
        assertEquals(2931, aged.definition.getAge());
        assertFalse(read("{\"id\":\"losttales:unborn\",\"name\":\"Unborn\",\"age\":0,"
                + "\"faction\":\"lotr:gondor\"}").warnings.isEmpty());
        assertFalse(read("{\"id\":\"losttales:old\",\"name\":\"Old\",\"age\":\"old\","
                + "\"faction\":\"lotr:gondor\"}").warnings.isEmpty());
    }

    @Test
    public void somethingOtherThanAnObjectIsNoFigure() {
        assertNull(LoreCharacterFiles.parse(new StringReader("[]"), "list.json",
                new ArrayList<String>()));
        assertNotNull(LoreCharacterFiles.parse(new StringReader("{}"), "empty.json",
                new ArrayList<String>()));
    }

    private static boolean contains(List<String> warnings, String part) {
        for (String warning : warnings) {
            if (warning.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static String repeat(int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append('a');
        }
        return text.toString();
    }

    static Read read(String json) {
        return read(json, new LoreTestGame());
    }

    static Read read(String json, LoreCharacterFiles.Game game) {
        List<String> warnings = new ArrayList<String>();
        LoreCharacterDefinition definition = LoreCharacterFiles.parse(
                new StringReader(json), "test.json", warnings);
        warnings.addAll(LoreCharacterFiles.warningsOf(definition, game));
        return new Read(definition, warnings);
    }

    /** A file read and checked: the figure it writes, and every warning it raises. */
    static final class Read {
        final LoreCharacterDefinition definition;
        final List<String> warnings;

        Read(LoreCharacterDefinition definition, List<String> warnings) {
            this.definition = definition;
            this.warnings = warnings;
        }
    }
}
