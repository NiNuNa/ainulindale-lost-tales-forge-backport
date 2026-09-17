package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LostTalesQuestObjectiveSelectionTest {

    @Test
    public void currentStageAndEarlierOptionalsRemainProgressible() {
        LostTalesQuestObjectiveDefinition firstRequired = objective(
                "first_required", false);
        LostTalesQuestObjectiveDefinition firstOptional = objective(
                "first_optional", true);
        LostTalesQuestObjectiveDefinition secondRequired = objective(
                "second_required", false);
        LostTalesQuestObjectiveDefinition secondOptional = objective(
                "second_optional", true);
        LostTalesQuestDefinition quest = quest(firstRequired, firstOptional,
                secondRequired, secondOptional);

        LostTalesQuestProgress progress = new LostTalesQuestProgress(
                quest.getId(), 99, "second");
        progress.setObjectiveProgress("first_optional", 2);

        List<LostTalesQuestObjectiveDefinition> selected =
                LostTalesQuestObjectiveSelection.getProgressibleObjectives(
                        quest, progress);

        assertEquals(Arrays.asList("first_optional", "second_required",
                "second_optional"), ids(selected));
        assertEquals(1, LostTalesQuestObjectiveSelection
                .getCurrentStageIndex(quest, progress));
        assertTrue(LostTalesQuestObjectiveSelection.isComplete(progress,
                firstOptional));
    }

    @Test
    public void stageIdWinsAndAnUnknownStageFallsBackWithinBounds() {
        LostTalesQuestDefinition quest = quest(objective("a", false),
                objective("b", true), objective("c", false),
                objective("d", true));
        assertEquals(0, LostTalesQuestObjectiveSelection
                .getCurrentStageIndex(quest,
                        new LostTalesQuestProgress(quest.getId(), 1,
                                "first")));
        assertEquals(1, LostTalesQuestObjectiveSelection
                .getCurrentStageIndex(quest,
                        new LostTalesQuestProgress(quest.getId(), 99,
                                "unknown")));
    }

    private static LostTalesQuestDefinition quest(
            LostTalesQuestObjectiveDefinition firstRequired,
            LostTalesQuestObjectiveDefinition firstOptional,
            LostTalesQuestObjectiveDefinition secondRequired,
            LostTalesQuestObjectiveDefinition secondOptional) {
        return new LostTalesQuestDefinition("losttales:selection", "Quest",
                "Description", false,
                LostTalesQuestDefinition.START_MODE_LOCKED,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Arrays.asList(
                        new LostTalesQuestStageDefinition("first",
                                Arrays.asList(firstRequired, firstOptional)),
                        new LostTalesQuestStageDefinition("second",
                                Arrays.asList(secondRequired,
                                        secondOptional))));
    }

    private static LostTalesQuestObjectiveDefinition objective(String id,
            boolean optional) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("count", "2");
        return new LostTalesQuestObjectiveDefinition(id, "gather", id,
                optional, params);
    }

    private static List<String> ids(
            List<LostTalesQuestObjectiveDefinition> objectives) {
        java.util.ArrayList<String> ids = new java.util.ArrayList<String>();
        for (LostTalesQuestObjectiveDefinition objective : objectives) {
            ids.add(objective.getId());
        }
        return ids;
    }
}
