package com.ninuna.losttales.character.registry;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A race file (CHAR-38) is read strictly: a key nothing reads, a field
 * missing, a body none of the six is, a size, health, speed or attack past
 * what the game bears, a skin entry out of shape, each is said, and a file
 * with anything said is left out. The code keeps the rules: the eyes and
 * the drawing follow the height on the body named.
 */
public final class CharacterRaceFilesTest {

    private static final Set<String> SKILLS =
            new HashSet<String>(Arrays.asList("losttales:husbandry", "losttales:blades"));

    /** A server's own race on the human body, as its folder would hold it. */
    private static final String BEORNING = "{"
            + "\"id\": \"myserver:beorning\", \"name\": \"Beorning\", \"body\": \"human\","
            + "\"sexes\": [\"losttales:male\", \"losttales:female\"],"
            + "\"size\": {\"width\": 0.65, \"height\": 1.9},"
            + "\"health\": 22, \"speed\": 1.0, \"attack\": 3,"
            + "\"faction_categories\": [\"human\"], \"factions\": [\"lotr:dale\"],"
            + "\"skins\": [{\"group\": \"beorning\", \"name\": \"Beorning\","
            + " \"sex\": \"losttales:male\", \"texture\": \"lotr:mob/bree/bree_male\", \"count\": 4}],"
            + "\"skills\": {\"losttales:husbandry\": 10}"
            + "}";

    @Test
    public void aServerRaceOnABodyReadsWithNoWarning() {
        List<String> said = new ArrayList<String>();
        CharacterRaceDefinition race = CharacterRaceFiles.parse(
                new StringReader(BEORNING), "beorning.json", said);

        assertEquals(new ArrayList<String>(), said);
        assertEquals(new ArrayList<String>(), CharacterRaceFiles.warningsOf(race, SKILLS));
        assertEquals("myserver:beorning", race.getId());
        assertEquals("Beorning", race.getName());
        assertEquals(CharacterBody.HUMAN, race.getBody());
        assertEquals(0.65F, race.getWidth(), 0.0001F);
        assertEquals(1.9F, race.getHeight(), 0.0001F);
        assertEquals(22.0D, race.getMaxHealth(), 0.0D);
        assertTrue(race.getAllowedFactionCategories().contains(CharacterFactionCategory.HUMAN));
        assertTrue(race.getAllowedFactionIds().contains("lotr:dale"));
        assertEquals(Integer.valueOf(10), race.getStartingSkills().get("losttales:husbandry"));
        assertEquals(1, race.getSkins().size());
        assertEquals("Beorning", race.getSkins().get(0).getName());
        assertFalse(race.isStandIn());
    }

    /** The eyes stand at 0.85 of the height and drop 0.08 sneaking; the drawing scales with it. */
    @Test
    public void theEyesAndTheDrawingFollowTheHeightOnTheBody() {
        CharacterRaceDefinition race = CharacterRaceFiles.parse(
                new StringReader(BEORNING), "beorning.json", new ArrayList<String>());

        assertEquals(1.9F * 0.85F, race.getStandingEyeHeight(), 0.0001F);
        assertEquals(1.9F * 0.85F - 0.08F, race.getSneakingEyeHeight(), 0.0001F);
        assertEquals(1.9F / 1.8F, race.getRendererScale(), 0.0001F);
        assertEquals(1.8F / 1.9F, race.getGuiPreviewScale(), 0.0001F);
        assertEquals(0, race.getGuiPreviewVerticalOffset());
    }

    @Test
    public void aKeyNothingReadsAndAMissingFieldAreSaid() {
        List<String> said = new ArrayList<String>();
        CharacterRaceFiles.parse(new StringReader(
                BEORNING.replace("\"health\": 22,", "\"wings\": 2,")), "f.json", said);

        assertTrue(said.toString(), contains(said, "'wings', which nothing reads"));
        assertTrue(said.toString(), contains(said, "gives no health"));
    }

    @Test
    public void aBodyNoneOfTheSixIsSaid() {
        List<String> said = new ArrayList<String>();
        CharacterRaceFiles.parse(new StringReader(
                BEORNING.replace("\"body\": \"human\"", "\"body\": \"troll\"")), "f.json", said);

        assertTrue(said.toString(), contains(said, "stands on the body 'troll'"));
    }

