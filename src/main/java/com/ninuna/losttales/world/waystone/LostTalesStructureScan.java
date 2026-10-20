package com.ninuna.losttales.world.waystone;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A LOTR structure scan as LOTR's {@code /strscan scan} writes it: one
 * line per block, {@code x.y.z."block".meta}, each place from the scan's
 * origin and each vanilla block named without {@code minecraft:} (WAY-17).
 * A waystone house takes plain blocks only: a line that names one of
 * LOTR's block aliases ({@code #alias#}, {@code ~alias~}), or marks a block
 * to fill down ({@code v}) or to find the lowest ground ({@code _}), is
 * said, and so is every line of another shape.
 */
public final class LostTalesStructureScan {
    /** The most characters a scan may hold. */
    public static final int MAX_CHARACTERS = 128 * 1024;
    /** The most blocks a scan may lay. */
    public static final int MAX_BLOCKS = 6144;
    private static final Pattern STEP = Pattern.compile(
            "(-?\\d{1,4})\\.(-?\\d{1,4})([v_]?)\\.(-?\\d{1,4})\\.\"([^\"]+)\"\\.(\\d{1,2})");

    private LostTalesStructureScan() {}

    /** One block of a scan: where it stands from the origin, its name and metadata. */
    public static final class Block {
        public final int x;
        public final int y;
        public final int z;
        public final String name;
        public final int metadata;

        public Block(int x, int y, int z, String name, int metadata) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
            this.metadata = metadata;
        }
    }

    /**
     * The blocks {@code reader} holds, in order. Each problem goes to
     * {@code said}, prefixed with {@code place}; a scan with anything said
     * makes no house.
     */
    public static List<Block> read(Reader reader, String place, List<String> said) {
        List<Block> blocks = new ArrayList<Block>();
        if (reader == null) {
            said.add(place + " cannot be read");
            return blocks;
        }
        BufferedReader lines = new BufferedReader(reader);
        int characters = 0;
        int number = 0;
        try {
            String line;
            while ((line = lines.readLine()) != null) {
                number++;
                characters += line.length() + 1;
                if (characters > MAX_CHARACTERS) {
                    said.add(place + " is longer than " + MAX_CHARACTERS + " characters");
                    return Collections.emptyList();
                }
                String text = line.trim();
                if (text.length() == 0) {
                    continue;
                }
                if (text.startsWith("#") || text.startsWith("~")
                        || text.indexOf('#') >= 0 || text.indexOf('~') >= 0) {
                    said.add(place + " line " + number + " uses one of LOTR's block aliases,"
                            + " which a waystone house cannot fill; scan plain blocks");
                    continue;
                }
                Matcher step = STEP.matcher(text);
                if (!step.matches()) {
                    said.add(place + " line " + number + " is not x.y.z.\"block\".meta");
                    continue;
                }
                if (step.group(3).length() > 0) {
                    said.add(place + " line " + number + " marks a block to fill down or to find"
                            + " the lowest ground; the house's foundation does that");
                    continue;
                }
                int metadata = Integer.parseInt(step.group(6));
                if (metadata > 15) {
                    said.add(place + " line " + number + " gives metadata " + metadata
                            + "; it is 0 to 15");
                    continue;
                }
                if (blocks.size() >= MAX_BLOCKS) {
                    said.add(place + " lays more than " + MAX_BLOCKS + " blocks");
                    return Collections.emptyList();
                }
                blocks.add(new Block(Integer.parseInt(step.group(1)),
                        Integer.parseInt(step.group(2)), Integer.parseInt(step.group(4)),
                        step.group(5), metadata));
            }
        } catch (IOException unreadable) {
            said.add(place + " cannot be read: " + unreadable.getMessage());
        }
        return blocks;
    }
}
