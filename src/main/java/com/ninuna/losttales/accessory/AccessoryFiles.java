package com.ninuna.losttales.accessory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.data.JsonReading;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and checks the accessory files (ACC-01, MOD-17), bundled under
 * {@code assets/losttales/accessories/} and a server's own in
 * {@code config/losttales/accessories/}. A file writes an {@code id}, the
 * {@code slot} it goes in, the {@code item} it is by registry name, and
 * its {@code effects}, each from {@link AccessoryEffect}'s list. A file
 * with anything said, an item the game lacks or an effect the code does
 * not hold among it, is left out.
 */
public final class AccessoryFiles {
    public static final String INDEX = "accessories/index.json";
    public static final String LIST_KEY = "accessories";
    public static final String FOLDER = "losttales/accessories";
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_ITEM_ID_LENGTH = 128;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_.]+");
    private static final Pattern ITEM_ID = Pattern.compile("[A-Za-z0-9_]+:[A-Za-z0-9_.\\-]+");
    static final Set<String> KEYS = JsonReading.keys("id", "slot", "item", "effects");

    /** What the game has, for an accessory's item. */
    public interface Game {
        boolean hasItem(String itemId);
    }

    private AccessoryFiles() {}

    /** The accessory files, each checked against what {@code game} has. */
    public static DataFiles.Kind<AccessoryDefinition> kind(final Game game) {
        return new DataFiles.Kind<AccessoryDefinition>() {
            @Override
            public AccessoryDefinition parse(Reader reader, String file, List<String> said) {
                return AccessoryFiles.parse(reader, file, said);
            }

            @Override
            public String idOf(AccessoryDefinition definition) {
                return definition.getId();
            }

            @Override
            public List<String> warnings(AccessoryDefinition definition) {
                return AccessoryFiles.warningsOf(definition, game);
            }
        };
    }

    public static AccessoryDefinition parse(Reader reader, String file, List<String> said) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        String id = new JsonReading(said, file).string(object, "id", "");
        JsonReading where = new JsonReading(said, "accessory=" + (id.length() == 0 ? file : id));
        where.unknownKeys(object, KEYS);
        String slotName = where.string(object, "slot", "");
        AccessorySlotType slot = AccessorySlotType.byName(slotName);
        if (slot == null) {
            List<String> slots = new ArrayList<String>();
            for (AccessorySlotType each : AccessorySlotType.values()) {
                slots.add(each.getName());
            }
            where.say("goes in the slot '" + slotName + "'; the slots are " + slots);
        }
        String item = where.string(object, "item", "");
        Set<AccessoryEffect> effects = EnumSet.noneOf(AccessoryEffect.class);
        Set<String> named = new LinkedHashSet<String>();
        for (String effectId : where.strings(object, "effects")) {
            AccessoryEffect effect = AccessoryEffect.byId(effectId);
            if (effect == null) {
                where.say("names the effect '" + effectId + "'; the effects are "
                        + AccessoryEffect.ids());
            } else if (!named.add(effectId)) {
                where.say("names the effect '" + effectId + "' twice");
            } else {
                effects.add(effect);
            }
        }
        return new AccessoryDefinition(id, slot, item, effects);
    }

    /** Every warning the accessory raises: its shape, then what {@code game} lacks. */
    public static List<String> warningsOf(AccessoryDefinition definition, Game game) {
        List<String> warnings = new ArrayList<String>();
        if (definition.getId().length() > MAX_ID_LENGTH
                || !ID.matcher(definition.getId()).matches()) {
            warnings.add("the id '" + definition.getId() + "' is not a namespace and a path of"
                    + " lower-case letters, digits, _ and . within " + MAX_ID_LENGTH
                    + " characters");
        }
        String item = definition.getItemId();
        if (item.length() > MAX_ITEM_ID_LENGTH || !ITEM_ID.matcher(item).matches()) {
            warnings.add("the item '" + item + "' is not an item's id with its mod's prefix,"
                    + " like lotr:item.goldRing");
        } else if (game != null && !game.hasItem(item)) {
            warnings.add("is the item '" + item + "', which the game does not have");
        }
        return warnings;
    }
}