    @Test
    public void aSizeHealthSpeedOrAttackPastTheBoundsIsAWarning() {
        assertWarns(BEORNING.replace("\"width\": 0.65", "\"width\": 1.2"), "wide");
        assertWarns(BEORNING.replace("\"height\": 1.9", "\"height\": 2.5"), "high");
        assertWarns(BEORNING.replace("\"height\": 1.9", "\"height\": 0.5"), "high");
        assertWarns(BEORNING.replace("\"health\": 22", "\"health\": 400"), "health");
        assertWarns(BEORNING.replace("\"speed\": 1.0", "\"speed\": 3"), "speed");
        assertWarns(BEORNING.replace("\"attack\": 3", "\"attack\": -1"), "attack");
    }

    @Test
    public void aRaceNobodyCouldStartAsIsAWarning() {
        assertWarns(BEORNING.replace("\"faction_categories\": [\"human\"], \"factions\": [\"lotr:dale\"],",
                ""), "nobody could start");
        assertWarns(BEORNING.replace("[\"losttales:male\", \"losttales:female\"]", "[]"),
                "names no sexes");
        assertWarns(BEORNING.replace("\"losttales:female\"]", "\"losttales:centaur\"]"),
                "which is none the game has");
    }

    @Test
    public void aSkinEntryOutOfShapeIsAWarning() {
        assertWarns(BEORNING.replace("lotr:mob/bree/bree_male", "http://example.com/skin"),
                "draws from");
        assertWarns(BEORNING.replace("lotr:mob/bree/bree_male", "minecraft:textures/x"),
                "draws from");
        assertWarns(BEORNING.replace("\"count\": 4", "\"count\": 0"), "pictures");
        assertWarns(BEORNING.replace("\"group\": \"beorning\"", "\"group\": \"account\""),
                "not 'account'");
        assertWarns(BEORNING.replace("\"sex\": \"losttales:male\"",
                "\"sex\": \"losttales:non_binary\""), "no sex the race has");
    }

    @Test
    public void aSkillThatDidNotLoadIsAWarningWhereSkillsAreKnown() {
        String unknownSkill = BEORNING.replace("losttales:husbandry", "losttales:flying");
        CharacterRaceDefinition race = CharacterRaceFiles.parse(
                new StringReader(unknownSkill), "f.json", new ArrayList<String>());

        assertTrue(contains(CharacterRaceFiles.warningsOf(race, SKILLS), "no skill that loaded"));
        assertEquals("unchecked where the skills are not known", new ArrayList<String>(),
                CharacterRaceFiles.warningsOf(race, null));
    }

    @Test
    public void valuesFromNamesOneOfTheSixLotrPeople() {
        assertWarns(BEORNING.replace("\"body\": \"human\",",
                "\"body\": \"human\", \"values_from\": \"lotr:balrog\","), "takes its values from");
        CharacterRaceDefinition race = CharacterRaceFiles.parse(new StringReader(
                BEORNING.replace("\"body\": \"human\",",
                        "\"body\": \"human\", \"values_from\": \"lotr:dwarf\",")),
                "f.json", new ArrayList<String>());
        assertEquals(new ArrayList<String>(), CharacterRaceFiles.warningsOf(race, SKILLS));
        assertEquals("lotr:dwarf", race.getLotrRaceAssociation());
    }

    /** A stand-in is its body's own race under the gone race's id, with no skins of its own. */
    @Test
    public void aStandInIsItsBodysOwnRace() {
        CharacterRaceDefinition dwarf = CharacterRaceRegistry.get(CharacterRaceRegistry.DWARF);
        assertNotNull(dwarf);
        CharacterRaceDefinition standIn =
                CharacterRaceDefinition.standIn("myserver:longbeard", dwarf);

        assertTrue(standIn.isStandIn());
        assertEquals(CharacterRaceRegistry.DWARF, standIn.getStandInFor());
        assertEquals("myserver:longbeard", standIn.getId());
        assertEquals(CharacterBody.DWARF, standIn.getBody());
        assertEquals(dwarf.getHeight(), standIn.getHeight(), 0.0F);
        assertEquals("", standIn.getName());
        assertTrue(standIn.getSkins().isEmpty());
        assertEquals(new ArrayList<String>(), CharacterRaceFiles.warningsOf(standIn, null));
    }

    private static void assertWarns(String json, String part) {
        List<String> said = new ArrayList<String>();
        CharacterRaceDefinition race = CharacterRaceFiles.parse(
                new StringReader(json), "f.json", said);
        List<String> all = new ArrayList<String>(said);
        all.addAll(CharacterRaceFiles.warningsOf(race, SKILLS));
        assertTrue(all.toString(), contains(all, part));
    }

    private static boolean contains(List<String> lines, String part) {
        for (String line : lines) {
            if (line.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
