package com.ninuna.losttales.armour;

import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The armour files shipped in the jar, read the way the server reads them
 * (CONTENT-06, CONTENT-07, CONTENT-11): every file loads with no warning,
 * the weight file names only materials the game has and weighs every
 * LOTR armour piece as the card draws the line, every named set takes
 * pieces of one weight that the game has, its variants included, each
 * bundled set gives one effect at its one size and the relic example
 * twice that, and every word a set shows has its lang line.
 */
public final class BundledArmourFilesTest {

    @Test
    public void everyBundledFileLoadsWithNoWarning() {
        DataFiles.Result<WeightFile> weights = DataFiles.readBundled(ProgressionFiles.CLASSPATH,
                ArmourFiles.INDEX, ArmourFiles.WEIGHTS_KEY, ArmourFiles.WEIGHTS);
        DataFiles.Result<NamedSet> sets = DataFiles.readBundled(ProgressionFiles.CLASSPATH,
                ArmourFiles.INDEX, ArmourFiles.SETS_KEY, ArmourFiles.SETS);
        assertEquals(new ArrayList<String>(), weights.problems);
        assertEquals(new ArrayList<String>(), sets.problems);
        assertEquals(1, weights.values.size());
        assertEquals("60 sets of a make and the relic example", 61, sets.values.size());
    }

    @Test
    public void theWeightFileNamesOnlyMaterialsTheGameHas() throws IOException {
        Set<String> known = LotrArmourBytecode.materials();
        for (String material : catalogue().getMaterials().keySet()) {
            assertTrue(material + " is a material the game registers", known.contains(material));
        }
    }

    @Test
    public void everyLotrArmourPieceIsLightOrHeavy() throws IOException {
        ArmourCatalogue catalogue = catalogue();
        Map<String, String> items = LotrArmourBytecode.armourItems();
        assertTrue("LOTR's armour is read off the jar", items.size() > 200);
        for (Map.Entry<String, String> item : items.entrySet()) {
            assertTrue(item.getKey() + " has a weight",
                    catalogue.weightOf(item.getKey(), item.getValue()).counts());
        }
    }

    @Test
    public void theWeightsFollowTheCard() {
        ArmourCatalogue catalogue = catalogue();
        String[] light = { "CLOTH", "CHAIN", "LOTR_MITHRIL", "LOTR_GALADHRIM", "LOTR_HIGH_ELVEN",
                "LOTR_ROHAN", "LOTR_RANGER", "LOTR_HITHLAIN", "LOTR_GALVORN", "LOTR_FUR" };
        String[] heavy = { "IRON", "GOLD", "DIAMOND", "LOTR_BRONZE", "LOTR_HALF_TROLL",
                "LOTR_GONDOLIN", "LOTR_GONDOR", "LOTR_URUK", "LOTR_DWARVEN", "LOTR_MORDOR" };
        for (String material : light) {
            assertEquals(material, ArmourWeight.LIGHT, catalogue.weightOf("", material));
        }
        for (String material : heavy) {
            assertEquals(material, ArmourWeight.HEAVY, catalogue.weightOf("", material));
        }
        for (String material : new String[] { "LOTR_HARAD_ROBES", "LOTR_KAFTAN", "LOTR_COSMETIC",
                "SOME_OTHER_MOD" }) {
            assertEquals(material, ArmourWeight.NEITHER, catalogue.weightOf("", material));
        }
        assertEquals(ArmourWeight.HEAVY, catalogue.weightOf("losttales:galadhrim_helmet_heavy",
                "LOTHLORIEN"));
        assertEquals(ArmourWeight.LIGHT, catalogue.weightOf("losttales:galadhrim_helmet_light",
                "LOTHLORIEN"));
        assertEquals(ArmourWeight.LIGHT, catalogue.weightOf("losttales:moon_elf_armor_light",
                "MOON_ELVES"));
    }

    @Test
    public void everySetTakesPiecesTheGameHasAllOfOneWeight() throws IOException {
        ArmourCatalogue catalogue = catalogue();
        Map<String, String> lotr = LotrArmourBytecode.armourItems();
        Map<String, String> english = LostTalesLangFile.english();
        int lightSets = 0;
        int heavySets = 0;
        for (NamedSet set : catalogue.getSets()) {
            Set<ArmourWeight> weights = new HashSet<ArmourWeight>();
            for (ArmourSlot slot : ArmourSlot.values()) {
                for (String item : set.items(slot)) {
                    String material = "";
                    if (item.startsWith("lotr:")) {
                        assertTrue(set.getId() + " takes " + item + ", which LOTR has",
                                lotr.containsKey(item));
                        material = lotr.get(item);
                    } else {
                        String path = item.substring(item.indexOf(':') + 1);
                        assertNotNull(set.getId() + " takes " + item + ", which the mod has",
                                english.get("item." + path + ".name"));
                    }
                    weights.add(catalogue.weightOf(item, material));
                }
            }
            assertEquals(set.getId() + " is of one weight", 1, weights.size());
            ArmourWeight weight = weights.iterator().next();
            assertTrue(set.getId() + " is light or heavy", weight.counts());
            if (!set.isRelic()) {
                if (weight == ArmourWeight.LIGHT) {
                    lightSets++;
                } else {
                    heavySets++;
                }
            }
        }
        assertEquals("28 of LOTR's and 5 of the mod's", 33, lightSets);
        assertEquals("25 of LOTR's and 2 of the mod's", 27, heavySets);
    }

