package com.ninuna.losttales.block.vessel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** A vessel item's tooltip lists what it holds, names and counts, bounded (CONTENT-09). */
public final class VesselTooltipTest {
    private static final VesselTooltip.Words WORDS = new VesselTooltip.Words() {
        @Override
        public String item(String name, int count) {
            return name + " x" + count;
        }

        @Override
        public String more(int count) {
            return "and " + count + " more";
        }
    };

    @Test
    public void eachItemByNameAndCount() {
        assertEquals(Arrays.asList("Bread x12", "Iron Sword x1"), VesselTooltip.lines(
                Arrays.asList(new VesselTooltip.Entry("Bread", 12),
                        new VesselTooltip.Entry("Iron Sword", 1)), WORDS));
    }

    @Test
    public void aLongListEndsInHowManyMore() {
        List<VesselTooltip.Entry> entries = new ArrayList<VesselTooltip.Entry>();
        for (int index = 1; index <= 8; index++) {
            entries.add(new VesselTooltip.Entry("Item " + index, index));
        }
        List<String> lines = VesselTooltip.lines(entries, WORDS);

        assertEquals(VesselTooltip.MAX_LINES, lines.size());
        assertEquals("Item 4 x4", lines.get(3));
        assertEquals("and 4 more", lines.get(4));
    }

    @Test
    public void exactlyFiveItemsShowAllFive() {
        List<VesselTooltip.Entry> entries = new ArrayList<VesselTooltip.Entry>();
        for (int index = 1; index <= VesselTooltip.MAX_LINES; index++) {
            entries.add(new VesselTooltip.Entry("Item " + index, 1));
        }
        assertEquals("Item 5 x1", VesselTooltip.lines(entries, WORDS).get(4));
    }

    @Test
    public void nothingListsNothing() {
        assertEquals(0, VesselTooltip.lines(new ArrayList<VesselTooltip.Entry>(), WORDS).size());
        assertEquals(0, VesselTooltip.lines(null, WORDS).size());
    }
}
