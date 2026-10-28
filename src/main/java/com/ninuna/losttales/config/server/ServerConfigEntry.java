package com.ninuna.losttales.config.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One server config key as the Server Settings page sees it: where it lives,
 * what it holds, what it defaults to, its bounds and its words. Its name
 * and its tip are not carried: the client reads them from its own lang
 * file by the category and the key ({@code LostTalesConfigWords}), in the
 * player's language. A secret key travels without its value, as one
 * blank value while it is set and as none while it is not, and is only
 * ever written, never shown. Immutable; the value of a list is a copy.
 *
 * <p>A setting whose list holds more lines than a change may carry, or a
 * value or default longer than a change may hold, is too long for the
 * page (DATA-16): it travels without its values, stands greyed on the
 * page and is never part of a save; it is edited in {@code server.cfg}.</p>
 */
public final class ServerConfigEntry {

    /** The value kinds the transport carries; colours and mod ids are strings. */
    public enum Type {
        STRING(0), INTEGER(1), BOOLEAN(2), DOUBLE(3);

        private final int code;

        Type(int code) {
            this.code = code;
        }

        public int getCode() {
            return this.code;
        }

        public static Type fromCode(int code) {
            for (Type type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            return null;
        }
    }

    private final String category;
    private final String key;
    private final Type type;
    private final boolean list;
    private final List<String> values;
    private final List<String> defaults;
    private final String minValue;
    private final String maxValue;
    private final boolean secret;
    private final List<String> validValues;
    private final boolean tooLong;

    public ServerConfigEntry(String category, String key, Type type, boolean list,
                             List<String> values, List<String> defaults,
                             String minValue, String maxValue, boolean secret,
                             List<String> validValues) {
        this(category, key, type, list, values, defaults, minValue, maxValue, secret,
                validValues, false);
    }

    private ServerConfigEntry(String category, String key, Type type, boolean list,
                              List<String> values, List<String> defaults,
                              String minValue, String maxValue, boolean secret,
                              List<String> validValues, boolean carriedWithoutValues) {
        if (category == null || category.length() == 0 || key == null
                || key.length() == 0 || type == null) {
            throw new IllegalArgumentException("category, key and type are required");
        }
        this.category = category;
        this.key = key;
        this.type = type;
        this.list = list;
        this.values = copy(values);
        this.defaults = copy(defaults);
        this.minValue = minValue == null ? "" : minValue;
        this.maxValue = maxValue == null ? "" : maxValue;
        this.secret = secret;
        this.validValues = copy(validValues);
        this.tooLong = carriedWithoutValues || !fitsAChange(this.values)
                || !fitsAChange(this.defaults);
    }

    /**
     * A setting too long for the page as the page receives it: its name,
     * kind, bounds and words, without its values (DATA-16).
     */
    public static ServerConfigEntry tooLongForThePage(String category, String key,
                                                      Type type, boolean list,
                                                      String minValue, String maxValue,
                                                      boolean secret,
                                                      List<String> validValues) {
        return new ServerConfigEntry(category, key, type, list, null, null, minValue,
                maxValue, secret, validValues, true);
    }

    /** Whether {@code values} fit one change: few enough lines, none too long. */
    private static boolean fitsAChange(List<String> values) {
        if (values.size() > ServerConfigChangeValidator.MAX_LIST_ITEMS) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.length() > ServerConfigChangeValidator.MAX_VALUE_LENGTH) {
                return false;
            }
        }
        return true;
    }

    private static List<String> copy(List<String> source) {
        return source == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(Arrays.asList(
                        source.toArray(new String[source.size()])));
    }

    public String getCategory() {
        return this.category;
    }

    public String getKey() {
        return this.key;
    }

    /** {@code category.key}, the name commands and results use. */
    public String qualifiedName() {
        return this.category + "." + this.key;
    }

    public Type getType() {
        return this.type;
    }

    public boolean isList() {
        return this.list;
    }

    /** The single value, or the first list item; empty for a secret. */
    public String getValue() {
        return this.values.isEmpty() ? "" : this.values.get(0);
    }

    public List<String> getValues() {
        return this.values;
    }

    public String getDefault() {
        return this.defaults.isEmpty() ? "" : this.defaults.get(0);
    }

    public List<String> getDefaults() {
        return this.defaults;
    }

    public String getMinValue() {
        return this.minValue;
    }

    public String getMaxValue() {
        return this.maxValue;
    }

    public boolean isSecret() {
        return this.secret;
    }

    /** Whether a secret holds a value on the server; false for any other entry. */
    public boolean isSecretSet() {
        return this.secret && !this.values.isEmpty();
    }

    public List<String> getValidValues() {
        return this.validValues;
    }

    /**
     * Whether the setting is too long for the Server Settings page: more
     * lines, or a longer value, than a change may carry (DATA-16).
     */
    public boolean isTooLong() {
        return this.tooLong;
    }
}
