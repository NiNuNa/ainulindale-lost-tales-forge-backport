package com.ninuna.losttales.block.vessel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A vessel item's tooltip shows its slots, bounded, and counts any items
 * past them; without pictures it lists names and counts, bounded too
 * (CONTENT-09).
 */
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

    @Test
    public void everyVesselShowsItsWholeCapacity() {
        // The amphora and the urn hold two stacks, the loutrophoros four.
        assertEquals(2, VesselTooltip.shownSlots(2));
        assertEquals(4, VesselTooltip.shownSlots(4));
        assertEquals(VesselTooltip.MAX_SLOTS, VesselTooltip.shownSlots(VesselTooltip.MAX_SLOTS));
    }

    @Test
    public void slotsPastTheMostShownAreLeftToTheLineUnderThem() {
        assertEquals(VesselTooltip.MAX_SLOTS, VesselTooltip.shownSlots(64));
        assertEquals(0, VesselTooltip.shownSlots(0));
        assertEquals(0, VesselTooltip.shownSlots(-3));
    }

    @Test
    public void hiddenItemsCountTheFilledSlotsPastTheShownOnes() {
        int[] counts = new int[VesselTooltip.MAX_SLOTS + 5];
        counts[0] = 12;
        counts[VesselTooltip.MAX_SLOTS - 1] = 1;
        counts[VesselTooltip.MAX_SLOTS] = 3;
        counts[VesselTooltip.MAX_SLOTS + 4] = 64;
        int shown = VesselTooltip.shownSlots(counts.length);

        assertEquals(2, VesselTooltip.hiddenItems(counts, shown));
    }

    @Test
    public void nothingIsHiddenWhereEverySlotShows() {
        assertEquals(0, VesselTooltip.hiddenItems(new int[] {5, 0, 1, 1},
                VesselTooltip.shownSlots(4)));
        assertEquals(0, VesselTooltip.hiddenItems(null, 0));
    }

    @Test
    public void oneRowHoldsTheBiggestVessel() {
        VesselTooltip.Grid grid = VesselTooltip.grid(4, 24, 5);

        assertEquals(4, grid.columns);
        assertEquals(1, grid.rows);
        assertEquals(4 * 24 + 3 * 5, grid.width);
        assertEquals(24, grid.height);
        assertEquals(0, grid.slotX(0));
        assertEquals(3 * (24 + 5), grid.slotX(3));
        assertEquals(0, grid.slotY(3));
    }

    @Test
    public void twoSlotsStandSideBySide() {
        VesselTooltip.Grid grid = VesselTooltip.grid(2, 24, 5);

        assertEquals(2 * 24 + 5, grid.width);
        assertEquals(24, grid.height);
        assertEquals(24 + 5, grid.slotX(1));
    }

    @Test
    public void moreSlotsWrapIntoRowsStartingAtTheLeft() {
        VesselTooltip.Grid grid = VesselTooltip.grid(VesselTooltip.COLUMNS + 2, 24, 5);

        assertEquals(VesselTooltip.COLUMNS, grid.columns);
        assertEquals(2, grid.rows);
        assertEquals(2 * 24 + 5, grid.height);
        assertEquals(0, grid.slotX(VesselTooltip.COLUMNS));
        assertEquals(24 + 5, grid.slotY(VesselTooltip.COLUMNS));
        assertEquals(24 + 5, grid.slotX(VesselTooltip.COLUMNS + 1));
    }

    @Test
    public void theGridNeverGrowsPastTheMostRows() {
        VesselTooltip.Grid grid = VesselTooltip.grid(1000, 24, 5);

        assertEquals(VesselTooltip.MAX_SLOTS, grid.slots);
        assertEquals(VesselTooltip.MAX_ROWS, grid.rows);
        assertEquals(VesselTooltip.MAX_ROWS * 24 + (VesselTooltip.MAX_ROWS - 1) * 5,
                grid.height);
    }

    @Test
    public void noSlotsTakeNoRoom() {
        VesselTooltip.Grid grid = VesselTooltip.grid(0, 24, 5);

        assertEquals(0, grid.width);
        assertEquals(0, grid.height);
        assertEquals(0, grid.rows);
    }
}
