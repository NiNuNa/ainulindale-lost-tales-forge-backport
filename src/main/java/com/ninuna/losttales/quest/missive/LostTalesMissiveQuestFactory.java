package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts generated missive data into the existing Lost Tales quest definition model. */
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
        Map<String, String> journalLog = createJournalLog(missive);
        Map<String, String> prerequisites = Collections.emptyMap();
        Map<String, String> interaction = Collections.emptyMap();
        Map<String, String> markers = createMarkerHints(missive);

        return new LostTalesQuestDefinition(
                missive.getQuestId(),
                missive.getTitle(),
                buildDescription(missive),
                missive.isRepeatable(),
                LostTalesQuestDefinition.START_MODE_LOCKED,
                prerequisites,
                missive.getRewardData().getRewards(),
                interaction,
                markers,
                journalLog,
                stages
        );
    }

    private static Map<String, String> createJournalLog(LostTalesMissiveData missive) {
        LinkedHashMap<String, String> journalLog = new LinkedHashMap<String, String>();
        String entry = missive.getFlavorText().length() > 0 ? missive.getFlavorText() : missive.getDescription();
        if (entry.length() == 0) {
            entry = "I accepted a missive from " + getIssuerOrFallback(missive) + ".";
        }
        journalLog.put(DEFAULT_STAGE_ID, entry);
        return journalLog;
    }

    private static Map<String, String> createMarkerHints(LostTalesMissiveData missive) {
        LinkedHashMap<String, String> markers = new LinkedHashMap<String, String>();
        String marker = missive.getGenerationContext().get("marker");
        if (marker == null || marker.length() == 0) {
            marker = missive.getGenerationContext().get("boardMarker");
        }
        if (marker != null && marker.length() > 0) {
            markers.put("giver", marker);
        }
        return markers;
    }

    private static String buildDescription(LostTalesMissiveData missive) {
        String description = missive.getDescription();
        String issuer = getIssuerOrFallback(missive);
        if (issuer.length() > 0 && description.indexOf(issuer) < 0) {
            return description.length() == 0 ? "Issued by " + issuer + "." : description + "\n\nIssued by " + issuer + ".";
        }
        return description;
    }

    private static String getIssuerOrFallback(LostTalesMissiveData missive) {
        return missive.getIssuer().length() == 0 ? "a local notice board" : missive.getIssuer();
    }
}
