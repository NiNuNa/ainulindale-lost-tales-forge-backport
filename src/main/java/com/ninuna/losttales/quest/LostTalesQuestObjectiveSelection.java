package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared rules for objectives that remain live at the player's current stage. */
public final class LostTalesQuestObjectiveSelection {
    private LostTalesQuestObjectiveSelection() {}

    public static int getCurrentStageIndex(LostTalesQuestDefinition quest,
            LostTalesQuestProgress progress) {
        if (quest == null || progress == null || quest.getStages().isEmpty()) {
            return -1;
        }
        List<LostTalesQuestStageDefinition> stages = quest.getStages();
        String stageId = progress.getStageId();
        if (stageId != null && stageId.length() > 0) {
            for (int index = 0; index < stages.size(); index++) {
                if (stageId.equals(stages.get(index).getId())) {
                    return index;
                }
            }
        }
        return Math.max(0, Math.min(progress.getStageIndex(),
                stages.size() - 1));
    }

    public static LostTalesQuestStageDefinition getCurrentStage(
            LostTalesQuestDefinition quest, LostTalesQuestProgress progress) {
        int index = getCurrentStageIndex(quest, progress);
        return index < 0 ? null : quest.getStages().get(index);
    }

    /**
     * Includes every current-stage objective plus optional objectives from
     * earlier stages. Future objectives never become live early.
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
        for (int stageIndex = 0; stageIndex <= currentStageIndex;
                stageIndex++) {
            for (LostTalesQuestObjectiveDefinition objective
                    : quest.getStages().get(stageIndex).getObjectives()) {
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
