package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.compat.lotr.LotrClientQuestAdapter;
import com.ninuna.losttales.quest.LostTalesQuestCategory;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveSelection;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestRewardText;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import com.ninuna.losttales.quest.world.WorldQuestRules;
import com.ninuna.losttales.quest.world.WorldQuestView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * Builds the journal and HUD view without transferring quest ownership.
 *
 * <p>The tracker HUD, the compass and the world markers each ask for the
 * list while a frame is being drawn, and a well-travelled character
 * carries hundreds of finished LOTR quests, so the list is built once a
 * tick and handed out unchanged for the rest of it. Nothing it is built
 * from — the definition and progress stores, LOTR's player data — moves
 * between ticks, since every packet that changes them is queued onto the
 * client thread.</p>
 */
public final class ClientQuestCatalog {
    private static List<ClientQuestEntry> cached = Collections.emptyList();
    private static Object cachedPlayer;
    private static int cachedTick = -1;

    private ClientQuestCatalog() {}

    /**
     * Every quest entry, from both systems, in journal order. Built at
     * most once per client tick; see the class comment.
     */
    public static synchronized List<ClientQuestEntry> getEntries(
            Minecraft minecraft) {
        Object player = minecraft == null ? null : minecraft.thePlayer;
        if (player == null) {
            forget();
            return Collections.emptyList();
        }
        int tick = minecraft.thePlayer.ticksExisted;
        if (player == cachedPlayer && tick == cachedTick) {
            return cached;
        }
        cached = buildEntries(minecraft);
        cachedPlayer = player;
        cachedTick = tick;
        return cached;
    }

    /** Drops the tick's list; called as a session ends. */
    public static synchronized void forget() {
        cached = Collections.emptyList();
        cachedPlayer = null;
        cachedTick = -1;
    }

    private static List<ClientQuestEntry> buildEntries(Minecraft minecraft) {
        ArrayList<ClientQuestEntry> entries = new ArrayList<ClientQuestEntry>();
        for (LostTalesQuestDefinition quest
                : LostTalesClientQuestDefinitionStore.getQuests()) {
            ClientQuestEntry entry = quest != null && quest.isWorldQuest()
                    ? createWorldEntry(minecraft, quest)
                    : createLostTalesEntry(minecraft, quest);
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
                                completedAtEnd), current, target,
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
                quest.getId(), quest.getTitle(), "",
                categoryName(LostTalesQuestCategory.of(quest)),
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

    /**
     * A world quest's entry while the world keeps a run of it: the whole
     * server's counts as its objectives, how many helped and this
     * player's part under its title, its time left while it runs, and on
     * the tracker for as long as it runs.
     */
    private static ClientQuestEntry createWorldEntry(Minecraft minecraft,
            LostTalesQuestDefinition quest) {
        WorldQuestView view = ClientWorldQuests.view(quest.getId());
        if (view == null) {
            return null;
        }
        ClientQuestEntry.Status status;
        switch (view.getState()) {
            case RUNNING:
                status = ClientQuestEntry.Status.ACTIVE;
                break;
            case COMPLETED:
                status = ClientQuestEntry.Status.COMPLETED;
                break;
            case FAILED:
                status = ClientQuestEntry.Status.FAILED;
                break;
            default:
                status = ClientQuestEntry.Status.ABANDONED;
                break;
        }
        ArrayList<ClientQuestEntry.Objective> objectives =
                new ArrayList<ClientQuestEntry.Objective>();
        for (LostTalesQuestObjectiveDefinition objective
                : WorldQuestRules.objectives(quest)) {
            int goal = WorldQuestRules.goal(objective);
            int count = Math.min(goal, view.getCount(objective.getId()));
            String description = objective.getDescription() == null
                    || objective.getDescription().length() == 0
                    ? objective.getId() : objective.getDescription();
            objectives.add(new ClientQuestEntry.Objective(
                    StatCollector.translateToLocalFormatted(
                            "gui.losttales.quest.world.objective",
                            description, Integer.valueOf(count),
                            Integer.valueOf(goal)),
                    count, goal, count >= goal, false));
        }
        long remainingTicks = -1L;
        if (view.isRunning() && minecraft != null
                && minecraft.theWorld != null) {
            remainingTicks = Math.max(0L, view.getEndsAt()
                    - minecraft.theWorld.getTotalWorldTime());
        }
        String subtitle = StatCollector.translateToLocalFormatted(
                "gui.losttales.quest.world.part",
                Integer.valueOf(view.getHelpers()),
                Integer.valueOf(view.getMine()),
                Integer.valueOf(WorldQuestRules.least(quest)));
        List<String> rewards = new ArrayList<String>();
        String summary = LostTalesQuestRewardText.summary(quest.getRewards());
        if (summary.length() > 0) {
            rewards.add(summary);
        }
        return new ClientQuestEntry(ClientQuestEntry.Source.WORLD,
                quest.getId(), quest.getTitle(), subtitle,
                categoryName(LostTalesQuestCategory.of(quest)),
                quest.getDescription(), status, view.isRunning(), 1, 1,
                remainingTicks, objectives, rewards,
                Collections.<ClientQuestEntry.Target>emptyList(), null, null);
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

    /**
     * A category's name, from the lang file: the word a category is filed
     * under ({@link LostTalesQuestCategory}) names its key. An entry carries
     * this name, and so does a card a player shares from it; a card the
     * server makes carries the word. A word no key has stands as it is.
     */
    public static String categoryName(String category) {
        String key = LostTalesQuestCategory.key(category);
        return StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : category;
    }
}