    @Test
    public void eachSetGivesOneEffectAtItsSizeAndTheRelicTwice() {
        Map<SetEffect, Double> size = new HashMap<SetEffect, Double>();
        size.put(SetEffect.PROTECTION, Double.valueOf(0.05D));
        size.put(SetEffect.SPEED, Double.valueOf(0.05D));
        size.put(SetEffect.KNOCKBACK, Double.valueOf(0.1D));
        size.put(SetEffect.EXPERIENCE, Double.valueOf(0.1D));
        size.put(SetEffect.FIRE, Double.valueOf(0.2D));
        size.put(SetEffect.FALLS, Double.valueOf(0.2D));
        size.put(SetEffect.ARROWS, Double.valueOf(0.2D));
        size.put(SetEffect.POISON, Double.valueOf(0.2D));
        Set<String> skills = bundledSkills();
        for (NamedSet set : catalogue().getSets()) {
            if (!set.isRelic()) {
                assertEquals(set.getId() + " gives one effect", 1, set.getBonuses().size());
            }
            for (SetBonus bonus : set.getBonuses()) {
                double expected = size.get(bonus.getEffect()).doubleValue()
                        * (set.isRelic() ? 2.0D : 1.0D);
                assertEquals(set.getId() + " " + bonus.getEffect().word(), expected,
                        bonus.getAmount(), 1.0E-9D);
                if (bonus.getEffect() == SetEffect.EXPERIENCE) {
                    assertTrue(set.getId() + " feeds a bundled skill",
                            skills.contains(bonus.getSkill()));
                }
            }
        }
    }

    @Test
    public void theVariantsCountForTheirSets() {
        ArmourCatalogue catalogue = catalogue();
        assertTaken(catalogue, "losttales:gondorian", "lotr:item.helmetGondorWinged");
        assertTaken(catalogue, "losttales:uruk", "lotr:item.helmetUrukBerserker");
        assertTaken(catalogue, "losttales:coast_southron", "lotr:item.helmetNearHaradWarlord");
        assertTaken(catalogue, "losttales:taurethrim", "lotr:item.helmetTauredainChieftain");
        assertTaken(catalogue, "losttales:golden_rhunic", "lotr:item.helmetRhunWarlord");
        assertTaken(catalogue, "losttales:dwarven", "lotr:item.helmetDwarvenMithril");
        assertTaken(catalogue, "losttales:arnorian_light", "losttales:arnorian_helmet_light_2");
        assertTaken(catalogue, "losttales:arnorian_heavy", "losttales:arnorian_helmet_heavy_2");
        assertTaken(catalogue, "losttales:orocarni_light", "losttales:orocarni_helmet_light_3");
        assertTaken(catalogue, "losttales:galadhrim", "losttales:galadhrim_helmet_light_hood");
        assertTrue("a heavy Galadhrim helmet joins no set", catalogue.setsOf(ArmourSlot.HELMET,
                "losttales:galadhrim_helmet_heavy", "").isEmpty());
        assertEquals("a Dwarven trim counts for the Dwarven Set alone", 1,
                catalogue.setsOf(ArmourSlot.LEGGINGS, "lotr:item.legsDwarvenGold", "").size());
    }

    @Test
    public void theRelicExampleIsTheRangersPiecesMarked() {
        ArmourCatalogue catalogue = catalogue();
        NamedSet relic = catalogue.set("losttales:striders_garb");
        assertTrue(relic.isRelic());
        assertEquals("an unmarked Ranger hood counts for the Ranger Set alone", 1,
                catalogue.setsOf(ArmourSlot.HELMET, "lotr:item.helmetRanger", "").size());
        assertEquals("a marked one counts for both", 2, catalogue.setsOf(ArmourSlot.HELMET,
                "lotr:item.helmetRanger", "losttales:striders_garb").size());
    }

    @Test
    public void everySetHasItsWords() {
        Map<String, String> english = LostTalesLangFile.english();
        for (NamedSet set : catalogue().getSets()) {
            assertTrue(set.getId() + " is worded by its lang lines", set.getName().length() == 0);
            assertNotNull(set.getId() + " has a name", english.get(ArmourWords.nameKey(set.getId())));
            if (set.isRelic()) {
                for (ArmourSlot slot : ArmourSlot.values()) {
                    assertNotNull(english.get(ArmourWords.pieceNameKey(set.getId(), slot)));
                    assertNotNull(english.get(ArmourWords.pieceLoreKey(set.getId(), slot)));
                }
            }
        }
        for (SetEffect effect : SetEffect.values()) {
            assertNotNull(effect.word() + " has its words",
                    english.get("item.losttales.tooltip.set.bonus." + effect.word()));
        }
    }

    private static void assertTaken(ArmourCatalogue catalogue, String setId, String item) {
        NamedSet set = catalogue.set(setId);
        assertNotNull(setId, set);
        boolean taken = false;
        for (ArmourSlot slot : ArmourSlot.values()) {
            taken |= set.items(slot).contains(item);
        }
        assertTrue(setId + " takes " + item, taken);
    }

    private static Set<String> bundledSkills() {
        Set<String> ids = new HashSet<String>();
        for (SkillDefinition skill : DataFiles.readBundled(ProgressionFiles.CLASSPATH,
                ProgressionFiles.SKILLS_INDEX, "skills", ProgressionFiles.SKILLS).values) {
            ids.add(skill.getId());
        }
        return ids;
    }

    static ArmourCatalogue catalogue() {
        List<String> problems = new ArrayList<String>();
        ArmourCatalogue catalogue = Armour.read(ProgressionFiles.CLASSPATH, null, null, problems);
        assertEquals(new ArrayList<String>(), problems);
        return catalogue;
    }
}
