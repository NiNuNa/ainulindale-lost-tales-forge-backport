package com.ninuna.losttales.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.LostTalesMetaData;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * Reads a quest file's JSON, and the quest index that lists the bundled
 * files. The server and the client read every quest file through here.
 * It only reads what is written; {@link LostTalesQuestDefinitionValidator}
 * says what is missing or wrong in what it read, and this says what it
 * could not read at all: a key no quest has, and a value of a shape its
 * key never takes (QUEST-04).
 */
public final class LostTalesQuestDefinitionJsonParser {
    /** The keys a quest file's top level may hold. */
    static final Set<String> QUEST_KEYS = keys("id", "title", "description",
            "repeatable", "restartable", "startMode", "prerequisites",
            "rewards", "interaction", "markers", "journalLog", "dialogue",
            "world", "stages", LostTalesQuestRules.RETURN_TO,
            LostTalesQuestRules.TIME_LIMIT, LostTalesQuestRules.FAIL_ON,
            LostTalesQuestRules.PODIUM, LostTalesQuestRules.RUMOUR);
    /** The rules a quest's top level may write: texts kept in its rules. */
    private static final String[] QUEST_RULES = { LostTalesQuestRules.RETURN_TO,
            LostTalesQuestRules.TIME_LIMIT, LostTalesQuestRules.FAIL_ON };
    /** The keys a stage may hold. */
    static final Set<String> STAGE_KEYS = keys("id", "objectives",
            LostTalesQuestRules.TIME_LIMIT, LostTalesQuestRules.NEXT,
            LostTalesQuestRules.ASK, LostTalesQuestRules.CHOICES);
    /** The rules a stage may write as texts. */
    private static final String[] STAGE_RULES = { LostTalesQuestRules.TIME_LIMIT,
            LostTalesQuestRules.NEXT, LostTalesQuestRules.ASK };
    /** The keys a stage's choice may hold. */
    static final Set<String> CHOICE_KEYS = keys("say", "next");
    /** The keys an objective may hold. */
    static final Set<String> OBJECTIVE_KEYS = keys("id", "type",
            "description", "optional", "params");

    private LostTalesQuestDefinitionJsonParser() {}

    /**
     * The files a quest index lists under {@code quests}, in its order.
     * Throws when the index is not an object with a {@code quests} list of
     * file names, or names an empty file, so a broken index is said rather
     * than read as an empty one.
     */
    public static List<String> parseQuestIndex(Reader reader) {
        JsonElement rootElement = reader == null ? null
                : new JsonParser().parse(reader);
        JsonElement questsElement = rootElement != null
                && rootElement.isJsonObject()
                ? rootElement.getAsJsonObject().get("quests") : null;
        if (questsElement == null || !questsElement.isJsonArray()) {
            throw new IllegalArgumentException(
                    "it holds no \"quests\" list");
        }
        List<String> files = new ArrayList<String>();
        JsonArray array = questsElement.getAsJsonArray();
        for (JsonElement entryElement : array) {
            String file = entryElement != null
                    && entryElement.isJsonPrimitive()
                    && entryElement.getAsJsonPrimitive().isString()
                    ? normalizeQuestFile(entryElement.getAsString()) : "";
            if (file.length() == 0) {
                throw new IllegalArgumentException("its entry "
                        + (files.size() + 1) + " names no file");
            }
            files.add(file);
        }
        return files;
    }

    public static LostTalesQuestDefinition parseQuest(Reader reader, String sourceFile) {
        return parseQuest(reader, sourceFile, null);
    }

    /**
     * The quest a file writes, or null for a file that is no quest.
     * {@code unread} is told, as the checker's warnings read, each thing
     * the file writes that nothing reads; null leaves them unsaid.
     */
    public static LostTalesQuestDefinition parseQuest(Reader reader,
            String sourceFile, List<String> unread) {
        if (reader == null) {
            return null;
        }

        JsonElement rootElement = new JsonParser().parse(reader);
        if (rootElement == null || !rootElement.isJsonObject()) {
            return null;
        }
        return parseQuest(rootElement.getAsJsonObject(), sourceFile,
                unread == null ? new ArrayList<String>() : unread);
    }

