package com.ninuna.losttales.client.motion;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.FMLLog;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

/**
 * Every motion in force, and the three settings that reach all of them:
 * {@code animations} (on or off), {@code animationSpeed} and
 * {@code reducedMotion}.
 *
 * <p>Motions come from files, one per family ({@link #FAMILIES}), at
 * {@code assets/losttales/motion/<family>.json}. Each family is read in
 * three layers, each replacing the motions it names:</p>
 * <ol>
 *   <li>the mod's own copy, read straight from its jar, which is always
 *   there and always valid;</li>
 *   <li>the copy the resource packs in use give, so a pack can restyle
 *   the mod's motion;</li>
 *   <li>{@code config/losttales/client/motion/<family>.json}, where the
 *   Motion Lab saves what is tuned in it.</li>
 * </ol>
 * <p>A file that cannot be read keeps the layer below it and says so in
 * one log line; a motion the code asks for that no file has lands at
 * once, and is named in the log the first time. F3+T reads everything
 * again.</p>
 */
public final class Motions {
    /** The families, one motion file each. */
    public static final List<String> FAMILIES = Collections.unmodifiableList(
            Arrays.asList("chat", "ui", "screen", "hud", "inventory", "map"));
    /** The slowest and fastest {@code animationSpeed} plays at. */
    public static final double MIN_SPEED = 0.25D;
    public static final double MAX_SPEED = 4.0D;
    /** Where the Motion Lab's files live under the client folder. */
    public static final String OVERRIDE_FOLDER = "motion";
    /** The longest a fade runs under reduced motion, in milliseconds. */
    public static final int REDUCED_MILLIS = 90;

    private static final Set<String> REPORTED = new HashSet<String>();
    private static volatile Map<String, Motion> current;
    private static volatile Map<String, String> familyOf;
    private static volatile Map<String, Motion> bundled;
    /** Motions played in place of their files' while the Motion Lab tunes them. */
    private static volatile Map<String, Motion> previews =
            Collections.emptyMap();
    private static File overrideFolder;

    /** Reads every motion again whenever the resources are, F3+T included. */
    public static final IResourceManagerReloadListener RELOADER =
            new IResourceManagerReloadListener() {
                @Override
                public void onResourceManagerReload(IResourceManager manager) {
                    reload(manager);
                }
            };

    private Motions() {}

    /** Where the Motion Lab's files are kept: the client folder's own. */
    public static synchronized void initialize(File clientFolder) {
        overrideFolder = clientFolder == null ? null
                : new File(clientFolder, OVERRIDE_FOLDER);
    }

    /* ---- the settings ---- */

    /** Whether anything moves at all. */
    public static boolean enabled() {
        return LostTalesConfig.animations;
    }

    /** Whether motion keeps to short fades: no travel, overshoot or anticipation. */
    public static boolean reduced() {
        return LostTalesConfig.reducedMotion;
    }

