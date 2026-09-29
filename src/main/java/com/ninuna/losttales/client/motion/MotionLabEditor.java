package com.ninuna.losttales.client.motion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The Motion Lab's rows for one motion: every number, curve and choice
 * its file writes, each a row with a value and a step either way. The
 * editor edits the motion as its file writes it, so whatever the file
 * holds survives an edit, the layers a row does not show included.
 *
 * <p>A number steps by its own amount, ten at a time when fast, rounded
 * to that amount and kept within its bounds; a curve and a choice step
 * through their names, round from the last to the first. A step that
 * changes the motion says so to the {@link Listener}, which plays the
 * edited motion in place of its file's; a step that only picks another
 * part or beat builds the rows again.</p>
 *
 * <p>Free of Minecraft: the page hands it the words its rows read, and a
 * test can step it as the page does.</p>
 */
final class MotionLabEditor {
    /** The words a row reads, by their key under the Lab's lang keys. */
    interface Words {
        String get(String key);
    }

    /** Told whenever a step changes the motion. */
    interface Listener {
        void changed();
    }

    /** How many steps a fast step takes. */
    static final int FAST_STEPS = 10;
    /** Where the parts of a row's name join. */
    static final String JOIN = " / ";

    private final Words words;
    private final Listener listener;
    private final List<Row> rows = new ArrayList<Row>();
    private String id;
    private JsonObject working;
    private int partIndex;
    private int beatIndex;

    MotionLabEditor(Words words, Listener listener) {
        this.words = words;
        this.listener = listener;
    }

    /**
     * Edits the motion {@code id} as {@code motion} writes it. The part
     * and beat stay where they were for the same motion read again, and
     * start from the first for another.
     */
    void edit(String id, JsonObject motion) {
        if (id == null || !id.equals(this.id)) {
            this.partIndex = 0;
            this.beatIndex = 0;
        }
        this.id = id;
        this.working = motion == null ? new JsonObject() : motion;
        rebuild();
    }

    /** The edited motion read back as a file of its own would be. */
    MotionCodec.Result read() {
        JsonObject wrapper = new JsonObject();
        if (this.id != null && this.working != null) {
            wrapper.add(this.id, this.working);
        }
        return MotionCodec.read(wrapper.toString());
    }

    /** The rows, top to bottom. */
    List<Row> rows() {
        return Collections.unmodifiableList(this.rows);
    }

    private String word(String key) {
        return this.words == null ? key : this.words.get(key);
    }

    private void changed() {
        if (this.listener != null) {
            this.listener.changed();
        }
    }

    /* ---- building the rows ---- */

    private void rebuild() {
        this.rows.clear();
        JsonObject motion = this.working;
        if (motion == null) {
            return;
        }
        if (motion.has("follow")) {
            this.rows.add(new NumberRow(word("row.follow"), owner(motion),
                    "follow", 0.01F, 0.0F, MotionCodec.MAX_FOLLOW_SECONDS,
                    0.0F));
        } else if (motion.has("parts")) {
            addPartRows(objectOf(motion, "parts"));
        } else {
            addTransitionRows(motion);
        }
        JsonObject params = motion.has("params")
                && motion.get("params").isJsonObject()
                ? motion.getAsJsonObject("params") : null;
        if (params != null) {
            this.rows.add(new TextRow(word("row.params"), true));
            for (Map.Entry<String, JsonElement> param : params.entrySet()) {
                float value = number(param.getValue(), 0.0F);
                this.rows.add(new NumberRow(param.getKey(), owner(params),
                        param.getKey(), stepFor(value), -MotionCodec.MAX_PARAM,
                        MotionCodec.MAX_PARAM, 0.0F));
            }
        }
    }

