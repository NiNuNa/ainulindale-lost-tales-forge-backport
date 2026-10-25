package com.ninuna.losttales.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a data file ({@link DataFiles}) the way the quest files are read:
 * what a file writes in a shape its key never takes, or under a key
 * nothing reads, is said where it stands, and a file with anything said
 * is left out (MOD-17).
 */
public final class JsonReading {
    private final List<String> said;
    private final String place;

    public JsonReading(List<String> said, String place) {
        this.said = said;
        this.place = place;
    }

    public JsonReading in(String part) {
        return new JsonReading(this.said, this.place + " " + part);
    }

    public void say(String problem) {
        this.said.add(this.place + " " + problem);
    }

    public static Set<String> keys(String... names) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(names)));
    }

    public void unknownKeys(JsonObject object, Set<String> known) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!known.contains(entry.getKey())) {
                say("writes '" + entry.getKey() + "', which nothing reads");
            }
        }
    }

    public String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            say("writes '" + key + "' as something other than a text");
            return fallback;
        }
        return element.getAsString();
    }

    public boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            say("writes '" + key + "' as something other than true or false");
            return fallback;
        }
        return element.getAsBoolean();
    }

    /** A finite number; anything else is said and {@code fallback} read. */
    public double number(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        Double value = number(element);
        if (value == null) {
            say("writes '" + key + "' as something other than a number");
            return fallback;
        }
        return value.doubleValue();
    }

    /** A whole number; anything else is said and {@code fallback} read. */
    public int whole(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        Integer value = whole(element);
        if (value == null) {
            say("writes '" + key + "' as something other than a whole number");
            return fallback;
        }
        return value.intValue();
    }

    /** A list of texts; an entry of another kind is said and left out. */
    public List<String> strings(JsonObject object, String key) {
        List<String> values = new ArrayList<String>();
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (!element.isJsonArray()) {
            say("writes '" + key + "' as something other than a list");
            return values;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry == null || !entry.isJsonPrimitive()
                    || !entry.getAsJsonPrimitive().isString()) {
                say("lists something other than a text in '" + key + "'");
                continue;
            }
            values.add(entry.getAsString());
        }
        return values;
    }

    /** A list of whole numbers, or one whole number as a list of one. */
    public int[] wholes(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return new int[0];
        }
        if (!element.isJsonArray()) {
            Integer single = whole(element);
            if (single == null) {
                say("writes '" + key + "' as something other than a whole number or a list of them");
                return new int[0];
            }
            return new int[] { single.intValue() };
        }
        JsonArray array = element.getAsJsonArray();
        int[] values = new int[array.size()];
        int index = 0;
        for (JsonElement entry : array) {
            Integer value = whole(entry);
            if (value == null) {
                say("lists something other than a whole number in '" + key + "'");
                return new int[0];
            }
            values[index++] = value.intValue();
        }
        return values;
    }

    /** A block of names, each a number; a name of another kind is said and left out. */
    public Map<String, Double> numbers(JsonObject object, String key) {
        Map<String, Double> values = new LinkedHashMap<String, Double>();
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (!element.isJsonObject()) {
            say("writes '" + key + "' as something other than a block of names");
            return values;
        }
        for (Map.Entry<String, JsonElement> entry
                : element.getAsJsonObject().entrySet()) {
            Double value = number(entry.getValue());
            if (value == null) {
                say("writes '" + key + "." + entry.getKey()
                        + "' as something other than a number");
                continue;
            }
            values.put(entry.getKey(), value);
        }
        return values;
    }

    /** A block of names, each a text; a name of another kind is said and left out. */
    public Map<String, String> texts(JsonObject object, String key) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (!element.isJsonObject()) {
            say("writes '" + key + "' as something other than a block of names");
            return values;
        }
        for (Map.Entry<String, JsonElement> entry
                : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()) {
                say("writes '" + key + "." + entry.getKey()
                        + "' as something other than a text");
                continue;
            }
            values.put(entry.getKey(), value.getAsString());
        }
        return values;
    }

    /** The objects of a list; anything else in it is said and left out. */
    public List<JsonObject> objects(JsonObject object, String key) {
        List<JsonObject> values = new ArrayList<JsonObject>();
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (!element.isJsonArray()) {
            say("writes '" + key + "' as something other than a list");
            return values;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry == null || !entry.isJsonObject()) {
                say("lists something other than a block in '" + key + "'");
                continue;
            }
            values.add(entry.getAsJsonObject());
        }
        return values;
    }

    private static Double number(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return null;
        }
        double value = primitive.getAsDouble();
        return Double.isNaN(value) || Double.isInfinite(value) ? null
                : Double.valueOf(value);
    }

    private static Integer whole(JsonElement element) {
        Double value = number(element);
        if (value == null || value.doubleValue() != Math.rint(value.doubleValue())
                || Math.abs(value.doubleValue()) > Integer.MAX_VALUE) {
            return null;
        }
        return Integer.valueOf((int) value.doubleValue());
    }
}
