package com.ninuna.losttales.character.lore;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-authoritative registry of unique lore-character identities.
 *
 * Bundled files and server-local additions use the same minimal format. Files
 * are loaded once during startup, and no later source may replace an identity
 * that was already registered by stable ID or normalized display name.
 */
public final class LoreCharacterRegistry {

    public static final String CLASSPATH_INDEX = "lore_characters/index.json";
    public static final String EXTERNAL_DIRECTORY = "losttales/lore_characters";
    public static final int MAX_EXTERNAL_FILES = 256;
    public static final long MAX_EXTERNAL_FILE_BYTES = 32L * 1024L;

    private static final Map<String, LoreCharacterDefinition> DEFINITIONS_BY_ID =
            new LinkedHashMap<String, LoreCharacterDefinition>();
    private static final Map<String, LoreCharacterDefinition> DEFINITIONS_BY_NAME =
            new LinkedHashMap<String, LoreCharacterDefinition>();
    private static List<LoreCharacterDefinition> sortedDefinitions =
            Collections.emptyList();
    private static List<String> loadErrors = Collections.emptyList();
    private static File externalDirectory;
    private static boolean loaded;

    private LoreCharacterRegistry() {}

    public static synchronized void load(File modConfigurationDirectory) {
        Map<String, LoreCharacterDefinition> byId =
                new LinkedHashMap<String, LoreCharacterDefinition>();
        Map<String, LoreCharacterDefinition> byName =
                new LinkedHashMap<String, LoreCharacterDefinition>();
        Map<String, String> idSources = new LinkedHashMap<String, String>();
        Map<String, String> nameSources = new LinkedHashMap<String, String>();
        List<String> errors = new ArrayList<String>();

        int bundledCount = loadBundled(
                byId, byName, idSources, nameSources, errors);
        externalDirectory = resolveExternalDirectory(modConfigurationDirectory);
        int externalCount = loadExternal(
                externalDirectory, byId, byName,
                idSources, nameSources, errors);

        DEFINITIONS_BY_ID.clear();
        DEFINITIONS_BY_ID.putAll(byId);
        DEFINITIONS_BY_NAME.clear();
        DEFINITIONS_BY_NAME.putAll(byName);
        rebuildSortedDefinitions();
        loadErrors = Collections.unmodifiableList(new ArrayList<String>(errors));
        loaded = true;

        logInfo("[%s] Registered %d unique lore characters (%d bundled, %d server-local, %d rejected files)",
                LostTalesMetaData.MOD_ID,
                Integer.valueOf(DEFINITIONS_BY_ID.size()),
                Integer.valueOf(bundledCount),
                Integer.valueOf(externalCount),
                Integer.valueOf(errors.size()));
        for (String error : errors) {
            logWarning("[%s] Lore character JSON rejected: %s",
                    LostTalesMetaData.MOD_ID, error);
        }
    }

    public static synchronized void ensureLoaded() {
        if (!loaded) {
            load(null);
        }
    }

    public static synchronized LoreCharacterDefinition get(String id) {
        ensureLoaded();
        return DEFINITIONS_BY_ID.get(normalizeIdentifier(id));
    }

    public static synchronized LoreCharacterDefinition getByName(String name) {
        ensureLoaded();
        return DEFINITIONS_BY_NAME.get(normalizeName(name));
    }

    public static synchronized boolean contains(String id) {
        return get(id) != null;
    }

    public static synchronized Collection<LoreCharacterDefinition> getAll() {
        ensureLoaded();
        return sortedDefinitions;
    }

    public static synchronized List<String> getLoadErrors() {
        ensureLoaded();
        return loadErrors;
    }

    public static synchronized File getExternalDirectory() {
        ensureLoaded();
        return externalDirectory;
    }

    private static int loadBundled(
            Map<String, LoreCharacterDefinition> byId,
            Map<String, LoreCharacterDefinition> byName,
            Map<String, String> idSources,
            Map<String, String> nameSources,
            List<String> errors) {
        Reader indexReader = null;
        List<String> files = Collections.emptyList();
        try {
            indexReader = openClasspathReader(CLASSPATH_INDEX);
            if (indexReader == null) {
                errors.add("missing bundled index assets/"
                        + LostTalesMetaData.MOD_ID + "/" + CLASSPATH_INDEX);
                return 0;
            }
            files = LoreCharacterDefinitionJsonParser.parseIndex(indexReader);
        } catch (RuntimeException e) {
            errors.add("bundled index: " + safeMessage(e));
            return 0;
        } catch (IOException e) {
            errors.add("bundled index: " + safeMessage(e));
            return 0;
        } finally {
            closeQuietly(indexReader);
        }

        int registered = 0;
        for (String file : files) {
            Reader reader = null;
            String source = "bundled:" + file;
            try {
                reader = openClasspathReader(file);
                if (reader == null) {
                    errors.add("missing bundled file " + file);
                    continue;
                }
                LoreCharacterDefinitionJsonParser.ParseResult result =
                        LoreCharacterDefinitionJsonParser.parseDefinition(
                                reader, source);
                errors.addAll(result.getErrors());
                if (result.isValid() && registerUnique(
                        result.getDefinition(), source,
                        byId, byName, idSources, nameSources, errors)) {
                    registered++;
                }
            } catch (RuntimeException e) {
                errors.add(source + ": " + safeMessage(e));
            } finally {
                closeQuietly(reader);
            }
        }
        return registered;
    }

