package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Which stage a quest stands at, and which objectives count there. */
public final class LostTalesQuestObjectiveSelection {
    private LostTalesQuestObjectiveSelection() {}

    /**
     * Where the quest stands: the stage its progress names by id, or -1
     * where the quest's file has no stage of that id. A quest whose stage
     * is gone from its file stands nowhere and counts nothing until the
     * file has the stage again (MOD-10).
     */
    public static int getCurrentStageIndex(LostTalesQuestDefinition quest,
            LostTalesQuestProgress progress) {
        if (quest == null || progress == null) {
            return -1;
        }
        int index = LostTalesQuestRules.stageIndexOf(quest,
                progress.getStageId());
        return index < quest.getStages().size() ? index : -1;
    }

    public static LostTalesQuestStageDefinition getCurrentStage(
            LostTalesQuestDefinition quest, LostTalesQuestProgress progress) {
        int index = getCurrentStageIndex(quest, progress);
        return index < 0 ? null : quest.getStages().get(index);
    }

    /**
     * Includes every current-stage objective plus optional objectives from
     * earlier stages the quest went through (a branch not taken stays
     * quiet, QUEST-32). Future objectives never become live early.
     */
    public static List<LostTalesQuestObjectiveDefinition>
            getProgressibleObjectives(LostTalesQuestDefinition quest,
            LostTalesQuestProgress progress) {
        int currentStageIndex = getCurrentStageIndex(quest, progress);
        if (currentStageIndex < 0) {
            return Collections.emptyList();
        }
        ArrayList<LostTalesQuestObjectiveDefinition> objectives =
                new ArrayList<LostTalesQuestObjectiveDefinition>();
        List<String> path = progress.getStagePath();
        for (int stageIndex = 0; stageIndex <= currentStageIndex;
                stageIndex++) {
            LostTalesQuestStageDefinition stage = quest.getStages().get(stageIndex);
            boolean reached = stageIndex == currentStageIndex || path.isEmpty()
                    || path.contains(stage.getId());
            if (!reached) {
                continue;
            }
            for (LostTalesQuestObjectiveDefinition objective
                    : stage.getObjectives()) {
                if (stageIndex == currentStageIndex
                        || objective.isOptional()) {
                    objectives.add(objective);
                }
            }
        }
        return Collections.unmodifiableList(objectives);
    }

    public static boolean isComplete(LostTalesQuestProgress progress,
            LostTalesQuestObjectiveDefinition objective) {
        if (progress == null || objective == null) {
            return false;
        }
        return progress.getObjectiveProgress(objective.getId())
                >= LostTalesQuestObjectiveTextHelper
                .getObjectiveTargetCount(objective);
    }
}
