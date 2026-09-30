package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A missive's quest, made from its letter: one stage of the letter's
 * objectives, its reward, its words as the quest's description and journal
 * line, and a locked start, so only the letter starts it. Who issued it is
 * said on the letter, not in the quest.
 */
public final class LostTalesMissiveQuestFactory {
    private static final String DEFAULT_STAGE_ID = "10";

    private LostTalesMissiveQuestFactory() {}

    public static LostTalesQuestDefinition createQuestDefinition(LostTalesMissiveData missive) {
        if (missive == null || !missive.isValid()) {
            return null;
        }

        ArrayList<LostTalesQuestObjectiveDefinition> objectives = new ArrayList<LostTalesQuestObjectiveDefinition>();
        for (LostTalesMissiveObjectiveData objective : missive.getObjectives()) {
            if (objective == null || !objective.isValid()) {
                continue;
            }
            objectives.add(new LostTalesQuestObjectiveDefinition(
                    objective.getId(),
                    objective.getType(),
                    objective.getDescription(),
                    objective.isOptional(),
                    objective.getParams()
            ));
        }
        if (objectives.isEmpty()) {
            return null;
        }

        List<LostTalesQuestStageDefinition> stages = Collections.<LostTalesQuestStageDefinition>singletonList(new LostTalesQuestStageDefinition(DEFAULT_STAGE_ID, objectives));
        Map<String, String> prerequisites = Collections.emptyMap();
        Map<String, String> interaction = Collections.emptyMap();
        Map<String, String> markers = Collections.emptyMap();

        return new LostTalesQuestDefinition(
                missive.getQuestId(),
                missive.getTitle(),
                missive.getDescription(),
                missive.isRepeatable(),
                LostTalesQuestDefinition.START_MODE_LOCKED,
                prerequisites,
                missive.getRewardData().getRewards(),
                interaction,
                markers,
                createJournalLog(missive),
                stages
        );
    }

    /** The journal's line: the letter's flavour text, else its description; none where it has neither. */
    private static Map<String, String> createJournalLog(LostTalesMissiveData missive) {
        LinkedHashMap<String, String> journalLog = new LinkedHashMap<String, String>();
        String entry = missive.getFlavorText().length() > 0 ? missive.getFlavorText() : missive.getDescription();
        if (entry.length() > 0) {
            journalLog.put(DEFAULT_STAGE_ID, entry);
        }
        return journalLog;
    }
}
