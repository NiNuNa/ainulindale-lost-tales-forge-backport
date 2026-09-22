package com.ninuna.losttales.client.motion;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Motion files read and written. A file is one JSON object naming its
 * motions by id:
 *
 * <pre>
 * {
 *   "chat.picker.open": { "about": "...", "duration": 140, "curve": "settle" },
 *   "chat.scroll":      { "about": "...", "follow": 0.06 },
 *   "chat.line.hover":  { "about": "...", "parts": { "chevron": { "poses": ..., "beats": ... } } }
 * }
 * </pre>
 *
 * <p>Every number is kept inside safe bounds and every list has a
 * ceiling. A motion that cannot be read is left out with a line saying
 * why, and the rest of the file still counts; a file that is not JSON at
 * all counts for nothing. Reading never throws.</p>
 */
public final class MotionCodec {
    /** The largest motion file read. */
    public static final int MAX_FILE_BYTES = 256 * 1024;
    static final int MAX_MOTIONS = 256;
    static final int MAX_PARTS = 16;
    static final int MAX_POSES = 16;
    static final int MAX_BEATS = 12;
    static final int MAX_PARAMS = 32;
    static final int MAX_ABOUT = 256;
    /** The longest a follower may take, in seconds. */
    static final float MAX_FOLLOW_SECONDS = 5.0F;
    /** How far a number the code reads may reach either way. */
    static final float MAX_PARAM = 100000.0F;

    private static final Pattern ID = Pattern.compile("[a-z0-9_.]{1,64}");
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");

    /** What a read found: the motions it could use and what it could not. */
    public static final class Result {
        private final boolean readable;
        private final Map<String, Motion> motions;
        private final List<String> problems;

        Result(boolean readable, Map<String, Motion> motions,
               List<String> problems) {
            this.readable = readable;
            this.motions = Collections.unmodifiableMap(motions);
            this.problems = Collections.unmodifiableList(problems);
        }

        /** Whether the file was JSON with an object at its top. */
        public boolean isReadable() {
            return this.readable;
        }

        public Map<String, Motion> motions() {
            return this.motions;
        }

        public List<String> problems() {
            return this.problems;
        }
    }

    private MotionCodec() {}

    /** The motions a file's text holds. */
    public static Result read(String text) {
        Map<String, Motion> motions = new LinkedHashMap<String, Motion>();
        List<String> problems = new ArrayList<String>();
        if (text == null || text.length() > MAX_FILE_BYTES) {
            problems.add("the file is missing or larger than "
                    + MAX_FILE_BYTES + " bytes");
            return new Result(false, motions, problems);
        }
        JsonElement root;
        try {
            root = new JsonParser().parse(text);
        } catch (JsonParseException malformed) {
            problems.add("not JSON: " + malformed.getMessage());
            return new Result(false, motions, problems);
        } catch (RuntimeException malformed) {
            problems.add("not JSON: " + malformed);
            return new Result(false, motions, problems);
        }
        if (root == null || !root.isJsonObject()) {
            problems.add("the file is not one JSON object");
            return new Result(false, motions, problems);
        }
        for (Map.Entry<String, JsonElement> entry
                : root.getAsJsonObject().entrySet()) {
            String id = entry.getKey();
            if (motions.size() >= MAX_MOTIONS) {
                problems.add("more than " + MAX_MOTIONS
                        + " motions; the rest are left out");
                break;
            }
            if (!ID.matcher(id).matches()) {
                problems.add("'" + clip(id) + "' is not a motion id "
                        + "(lower-case letters, digits, dots, underscores)");
                continue;
            }
            Motion motion = motion(id, entry.getValue(), problems);
            if (motion != null) {
                motions.put(id, motion);
            }
        }
        return new Result(true, motions, problems);
    }

