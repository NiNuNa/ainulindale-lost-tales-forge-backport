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
 * Shared parser for bundled Lost Tales quest JSON files.
 *
 * The modern branch uses datapack reload listeners and codecs. Forge 1.7.10 does not
 * have those systems, so both the server registry and the client resource reload cache
 * use this small Gson parser instead.
 */
public final class LostTalesQuestDefinitionJsonParser {

    private LostTalesQuestDefinitionJsonParser() {}

    public static List<String> parseQuestIndex(Reader reader) {
        List<String> files = new ArrayList<String>();
        if (reader == null) {
            return files;
        }

        JsonElement rootElement = new JsonParser().parse(reader);
        if (rootElement == null || !rootElement.isJsonObject()) {
            return files;
        }

        JsonElement questsElement = rootElement.getAsJsonObject().get("quests");
        if (questsElement == null || !questsElement.isJsonArray()) {
            return files;
        }

        JsonArray array = questsElement.getAsJsonArray();
        for (JsonElement entryElement : array) {
            if (entryElement == null || entryElement.isJsonNull()) continue;
            try {
                String file = normalizeQuestFile(entryElement.getAsString());
                if (file.length() > 0) {
                    files.add(file);
                }
            } catch (RuntimeException ignored) {}
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
        if (normalized.startsWith("quest/")) {
            normalized = normalized.substring("quest/".length());
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
        String startMode = getString(object, "startMode", getString(object, "start", LostTalesQuestDefinition.START_MODE_JOURNAL));
        Map<String, String> prerequisites = parseStringMap(object.get("prerequisites"));
        prerequisites.putAll(parseStringMap(object.get("requirements")));
        Map<String, String> rewards = parseStringMap(object.get("rewards"));
        Map<String, String> interaction = parseStringMap(object.get("interaction"));
        interaction.putAll(parseStringMap(object.get("questGiver")));
        Map<String, String> markers = parseStringMap(object.get("markers"));
        markers.putAll(parseStringMap(object.get("mapMarkers")));
        Map<String, String> journalLog = parseStringMap(object.get("journalLog"));
        List<LostTalesQuestStageDefinition> stages = parseStages(object.get("stages"));

        if (id == null || id.length() == 0) {
            return null;
        }
        return new LostTalesQuestDefinition(id, title, description, repeatable, startMode, prerequisites, rewards, interaction, markers, journalLog, stages);
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
            String id = getString(stageObject, "id", String.valueOf(stages.size() + 1));
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
            String id = getString(objectiveObject, "id", "objective_" + (objectives.size() + 1));
            String type = getString(objectiveObject, "type", "unknown");
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
