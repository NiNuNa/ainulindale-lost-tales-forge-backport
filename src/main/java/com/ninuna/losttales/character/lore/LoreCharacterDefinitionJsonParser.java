package com.ninuna.losttales.character.lore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict parser for the intentionally small lore-character JSON format. */
public final class LoreCharacterDefinitionJsonParser {

    public static final int MAX_JSON_CHARACTERS = 16 * 1024;

    private static final Pattern IDENTIFIER = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<String> DEFINITION_KEYS = set(
            "dataVersion", "id", "name", "description", "appearance");
    private static final Set<String> APPEARANCE_KEYS = set(
            "raceId", "genderId", "modelId", "skinId");

    private LoreCharacterDefinitionJsonParser() {}

    public static ParseResult parseDefinition(Reader reader, String sourceName) {
        String source = normalizeSource(sourceName);
        List<String> errors = new ArrayList<String>();
        if (reader == null) {
            errors.add(source + ": reader is null");
            return new ParseResult(null, errors);
        }

        try {
            JsonElement root = new JsonParser().parse(readBounded(reader));
            if (root == null || !root.isJsonObject()) {
                throw invalid("root must be an object");
            }
            JsonObject object = root.getAsJsonObject();
            validateAllowedKeys(object, DEFINITION_KEYS, "root");
            if (object.entrySet().size() != DEFINITION_KEYS.size()) {
                throw invalid("root must contain exactly dataVersion, id, name, description, and appearance");
            }

            int dataVersion = requiredInteger(
                    object.get("dataVersion"), "dataVersion");
            if (dataVersion != LoreCharacterDefinition.CURRENT_DATA_VERSION) {
                throw invalid("unsupported dataVersion " + dataVersion);
            }
            String id = requiredIdentifier(object.get("id"), "id");
            String name = requiredString(object.get("name"), "name", 64);
            String description = string(
                    object.get("description"), "description", 512, true);
            LoreCharacterDefinition.Appearance appearance = parseAppearance(
                    object.get("appearance"), "appearance");
            return new ParseResult(
                    new LoreCharacterDefinition(
                            dataVersion, id, name, description, appearance), errors);
        } catch (RuntimeException e) {
            errors.add(source + ": " + safeMessage(e));
        } catch (IOException e) {
            errors.add(source + ": unable to read JSON: " + safeMessage(e));
        }
        return new ParseResult(null, errors);
    }

    public static List<String> parseIndex(Reader reader) throws IOException {
        if (reader == null) {
            throw invalid("index reader is null");
        }
        JsonElement root = new JsonParser().parse(readBounded(reader));
        if (root == null || !root.isJsonObject()) {
            throw invalid("index root must be an object");
        }
        JsonObject object = root.getAsJsonObject();
        validateAllowedKeys(object, set("files"), "index");
        JsonElement filesElement = object.get("files");
        if (filesElement == null || !filesElement.isJsonArray()) {
            throw invalid("index.files must be an array");
        }
        JsonArray files = filesElement.getAsJsonArray();
        if (files.size() > 256) {
            throw invalid("index contains too many files");
        }

        List<String> result = new ArrayList<String>();
        Set<String> unique = new LinkedHashSet<String>();
        for (JsonElement entry : files) {
            String path = requiredString(entry, "index.files entry", 160)
                    .replace('\\', '/');
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (!path.endsWith(".json")) {
                path = path + ".json";
            }
            if (!path.startsWith("lore_characters/")
                    || path.substring("lore_characters/".length()).indexOf('/') >= 0
                    || path.contains("..") || path.indexOf(':') >= 0) {
                throw invalid("lore-character files must be direct children of lore_characters: "
                        + path);
            }
            if (!unique.add(path)) {
                throw invalid("duplicate lore-character index path: " + path);
            }
            result.add(path);
        }
        return Collections.unmodifiableList(result);
    }

