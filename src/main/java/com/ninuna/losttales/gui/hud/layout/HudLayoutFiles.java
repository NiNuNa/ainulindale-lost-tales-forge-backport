package com.ninuna.losttales.gui.hud.layout;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.data.JsonReading;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.File;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and checks the HUD layout files (MAP-11): bundled under
 * {@code assets/losttales/hud_layouts/}, listed in its index and read
 * through the resources, so a resource pack may change them; a player's
 * own in {@code config/losttales/client/hud_layouts/}, one file each. A
 * player's file with a bundled layout's id takes its place. Each file
 * places every HUD panel, its two numbers whole and from 0 to 100
 * (MAP-10); a file with anything said is left out.
 *
 * <pre>
 * {
 *   "id": "low",
 *   "name": "Low",
 *   "description": "Everything down by the hotbar.",
 *   "panels": {
 *     "compass": {"across": 50, "down": 90},
 *     ...
 *   }
 * }
 * </pre>
 *
 * <p>A bundled file writes no words: its name is the lang line
 * {@link #NAME_KEY_PREFIX} and its id, what it is for
 * {@link #DESCRIPTION_KEY_PREFIX} and its id. A player's file writes its
 * name, shown as written in every language; with none, its id must have
 * a name line, as a bundled id has.</p>
 */
public final class HudLayoutFiles {
    public static final String INDEX = "hud_layouts/index.json";
    /** The list the index names its files in. */
    public static final String LIST = "layouts";
    public static final String NAME_KEY_PREFIX = "gui.losttales.settings.hud.preset.";
    public static final String DESCRIPTION_KEY_PREFIX = "gui.losttales.page.hud_placement.layout.";
    public static final int MAX_NAME = 64;
    public static final int MAX_DESCRIPTION = 256;
    static final Set<String> KEYS = JsonReading.keys("id", "name", "description", "panels");
    static final Set<String> PANEL_KEYS = JsonReading.keys("across", "down");

    private HudLayoutFiles() {}

    public static final DataFiles.Kind<HudLayout> KIND = new DataFiles.Kind<HudLayout>() {
        @Override
        public HudLayout parse(Reader reader, String file, List<String> said) {
            return HudLayoutFiles.parse(reader, file, said);
        }

        @Override
        public String idOf(HudLayout layout) {
            return layout.getId();
        }

        @Override
        public List<String> warnings(HudLayout layout) {
            return HudLayoutFiles.warningsOf(layout);
        }
    };

    /**
     * The bundled layouts {@code bundledSource} holds, in their index's
     * order, with the player's own from {@code folder} in place of those of
     * the same id and their new ones after; and why each file left out was
     * left out.
     */
    public static DataFiles.Result<HudLayout> read(DataFiles.Source bundledSource,
                                                   File folder) {
        DataFiles.Result<HudLayout> bundled = DataFiles.readBundled(bundledSource,
                INDEX, LIST, KIND);
        DataFiles.Result<HudLayout> own = DataFiles.readFolder(folder, KIND);
        List<String> problems = new ArrayList<String>();
        for (String problem : bundled.problems) {
            problems.add("bundled " + problem);
        }
        for (String problem : own.problems) {
            problems.add("your " + problem);
        }
        return new DataFiles.Result<HudLayout>(
                DataFiles.merge(bundled.values, own.values, KIND), problems);
    }

    /**
     * Each layout's places as the client file's options take them, by id:
     * {across, down} for every panel of {@link LostTalesConfig#hudElements}
     * in turn.
     */
    public static Map<String, double[][]> placesById(List<HudLayout> layouts) {
        List<String> panels = LostTalesConfig.hudElements();
        Map<String, double[][]> byId = new LinkedHashMap<String, double[][]>();
        for (HudLayout layout : layouts) {
            double[][] places = new double[panels.size()][];
            for (int panel = 0; panel < places.length; panel++) {
                int[] place = layout.place(panels.get(panel));
                places[panel] = place == null ? null
                        : new double[] {place[0], place[1]};
            }
            byId.put(layout.getId(), places);
        }
        return byId;
    }

    public static HudLayout parse(Reader reader, String file, List<String> said) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        String id = new JsonReading(said, "layout " + file).string(object, "id", "");
        JsonReading where = new JsonReading(said, "layout " + (id.length() > 0 ? id : file));
        where.unknownKeys(object, KEYS);
        String name = where.string(object, "name", "");
        String description = where.string(object, "description", "");
        Map<String, int[]> places = new LinkedHashMap<String, int[]>();
        JsonElement panels = object.get("panels");
        if (panels != null && !panels.isJsonNull()) {
            if (!panels.isJsonObject()) {
                where.say("writes 'panels' as something other than a block of panels");
            } else {
                for (Map.Entry<String, JsonElement> entry
                        : panels.getAsJsonObject().entrySet()) {
                    int[] place = place(where.in("panel " + entry.getKey()),
                            entry.getValue(), said);
                    if (place != null) {
                        places.put(entry.getKey(), place);
                    }
                }
            }
        }
        return new HudLayout(id, name, description, places);
    }

    /**
     * One panel's {across, down}; null for anything else, what was wrong
     * said into {@code said}.
     */
    private static int[] place(JsonReading where, JsonElement element,
                               List<String> said) {
        if (element == null || !element.isJsonObject()) {
            where.say("is written as something other than a block of across and down");
            return null;
        }
        JsonObject panel = element.getAsJsonObject();
        int before = said.size();
        where.unknownKeys(panel, PANEL_KEYS);
        for (String axis : new String[] {"across", "down"}) {
            if (!panel.has(axis) || panel.get(axis).isJsonNull()) {
                where.say("writes no '" + axis + "'");
            }
        }
        int across = where.whole(panel, "across", -1);
        int down = where.whole(panel, "down", -1);
        return said.size() == before ? new int[] {across, down} : null;
    }

    public static List<String> warningsOf(HudLayout layout) {
        List<String> warnings = new ArrayList<String>();
        String id = layout.getId();
        if (id.length() == 0) {
            warnings.add("writes no id");
        } else if (!id.matches(LostTalesConfig.HUD_LAYOUT_ID)) {
            warnings.add("the id '" + id + "' is not lower-case letters, digits, - and _"
                    + " within 64 characters");
        } else if (LostTalesConfig.HUD_PRESET_CUSTOM.equals(id)) {
            warnings.add("takes the id custom, which keeps the panels where the player put them");
        }
        List<String> panels = LostTalesConfig.hudElements();
        for (String panel : panels) {
            if (!layout.getPlaces().containsKey(panel)) {
                warnings.add("places no " + panel);
            }
        }
        for (Map.Entry<String, int[]> place : layout.getPlaces().entrySet()) {
            if (!panels.contains(place.getKey())) {
                warnings.add("places '" + place.getKey() + "', which is no HUD panel;"
                        + " the panels are " + join(panels));
                continue;
            }
            int[] numbers = place.getValue();
            if (numbers[0] < 0 || numbers[0] > 100 || numbers[1] < 0 || numbers[1] > 100) {
                warnings.add("puts " + place.getKey() + " at " + numbers[0] + " across, "
                        + numbers[1] + " down; each runs from 0 to 100");
            }
        }
        if (layout.getName().length() == 0 && id.length() > 0
                && !LostTalesLangFile.english().containsKey(NAME_KEY_PREFIX + id)) {
            warnings.add("writes no name, and no lang line names the id " + id);
        }
        if (layout.getName().length() > MAX_NAME
                || layout.getDescription().length() > MAX_DESCRIPTION) {
            warnings.add("its name runs past " + MAX_NAME + " characters or its description past "
                    + MAX_DESCRIPTION);
        }
        if (!plain(layout.getName()) || !plain(layout.getDescription())) {
            warnings.add("writes a formatting code or a control character in its words");
        }
        return warnings;
    }

    /** Whether the words hold no formatting code and no control character. */
    private static boolean plain(String words) {
        for (int index = 0; index < words.length(); index++) {
            char letter = words.charAt(index);
            if (letter == '§' || Character.isISOControl(letter)) {
                return false;
            }
        }
        return true;
    }

    private static String join(List<String> names) {
        StringBuilder joined = new StringBuilder();
        for (int index = 0; index < names.size(); index++) {
            if (index > 0) {
                joined.append(index == names.size() - 1 ? " and " : ", ");
            }
            joined.append(names.get(index));
        }
        return joined.toString();
    }
}
