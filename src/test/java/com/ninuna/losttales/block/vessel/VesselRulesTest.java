package com.ninuna.losttales.block.vessel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A vessel gives up what it holds one way (CONTENT-09). */
public final class VesselRulesTest {

    @Test
    public void aSealedVesselGivesUpOnlyWhenBroken() {
        assertTrue(VesselRules.givesUp(VesselRules.Way.BREAKING, true));
        assertFalse(VesselRules.givesUp(VesselRules.Way.QUICK_LOOT, true));
        assertFalse(VesselRules.givesUp(VesselRules.Way.RIGHT_CLICK, true));
        assertFalse(VesselRules.givesUp(VesselRules.Way.HOPPER, true));
    }

    @Test
    public void anUnsealedVesselGivesUpOnlyThroughQuickLoot() {
        assertTrue(VesselRules.givesUp(VesselRules.Way.QUICK_LOOT, false));
        assertFalse("broken, it keeps its items inside",
                VesselRules.givesUp(VesselRules.Way.BREAKING, false));
        assertFalse(VesselRules.givesUp(VesselRules.Way.RIGHT_CLICK, false));
        assertFalse(VesselRules.givesUp(VesselRules.Way.HOPPER, false));
        assertFalse(VesselRules.givesUp(null, false));
    }

    @Test
    public void breakingASealedVesselSpillsWhatItHeld() {
        assertEquals(VesselRules.Break.SPILL_AND_DROP_EMPTY,
                VesselRules.onBreak(true, true, false, true));
        assertEquals("a creative player's break drops no vessel", VesselRules.Break.SPILL,
                VesselRules.onBreak(true, true, true, true));
        assertEquals(VesselRules.Break.SPILL, VesselRules.onBreak(true, true, false, false));
        assertEquals(VesselRules.Break.DROP_EMPTY, VesselRules.onBreak(true, false, false, true));
    }

    @Test
    public void breakingAnUnsealedVesselKeepsItsItemsInside() {
        assertEquals(VesselRules.Break.DROP_FILLED,
                VesselRules.onBreak(false, true, false, true));
        assertEquals("a creative player's break keeps the items as a shulker box does",
                VesselRules.Break.DROP_FILLED, VesselRules.onBreak(false, true, true, true));
        assertEquals("where blocks drop nothing, the items fall out as a chest's do",
                VesselRules.Break.SPILL, VesselRules.onBreak(false, true, false, false));
    }

    @Test
    public void anEmptyVesselDropsItselfOutsideCreative() {
        assertEquals(VesselRules.Break.DROP_EMPTY, VesselRules.onBreak(false, false, false, true));
        assertEquals(VesselRules.Break.NOTHING, VesselRules.onBreak(false, false, true, true));
        assertEquals(VesselRules.Break.NOTHING, VesselRules.onBreak(false, false, false, false));
    }

    @Test
    public void whatEachBreakDrops() {
        assertTrue(VesselRules.Break.SPILL_AND_DROP_EMPTY.spills());
        assertTrue(VesselRules.Break.SPILL_AND_DROP_EMPTY.dropsEmptyVessel());
        assertTrue(VesselRules.Break.SPILL.spills());
        assertFalse(VesselRules.Break.SPILL.dropsEmptyVessel());
        assertFalse(VesselRules.Break.DROP_FILLED.spills());
        assertFalse(VesselRules.Break.DROP_FILLED.dropsEmptyVessel());
        assertTrue(VesselRules.Break.DROP_EMPTY.dropsEmptyVessel());
        assertFalse(VesselRules.Break.NOTHING.spills());
        assertFalse(VesselRules.Break.NOTHING.dropsEmptyVessel());
    }

    @Test
    public void emptyVesselsStackAndAFilledOneStandsAlone() {
        assertEquals(16, VesselRules.stackLimit(false));
        assertEquals(1, VesselRules.stackLimit(true));
    }

    @Test
    public void noVesselTakesAFilledVessel() {
        assertFalse(VesselRules.mayHold(true, true));
        assertTrue("an empty vessel goes in as any item", VesselRules.mayHold(true, false));
        assertTrue(VesselRules.mayHold(false, true));
        assertTrue(VesselRules.mayHold(false, false));
    }
}
