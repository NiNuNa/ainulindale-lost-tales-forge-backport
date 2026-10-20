package com.ninuna.losttales.quest;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesQuestParamsTest {

    @Test
    public void aValueIsReadTrimmedAndEmptyForNone() {
        Map<String, String> params = new HashMap<String, String>();
        params.put("entity", "  lotr:gondor_soldier ");

        assertEquals("lotr:gondor_soldier", LostTalesQuestParams.value(params, "entity"));
        assertEquals("", LostTalesQuestParams.value(params, "group"));
        assertEquals("", LostTalesQuestParams.value(null, "entity"));
    }

    @Test
    public void aBlankValueCountsAsAbsent() {
        Map<String, String> params = new HashMap<String, String>();
        params.put("x", "   ");

        assertEquals("", LostTalesQuestParams.value(params, "x"));
    }

    @Test
    public void numbersAreReadWithSpacesIgnored() {
        assertEquals(3, LostTalesQuestParams.parseInt(" 3 ", 1));
        assertEquals(1, LostTalesQuestParams.parseInt("three", 1));
        assertEquals(1, LostTalesQuestParams.parseInt(null, 1));
        assertEquals(-9999, LostTalesQuestParams.parseInt("", -9999));

        assertEquals(1.5D, LostTalesQuestParams.parseDouble(" 1.5 ", 0.0D), 0.0D);
        assertEquals(3.0D, LostTalesQuestParams.parseDouble("far", 3.0D), 0.0D);
        assertEquals(3.0D, LostTalesQuestParams.parseDouble(null, 3.0D), 0.0D);

        assertEquals(Double.valueOf(1.5D), LostTalesQuestParams.parseNumber(" 1.5 "));
        assertNull(LostTalesQuestParams.parseNumber("far"));
        assertNull(LostTalesQuestParams.parseNumber(""));
        assertNull(LostTalesQuestParams.parseNumber(null));
    }

    @Test
    public void aLocationIsXYAndZInTheDimensionItNames() {
        LostTalesQuestParams.Location location = LostTalesQuestParams.location(
                params("x", " 12 ", "y", "70", "z", "-4.5", "dimension", "-1"), 0);

        assertEquals(-1, location.getDimensionId());
        assertEquals(12.0D, location.getX(), 0.0D);
        assertEquals(70.0D, location.getY(), 0.0D);
        assertEquals(-4.5D, location.getZ(), 0.0D);
        assertEquals(1, LostTalesQuestParams.location(
                params("x", "1", "y", "2", "z", "3", "dimension", "the_end"),
                0).getDimensionId());
    }

    @Test
    public void aLocationWithoutADimensionIsWhereThePlayerStands() {
        assertEquals(100, LostTalesQuestParams.location(
                params("x", "1", "y", "2", "z", "3"), 100).getDimensionId());
    }

    @Test
    public void aLocationNeedsAllThreeCoordinatesUnderTheirOwnNames() {
        assertNull(LostTalesQuestParams.location(null, 0));
        assertNull(LostTalesQuestParams.location(params("x", "1", "z", "2"), 0));
        assertNull(LostTalesQuestParams.location(
                params("x", "  ", "y", "1", "z", "1"), 0));
        assertNull(LostTalesQuestParams.location(
                params("x", "1", "y", "high", "z", "2"), 0));
        assertNull(LostTalesQuestParams.location(
                params("posX", "1", "targetY", "70", "posZ", "3"), 0));
    }

    /** A marker or any coordinate names a place; a radius, a dimension or a blank value does not (QUEST-40). */
    @Test
    public void aPlaceIsAMarkerOrACoordinate() {
        assertTrue(LostTalesQuestParams.namesPlace(params("marker", "losttales:camp")));
        assertTrue(LostTalesQuestParams.namesPlace(params("x", "1", "y", "2", "z", "3")));
        assertTrue("an incomplete place is still one",
                LostTalesQuestParams.namesPlace(params("z", "3")));
        assertFalse(LostTalesQuestParams.namesPlace(params("radius", "16", "dimension", "-1")));
        assertFalse(LostTalesQuestParams.namesPlace(params("x", " ", "marker", "")));
        assertFalse(LostTalesQuestParams.namesPlace(null));
    }

    /** A point is within a place's radius only in the place's own dimension, and never for no radius. */
    @Test
    public void aPointIsWithinARadiusOfAPlaceInItsDimension() {
        LostTalesQuestParams.Location camp = new LostTalesQuestParams.Location(0, 100.0D,
                64.0D, -300.0D);
        assertTrue(LostTalesQuestParams.isWithin(camp, 0, 110.0D, 64.0D, -300.0D, 10.0D));
        assertTrue(LostTalesQuestParams.isWithin(camp, 0, 106.0D, 72.0D, -300.0D, 10.0D));
        assertFalse("three dimensions", LostTalesQuestParams.isWithin(camp, 0, 106.0D,
                73.0D, -300.0D, 10.0D));
        assertFalse("another dimension", LostTalesQuestParams.isWithin(camp, -1, 100.0D,
                64.0D, -300.0D, 10.0D));
        assertFalse("no radius", LostTalesQuestParams.isWithin(camp, 0, 100.0D, 64.0D,
                -300.0D, 0.0D));
        assertFalse("no place", LostTalesQuestParams.isWithin(null, 0, 100.0D, 64.0D,
                -300.0D, 10.0D));
    }

    private static Map<String, String> params(String... keysAndValues) {
        Map<String, String> params = new HashMap<String, String>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            params.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return params;
    }
}
