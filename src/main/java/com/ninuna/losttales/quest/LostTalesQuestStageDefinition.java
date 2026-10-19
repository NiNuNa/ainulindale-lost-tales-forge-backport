package com.ninuna.losttales.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * One stage of a quest: its id, its objectives, and its rules, the keys a
 * stage writes beside them ({@link LostTalesQuestRules}).
 */
public final class LostTalesQuestStageDefinition {
    private final String id;
    private final List<LostTalesQuestObjectiveDefinition> objectives;
    private final Map<String, String> rules;

    public LostTalesQuestStageDefinition(String id, List<LostTalesQuestObjectiveDefinition> objectives) {
        this(id, objectives, Collections.<String, String>emptyMap());
    }

    public LostTalesQuestStageDefinition(String id,
            List<LostTalesQuestObjectiveDefinition> objectives,
            Map<String, String> rules) {
        this.id = id;
        this.objectives = Collections.unmodifiableList(new ArrayList<LostTalesQuestObjectiveDefinition>(objectives == null ? Collections.<LostTalesQuestObjectiveDefinition>emptyList() : objectives));
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<String, String>(
                rules == null ? Collections.<String, String>emptyMap() : rules));
    }

    /** The stage's rules as written, its time limit among them; empty for none. */
    public Map<String, String> getRules() {
        return this.rules;
    }

    public String getId() {
        return this.id;
    }

    public List<LostTalesQuestObjectiveDefinition> getObjectives() {
        return this.objectives;
    }
}
