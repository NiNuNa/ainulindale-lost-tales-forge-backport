package com.ninuna.losttales.character.cape;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.data.JsonReading;
import com.ninuna.losttales.util.LostTalesLog;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The capes the creator offers (CHAR-39), as data files: bundled under
 * {@code assets/losttales/capes/}, a server's own in
 * {@code config/losttales/capes/}, one cape a file. A cape is open to every
 * character unless its file names the races or the factions that may wear
 * it. A file names only a picture every player has: one of LOTR's capes by
 * its LOTR name, or one under the mod's own {@code textures/capes/}. The
 * bundled capes are read as the mod loads; the server's list takes their
 * place as it starts, and a client's is the list the server sent.
 */
public final class CharacterCapeCatalog {

    /** The id of no cape. */
    public static final String NONE_ID = "";
    public static final String INDEX = "capes/index.json";
    public static final String LIST_KEY = "capes";
    public static final String FOLDER = "losttales/capes";
    /** The most capes a list holds. */
    public static final int MAX_CAPES = 256;
    public static final int MAX_NAMED = 8;
    public static final int MAX_NAME = 32;
    public static final int MAX_ID = 64;
    public static final String LOTR_PICTURE = "lotr:";
    public static final String OWN_PICTURE = "losttales:textures/capes/";

