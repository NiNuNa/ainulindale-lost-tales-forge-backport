package com.ninuna.losttales.block.vessel;

import java.util.ArrayList;
import java.util.List;

/**
 * What a vessel item's tooltip shows of what it holds (CONTENT-09): the
 * vessel's slots side by side, each item drawn in the slot it lies in
 * with its count, as a shulker box's tooltip shows them; and, wherever
 * pictures cannot be drawn in a tooltip, the same items as lines of
 * words, each item's name and count. Both are bounded, and both end on a
 * line saying how many more items there are rather than leave any out
 * unsaid.
 */
public final class VesselTooltip {
    /** The most slots standing side by side in one row. */
    public static final int COLUMNS = 4;
    /** The most rows of slots. */
    public static final int MAX_ROWS = 3;
    /** The most slots shown; the items of any past them are counted under the slots. */
    public static final int MAX_SLOTS = COLUMNS * MAX_ROWS;
    /** The most items listed by name. */
    public static final int MAX_LINES = 5;

    /** The words of the lines, in the reader's language. */
    public interface Words {
        /** One item's line: its name and how many there are. */
        String item(String name, int count);

        /** The line under the list: how many more items it holds. */
        String more(int count);
    }

    /** One item a vessel holds: its name as the reader reads it, and how many. */
    public static final class Entry {
        public final String name;
        public final int count;

        public Entry(String name, int count) {
            this.name = name == null ? "" : name;
            this.count = count;
        }
    }

    /**
     * Where the shown slots stand: in slot order, rows of at most
     * {@link #COLUMNS} from the top left, each slot {@code size} square
     * and {@code gap} clear of the next across and down. A short last
     * row starts at the left, as an inventory's rows do.
     */
    public static final class Grid {
        public final int slots;
        public final int columns;
        public final int rows;
        public final int size;
        public final int gap;
        public final int width;
        public final int height;

        Grid(int slots, int size, int gap) {
            this.slots = slots;
            this.columns = Math.min(slots, COLUMNS);
            this.rows = slots == 0 ? 0 : (slots + COLUMNS - 1) / COLUMNS;
            this.size = size;
            this.gap = gap;
            this.width = span(this.columns, size, gap);
            this.height = span(this.rows, size, gap);
        }

        /** The left of {@code slot}, from the grid's left. */
        public int slotX(int slot) {
            return slot % COLUMNS * (this.size + this.gap);
        }

        /** The top of {@code slot}, from the grid's top. */
        public int slotY(int slot) {
            return slot / COLUMNS * (this.size + this.gap);
        }

        private static int span(int count, int size, int gap) {
            return count <= 0 ? 0 : count * size + (count - 1) * gap;
        }
    }

    private VesselTooltip() {}

    /** How many of a vessel's {@code capacity} slots are shown: all of them, at most {@link #MAX_SLOTS}. */
    public static int shownSlots(int capacity) {
        return Math.max(0, Math.min(capacity, MAX_SLOTS));
    }

    /**
     * How many items lie past the {@code shown} slots, from how many
     * each slot holds in slot order (0 for an empty one): what the line
     * under the slots counts.
     */
    public static int hiddenItems(int[] counts, int shown) {
        int hidden = 0;
        if (counts == null) {
            return hidden;
        }
        for (int slot = Math.max(0, shown); slot < counts.length; slot++) {
            if (counts[slot] > 0) {
                hidden++;
            }
        }
        return hidden;
    }

    /** The grid of {@code slots} slots, each {@code size} square and {@code gap} apart. */
    public static Grid grid(int slots, int size, int gap) {
        return new Grid(Math.max(0, Math.min(slots, MAX_SLOTS)),
                Math.max(0, size), Math.max(0, gap));
    }

    /**
     * The items as lines of words, in slot order: at most
     * {@link #MAX_LINES} of them, the last saying how many more there are
     * where they do not all fit.
     */
    public static List<String> lines(List<Entry> entries, Words words) {
        List<String> lines = new ArrayList<String>();
        if (entries == null || words == null) {
            return lines;
        }
        int shown = entries.size() > MAX_LINES ? MAX_LINES - 1 : entries.size();
        for (int index = 0; index < shown; index++) {
            Entry entry = entries.get(index);
            lines.add(words.item(entry.name, Math.max(1, entry.count)));
        }
        if (shown < entries.size()) {
            lines.add(words.more(entries.size() - shown));
        }
        return lines;
    }
}
