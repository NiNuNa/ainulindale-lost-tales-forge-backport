package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * A quest as its file writes it: the quests bundled with the mod, those a
 * server writes in its own folder, and the missives the game makes. Read
 * from JSON by {@link LostTalesQuestDefinitionJsonParser}, checked by
 * {@link LostTalesQuestDefinitionValidator}.
 */
public final class LostTalesQuestDefinition {
    public static final String START_MODE_ITEM = "item";
    public static final String START_MODE_INTERACTION = "interaction";
    public static final String START_MODE_ANY = "any";
    public static final String START_MODE_LOCKED = "locked";

    private final String id;
    private final String title;
    private final String description;
    private final boolean repeatable;
    private final boolean restartable;
    private final String startMode;
    private final Map<String, String> prerequisites;
    private final Map<String, String> rewards;
    private final Map<String, String> interaction;
    private final Map<String, String> markers;
    private final Map<String, String> journalLog;
    private final Map<String, String> dialogue;
    private final Map<String, String> world;
    private final List<LostTalesQuestStageDefinition> stages;

    public LostTalesQuestDefinition(String id, String title, String description, boolean repeatable, String startMode, Map<String, String> prerequisites, Map<String, String> rewards, Map<String, String> interaction, Map<String, String> markers, Map<String, String> journalLog, List<LostTalesQuestStageDefinition> stages) {
        this(id, title, description, repeatable, repeatable, startMode,
                prerequisites, rewards, interaction, markers, journalLog,
                stages);
    }

    public LostTalesQuestDefinition(String id, String title,
            String description, boolean repeatable, boolean restartable,
            String startMode, Map<String, String> prerequisites,
            Map<String, String> rewards, Map<String, String> interaction,
            Map<String, String> markers, Map<String, String> journalLog,
            List<LostTalesQuestStageDefinition> stages) {
        this(id, title, description, repeatable, restartable, startMode,
                prerequisites, rewards, interaction, markers, journalLog,
                Collections.<String, String>emptyMap(), stages);
    }

    public LostTalesQuestDefinition(String id, String title,
            String description, boolean repeatable, boolean restartable,
            String startMode, Map<String, String> prerequisites,
            Map<String, String> rewards, Map<String, String> interaction,
            Map<String, String> markers, Map<String, String> journalLog,
            Map<String, String> dialogue,
            List<LostTalesQuestStageDefinition> stages) {
        this(id, title, description, repeatable, restartable, startMode,
                prerequisites, rewards, interaction, markers, journalLog,
                dialogue, Collections.<String, String>emptyMap(), stages);
    }

