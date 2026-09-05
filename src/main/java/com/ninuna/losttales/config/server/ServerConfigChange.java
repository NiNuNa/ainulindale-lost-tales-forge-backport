package com.ninuna.losttales.config.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** One key an operator wants set: a single value, or every item of a list. */
public final class ServerConfigChange {

    private final String category;
    private final String key;
    private final boolean list;
    private final List<String> values;

    public ServerConfigChange(String category, String key, String value) {
        this(category, key, false, Collections.singletonList(value == null ? "" : value));
    }

    public ServerConfigChange(String category, String key, boolean list, List<String> values) {
        if (category == null || category.length() == 0 || key == null || key.length() == 0) {
            throw new IllegalArgumentException("category and key are required");
        }
        this.category = category;
        this.key = key;
        this.list = list;
        this.values = values == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(Arrays.asList(
                        values.toArray(new String[values.size()])));
    }

    public String getCategory() {
        return this.category;
    }

    public String getKey() {
        return this.key;
    }

    public String qualifiedName() {
        return this.category + "." + this.key;
    }

    public boolean isList() {
        return this.list;
    }

    public String getValue() {
        return this.values.isEmpty() ? "" : this.values.get(0);
    }

    public List<String> getValues() {
        return this.values;
    }
}