    private void addTransitionRows(final JsonObject motion) {
        this.rows.add(new NumberRow(word("row.duration"), owner(motion),
                "duration", 10.0F, 0.0F, MotionBeat.MAX_MILLIS, 0.0F));
        this.rows.add(new CurveRow(word("row.curve"), owner(motion), "curve",
                MotionCurve.EASE_OUT.name()));
        // The way back is the way on until it is tuned on its own.
        Owner off = new Owner() {
            @Override
            public JsonObject get(boolean create) {
                if (motion.has("off") && motion.get("off").isJsonObject()) {
                    return motion.getAsJsonObject("off");
                }
                if (!create) {
                    return motion;
                }
                JsonObject made = new JsonObject();
                made.add("duration", copyOf(motion.get("duration")));
                made.add("curve", copyOf(motion.get("curve")));
                motion.add("off", made);
                return made;
            }
        };
        this.rows.add(new NumberRow(word("row.off_duration"), off, "duration",
                10.0F, 0.0F, MotionBeat.MAX_MILLIS, 0.0F));
        this.rows.add(new CurveRow(word("row.off_curve"), off, "curve",
                MotionCurve.EASE_OUT.name()));
    }

    private void addPartRows(JsonObject parts) {
        List<String> partNames = keys(parts);
        if (partNames.isEmpty()) {
            return;
        }
        this.partIndex = Math.max(0, Math.min(this.partIndex,
                partNames.size() - 1));
        this.rows.add(new PickRow(word("row.part"), partNames,
                this.partIndex) {
            @Override
            void picked(int index) {
                MotionLabEditor.this.partIndex = index;
                MotionLabEditor.this.beatIndex = 0;
                rebuild();
            }
        });
        JsonObject part = objectOf(parts, partNames.get(this.partIndex));
        JsonObject poses = objectOf(part, "poses");
        JsonObject beats = objectOf(part, "beats");
        List<String> beatNames = keys(beats);
        if (!beatNames.isEmpty()) {
            this.beatIndex = Math.max(0, Math.min(this.beatIndex,
                    beatNames.size() - 1));
            this.rows.add(new PickRow(word("row.beat"), beatNames,
                    this.beatIndex) {
                @Override
                void picked(int index) {
                    MotionLabEditor.this.beatIndex = index;
                    rebuild();
                }
            });
            final JsonObject beat = objectOf(beats,
                    beatNames.get(this.beatIndex));
            final List<String> targets = new ArrayList<String>();
            targets.add("");
            targets.addAll(keys(poses));
            String to = text(beat.get("to"));
            final String toCode = word("row.to_code");
            this.rows.add(new PickRow(word("row.to"), targets,
                    Math.max(0, targets.indexOf(to))) {
                @Override
                String label(String choice) {
                    return choice.length() == 0 ? toCode : choice;
                }

                @Override
                void picked(int index) {
                    if (targets.get(index).length() == 0) {
                        beat.remove("to");
                    } else {
                        beat.addProperty("to", targets.get(index));
                    }
                    rebuild();
                    changed();
                }
            });
            this.rows.add(new NumberRow(word("row.duration"), owner(beat),
                    "duration", 10.0F, 0.0F, MotionBeat.MAX_MILLIS, 0.0F));
            this.rows.add(new NumberRow(word("row.delay"), owner(beat),
                    "delay", 10.0F, 0.0F, MotionBeat.MAX_MILLIS, 0.0F));
            this.rows.add(new NumberRow(word("row.stagger"), owner(beat),
                    "stagger", 1.0F, 0.0F, MotionBeat.MAX_MILLIS, 0.0F));
            this.rows.add(new CurveRow(word("row.curve"), owner(beat), "curve",
                    MotionCurve.EASE_OUT.name()));
            addLayerRows("", objectOf(beat, "tracks"));
            JsonObject from = objectOf(beat, "from");
            for (String pose : keys(from)) {
                addLayerRows(word("row.from") + " " + pose + JOIN,
                        objectOf(from, pose));
            }
        }
        if (!keys(poses).isEmpty()) {
            this.rows.add(new TextRow(word("row.poses"), true));
            for (String pose : keys(poses)) {
                JsonObject values = objectOf(poses, pose);
                for (String key : keys(values)) {
                    MotionTrack track = MotionTrack.parse(key);
                    if (track == null) {
                        continue;
                    }
                    this.rows.add(new NumberRow(pose + JOIN + key,
                            owner(values), key, trackStep(track),
                            -track.reach(), track.reach(), 0.0F));
                }
            }
        }
    }

