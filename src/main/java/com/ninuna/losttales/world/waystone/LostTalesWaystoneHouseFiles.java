package com.ninuna.losttales.world.waystone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.data.JsonReading;
import com.ninuna.losttales.util.LostTalesCloseables;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the waystone houses (WAY-17): each a rules file,
 * {@code <name>.json}, with LOTR's structure scan beside it,
 * {@code <name>.strscan}. The bundled ones lie under
 * {@code assets/losttales/structures/}, listed in its index; a server's
 * own in {@code config/losttales/structures/}, one taking a bundled one's
 * place by its name. A house's id is {@code losttales:} and its name. A
 * file with anything said is left out.
 */
public final class LostTalesWaystoneHouseFiles {
    public static final String INDEX = "structures/index.json";
    public static final String LIST_KEY = "houses";
    public static final String FOLDER = "structures";
    public static final String SCAN_SUFFIX = ".strscan";
    public static final int MAX_CLEARANCE = 24;
    public static final int MAX_FOUNDATION = 16;
    /** The widest and deepest a house may be, in blocks. */
    public static final int MAX_SPAN = 16;
    /** How far from the waystone a traveller may arrive, each way. */
    public static final int MAX_ARRIVAL = 16;
    static final Set<String> KEYS = JsonReading.keys(
            "waystone", "arrival", "clearance", "foundation", "foundationBlock");

    private LostTalesWaystoneHouseFiles() {}

    /** Opens the scan beside a rules file; null where there is none. */
    public interface Scans {
        Reader open(String rulesFile) throws IOException;
    }

    /** Whether the game has a block of that name. */
    public interface BlockNames {
        boolean has(String name);
    }

    public static DataFiles.Kind<LostTalesWaystoneHouse> kind(final Scans scans,
                                                              final BlockNames blocks) {
        return new DataFiles.Kind<LostTalesWaystoneHouse>() {
            @Override
            public LostTalesWaystoneHouse parse(Reader reader, String file, List<String> said) {
                return LostTalesWaystoneHouseFiles.parse(reader, file, said, scans);
            }

            @Override
            public String idOf(LostTalesWaystoneHouse house) {
                return house.getId();
            }

            @Override
            public List<String> warnings(LostTalesWaystoneHouse house) {
                return warningsOf(house, blocks);
            }
        };
    }

    /** The scans of bundled houses, read from where their rules are. */
    public static Scans bundledScans(final DataFiles.Source source) {
        return new Scans() {
            @Override
            public Reader open(String rulesFile) throws IOException {
                return source.open(scanFile(rulesFile));
            }
        };
    }

    /** The scans beside a server's rules files, each at most {@link LostTalesStructureScan#MAX_CHARACTERS} bytes. */
    public static Scans folderScans(final File folder) {
        return new Scans() {
            @Override
            public Reader open(String rulesFile) throws IOException {
                File scan = new File(folder, scanFile(rulesFile));
                if (!scan.isFile()) {
                    return null;
                }
                if (scan.length() > LostTalesStructureScan.MAX_CHARACTERS) {
                    throw new IOException(scan.getName() + " is larger than "
                            + LostTalesStructureScan.MAX_CHARACTERS + " bytes");
                }
                return new InputStreamReader(new FileInputStream(scan), StandardCharsets.UTF_8);
            }
        };
    }

    /** {@code structures/glowstone_house.json} is {@code losttales:glowstone_house}. */
    public static String idOf(String rulesFile) {
        return LostTalesMetaData.MOD_ID + ":" + baseName(rulesFile);
    }

    /** The scan's file beside a rules file: the same path, {@code .strscan} for {@code .json}. */
    static String scanFile(String rulesFile) {
        String path = rulesFile == null ? "" : rulesFile.trim();
        if (path.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return path + SCAN_SUFFIX;
    }

    /**
     * The ids of the houses the bundled index lists, as a marker file is
     * checked against them; none where the index does not read.
     */
    public static Set<String> bundledIds(DataFiles.Source source) {
        Set<String> ids = new LinkedHashSet<String>();
        Reader reader = null;
        try {
            reader = source.open(INDEX);
            JsonElement root = reader == null ? null : new JsonParser().parse(reader);
            JsonElement list = root != null && root.isJsonObject()
                    ? root.getAsJsonObject().get(LIST_KEY) : null;
            if (list != null && list.isJsonArray()) {
                JsonArray files = list.getAsJsonArray();
                for (JsonElement entry : files) {
                    if (entry != null && entry.isJsonPrimitive()
                            && entry.getAsJsonPrimitive().isString()) {
                        ids.add(idOf(entry.getAsString()));
                    }
                }
            }
        } catch (IOException unreadable) {
            return Collections.unmodifiableSet(ids);
        } catch (RuntimeException malformed) {
            return Collections.unmodifiableSet(ids);
        } finally {
            LostTalesCloseables.closeQuietly(reader);
        }
        return Collections.unmodifiableSet(ids);
    }

    static LostTalesWaystoneHouse parse(Reader reader, String file, List<String> said,
                                        Scans scans) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        String id = idOf(file);
        JsonReading where = new JsonReading(said, "house " + id);
        where.unknownKeys(object, KEYS);
        for (String key : KEYS) {
            if (!object.has(key)) {
                where.say("writes no '" + key + "'");
            }
        }
        int[] waystone = place(where, object, "waystone");
        int[] arrival = place(where, object, "arrival");
        int clearance = where.whole(object, "clearance", 0);
        int foundation = where.whole(object, "foundation", 0);
        String foundationBlock = where.string(object, "foundationBlock", "");
        String scanName = baseName(file) + SCAN_SUFFIX;
        List<LostTalesStructureScan.Block> blocks =
                new ArrayList<LostTalesStructureScan.Block>();
        Reader scan = null;
        try {
            scan = scans == null ? null : scans.open(file);
            if (scan == null) {
                where.say("has no scan beside it; LOTR's /strscan writes one, " + scanName);
            } else {
                blocks = LostTalesStructureScan.read(scan, "the scan " + scanName, said);
            }
        } catch (IOException unreadable) {
            where.say("cannot read its scan " + scanName + ": " + unreadable.getMessage());
        } finally {
            LostTalesCloseables.closeQuietly(scan);
        }
        return new LostTalesWaystoneHouse(id, blocks, waystone, arrival, clearance,
                foundation, foundationBlock);
    }