    public static String normalizeQuestFile(String questFile) {
        if (questFile == null) {
            return "";
        }
        String normalized = questFile.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() > 0 && !normalized.endsWith(".json")) {
            normalized = normalized + ".json";
        }
        return normalized;
    }

    public static String deriveQuestId(String sourceFile) {
        String normalized = normalizeQuestFile(sourceFile);
        int colonIndex = normalized.indexOf(':');
        if (colonIndex > 0) {
            normalized = normalized.substring(colonIndex + 1);
        }
        if (normalized.startsWith("quests/")) {
            normalized = normalized.substring("quests/".length());
        }
        if (normalized.endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - ".json".length());
        }
        return LostTalesMetaData.MOD_ID + ":" + normalized;
    }

    private static LostTalesQuestDefinition parseQuest(JsonObject object,
            String sourceFile, List<String> unread) {
        String id = getString(object, "id", deriveQuestId(sourceFile), null);
        Where quest = new Where(unread, "quest=" + id);
        quest.unknownKeys(object, QUEST_KEYS);
        getString(object, "id", "", quest);
        String title = getString(object, "title", id, quest);
        String description = getString(object, "description", "", quest);
        boolean repeatable = getBoolean(object, "repeatable", false, quest);
        boolean restartable = getBoolean(object, "restartable", false, quest);
        String startMode = getString(object, "startMode",
                LostTalesQuestDefinition.START_MODE_LOCKED, quest);
        Map<String, String> prerequisites = parseStringMap(object, "prerequisites", quest);
        Map<String, String> rewards = parseStringMap(object, "rewards", quest);
        Map<String, String> interaction = parseStringMap(object, "interaction", quest);
        Map<String, String> markers = parseStringMap(object, "markers", quest);
        Map<String, String> journalLog = parseStringMap(object, "journalLog", quest);
        Map<String, String> dialogue = parseStringMap(object, "dialogue", quest);
        Map<String, String> world = parseStringMap(object, "world", quest);
        List<LostTalesQuestStageDefinition> stages =
                parseStages(object.get("stages"), quest);
        Map<String, String> rules = parseRules(object, QUEST_RULES, quest);
        for (Map.Entry<String, String> reward
                : parseStringMap(object, LostTalesQuestRules.PODIUM, quest).entrySet()) {
            rules.put(LostTalesQuestRules.PODIUM_PREFIX + reward.getKey(),
                    reward.getValue());
        }
        for (Map.Entry<String, String> rumour
                : parseStringMap(object, LostTalesQuestRules.RUMOUR, quest).entrySet()) {
            rules.put(LostTalesQuestRules.RUMOUR_PREFIX + rumour.getKey(),
                    rumour.getValue());
        }

        if (id == null || id.length() == 0) {
            return null;
        }
        return new LostTalesQuestDefinition(id, title, description,
                repeatable, restartable, startMode, prerequisites, rewards,
                interaction, markers, journalLog, dialogue, world, stages,
                false, null, rules);
    }

    private static List<LostTalesQuestStageDefinition> parseStages(
            JsonElement element, Where quest) {
        List<LostTalesQuestStageDefinition> stages = new ArrayList<LostTalesQuestStageDefinition>();
        if (element == null || element.isJsonNull()) {
            return stages;
        }
        if (!element.isJsonArray()) {
            quest.say("writes 'stages' as something other than a list");
            return stages;
        }

        JsonArray array = element.getAsJsonArray();
        for (JsonElement stageElement : array) {
            if (stageElement == null || !stageElement.isJsonObject()) {
                quest.say("lists a stage that is not an object");
                continue;
            }
            JsonObject stageObject = stageElement.getAsJsonObject();
            String id = getString(stageObject, "id", "", quest);
            Where stage = quest.in("stage=" + id);
            stage.unknownKeys(stageObject, STAGE_KEYS);
            List<LostTalesQuestObjectiveDefinition> objectives =
                    parseObjectives(stageObject.get("objectives"), stage);
            Map<String, String> rules = parseRules(stageObject, STAGE_RULES, stage);
            parseChoices(stageObject.get(LostTalesQuestRules.CHOICES), rules, stage);
            stages.add(new LostTalesQuestStageDefinition(id, objectives, rules));
        }
        return stages;
    }

    private static List<LostTalesQuestObjectiveDefinition> parseObjectives(
            JsonElement element, Where stage) {
        List<LostTalesQuestObjectiveDefinition> objectives = new ArrayList<LostTalesQuestObjectiveDefinition>();
        if (element == null || element.isJsonNull()) {
            return objectives;
        }
        if (!element.isJsonArray()) {
            stage.say("writes 'objectives' as something other than a list");
            return objectives;
        }

        JsonArray array = element.getAsJsonArray();
        for (JsonElement objectiveElement : array) {
            if (objectiveElement == null || !objectiveElement.isJsonObject()) {
                stage.say("lists an objective that is not an object");
                continue;
            }
            JsonObject objectiveObject = objectiveElement.getAsJsonObject();
            String id = getString(objectiveObject, "id", "", stage);
            Where objective = stage.in("objective=" + id);
            objective.unknownKeys(objectiveObject, OBJECTIVE_KEYS);
            String type = getString(objectiveObject, "type", "", objective);
            String description = getString(objectiveObject, "description", "", objective);
            boolean optional = getBoolean(objectiveObject, "optional", false, objective);
            Map<String, String> params = parseStringMap(objectiveObject, "params", objective);
            objectives.add(new LostTalesQuestObjectiveDefinition(id, type, description, optional, params));
        }
        return objectives;
    }

    /**
     * A block of named texts, such as {@code rewards} or {@code params}:
     * each value a text, a number or true or false, read as its text.
     */
    private static Map<String, String> parseStringMap(JsonObject owner,
            String key, Where where) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        JsonElement element = owner.get(key);
        if (element == null || element.isJsonNull()) {
            return map;
        }
        if (!element.isJsonObject()) {
            where.say("writes '" + key + "' as something other than a block of names");
            return map;
        }

        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) continue;
            if (!value.isJsonPrimitive()) {
                where.say("writes '" + key + "." + entry.getKey()
                        + "' as a list or a block; it is a text");
                continue;
            }
            map.put(entry.getKey(), value.getAsString());
        }
        return map;
    }

    /**
     * A stage's choices, a list of {@code say} and {@code next}, kept
     * among its rules as {@code choice.<n>.say} and {@code choice.<n>.next}.
     * Past {@link LostTalesQuestRules#MAX_CHOICES} is said, not kept.
     */
    private static void parseChoices(JsonElement element, Map<String, String> rules,
                                     Where stage) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (!element.isJsonArray()) {
            stage.say("writes 'choices' as something other than a list");
            return;
        }
        int index = 0;
        for (JsonElement choiceElement : element.getAsJsonArray()) {
            if (choiceElement == null || !choiceElement.isJsonObject()) {
                stage.say("lists a choice that is not an object");
                continue;
            }
            if (index >= LostTalesQuestRules.MAX_CHOICES) {
                stage.say("offers more than " + LostTalesQuestRules.MAX_CHOICES
                        + " choices");
                return;
            }
            JsonObject choice = choiceElement.getAsJsonObject();
            Where where = stage.in("choice=" + (index + 1));
            where.unknownKeys(choice, CHOICE_KEYS);
            rules.put(LostTalesQuestRules.CHOICE_PREFIX + index + ".say",
                    getString(choice, "say", "", where));
            rules.put(LostTalesQuestRules.CHOICE_PREFIX + index + ".next",
                    getString(choice, "next", "", where));
            index++;
        }
    }

    /** The rules an object writes among its keys, each a text. */
    private static Map<String, String> parseRules(JsonObject object,
            String[] names, Where where) {
        Map<String, String> rules = new LinkedHashMap<String, String>();
        for (String name : names) {
            String value = getString(object, name, null, where);
            if (value != null) {
                rules.put(name, value);
            }
        }
        return rules;
    }

    private static String getString(JsonObject object, String key,
            String fallback, Where where) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive()) {
            if (where != null) {
                where.say("writes '" + key + "' as a list or a block; it is a text");
            }
            return fallback;
        }
        return element.getAsString();
    }

    /** {@code true} or {@code false}, as a JSON value or a text; anything else is said. */
    private static boolean getBoolean(JsonObject object, String key,
            boolean fallback, Where where) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        String value = element.isJsonPrimitive()
                ? element.getAsString().trim() : "";
        if ("true".equals(value) || "false".equals(value)) {
            return "true".equals(value);
        }
        where.say("writes '" + key + "' as " + (value.length() == 0
                ? "something else" : "'" + value + "'") + "; it is true or false");
        return fallback;
    }

    private static Set<String> keys(String... names) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(names)));
    }

    /** Where in a file a problem stands, and who is told of it. */
    private static final class Where {
        private final List<String> unread;
        private final String place;

        Where(List<String> unread, String place) {
            this.unread = unread;
            this.place = place;
        }

        Where in(String part) {
            return new Where(this.unread, this.place + " " + part);
        }

        void say(String problem) {
            this.unread.add(this.place + " " + problem);
        }

        void unknownKeys(JsonObject object, Set<String> known) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (!known.contains(entry.getKey())) {
                    say("writes '" + entry.getKey() + "', which nothing reads");
                }
            }
        }
    }
}
