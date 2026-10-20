package com.ninuna.losttales.character.kit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.data.JsonReading;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and checks the starting-kit files (CHAR-40), bundled under
 * {@code assets/losttales/kits/} and a server's own in
 * {@code config/losttales/kits/}. A kit writes {@code id}, {@code race}
 * or {@code faction} or both, and {@code items}, each entry an
 * {@code item} id, a {@code count} and, where it is not 0, a
 * {@code damage}. A file with anything said is left out.
 */
public final class StartingKitFiles {
    public static final String INDEX = "kits/index.json";
    public static final String LIST_KEY = "kits";
    public static final String FOLDER = "losttales/kits";
    /** The most entries a kit lists: a new character's whole inventory. */
    public static final int MAX_ITEMS = 36;
    /** The most of one entry: a full stack of the most stackable item. */
    public static final int MAX_COUNT = 64;
    public static final int MAX_DAMAGE = Short.MAX_VALUE;
    public static final int MAX_ITEM_ID_LENGTH = 128;
    private static final Pattern KIT_ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_.]+");
    private static final Pattern RACE_OR_FACTION = Pattern.compile("[a-z0-9_]+:[a-z0-9_]+");
    private static final Pattern ITEM_ID = Pattern.compile("[A-Za-z0-9_]+:[A-Za-z0-9_.\\-]+");
    static final Set<String> KEYS = JsonReading.keys("id", "race", "faction", "items");
    static final Set<String> ITEM_KEYS = JsonReading.keys("item", "count", "damage");

    /** What the game has, for the checks a kit's names need. */
    public interface Game {
        boolean hasRace(String raceId);

        boolean hasFaction(String factionId);

        /**
         * How many of the item at that damage one stack holds; 0 when the
         * game has no such item.
         */
        int stackLimit(String itemId, int damage);

        /** Whether the item wears and is worn through at that damage. */
        boolean wornThrough(String itemId, int damage);
    }

    private StartingKitFiles() {}

    /** The kit files, each checked against what {@code game} has. */
    public static DataFiles.Kind<StartingKit> kind(final Game game) {
        return new DataFiles.Kind<StartingKit>() {
            @Override
            public StartingKit parse(Reader reader, String file, List<String> said) {
                return StartingKitFiles.parse(reader, file, said);
            }

            @Override
            public String idOf(StartingKit kit) {
                return kit.getId();
            }

            @Override
            public List<String> warnings(StartingKit kit) {
                return StartingKitFiles.warningsOf(kit, game);
            }
        };
    }

    public static StartingKit parse(Reader reader, String file, List<String> said) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        // A missing id, item or count reads as empty or 0, which the
        // shape's checks then name.
        String id = new JsonReading(said, file).string(object, "id", "");
        JsonReading where = new JsonReading(said, "kit=" + (id.length() == 0 ? file : id));
        where.unknownKeys(object, KEYS);
        String race = where.string(object, "race", "");
        String faction = where.string(object, "faction", "");
        List<KitItem> items = new ArrayList<KitItem>();
        int index = 0;
        for (JsonObject entry : where.objects(object, "items")) {
            JsonReading at = where.in("item " + (++index));
            at.unknownKeys(entry, ITEM_KEYS);
            items.add(new KitItem(at.string(entry, "item", ""), at.whole(entry, "count", 0),
                    at.whole(entry, "damage", 0)));
        }
        return new StartingKit(id, race, faction, items);
    }

    /** Every warning the kit raises: its shape, then what {@code game} lacks. */
    public static List<String> warningsOf(StartingKit kit, Game game) {
        List<String> warnings = shapeWarnings(kit);
        if (game == null) {
            return warnings;
        }
        if (kit.getRaceId().length() > 0 && !game.hasRace(kit.getRaceId())) {
            warnings.add("is for the race " + kit.getRaceId() + ", which the game does not have");
        }
        if (kit.getFactionId().length() > 0 && !game.hasFaction(kit.getFactionId())) {
            warnings.add("is for the faction " + kit.getFactionId() + ", which LOTR does not have");
        }
        for (KitItem item : kit.getItems()) {
            if (!ITEM_ID.matcher(item.getItemId()).matches()) {
                continue;
            }
            int limit = game.stackLimit(item.getItemId(), item.getDamage());
            if (limit <= 0) {
                warnings.add("gives the item '" + item.getItemId() + "', which the game does not have");
                continue;
            }
            if (item.getCount() > limit && item.getCount() <= MAX_COUNT) {
                warnings.add("gives " + item.getCount() + " of " + item.getItemId()
                        + "; one stack of it holds " + limit);
            }
            if (game.wornThrough(item.getItemId(), item.getDamage())) {
                warnings.add("gives " + item.getItemId() + " at damage " + item.getDamage()
                        + ", which wears it through");
            }
        }
        return warnings;
    }

    /** The warnings a kit raises by its shape alone, what a bundled file is held to as it loads. */
    public static List<String> shapeWarnings(StartingKit kit) {
        List<String> warnings = new ArrayList<String>();
        if (kit.getId().length() > 64 || !KIT_ID.matcher(kit.getId()).matches()) {
            warnings.add("the id '" + kit.getId() + "' is not a namespace and a path of"
                    + " lower-case letters, digits, _ and . within 64 characters");
        }
        if (kit.getRaceId().length() == 0 && kit.getFactionId().length() == 0) {
            warnings.add("names neither a race nor a faction, so it fits nobody");
        }
        if (kit.getRaceId().length() > 0 && !RACE_OR_FACTION.matcher(kit.getRaceId()).matches()) {
            warnings.add("the race '" + kit.getRaceId() + "' is not written as a race's id,"
                    + " like losttales:hobbit");
        }
        if (kit.getFactionId().length() > 0
                && !RACE_OR_FACTION.matcher(kit.getFactionId()).matches()) {
            warnings.add("the faction '" + kit.getFactionId() + "' is not written as a"
                    + " faction's id, like lotr:hobbit");
        }
        if (kit.getItems().isEmpty()) {
            warnings.add("gives nothing");
        }
        if (kit.getItems().size() > MAX_ITEMS) {
            warnings.add("lists " + kit.getItems().size() + " items; a kit lists at most "
                    + MAX_ITEMS);
        }
        for (KitItem item : kit.getItems()) {
            if (item.getItemId().length() > MAX_ITEM_ID_LENGTH
                    || !ITEM_ID.matcher(item.getItemId()).matches()) {
                warnings.add("the item '" + item.getItemId() + "' is not an item's id with its"
                        + " mod's prefix, like minecraft:bread");
            }
            if (item.getCount() < 1 || item.getCount() > MAX_COUNT) {
                warnings.add("gives " + item.getCount() + " of " + item.getItemId()
                        + "; a count runs from 1 to " + MAX_COUNT);
            }
            if (item.getDamage() < 0 || item.getDamage() > MAX_DAMAGE) {
                warnings.add("gives " + item.getItemId() + " at damage " + item.getDamage()
                        + "; a damage runs from 0 to " + MAX_DAMAGE);
            }
        }
        return warnings;
    }
}
