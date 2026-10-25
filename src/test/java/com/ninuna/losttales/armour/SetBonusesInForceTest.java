package com.ninuna.losttales.armour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Which set bonuses the inventory's effect column shows (CONTENT-12):
 * the weight set's while all four pieces are of one weight and the
 * server gives it something, then every complete named set with a
 * bonus, a relic set beside its make's; none for a broken set.
 */
public final class SetBonusesInForceTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    public void nothingWornShowsNothing() {
        assertTrue(SetBonusesInForce.of(WornArmour.NONE, ArmourSettings.SHIPPED).isEmpty());
        assertFalse(SetBonusesInForce.any(WornArmour.NONE, ArmourSettings.SHIPPED));
        assertTrue(SetBonusesInForce.of(null, ArmourSettings.SHIPPED).isEmpty());
        assertTrue(SetBonusesInForce.of(WornArmour.NONE, null).isEmpty());
    }

    @Test
    public void fourLightPiecesShowTheWeightSetsSpeed() {
        WornArmour light = ArmourRulesTest.worn(ArmourRulesTest.catalogue(), "test:light_helmet",
                "test:light_body", "test:light_legs", "test:light_boots");
        List<SetBonusesInForce.Entry> entries = SetBonusesInForce.of(light,
                ArmourSettings.SHIPPED);
        assertEquals(1, entries.size());
        SetBonusesInForce.Entry entry = entries.get(0);
        assertTrue(entry.isWeightSet());
        assertEquals(ArmourWeight.LIGHT, entry.getWeight());
        assertEquals(0.05D, entry.getWeightAmount(), EPSILON);
        assertNull(entry.getSet());
        assertEquals("one line: what the weight set gives", 1, entry.effectLines());
    }

    @Test
    public void aWeightSetTheServerGivesNothingShowsNothing() {
        WornArmour light = ArmourRulesTest.worn(ArmourRulesTest.catalogue(), "test:light_helmet",
                "test:light_body", "test:light_legs", "test:light_boots");
        ArmourSettings nothing = ArmourSettings.ofPercent(0.0D, 20.0D, 2.5D, 5.0D);
        assertTrue(SetBonusesInForce.of(light, nothing).isEmpty());
        WornArmour heavy = ArmourRulesTest.worn(ArmourRulesTest.catalogue(), "test:heavy_helmet",
                "test:heavy_body", "test:heavy_legs", "test:heavy_boots");
        List<SetBonusesInForce.Entry> entries = SetBonusesInForce.of(heavy, nothing);
        assertEquals(ArmourWeight.HEAVY, entries.get(0).getWeight());
        assertEquals(0.2D, entries.get(0).getWeightAmount(), EPSILON);
    }

    @Test
    public void aCompleteNamedSetFollowsTheWeightSet() {
        ArmourCatalogue catalogue = ArmourRulesTest.catalogue();
        WornArmour gondorian = ArmourRulesTest.worn(catalogue, "test:heavy_helmet_winged",
                "test:heavy_body", "test:heavy_legs", "test:heavy_boots");
        List<SetBonusesInForce.Entry> entries = SetBonusesInForce.of(gondorian,
                ArmourSettings.SHIPPED);
        assertEquals(2, entries.size());
        assertTrue(entries.get(0).isWeightSet());
        assertFalse(entries.get(1).isWeightSet());
        assertSame(catalogue.set("test:gondorian"), entries.get(1).getSet());
        assertNull(entries.get(1).getWeight());
        assertEquals(1, entries.get(1).effectLines());
    }

    @Test
    public void aNamedSetsLinesAreItsEffects() {
        ArmourCatalogue catalogue = ArmourRulesTest.catalogue();
        WornArmour scouts = ArmourRulesTest.worn(catalogue, "test:light_helmet",
                "test:light_body", "test:light_legs", "test:scout_boots");
        List<SetBonusesInForce.Entry> entries = SetBonusesInForce.of(scouts,
                ArmourSettings.SHIPPED);
        assertEquals("the weight set, the scouts' and the footing set", 3, entries.size());
        assertEquals("test:scouts", entries.get(1).getSet().getId());
        assertEquals(3, entries.get(1).effectLines());
        assertEquals("test:footing", entries.get(2).getSet().getId());
        assertEquals(2, entries.get(2).effectLines());
    }

    @Test
    public void aBrokenSetShowsNothing() {
        ArmourCatalogue catalogue = ArmourRulesTest.catalogue();
        WornArmour three = ArmourRulesTest.worn(catalogue, null, "test:heavy_body",
                "test:heavy_legs", "test:heavy_boots");
        assertTrue(SetBonusesInForce.of(three, ArmourSettings.SHIPPED).isEmpty());
        WornArmour mixed = ArmourRulesTest.worn(catalogue, "test:light_helmet",
                "test:heavy_body", "test:heavy_legs", "test:heavy_boots");
        assertTrue("no weight set and no named set",
                SetBonusesInForce.of(mixed, ArmourSettings.SHIPPED).isEmpty());
    }

    @Test
    public void aRelicSetStandsBesideItsMakes() {
        ArmourCatalogue catalogue = ArmourRulesTest.catalogue();
        Map<ArmourSlot, WornArmour.Piece> pieces =
                new EnumMap<ArmourSlot, WornArmour.Piece>(ArmourSlot.class);
        pieces.put(ArmourSlot.HELMET, new WornArmour.Piece("test:heavy_helmet", "",
                "test:isildurs", 3));
        pieces.put(ArmourSlot.CHESTPLATE, new WornArmour.Piece("test:heavy_body", "",
                "test:isildurs", 8));
        pieces.put(ArmourSlot.LEGGINGS, new WornArmour.Piece("test:heavy_legs", "",
                "test:isildurs", 6));
        pieces.put(ArmourSlot.BOOTS, new WornArmour.Piece("test:heavy_boots", "",
                "test:isildurs", 3));
        List<SetBonusesInForce.Entry> entries = SetBonusesInForce.of(
                WornArmour.of(pieces, catalogue), ArmourSettings.SHIPPED);
        List<String> keys = new ArrayList<String>();
        for (SetBonusesInForce.Entry entry : entries) {
            keys.add(entry.key());
        }
        assertEquals(java.util.Arrays.asList("weight:HEAVY", "set:test:gondorian",
                "set:test:isildurs"), keys);
        assertEquals(2, entries.get(2).effectLines());
    }

    @Test
    public void aNamedSetWithoutABonusShowsNothing() {
        Map<String, ArmourWeight> items = new LinkedHashMap<String, ArmourWeight>();
        Map<ArmourSlot, List<String>> slots =
                new EnumMap<ArmourSlot, List<String>>(ArmourSlot.class);
        for (ArmourSlot slot : ArmourSlot.values()) {
            slots.put(slot, Collections.singletonList("test:robe_" + slot.word()));
        }
        ArmourCatalogue catalogue = ArmourCatalogue.of(Collections.singletonList(
                new WeightFile("test:weights", new LinkedHashMap<String, ArmourWeight>(), items)),
                Collections.singletonList(new NamedSet("test:robes", false, slots,
                        new ArrayList<SetBonus>(), "", null, null)));
        WornArmour robes = ArmourRulesTest.worn(catalogue, "test:robe_helmet",
                "test:robe_chestplate", "test:robe_leggings", "test:robe_boots");
        assertEquals(1, robes.completeSets().size());
        assertTrue(SetBonusesInForce.of(robes, ArmourSettings.SHIPPED).isEmpty());
    }

    @Test
    public void keysTellTheBonusesApart() {
        WornArmour light = ArmourRulesTest.worn(ArmourRulesTest.catalogue(), "test:light_helmet",
                "test:light_body", "test:light_legs", "test:light_boots");
        WornArmour heavy = ArmourRulesTest.worn(ArmourRulesTest.catalogue(), "test:heavy_helmet",
                "test:heavy_body", "test:heavy_legs", "test:heavy_boots");
        assertNotEquals(SetBonusesInForce.of(light, ArmourSettings.SHIPPED).get(0).key(),
                SetBonusesInForce.of(heavy, ArmourSettings.SHIPPED).get(0).key());
        assertEquals(SetBonusesInForce.of(light, ArmourSettings.SHIPPED).get(0).key(),
                SetBonusesInForce.of(light, ArmourSettings.SHIPPED).get(0).key());
    }
}
