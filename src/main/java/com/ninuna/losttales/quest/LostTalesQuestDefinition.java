package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.missive.MissiveWords;
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
    private final Map<String, String> conversation;
    private final Map<String, String> world;
    private final List<LostTalesQuestStageDefinition> stages;
    private final boolean bundled;
    private final Map<String, String> words;
    private final Map<String, String> rules;

    /**
     * A quest as its file, its packet or its missive writes it. Its words
     * are found as {@code bundled} and {@code words} say
     * ({@link LostTalesQuestWords}): a bundled quest's by lang lines under
     * its id, a missive's by its template ids in {@code words}.
     * {@code rules} are the keys its file writes at the top level beside
     * its blocks, how it ends and how long it may take among them
     * ({@link LostTalesQuestRules}). A null block stands for an empty one.
     */
    public LostTalesQuestDefinition(String id, String title,
            String description, boolean repeatable, boolean restartable,
            String startMode, Map<String, String> prerequisites,
            Map<String, String> rewards, Map<String, String> interaction,
            Map<String, String> markers, Map<String, String> journalLog,
            Map<String, String> conversation, Map<String, String> world,
            List<LostTalesQuestStageDefinition> stages, boolean bundled,
            Map<String, String> words, Map<String, String> rules) {
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
        this.conversation = Collections.unmodifiableMap(new LinkedHashMap<String, String>(conversation == null ? Collections.<String, String>emptyMap() : conversation));
        this.world = Collections.unmodifiableMap(new LinkedHashMap<String, String>(world == null ? Collections.<String, String>emptyMap() : world));
        // A missive keeps only its letter's ids; its objectives read by its
        // kinds' missive lines wherever its quest is made or read again.
        this.stages = Collections.unmodifiableList(new ArrayList<LostTalesQuestStageDefinition>(stages == null ? Collections.<LostTalesQuestStageDefinition>emptyList()
                : !bundled && MissiveWords.isMissive(words)
                ? MissiveWords.wordedStages(stages) : stages));
        this.bundled = bundled;
        this.words = Collections.unmodifiableMap(new LinkedHashMap<String, String>(words == null ? Collections.<String, String>emptyMap() : words));
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<String, String>(rules == null ? Collections.<String, String>emptyMap() : rules));
    }

    /** The quest's rules as written; read them through {@link LostTalesQuestRules}. */
    public Map<String, String> getRules() {
        return this.rules;
    }

    /**
     * Whether the quest ships with the mod: its words are the lang lines
     * its id names, in each player's language ({@link LostTalesQuestWords}).
     * A quest read from the server's folder, sent over the wire or made in
     * the game is never bundled.
     */
    public boolean isBundled() {
        return this.bundled;
    }

    /**
     * The ids a quest made in the game is worded by, a missive's template
     * ids and target ({@link com.ninuna.losttales.quest.missive.MissiveWords});
     * empty for a quest whose words are written out.
     */
    public Map<String, String> getWords() {
        return this.words;
    }

    /**
     * What the quest's giver says and what the player may say back, as
     * written; empty for a quest nobody talks about. Read it through
     * {@link LostTalesQuestConversation}.
     */
    public Map<String, String> getConversation() {
        return this.conversation;
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

    /**
     * The title as written: an operator's words, or a bundled quest's
     * English line. Every place a player reads it asks
     * {@link LostTalesQuestWords#title} instead.
     */
    public String getTitle() {
        return this.title;
    }

    /** The description as written; read through {@link LostTalesQuestWords#description}. */
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
     * {@code history} may take it again at all: never taken, yes;
     * finished, only a repeatable quest; failed or abandoned, only a
     * restartable one. The server starts and offers by
     * {@link #mayTakeAgain(LostTalesQuestHistoryEntry, long)}, which adds a
     * repeatable quest's wait.
     */
    public boolean mayTakeAgain(LostTalesQuestHistoryEntry history) {
        if (history == null) {
            return true;
        }
        return history.isCompleted() ? this.repeatable : this.restartable;
    }

    /**
     * Whether the player may take it at world time {@code now}: as
     * {@link #mayTakeAgain(LostTalesQuestHistoryEntry)} says, and a
     * repeatable quest last completed only once its wait is past
     * ({@link #takenAgainAt}, QUEST-45).
     */
    public boolean mayTakeAgain(LostTalesQuestHistoryEntry history, long now) {
        return mayTakeAgain(history) && now >= takenAgainAt(history);
    }

    /**
     * When a repeatable quest may be taken again after the completion its
     * History line records: that completion's world time and its
     * {@code repeatAfter} (QUEST-45). 0 for a quest that does not wait:
     * never taken, last failed or abandoned, not repeatable, or with no
     * wait.
     */
    public long takenAgainAt(LostTalesQuestHistoryEntry history) {
        long wait = LostTalesQuestRules.repeatAfterTicks(this);
        if (history == null || !history.isCompleted() || !this.repeatable
                || wait <= 0L) {
            return 0L;
        }
        return history.getWorldTime() + wait;
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
     * the chat: only a quest a player may start by item or interaction,
     * and only by a way the server allows ({@code itemStarts},
     * {@code interactionStarts}). A locked quest starts only on its own
     * server path, a missive board's for one, so its card is never joined.
     */
    public boolean canStartFromShare(boolean itemStarts,
                                     boolean interactionStarts) {
        return itemStarts && canStartFromItem()
                || interactionStarts && canStartFromInteraction();
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