    /** How fast every motion plays; 2 is twice as fast. */
    public static double speed() {
        double speed = LostTalesConfig.animationSpeed;
        if (Double.isNaN(speed)) {
            return 1.0D;
        }
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    /**
     * Whether motion beyond a plain move or fade plays: an anticipation,
     * an overshoot, a tremor, and the loops that run while nothing
     * happens (the key hints' breath, the compass chevrons' pulse, the
     * clouds' sway on the map, the typing dots). The Animations switch and
     * reduced motion both still them. What asks is timed by hand rather
     * than by a motion file.
     */
    public static boolean flourishes() {
        return enabled() && !reduced();
    }

    /**
     * A span of time as hand-timed motion reads it: {@code nanos} at the
     * speed in force, so the motion's own constants are its pace at
     * speed 1.
     */
    public static long paced(long nanos) {
        return Math.round(nanos * speed());
    }

    /** A time from a motion file, in nanoseconds at the speed in force; none while motion is off. */
    public static long scaledNanos(int millis) {
        if (!enabled() || millis <= 0) {
            return 0L;
        }
        return Math.round(millis * 1000000.0D / speed());
    }

    /**
     * How long a beat that fades or changes in place runs: its time at
     * the speed in force, and no longer than {@link #REDUCED_MILLIS}
     * under reduced motion.
     */
    public static long beatNanos(int millis) {
        long nanos = scaledNanos(millis);
        return reduced() ? Math.min(nanos, REDUCED_MILLIS * 1000000L) : nanos;
    }

    /* ---- the motions ---- */

    /** The motion of that id; one that moves nothing when no file has it. */
    public static Motion get(String id) {
        Motion previewed = previews.get(id);
        if (previewed != null) {
            return previewed;
        }
        Motion motion = motions().get(id);
        if (motion != null) {
            return motion;
        }
        report("missing:" + id, "No motion '" + id + "' is in any motion file;"
                + " what it moves lands at once");
        return Motion.instant(id);
    }

    /**
     * Plays {@code motion} in place of the one of its id until the
     * previews are cleared: what the Motion Lab shows while a motion is
     * being tuned, before anything is saved.
     */
    public static synchronized void preview(Motion motion) {
        if (motion == null) {
            return;
        }
        Map<String, Motion> next = new LinkedHashMap<String, Motion>(previews);
        next.put(motion.id(), motion);
        previews = Collections.unmodifiableMap(next);
    }

    /** Plays every motion as its files say again. */
    public static synchronized void clearPreviews() {
        previews = Collections.emptyMap();
    }

    /** Plays the motion of {@code id} as its files say again. */
    public static synchronized void clearPreview(String id) {
        if (!previews.containsKey(id)) {
            return;
        }
        Map<String, Motion> next = new LinkedHashMap<String, Motion>(previews);
        next.remove(id);
        previews = Collections.unmodifiableMap(next);
    }

    /**
     * Writes {@code motion} into the Motion Lab's file of its family, in
     * place of any motion of its id there, and reads every motion again.
     * Answers whether the file was written.
     */
    public static synchronized boolean save(Motion motion,
                                            IResourceManager manager) {
        String family = family(motion.id());
        Map<String, Motion> saved = savedMotions(family);
        if (saved == null) {
            return false;
        }
        saved.put(motion.id(), motion);
        return write(family, saved, manager);
    }

    /**
     * Takes the motion of {@code id} out of the Motion Lab's file, so the
     * mod's own or a pack's is in force again, and reads every motion
     * again. Answers whether the file was written.
     */
    public static synchronized boolean forget(String id,
                                              IResourceManager manager) {
        String family = family(id);
        Map<String, Motion> saved = savedMotions(family);
        if (saved == null) {
            return false;
        }
        if (saved.remove(id) == null) {
            return true;
        }
        return write(family, saved, manager);
    }

    /** The motions the Motion Lab's file of a family holds, or null when it has no file there. */
    private static Map<String, Motion> savedMotions(String family) {
        File file = savedFile(family);
        if (file == null) {
            return null;
        }
        String text = file.isFile() ? savedText(family) : "{}";
        return new LinkedHashMap<String, Motion>(
                MotionCodec.read(text == null ? "{}" : text).motions());
    }

    private static boolean write(String family, Map<String, Motion> saved,
                                 IResourceManager manager) {
        File file = savedFile(family);
        try {
            if (saved.isEmpty()) {
                if (file.isFile() && !file.delete()) {
                    log(file.getPath() + " could not be removed");
                    return false;
                }
            } else {
                File folder = file.getParentFile();
                if (!folder.isDirectory() && !folder.mkdirs()) {
                    log(folder.getPath() + " could not be made");
                    return false;
                }
                File written = new File(folder, file.getName() + ".tmp");
                java.io.Writer writer = new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(written),
                        StandardCharsets.UTF_8);
                try {
                    writer.write(MotionCodec.write(saved));
                } finally {
                    writer.close();
                }
                if (file.isFile() && !file.delete()) {
                    log(file.getPath() + " could not be replaced");
                    return false;
                }
                if (!written.renameTo(file)) {
                    log(written.getPath() + " could not be moved into place");
                    return false;
                }
            }
        } catch (IOException unwritable) {
            log(file.getPath() + " could not be written: " + unwritable);
            return false;
        }
        reload(manager);
        return true;
    }

    /** Every motion id in force, in file order. */
    public static List<String> ids() {
        return Collections.unmodifiableList(
                new java.util.ArrayList<String>(motions().keySet()));
    }

    /** The family a motion's file is, or empty. */
    public static String family(String id) {
        motions();
        String family = familyOf.get(id);
        return family == null ? "" : family;
    }

    /** The motion as the mod's own file writes it, before any pack or saved tuning. */
    public static Motion bundled(String id) {
        motions();
        return bundled.get(id);
    }

    /**
     * How long a beat of the motion's leading part takes, as a beat that
     * fades or changes in place ({@link #beatNanos}): {@link Motion#ON}
     * unless named. None while motion is off.
     */
    public static long nanos(String id) {
        return nanos(id, Motion.ON);
    }

    public static long nanos(String id, String beat) {
        MotionBeat found = get(id).beat(beat);
        return found == null ? 0L : beatNanos(found.durationMillis());
    }

    /**
     * How long a beat that moves something through space takes: its
     * time at the speed in force, and none under reduced motion, which
     * sets travel down at its end at once.
     */
    public static long travelNanos(String id) {
        return travelNanos(id, Motion.ON);
    }

    public static long travelNanos(String id, String beat) {
        MotionBeat found = get(id).beat(beat);
        return found == null || reduced() ? 0L
                : scaledNanos(found.durationMillis());
    }

    /** The curve a beat of the motion's leading part travels on, as reduced motion allows. */
    public static MotionCurve curve(String id) {
        return curve(id, Motion.ON);
    }

    public static MotionCurve curve(String id, String beat) {
        MotionBeat found = get(id).beat(beat);
        MotionCurve curve = found == null ? MotionCurve.EASE_OUT : found.curve();
        return reduced() ? curve.reduced() : curve;
    }

