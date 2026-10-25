package com.ninuna.losttales.progression;

import com.ninuna.losttales.armour.ArmourWeight;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceFiles;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.PerkEffect;
import com.ninuna.losttales.progression.skill.SkillAct;
import com.ninuna.losttales.progression.skill.SkillCount;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import com.ninuna.losttales.progression.skill.WeaponKind;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The skill and race files shipped in the jar, read the way the server
 * reads them: every file loads with no warning, the twelve trees hold
 * the perks agreed on 2026-10-19 and 2026-10-25, every race starts one
 * skill at 10 and three at 5 as PROG-05 gives them, every word a bundled
 * skill shows has its lang line, Archery counts slings and blowguns, Lore
 * counts LOTR's waypoints, the weapon skills count blocks and the armour
 * skills what their weight keeps (PROG-01, PROG-17).
 */
public final class BundledProgressionFilesTest {

    @Test
    public void everyBundledSkillLoadsWithNoWarning() {
        DataFiles.Result<SkillDefinition> read = DataFiles.readBundled(
                ProgressionFiles.CLASSPATH, ProgressionFiles.SKILLS_INDEX, "skills",
                ProgressionFiles.SKILLS);
        assertEquals(new ArrayList<String>(), read.problems);
        List<String> ids = new ArrayList<String>();
        for (SkillDefinition skill : read.values) {
            ids.add(skill.getId());
        }
        assertEquals(Arrays.asList("losttales:archery", "losttales:blades",
                "losttales:heavy_arms", "losttales:polearms", "losttales:light_armour",
                "losttales:heavy_armour", "losttales:riding", "losttales:smithing",
                "losttales:cooking", "losttales:husbandry", "losttales:mining",
                "losttales:lore"), ids);
    }

    @Test
    public void theTreesHoldThePerksAgreedOnTheCard() {
        Map<String, SkillDefinition> skills = skills();
        assertTree(skills, "losttales:archery", "steady_draw:10,30,50", "eagle_eye:30",
                "recovery:40", "crossbow_hand:50", "quick_nock:60<steady_draw:2",
                "pinning_shot:80", "longbowman:100");
        assertTree(skills, "losttales:blades", "edge:10,30,50", "light_feet:20",
                "daggerwork:40", "riposte:40", "keen_edge:60", "bladesong:80<riposte:1",
                "swordmaster:100");
        assertTree(skills, "losttales:heavy_arms", "weight:10,30,50", "woodcutters_arm:20",
                "sunder:30", "stagger:50", "unbreakable_grip:60", "cleave:70<stagger:1",
                "warlord:100");
        assertTree(skills, "losttales:polearms", "point:10,30,50", "thrower:20", "brace:40",
                "recovery:50", "horse_breaker:60", "phalanx:80<brace:1", "pikemaster:100");
        assertTree(skills, "losttales:light_armour", "agile_defender:10,30,50",
                "soft_step:20", "custom_fit:30", "unhindered:40", "matching_set:50",
                "wind_walker:60", "deft_movement:80<custom_fit:1", "elusive:100<deft_movement:1");
        assertTree(skills, "losttales:heavy_armour", "juggernaut:10,30,50",
                "conditioning:20,60", "well_fitted:30", "tower_of_strength:40",
                "matching_set:50", "cushioned:70", "standing_firm:80<tower_of_strength:1",
                "last_stand:100");
        assertTree(skills, "losttales:riding", "horse_sense:10", "long_road:20,50",
                "tamer:30", "mounted_archer:40", "caretaker:50", "steady_seat:60",
                "charger:80<long_road:2", "rider_of_the_mark:100");
        assertTree(skills, "losttales:smithing", "quick_hands:10", "thrifty_forge:20,50",
                "repair:30", "tempered:40", "masterwork:80<tempered:1", "master_smith:100");
        assertTree(skills, "losttales:cooking", "hearty_meals:10,40", "brewer:20",
                "second_helping:30", "ale_wise:50", "feast:60<hearty_meals:2",
                "master_cook:100");
        assertTree(skills, "losttales:husbandry", "harvest:10,40", "breeder:20",
                "shepherd:30", "herbalist:50", "butcher:60", "master_of_beasts:100<breeder:1");
        assertTree(skills, "losttales:mining", "prospector:10,40", "steady_pick:20",
                "stonecutter:30", "deep_delver:50", "gem_cutter:70<prospector:2",
                "dwarf_craft:100");
        assertTree(skills, "losttales:lore", "wayfarer:10,40", "pathfinder:20",
                "rumour_hound:30", "fair_dealer:50", "friend_of_the_wise:60",
                "storyteller:80<rumour_hound:1", "sage:100");
    }

