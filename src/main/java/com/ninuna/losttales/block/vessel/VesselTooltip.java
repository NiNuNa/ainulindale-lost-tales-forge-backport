package com.ninuna.losttales.block.vessel;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines a vessel item's tooltip lists what it holds in (CONTENT-09),
 * as a bundle's does: each item's name and count, in slot order, at most
 * {@link #MAX_LINES} of them, and a last line saying how many more.
 */
public final class VesselTooltip {
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

    private VesselTooltip() {}

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
