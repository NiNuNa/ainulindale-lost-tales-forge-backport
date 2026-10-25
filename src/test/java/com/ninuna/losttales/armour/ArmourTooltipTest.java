package com.ninuna.losttales.armour;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * An armour piece's tooltip lines (CONTENT-06, CONTENT-07, CONTENT-11): the
 * weight set counted by weight with its bonus and a heavy piece's
 * slowdown, what each slot holds, then each named set the piece counts
 * for, {@code [3/4] Gondorian Set}, with its bonus; a bonus in force in
 * the attribute blue, one not yet in dark grey.
 */
public final class ArmourTooltipTest {
    private static final String IN_FORCE = "§9  ";
    private static final String NOT_YET = "§8  ";

    private static final ArmourTooltip.Words WORDS = new ArmourTooltip.Words() {
        @Override
        public String weightSet(ArmourWeight weight) {
            return weight == ArmourWeight.HEAVY ? "Heavy Armor Set" : "Light Armor Set";
        }

        @Override
        public String setName(NamedSet set) {
            return set.getId();
        }

        @Override
        public String bonus(SetBonus bonus) {
            return bonus.getEffect().word() + " " + bonus.getAmount();
        }

        @Override
        public String weightBonus(ArmourWeight weight, double amount) {
            return weight.word() + " set " + amount;
        }

        @Override
        public String slow(double share) {
            return "slow " + share;
        }

        @Override
        public String slotItem(ArmourSlot slot) {
            return slot.word();
        }
    };

    @Test
    public void aHeavyPieceCountsItsWeightAndItsSets() {
        ArmourCatalogue catalogue = ArmourRulesTest.catalogue();
        WornArmour worn = ArmourRulesTest.worn(catalogue, "test:heavy_helmet_winged",
                "test:heavy_body", "test:heavy_legs", "test:light_boots");
        List<String> lines = ArmourTooltip.lines(WORDS, worn, catalogue, ArmourSettings.SHIPPED,
                ArmourWeight.HEAVY, ArmourSlot.CHESTPLATE, "test:heavy_body", "");
        assertEquals("§e[3/4] §r§7Heavy Armor Set", lines.get(0));
        assertEquals(NOT_YET + "heavy set 0.2", lines.get(1));
        assertEquals(NOT_YET + "slow 0.05", lines.get(2));
        assertEquals("§e>§o helmet", lines.get(3));
        assertEquals("light boots do not count for the heavy set", "§8>§o boots",
                lines.get(6));
        assertEquals("§e[3/4] §r§7test:gondorian", lines.get(7));
        assertEquals(NOT_YET + "protection 0.05", lines.get(8));
        assertEquals("an unmarked piece counts for no relic set", 9, lines.size());
    }

    @Test
    public void aCompleteSetsBonusIsInForce() {
        ArmourCatalogue catalogue = ArmourRulesTest.catalogue();
        WornArmour worn = ArmourRulesTest.worn(catalogue, "test:light_helmet", "test:light_body",
                "test:light_legs", "test:scout_boots");
        List<String> lines = ArmourTooltip.lines(WORDS, worn, catalogue, ArmourSettings.SHIPPED,
                ArmourWeight.LIGHT, ArmourSlot.BOOTS, "test:scout_boots", "");
        assertEquals(IN_FORCE + "light set 0.05", lines.get(1));
        assertTrue("a light piece slows nothing", lines.get(2).startsWith("§e>"));
        assertTrue(lines.contains("§e[4/4] §r§7test:scouts"));
        assertTrue(lines.contains(IN_FORCE + "fire 0.2"));
        assertTrue(lines.contains(IN_FORCE + "knockback 0.1"));
    }

    @Test
    public void aRelicPieceCountsForItsMakesSetAndItsRelicSet() {
        ArmourCatalogue catalogue = ArmourRulesTest.catalogue();
        List<String> lines = ArmourTooltip.lines(WORDS, WornArmour.NONE, catalogue,
                ArmourSettings.SHIPPED, ArmourWeight.HEAVY, ArmourSlot.HELMET,
                "test:heavy_helmet", "test:isildurs");
        assertTrue(lines.contains("§e[0/4] §r§7test:gondorian"));
        assertTrue(lines.contains("§e[0/4] §r§7test:isildurs"));
        assertTrue(lines.contains(NOT_YET + "experience 0.2"));
    }

    @Test
    public void aPieceOfNeitherWeightInNoSetTellsNothing() {
        ArmourCatalogue catalogue = ArmourRulesTest.catalogue();
        assertFalse(ArmourTooltip.tells(catalogue, ArmourWeight.NEITHER, ArmourSlot.HELMET,
                "othermod:hat", ""));
        assertTrue(ArmourTooltip.lines(WORDS, WornArmour.NONE, catalogue, ArmourSettings.SHIPPED,
                ArmourWeight.NEITHER, ArmourSlot.HELMET, "othermod:hat", "").isEmpty());
        assertTrue(ArmourTooltip.tells(catalogue, ArmourWeight.LIGHT, ArmourSlot.HELMET,
                "othermod:cap", ""));
    }
}