    private static int loadExternal(
            File directory,
            Map<String, LoreCharacterDefinition> byId,
            Map<String, LoreCharacterDefinition> byName,
            Map<String, String> idSources,
            Map<String, String> nameSources,
            List<String> errors) {
        if (directory == null) {
            return 0;
        }
        if (!directory.exists() && !directory.mkdirs()) {
            errors.add("cannot create external directory " + directory.getPath());
            return 0;
        }
        if (!directory.isDirectory()) {
            errors.add("external path is not a directory: " + directory.getPath());
            return 0;
        }

        File[] candidates = directory.listFiles();
        if (candidates == null) {
            errors.add("cannot list external directory " + directory.getPath());
            return 0;
        }
        List<File> files = new ArrayList<File>();
        for (File candidate : candidates) {
            if (candidate != null && candidate.isFile()
                    && candidate.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                files.add(candidate);
            }
        }
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        if (files.size() > MAX_EXTERNAL_FILES) {
            errors.add("external directory contains " + files.size()
                    + " JSON files; only the first " + MAX_EXTERNAL_FILES
                    + " sorted files are considered");
            files = new ArrayList<File>(files.subList(0, MAX_EXTERNAL_FILES));
        }

        int registered = 0;
        for (File file : files) {
            String source = "config:" + file.getName();
            if (!isDirectChild(directory, file)) {
                errors.add(source + " escapes the lore-character directory");
                continue;
            }
            if (file.length() < 0L || file.length() > MAX_EXTERNAL_FILE_BYTES) {
                errors.add(source + " exceeds " + MAX_EXTERNAL_FILE_BYTES + " bytes");
                continue;
            }
            Reader reader = null;
            try {
                reader = new InputStreamReader(
                        new FileInputStream(file), StandardCharsets.UTF_8);
                LoreCharacterDefinitionJsonParser.ParseResult result =
                        LoreCharacterDefinitionJsonParser.parseDefinition(
                                reader, source);
                errors.addAll(result.getErrors());
                if (result.isValid() && registerUnique(
                        result.getDefinition(), source,
                        byId, byName, idSources, nameSources, errors)) {
                    registered++;
                }
            } catch (FileNotFoundException e) {
                errors.add(source + ": " + safeMessage(e));
            } finally {
                closeQuietly(reader);
            }
        }
        return registered;
    }

    private static boolean registerUnique(
            LoreCharacterDefinition definition,
            String source,
            Map<String, LoreCharacterDefinition> byId,
            Map<String, LoreCharacterDefinition> byName,
            Map<String, String> idSources,
            Map<String, String> nameSources,
            List<String> errors) {
        String id = definition.getId();
        if (byId.containsKey(id)) {
            errors.add(source + " duplicates id " + id
                    + " already registered by " + idSources.get(id));
            return false;
        }
        String normalizedName = normalizeName(definition.getName());
        if (normalizedName.length() == 0) {
            errors.add(source + " has a display name without letters or numbers");
            return false;
        }
        if (byName.containsKey(normalizedName)) {
            errors.add(source + " duplicates display name " + definition.getName()
                    + " already registered by " + nameSources.get(normalizedName));
            return false;
        }

        byId.put(id, definition);
        byName.put(normalizedName, definition);
        idSources.put(id, source);
        nameSources.put(normalizedName, source);
        return true;
    }

    static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(name.trim(), Normalizer.Form.NFKD);
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < decomposed.length(); index++) {
            char character = decomposed.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                normalized.append(Character.toLowerCase(character));
            }
        }
        return normalized.toString();
    }

    private static File resolveExternalDirectory(File modConfigurationDirectory) {
        return modConfigurationDirectory == null ? null
                : new File(modConfigurationDirectory, EXTERNAL_DIRECTORY);
    }

    private static Reader openClasspathReader(String file) {
        String path = "assets/" + LostTalesMetaData.MOD_ID + "/" + file;
        InputStream stream = LoreCharacterRegistry.class.getClassLoader()
                .getResourceAsStream(path);
        return stream == null ? null
                : new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private static boolean isDirectChild(File directory, File file) {
        try {
            return directory.getCanonicalFile().equals(
                    file.getCanonicalFile().getParentFile());
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void rebuildSortedDefinitions() {
        List<LoreCharacterDefinition> values =
                new ArrayList<LoreCharacterDefinition>(DEFINITIONS_BY_ID.values());
        Collections.sort(values, new Comparator<LoreCharacterDefinition>() {
            @Override
            public int compare(LoreCharacterDefinition left,
                               LoreCharacterDefinition right) {
                int name = left.getName().compareToIgnoreCase(right.getName());
                return name != 0 ? name
                        : left.getId().compareToIgnoreCase(right.getId());
            }
        });
        sortedDefinitions = Collections.unmodifiableList(values);
    }

    private static String normalizeIdentifier(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.length() == 0
                ? throwable.getClass().getSimpleName() : message;
    }

    private static void closeQuietly(Reader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {}
        }
    }

    private static void logInfo(String format, Object... arguments) {
        try {
            FMLLog.info(format, arguments);
        } catch (RuntimeException ignored) {}
    }

    private static void logWarning(String format, Object... arguments) {
        try {
            FMLLog.warning(format, arguments);
        } catch (RuntimeException ignored) {}
    }
}
