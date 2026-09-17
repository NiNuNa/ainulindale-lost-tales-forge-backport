package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.compat.lotr.LotrClientQuestAdapter;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveSelection;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/** Builds the journal and HUD view without transferring quest ownership. */
public final class ClientQuestCatalog {
    private ClientQuestCatalog() {}

    public static List<ClientQuestEntry> getEntries(Minecraft minecraft) {
        ArrayList<ClientQuestEntry> entries = new ArrayList<ClientQuestEntry>();
        for (LostTalesQuestDefinition quest
                : LostTalesClientQuestDefinitionStore.getQuests()) {
            ClientQuestEntry entry = createLostTalesEntry(minecraft, quest);
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.addAll(LotrClientQuestAdapter.getEntries(minecraft));
        Collections.sort(entries, new Comparator<ClientQuestEntry>() {
            @Override
            public int compare(ClientQuestEntry left, ClientQuestEntry right) {
                int category = left.getCategory().compareToIgnoreCase(
                        right.getCategory());
                if (category != 0) {
                    return category;
                }
                int status = statusRank(left) - statusRank(right);
                if (status != 0) {
                    return status;
                }
                return left.getTitle().compareToIgnoreCase(right.getTitle());
            }

            private int statusRank(ClientQuestEntry entry) {
                if (entry.isActive()) {
                    return 0;
                }
                if (entry.isCompleted()) {
                    return 1;
                }
                return 2;
            }
        });
        return Collections.unmodifiableList(entries);
    }

    public static ClientQuestEntry find(Minecraft minecraft,
            String reference) {
        if (reference == null || reference.length() == 0) {
            return null;
        }
        for (ClientQuestEntry entry : getEntries(minecraft)) {
            if (reference.equals(entry.getReference())) {
                return entry;
            }
        }
        return null;
    }

    private static ClientQuestEntry createLostTalesEntry(Minecraft minecraft,
            LostTalesQuestDefinition quest) {
        if (quest == null || quest.getId() == null) {
            return null;
        }
        LostTalesQuestProgress progress =
                LostTalesClientQuestProgressStore.getActiveQuest(quest.getId());
        boolean completed = LostTalesClientQuestProgressStore
                .isQuestCompleted(quest.getId());
        LostTalesQuestHistoryEntry history =
                LostTalesClientQuestProgressStore.getQuestHistoryEntry(
                        quest.getId());
        if (progress == null && !completed && history == null) {
            return null;
        }
        ClientQuestEntry.Status status = completed
                ? ClientQuestEntry.Status.COMPLETED
                : history != null && history.isFailed()
                ? ClientQuestEntry.Status.FAILED
                : history != null && history.isAbandoned()
                ? ClientQuestEntry.Status.ABANDONED
                : ClientQuestEntry.Status.ACTIVE;
        ArrayList<ClientQuestEntry.Objective> objectives =
                new ArrayList<ClientQuestEntry.Objective>();
        if (progress != null || completed) {
            for (LostTalesQuestObjectiveDefinition objective
                    : visibleObjectives(quest, progress, completed)) {
                int target = LostTalesQuestObjectiveTextHelper
                        .getObjectiveTargetCount(objective);
                boolean completedAtEnd = completed
                        && (!objective.isOptional()
                        || history != null
                        && history.isOptionalObjectiveCompleted(
                        objective.getId()));
                int current = completedAtEnd ? target : progress == null ? 0
                        : progress.getObjectiveProgress(objective.getId());
                objectives.add(new ClientQuestEntry.Objective(
                        LostTalesQuestObjectiveTextHelper.buildObjectiveLine(
                                progress, objective, progress != null,
                                completedAtEnd, false, false), current, target,
                        current >= target, objective.isOptional()));
            }
        }
        long remainingTicks = -1L;
        if (progress != null && progress.hasTimeLimit()
                && minecraft != null && minecraft.theWorld != null) {
            remainingTicks = progress.getRemainingTicks(
                    minecraft.theWorld.getTotalWorldTime());
        }
        return new ClientQuestEntry(ClientQuestEntry.Source.LOST_TALES,
                quest.getId(), quest.getTitle(), "", category(quest),
                quest.getDescription(), status,
                LostTalesClientQuestProgressStore.isQuestPinned(quest.getId()),
                progress == null ? 0
                        : LostTalesQuestObjectiveSelection
                        .getCurrentStageIndex(quest, progress) + 1,
                quest.getStages().size(), remainingTicks, objectives,
                Collections.<String>emptyList(),
                Collections.<ClientQuestEntry.Target>emptyList(), quest,
                progress, history);
    }

    private static List<LostTalesQuestObjectiveDefinition> visibleObjectives(
            LostTalesQuestDefinition quest, LostTalesQuestProgress progress,
            boolean completed) {
        ArrayList<LostTalesQuestObjectiveDefinition> result =
                new ArrayList<LostTalesQuestObjectiveDefinition>();
        if (quest == null || quest.getStages().isEmpty()) {
            return result;
        }
        if (completed) {
            for (LostTalesQuestStageDefinition stage : quest.getStages()) {
                result.addAll(stage.getObjectives());
            }
            return result;
        }
        result.addAll(LostTalesQuestObjectiveSelection
                .getProgressibleObjectives(quest, progress));
        return result;
    }

    private static String category(LostTalesQuestDefinition quest) {
        String id = quest == null || quest.getId() == null
                ? "" : quest.getId();
        int colon = id.indexOf(':');
        String path = (colon >= 0 ? id.substring(colon + 1) : id)
                .toLowerCase(Locale.ENGLISH);
        if (path.startsWith("tutorial/")) {
            return "Tutorials";
        }
        if (path.startsWith("faction/")) {
            return "Factions";
        }
        if (path.startsWith("missive/")) {
            return "Missives";
        }
        if (path.startsWith("path/")) {
            return "Paths";
        }
        if (path.startsWith("regional/") || path.startsWith("region/")) {
            return "Regional";
        }
        if (path.startsWith("story/") || path.startsWith("main/")) {
            return "Main Story";
        }
        return "Miscellaneous";
    }
}
