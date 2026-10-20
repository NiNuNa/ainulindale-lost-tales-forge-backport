package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestRewardText;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * A missive's words in each player's language. A letter from a bundled
 * template keeps no sentences: the generator picks template ids (its
 * title, issuer, description and flavour line) and a target, and each game
 * words them from the lang file ({@code missive.losttales.*}). A letter
 * from a server's own template carries its title, issuer and flavour line
 * as its operator wrote them, shown as written in every language
 * ({@link LostTalesMissiveData#isWritten}, QUEST-42); its description and
 * objectives are still the lang file's. The target is a kind of creature,
 * a group or an item id; the description and each objective
 * ({@code Defeat 5 zombies.}) name it by the mod's own noun line where one
 * exists ({@code zombies}, {@code iron ingots}), else by the game's name
 * for it, else by its id.
 */
public final class MissiveWords {
    public static final String KEY_PREFIX = "missive.losttales.";
    private static final String OBJECTIVE_KEY_PREFIX = KEY_PREFIX + "objective.";
    /** The longest template id; a few words joined by underscores. */
    public static final int MAX_TEMPLATE_ID_BYTES = 64;
    /** The longest target: a creature kind, a group or an item id. */
    public static final int MAX_TARGET_BYTES = 128;
    /** The longest written title, issuer or flavour line, in UTF-8 bytes. */
    public static final int MAX_WRITTEN_BYTES = 256;

    /** The entries of a missive's quest definition that word it ({@link #wordsOf}). */
    public static final String TITLE = "missive.title";
    public static final String ISSUER = "missive.issuer";
    public static final String DESCRIPTION = "missive.description";
    public static final String FLAVOR = "missive.flavor";
    public static final String TARGET = "missive.target";
    /** Present, {@code true}, where the title, issuer and flavour are written words, not ids. */
    public static final String WRITTEN = "missive.written";

    private MissiveWords() {}

    /**
     * Whether {@code words} are words a server's template may write: not
     * blank, no control characters, at most {@link #MAX_WRITTEN_BYTES}.
     */
    public static boolean isWrittenWords(String words) {
        if (words == null || words.trim().length() == 0
                || com.ninuna.losttales.quest.LostTalesQuestIds.utf8Bytes(words)
                > MAX_WRITTEN_BYTES) {
            return false;
        }
        for (int index = 0; index < words.length(); index++) {
            if (Character.isISOControl(words.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code id} is a template id: lower-case letters, digits and underscores. */
    public static boolean isTemplateId(String id) {
        return id != null && id.length() > 0
                && id.length() <= MAX_TEMPLATE_ID_BYTES
                && id.matches("[a-z0-9_]+");
    }

    /**
     * Whether {@code target} is one a missive may name: letters, digits
     * and {@code _ . : -}, as a creature kind, a group or an item id is
     * written.
     */
    public static boolean isTarget(String target) {
        return target != null && target.length() > 0
                && target.length() <= MAX_TARGET_BYTES
                && target.matches("[A-Za-z0-9_.:\\-]+");
    }

    /**
     * Whether a quest definition's words are none, or a missive's as
     * {@link #wordsOf} writes them: a title, and each other entry empty or
     * well formed; the title, issuer and flavour template ids, or written
     * words where {@link #WRITTEN} says so. What a saved quest log and a
     * quest sent over the wire are held to.
     */
    public static boolean isWellFormed(Map<String, String> words) {
        if (words == null || words.isEmpty()) {
            return true;
        }
        boolean written = words.containsKey(WRITTEN);
        for (Map.Entry<String, String> entry : words.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (TARGET.equals(name)) {
                if (value.length() > 0 && !isTarget(value)) {
                    return false;
                }
            } else if (WRITTEN.equals(name)) {
                if (!"true".equals(value)) {
                    return false;
                }
            } else if (TITLE.equals(name)) {
                if (!isWord(value, written)) {
                    return false;
                }
            } else if (ISSUER.equals(name) || FLAVOR.equals(name)) {
                if (value.length() > 0 && !isWord(value, written)) {
                    return false;
                }
            } else if (DESCRIPTION.equals(name)) {
                if (value.length() > 0 && !isTemplateId(value)) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return words.containsKey(TITLE);
    }

    /** A title, issuer or flavour: written words for a letter written so, else a template id. */
    static boolean isWord(String value, boolean written) {
        return written ? isWrittenWords(value) : isTemplateId(value);
    }

    /** Whether a quest definition's words are a missive's. */
    public static boolean isMissive(Map<String, String> words) {
        return words != null && words.containsKey(TITLE);
    }

    /** Whether a missive quest's title, issuer and flavour are written words (QUEST-42). */
    public static boolean isWritten(Map<String, String> words) {
        return "true".equals(entry(words, WRITTEN));
    }

    /** The entries a missive's quest is worded by. */
    public static Map<String, String> wordsOf(LostTalesMissiveData missive) {
        Map<String, String> words = new LinkedHashMap<String, String>();
        words.put(TITLE, missive.getTitleId());
        words.put(ISSUER, missive.getIssuerId());
        words.put(DESCRIPTION, missive.getDescriptionId());
        words.put(FLAVOR, missive.getFlavorId());
        words.put(TARGET, missive.getTarget());
        if (missive.isWritten()) {
            words.put(WRITTEN, "true");
        }
        return words;
    }

    public static String titleKey(String titleId) {
        return KEY_PREFIX + "title." + titleId;
    }

    public static String issuerKey(String issuerId) {
        return KEY_PREFIX + "issuer." + issuerId;
    }

    public static String descriptionKey(String descriptionId) {
        return KEY_PREFIX + "description." + descriptionId;
    }

    public static String flavorKey(String flavorId) {
        return KEY_PREFIX + "flavor." + flavorId;
    }

    /** The line a missive's objective of {@code type} reads by: {@code Defeat %s %s.}. */
    public static String objectiveKey(String type) {
        return OBJECTIVE_KEY_PREFIX + (type == null ? "" : type.trim());
    }

    /** Whether {@code key} is a missive objective's line. */
    public static boolean isObjectiveKey(String key) {
        return key != null && key.startsWith(OBJECTIVE_KEY_PREFIX);
    }

    /**
     * A missive's objective in words, its count and its target's noun in
     * the line its kind reads by: {@code Defeat 5 zombies.}; empty where
     * the game has no such line.
     */
    public static String objectiveLine(LostTalesQuestObjectiveDefinition objective) {
        String key = objective == null ? "" : objective.getDescriptionKey();
        if (!isObjectiveKey(key) || !StatCollector.canTranslate(key)) {
            return "";
        }
        return StatCollector.translateToLocalFormatted(key,
                String.valueOf(LostTalesQuestObjectiveTextHelper
                        .getObjectiveTargetCount(objective)),
                targetNoun(target(objective.getParams())));
    }

    /**
     * The objectives of a missive's quest, each worded by its kind's
     * missive line; the letter's ids are all a missive keeps, so these
     * lines are found again wherever its quest is read.
     */
    public static List<LostTalesQuestStageDefinition> wordedStages(
            List<LostTalesQuestStageDefinition> stages) {
        List<LostTalesQuestStageDefinition> worded =
                new ArrayList<LostTalesQuestStageDefinition>();
        if (stages == null) {
            return worded;
        }
        for (LostTalesQuestStageDefinition stage : stages) {
            if (stage == null) {
                continue;
            }
            List<LostTalesQuestObjectiveDefinition> objectives =
                    new ArrayList<LostTalesQuestObjectiveDefinition>();
            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                objectives.add(objective == null ? null
                        : new LostTalesQuestObjectiveDefinition(objective.getId(),
                                objective.getType(), objective.getDescription(),
                                objective.isOptional(), objective.getParams(),
                                objectiveKey(objective.getType())));
            }
            worded.add(new LostTalesQuestStageDefinition(stage.getId(), objectives,
                    stage.getRules()));
        }
        return worded;
    }

    /** What an objective names: its creature kind, else its group, else its item. */
    private static String target(Map<String, String> params) {
        for (String param : new String[] {"entity", "group", "item"}) {
            String value = params == null ? null : params.get(param);
            if (value != null && value.trim().length() > 0) {
                int comma = value.indexOf(',');
                return (comma >= 0 ? value.substring(0, comma) : value).trim();
            }
        }
        return "";
    }

    /** The noun line a target is named by: {@code missive.losttales.target.minecraft.iron_ingot}. */
    public static String targetKey(String target) {
        return KEY_PREFIX + "target." + (target == null ? ""
                : target.toLowerCase(Locale.ROOT).replace(':', '.'));
    }

    /** A letter's title: as written, or {@code Missive} for a title id the game has no line for. */
    public static String title(LostTalesMissiveData missive) {
        return missive == null ? "" : missive.isWritten() ? missive.getTitleId()
                : title(missive.getTitleId());
    }

    public static String title(String titleId) {
        return line(titleKey(titleId), KEY_PREFIX + "title");
    }

    /** Who wrote the letter, as written; empty for none or an issuer id the game has no line for. */
    public static String issuer(LostTalesMissiveData missive) {
        return missive == null ? "" : missive.isWritten() ? missive.getIssuerId()
                : optional(issuerKey(missive.getIssuerId()));
    }

    /** What the letter asks and why, its target named in the game's words. */
    public static String description(LostTalesMissiveData missive) {
        return missive == null ? ""
                : description(missive.getDescriptionId(), missive.getTarget());
    }

    public static String description(String descriptionId, String target) {
        String key = descriptionKey(descriptionId);
        return StatCollector.canTranslate(key)
                ? StatCollector.translateToLocalFormatted(key, targetNoun(target))
                : "";
    }

    /** The letter's flavour line, as written; empty for none or one the game has no line for. */
    public static String flavor(LostTalesMissiveData missive) {
        return missive == null ? "" : missive.isWritten() ? missive.getFlavorId()
                : optional(flavorKey(missive.getFlavorId()));
    }

    /** The words a letter is read by in the journal: its flavour line, else its description. */
    public static String journalLine(LostTalesMissiveData missive) {
        String flavor = flavor(missive);
        return flavor.length() > 0 ? flavor : description(missive);
    }

    /**
     * One of the letter's objectives in the words the journal gives the
     * same objective: its kind's missive line with its count and target.
     */
    public static String objective(LostTalesMissiveObjectiveData objective) {
        return objective == null ? ""
                : LostTalesQuestObjectiveTextHelper.describe(
                        new LostTalesQuestObjectiveDefinition(objective.getId(),
                                objective.getType(), "", objective.isOptional(),
                                objective.getParams(),
                                objectiveKey(objective.getType())));
    }

    /** A missive quest's title, from the entries its definition keeps. */
    public static String title(Map<String, String> words) {
        return isWritten(words) ? entry(words, TITLE) : title(entry(words, TITLE));
    }

    public static String description(Map<String, String> words) {
        return description(entry(words, DESCRIPTION), entry(words, TARGET));
    }

    public static String journalLine(Map<String, String> words) {
        String flavor = isWritten(words) ? entry(words, FLAVOR)
                : optional(flavorKey(entry(words, FLAVOR)));
        return flavor.length() > 0 ? flavor : description(words);
    }

    /**
     * A missive quest's title as an argument of a line the server sends:
     * a translation each reader's game words, or the words as written.
     */
    public static IChatComponent titleComponent(Map<String, String> words) {
        if (isWritten(words)) {
            return new ChatComponentText(entry(words, TITLE));
        }
        String key = titleKey(entry(words, TITLE));
        return StatCollector.canTranslate(key) ? new ChatComponentTranslation(key)
                : new ChatComponentTranslation(KEY_PREFIX + "title");
    }

    /**
     * A target as a description names it: the mod's noun line, else the
     * game's name for the creature or the item, else the id made readable.
     */
    public static String targetNoun(String target) {
        String id = target == null ? "" : target.trim();
        if (id.length() == 0) {
            return "";
        }
        String key = targetKey(id);
        if (StatCollector.canTranslate(key)) {
            return StatCollector.translateToLocal(key);
        }
        String entityKey = "entity." + id + ".name";
        if (StatCollector.canTranslate(entityKey)) {
            return StatCollector.translateToLocal(entityKey);
        }
        String item = LostTalesQuestRewardText.itemPhrase(id);
        return item.length() > 0 ? item : id;
    }

    private static String line(String key, String fallbackKey) {
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key)
                : StatCollector.translateToLocal(fallbackKey);
    }

    private static String optional(String key) {
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : "";
    }

    private static String entry(Map<String, String> words, String name) {
        String value = words == null ? null : words.get(name);
        return value == null ? "" : value;
    }
}
