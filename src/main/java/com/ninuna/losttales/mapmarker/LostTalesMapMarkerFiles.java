package com.ninuna.losttales.mapmarker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.data.JsonReading;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneHousePlacer;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneStructureRegistry;
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
import lotr.common.LOTRDimension;

/**
 * Reads and checks the map marker files (MOD-17): the bundled ones under
 * {@code assets/losttales/map_markers/}, listed in its index, and a
 * server's own in {@code config/losttales/map_markers/}. Each file holds a
 * {@code map_markers} list. A file with anything said is left out whole,
 * and so is a file repeating a marker id an earlier file of its side has.
 *
 * <p>A bundled Lost Tales marker's words come from the lang file under
 * its waypoint's code ({@link LostTalesMapMarkerNames}), so its file
 * writes none; a bundled LOTR waypoint's file names it in English, as
 * LOTR's own lang file does. A server's marker writes its name, and its
 * description where it has one, and every game shows them as written.</p>
 */
public final class LostTalesMapMarkerFiles {
    public static final String INDEX = "map_markers/index.json";
    public static final String LIST_KEY = "files";
    /** A server's own marker files, under Forge's config directory. */
    public static final String FOLDER = LostTalesMetaData.MOD_ID + "/map_markers";
    /** A server's own waystone houses, under Forge's config directory (WAY-17). */
    public static final String HOUSES_FOLDER = LostTalesMetaData.MOD_ID + "/structures";
    static final String MARKERS = "map_markers";
    /** The most markers one file holds. */
    public static final int MAX_MARKERS = 1024;
    public static final int MAX_ID_LENGTH = 128;
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 512;
    public static final int MAX_CATEGORY_LENGTH = 64;
    public static final double MAX_COORDINATE = 30000000.0D;
    public static final double MAX_RADIUS = 1000000.0D;
    private static final String LOTR_WAYPOINT = "lotr:waypoint:";
    private static final String MIDDLE_EARTH = "lotr:middle_earth";
    private static final String PLAYER_WAYSTONES = LostTalesMetaData.MOD_ID + ":player/";
    static final Set<String> FILE_KEYS = JsonReading.keys(MARKERS);
    static final Set<String> KEYS = JsonReading.keys("id", "name", "description", "category",
            "icon", "color", "dimension", "x", "y", "z", "compassFadeInRadius",
            "discoveryRadius", "isDiscoverable", "hiddenUntilDiscovered",
            "requiresRegionUnlock", "hasFastTravel", "hasWaystone", "waystoneStructureType",
            "relevance");
    /** The keys every marker writes; no default stands in for one. */
    static final String[] REQUIRED = {"id", "relevance", "icon", "color", "dimension", "x", "z",
            "compassFadeInRadius", "discoveryRadius", "isDiscoverable",
            "hiddenUntilDiscovered", "requiresRegionUnlock", "hasFastTravel", "hasWaystone"};
    /** The worlds a marker stands in, by the one name each is written with ({@link #dimension}). */
    private static final String WORLDS = "lotr:middle_earth, lotr:utumno, minecraft:overworld,"
            + " minecraft:the_nether or minecraft:the_end";
    /** The colours a marker is drawn in by name; any other is {@code #rrggbb}. */
    static final Set<String> COLORS = JsonReading.keys("white", "red", "green", "blue",
            "yellow", "gold", "orange", "purple", "violet", "gray", "dark_gray", "black");

    private static File configDirectory;

    private LostTalesMapMarkerFiles() {}

    /** Forge's config directory; set once as the mod loads. */
    public static synchronized void configure(File modConfigurationDirectory) {
        configDirectory = modConfigurationDirectory;
    }

    public static synchronized File folder(String name) {
        return configDirectory == null ? null : new File(configDirectory, name);
    }

    /**
     * Reads a server's own waystone houses, then its own marker files,
     * each marker checked against every house that loaded; answers why
     * each file left out was left out. Called as the server starts.
     */
    public static List<String> loadServer() {
        List<String> problems = new ArrayList<String>(
                LostTalesWaystoneStructureRegistry.loadServerHouses(folder(HOUSES_FOLDER)));
        problems.addAll(LostTalesMapMarkerCatalog.loadServerFiles(folder(FOLDER),
                LostTalesWaystoneStructureRegistry.houseIds()));
        return problems;
    }