    @Test
    public void everyRaceStartsOneSkillAtTenAndThreeAtFive() {
        Map<String, SkillDefinition> skills = skills();
        DataFiles.Result<CharacterRaceDefinition> read = DataFiles.readBundled(
                CharacterRaceRegistry.CLASSPATH, CharacterRaceFiles.INDEX,
                CharacterRaceFiles.LIST_KEY, CharacterRaceFiles.kind(skills.keySet()));
        assertEquals(new ArrayList<String>(), read.problems);
        assertEquals(6, read.values.size());
        for (CharacterRaceDefinition race : read.values) {
            List<Integer> levels = new ArrayList<Integer>(race.getStartingSkills().values());
            Collections.sort(levels);
            assertEquals(race.getId(), Arrays.asList(Integer.valueOf(5), Integer.valueOf(5),
                    Integer.valueOf(5), Integer.valueOf(10)), levels);
        }
    }

    @Test
    public void theRacesStartAsTheCardGivesThem() {
        Map<String, SkillDefinition> skills = skills();
        DataFiles.Result<CharacterRaceDefinition> read = DataFiles.readBundled(
                CharacterRaceRegistry.CLASSPATH, CharacterRaceFiles.INDEX,
                CharacterRaceFiles.LIST_KEY, CharacterRaceFiles.kind(skills.keySet()));
        Map<String, Map<String, Integer>> starts = new LinkedHashMap<String, Map<String, Integer>>();
        for (CharacterRaceDefinition race : read.values) {
            starts.put(race.getId(), race.getStartingSkills());
        }
        assertStarts(starts, "losttales:elf", "archery", "blades", "lore", "light_armour");
        assertStarts(starts, "losttales:dwarf", "smithing", "mining", "heavy_arms", "heavy_armour");
        assertStarts(starts, "losttales:uruk", "heavy_arms", "heavy_armour", "polearms", "archery");
    }

    @Test
    public void theArmourSkillsCountWhatTheirWeightKeeps() {
        Map<String, SkillDefinition> skills = skills();
        assertEquals(ArmourWeight.LIGHT, skills.get("losttales:light_armour").getWeight());
        assertEquals(ArmourWeight.HEAVY, skills.get("losttales:heavy_armour").getWeight());
        for (String id : new String[] { "losttales:light_armour", "losttales:heavy_armour" }) {
            List<SkillAct> acts = new ArrayList<SkillAct>();
            for (SkillCount count : skills.get(id).getCounts()) {
                acts.add(count.getAct());
            }
            assertEquals(id, Collections.singletonList(SkillAct.ARMOUR), acts);
        }
        for (String id : new String[] { "losttales:blades", "losttales:heavy_arms",
                "losttales:polearms" }) {
            boolean blocks = false;
            for (SkillCount count : skills.get(id).getCounts()) {
                blocks |= count.getAct() == SkillAct.BLOCK && !count.getWeapons().isEmpty();
            }
            assertTrue(id + " counts blows blocked with its weapons", blocks);
        }
        for (SkillDefinition skill : skills.values()) {
            if (skill.getWeight() == null) {
                for (SkillCount count : skill.getCounts()) {
                    assertTrue(skill.getId() + " counts no armour",
                            count.getAct() != SkillAct.ARMOUR);
                }
            }
        }
    }

    private static void assertStarts(Map<String, Map<String, Integer>> starts, String race,
                                     String ten, String... fives) {
        Map<String, Integer> expected = new LinkedHashMap<String, Integer>();
        expected.put("losttales:" + ten, Integer.valueOf(10));
        for (String five : fives) {
            expected.put("losttales:" + five, Integer.valueOf(5));
        }
        assertEquals(race, expected, starts.get(race));
    }