    /**
     * A number the drawing code reads from the motion by name. A name the
     * motion lacks reads as {@code fallback}, and is named in the log the
     * first time.
     */
    public static float param(String id, String name, float fallback) {
        float value = get(id).param(name);
        if (Float.isNaN(value)) {
            report("param:" + id + ":" + name, "Motion '" + id
                    + "' has no param '" + name + "'; " + fallback + " is used");
            return fallback;
        }
        return value;
    }

    /**
     * One step of a follower that fades or changes in place toward
     * {@code target}: most of the way in the motion's time at the speed
     * in force, at once while motion is off. Frame-rate independent, as
     * {@link LostTalesGuiEasing#approach}.
     */
    public static double follow(String id, double current, double target,
                                double elapsedSeconds) {
        if (!enabled()) {
            return target;
        }
        return LostTalesGuiEasing.approach(current, target, elapsedSeconds,
                get(id).followSeconds() / speed());
    }

    /**
     * The same for a follower that moves something through space, such
     * as a scroll: at once under reduced motion as well.
     */
    public static double followTravel(String id, double current,
                                      double target, double elapsedSeconds) {
        return reduced() ? target
                : follow(id, current, target, elapsedSeconds);
    }

    /* ---- loading ---- */

    private static Map<String, Motion> motions() {
        Map<String, Motion> motions = current;
        if (motions == null) {
            synchronized (Motions.class) {
                if (current == null) {
                    load(null);
                }
                motions = current;
            }
        }
        return motions;
    }

    /** Reads every family again: the mod's own, the packs', the Motion Lab's. */
    public static synchronized void reload(IResourceManager manager) {
        synchronized (REPORTED) {
            REPORTED.clear();
        }
        load(manager);
    }

    private static void load(IResourceManager manager) {
        Map<String, Motion> motions = new LinkedHashMap<String, Motion>();
        Map<String, String> families = new LinkedHashMap<String, String>();
        Map<String, Motion> own = new LinkedHashMap<String, Motion>();
        for (String family : FAMILIES) {
            String name = family + ".json";
            MotionCodec.Result base = read(classpathText(family), "the mod's "
                    + name);
            own.putAll(base.motions());
            merge(motions, families, family, base);
            if (manager != null) {
                String packed = packText(manager, family);
                if (packed != null) {
                    merge(motions, families, family,
                            read(packed, "the resource packs' " + name));
                }
            }
            String saved = savedText(family);
            if (saved != null) {
                merge(motions, families, family,
                        read(saved, "the Motion Lab's " + name));
            }
        }
        bundled = Collections.unmodifiableMap(own);
        familyOf = Collections.unmodifiableMap(families);
        current = Collections.unmodifiableMap(motions);
    }

    private static void merge(Map<String, Motion> motions,
                              Map<String, String> families, String family,
                              MotionCodec.Result result) {
        for (Map.Entry<String, Motion> motion : result.motions().entrySet()) {
            motions.put(motion.getKey(), motion.getValue());
            families.put(motion.getKey(), family);
        }
    }

    private static MotionCodec.Result read(String text, String source) {
        MotionCodec.Result result = MotionCodec.read(text);
        if (!result.problems().isEmpty()) {
            log(source + (result.isReadable() ? " has " : " is not read: ")
                    + result.problems().size() + " problem"
                    + (result.problems().size() == 1 ? "" : "s") + "; the first: "
                    + result.problems().get(0));
        }
        return result;
    }

    private static String classpathText(String family) {
        InputStream stream = Motions.class.getResourceAsStream("/assets/"
                + LostTalesMetaData.MOD_ID + "/motion/" + family + ".json");
        return stream == null ? "{}" : text(stream);
    }

    private static String packText(IResourceManager manager, String family) {
        try {
            IResource resource = manager.getResource(new ResourceLocation(
                    LostTalesMetaData.MOD_ID, "motion/" + family + ".json"));
            return text(resource.getInputStream());
        } catch (IOException missing) {
            return null;
        } catch (RuntimeException broken) {
            log("the resource packs' " + family + ".json could not be read: "
                    + broken);
            return null;
        }
    }

    private static String savedText(String family) {
        File file = savedFile(family);
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return text(new FileInputStream(file));
        } catch (IOException unreadable) {
            log(file.getPath() + " could not be read: " + unreadable);
            return null;
        }
    }

    /** The file the Motion Lab saves a family's tuning to, or null before the client folder is known. */
    public static synchronized File savedFile(String family) {
        return overrideFolder == null || !FAMILIES.contains(family) ? null
                : new File(overrideFolder, family + ".json");
    }

    /** A stream's text, as far as the largest motion file; the stream is closed. */
    private static String text(InputStream stream) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                bytes.write(buffer, 0, read);
                if (bytes.size() > MotionCodec.MAX_FILE_BYTES) {
                    return null;
                }
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return null;
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Nothing more to read from it either way.
            }
        }
    }

    private static void report(String key, String message) {
        synchronized (REPORTED) {
            if (!REPORTED.add(key)) {
                return;
            }
        }
        log(message);
    }

    private static void log(String message) {
        try {
            FMLLog.warning("[losttales] %s", message);
        } catch (Throwable ignored) {
            // Early bootstrap and unit tests may not have an FML logger.
        }
    }
}
