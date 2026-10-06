package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A missive's quest, made from its letter: one stage of the letter's
 * objectives, its reward, the letter's template ids as the words it is
 * read by ({@link MissiveWords}: its title, its description, and its
 * flavour line as the journal's), and a locked start, so only the letter
 * starts it.
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
                    "",
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
                "",
                "",
                missive.isRepeatable(),
                missive.isRepeatable(),
                LostTalesQuestDefinition.START_MODE_LOCKED,
                prerequisites,
                missive.getRewardData().getRewards(),
                interaction,
                markers,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                stages,
                false,
                MissiveWords.wordsOf(missive)
        );
    }
}
