package com.ninuna.losttales.client.camera;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict, bounded parser for editable camera preset resources. */
public final class CameraPresetJsonParser {
    public static final int MAX_JSON_CHARACTERS = 64 * 1024;

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Set<String> ROOT_KEYS = set(
            "dataVersion", "id", "name", "profiles");
    private static final Set<String> PROFILE_REQUIRED_KEYS = set(
            "distance", "shoulderOffset", "verticalOffset",
            "fovOffset", "smoothing");
    private static final Set<String> PROFILE_KEYS = set(
            "distance", "shoulderOffset", "verticalOffset",
            "fovOffset", "smoothing", "motion");
    private static final Set<String> SMOOTHING_REQUIRED_KEYS = set(
            "positionRate", "rotationRate", "zoomRate",
            "shoulderRate", "verticalRate", "fovRate");
    private static final Set<String> SMOOTHING_KEYS = set(
            "positionRate", "verticalPositionRate",
            "rotationRate", "zoomRate",
            "shoulderRate", "verticalRate", "fovRate");
    private static final Set<String> MOTION_REQUIRED_KEYS = set(
            "horizontalFollowLimit", "verticalFollowLimit",
            "sideSway", "verticalSway", "forwardSway",
            "turnSway", "swayCyclesPerBlock", "responseRate");
    private static final Set<String> MOTION_KEYS = set(
            "horizontalFollowLimit", "verticalFollowLimit",
            "sideSway", "verticalSway", "forwardSway",
            "turnSway", "swayCyclesPerBlock", "responseRate",
            "lookPitchSway", "lookForwardSway",
            "lookResponseRate", "lookReferenceSpeed",
            "idleSideSway", "idleVerticalSway", "idleForwardSway",
            "idleCyclesPerSecond");

    private CameraPresetJsonParser() {}