    private static String requiredIdentifier(JsonElement element, String path) {
        String value = requiredString(element, path, 160)
                .toLowerCase(Locale.ROOT);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw invalid(path + " must be a lowercase namespaced identifier");
        }
        return value;
    }

    private static String requiredString(JsonElement element, String path,
                                         int maxLength) {
        return string(element, path, maxLength, false);
    }

    private static String string(JsonElement element, String path,
                                 int maxLength, boolean allowBlank) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw invalid(path + " must be a string");
        }
        String value = element.getAsString().trim();
        if (!allowBlank && value.length() == 0) {
            throw invalid(path + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw invalid(path + " exceeds " + maxLength + " characters");
        }
        if (value.indexOf('\u00a7') >= 0 || containsUnsafeControl(value)) {
            throw invalid(path + " contains formatting or control characters");
        }
        return value;
    }

    private static LoreCharacterDefinition.Appearance parseAppearance(
            JsonElement element, String path) {
        if (element == null) {
            throw invalid(path + " must be an object or null");
        }
        if (element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            throw invalid(path + " must be an object or null");
        }
        JsonObject object = element.getAsJsonObject();
        validateAllowedKeys(object, APPEARANCE_KEYS, path);
        if (object.entrySet().size() != APPEARANCE_KEYS.size()) {
            throw invalid(path + " must contain exactly raceId, genderId, modelId, and skinId");
        }

        String raceId = requiredIdentifier(object.get("raceId"), path + ".raceId");
        String genderId = requiredIdentifier(object.get("genderId"), path + ".genderId");
        String modelId = requiredIdentifier(object.get("modelId"), path + ".modelId");
        String skinId = requiredIdentifier(object.get("skinId"), path + ".skinId");
        raceId = CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        genderId = CharacterGenderRegistry.normalizeIdentifier(genderId);
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (race == null) {
            throw invalid(path + ".raceId is not a supported playable race: " + raceId);
        }
        if (!race.isGenderAllowed(genderId)) {
            throw invalid(path + ".genderId is incompatible with race " + raceId);
        }
        if (!isCompatibleModel(raceId, modelId)) {
            throw invalid(path + ".modelId is incompatible with race " + raceId);
        }
        if (!CharacterSkinRegistry.isCompatible(skinId, raceId, genderId)) {
            throw invalid(path + ".skinId is incompatible with the fixed race/model");
        }
        return new LoreCharacterDefinition.Appearance(
                raceId, genderId, modelId, skinId);
    }

    private static boolean isCompatibleModel(String raceId, String modelId) {
        if (LoreCharacterDefinition.Appearance.RACE_DEFAULT_MODEL.equals(modelId)) {
            return true;
        }
        if (CharacterRaceRegistry.HUMAN.equals(raceId)) return "lotr:human".equals(modelId);
        if (CharacterRaceRegistry.ELF.equals(raceId)) return "lotr:elf".equals(modelId);
        if (CharacterRaceRegistry.DWARF.equals(raceId)) return "lotr:dwarf".equals(modelId);
        if (CharacterRaceRegistry.HOBBIT.equals(raceId)) return "lotr:hobbit".equals(modelId);
        if (CharacterRaceRegistry.ORC.equals(raceId)) return "lotr:orc".equals(modelId);
        if (CharacterRaceRegistry.URUK.equals(raceId)) return "lotr:uruk".equals(modelId);
        return CharacterRaceRegistry.HALF_TROLL.equals(raceId)
                && "lotr:half_troll".equals(modelId);
    }

    private static int requiredInteger(JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive()) {
            throw invalid(path + " must be an integer");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw invalid(path + " must be an integer");
        }
        double value = primitive.getAsDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)
                || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalid(path + " must be an integer");
        }
        return (int) value;
    }

    private static void validateAllowedKeys(JsonObject object,
                                            Set<String> allowed,
                                            String path) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw invalid(path + " contains unknown field " + entry.getKey());
            }
        }
    }

    private static String readBounded(Reader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[2048];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            if (read == 0) continue;
            if (result.length() + read > MAX_JSON_CHARACTERS) {
                throw invalid("JSON exceeds " + MAX_JSON_CHARACTERS + " characters");
            }
            result.append(buffer, 0, read);
        }
        return result.toString();
    }

    private static boolean containsUnsafeControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 32 && character != '\n'
                    && character != '\r' && character != '\t') {
                return true;
            }
        }
        return false;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.length() == 0
                ? throwable.getClass().getSimpleName() : message;
    }

    private static String normalizeSource(String sourceName) {
        return sourceName == null || sourceName.trim().length() == 0
                ? "<unknown>" : sourceName.trim();
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(Arrays.asList(values)));
    }

    public static final class ParseResult {
        private final LoreCharacterDefinition definition;
        private final List<String> errors;

        private ParseResult(LoreCharacterDefinition definition,
                            List<String> errors) {
            this.definition = definition;
            this.errors = Collections.unmodifiableList(
                    new ArrayList<String>(errors));
        }

        public LoreCharacterDefinition getDefinition() {
            return this.definition;
        }

        public List<String> getErrors() {
            return this.errors;
        }

        public boolean isValid() {
            return this.definition != null && this.errors.isEmpty();
        }
    }
}