    /** LOTR's capes, by the names its {@code LOTRCapes} gives them. */
    public static final List<String> LOTR_CAPES = Collections.unmodifiableList(Arrays.asList(
            "GONDOR", "TOWER_GUARD", "RANGER", "RANGER_ITHILIEN", "LOSSARNACH", "PELARGIR",
            "BLACKROOT", "PINNATH_GELIN", "LAMEDON", "ROHAN", "DALE", "DUNLENDING_BERSERKER",
            "GALADHRIM", "GALADHRIM_TRADER", "WOOD_ELF", "HIGH_ELF", "RIVENDELL",
            "RIVENDELL_TRADER", "NEAR_HARAD", "SOUTHRON_CHAMPION", "GULF_HARAD", "TAURETHRIM",
            "GALADHRIM_SMITH", "DORWINION_CAPTAIN", "DORWINION_ELF_CAPTAIN", "GANDALF",
            "GANDALF_SANTA"));

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_]+");
    private static final Pattern NAMED_ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_.]+");
    private static final Pattern OWN_FILE = Pattern.compile("[a-z0-9_/]+\\.png");
    static final Set<String> KEYS = JsonReading.keys("id", "name", "picture", "races",
            "factions");

    /** Where the bundled files are read from: the mod's own classpath. */
    public static final DataFiles.Source CLASSPATH = new DataFiles.Source() {
        @Override
        public Reader open(String path) {
            InputStream stream = CharacterCapeCatalog.class.getClassLoader()
                    .getResourceAsStream("assets/" + LostTalesMetaData.MOD_ID + "/" + path);
            return stream == null ? null
                    : new InputStreamReader(stream, StandardCharsets.UTF_8);
        }
    };

    public static final DataFiles.Kind<CharacterCapeDefinition> KIND =
            new DataFiles.Kind<CharacterCapeDefinition>() {
                @Override
                public CharacterCapeDefinition parse(Reader reader, String file,
                                                     List<String> said) {
                    return CharacterCapeCatalog.parse(reader, file, said);
                }

                @Override
                public String idOf(CharacterCapeDefinition cape) {
                    return cape.getId();
                }

                @Override
                public List<String> warnings(CharacterCapeDefinition cape) {
                    return warningsOf(cape);
                }
            };

    private static final List<CharacterCapeDefinition> BUNDLED = readBundled();
    private static volatile Map<String, CharacterCapeDefinition> capes = index(BUNDLED);

    private CharacterCapeCatalog() {}

    /** Every cape of the list, in its order. */
    public static List<CharacterCapeDefinition> getDefinitions() {
        return new ArrayList<CharacterCapeDefinition>(capes.values());
    }

    /** A cape by id; null for none and for one the list does not hold. */
    public static CharacterCapeDefinition get(String id) {
        return id == null ? null : capes.get(id);
    }

    /**
     * The capes a character of {@code raceId} in {@code factionId} may
     * choose, as ids, none first, in the list's order; the one it wears
     * now, {@code current}, stays among them whether its file still allows
     * it or not.
     */
    public static List<String> choicesFor(String raceId, String factionId, String current) {
        List<String> ids = new ArrayList<String>();
        ids.add(NONE_ID);
        for (CharacterCapeDefinition cape : capes.values()) {
            if (cape.mayWear(raceId, factionId) || cape.getId().equals(current)) {
                ids.add(cape.getId());
            }
        }
        if (current != null && current.length() > 0 && !ids.contains(current)) {
            ids.add(current);
        }
        return ids;
    }

    /** Whether the id is no cape or one the list holds. */
    public static boolean isValidSelection(String id) {
        return NONE_ID.equals(id) || get(id) != null;
    }

    /** The bundled capes, as their files write them. */
    public static List<CharacterCapeDefinition> getBundled() {
        return BUNDLED;
    }

    /** Puts a list in the bundled capes' place: the server's own, or what a server sent. */
    public static synchronized void set(List<CharacterCapeDefinition> list) {
        capes = index(list == null ? BUNDLED : list);
    }

    /** The bundled capes again, as the server stops and a client leaves. */
    public static synchronized void resetToBundled() {
        set(BUNDLED);
    }

    public static CharacterCapeDefinition parse(Reader reader, String file, List<String> said) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        String id = new JsonReading(said, file).string(object, "id", "");
        JsonReading where = new JsonReading(said, "cape=" + (id.length() == 0 ? file : id));
        where.unknownKeys(object, KEYS);
        if (id.length() == 0) {
            where.say("names no id");
        }
        return new CharacterCapeDefinition(id, where.string(object, "name", ""),
                where.string(object, "picture", ""),
                new LinkedHashSet<String>(where.strings(object, "races")),
                new LinkedHashSet<String>(where.strings(object, "factions")));
    }

    /** Every warning a cape raises. */
    public static List<String> warningsOf(CharacterCapeDefinition cape) {
        List<String> warnings = new ArrayList<String>();
        if (cape.getId().length() > MAX_ID || !ID.matcher(cape.getId()).matches()) {
            warnings.add("the id '" + cape.getId() + "' is not a namespace and a path of"
                    + " lower-case letters, digits and _ within " + MAX_ID + " characters");
        }
        if (cape.getName().length() > MAX_NAME) {
            warnings.add("its name is longer than " + MAX_NAME + " characters");
        }
        if (!isPictureEveryoneHas(cape.getPicture())) {
            warnings.add("shows the picture '" + cape.getPicture() + "'; it is one of LOTR's"
                    + " capes by name (" + LOTR_PICTURE + "GONDOR) or a picture under the mod's own "
                    + OWN_PICTURE);
        }
        if (cape.getRaces().size() > MAX_NAMED || cape.getFactions().size() > MAX_NAMED) {
            warnings.add("names more than " + MAX_NAMED + " races or factions");
        }
        for (String race : cape.getRaces()) {
            if (!ID.matcher(race).matches()) {
                warnings.add("names the race '" + race + "', which is no race's id");
            }
        }
        for (String faction : cape.getFactions()) {
            if (!NAMED_ID.matcher(faction).matches()) {
                warnings.add("names the faction '" + faction + "', which is no faction's id");
            }
        }
        return warnings;
    }

    /** Whether every player has the picture: LOTR's cape by name, or the mod's own. */
    static boolean isPictureEveryoneHas(String picture) {
        if (picture.startsWith(LOTR_PICTURE)) {
            return LOTR_CAPES.contains(picture.substring(LOTR_PICTURE.length()));
        }
        if (!picture.startsWith(OWN_PICTURE)) {
            return false;
        }
        String file = picture.substring(OWN_PICTURE.length());
        return OWN_FILE.matcher(file).matches() && !file.contains("//")
                && CharacterCapeCatalog.class.getClassLoader().getResource("assets/"
                        + LostTalesMetaData.MOD_ID + "/textures/capes/" + file) != null;
    }

    private static List<CharacterCapeDefinition> readBundled() {
        DataFiles.Result<CharacterCapeDefinition> result =
                DataFiles.readBundled(CLASSPATH, INDEX, LIST_KEY, KIND);
        for (String problem : result.problems) {
            LostTalesLog.warning("Bundled cape left out: %s", problem);
        }
        return Collections.unmodifiableList(
                new ArrayList<CharacterCapeDefinition>(result.values));
    }

    private static Map<String, CharacterCapeDefinition> index(
            List<CharacterCapeDefinition> list) {
        LinkedHashMap<String, CharacterCapeDefinition> byId =
                new LinkedHashMap<String, CharacterCapeDefinition>();
        for (CharacterCapeDefinition cape : list) {
            if (cape != null && cape.getId().length() > 0 && !byId.containsKey(cape.getId())
                    && byId.size() < MAX_CAPES) {
                byId.put(cape.getId(), cape);
            }
        }
        return Collections.unmodifiableMap(byId);
    }
}
