package com.ninuna.losttales.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.LostTalesMetaData;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Reads a quest file's JSON, and the quest index that lists the bundled
 * files. The server and the client read every quest file through here.
 * It only reads what is written; {@link LostTalesQuestDefinitionValidator}
 * says what is missing or wrong.
 */
public final class LostTalesQuestDefinitionJsonParser {

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
        if (reader == null) {
            return null;
        }

        JsonElement rootElement = new JsonParser().parse(reader);
        if (rootElement == null || !rootElement.isJsonObject()) {
            return null;
        }
        return parseQuest(rootElement.getAsJsonObject(), sourceFile);
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

    private static LostTalesQuestDefinition parseQuest(JsonObject object, String sourceFile) {
        String id = getString(object, "id", deriveQuestId(sourceFile));
        String title = getString(object, "title", id);
        String description = getString(object, "description", "");
        boolean repeatable = getBoolean(object, "repeatable", false);
        boolean restartable = getBoolean(object, "restartable", false);
        String startMode = getString(object, "startMode", LostTalesQuestDefinition.START_MODE_LOCKED);
        Map<String, String> prerequisites = parseStringMap(object.get("prerequisites"));
        Map<String, String> rewards = parseStringMap(object.get("rewards"));
        Map<String, String> interaction = parseStringMap(object.get("interaction"));
        Map<String, String> markers = parseStringMap(object.get("markers"));
        Map<String, String> journalLog = parseStringMap(object.get("journalLog"));
        Map<String, String> dialogue = parseStringMap(object.get("dialogue"));
        Map<String, String> world = parseStringMap(object.get("world"));
        List<LostTalesQuestStageDefinition> stages = parseStages(object.get("stages"));

        if (id == null || id.length() == 0) {
            return null;
        }
        return new LostTalesQuestDefinition(id, title, description,
                repeatable, restartable, startMode, prerequisites, rewards,
                interaction, markers, journalLog, dialogue, world, stages);
    }

    private static List<LostTalesQuestStageDefinition> parseStages(JsonElement element) {
        List<LostTalesQuestStageDefinition> stages = new ArrayList<LostTalesQuestStageDefinition>();
        if (element == null || !element.isJsonArray()) {
            return stages;
        }

        JsonArray array = element.getAsJsonArray();
        for (JsonElement stageElement : array) {
            if (stageElement == null || !stageElement.isJsonObject()) continue;
            JsonObject stageObject = stageElement.getAsJsonObject();
            String id = getString(stageObject, "id", "");
            List<LostTalesQuestObjectiveDefinition> objectives = parseObjectives(stageObject.get("objectives"));
            stages.add(new LostTalesQuestStageDefinition(id, objectives));
        }
        return stages;
    }

    private static List<LostTalesQuestObjectiveDefinition> parseObjectives(JsonElement element) {
        List<LostTalesQuestObjectiveDefinition> objectives = new ArrayList<LostTalesQuestObjectiveDefinition>();
        if (element == null || !element.isJsonArray()) {
            return objectives;
        }

        JsonArray array = element.getAsJsonArray();
        for (JsonElement objectiveElement : array) {
            if (objectiveElement == null || !objectiveElement.isJsonObject()) continue;
            JsonObject objectiveObject = objectiveElement.getAsJsonObject();
            String id = getString(objectiveObject, "id", "");
            String type = getString(objectiveObject, "type", "");
            String description = getString(objectiveObject, "description", "");
            boolean optional = getBoolean(objectiveObject, "optional", false);
            Map<String, String> params = parseStringMap(objectiveObject.get("params"));
            objectives.add(new LostTalesQuestObjectiveDefinition(id, type, description, optional, params));
        }
        return objectives;
    }

    private static Map<String, String> parseStringMap(JsonElement element) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (element == null || !element.isJsonObject()) {
            return map;
        }

        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) continue;
            try {
                map.put(entry.getKey(), value.getAsString());
            } catch (RuntimeException ignored) {}
        }
        return map;
    }

    private static String getString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
