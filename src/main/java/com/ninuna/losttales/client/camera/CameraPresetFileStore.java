package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.client.diagnostics.LostTalesClientDiagnostics;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Loads editable client camera presets from config/losttales. */
public final class CameraPresetFileStore {
    public static final int MAX_PRESET_FILES = 128;
    public static final long MAX_JSON_BYTES = 128L * 1024L;

    private static final String PRESET_DIRECTORY =
            "losttales/camera_presets";
    private static final String BUNDLED_RESOURCE_ROOT =
            "/assets/losttales/camera_presets/";
    private static final String[] BUNDLED_FILE_NAMES = {
            "modern_action_rpg.json"
    };
    private static final Set<String> RETIRED_BUILT_IN_IDS =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                    "wide_exploration", "vanilla_plus")));
    private static final Pattern SAFE_FILE_NAME = Pattern.compile(
            "[a-z0-9_.-]{1,80}\\.json", Pattern.CASE_INSENSITIVE);

    private static volatile Map<String, CameraPresetDefinition> presets =
            createFallbackDefinitions();
    private static volatile boolean loaded;
    private static File presetDirectory;

    private CameraPresetFileStore() {}

    public static synchronized void initialize(File configDirectory) {
        if (configDirectory == null) {
            throw new IllegalArgumentException(
                    "configDirectory is required");
        }
        presetDirectory = new File(configDirectory, PRESET_DIRECTORY);
        loaded = false;
        reload();
    }

    public static synchronized void ensureLoaded() {
        if (!loaded) {
            reload();
        }
    }

    /**
     * Rescans the preset folder. Existing JSON is never overwritten; missing
     * bundled templates are installed so players have editable examples.
     */
    public static synchronized void reload() {
        Map<String, CameraPresetDefinition> rebuilt =
                new LinkedHashMap<String, CameraPresetDefinition>(
                        createFallbackDefinitions());
        File directory = presetDirectory;
        if (directory != null && ensureDirectory(directory)) {
            installBundledTemplates(directory);
            loadFiles(directory, rebuilt);
        }
        presets = Collections.unmodifiableMap(rebuilt);
        loaded = true;
    }

    public static CameraPreset getPreset(String id) {
        CameraPresetDefinition definition = presets.get(normalizeId(id));
        if (definition == null) {
            definition = presets.get(
                    CameraPresetId.MODERN_ACTION_RPG.getConfigValue());
        }
        return definition == null
                ? CameraPreset.modernActionRpgDefaults()
                : definition.getPreset();
    }

    public static CameraPresetDefinition getDefinition(String id) {
        return presets.get(normalizeId(id));
    }

    public static String[] getConfigValues() {
        return presets.keySet().toArray(new String[presets.size()]);
    }

    public static File getPresetDirectory() {
        File directory = presetDirectory;
        return directory == null ? null : directory.getAbsoluteFile();
    }

    public static String normalizeId(String id) {
        if (id == null) {
            return CameraPresetId.MODERN_ACTION_RPG.getConfigValue();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]{1,64}")) {
            return CameraPresetId.MODERN_ACTION_RPG.getConfigValue();
        }
        return normalized;
    }

    private static boolean ensureDirectory(File directory) {
        if (directory.isDirectory()) {
            return true;
        }
        if (!directory.exists() && directory.mkdirs()
                && directory.isDirectory()) {
            return true;
        }
        LostTalesClientDiagnostics.warnOnce(
                "camera-preset-directory",
                "Camera preset directory is unavailable: "
                        + directory.getAbsolutePath(),
                new IOException("Unable to create or access directory"));
        return false;
    }

    private static void installBundledTemplates(File directory) {
        for (String fileName : BUNDLED_FILE_NAMES) {
            File target = new File(directory, fileName);
            if (target.exists()) {
                continue;
            }
            try {
                copyBundledTemplate(directory, fileName, target);
            } catch (Throwable throwable) {
                LostTalesClientDiagnostics.warnOnce(
                        "camera-preset-template-" + fileName,
                        "Could not install editable camera preset template "
                                + target.getAbsolutePath(),
                        throwable);
            }
        }
    }

    private static void copyBundledTemplate(
            File directory, String fileName, File target)
            throws IOException {
        File canonicalDirectory = directory.getCanonicalFile();
        File canonicalTarget = target.getCanonicalFile();
        if (!canonicalDirectory.equals(canonicalTarget.getParentFile())) {
            throw new IOException("Preset template escaped its directory");
        }

        InputStream input = CameraPresetFileStore.class.getResourceAsStream(
                BUNDLED_RESOURCE_ROOT + fileName);
        if (input == null) {
            throw new IOException("Missing bundled template " + fileName);
        }
        File temporary = new File(directory, "." + fileName + ".tmp");
        OutputStream output = null;
        try {
            output = new FileOutputStream(temporary, false);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
        } finally {
            close(output);
            close(input);
        }

        if (target.exists()) {
            temporary.delete();
            return;
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Unable to move preset template into place");
        }
    }

    private static void loadFiles(
            File directory,
            Map<String, CameraPresetDefinition> rebuilt) {
        File[] candidates = directory.listFiles();
        if (candidates == null) {
            LostTalesClientDiagnostics.warnOnce(
                    "camera-preset-list",
                    "Camera preset directory could not be listed: "
                            + directory.getAbsolutePath(),
                    new IOException("Directory listing returned null"));
            return;
        }
        Arrays.sort(candidates, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                int insensitive = left.getName().compareToIgnoreCase(
                        right.getName());
                return insensitive != 0 ? insensitive
                        : left.getName().compareTo(right.getName());
            }
        });

        File canonicalDirectory;
        try {
            canonicalDirectory = directory.getCanonicalFile();
        } catch (IOException exception) {
            LostTalesClientDiagnostics.warnOnce(
                    "camera-preset-canonical-directory",
                    "Camera preset directory could not be resolved",
                    exception);
            return;
        }

        int acceptedFiles = 0;
        Set<String> loadedIds = new HashSet<String>();
        for (File candidate : candidates) {
            if (!isSafeJsonFile(candidate, canonicalDirectory)) {
                continue;
            }
            if (acceptedFiles >= MAX_PRESET_FILES) {
                LostTalesClientDiagnostics.warnOnce(
                        "camera-preset-file-limit",
                        "Only the first " + MAX_PRESET_FILES
                                + " camera preset JSON files are loaded",
                        new IllegalArgumentException("Preset file limit"));
                break;
            }
            acceptedFiles++;
            CameraPresetDefinition definition = loadDefinition(candidate);
            if (definition == null) {
                continue;
            }
            if (RETIRED_BUILT_IN_IDS.contains(definition.getId())) {
                continue;
            }
            if (!loadedIds.add(definition.getId())) {
                LostTalesClientDiagnostics.warnOnce(
                        "camera-preset-duplicate-" + definition.getId(),
                        "Duplicate camera preset id " + definition.getId()
                                + " in " + candidate.getAbsolutePath()
                                + "; the first file remains active",
                        new IllegalArgumentException("Duplicate preset id"));
                continue;
            }
            rebuilt.put(definition.getId(), definition);
        }
    }

    private static boolean isSafeJsonFile(
            File candidate, File canonicalDirectory) {
        if (candidate == null || !candidate.isFile()
                || !SAFE_FILE_NAME.matcher(candidate.getName()).matches()) {
            return false;
        }
        if (candidate.length() > MAX_JSON_BYTES) {
            LostTalesClientDiagnostics.warnOnce(
                    "camera-preset-size-" + candidate.getName(),
                    "Camera preset exceeds " + MAX_JSON_BYTES + " bytes: "
                            + candidate.getAbsolutePath(),
                    new IllegalArgumentException("Preset file too large"));
            return false;
        }
        try {
            return canonicalDirectory.equals(
                    candidate.getCanonicalFile().getParentFile());
        } catch (IOException exception) {
            LostTalesClientDiagnostics.warnOnce(
                    "camera-preset-path-" + candidate.getName(),
                    "Camera preset path could not be resolved: "
                            + candidate.getAbsolutePath(),
                    exception);
            return false;
        }
    }

    private static CameraPresetDefinition loadDefinition(File file) {
        Reader reader = null;
        try {
            reader = new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8);
            CameraPresetJsonParser.ParseResult result =
                    CameraPresetJsonParser.parseDefinition(
                            reader, file.getAbsolutePath());
            if (result.isValid()) {
                return result.getDefinition();
            }
            throw new IllegalArgumentException(result.getErrors().toString());
        } catch (Throwable throwable) {
            LostTalesClientDiagnostics.warnOnce(
                    "camera-preset-file-" + file.getName(),
                    "Invalid camera preset " + file.getAbsolutePath()
                            + "; a compiled fallback will be used if needed",
                    throwable);
            return null;
        } finally {
            close(reader);
        }
    }

    private static Map<String, CameraPresetDefinition>
    createFallbackDefinitions() {
        Map<String, CameraPresetDefinition> fallbacks =
                new LinkedHashMap<String, CameraPresetDefinition>();
        addFallback(fallbacks, CameraPresetId.MODERN_ACTION_RPG,
                "Modern Action RPG");
        return fallbacks;
    }

    private static void addFallback(
            Map<String, CameraPresetDefinition> target,
            CameraPresetId id, String name) {
        target.put(id.getConfigValue(), new CameraPresetDefinition(
                CameraPresetDefinition.CURRENT_DATA_VERSION,
                id.getConfigValue(), name, CameraPreset.forId(id)));
    }

    private static void close(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {}
        }
    }
}