    /** A row for every number of every layer a beat's tracks hold. */
    private void addLayerRows(String prefix, JsonObject tracks) {
        for (String key : keys(tracks)) {
            MotionTrack track = MotionTrack.parse(key);
            JsonElement listed = tracks.get(key);
            if (track == null || listed == null) {
                continue;
            }
            List<JsonObject> layers = new ArrayList<JsonObject>();
            if (listed.isJsonArray()) {
                for (JsonElement layer : listed.getAsJsonArray()) {
                    if (layer.isJsonObject()) {
                        layers.add(layer.getAsJsonObject());
                    }
                }
            } else if (listed.isJsonObject()) {
                layers.add(listed.getAsJsonObject());
            }
            for (JsonObject layer : layers) {
                String name = prefix + key + JOIN;
                if (layer.has("travel")) {
                    this.rows.add(new CurveRow(name + word("row.travel"),
                            owner(layer), "travel",
                            MotionCurve.EASE_OUT.name()));
                } else if (layer.has("bump")) {
                    this.rows.add(new NumberRow(name + word("row.bump"),
                            owner(layer), "bump", 0.05F, -track.reach(),
                            track.reach(), 0.0F));
                } else if (layer.has("ring")) {
                    this.rows.add(new NumberRow(name + word("row.ring"),
                            owner(layer), "ring", 0.05F, -track.reach(),
                            track.reach(), 0.0F));
                    this.rows.add(new NumberRow(name + word("row.lobes"),
                            owner(layer), "lobes", 1.0F, 1.0F,
                            MotionLayer.MAX_LOBES, 3.0F));
                } else {
                    this.rows.add(new TextRow(name + word("row.keys"), false));
                    continue;
                }
                this.rows.add(new NumberRow(name + word("row.begin"),
                        owner(layer), "begin", 0.02F, 0.0F, 1.0F, 0.0F));
                this.rows.add(new NumberRow(name + word("row.end"),
                        owner(layer), "end", 0.02F, 0.0F, 1.0F, 1.0F));
            }
        }
    }

    /* ---- the rows ---- */

    /** Where a row's value lives: an object of the motion's JSON, made when it is first stepped if it must be. */
    interface Owner {
        JsonObject get(boolean create);
    }

    private static Owner owner(final JsonObject object) {
        return new Owner() {
            @Override
            public JsonObject get(boolean create) {
                return object;
            }
        };
    }

    /** One line of the editor: a name, a value, and a step either way. */
    abstract static class Row {
        final String label;

        Row(String label) {
            this.label = label == null ? "" : label;
        }

        /** The value as the stepper shows it. */
        abstract String value();

        /** One step on (1) or back (-1), {@link #FAST_STEPS} of them when fast. */
        abstract void step(int direction, boolean fast);

        /** Whether the row has a stepper. */
        boolean editable() {
            return true;
        }

        /** Whether the row heads a section. */
        boolean header() {
            return false;
        }
    }

    /** A number, stepped within its bounds. */
    final class NumberRow extends Row {
        private final Owner owner;
        private final String key;
        private final float step;
        private final float least;
        private final float most;
        private final float fallback;

        NumberRow(String label, Owner owner, String key, float step,
                  float least, float most, float fallback) {
            super(label);
            this.owner = owner;
            this.key = key;
            this.step = step;
            this.least = least;
            this.most = most;
            this.fallback = fallback;
        }

        float current() {
            return number(this.owner.get(false).get(this.key), this.fallback);
        }

        @Override
        String value() {
            return shown(current());
        }

        @Override
        void step(int direction, boolean fast) {
            float next = stepped(current(), this.step, direction, fast,
                    this.least, this.most);
            this.owner.get(true).add(this.key, jsonNumber(next));
            changed();
        }
    }

    /** A curve, stepped through the named ones. */
    final class CurveRow extends Row {
        private final Owner owner;
        private final String key;
        private final String fallback;

        CurveRow(String label, Owner owner, String key, String fallback) {
            super(label);
            this.owner = owner;
            this.key = key;
            this.fallback = fallback;
        }

