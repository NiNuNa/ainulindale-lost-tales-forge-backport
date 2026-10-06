package com.ninuna.losttales.quest.missive;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One objective of a missive: its id, kind and parameters, before the
 * missive becomes a quest. It carries no words; each game words it from
 * its kind and target ({@code LostTalesQuestObjectiveTextHelper.describe}).
 */
public final class LostTalesMissiveObjectiveData {
    public static final String TYPE_KILL = "kill";
    public static final String TYPE_GATHER = "gather";

    private final String id;
    private final String type;
    private final boolean optional;
    private final Map<String, String> params;

    public LostTalesMissiveObjectiveData(String id, String type, boolean optional, Map<String, String> params) {
        this.id = clean(id);
        this.type = clean(type);
        this.optional = optional;
        this.params = Collections.unmodifiableMap(copyStringMap(params));
    }

    public String getId() {
        return this.id;
    }

    public String getType() {
        return this.type;
    }

    public boolean isOptional() {
        return this.optional;
    }

    public Map<String, String> getParams() {
        return this.params;
    }

    public boolean isValid() {
        return this.id.length() > 0 && this.type.length() > 0;
    }

    static Map<String, String> copyStringMap(Map<String, String> source) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<String, String>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey().trim().length() == 0 || entry.getValue() == null) {
                continue;
            }
            copy.put(entry.getKey().trim(), entry.getValue());
        }
        return copy;
    }

    static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
