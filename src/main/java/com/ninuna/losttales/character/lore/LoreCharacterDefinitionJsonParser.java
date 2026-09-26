package com.ninuna.losttales.character.lore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.registry.CharacterBodyModelRegistry;
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
import com.ninuna.losttales.character.validation.CharacterValidator;

/** Strict parser for the intentionally small lore-character JSON format. */
public final class LoreCharacterDefinitionJsonParser {

    public static final int MAX_JSON_CHARACTERS = 16 * 1024;

    private static final Pattern IDENTIFIER = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<String> DEFINITION_KEYS = set(
            "dataVersion", "id", "name", "description", "age", "appearance",
            "profile");
    /** Every key but {@code age} and {@code profile}, which a file may leave out. */
    private static final Set<String> REQUIRED_DEFINITION_KEYS = set(
            "dataVersion", "id", "name", "description", "appearance");
    private static final Set<String> APPEARANCE_KEYS = set(
            "raceId", "genderId", "modelId", "skinId");
    private static final Set<String> PROFILE_KEYS = set(
            "appearance", "personality", "history", "facts", "glances");
    private static final Set<String> GLANCE_KEYS = set("emoji", "title", "line");
    /** A glance's emoji is a chat emoji's name; well past the longest. */
    private static final int MAX_EMOJI_NAME_LENGTH = 64;

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
            for (String key : REQUIRED_DEFINITION_KEYS) {
                if (!object.has(key)) {
                    throw invalid("root must contain dataVersion, id, name, description, and appearance");
                }
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
            int age = object.has("age")
                    ? requiredInteger(object.get("age"), "age")
                    : LoreCharacterDefinition.DEFAULT_AGE;
            if (age < CharacterValidator.MIN_AGE || age > CharacterValidator.MAX_AGE) {
                throw invalid("age must be between " + CharacterValidator.MIN_AGE
                        + " and " + CharacterValidator.MAX_AGE);
            }
            LoreCharacterDefinition.Appearance appearance = parseAppearance(
                    object.get("appearance"), "appearance");
            CharacterProfile profile = parseProfile(object.get("profile"),
                    description);
            return new ParseResult(
                    new LoreCharacterDefinition(dataVersion, id, name,
                            description, age, appearance, profile), errors);
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
        raceId = CharacterRaceRegistry.normalizeIdentifier(raceId);
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

    /**
     * The profile a definition gives its character: an object of the
     * profile's parts, every one optional — {@code appearance},
     * {@code personality} and {@code history} (up to 512 characters,
     * paragraphs kept), {@code facts} by name (up to 24 characters each),
     * and up to five {@code glances}, each the {@code emoji} of the chat's
     * by its name, a {@code title} and an optional {@code line}. The
     * History is the description where the profile gives none, and a file
     * with no profile is its description alone. Held to the bounds a
     * player's profile is, normalised as one is stored.
     */
    private static CharacterProfile parseProfile(JsonElement element,
                                                 String description) {
        CharacterProfile profile = CharacterProfile.EMPTY;
        if (element != null && !element.isJsonNull()) {
            if (!element.isJsonObject()) {
                throw invalid("profile must be an object");
            }
            JsonObject object = element.getAsJsonObject();
            validateAllowedKeys(object, PROFILE_KEYS, "profile");
            for (CharacterProfile.Section section
                    : CharacterProfile.Section.values()) {
                if (object.has(section.getId())) {
                    profile = profile.withSection(section, string(
                            object.get(section.getId()),
                            "profile." + section.getId(),
                            CharacterProfile.MAX_SECTION_LENGTH, true));
                }
            }
            if (object.has("facts")) {
                profile = parseFacts(profile, object.get("facts"));
            }
            if (object.has("glances")) {
                profile = profile.withGlances(parseGlances(
                        object.get("glances")));
            }
        }
        if (profile.section(CharacterProfile.Section.HISTORY).length() == 0) {
            profile = profile.withSection(CharacterProfile.Section.HISTORY,
                    description);
        }
        CharacterProfile stored = CharacterValidator.normalizeProfile(profile);
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            if (!CharacterValidator.isValidSection(stored.section(section))) {
                throw invalid("profile." + section.getId()
                        + " holds characters a profile may not");
            }
        }
        for (int index = 0; index < stored.glances().size(); index++) {
            if (!CharacterValidator.isValidGlance(stored.glances().get(index))) {
                throw invalid("profile.glances[" + index + "] needs one of the"
                        + " chat's emoji by its name and a title");
            }
        }
        return stored;
    }

    private static CharacterProfile parseFacts(CharacterProfile profile,
                                               JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw invalid("profile.facts must be an object");
        }
        JsonObject facts = element.getAsJsonObject();
        Set<String> names = new LinkedHashSet<String>();
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            names.add(fact.getId());
        }
        validateAllowedKeys(facts, names, "profile.facts");
        CharacterProfile read = profile;
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            if (facts.has(fact.getId())) {
                String value = string(facts.get(fact.getId()),
                        "profile.facts." + fact.getId(),
                        CharacterProfile.MAX_FACT_LENGTH, true);
                if (!CharacterValidator.isValidLine(
                        CharacterValidator.normalizeLine(value),
                        CharacterProfile.MAX_FACT_LENGTH)) {
                    throw invalid("profile.facts." + fact.getId()
                            + " must be one line");
                }
                read = read.withFact(fact, value);
            }
        }
        return read;
    }

    private static List<CharacterProfile.Glance> parseGlances(
            JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            throw invalid("profile.glances must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > CharacterProfile.MAX_GLANCES) {
            throw invalid("profile.glances holds more than "
                    + CharacterProfile.MAX_GLANCES);
        }
        List<CharacterProfile.Glance> glances =
                new ArrayList<CharacterProfile.Glance>(array.size());
        for (int index = 0; index < array.size(); index++) {
            String path = "profile.glances[" + index + "]";
            JsonElement entry = array.get(index);
            if (entry == null || !entry.isJsonObject()) {
                throw invalid(path + " must be an object");
            }
            JsonObject glance = entry.getAsJsonObject();
            validateAllowedKeys(glance, GLANCE_KEYS, path);
            glances.add(new CharacterProfile.Glance(
                    requiredString(glance.get("emoji"), path + ".emoji",
                            MAX_EMOJI_NAME_LENGTH),
                    requiredString(glance.get("title"), path + ".title",
                            CharacterProfile.MAX_GLANCE_TITLE_LENGTH),
                    glance.has("line") ? string(glance.get("line"),
                            path + ".line",
                            CharacterProfile.MAX_GLANCE_LINE_LENGTH, true)
                            : ""));
        }
        return glances;
    }

    private static boolean isCompatibleModel(String raceId, String modelId) {
        if (LoreCharacterDefinition.Appearance.RACE_DEFAULT_MODEL.equals(modelId)) {
            return true;
        }
        return CharacterBodyModelRegistry.isCompatible(raceId, modelId);
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