    /** Three whole numbers, x, y and z; anything else is said. */
    private static int[] place(JsonReading where, JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null) {
            return new int[3];
        }
        if (!element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            where.say("writes '" + key + "' as something other than three whole numbers, x, y and z");
            return new int[3];
        }
        int[] values = where.wholes(object, key);
        return values.length == 3 ? values : new int[3];
    }

    static List<String> warningsOf(LostTalesWaystoneHouse house, BlockNames blocks) {
        List<String> warnings = new ArrayList<String>();
        if (!house.getId().matches("[a-z0-9_]+:[a-z0-9_]{1,64}")) {
            warnings.add("its file name is not lower-case letters, digits and _, within 64");
        }
        if (house.getClearance() < 1 || house.getClearance() > MAX_CLEARANCE) {
            warnings.add("clears " + house.getClearance() + " layers; it is 1 to " + MAX_CLEARANCE);
        }
        if (house.getFoundation() < 0 || house.getFoundation() > MAX_FOUNDATION) {
            warnings.add("fills a foundation " + house.getFoundation() + " deep; it is 0 to "
                    + MAX_FOUNDATION);
        }
        String foundationBlock = house.getFoundationBlock();
        if (foundationBlock.indexOf(':') <= 0 || !has(blocks, foundationBlock)) {
            warnings.add("fills its foundation with '" + foundationBlock
                    + "', which is no block the game has by that name");
        }
        if (house.getBlocks().isEmpty()) {
            warnings.add("its scan lays no block");
            return warnings;
        }
        int width = house.getMaxX() - house.getMinX() + 1;
        int depth = house.getMaxZ() - house.getMinZ() + 1;
        if (width > MAX_SPAN || depth > MAX_SPAN) {
            warnings.add("is " + width + " by " + depth + " blocks; a house is at most "
                    + MAX_SPAN + " by " + MAX_SPAN);
        }
        if (house.getMinY() < 0) {
            warnings.add("its scan lays blocks below its origin; set the origin on the"
                    + " house's lowest layer");
        }
        if (house.getMaxY() > house.getClearance()) {
            warnings.add("its scan stands " + (house.getMaxY() + 1) + " layers high, above the "
                    + (house.getClearance() + 1) + " its clearance clears");
        }
        int[] waystone = house.getWaystone();
        if (waystone[0] < house.getMinX() || waystone[0] > house.getMaxX()
                || waystone[2] < house.getMinZ() || waystone[2] > house.getMaxZ()
                || waystone[1] < 0 || waystone[1] + 1 > house.getClearance()) {
            warnings.add("the waystone at " + waystone[0] + ", " + waystone[1] + ", "
                    + waystone[2] + " stands outside the house");
        } else if (house.laysBlockAt(waystone[0], waystone[1], waystone[2])
                || house.laysBlockAt(waystone[0], waystone[1] + 1, waystone[2])) {
            warnings.add("its scan lays a block where the waystone stands");
        }
        int[] arrival = house.getArrival();
        if (Math.abs(arrival[0]) > MAX_ARRIVAL || Math.abs(arrival[1]) > MAX_ARRIVAL
                || Math.abs(arrival[2]) > MAX_ARRIVAL) {
            warnings.add("a traveller arrives more than " + MAX_ARRIVAL
                    + " blocks from the waystone");
        } else if (arrival[0] == 0 && arrival[2] == 0
                && (arrival[1] == 0 || arrival[1] == 1)) {
            warnings.add("a traveller arrives in the waystone itself");
        }
        Set<String> unknown = new LinkedHashSet<String>();
        for (LostTalesStructureScan.Block block : house.getBlocks()) {
            if (!has(blocks, block.name)) {
                unknown.add(block.name);
            }
        }
        for (String name : unknown) {
            warnings.add("its scan lays '" + name + "', which is no block the game has");
        }
        return warnings;
    }

    private static boolean has(BlockNames blocks, String name) {
        return name != null && name.length() > 0 && !name.matches("[0-9]+")
                && (blocks == null || blocks.has(name));
    }

    private static String baseName(String file) {
        String path = file == null ? "" : file.trim().replace('\\', '/');
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        if (path.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return path;
    }
}
