package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBody;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The server's races and capes as it starts (CHAR-38, CHAR-39): its own
 * files beside the bundled ones, each held to the files' checks and to what
 * LOTR has; a race whose skins would take another's ids is left out; a race
 * a character names that no file has stands in as the body it stood on.
 */
public final class CharacterRaceCatalogTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    /** The bundled skills, as the server has them when it reads its races. */
    private static final List<String> SKILLS = bundledSkills();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aServerRaceJoinsTheBundledSix() throws IOException {
        write("beorning.json", race("myserver:beorning", "beorning", "lotr:dale"));

        List<String> problems = new ArrayList<String>();
        List<CharacterRaceDefinition> races = CharacterRaceCatalog.readRaces(
                SKILLS, this.folder.getRoot(), lotr(), problems);

        assertEquals(new ArrayList<String>(), problems);
        assertEquals(7, races.size());
        assertEquals("myserver:beorning", races.get(6).getId());
    }

    @Test
    public void aServerFileOfABundledIdTakesItsPlace() throws IOException {
        write("human.json", race(CharacterRaceRegistry.HUMAN, "tall_human", "lotr:dale"));

        List<CharacterRaceDefinition> races = CharacterRaceCatalog.readRaces(
                SKILLS, this.folder.getRoot(), lotr(), new ArrayList<String>());

        assertEquals(6, races.size());
        assertEquals(1.9F, races.get(0).getHeight(), 0.0001F);
    }

    @Test
    public void aRaceNamingAFactionLotrLacksIsLeftOut() throws IOException {
        write("beorning.json", race("myserver:beorning", "beorning", "lotr:atlantis"));

        List<String> problems = new ArrayList<String>();
        List<CharacterRaceDefinition> races = CharacterRaceCatalog.readRaces(
                SKILLS, this.folder.getRoot(), lotr(), problems);

        assertEquals(6, races.size());
        assertTrue(problems.toString(), problems.get(0).contains("lotr:atlantis"));
    }

    @Test
    public void aRaceWhoseSkinsTakeAnotherRacesIdsIsLeftOut() throws IOException {
        // The losttales namespace and a bundled group: losttales:human_bree_male_0 again.
        write("copy.json", race("losttales:copy", "human_bree", "lotr:dale"));

        List<String> problems = new ArrayList<String>();
        List<CharacterRaceDefinition> races = CharacterRaceCatalog.readRaces(
                SKILLS, this.folder.getRoot(), lotr(), problems);

        assertEquals(6, races.size());
        assertTrue(problems.toString(), problems.get(0).contains("losttales:copy"));
    }

    /** Skill starts are checked against the skills that loaded. */
    @Test
    public void aStartInASkillThatDidNotLoadLeavesTheRaceOut() throws IOException {
        write("beorning.json", race("myserver:beorning", "beorning", "lotr:dale")
                .replace("\"skins\"", "\"skills\": {\"losttales:flying\": 10}, \"skins\""));

        List<String> problems = new ArrayList<String>();
        List<CharacterRaceDefinition> races = CharacterRaceCatalog.readRaces(
                SKILLS, this.folder.getRoot(), lotr(), problems);

        assertEquals(6, races.size());
        assertTrue(problems.toString(), problems.toString().contains("losttales:flying"));
    }

    /**
     * A character whose race no file has stands in as the body it
     * remembers; one whose race is held remembers that race's body.
     */
    @Test
    public void aGoneRaceStandsInAsTheBodyItStoodOn() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        roster.addCharacter(character("myserver:longbeard", CharacterBody.DWARF, 0));
        RoleplayCharacter elf = character(CharacterRaceRegistry.ELF, CharacterBody.HUMAN, 1);
        roster.addCharacter(elf);
        List<CharacterRoster> changed = new ArrayList<CharacterRoster>();

        List<CharacterRaceDefinition> standIns = CharacterRaceCatalog.standInsFor(
                Arrays.asList(roster), CharacterRaceRegistry.getBundled(), changed);

        assertEquals(1, standIns.size());
        CharacterRaceDefinition longbeard = standIns.get(0);
        assertEquals("myserver:longbeard", longbeard.getId());
        assertTrue(longbeard.isStandIn());
        assertEquals(CharacterRaceRegistry.DWARF, longbeard.getStandInFor());
        assertEquals(1.5F, longbeard.getHeight(), 0.0F);
        assertEquals(CharacterBody.ELF, elf.getBody());
        assertEquals(Arrays.asList(roster), changed);
    }

    @Test
    public void aCapeNamingARaceTheServerLacksIsLeftOut() throws IOException {
        write("bear.json", "{\"id\": \"myserver:bear\", \"name\": \"Bear Cloak\","
                + " \"picture\": \"lotr:ROHAN\", \"races\": [\"myserver:beorning\"]}");
        write("plain.json", "{\"id\": \"myserver:plain\", \"name\": \"Plain\","
                + " \"picture\": \"lotr:DALE\", \"factions\": [\"lotr:dale\"]}");

        List<String> problems = new ArrayList<String>();
        List<CharacterCapeDefinition> capes = CharacterRaceCatalog.readCapes(
                CharacterRaceRegistry.getBundled(), this.folder.getRoot(), lotr(), problems);

        assertEquals(28, capes.size());
        assertEquals("myserver:plain", capes.get(27).getId());
        assertTrue(problems.toString(), problems.get(0).contains("myserver:beorning"));
    }

    private static List<String> bundledSkills() {
        List<String> ids = new ArrayList<String>();
        for (SkillDefinition skill : DataFiles.readBundled(ProgressionFiles.CLASSPATH,
                ProgressionFiles.SKILLS_INDEX, "skills", ProgressionFiles.SKILLS).values) {
            ids.add(skill.getId());
        }
        return ids;
    }

    /** A race file on the human body with one skin group. */
    private static String race(String id, String group, String faction) {
        return "{\"id\": \"" + id + "\", \"name\": \"Kin\", \"body\": \"human\","
                + " \"sexes\": [\"losttales:male\"], \"size\": {\"width\": 0.6, \"height\": 1.9},"
                + " \"health\": 20, \"speed\": 1, \"attack\": 2, \"factions\": [\"" + faction + "\"],"
                + " \"skins\": [{\"group\": \"" + group + "\", \"name\": \"Kin\","
                + " \"sex\": \"losttales:male\", \"texture\": \"lotr:mob/bree/bree_male\", \"count\": 2}]}";
    }

    private void write(String name, String json) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(
                new File(this.folder.getRoot(), name)), StandardCharsets.UTF_8);
        try {
            writer.write(json);
        } finally {
            writer.close();
        }
    }

    private static RoleplayCharacter character(String raceId, CharacterBody body, int slot) {
        return RoleplayCharacter.builder(UUID.randomUUID(), OWNER)
                .slot(slot).name("Kin " + slot).race(raceId).body(body)
                .gender(CharacterGenderRegistry.MALE).skin("losttales:human_bree_male_0")
                .build();
    }

    /** LOTR as far as these files ask: Dale is a faction, Atlantis is not. */
    private static CharacterFactionResolver lotr() {
        return new CharacterFactionResolver() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public CharacterFactionDefinition resolve(String factionId) {
                return "lotr:dale".equals(factionId)
                        ? new CharacterFactionDefinition(factionId, true, null) : null;
            }

            @Override
            public String resolveStartingWaypointId(String factionId, String waypointId,
                                                    boolean allowAnyRegion) {
                return null;
            }
        };
    }

    @Test
    public void theRosterIsNotTouchedWhenNothingChanges() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        roster.addCharacter(character(CharacterRaceRegistry.HUMAN, CharacterBody.HUMAN, 0));
        List<CharacterRoster> changed = new ArrayList<CharacterRoster>();

        assertTrue(CharacterRaceCatalog.standInsFor(Arrays.asList(roster),
                CharacterRaceRegistry.getBundled(), changed).isEmpty());
        assertFalse(changed.contains(roster));
    }
}