    @Test
    public void everyBundledSkillAndPerkHasItsWords() {
        Map<String, String> english = LostTalesLangFile.english();
        for (SkillDefinition skill : skills().values()) {
            assertTrue(skill.getId() + " is worded by its lang lines, not its file",
                    skill.getName().length() == 0 && skill.getDescription().length() == 0);
            assertLine(english, SkillWords.nameKey(skill.getId()));
            assertLine(english, SkillWords.descriptionKey(skill.getId()));
            for (PerkDefinition perk : skill.getPerks()) {
                assertLine(english, SkillWords.perkNameKey(skill.getId(), perk.getId()));
                String description = english.get(
                        SkillWords.perkDescriptionKey(skill.getId(), perk.getId()));
                assertNotNull(perk.getId() + " has a description", description);
                PerkEffect effect = PerkEffect.fromWord(perk.getEffect());
                for (int index = effect.getParams().size() + 2; index < 6; index++) {
                    assertTrue(perk.getId() + " names no number it lacks",
                            description.indexOf("%" + index + "$s") < 0);
                }
            }
        }
    }

    @Test
    public void archeryCountsBowsCrossbowsSlingsAndBlowguns() {
        SkillDefinition archery = skills().get("losttales:archery");
        int counted = 0;
        for (SkillCount count : archery.getCounts()) {
            if (count.getAct() == SkillAct.HIT || count.getAct() == SkillAct.KILL) {
                for (WeaponKind kind : new WeaponKind[] { WeaponKind.BOW, WeaponKind.CROSSBOW,
                        WeaponKind.SLING, WeaponKind.BLOWGUN }) {
                    assertTrue(count.getAct().word() + " names " + kind.word(),
                            count.getWeapons().contains(kind.word()));
                }
                counted++;
            }
        }
        assertEquals("a hit and a kill", 2, counted);
    }

    @Test
    public void loreCountsLotrsRegionsAndWaypointsAndTheModsMarkersAndWaystones() {
        SkillDefinition lore = skills().get("losttales:lore");
        Map<String, Double> worth = null;
        for (SkillCount count : lore.getCounts()) {
            if (count.getAct() == SkillAct.DISCOVER) {
                worth = count.getWorth().entries();
            }
        }
        assertNotNull("Lore counts finds", worth);
        assertEquals(Double.valueOf(25.0D), worth.get("region"));
        assertEquals(Double.valueOf(15.0D), worth.get("waypoint"));
        assertEquals(Double.valueOf(15.0D), worth.get("waystone"));
        assertEquals(Double.valueOf(10.0D), worth.get("marker"));
    }

    private static void assertLine(Map<String, String> english, String key) {
        String line = english.get(key);
        assertTrue(key + " has a line", line != null && line.length() > 0);
    }

    private static void assertTree(Map<String, SkillDefinition> skills, String skillId,
                                   String... perks) {
        SkillDefinition skill = skills.get(skillId);
        assertNotNull(skillId, skill);
        List<String> found = new ArrayList<String>();
        for (PerkDefinition perk : skill.getPerks()) {
            StringBuilder written = new StringBuilder(perk.getId()).append(':');
            int[] levels = perk.getLevels();
            for (int index = 0; index < levels.length; index++) {
                written.append(index == 0 ? "" : ",").append(levels[index]);
            }
            if (perk.getNeedsPerk().length() > 0) {
                written.append('<').append(perk.getNeedsPerk()).append(':')
                        .append(perk.getNeedsRank());
            }
            found.add(written.toString());
        }
        assertEquals(skillId, Arrays.asList(perks), found);
    }

    private static Map<String, SkillDefinition> skills() {
        DataFiles.Result<SkillDefinition> read = DataFiles.readBundled(
                ProgressionFiles.CLASSPATH, ProgressionFiles.SKILLS_INDEX, "skills",
                ProgressionFiles.SKILLS);
        Map<String, SkillDefinition> byId = new LinkedHashMap<String, SkillDefinition>();
        for (SkillDefinition skill : read.values) {
            byId.put(skill.getId(), skill);
        }
        return byId;
    }
}
