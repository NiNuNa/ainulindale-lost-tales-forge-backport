package com.ninuna.losttales.config.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One server config key as an operator's client sees it: where it lives,
 * what it holds, what it defaults to, its bounds and its comment. A
 * secret key travels with an empty value and is only ever written, never
 * shown. Immutable; the value of a list is a copy.
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
    private final String comment;
    private final String langKey;
    private final boolean secret;
    private final List<String> validValues;

    public ServerConfigEntry(String category, String key, Type type, boolean list,
                             List<String> values, List<String> defaults,
                             String minValue, String maxValue, String comment,
                             String langKey, boolean secret, List<String> validValues) {
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
        this.comment = comment == null ? "" : comment;
        this.langKey = langKey == null || langKey.length() == 0 ? key : langKey;
        this.secret = secret;
        this.validValues = copy(validValues);
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

    public String getComment() {
        return this.comment;
    }

    public String getLangKey() {
        return this.langKey;
    }

    public boolean isSecret() {
        return this.secret;
    }

    public List<String> getValidValues() {
        return this.validValues;
    }

    /** The same entry with other values, for a client's edited copy. */
    public ServerConfigEntry withValues(List<String> replacement) {
        return new ServerConfigEntry(this.category, this.key, this.type, this.list,
                replacement, this.defaults, this.minValue, this.maxValue, this.comment,
                this.langKey, this.secret, this.validValues);
    }
}