    /**
     * Reads the marker files again, bundled and the server's own, and the
     * server's own waystone houses, each marker checked against every house
     * that loaded, and puts houses and markers in place together once all
     * are read: a read that fails leaves them as they were. Answers why each
     * file left out was left out. Called on {@code /losttales reload
     * markers} and {@code mapmarker reseed}.
     */
    public static List<String> reload() {
        List<String> problems = new ArrayList<String>();
        Map<String, LostTalesWaystoneHousePlacer> houses =
                LostTalesWaystoneStructureRegistry.readServerHouses(folder(HOUSES_FOLDER), problems);
        LostTalesMapMarkerCatalog.Read markers = LostTalesMapMarkerCatalog.read(folder(FOLDER),
                LostTalesWaystoneStructureRegistry.houseIdsWith(houses));
        problems.addAll(markers.getProblems());
        LostTalesWaystoneStructureRegistry.putServerHouses(houses);
        LostTalesMapMarkerCatalog.put(markers);
        return problems;
    }

    /** Forgets a server's own markers and houses as it stops. */
    public static void clearServer() {
        LostTalesMapMarkerCatalog.clearServerFiles();
        LostTalesWaystoneStructureRegistry.clearServerHouses();
    }

    /** The bundled files as the server reads them: from the mod's own classpath. */
    public static final DataFiles.Source CLASSPATH = new DataFiles.Source() {
        @Override
        public Reader open(String path) {
            InputStream stream = LostTalesMapMarkerFiles.class.getClassLoader()
                    .getResourceAsStream("assets/" + LostTalesMetaData.MOD_ID + "/" + path);
            return stream == null ? null
                    : new InputStreamReader(stream, StandardCharsets.UTF_8);
        }
    };

    /** One marker file: its name and its markers, in order. */
    public static final class MarkerFile {
        private final String name;
        private final List<LostTalesMapMarkerDefinition> markers;

        MarkerFile(String name, List<LostTalesMapMarkerDefinition> markers) {
            this.name = name;
            this.markers = Collections.unmodifiableList(
                    new ArrayList<LostTalesMapMarkerDefinition>(markers));
        }

        public String getName() { return this.name; }
        public List<LostTalesMapMarkerDefinition> getMarkers() { return this.markers; }
    }

    /** The bundled files, checked against the houses the bundled index lists. */
    public static DataFiles.Kind<MarkerFile> bundled(Set<String> houses) {
        return kind(true, houses, Collections.<String>emptySet());
    }

    /**
     * A server's files, checked against every house that loaded; a LOTR
     * waypoint's marker only in place of the bundled one.
     */
    public static DataFiles.Kind<MarkerFile> server(Set<String> houses,
                                                    Set<String> bundledIds) {
        return kind(false, houses, bundledIds);
    }

    private static DataFiles.Kind<MarkerFile> kind(final boolean bundled,
                                                   final Set<String> houses,
                                                   final Set<String> bundledIds) {
        return new DataFiles.Kind<MarkerFile>() {
            @Override
            public MarkerFile parse(Reader reader, String file, List<String> said) {
                return LostTalesMapMarkerFiles.parse(reader, file, said, bundled);
            }

            @Override
            public String idOf(MarkerFile markers) {
                return markers.getName();
            }

            @Override
            public List<String> warnings(MarkerFile markers) {
                return warningsOf(markers, bundled, houses, bundledIds);
            }
        };
    }

    /**
     * The markers of {@code files} by id, in order. A file that repeats a
     * marker id an earlier one has is left out whole, said in
     * {@code problems}.
     */
    public static Map<String, LostTalesMapMarkerDefinition> join(List<MarkerFile> files,
                                                                 List<String> problems) {
        Map<String, LostTalesMapMarkerDefinition> byId =
                new LinkedHashMap<String, LostTalesMapMarkerDefinition>();
        for (MarkerFile file : files) {
            String repeated = null;
            for (LostTalesMapMarkerDefinition marker : file.getMarkers()) {
                if (byId.containsKey(marker.getId())) {
                    repeated = marker.getId();
                    break;
                }
            }
            if (repeated != null) {
                problems.add(file.getName() + ": the marker " + repeated
                        + " is an earlier file's");
                continue;
            }
            for (LostTalesMapMarkerDefinition marker : file.getMarkers()) {
                byId.put(marker.getId(), marker);
            }
        }
        return byId;
    }

