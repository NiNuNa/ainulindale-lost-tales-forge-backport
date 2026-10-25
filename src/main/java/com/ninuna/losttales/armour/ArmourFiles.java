package com.ninuna.losttales.armour;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.data.JsonReading;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and checks the armour files (MOD-17), bundled under
 * {@code assets/losttales/armour/} and a server's own in
 * {@code config/losttales/armour/weights/} and {@code .../sets/}.
 * A weight file (CONTENT-06) writes an {@code id} and blocks of
 * {@code materials} and {@code items}, each naming its weight; a named
 * set (CONTENT-07) writes an {@code id}, the items of its
 * {@code helmet}, {@code chestplate}, {@code leggings} and {@code boots},
 * and its {@code bonus}; a relic set (CONTENT-11) adds {@code relic} and,
 * a server's own, its pieces' {@code names} and {@code lore}. A file with
 * anything said or warned is left out whole.
 */
public final class ArmourFiles {
    public static final String INDEX = "armour/index.json";
    public static final String WEIGHTS_KEY = "weights";
    public static final String SETS_KEY = "sets";
    public static final String WEIGHTS_FOLDER = "losttales/armour/weights";
    public static final String SETS_FOLDER = "losttales/armour/sets";
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_ITEM_ID_LENGTH = 128;
    public static final int MAX_MATERIAL_LENGTH = 128;
    /** The most materials, and the most items, one weight file names. */
    public static final int MAX_ENTRIES = 1024;
    /** The most items one slot of a named set takes. */
    public static final int MAX_SLOT_ITEMS = 16;
    /** The most effects one set's bonus holds. */
    public static final int MAX_BONUSES = 3;
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_LORE_LENGTH = 256;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_.]+");
    private static final Pattern ITEM_ID = Pattern.compile("[A-Za-z0-9_]+:[A-Za-z0-9_.\\-]+");
    private static final Pattern MATERIAL = Pattern.compile("[A-Za-z0-9_.\\-:]+");
    static final Set<String> WEIGHT_KEYS = JsonReading.keys("id", "materials", "items");
    static final Set<String> SET_KEYS = JsonReading.keys("id", "relic", "helmet", "chestplate",
            "leggings", "boots", "bonus", "name", "names", "lore");
    static final Set<String> BONUS_KEYS = JsonReading.keys("effect", "amount", "skill");

    private ArmourFiles() {}

    public static final DataFiles.Kind<WeightFile> WEIGHTS = new DataFiles.Kind<WeightFile>() {
        @Override
        public WeightFile parse(Reader reader, String file, List<String> said) {
            return parseWeights(reader, file, said);
        }

        @Override
        public String idOf(WeightFile weights) {
            return weights.getId();
        }

        @Override
        public List<String> warnings(WeightFile weights) {
            return warningsOf(weights);
        }
    };

    public static final DataFiles.Kind<NamedSet> SETS = new DataFiles.Kind<NamedSet>() {
        @Override
        public NamedSet parse(Reader reader, String file, List<String> said) {
            return parseSet(reader, file, said);
        }

        @Override
        public String idOf(NamedSet set) {
            return set.getId();
        }

        @Override
        public List<String> warnings(NamedSet set) {
            return warningsOf(set);
        }
    };

    public static WeightFile parseWeights(Reader reader, String file, List<String> said) {
        JsonObject object = object(reader);
        if (object == null) {
            return null;
        }
        String id = new JsonReading(said, file).string(object, "id", idFromFile(file));
        JsonReading where = new JsonReading(said, "weights=" + id);
        where.unknownKeys(object, WEIGHT_KEYS);
        return new WeightFile(id, weights(where, object, "materials"),
                weights(where, object, "items"));
    }

    private static Map<String, ArmourWeight> weights(JsonReading where, JsonObject object,
                                                     String key) {
        Map<String, ArmourWeight> weights = new LinkedHashMap<String, ArmourWeight>();
        for (Map.Entry<String, String> entry : where.texts(object, key).entrySet()) {
            ArmourWeight weight = ArmourWeight.fromWord(entry.getValue());
            if (weight == null) {
                where.say("weighs " + entry.getKey() + " '" + entry.getValue()
                        + "'; a weight is light, heavy or neither");
                continue;
            }
            weights.put(entry.getKey(), weight);
        }
        return weights;
    }

    public static NamedSet parseSet(Reader reader, String file, List<String> said) {
        JsonObject object = object(reader);
        if (object == null) {
            return null;
        }
        String id = new JsonReading(said, file).string(object, "id", idFromFile(file));
        JsonReading where = new JsonReading(said, "set=" + id);
        where.unknownKeys(object, SET_KEYS);
        boolean relic = where.bool(object, "relic", false);
        Map<ArmourSlot, List<String>> slots = new EnumMap<ArmourSlot, List<String>>(ArmourSlot.class);
        for (ArmourSlot slot : ArmourSlot.values()) {
            slots.put(slot, where.strings(object, slot.word()));
        }
        List<SetBonus> bonuses = new ArrayList<SetBonus>();
        int index = 0;
        for (JsonObject bonusObject : where.objects(object, "bonus")) {
            JsonReading bonus = where.in("bonus=" + (++index));
            bonus.unknownKeys(bonusObject, BONUS_KEYS);
            String word = bonus.string(bonusObject, "effect", "");
            SetEffect effect = SetEffect.fromWord(word);
            if (effect == null) {
                bonus.say("names the effect '" + word + "'; the effects are " + SetEffect.words());
                continue;
            }
            bonuses.add(new SetBonus(effect, bonus.number(bonusObject, "amount", 0.0D),
                    bonus.string(bonusObject, "skill", "")));
        }
        String name = where.string(object, "name", "");
        return new NamedSet(id, relic, slots, bonuses, name, pieceWords(where, object, "names"),
                pieceWords(where, object, "lore"));
    }

    private static Map<ArmourSlot, String> pieceWords(JsonReading where, JsonObject object,
                                                      String key) {
        Map<ArmourSlot, String> words = new EnumMap<ArmourSlot, String>(ArmourSlot.class);
        for (Map.Entry<String, String> entry : where.texts(object, key).entrySet()) {
            ArmourSlot slot = ArmourSlot.fromWord(entry.getKey());
            if (slot == null) {
                where.say("writes '" + key + "." + entry.getKey()
                        + "'; the pieces are helmet, chestplate, leggings and boots");
                continue;
            }
            words.put(slot, entry.getValue());
        }
        return words;
    }

    /** Every warning a weight file raises. */
    public static List<String> warningsOf(WeightFile weights) {
        List<String> warnings = new ArrayList<String>();
        id(warnings, weights.getId());
        if (weights.getMaterials().isEmpty() && weights.getItems().isEmpty()) {
            warnings.add("it names no material and no item, so it weighs nothing");
        }
        if (weights.getMaterials().size() > MAX_ENTRIES) {
            warnings.add("it names more than " + MAX_ENTRIES + " materials");
        }
        if (weights.getItems().size() > MAX_ENTRIES) {
            warnings.add("it names more than " + MAX_ENTRIES + " items");
        }
        for (String material : weights.getMaterials().keySet()) {
            if (!isMaterial(material)) {
                warnings.add("the material '" + material + "' is not a material's name,"
                        + " like LOTR_GONDOR or IRON");
            }
        }
        for (String item : weights.getItems().keySet()) {
            item(warnings, "it weighs", item);
        }
        return warnings;
    }

    /** Every warning a named set raises. */
    public static List<String> warningsOf(NamedSet set) {
        List<String> warnings = new ArrayList<String>();
        id(warnings, set.getId());
        for (ArmourSlot slot : ArmourSlot.values()) {
            List<String> items = set.items(slot);
            if (items.isEmpty() || items.size() > MAX_SLOT_ITEMS) {
                warnings.add("its " + slot.word() + " takes between 1 and " + MAX_SLOT_ITEMS
                        + " items");
            }
            Set<String> seen = new HashSet<String>();
            for (String item : items) {
                item(warnings, "its " + slot.word() + " takes", item);
                if (!seen.add(item)) {
                    warnings.add("its " + slot.word() + " takes " + item + " twice");
                }
            }
        }
        if (set.getBonuses().isEmpty() || set.getBonuses().size() > MAX_BONUSES) {
            warnings.add("its bonus holds between 1 and " + MAX_BONUSES + " effects");
        }
        Set<SetEffect> effects = EnumSet.noneOf(SetEffect.class);
        for (SetBonus bonus : set.getBonuses()) {
            String where = "its " + bonus.getEffect().word();
            if (!effects.add(bonus.getEffect())) {
                warnings.add(where + " is written twice");
            }
            if (!(bonus.getAmount() > 0.0D) || bonus.getAmount() > 1.0D) {
                warnings.add(where + " is " + bonus.getAmount()
                        + "; a share or a chance lies above 0 and at most 1");
            }
            if (bonus.getEffect() == SetEffect.EXPERIENCE) {
                if (bonus.getSkill().length() > MAX_ID_LENGTH
                        || !ID.matcher(bonus.getSkill()).matches()) {
                    warnings.add(where + " names the skill '" + bonus.getSkill()
                            + "', which is no skill's id");
                }
            } else if (bonus.getSkill().length() > 0) {
                warnings.add(where + " names a skill, which only experience reads");
            }
        }
        if (set.getName().length() > MAX_NAME_LENGTH) {
            warnings.add("its name is longer than " + MAX_NAME_LENGTH + " characters");
        }
        if (!set.isRelic() && (!set.getPieceNames().isEmpty() || !set.getPieceLore().isEmpty())) {
            warnings.add("it names its pieces, which only a relic set does");
        }
        for (String name : set.getPieceNames().values()) {
            if (name.length() > MAX_NAME_LENGTH) {
                warnings.add("a piece's name is longer than " + MAX_NAME_LENGTH + " characters");
            }
        }
        for (String lore : set.getPieceLore().values()) {
            if (lore.length() > MAX_LORE_LENGTH) {
                warnings.add("a piece's lore is longer than " + MAX_LORE_LENGTH + " characters");
            }
        }
        return warnings;
    }

    /** {@code armour/sets/gondorian.json} reads as {@code losttales:gondorian}. */
    public static String idFromFile(String file) {
        String path = file == null ? "" : file.trim().replace('\\', '/');
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return LostTalesMetaData.MOD_ID + ":" + path;
    }

    /** Whether a name is a skill's or a set's id: a namespace and a path. */
    public static boolean isId(String id) {
        return id != null && id.length() <= MAX_ID_LENGTH && ID.matcher(id).matches();
    }

    /** Whether a name is an armour material's, as the game registers it. */
    public static boolean isMaterial(String material) {
        return material != null && material.length() <= MAX_MATERIAL_LENGTH
                && MATERIAL.matcher(material).matches();
    }

    /** Whether a name is an item's registry name with its mod's prefix. */
    public static boolean isItemId(String item) {
        return item != null && item.length() <= MAX_ITEM_ID_LENGTH
                && ITEM_ID.matcher(item).matches();
    }

    private static void id(List<String> warnings, String id) {
        if (!isId(id)) {
            warnings.add("the id '" + id + "' is not a namespace and a path of lower-case"
                    + " letters, digits, _ and . within " + MAX_ID_LENGTH + " characters");
        }
    }

    private static void item(List<String> warnings, String where, String item) {
        if (!isItemId(item)) {
            warnings.add(where + " '" + item + "', which is not an item's id with its mod's"
                    + " prefix, like lotr:item.helmetGondor");
        }
    }

    private static JsonObject object(Reader reader) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        return root == null || !root.isJsonObject() ? null : root.getAsJsonObject();
    }
}