    public static ParseResult parseDefinition(
            Reader reader, String sourceName) {
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
            validateExactKeys(object, ROOT_KEYS, "root");
            int dataVersion = integer(
                    object.get("dataVersion"), "dataVersion");
            if (dataVersion != CameraPresetDefinition.CURRENT_DATA_VERSION) {
                throw invalid("unsupported dataVersion " + dataVersion);
            }
            String id = string(object.get("id"), "id", 64)
                    .toLowerCase(Locale.ROOT);
            if (!ID.matcher(id).matches()) {
                throw invalid("id must match " + ID.pattern());
            }
            String name = string(object.get("name"), "name", 64);
            CameraPreset preset = parseProfiles(
                    object.get("profiles"), builtInPreset(id));
            return new ParseResult(new CameraPresetDefinition(
                    dataVersion, id, name, preset), errors);
        } catch (RuntimeException exception) {
            errors.add(source + ": " + safeMessage(exception));
        } catch (IOException exception) {
            errors.add(source + ": unable to read JSON: "
                    + safeMessage(exception));
        }
        return new ParseResult(null, errors);
    }

    private static CameraPreset parseProfiles(
            JsonElement element, CameraPreset builtInFallback) {
        if (element == null || !element.isJsonObject()) {
            throw invalid("profiles must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        Set<String> expected = new LinkedHashSet<String>();
        for (CameraProfileId id : CameraProfileId.values()) {
            expected.add(profileKey(id));
        }
        validateExactKeys(object, expected, "profiles");
        EnumMap<CameraProfileId, CameraProfile> profiles =
                new EnumMap<CameraProfileId, CameraProfile>(
                        CameraProfileId.class);
        for (CameraProfileId id : CameraProfileId.values()) {
            profiles.put(id, parseProfile(
                    id, object.get(profileKey(id)),
                    "profiles." + profileKey(id),
                    builtInFallback == null
                            ? null : builtInFallback.get(id)));
        }
        return new CameraPreset(profiles);
    }

    private static CameraProfile parseProfile(
            CameraProfileId id, JsonElement element, String path,
            CameraProfile builtInFallback) {
        if (element == null || !element.isJsonObject()) {
            throw invalid(path + " must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        validateKeys(
                object, PROFILE_KEYS, PROFILE_REQUIRED_KEYS, path);
        return new CameraProfile(
                id,
                number(object.get("distance"), path + ".distance", 0.0D, 12.0D),
                number(object.get("shoulderOffset"), path + ".shoulderOffset", 0.0D, 3.0D),
                number(object.get("verticalOffset"), path + ".verticalOffset", -3.0D, 3.0D),
                number(object.get("fovOffset"), path + ".fovOffset", -20.0D, 20.0D),
                parseSmoothing(
                        object.get("smoothing"), path + ".smoothing",
                        builtInFallback),
                object.has("motion")
                        ? parseMotion(
                        object.get("motion"), path + ".motion",
                        builtInFallback)
                        : builtInFallback == null
                        ? CameraMotionProfile.NONE
                        : builtInFallback.getMotion());
    }

    private static CameraSmoothing parseSmoothing(
            JsonElement element, String path,
            CameraProfile builtInFallback) {
        if (element == null || !element.isJsonObject()) {
            throw invalid(path + " must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        validateKeys(
                object, SMOOTHING_KEYS,
                SMOOTHING_REQUIRED_KEYS, path);
        double positionRate = number(
                object.get("positionRate"),
                path + ".positionRate", 0.0D, 60.0D);
        return new CameraSmoothing(
                positionRate,
                object.has("verticalPositionRate")
                        ? number(object.get("verticalPositionRate"),
                        path + ".verticalPositionRate", 0.0D, 60.0D)
                        : builtInFallback == null
                        ? positionRate
                        : builtInFallback.getSmoothing()
                                .getVerticalPositionRate(),
                number(object.get("rotationRate"), path + ".rotationRate", 0.0D, 60.0D),
                number(object.get("zoomRate"), path + ".zoomRate", 0.0D, 60.0D),
                number(object.get("shoulderRate"), path + ".shoulderRate", 0.0D, 60.0D),
                number(object.get("verticalRate"), path + ".verticalRate", 0.0D, 60.0D),
                number(object.get("fovRate"), path + ".fovRate", 0.0D, 60.0D));
    }

    private static CameraMotionProfile parseMotion(
            JsonElement element, String path,
            CameraProfile builtInFallback) {
        if (element == null || !element.isJsonObject()) {
            throw invalid(path + " must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        validateKeys(object, MOTION_KEYS, MOTION_REQUIRED_KEYS, path);
        CameraMotionProfile fallback = builtInFallback == null
                ? CameraMotionProfile.NONE
                : builtInFallback.getMotion();
        double responseRate = number(object.get("responseRate"),
                path + ".responseRate", 0.0D, 60.0D);
        return new CameraMotionProfile(
                number(object.get("horizontalFollowLimit"),
                        path + ".horizontalFollowLimit", 0.0D, 2.0D),
                number(object.get("verticalFollowLimit"),
                        path + ".verticalFollowLimit", 0.0D, 1.0D),
                number(object.get("sideSway"),
                        path + ".sideSway", 0.0D, 0.30D),
                number(object.get("verticalSway"),
                        path + ".verticalSway", 0.0D, 0.30D),
                number(object.get("forwardSway"),
                        path + ".forwardSway", 0.0D, 0.30D),
                number(object.get("turnSway"),
                        path + ".turnSway", 0.0D, 0.30D),
                optionalNumber(object, "lookPitchSway", path,
                        0.0D, 0.30D,
                        fallback.getLookPitchSway()),
                optionalNumber(object, "lookForwardSway", path,
                        0.0D, 0.30D,
                        fallback.getLookForwardSway()),
                optionalNumber(object, "lookResponseRate", path,
                        0.0D, 60.0D,
                        builtInFallback == null
                                ? responseRate
                                : fallback.getLookResponseRate()),
                optionalNumber(object, "lookReferenceSpeed", path,
                        30.0D, 720.0D,
                        builtInFallback == null
                                ? 240.0D
                                : fallback.getLookReferenceSpeed()),
                number(object.get("swayCyclesPerBlock"),
                        path + ".swayCyclesPerBlock", 0.0D, 4.0D),
                responseRate,
                optionalNumber(object, "idleSideSway", path,
                        0.0D, 0.10D, fallback.getIdleSideSway()),
                optionalNumber(object, "idleVerticalSway", path,
                        0.0D, 0.10D, fallback.getIdleVerticalSway()),
                optionalNumber(object, "idleForwardSway", path,
                        0.0D, 0.10D, fallback.getIdleForwardSway()),
                optionalNumber(object, "idleCyclesPerSecond", path,
                        0.0D, 1.0D, fallback.getIdleCyclesPerSecond()));
    }

    private static double optionalNumber(
            JsonObject object, String key, String path,
            double minimum, double maximum, double fallback) {
        return object.has(key)
                ? number(object.get(key), path + "." + key,
                minimum, maximum)
                : fallback;
    }

    private static double number(
            JsonElement element, String path, double minimum,
            double maximum) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw invalid(path + " must be a number");
        }
        double value = element.getAsDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)
                || value < minimum || value > maximum) {
            throw invalid(path + " must be between "
                    + minimum + " and " + maximum);
        }
        return value;
    }

    private static int integer(JsonElement element, String path) {
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
        return (int)value;
    }

    private static String string(
            JsonElement element, String path, int maximumLength) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw invalid(path + " must be a string");
        }
        String value = element.getAsString().trim();
        if (value.length() == 0 || value.length() > maximumLength
                || value.indexOf('\u00a7') >= 0
                || containsUnsafeControl(value)) {
            throw invalid(path + " is blank, too long, or unsafe");
        }
        return value;
    }

    private static void validateExactKeys(
            JsonObject object, Set<String> expected, String path) {
        validateKeys(object, expected, expected, path);
    }

    private static void validateKeys(
            JsonObject object, Set<String> allowed,
            Set<String> required, String path) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw invalid(path + " contains unknown field "
                        + entry.getKey());
            }
        }
        for (String key : required) {
            if (!object.has(key)) {
                throw invalid(path + " is missing required field " + key);
            }
        }
    }

    private static String readBounded(Reader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[2048];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            if (result.length() + read > MAX_JSON_CHARACTERS) {
                throw invalid("JSON exceeds "
                        + MAX_JSON_CHARACTERS + " characters");
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

    private static String profileKey(CameraProfileId id) {
        return id.name().toLowerCase(Locale.ROOT);
    }

    private static CameraPreset builtInPreset(String id) {
        for (CameraPresetId presetId : CameraPresetId.values()) {
            if (presetId.getConfigValue().equals(id)) {
                return CameraPreset.forId(presetId);
            }
        }
        return null;
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(Arrays.asList(values)));
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

    public static final class ParseResult {
        private final CameraPresetDefinition definition;
        private final List<String> errors;

        private ParseResult(
                CameraPresetDefinition definition, List<String> errors) {
            this.definition = definition;
            this.errors = Collections.unmodifiableList(
                    new ArrayList<String>(errors));
        }

        public CameraPresetDefinition getDefinition() {
            return definition;
        }

        public List<String> getErrors() {
            return errors;
        }

        public boolean isValid() {
            return definition != null && errors.isEmpty();
        }
    }
}
