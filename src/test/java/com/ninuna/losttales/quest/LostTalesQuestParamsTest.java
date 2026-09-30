package com.ninuna.losttales.quest;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

    private static Map<String, String> params(String... keysAndValues) {
        Map<String, String> params = new HashMap<String, String>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            params.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return params;
    }
}
