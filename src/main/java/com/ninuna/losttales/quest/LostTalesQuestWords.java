package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.missive.MissiveWords;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * A quest's words in each player's language.
 *
 * <p>A bundled quest is worded by the lang lines its id names:
 * {@code losttales:tutorial/meet_nia} reads its title under
 * {@code quest.losttales.tutorial.meet_nia.title}, its description under
 * {@code .description}, an objective under {@code .objective.<id>}, a
 * journal line under {@code .journal.<stage id>} and a line of its
 * conversation under {@code .dialogue.<line>}. The quest files leave
 * those words out; the English lines fill the definition as it is read
 * ({@link #bundled}), so the server's own uses and a line the game has
 * no translation for still read in English. A missive is worded by its
 * template ids ({@link MissiveWords}). A quest from the server's folder,
 * a world quest's included, keeps the words its operator wrote, in every
 * language.</p>
 */
public final class LostTalesQuestWords {
    public static final String KEY_PREFIX = "quest.";

    private LostTalesQuestWords() {}

    /**
     * The start of every lang key a bundled quest is worded by:
     * {@code quest.losttales.tutorial.meet_nia} for
     * {@code losttales:tutorial/meet_nia}.
     */
    public static String keyPrefix(String questId) {
        String id = questId == null ? "" : questId.trim();
        return KEY_PREFIX + id.replace(':', '.').replace('/', '.');
    }

    public static String titleKey(String questId) {
        return keyPrefix(questId) + ".title";
    }

    public static String descriptionKey(String questId) {
        return keyPrefix(questId) + ".description";
    }

    public static String objectiveKey(String questId, String objectiveId) {
        return keyPrefix(questId) + ".objective." + objectiveId;
    }

    public static String journalKey(String questId, String stageId) {
        return keyPrefix(questId) + ".journal." + stageId;
    }

    public static String dialogueKey(String questId, String line) {
        return keyPrefix(questId) + ".dialogue." + line;
    }

    /**
     * A bundled quest as its file was read, worded by its lang lines: each
     * title, description, objective, journal and conversation line the file
     * leaves out takes the English line its key holds, and each objective
     * keeps the key of its line. An objective with neither words nor a
     * line is worded from its kind and target. {@code english} is the
     * mod's English lang file.
     */
    public static LostTalesQuestDefinition bundled(LostTalesQuestDefinition quest,
                                                   Map<String, String> english) {
        if (quest == null) {
            return null;
        }
        String id = quest.getId();
        List<LostTalesQuestStageDefinition> stages =
                new ArrayList<LostTalesQuestStageDefinition>();
        Map<String, String> journal =
                new LinkedHashMap<String, String>(quest.getJournalLog());
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            List<LostTalesQuestObjectiveDefinition> objectives =
                    new ArrayList<LostTalesQuestObjectiveDefinition>();
            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                String key = objectiveKey(id, objective.getId());
                boolean lined = english != null && english.containsKey(key);
                objectives.add(new LostTalesQuestObjectiveDefinition(
                        objective.getId(), objective.getType(),
                        written(objective.getDescription(), english, key),
                        objective.isOptional(), objective.getParams(),
                        lined ? key : ""));
            }
            stages.add(new LostTalesQuestStageDefinition(stage.getId(), objectives));
            fill(journal, stage.getId(), english, journalKey(id, stage.getId()));
        }
        Map<String, String> dialogue =
                new LinkedHashMap<String, String>(quest.getDialogue());
        for (String line : LostTalesQuestDialogue.LINES) {
            fill(dialogue, line, english, dialogueKey(id, line));
        }
        String title = quest.getTitle();
        if (title == null || title.trim().length() == 0 || title.equals(id)) {
            title = written("", english, titleKey(id));
        }
        return new LostTalesQuestDefinition(id,
                title.length() == 0 ? id : title,
                written(quest.getDescription(), english, descriptionKey(id)),
                quest.isRepeatable(), quest.isRestartable(),
                quest.getStartMode(), quest.getPrerequisites(),
                quest.getRewards(), quest.getInteraction(), quest.getMarkers(),
                journal, dialogue, quest.getWorld(), stages, true,
                quest.getWords());
    }

    /** A quest's title as this side reads it; its id where it has no words. */
    public static String title(LostTalesQuestDefinition quest) {
        if (quest == null) {
            return "";
        }
        if (quest.isBundled()) {
            return line(titleKey(quest.getId()), orId(quest.getTitle(), quest));
        }
        if (MissiveWords.isMissive(quest.getWords())) {
            return MissiveWords.title(quest.getWords());
        }
        return orId(quest.getTitle(), quest);
    }

    /** A quest's description as this side reads it; empty where it has none. */
    public static String description(LostTalesQuestDefinition quest) {
        if (quest == null) {
            return "";
        }
        if (quest.isBundled()) {
            return line(descriptionKey(quest.getId()), safe(quest.getDescription()));
        }
        if (MissiveWords.isMissive(quest.getWords())) {
            return MissiveWords.description(quest.getWords());
        }
        return safe(quest.getDescription());
    }

    /**
     * The journal line for the stage at {@code stageIndex} as this side
     * reads it ({@link LostTalesQuestDefinition#journalStageId}); a
     * missive's is its flavour line, else its description.
     */
    public static String journalLine(LostTalesQuestDefinition quest, int stageIndex) {
        if (quest == null) {
            return "";
        }
        if (MissiveWords.isMissive(quest.getWords())) {
            return MissiveWords.journalLine(quest.getWords());
        }
        String stageId = quest.journalStageId(stageIndex);
        if (stageId.length() == 0) {
            return "";
        }
        String written = safe(quest.getJournalLog().get(stageId));
        return quest.isBundled()
                ? line(journalKey(quest.getId(), stageId), written) : written;
    }

    /**
     * A quest's title as an argument of a chat line the server sends: a
     * translation each reader's game words for a bundled quest or a
     * missive, the operator's words as they are for any other.
     */
    public static IChatComponent titleComponent(LostTalesQuestDefinition quest) {
        if (quest == null) {
            return new ChatComponentText("");
        }
        if (quest.isBundled()) {
            return new ChatComponentTranslation(titleKey(quest.getId()));
        }
        if (MissiveWords.isMissive(quest.getWords())) {
            return MissiveWords.titleComponent(quest.getWords());
        }
        return new ChatComponentText(orId(quest.getTitle(), quest));
    }

    /**
     * Whether a bundled quest's id makes lang keys: lower-case letters,
     * digits and {@code _ . / :} only. Only the mod's own ids name keys;
     * an id an operator chose never does.
     */
    public static boolean isKeySafe(String questId) {
        return questId != null && questId.length() > 0
                && questId.toLowerCase(Locale.ROOT).equals(questId)
                && questId.matches("[a-z0-9_./:]+");
    }

    /** The line under {@code key} in this side's language, else {@code written}. */
    static String line(String key, String written) {
        return StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : written;
    }

    /** {@code value} where it is written, else the English line under {@code key}, else nothing. */
    private static String written(String value, Map<String, String> english, String key) {
        if (value != null && value.trim().length() > 0) {
            return value;
        }
        String line = english == null ? null : english.get(key);
        return line == null ? "" : line;
    }

    private static void fill(Map<String, String> lines, String name,
                             Map<String, String> english, String key) {
        String value = lines.get(name);
        if (value != null && value.trim().length() > 0) {
            return;
        }
        String line = english == null ? null : english.get(key);
        if (line != null && line.trim().length() > 0) {
            lines.put(name, line);
        }
    }

    private static String orId(String value, LostTalesQuestDefinition quest) {
        String text = safe(value);
        return text.length() > 0 ? text : safe(quest.getId());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
