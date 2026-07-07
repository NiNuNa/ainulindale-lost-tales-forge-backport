package com.ninuna.losttales.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Simple immutable quest stage definition loaded from JSON. */
public final class LostTalesQuestStageDefinition {
    private final String id;
    private final List<LostTalesQuestObjectiveDefinition> objectives;

    public LostTalesQuestStageDefinition(String id, List<LostTalesQuestObjectiveDefinition> objectives) {
        this.id = id;
        this.objectives = Collections.unmodifiableList(new ArrayList<LostTalesQuestObjectiveDefinition>(objectives == null ? Collections.<LostTalesQuestObjectiveDefinition>emptyList() : objectives));
    }

    public String getId() {
        return this.id;
    }

    public List<LostTalesQuestObjectiveDefinition> getObjectives() {
        return this.objectives;
    }
}