        @Override
        String value() {
            String name = text(this.owner.get(false).get(this.key));
            return name.length() == 0 ? this.fallback : name;
        }

        @Override
        void step(int direction, boolean fast) {
            List<MotionCurve> curves = MotionCurve.named();
            MotionCurve current = MotionCurve.parse(value());
            int index = Math.max(0, curves.indexOf(current));
            this.owner.get(true).addProperty(this.key,
                    curves.get(cycled(index, direction, curves.size())).name());
            changed();
        }
    }

    /** One of a few words, stepped through them. */
    abstract class PickRow extends Row {
        private final List<String> choices;
        private final int chosen;

        PickRow(String label, List<String> choices, int chosen) {
            super(label);
            this.choices = choices;
            this.chosen = chosen;
        }

        String label(String choice) {
            return choice;
        }

        abstract void picked(int index);

        @Override
        String value() {
            return label(this.choices.get(this.chosen));
        }

        @Override
        void step(int direction, boolean fast) {
            picked(cycled(this.chosen, direction, this.choices.size()));
        }
    }

    /** A section's header, or a note that is read and not stepped. */
    static final class TextRow extends Row {
        private final boolean header;

        TextRow(String label, boolean header) {
            super(label);
            this.header = header;
        }

        @Override
        String value() {
            return "";
        }

        @Override
        void step(int direction, boolean fast) {}

        @Override
        boolean editable() {
            return false;
        }

        @Override
        boolean header() {
            return this.header;
        }
    }

    /* ---- the steps ---- */

    /**
     * A number one step on or back from {@code current}, ten steps when
     * fast: rounded to the step, so a value off the step's grid lands on
     * it, and kept within {@code least} and {@code most}.
     */
    static float stepped(float current, float step, int direction,
                         boolean fast, float least, float most) {
        float size = step <= 0.0F ? 1.0F : step;
        float next = current + Integer.signum(direction) * size
                * (fast ? FAST_STEPS : 1);
        next = Math.round(next / size) * size;
        return Math.max(least, Math.min(most, next));
    }

    /** An index one step on or back through {@code size} choices, round from the last to the first. */
    static int cycled(int index, int direction, int size) {
        if (size <= 0) {
            return 0;
        }
        return ((index + Integer.signum(direction)) % size + size) % size;
    }

    /** A value as the Lab shows it: whole where it is, else to three places. */
    static String shown(float value) {
        if (value == Math.rint(value)) {
            return String.valueOf(Math.round(value));
        }
        return String.valueOf(Math.round(value * 1000.0F) / 1000.0F);
    }

    /** The step a number the file names without one takes: finer for a smaller number. */
    static float stepFor(float value) {
        float size = Math.abs(value);
        return size >= 10.0F ? 1.0F : size >= 1.0F ? 0.25F : 0.05F;
    }

    private static float trackStep(MotionTrack track) {
        switch (track) {
            case X:
            case Y:
            case GAP:
            case TURN:
                return 0.25F;
            default:
                return 0.05F;
        }
    }

    /* ---- JSON ---- */

    private static List<String> keys(JsonObject object) {
        List<String> keys = new ArrayList<String>();
        if (object != null) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    private static JsonObject objectOf(JsonObject parent, String key) {
        JsonElement element = parent == null ? null : parent.get(key);
        return element != null && element.isJsonObject()
                ? element.getAsJsonObject() : new JsonObject();
    }

    private static String text(JsonElement element) {
        return element != null && element.isJsonPrimitive()
                ? element.getAsString() : "";
    }

    private static float number(JsonElement element, float fallback) {
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()
                ? element.getAsFloat() : fallback;
    }

    private static JsonElement copyOf(JsonElement element) {
        return element == null ? new JsonPrimitive(Integer.valueOf(0))
                : element;
    }

    private static JsonPrimitive jsonNumber(float value) {
        if (value == Math.rint(value)) {
            return new JsonPrimitive(Integer.valueOf(Math.round(value)));
        }
        return new JsonPrimitive(Double.valueOf(shown(value)));
    }
}