    private static Motion motion(String id, JsonElement element,
                                 List<String> problems) {
        if (element == null || !element.isJsonObject()) {
            problems.add(id + ": not an object");
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String about = text(object, "about");
        if (about.length() > MAX_ABOUT) {
            about = about.substring(0, MAX_ABOUT);
        }
        Map<String, Float> params = params(id, object, problems);
        if (object.has("follow")) {
            float seconds = number(object.get("follow"), Float.NaN);
            if (Float.isNaN(seconds)) {
                problems.add(id + ": follow is not a number of seconds");
                return null;
            }
            return new Motion(id, about, null, params,
                    Math.max(0.0F, Math.min(MAX_FOLLOW_SECONDS, seconds)));
        }
        if (object.has("parts")) {
            Map<String, MotionPart> parts = parts(id, object.get("parts"),
                    problems);
            return parts == null ? null
                    : new Motion(id, about, parts, params, Float.NaN);
        }
        if (object.has("duration")) {
            return transition(id, about, params, object, problems);
        }
        problems.add(id + ": names neither parts, a duration nor follow");
        return null;
    }

    /** The short form: one value from off to on and back. */
    private static Motion transition(String id, String about,
                                     Map<String, Float> params,
                                     JsonObject object,
                                     List<String> problems) {
        int onMillis = millis(object.get("duration"));
        MotionCurve onCurve = curve(id, object, "curve", MotionCurve.EASE_OUT,
                problems);
        int offMillis = onMillis;
        MotionCurve offCurve = onCurve;
        JsonElement off = object.get("off");
        if (off != null) {
            if (!off.isJsonObject()) {
                problems.add(id + ": off is not an object");
                return null;
            }
            JsonObject offObject = off.getAsJsonObject();
            if (offObject.has("duration")) {
                offMillis = millis(offObject.get("duration"));
            }
            offCurve = curve(id, offObject, "curve", onCurve, problems);
        }
        return new Motion(id, about, transitionParts(onMillis, onCurve,
                offMillis, offCurve), params, Float.NaN);
    }

    /** The one part a transition moves, from 0 at rest to 1 on. */
    static Map<String, MotionPart> transitionParts(int onMillis,
                                                   MotionCurve onCurve,
                                                   int offMillis,
                                                   MotionCurve offCurve) {
        Map<String, float[]> poses = new LinkedHashMap<String, float[]>();
        poses.put(Motion.REST, MotionPart.pose(Collections.singletonMap(
                MotionTrack.VALUE, Float.valueOf(0.0F))));
        poses.put(Motion.ON, MotionPart.pose(Collections.singletonMap(
                MotionTrack.VALUE, Float.valueOf(1.0F))));
        Map<String, MotionBeat> beats = new LinkedHashMap<String, MotionBeat>();
        beats.put(Motion.ON, MotionBeat.plain(Motion.ON, Motion.ON, onMillis,
                onCurve));
        beats.put(Motion.OFF, MotionBeat.plain(Motion.OFF, Motion.REST,
                offMillis, offCurve));
        Map<String, MotionPart> parts = new LinkedHashMap<String, MotionPart>();
        parts.put(Motion.MAIN, new MotionPart(Motion.MAIN, poses, beats));
        return parts;
    }

    private static Map<String, MotionPart> parts(String id, JsonElement element,
                                                 List<String> problems) {
        if (!element.isJsonObject()) {
            problems.add(id + ": parts is not an object");
            return null;
        }
        Map<String, MotionPart> parts = new LinkedHashMap<String, MotionPart>();
        for (Map.Entry<String, JsonElement> entry
                : element.getAsJsonObject().entrySet()) {
            String name = entry.getKey();
            String where = id + " > " + clip(name);
            if (parts.size() >= MAX_PARTS) {
                problems.add(id + ": more than " + MAX_PARTS + " parts");
                break;
            }
            if (!NAME.matcher(name).matches() || !entry.getValue().isJsonObject()) {
                problems.add(where + ": not a part");
                continue;
            }
            MotionPart part = part(name, where,
                    entry.getValue().getAsJsonObject(), problems);
            if (part != null) {
                parts.put(name, part);
            }
        }
        if (parts.isEmpty()) {
            problems.add(id + ": no part could be read");
            return null;
        }
        return parts;
    }

    private static MotionPart part(String name, String where, JsonObject object,
                                   List<String> problems) {
        Map<String, float[]> poses = new LinkedHashMap<String, float[]>();
        JsonElement posesElement = object.get("poses");
        if (posesElement != null && posesElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> pose
                    : posesElement.getAsJsonObject().entrySet()) {
                if (poses.size() >= MAX_POSES) {
                    problems.add(where + ": more than " + MAX_POSES + " poses");
                    break;
                }
                if (!NAME.matcher(pose.getKey()).matches()
                        || !pose.getValue().isJsonObject()) {
                    problems.add(where + ": pose '" + clip(pose.getKey())
                            + "' is not a pose");
                    continue;
                }
                Map<MotionTrack, Float> values =
                        new EnumMap<MotionTrack, Float>(MotionTrack.class);
                for (Map.Entry<String, JsonElement> value
                        : pose.getValue().getAsJsonObject().entrySet()) {
                    MotionTrack track = MotionTrack.parse(value.getKey());
                    float number = number(value.getValue(), Float.NaN);
                    if (track == null || Float.isNaN(number)) {
                        problems.add(where + ": pose " + pose.getKey()
                                + " has '" + clip(value.getKey())
                                + "', which is no track with a number");
                        continue;
                    }
                    values.put(track, Float.valueOf(number));
                }
                poses.put(pose.getKey(), MotionPart.pose(values));
            }
        } else if (posesElement != null) {
            problems.add(where + ": poses is not an object");
        }
        Map<String, MotionBeat> beats = new LinkedHashMap<String, MotionBeat>();
        JsonElement beatsElement = object.get("beats");
        if (beatsElement != null && beatsElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> beat
                    : beatsElement.getAsJsonObject().entrySet()) {
                if (beats.size() >= MAX_BEATS) {
                    problems.add(where + ": more than " + MAX_BEATS + " beats");
                    break;
                }
                String beatWhere = where + " > " + clip(beat.getKey());
                if (!NAME.matcher(beat.getKey()).matches()
                        || !beat.getValue().isJsonObject()) {
                    problems.add(beatWhere + ": not a beat");
                    continue;
                }
                beats.put(beat.getKey(), beat(beat.getKey(), beatWhere,
                        beat.getValue().getAsJsonObject(), poses.keySet(),
                        problems));
            }
        } else if (beatsElement != null) {
            problems.add(where + ": beats is not an object");
        }
        return new MotionPart(name, poses, beats);
    }

    private static MotionBeat beat(String name, String where, JsonObject object,
                                   java.util.Set<String> poses,
                                   List<String> problems) {
        String to = text(object, "to");
        if (to.length() > 0 && !poses.contains(to)) {
            problems.add(where + ": goes to pose '" + clip(to)
                    + "', which the part does not have");
            to = "";
        }
        MotionCurve curve = curve(where, object, "curve", MotionCurve.EASE_OUT,
                problems);
        Map<MotionTrack, MotionLayer[]> tracks = tracks(where,
                object.get("tracks"), problems);
        Map<String, Map<MotionTrack, MotionLayer[]>> variants =
                new LinkedHashMap<String, Map<MotionTrack, MotionLayer[]>>();
        JsonElement from = object.get("from");
        if (from != null && from.isJsonObject()) {
            for (Map.Entry<String, JsonElement> variant
                    : from.getAsJsonObject().entrySet()) {
                if (!poses.contains(variant.getKey())) {
                    problems.add(where + ": answers pose '"
                            + clip(variant.getKey())
                            + "', which the part does not have");
                    continue;
                }
                variants.put(variant.getKey(), tracks(where + " from "
                        + variant.getKey(), variant.getValue(), problems));
            }
        } else if (from != null) {
            problems.add(where + ": from is not an object");
        }
        return new MotionBeat(name, to, millis(object.get("duration")),
                millis(object.get("delay")), millis(object.get("stagger")),
                curve, tracks, variants);
    }

    private static Map<MotionTrack, MotionLayer[]> tracks(String where,
                                                          JsonElement element,
                                                          List<String> problems) {
        Map<MotionTrack, MotionLayer[]> tracks =
                new EnumMap<MotionTrack, MotionLayer[]>(MotionTrack.class);
        if (element == null) {
            return tracks;
        }
        if (!element.isJsonObject()) {
            problems.add(where + ": tracks is not an object");
            return tracks;
        }
        for (Map.Entry<String, JsonElement> entry
                : element.getAsJsonObject().entrySet()) {
            MotionTrack track = MotionTrack.parse(entry.getKey());
            if (track == null) {
                problems.add(where + ": '" + clip(entry.getKey())
                        + "' is not a track");
                continue;
            }
            List<JsonElement> listed = new ArrayList<JsonElement>();
            if (entry.getValue().isJsonArray()) {
                for (JsonElement layer : entry.getValue().getAsJsonArray()) {
                    listed.add(layer);
                }
            } else {
                listed.add(entry.getValue());
            }
            List<MotionLayer> layers = new ArrayList<MotionLayer>();
            boolean travels = false;
            for (JsonElement layerElement : listed) {
                if (layers.size() >= MotionBeat.MAX_LAYERS) {
                    problems.add(where + " " + track.key() + ": more than "
                            + MotionBeat.MAX_LAYERS + " layers");
                    break;
                }
                MotionLayer layer = layer(where + " " + track.key(), track,
                        layerElement, problems);
                if (layer == null) {
                    continue;
                }
                if (layer.kind() == MotionLayer.Kind.TRAVEL) {
                    if (travels) {
                        problems.add(where + " " + track.key()
                                + ": travels twice; the second is left out");
                        continue;
                    }
                    travels = true;
                }
                layers.add(layer);
            }
            tracks.put(track, layers.toArray(new MotionLayer[0]));
        }
        return tracks;
    }

    private static MotionLayer layer(String where, MotionTrack track,
                                     JsonElement element,
                                     List<String> problems) {
        if (element == null || !element.isJsonObject()) {
            problems.add(where + ": a layer is not an object");
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        float begin = number(object.get("begin"), 0.0F);
        float end = number(object.get("end"), 1.0F);
        float reach = track.reach();
        if (object.has("travel")) {
            MotionCurve curve = MotionCurve.parse(text(object, "travel"));
            if (curve == null) {
                problems.add(where + ": travels on '"
                        + clip(text(object, "travel"))
                        + "', which is no curve");
                curve = MotionCurve.EASE_OUT;
            }
            return MotionLayer.travel(curve, begin, end);
        }
        if (object.has("bump")) {
            return MotionLayer.bump(bounded(number(object.get("bump"), 0.0F),
                    reach), begin, end);
        }
        if (object.has("ring")) {
            int lobes = Math.round(number(object.get("lobes"), 3.0F));
            return MotionLayer.ring(bounded(number(object.get("ring"), 0.0F),
                    reach), lobes, begin, end);
        }
        if (object.has("keys") && object.get("keys").isJsonArray()) {
            JsonArray keys = object.get("keys").getAsJsonArray();
            int count = Math.min(MotionLayer.MAX_KEYS, keys.size());
            float[] at = new float[count];
            float[] values = new float[count];
            MotionCurve[] curves = new MotionCurve[count];
            for (int index = 0; index < count; index++) {
                JsonElement key = keys.get(index);
                if (!key.isJsonArray() || key.getAsJsonArray().size() < 2) {
                    problems.add(where + ": a key is not [at, value, curve]");
                    return null;
                }
                JsonArray parts = key.getAsJsonArray();
                at[index] = number(parts.get(0), 0.0F);
                values[index] = bounded(number(parts.get(1), 0.0F), reach);
                curves[index] = parts.size() > 2
                        ? MotionCurve.parse(parts.get(2).isJsonPrimitive()
                                ? parts.get(2).getAsString() : "")
                        : MotionCurve.EASE_IN_OUT;
                if (curves[index] == null) {
                    problems.add(where + ": a key travels on no known curve");
                    curves[index] = MotionCurve.EASE_IN_OUT;
                }
            }
            return MotionLayer.keys(at, values, curves,
                    curve(where, object, "curve", MotionCurve.EASE_IN_OUT,
                            problems), begin, end);
        }
        problems.add(where + ": a layer is none of travel, bump, ring or keys");
        return null;
    }

    private static Map<String, Float> params(String id, JsonObject object,
                                             List<String> problems) {
        Map<String, Float> params = new LinkedHashMap<String, Float>();
        JsonElement element = object.get("params");
        if (element == null) {
            return params;
        }
        if (!element.isJsonObject()) {
            problems.add(id + ": params is not an object");
            return params;
        }
        for (Map.Entry<String, JsonElement> entry
                : element.getAsJsonObject().entrySet()) {
            if (params.size() >= MAX_PARAMS) {
                problems.add(id + ": more than " + MAX_PARAMS + " params");
                break;
            }
            float value = number(entry.getValue(), Float.NaN);
            if (!NAME.matcher(entry.getKey()).matches() || Float.isNaN(value)) {
                problems.add(id + ": param '" + clip(entry.getKey())
                        + "' is not a name with a number");
                continue;
            }
            params.put(entry.getKey(), Float.valueOf(
                    Math.max(-MAX_PARAM, Math.min(MAX_PARAM, value))));
        }
        return params;
    }

    private static MotionCurve curve(String where, JsonObject object,
                                     String key, MotionCurve fallback,
                                     List<String> problems) {
        if (!object.has(key)) {
            return fallback;
        }
        MotionCurve curve = MotionCurve.parse(text(object, key));
        if (curve == null) {
            problems.add(where + ": '" + clip(text(object, key))
                    + "' is no curve");
            return fallback;
        }
        return curve;
    }

    private static int millis(JsonElement element) {
        float value = number(element, 0.0F);
        return Math.max(0, Math.min(MotionBeat.MAX_MILLIS, Math.round(value)));
    }

    private static float number(JsonElement element, float fallback) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        float value = element.getAsFloat();
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : value;
    }

    private static float bounded(float value, float reach) {
        return Math.max(-reach, Math.min(reach, value));
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive()
                ? element.getAsString() : "";
    }

    private static String clip(String text) {
        return text == null ? "" : text.length() > 40
                ? text.substring(0, 40) + "..." : text;
    }

    /* ---- writing ---- */

    /** The file text for {@code motions}, in their order. */
    public static String write(Map<String, Motion> motions) {
        JsonObject root = new JsonObject();
        for (Motion motion : motions.values()) {
            root.add(motion.id(), encode(motion));
        }
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
                .create().toJson(root);
    }

    static JsonObject encode(Motion motion) {
        JsonObject object = new JsonObject();
        if (motion.about().length() > 0) {
            object.addProperty("about", motion.about());
        }
        if (motion.isFollower()) {
            object.add("follow", number(motion.followSeconds()));
        } else if (isTransition(motion)) {
            MotionBeat on = motion.beat(Motion.ON);
            MotionBeat off = motion.beat(Motion.OFF);
            object.add("duration", number(on.durationMillis()));
            object.addProperty("curve", on.curve().name());
            if (off.durationMillis() != on.durationMillis()
                    || !off.curve().equals(on.curve())) {
                JsonObject offObject = new JsonObject();
                offObject.add("duration", number(off.durationMillis()));
                offObject.addProperty("curve", off.curve().name());
                object.add("off", offObject);
            }
        } else {
            JsonObject parts = new JsonObject();
            for (MotionPart part : motion.parts().values()) {
                parts.add(part.name(), encode(part));
            }
            object.add("parts", parts);
        }
        if (!motion.params().isEmpty()) {
            JsonObject params = new JsonObject();
            for (Map.Entry<String, Float> param : motion.params().entrySet()) {
                params.add(param.getKey(), number(param.getValue().floatValue()));
            }
            object.add("params", params);
        }
        return object;
    }

    /** Whether the motion is the short form's off and on, and nothing more. */
    static boolean isTransition(Motion motion) {
        MotionPart part = motion.part(Motion.MAIN);
        if (motion.parts().size() != 1 || part == null
                || part.poses().size() != 2 || part.beats().size() != 2) {
            return false;
        }
        MotionBeat on = part.beat(Motion.ON);
        MotionBeat off = part.beat(Motion.OFF);
        return on != null && off != null && on.tracks().isEmpty()
                && off.tracks().isEmpty() && on.variants().isEmpty()
                && off.variants().isEmpty() && on.delayMillis() == 0
                && off.delayMillis() == 0 && on.staggerMillis() == 0
                && off.staggerMillis() == 0
                && Motion.ON.equals(on.to()) && Motion.REST.equals(off.to())
                && part.poseValue(Motion.REST, MotionTrack.VALUE) == 0.0F
                && part.poseValue(Motion.ON, MotionTrack.VALUE) == 1.0F;
    }

    private static JsonObject encode(MotionPart part) {
        JsonObject object = new JsonObject();
        JsonObject poses = new JsonObject();
        for (Map.Entry<String, float[]> pose : part.poses().entrySet()) {
            JsonObject values = new JsonObject();
            for (MotionTrack track : MotionTrack.values()) {
                float value = pose.getValue()[track.ordinal()];
                if (!Float.isNaN(value)) {
                    values.add(track.key(), number(value));
                }
            }
            poses.add(pose.getKey(), values);
        }
        object.add("poses", poses);
        JsonObject beats = new JsonObject();
        for (MotionBeat beat : part.beats().values()) {
            beats.add(beat.name(), encode(beat));
        }
        object.add("beats", beats);
        return object;
    }

    private static JsonObject encode(MotionBeat beat) {
        JsonObject object = new JsonObject();
        if (beat.to().length() > 0) {
            object.addProperty("to", beat.to());
        }
        object.add("duration", number(beat.durationMillis()));
        if (beat.delayMillis() > 0) {
            object.add("delay", number(beat.delayMillis()));
        }
        if (beat.staggerMillis() > 0) {
            object.add("stagger", number(beat.staggerMillis()));
        }
        object.addProperty("curve", beat.curve().name());
        if (!beat.tracks().isEmpty()) {
            object.add("tracks", encode(beat.tracks()));
        }
        if (!beat.variants().isEmpty()) {
            JsonObject from = new JsonObject();
            for (Map.Entry<String, Map<MotionTrack, MotionLayer[]>> variant
                    : beat.variants().entrySet()) {
                from.add(variant.getKey(), encode(variant.getValue()));
            }
            object.add("from", from);
        }
        return object;
    }

    private static JsonObject encode(Map<MotionTrack, MotionLayer[]> tracks) {
        JsonObject object = new JsonObject();
        for (Map.Entry<MotionTrack, MotionLayer[]> track : tracks.entrySet()) {
            JsonArray layers = new JsonArray();
            for (MotionLayer layer : track.getValue()) {
                layers.add(encode(layer));
            }
            object.add(track.getKey().key(), layers);
        }
        return object;
    }

    private static JsonObject encode(MotionLayer layer) {
        JsonObject object = new JsonObject();
        switch (layer.kind()) {
            case TRAVEL:
                object.addProperty("travel", layer.curve().name());
                break;
            case BUMP:
                object.add("bump", number(layer.size()));
                break;
            case RING:
                object.add("ring", number(layer.size()));
                object.add("lobes", number(layer.lobes()));
                break;
            default:
                JsonArray keys = new JsonArray();
                for (int index = 0; index < layer.keyCount(); index++) {
                    JsonArray key = new JsonArray();
                    key.add(number(layer.keyAt(index)));
                    key.add(number(layer.keyValue(index)));
                    key.add(new JsonPrimitive(layer.keyCurve(index).name()));
                    keys.add(key);
                }
                object.add("keys", keys);
                object.addProperty("curve", layer.curve().name());
                break;
        }
        if (layer.begin() > 0.0F) {
            object.add("begin", number(layer.begin()));
        }
        if (layer.end() < 1.0F) {
            object.add("end", number(layer.end()));
        }
        return object;
    }

    /** A number as a file writes it: whole where it is, else its shortest decimal. */
    private static JsonPrimitive number(float value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E7F) {
            return new JsonPrimitive(Integer.valueOf(Math.round(value)));
        }
        return new JsonPrimitive(Double.valueOf(Float.toString(value)));
    }
}
