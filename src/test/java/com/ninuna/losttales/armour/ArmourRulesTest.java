package com.ninuna.losttales.armour;

import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.perk.PerkIndex;
import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.PerkEffect;
import com.ninuna.losttales.progression.skill.SkillAct;
import com.ninuna.losttales.progression.skill.SkillCount;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * What the armour worn comes to (CONTENT-06, CONTENT-07, CONTENT-11,
 * PROG-17, PROG-18, PROG-19): weights and weight sets, named sets and
 * relic sets counted, the armour skills' perks working as far as the
 * pieces let them, speed, footing and what is left of a blow.
 */
public final class ArmourRulesTest {
    private static final double EPSILON = 1.0E-9D;
    private static final ArmourSettings SHIPPED = ArmourSettings.SHIPPED;

    @Test
    public void fourPiecesOfOneWeightAreAWeightSet() {
        ArmourCatalogue catalogue = catalogue();
        WornArmour light = worn(catalogue, "test:light_helmet", "test:light_body",
                "test:light_legs", "test:light_boots");
        assertEquals(ArmourWeight.LIGHT, light.weightSet());
        assertEquals(4, light.count(ArmourWeight.LIGHT));
        WornArmour mixed = worn(catalogue, "test:heavy_helmet", "test:light_body",
                "test:light_legs", "test:light_boots");
        assertEquals(null, mixed.weightSet());
        assertEquals(1, mixed.count(ArmourWeight.HEAVY));
        WornArmour bare = worn(catalogue, null, null, null, null);
        assertEquals(null, bare.weightSet());
        assertEquals(ArmourWeight.NEITHER, bare.weight(ArmourSlot.HELMET));
    }

    @Test
    public void theWeightSetsBonusesAndTheHeavySlowdown() {
        ArmourCatalogue catalogue = catalogue();
        WornArmour light = worn(catalogue, "test:light_helmet", "test:light_body",
                "test:light_legs", "test:light_boots");
        WornArmour heavy = worn(catalogue, "test:heavy_helmet", "test:heavy_body",
                "test:heavy_legs", "test:heavy_boots");
        WornArmour oneHeavy = worn(catalogue, "test:heavy_helmet", "test:light_body",
                "test:light_legs", "test:light_boots");
        assertEquals(0.05D, ArmourRules.speed(light, PerkIndex.EMPTY, SHIPPED), EPSILON);
        assertEquals(0.0D, ArmourRules.footing(light, PerkIndex.EMPTY, SHIPPED), EPSILON);
        assertEquals("15% slower in four heavy pieces", -0.15D,
                ArmourRules.speed(heavy, PerkIndex.EMPTY, SHIPPED), EPSILON);
        assertEquals(0.2D, ArmourRules.footing(heavy, PerkIndex.EMPTY, SHIPPED), EPSILON);
        assertEquals("a heavy helmet slows 2.5%, and makes no weight set", -0.025D,
                ArmourRules.speed(oneHeavy, PerkIndex.EMPTY, SHIPPED), EPSILON);
        assertEquals(0.0D, ArmourRules.footing(oneHeavy, PerkIndex.EMPTY, SHIPPED), EPSILON);
        ArmourSettings server = ArmourSettings.ofPercent(10.0D, 30.0D, 0.0D, 1.0D);
        assertEquals(0.1D, ArmourRules.speed(light, PerkIndex.EMPTY, server), EPSILON);
        assertEquals(-0.02D, ArmourRules.speed(heavy, PerkIndex.EMPTY, server), EPSILON);
    }

    @Test
    public void unhinderedAndTowerOfStrengthGrowTheWeightSet() {
        ArmourCatalogue catalogue = catalogue();
        WornArmour light = worn(catalogue, "test:light_helmet", "test:light_body",
                "test:light_legs", "test:light_boots");
        WornArmour heavy = worn(catalogue, "test:heavy_helmet", "test:heavy_body",
                "test:heavy_legs", "test:heavy_boots");
        assertEquals("Unhindered: 10% with the weight set's", 0.1D, ArmourRules.speed(light,
                perks(light, "losttales:light_armour", "unhindered", 1), SHIPPED), EPSILON);
        assertEquals("Tower of Strength: 50% with the weight set's", 0.5D,
                ArmourRules.footing(heavy, perks(heavy, "losttales:heavy_armour",
                        "tower_of_strength", 1), SHIPPED), EPSILON);
    }

