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
 * {@code .description}, an objective under {@code .objective.<id>}, what
 * an optional one achieved adds under {@code .outcome.<id>}, a journal
 * line under {@code .journal.<stage id>} and a line of its conversation
 * under {@code .conversation.<line>}. The quest files leave
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

    public static String conversationKey(String questId, String line) {
        return keyPrefix(questId) + ".conversation." + line;
    }

    /** A bundled quest's line for what an optional objective achieved adds to its History (QUEST-15). */
    public static String outcomeKey(String questId, String objectiveId) {
        return keyPrefix(questId) + ".outcome." + objectiveId;
    }

    /**
     * What a finished quest's optional objectives add to its History line,
     * in this side's language: the {@code outcome} of each one whose id is
     * in {@code achieved}, in the quest's order, parted by spaces. Empty
     * where none achieved writes one.
     */
    public static String outcomeLine(LostTalesQuestDefinition quest,
                                      java.util.Collection<String> achieved) {
        StringBuilder outcome = new StringBuilder();
        if (quest == null || achieved == null || achieved.isEmpty()) {
            return "";
        }
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                if (!objective.isOptional() || !achieved.contains(objective.getId())) {
                    continue;
                }
                String written = LostTalesQuestParams.value(objective.getParams(),
                        "outcome");
                String line = quest.isBundled()
                        ? line(outcomeKey(quest.getId(), objective.getId()), written)
                        : written;
                if (line.trim().length() == 0) {
                    continue;
                }
                if (outcome.length() > 0) {
                    outcome.append(' ');
                }
                outcome.append(line.trim());
            }
        }
        return outcome.toString();
    }

    /** A bundled quest's rumour line (QUEST-20). */
    public static String rumourKey(String questId) {
        return keyPrefix(questId) + ".rumour";
    }

    /** The quest's rumour, as the teller says it, in this side's language; empty for none. */
    public static String rumourLine(LostTalesQuestDefinition quest) {
        if (quest == null) {
            return "";
        }
        String written = LostTalesQuestParams.value(quest.getRules(),
                LostTalesQuestRules.RUMOUR_PREFIX + "line");
        return quest.isBundled() ? line(rumourKey(quest.getId()), written) : written;
    }

    /** A bundled quest's line for what its giver asks at a stage's choice. */
    public static String askKey(String questId, String stageId) {
        return keyPrefix(questId) + ".ask." + stageId;
    }

    /** A bundled quest's line for one reply of a stage's choice, {@code index} from 0. */
    public static String choiceKey(String questId, String stageId, int index) {
        return keyPrefix(questId) + ".choice." + stageId + "." + index;
    }

    /** What the giver asks at the stage's choice, in this side's language (QUEST-32). */
    public static String askLine(LostTalesQuestDefinition quest,
                                 LostTalesQuestStageDefinition stage) {
        String written = LostTalesQuestParams.value(stage.getRules(),
                LostTalesQuestRules.ASK);
        return quest.isBundled() ? line(askKey(quest.getId(), stage.getId()), written)
                : written;
    }

    /** One reply of the stage's choice, in this side's language. */
    public static String choiceWords(LostTalesQuestDefinition quest,
            LostTalesQuestStageDefinition stage, int index) {
        String written = LostTalesQuestParams.value(stage.getRules(),
                LostTalesQuestRules.CHOICE_PREFIX + index + ".say");
        return quest.isBundled()
                ? line(choiceKey(quest.getId(), stage.getId(), index), written)
                : written;
    }

    /**
     * A bundled quest as its file was read, worded by its lang lines: each
     * title, description, objective, outcome, journal and conversation line
     * the file leaves out takes the English line its key holds, and each objective
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
                Map<String, String> params =
                        new LinkedHashMap<String, String>(objective.getParams());
                fill(params, "outcome", english, outcomeKey(id, objective.getId()));
                objectives.add(new LostTalesQuestObjectiveDefinition(
                        objective.getId(), objective.getType(),
                        written(objective.getDescription(), english, key),
                        objective.isOptional(), params,
                        lined ? key : ""));
            }
            Map<String, String> rules = new LinkedHashMap<String, String>(stage.getRules());
            fill(rules, LostTalesQuestRules.ASK, english, askKey(id, stage.getId()));
            for (int index = 0; index < LostTalesQuestRules.MAX_CHOICES
                    && rules.containsKey(LostTalesQuestRules.CHOICE_PREFIX + index + ".next");
                    index++) {
                fill(rules, LostTalesQuestRules.CHOICE_PREFIX + index + ".say", english,
                        choiceKey(id, stage.getId(), index));
            }
            stages.add(new LostTalesQuestStageDefinition(stage.getId(), objectives,
                    rules));
            fill(journal, stage.getId(), english, journalKey(id, stage.getId()));
        }
        Map<String, String> rules = new LinkedHashMap<String, String>(quest.getRules());
        if (!LostTalesQuestRules.rumour(quest).isEmpty()) {
            fill(rules, LostTalesQuestRules.RUMOUR_PREFIX + "line", english,
                    rumourKey(id));
        }
        Map<String, String> conversation =
                new LinkedHashMap<String, String>(quest.getConversation());
        for (String line : LostTalesQuestConversation.LINES) {
            fill(conversation, line, english, conversationKey(id, line));
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
                journal, conversation, quest.getWorld(), stages, true,
                quest.getWords(), rules);
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
     * The journal's tale of a quest as this side reads it (QUEST-30): the
     * line of each stage on {@code stagePath}, in the order it was
     * reached, a stage without a line left out. An empty path stands for
     * the first stage alone, or for every stage when {@code whole} says
     * the quest was finished. A missive's tale is its one line.
     */
    public static List<String> journalParagraphs(LostTalesQuestDefinition quest,
            List<String> stagePath, boolean whole) {
        List<String> paragraphs = new ArrayList<String>();
        if (quest == null) {
            return paragraphs;
        }
        if (MissiveWords.isMissive(quest.getWords())) {
            addParagraph(paragraphs, MissiveWords.journalLine(quest.getWords()));
            return paragraphs;
        }
        List<String> path = new ArrayList<String>();
        if (stagePath != null && !stagePath.isEmpty()) {
            path.addAll(stagePath);
        } else {
            for (LostTalesQuestStageDefinition stage : quest.getStages()) {
                path.add(stage.getId());
                if (!whole) {
                    break;
                }
            }
        }
        for (String stageId : path) {
            String written = safe(quest.getJournalLog().get(stageId));
            addParagraph(paragraphs, quest.isBundled()
                    ? line(journalKey(quest.getId(), stageId), written) : written);
        }
        return paragraphs;
    }

    private static void addParagraph(List<String> paragraphs, String paragraph) {
        if (paragraph != null && paragraph.trim().length() > 0) {
            paragraphs.add(paragraph);
        }
    }

    /**
     * What a quest waiting for its hand-in asks, in this side's language
     * (QUEST-31): <em>Return to Nia.</em>, or a missive's <em>Hand it in at
     * a missive board.</em>
     */
    public static String returnLine(LostTalesQuestDefinition quest,
            com.ninuna.losttales.quest.progress.LostTalesQuestProgress progress) {
        if (LostTalesQuestRules.returnsToBoard(quest)) {
            return StatCollector.translateToLocal("gui.losttales.quest.return.board");
        }
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.quest.return.giver", giverName(quest, progress));
    }

    /** What a quest waiting for a choice asks of the player (QUEST-32): <em>Talk to Nia and decide.</em> */
    public static String chooseLine(LostTalesQuestDefinition quest,
            com.ninuna.losttales.quest.progress.LostTalesQuestProgress progress) {
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.quest.choose.giver", giverName(quest, progress));
    }

    /**
     * Who gave a running quest, in this side's language: their own name
     * where they have one, else their kind's name, else the first kind the
     * quest's interaction names.
     */
    public static String giverName(LostTalesQuestDefinition quest,
            com.ninuna.losttales.quest.progress.LostTalesQuestProgress progress) {
        String own = progress == null ? "" : progress.getGiverName();
        if (own.length() > 0) {
            return own;
        }
        String kind = progress == null ? "" : progress.getGiverKind();
        if (kind.length() == 0 && quest != null) {
            kind = LostTalesQuestParams.value(quest.getInteraction(), "entity")
                    .split(",")[0].trim();
        }
        String key = "entity." + kind + ".name";
        if (StatCollector.canTranslate(key)) {
            return StatCollector.translateToLocal(key);
        }
        int dot = kind.lastIndexOf('.');
        return dot >= 0 ? kind.substring(dot + 1) : kind;
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
