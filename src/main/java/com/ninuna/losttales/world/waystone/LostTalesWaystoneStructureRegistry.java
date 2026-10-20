package com.ninuna.losttales.world.waystone;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.util.LostTalesLog;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The waystone houses the server builds (WAY-17): the bundled ones, read
 * as they are first asked for, and a server's own from
 * {@code config/losttales/structures/}, read as the server starts and on
 * {@code /losttales mapmarker reseed}, each in place of a bundled one of
 * its id. A house whose file was left out builds nothing; a marker naming
 * it is refused before it loads. The server's own are forgotten as the
 * server stops.
 */
public final class LostTalesWaystoneStructureRegistry {
    /** The bundled files as the server reads them: from the mod's own classpath. */
    public static final DataFiles.Source CLASSPATH = new DataFiles.Source() {
        @Override
        public Reader open(String path) {
            InputStream stream = LostTalesWaystoneStructureRegistry.class.getClassLoader()
                    .getResourceAsStream("assets/" + LostTalesMetaData.MOD_ID + "/" + path);
            return stream == null ? null
                    : new InputStreamReader(stream, StandardCharsets.UTF_8);
        }
    };
    /** Whether the game has a block of a name, as the houses' files name blocks. */
    static final LostTalesWaystoneHouseFiles.BlockNames GAME_BLOCKS =
            new LostTalesWaystoneHouseFiles.BlockNames() {
                @Override
                public boolean has(String name) {
                    return LostTalesWaystoneHousePlacer.hasBlock(name);
                }
            };

    private static final Map<String, LostTalesWaystoneHousePlacer> BUNDLED =
            new LinkedHashMap<String, LostTalesWaystoneHousePlacer>();
    private static final Map<String, LostTalesWaystoneHousePlacer> PLACERS =
            new LinkedHashMap<String, LostTalesWaystoneHousePlacer>();
    private static boolean initialized;

    private LostTalesWaystoneStructureRegistry() {}

    /** Reads the bundled houses once; every house a file left out is logged. */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        DataFiles.Result<LostTalesWaystoneHouse> bundled = DataFiles.readBundled(
                CLASSPATH, LostTalesWaystoneHouseFiles.INDEX,
                LostTalesWaystoneHouseFiles.LIST_KEY,
                LostTalesWaystoneHouseFiles.kind(
                        LostTalesWaystoneHouseFiles.bundledScans(CLASSPATH), GAME_BLOCKS));
        log("Bundled waystone house", bundled.problems);
        BUNDLED.clear();
        BUNDLED.putAll(placers(bundled.values, new ArrayList<String>()));
        PLACERS.clear();
        PLACERS.putAll(BUNDLED);
    }

    /**
     * Reads the server's own houses from {@code folder} again, each in
     * place of a bundled one of its id or beside them; answers why each
     * file left out was left out.
     */
    public static synchronized List<String> loadServerHouses(File folder) {
        initialize();
        List<String> problems = new ArrayList<String>();
        if (folder == null) {
            return problems;
        }
        DataFiles.Result<LostTalesWaystoneHouse> server = DataFiles.readFolder(folder,
                LostTalesWaystoneHouseFiles.kind(
                        LostTalesWaystoneHouseFiles.folderScans(folder), GAME_BLOCKS));
        List<String> said = new ArrayList<String>(server.problems);
        Map<String, LostTalesWaystoneHousePlacer> own = placers(server.values, said);
        log("Server waystone house", said);
        for (String problem : said) {
            problems.add("Server waystone house: " + problem);
        }
        PLACERS.clear();
        PLACERS.putAll(BUNDLED);
        PLACERS.putAll(own);
        return problems;
    }

    /** Forgets the server's own houses; the bundled ones stay. */
    public static synchronized void clearServerHouses() {
        PLACERS.clear();
        PLACERS.putAll(BUNDLED);
    }

    public static synchronized LostTalesWaystoneStructurePlacer get(String id) {
        initialize();
        return id == null ? null : PLACERS.get(id.trim());
    }

    /** The ids of every house that loaded, bundled and the server's own. */
    public static synchronized Set<String> houseIds() {
        initialize();
        return Collections.unmodifiableSet(new LinkedHashSet<String>(PLACERS.keySet()));
    }

    /** Where a traveller arrives at a house's waystone, from its lower half; null for no house of that id. */
    public static synchronized int[] arrival(String id) {
        initialize();
        LostTalesWaystoneHousePlacer placer = id == null ? null : PLACERS.get(id.trim());
        return placer == null ? null : placer.getArrival();
    }

    private static Map<String, LostTalesWaystoneHousePlacer> placers(
            List<LostTalesWaystoneHouse> houses, List<String> problems) {
        Map<String, LostTalesWaystoneHousePlacer> placers =
                new LinkedHashMap<String, LostTalesWaystoneHousePlacer>();
        for (LostTalesWaystoneHouse house : houses) {
            try {
                placers.put(house.getId(), new LostTalesWaystoneHousePlacer(house));
            } catch (RuntimeException unbuildable) {
                problems.add(house.getId() + ": " + unbuildable.getMessage());
            }
        }
        return placers;
    }

    private static void log(String kind, List<String> problems) {
        for (String problem : problems) {
            LostTalesLog.warning("%s left out: %s", kind, problem);
        }
    }
}