    @Test
    public void conditioningTakesItsShareOffEachHeavyPiecesSlowdown() {
        ArmourCatalogue catalogue = catalogue();
        WornArmour heavy = worn(catalogue, "test:heavy_helmet", "test:heavy_body",
                "test:heavy_legs", "test:heavy_boots");
        WornArmour twoHeavy = worn(catalogue, "test:heavy_helmet", "test:heavy_body",
                "test:light_legs", "test:light_boots");
        assertEquals(-0.075D, ArmourRules.speed(heavy,
                perks(heavy, "losttales:heavy_armour", "conditioning", 1), SHIPPED), EPSILON);
        assertEquals("not at all at rank II", 0.0D, ArmourRules.speed(heavy,
                perks(heavy, "losttales:heavy_armour", "conditioning", 2), SHIPPED), EPSILON);
        assertEquals("rank II takes it all off two heavy pieces too", 0.0D,
                ArmourRules.speed(twoHeavy, perks(twoHeavy, "losttales:heavy_armour",
                        "conditioning", 2), SHIPPED), EPSILON);
    }

    @Test
    public void protectionTakesOneShareAfterAnother() {
        ArmourCatalogue catalogue = catalogue();
        WornArmour gondorian = worn(catalogue, "test:heavy_helmet", "test:heavy_body",
                "test:heavy_legs", "test:heavy_boots");
        ProgressionState state = new ProgressionState();
        state.setPerkRank("losttales:heavy_armour", "juggernaut", 3);
        state.setPerkRank("losttales:heavy_armour", "well_fitted", 1);
        state.setPerkRank("losttales:heavy_armour", "matching_set", 1);
        PerkIndex perks = PerkIndex.of(state, skills()).armoured(gondorian);
        ArmourRules.Harm blow = new ArmourRules.Harm(true, false, false, false);
        // Juggernaut III, Well Fitted, Matching Set, then the set's own 5% (PROG-19).
        assertEquals(0.85D * 0.9D * 0.9D * 0.95D,
                ArmourRules.left(gondorian, perks, blow, false), EPSILON);
        assertEquals("Standing Firm not taken takes nothing",
                0.85D * 0.9D * 0.9D * 0.95D,
                ArmourRules.left(gondorian, perks, blow, true), EPSILON);
        ArmourRules.Harm pastArmour = new ArmourRules.Harm(false, false, false, false);
        assertEquals("nothing protects from what passes the armour", 1.0D,
                ArmourRules.left(gondorian, perks, pastArmour, false), EPSILON);
        state.setPerkRank("losttales:heavy_armour", "tower_of_strength", 1);
        state.setPerkRank("losttales:heavy_armour", "standing_firm", 1);
        perks = PerkIndex.of(state, skills()).armoured(gondorian);
        assertEquals(0.85D * 0.9D * 0.9D * 0.95D * 0.9D,
                ArmourRules.left(gondorian, perks, blow, true), EPSILON);
        assertEquals(0.85D * 0.9D * 0.9D * 0.95D,
                ArmourRules.left(gondorian, perks, blow, false), EPSILON);
    }

    @Test
    public void aRankPerkCountsAQuarterForEachPieceOfItsWeight() {
        ArmourCatalogue catalogue = catalogue();
        WornArmour twoLight = worn(catalogue, "test:light_helmet", "test:light_body",
                "test:heavy_legs", null);
        PerkIndex perks = perks(twoLight, "losttales:light_armour", "agile_defender", 3);
        assertEquals(0.075D, perks.total(PerkEffect.WEIGHT_PROTECTION), EPSILON);
        assertEquals(1.0D - 0.075D, ArmourRules.left(twoLight, perks,
                new ArmourRules.Harm(true, false, false, false), false), EPSILON);
        WornArmour none = worn(catalogue, "test:heavy_helmet", null, null, null);
        assertFalse(perks(none, "losttales:light_armour", "agile_defender", 3)
                .has(PerkEffect.WEIGHT_PROTECTION));
    }

