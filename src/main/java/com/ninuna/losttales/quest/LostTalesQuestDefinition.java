package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
/**
 * Data-only quest definition compatible with the JSON files used by the modern branch.
 *
 * Minecraft 1.7.10 has no datapack/codec system, so these definitions are loaded with
 * Gson from bundled assets instead of from server datapacks.
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
     * Whether a party member may join this quest from a card shared in
     * the chat. A locked quest starts only on its own server path, a
     * missive board's for one, so its card is never joined.
     */
    public boolean canStartFromShare() {
        return !START_MODE_LOCKED.equals(this.startMode);
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
     * Optional display-only map marker hints, such as giver/objective/turnIn marker IDs.
     * Marker discovery/sync can be layered on later without changing quest progress NBT.
     */
    public Map<String, String> getMarkers() {
        return this.markers;
    }

    public Map<String, String> getJournalLog() {
        return this.journalLog;
    }

    public List<LostTalesQuestStageDefinition> getStages() {
        return this.stages;
    }

    public LostTalesQuestStageDefinition getFirstStage() {
        return this.stages.isEmpty() ? null : this.stages.get(0);
    }

    private static String normalizeStartMode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 0) {
            return START_MODE_LOCKED;
        }
        if ("npc".equals(normalized) || "entity".equals(normalized)) {
            return START_MODE_INTERACTION;
        }
        if ("manual".equals(normalized) || "command".equals(normalized)) {
            return START_MODE_LOCKED;
        }
        if (START_MODE_ITEM.equals(normalized) || START_MODE_INTERACTION.equals(normalized) || START_MODE_ANY.equals(normalized) || START_MODE_LOCKED.equals(normalized)) {
            return normalized;
        }
        return START_MODE_LOCKED;
    }
}
