package com.ninuna.losttales.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable description of one quest objective loaded from JSON.
 *
 * This is deliberately data-only for the first 1.7.10 quest backport stage.
 * Runtime progress, event handlers, and syncing can be layered on top later.
 */
public final class LostTalesQuestObjectiveDefinition {
    private final String id;
    private final String type;
    private final String description;
    private final boolean optional;
    private final Map<String, String> params;

    public LostTalesQuestObjectiveDefinition(String id, String type, String description, boolean optional, Map<String, String> params) {
        this.id = id;
        this.type = type;
        this.description = description;
        this.optional = optional;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<String, String>(params == null ? Collections.<String, String>emptyMap() : params));
    }

    public String getId() {
        return this.id;
    }

    public String getType() {
        return this.type;
    }

    public String getDescription() {
        return this.description;
    }

    public boolean isOptional() {
        return this.optional;
    }

    public Map<String, String> getParams() {
        return this.params;
    }

    public String getParam(String key, String fallback) {
        String value = this.params.get(key);
        return value == null ? fallback : value;
    }
}