    @Test
    public void everyOtherPerkNeedsAllFourPiecesOfItsWeight() {
        ArmourCatalogue catalogue = catalogue();
        WornArmour threeLight = worn(catalogue, "test:light_helmet", "test:light_body",
                "test:light_legs", "test:heavy_boots");
        WornArmour fourLight = worn(catalogue, "test:light_helmet", "test:light_body",
                "test:light_legs", "test:light_boots");
        assertFalse(perks(threeLight, "losttales:light_armour", "custom_fit", 1)
                .has(PerkEffect.PROTECTION));
        assertTrue(perks(fourLight, "losttales:light_armour", "custom_fit", 1)
                .has(PerkEffect.PROTECTION));
        assertFalse("four light pieces of no named set", perks(fourLight,
                "losttales:light_armour", "matching_set", 1).has(PerkEffect.MATCHING_SET));
        assertFalse("a heavy perk in light pieces", perks(fourLight,
                "losttales:heavy_armour", "well_fitted", 1).has(PerkEffect.PROTECTION));
    }

    @Test
    public void aPerkOfNoArmourSkillIgnoresTheArmour() {
        ProgressionState state = new ProgressionState();
        state.setPerkRank("losttales:lore", "sage", 1);
        PerkIndex taken = PerkIndex.of(state, skills());
        assertFalse(taken.hasArmourPerks());
        assertTrue(taken.armoured(WornArmour.NONE) == taken);
        assertEquals(1.0D, taken.total(PerkEffect.PERK_POINT), EPSILON);
    }

    @Test
    public void namedSetsCountTheirSlotsVariantsAndRelics() {
        ArmourCatalogue catalogue = catalogue();
        NamedSet gondorian = catalogue.set("test:gondorian");
        NamedSet relic = catalogue.set("test:isildurs");
        WornArmour three = worn(catalogue, "test:heavy_helmet_winged", "test:heavy_body",
                "test:heavy_legs", "test:light_boots");
        assertEquals("the winged helmet counts", 3, three.count(gondorian));
        assertFalse(three.hasCompleteSet());
        Map<ArmourSlot, WornArmour.Piece> pieces = pieces("test:heavy_helmet", "test:heavy_body",
                "test:heavy_legs", "test:heavy_boots");
        pieces.put(ArmourSlot.HELMET, new WornArmour.Piece("test:heavy_helmet", "", "test:isildurs", 2));
        WornArmour marked = WornArmour.of(pieces, catalogue);
        assertEquals("a relic piece counts for its make's set", 4, marked.count(gondorian));
        assertEquals("and for its relic set", 1, marked.count(relic));
        assertEquals(Arrays.asList(gondorian), marked.completeSets());
        Map<ArmourSlot, WornArmour.Piece> allMarked = new EnumMap<ArmourSlot, WornArmour.Piece>(
                ArmourSlot.class);
        for (Map.Entry<ArmourSlot, WornArmour.Piece> entry : pieces.entrySet()) {
            allMarked.put(entry.getKey(), new WornArmour.Piece(entry.getValue().getItemId(), "",
                    "test:isildurs", 2));
        }
        WornArmour relicSet = WornArmour.of(allMarked, catalogue);
        assertEquals(2, relicSet.completeSets().size());
        assertEquals("both bonuses: the make's 5% and the relic's 10%", 0.95D * 0.9D,
                relicSet.left(SetEffect.PROTECTION), EPSILON);
        assertEquals(0.2D, relicSet.experience("losttales:lore"), EPSILON);
        assertEquals(0.0D, relicSet.experience("losttales:archery"), EPSILON);
        PerkIndex perks = perks(relicSet, "losttales:heavy_armour", "matching_set", 1);
        assertEquals("Matching Set counts once however many sets are complete", 1,
                perks.taken(PerkEffect.MATCHING_SET).size());
        assertEquals(0.9D, ArmourRules.left(WornArmour.NONE, perks,
                new ArmourRules.Harm(true, false, false, false), false), EPSILON);
    }

    @Test
    public void theSetsFireFallsArrowsSpeedAndFooting() {
        ArmourCatalogue catalogue = catalogue();
        WornArmour scouts = worn(catalogue, "test:light_helmet", "test:light_body",
                "test:light_legs", "test:scout_boots");
        assertEquals("one set of pieces can complete two named sets", 2,
                scouts.completeSets().size());
        assertEquals(0.8D, ArmourRules.left(scouts, PerkIndex.EMPTY,
                new ArmourRules.Harm(false, true, false, false), false), EPSILON);
        assertEquals(0.8D, ArmourRules.left(scouts, PerkIndex.EMPTY,
                new ArmourRules.Harm(true, false, false, true), false), EPSILON);
        assertEquals(1.0D, ArmourRules.left(scouts, PerkIndex.EMPTY,
                new ArmourRules.Harm(false, false, true, false), false), EPSILON);
        assertEquals("the weight set's 5% and the set's 5%", 0.1D,
                ArmourRules.speed(scouts, PerkIndex.EMPTY, SHIPPED), EPSILON);
        assertEquals(0.1D, ArmourRules.footing(scouts, PerkIndex.EMPTY, SHIPPED), EPSILON);
        assertEquals(0.75D, scouts.left(SetEffect.POISON), EPSILON);
    }