    static MarkerFile parse(Reader reader, String file, List<String> said, boolean bundled) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        JsonReading where = new JsonReading(said, "the file");
        where.unknownKeys(object, FILE_KEYS);
        if (!object.has(MARKERS)) {
            where.say("holds no '" + MARKERS + "' list");
        }
        List<JsonObject> entries = where.objects(object, MARKERS);
        if (entries.size() > MAX_MARKERS) {
            where.say("holds " + entries.size() + " markers; a file holds at most " + MAX_MARKERS);
            entries = Collections.emptyList();
        }
        List<LostTalesMapMarkerDefinition> markers =
                new ArrayList<LostTalesMapMarkerDefinition>();
        for (int index = 0; index < entries.size(); index++) {
            LostTalesMapMarkerDefinition marker = marker(entries.get(index), index, said,
                    bundled);
            if (marker != null) {
                markers.add(marker);
            }
        }
        return new MarkerFile(baseName(file), markers);
    }

    private static LostTalesMapMarkerDefinition marker(JsonObject entry, int index,
                                                       List<String> said, boolean bundled) {
        String id = entry.has("id") && entry.get("id").isJsonPrimitive()
                ? entry.get("id").getAsString() : "#" + (index + 1);
        JsonReading where = new JsonReading(said, "marker " + id);
        where.unknownKeys(entry, KEYS);
        for (String key : REQUIRED) {
            if (!entry.has(key)) {
                where.say("writes no '" + key + "'");
            }
        }
        id = where.string(entry, "id", "");
        boolean lotrWaypoint = id.startsWith(LOTR_WAYPOINT);
        String name;
        String description;
        if (bundled && !lotrWaypoint) {
            if (entry.has("name") || entry.has("description")) {
                where.say("writes its words, which a bundled marker takes from the lang file");
            }
            name = LostTalesMapMarkerNames.englishName(id);
            description = LostTalesMapMarkerNames.englishDescription(id);
            if (name.length() == 0) {
                where.say("has no English name in the lang file, "
                        + LostTalesMapMarkerNames.nameKey(id));
            }
        } else {
            if (!entry.has("name")) {
                where.say("writes no 'name'");
            }
            if (bundled && entry.has("description")) {
                where.say("writes a description; LOTR describes its own waypoints");
            }
            name = where.string(entry, "name", "").trim();
            description = where.string(entry, "description", "").trim();
        }
        String icon = where.string(entry, "icon", "");
        String color = where.string(entry, "color", "");
        String category = where.string(entry, "category", "").trim();
        String dimensionName = where.string(entry, "dimension", "");
        Integer dimension = dimension(dimensionName);
        if (entry.has("dimension") && dimension == null) {
            where.say("stands in the world '" + dimensionName + "'; it is " + WORLDS);
        }
        double x = where.number(entry, "x", 0.0D);
        double y = entry.has("y") ? where.number(entry, "y", 64.0D)
                : LostTalesMapMarkerDefinition.AUTOMATIC_Y;
        if (entry.has("y") && (y < 1.0D || y > 255.0D)) {
            where.say("stands at a height of " + y + "; it is 1 to 255, or no 'y' for the ground");
        }
        double z = where.number(entry, "z", 0.0D);
        double compass = where.number(entry, "compassFadeInRadius", 0.0D);
        double discovery = where.number(entry, "discoveryRadius", 1.0D);
        boolean discoverable = where.bool(entry, "isDiscoverable", false);
        boolean hidden = where.bool(entry, "hiddenUntilDiscovered", false);
        boolean region = where.bool(entry, "requiresRegionUnlock", false);
        boolean fastTravel = where.bool(entry, "hasFastTravel", false);
        boolean waystone = where.bool(entry, "hasWaystone", false);
        String structure = where.string(entry, "waystoneStructureType", "").trim();
        String relevanceName = where.string(entry, "relevance", "");
        LostTalesMapMarkerRelevance relevance = relevance(relevanceName);
        if (entry.has("relevance") && relevance == null) {
            where.say("is of the relevance '" + relevanceName
                    + "'; it is very-low, low, medium, high or very-high");
        }
        if (hidden && !discoverable) {
            where.say("is hidden until discovered but cannot be discovered");
        }
        if (fastTravel && dimension != null && !MIDDLE_EARTH.equals(dimensionName)) {
            where.say("has fast travel outside Middle-earth");
        }
        if (waystone && !entry.has("waystoneStructureType")) {
            where.say("has a waystone but names no house in 'waystoneStructureType'");
        }
        if (!waystone && entry.has("waystoneStructureType")) {
            where.say("names a house but has no waystone");
        }
        try {
            return new LostTalesMapMarkerDefinition(id, name, icon, color, category,
                    description, fastTravel, dimension == null ? 0 : dimension.intValue(),
                    x, y, z, compass, discovery, hidden, discoverable, region,
                    lotrWaypoint ? LostTalesMapMarkerSource.LOTR_ADAPTER
                            : LostTalesMapMarkerSource.CUSTOM_PRESET,
                    waystone, waystone ? structure : "",
                    relevance == null ? 0 : relevance.getRank());
        } catch (RuntimeException unmade) {
            where.say("cannot be made: " + unmade.getMessage());
            return null;
        }
    }

    static List<String> warningsOf(MarkerFile file, boolean bundled, Set<String> houses,
                                   Set<String> bundledIds) {
        List<String> warnings = new ArrayList<String>();
        Set<String> ids = new LinkedHashSet<String>();
        for (LostTalesMapMarkerDefinition marker : file.getMarkers()) {
            String id = marker.getId();
            String at = "marker " + id + " ";
            if (!ids.add(id)) {
                warnings.add(at + "is in the file twice");
            }
            if (id.length() > MAX_ID_LENGTH || !id.matches("[a-z0-9_]+:[a-z0-9_./:-]+")) {
                warnings.add(at + "has an id that is not a namespace and a path of lower-case"
                        + " letters, digits and _ . / : - within " + MAX_ID_LENGTH
                        + " characters");
            }
            if (id.startsWith(PLAYER_WAYSTONES)) {
                warnings.add(at + "takes an id kept for the waystones players place");
            }
            if (!bundled && id.startsWith(LOTR_WAYPOINT) && !bundledIds.contains(id)) {
                warnings.add(at + "names a LOTR waypoint no bundled marker stands for");
            }
            if (marker.getName().length() > MAX_NAME_LENGTH) {
                warnings.add(at + "has a name longer than " + MAX_NAME_LENGTH);
            }
            if (!bundled && marker.getName().length() == 0) {
                warnings.add(at + "has an empty name");
            }
            if (marker.getDescription().length() > MAX_DESCRIPTION_LENGTH
                    || marker.getCategoryName().length() > MAX_CATEGORY_LENGTH) {
                warnings.add(at + "has a description past " + MAX_DESCRIPTION_LENGTH
                        + " or a category past " + MAX_CATEGORY_LENGTH + " characters");
            }
            if (!marker.getIconName().matches("[A-Za-z0-9_-]{1,64}")) {
                warnings.add(at + "has the icon '" + marker.getIconName()
                        + "', which is no icon's name");
            }
            if (!COLORS.contains(marker.getColorName())
                    && !marker.getColorName().matches("#[0-9a-f]{6}")) {
                warnings.add(at + "has the colour '" + marker.getColorName()
                        + "'; it is " + COLORS + " or #rrggbb");
            }
            if (Math.abs(marker.getX()) > MAX_COORDINATE
                    || Math.abs(marker.getZ()) > MAX_COORDINATE) {
                warnings.add(at + "stands past the world's edge");
            }
            if (marker.getCompassFadeInRadius() < 0.0D
                    || marker.getCompassFadeInRadius() > MAX_RADIUS) {
                warnings.add(at + "fades in on the compass from " + marker.getCompassFadeInRadius()
                        + " blocks; it is 0 to " + MAX_RADIUS);
            }
            if (marker.getDiscoveryRadius() < 1.0D || marker.getDiscoveryRadius() > MAX_RADIUS) {
                warnings.add(at + "is found from " + marker.getDiscoveryRadius()
                        + " blocks; it is 1 to " + MAX_RADIUS);
            }
            if (marker.hasWaystone() && !houses.contains(marker.getWaystoneStructureType())) {
                warnings.add(at + "names the house '" + marker.getWaystoneStructureType()
                        + "', which is no house that loaded");
            }
        }
        return warnings;
    }

    private static LostTalesMapMarkerRelevance relevance(String name) {
        for (LostTalesMapMarkerRelevance relevance : LostTalesMapMarkerRelevance.values()) {
            if (relevance.getSerializedName().equals(name)) {
                return relevance;
            }
        }
        return null;
    }

    /** The world a name stands for, its id as LOTR's config gives it now; null for no world's name. */
    private static Integer dimension(String name) {
        if (MIDDLE_EARTH.equals(name)) {
            return Integer.valueOf(LOTRDimension.MIDDLE_EARTH.dimensionID);
        }
        if ("lotr:utumno".equals(name)) {
            return Integer.valueOf(LOTRDimension.UTUMNO.dimensionID);
        }
        if ("minecraft:overworld".equals(name)) {
            return Integer.valueOf(0);
        }
        if ("minecraft:the_nether".equals(name)) {
            return Integer.valueOf(-1);
        }
        if ("minecraft:the_end".equals(name)) {
            return Integer.valueOf(1);
        }
        return null;
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
