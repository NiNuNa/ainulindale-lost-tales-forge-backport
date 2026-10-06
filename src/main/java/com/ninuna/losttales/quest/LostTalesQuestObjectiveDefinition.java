package com.ninuna.losttales.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * One objective of a quest, as its file or a missive writes it: its id,
 * kind, words and parameters. Progress is the server's and lives
 * elsewhere.
 */
public final class LostTalesQuestObjectiveDefinition {
    private final String id;
    private final String type;
    private final String description;
    private final boolean optional;
    private final Map<String, String> params;
    private final String descriptionKey;

    public LostTalesQuestObjectiveDefinition(String id, String type, String description, boolean optional, Map<String, String> params) {
        this(id, type, description, optional, params, "");
    }

    /**
     * An objective whose description is the lang line under
     * {@code descriptionKey} in each player's language, a bundled quest's;
     * an empty key leaves the description as written.
     */
    public LostTalesQuestObjectiveDefinition(String id, String type, String description, boolean optional, Map<String, String> params, String descriptionKey) {
        this.id = id;
        this.type = type;
        this.description = description;
        this.optional = optional;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<String, String>(params == null ? Collections.<String, String>emptyMap() : params));
        this.descriptionKey = descriptionKey == null ? "" : descriptionKey;
    }

    public String getId() {
        return this.id;
    }

    public String getType() {
        return this.type;
    }

    /** The description as written; read through {@link LostTalesQuestObjectiveTextHelper#describe}. */
    public String getDescription() {
        return this.description;
    }

    /** The lang key of a bundled quest's objective line; empty for one written out. */
    public String getDescriptionKey() {
        return this.descriptionKey;
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