    @Test
    public void armourPointsAreSharedByWeight() {
        ArmourCatalogue catalogue = catalogue();
        Map<ArmourSlot, WornArmour.Piece> pieces = new EnumMap<ArmourSlot, WornArmour.Piece>(
                ArmourSlot.class);
        pieces.put(ArmourSlot.CHESTPLATE, new WornArmour.Piece("test:heavy_body", "", "", 6));
        pieces.put(ArmourSlot.HELMET, new WornArmour.Piece("test:light_helmet", "", "", 2));
        pieces.put(ArmourSlot.LEGGINGS, new WornArmour.Piece("test:light_legs", "", "", 4));
        pieces.put(ArmourSlot.BOOTS, new WornArmour.Piece("othermod:boots", "", "", 2));
        WornArmour worn = WornArmour.of(pieces, catalogue);
        assertEquals(6, worn.points(ArmourWeight.HEAVY));
        assertEquals(6, worn.points(ArmourWeight.LIGHT));
        assertEquals(14, worn.totalPoints());
        Map<ArmourWeight, Double> kept = com.ninuna.losttales.progression.count.CountEvents
                .kept(worn, 10.0D, 14);
        double armour = 10.0D * 14 / 25.0D;
        assertEquals("a heavy chestplate of 6 and light pieces of 6 train both by half",
                armour * 6 / 14, kept.get(ArmourWeight.HEAVY).doubleValue(), EPSILON);
        assertEquals(armour * 6 / 14, kept.get(ArmourWeight.LIGHT).doubleValue(), EPSILON);
        assertFalse("a piece of neither weight trains nothing",
                kept.containsKey(ArmourWeight.NEITHER));
    }

    @Test
    public void theKeyChangesWithThePiecesAndTheCatalogue() {
        ArmourCatalogue catalogue = catalogue();
        WornArmour one = worn(catalogue, "test:light_helmet", null, null, null);
        WornArmour same = worn(catalogue, "test:light_helmet", null, null, null);
        WornArmour other = worn(catalogue, "test:heavy_helmet", null, null, null);
        assertEquals(one.key(), same.key());
        assertNotEquals(one.key(), other.key());
        assertNotEquals(one.key(), worn(catalogue(), "test:light_helmet", null, null, null).key());
    }

    static ArmourCatalogue catalogue() {
        Map<String, ArmourWeight> items = new LinkedHashMap<String, ArmourWeight>();
        for (String light : new String[] { "test:light_helmet", "test:light_body",
                "test:light_legs", "test:light_boots", "test:scout_boots" }) {
            items.put(light, ArmourWeight.LIGHT);
        }
        for (String heavy : new String[] { "test:heavy_helmet", "test:heavy_helmet_winged",
                "test:heavy_body", "test:heavy_legs", "test:heavy_boots" }) {
            items.put(heavy, ArmourWeight.HEAVY);
        }
        List<NamedSet> sets = new ArrayList<NamedSet>();
        sets.add(set("test:gondorian", false, new String[] { "test:heavy_helmet",
                "test:heavy_helmet_winged" }, "test:heavy_body", "test:heavy_legs",
                "test:heavy_boots", new SetBonus(SetEffect.PROTECTION, 0.05D, "")));
        sets.add(set("test:isildurs", true, new String[] { "test:heavy_helmet" },
                "test:heavy_body", "test:heavy_legs", "test:heavy_boots",
                new SetBonus(SetEffect.PROTECTION, 0.1D, ""),
                new SetBonus(SetEffect.EXPERIENCE, 0.2D, "losttales:lore")));
        sets.add(set("test:scouts", false, new String[] { "test:light_helmet" },
                "test:light_body", "test:light_legs", "test:scout_boots",
                new SetBonus(SetEffect.FIRE, 0.2D, ""), new SetBonus(SetEffect.ARROWS, 0.2D, ""),
                new SetBonus(SetEffect.SPEED, 0.05D, "")));
        sets.add(set("test:footing", false, new String[] { "test:light_helmet" },
                "test:light_body", "test:light_legs", "test:scout_boots",
                new SetBonus(SetEffect.KNOCKBACK, 0.1D, ""),
                new SetBonus(SetEffect.POISON, 0.25D, "")));
        return ArmourCatalogue.of(Collections.singletonList(new WeightFile("test:weights",
                new LinkedHashMap<String, ArmourWeight>(), items)), sets);
    }