    public LostTalesQuestDefinition(String id, String title,
            String description, boolean repeatable, boolean restartable,
            String startMode, Map<String, String> prerequisites,
            Map<String, String> rewards, Map<String, String> interaction,
            Map<String, String> markers, Map<String, String> journalLog,
            Map<String, String> dialogue, Map<String, String> world,
            List<LostTalesQuestStageDefinition> stages) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.repeatable = repeatable;
        this.restartable = restartable;
        this.startMode = normalizeStartMode(startMode);
        this.prerequisites = Collections.unmodifiableMap(new LinkedHashMap<String, String>(prerequisites == null ? Collections.<String, String>emptyMap() : prerequisites));
        this.rewards = Collections.unmodifiableMap(new LinkedHashMap<String, String>(rewards == null ? Collections.<String, String>emptyMap() : rewards));
        this.interaction = Collections.unmodifiableMap(new LinkedHashMap<String, String>(interaction == null ? Collections.<String, String>emptyMap() : interaction));
        this.markers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(markers == null ? Collections.<String, String>emptyMap() : markers));
        this.journalLog = Collections.unmodifiableMap(new LinkedHashMap<String, String>(journalLog == null ? Collections.<String, String>emptyMap() : journalLog));
        this.dialogue = Collections.unmodifiableMap(new LinkedHashMap<String, String>(dialogue == null ? Collections.<String, String>emptyMap() : dialogue));
        this.world = Collections.unmodifiableMap(new LinkedHashMap<String, String>(world == null ? Collections.<String, String>emptyMap() : world));
        this.stages = Collections.unmodifiableList(new ArrayList<LostTalesQuestStageDefinition>(stages == null ? Collections.<LostTalesQuestStageDefinition>emptyList() : stages));
    }

    /**
     * What the quest's giver says and what the player may say back, as
     * written; empty for a quest nobody talks about. Read it through
     * {@link LostTalesQuestDialogue}.
     */
    public Map<String, String> getDialogue() {
        return this.dialogue;
    }

    /**
     * The quest's world block, as written: the {@code days} it runs for
     * and the {@code least} a helper must add to be paid. Empty for a
     * quest a player takes for themselves. Read it through
     * {@link com.ninuna.losttales.quest.world.WorldQuestRules}.
     */
    public Map<String, String> getWorld() {
        return this.world;
    }

    /**
     * Whether the whole server works on this quest together: one shared
     * count, started by an operator, never taken by a player.
     */
    public boolean isWorldQuest() {
        return !this.world.isEmpty();
    }

    public String getId() {
        return this.id;
    }

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public boolean isRepeatable() {
        return this.repeatable;
    }

    public boolean isRestartable() {
        return this.restartable;
    }

    /**
     * Whether a player whose last run of this quest ended as
     * {@code history} may take it now: never taken, yes; finished, only a
     * repeatable quest; failed or abandoned, only a restartable one. The
     * one rule the server starts by and a conversation offers by.
     */
    public boolean mayTakeAgain(LostTalesQuestHistoryEntry history) {
        if (history == null) {
            return true;
        }
        return history.isCompleted() ? this.repeatable : this.restartable;
    }

    public String getStartMode() {
        return this.startMode;
    }

    public boolean canStartFromItem() {
        return START_MODE_ITEM.equals(this.startMode) || START_MODE_ANY.equals(this.startMode);
    }

    public boolean canStartFromInteraction() {
        return START_MODE_INTERACTION.equals(this.startMode) || START_MODE_ANY.equals(this.startMode);
    }

    /**
     * Whether a fellowship member may join this quest from a card shared in
     * the chat: only a quest a player may start by item or interaction. A
     * locked quest starts only on its own server path, a missive board's
     * for one, so its card is never joined.
     */
    public boolean canStartFromShare() {
        return canStartFromItem() || canStartFromInteraction();
    }

    public Map<String, String> getPrerequisites() {
        return this.prerequisites;
    }

    public Map<String, String> getRewards() {
        return this.rewards;
    }

    public Map<String, String> getInteraction() {
        return this.interaction;
    }

    /**
     * The map markers the quest names, by role: its giver, its objective,
     * where it is handed in. They are revealed and shown; they never change
     * the quest's progress.
     */
    public Map<String, String> getMarkers() {
        return this.markers;
    }

    /** The journal's lines, each under the id of the stage it is written for. */
    public Map<String, String> getJournalLog() {
        return this.journalLog;
    }

    /**
     * The journal line for the stage at {@code stageIndex}: the one under
     * that stage's id, else the one under the latest stage before it that
     * has a line; empty where none has.
     */
    public String journalLine(int stageIndex) {
        for (int index = Math.min(stageIndex, this.stages.size() - 1);
                index >= 0; index--) {
            String line = this.journalLog.get(this.stages.get(index).getId());
            if (line != null && line.trim().length() > 0) {
                return line;
            }
        }
        return "";
    }

    public List<LostTalesQuestStageDefinition> getStages() {
        return this.stages;
    }

    public LostTalesQuestStageDefinition getFirstStage() {
        return this.stages.isEmpty() ? null : this.stages.get(0);
    }

    /**
     * Whether the start mode is one of the four words: {@code item},
     * {@code interaction}, {@code any} or {@code locked}. Any other word
     * starts the quest from nowhere but a command, and the checker says so.
     */
    public boolean isKnownStartMode() {
        return START_MODE_ITEM.equals(this.startMode)
                || START_MODE_INTERACTION.equals(this.startMode)
                || START_MODE_ANY.equals(this.startMode)
                || START_MODE_LOCKED.equals(this.startMode);
    }

    /** The start mode as written, trimmed; {@code locked} when none is. */
    private static String normalizeStartMode(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() == 0 ? START_MODE_LOCKED : trimmed;
    }
}