    private static NamedSet set(String id, boolean relic, String[] helmets, String chest,
                                String legs, String boots, SetBonus... bonuses) {
        Map<ArmourSlot, List<String>> slots = new EnumMap<ArmourSlot, List<String>>(ArmourSlot.class);
        slots.put(ArmourSlot.HELMET, Arrays.asList(helmets));
        slots.put(ArmourSlot.CHESTPLATE, Collections.singletonList(chest));
        slots.put(ArmourSlot.LEGGINGS, Collections.singletonList(legs));
        slots.put(ArmourSlot.BOOTS, Collections.singletonList(boots));
        return new NamedSet(id, relic, slots, Arrays.asList(bonuses), "", null, null);
    }

    static WornArmour worn(ArmourCatalogue catalogue, String helmet, String body, String legs,
                           String boots) {
        return WornArmour.of(pieces(helmet, body, legs, boots), catalogue);
    }

    private static Map<ArmourSlot, WornArmour.Piece> pieces(String helmet, String body,
                                                            String legs, String boots) {
        Map<ArmourSlot, WornArmour.Piece> pieces = new EnumMap<ArmourSlot, WornArmour.Piece>(
                ArmourSlot.class);
        String[] items = { helmet, body, legs, boots };
        ArmourSlot[] slots = { ArmourSlot.HELMET, ArmourSlot.CHESTPLATE, ArmourSlot.LEGGINGS,
                ArmourSlot.BOOTS };
        for (int index = 0; index < items.length; index++) {
            if (items[index] != null) {
                pieces.put(slots[index], new WornArmour.Piece(items[index], "", "", 2));
            }
        }
        return pieces;
    }

    private static PerkIndex perks(WornArmour worn, String skill, String perk, int rank) {
        ProgressionState state = new ProgressionState();
        state.setPerkRank(skill, perk, rank);
        return PerkIndex.of(state, skills()).armoured(worn);
    }

    static Map<String, SkillDefinition> skills() {
        Map<String, SkillDefinition> skills = new LinkedHashMap<String, SkillDefinition>();
        List<SkillCount> armour = Collections.singletonList(
                new SkillCount(SkillAct.ARMOUR, 1.0D, null, null, null));
        skills.put("losttales:light_armour", new SkillDefinition("losttales:light_armour", "", 5,
                "", "", armour, Arrays.asList(
                        perk("agile_defender", "weight_protection", 0.05D, 10, 30, 50),
                        perk("custom_fit", "protection", 0.1D, 30),
                        perk("unhindered", "speed", 0.05D, 40),
                        perk("matching_set", "matching_set", 0.1D, 50)), ArmourWeight.LIGHT));
        skills.put("losttales:heavy_armour", new SkillDefinition("losttales:heavy_armour", "", 6,
                "", "", armour, Arrays.asList(
                        perk("juggernaut", "weight_protection", 0.05D, 10, 30, 50),
                        perk("conditioning", "conditioning", 0.5D, 20, 60),
                        perk("well_fitted", "protection", 0.1D, 30),
                        perk("tower_of_strength", "knockback", 0.3D, 40),
                        perk("matching_set", "matching_set", 0.1D, 50),
                        perk("standing_firm", "standing_firm", 0.1D, 80)), ArmourWeight.HEAVY));
        skills.put("losttales:lore", new SkillDefinition("losttales:lore", "", 12, "", "",
                Collections.<SkillCount>emptyList(), Collections.singletonList(
                        perk("sage", "perk_point", 1.0D, 100))));
        return skills;
    }

    private static PerkDefinition perk(String id, String effect, double amount, int... levels) {
        return new PerkDefinition(id, levels, "", 0, effect, amount, null, null, true, "", "");
    }
}
