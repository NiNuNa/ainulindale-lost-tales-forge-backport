package com.ninuna.losttales.character.lore;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.validation.CharacterNames;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.util.LostTalesIdentifiers;
import com.ninuna.losttales.util.LostTalesLog;
import cpw.mods.fml.common.FMLLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The lore characters the logical server knows (CHAR-43): the bundled
 * files, with a server's own in {@code config/losttales/lore_characters/}
 * in place of those of the same id and its new ones after, read through
 * {@link DataFiles} as the server starts, after its races, and on
 * {@code /losttales reload lore}, against the races in place then; cleared
 * as it stops. Before the server starts it holds none.
 *
 * <p>No two hold one name, as {@link CharacterNames#key} compares names: a
 * server's file whose name another figure has is left out, and so is a
 * later bundled one.</p>
 */
public final class LoreCharacterRegistry {

    private static Map<String, LoreCharacterDefinition> byId =
            Collections.<String, LoreCharacterDefinition>emptyMap();
    private static Map<String, LoreCharacterDefinition> byName =
            Collections.<String, LoreCharacterDefinition>emptyMap();
    private static List<LoreCharacterDefinition> sorted =
            Collections.<LoreCharacterDefinition>emptyList();
    private static List<String> problems = Collections.<String>emptyList();
    private static boolean loaded;

    private LoreCharacterRegistry() {}

    /**
     * Reads the bundled files and the server's own again, against the
     * server's races and LOTR's factions; answers why each file left out
     * was left out.
     */
    public static synchronized List<String> load() {
        return load(ProgressionFiles.folder(LoreCharacterFiles.FOLDER));
    }

    /** The bundled files and those in {@code folder}, none for null, against the running game. */
    public static synchronized List<String> load(File folder) {
        return load(ProgressionFiles.CLASSPATH, folder, LoreCharacterFiles.GAME);
    }

    /** The bundled files {@code bundledSource} holds and those in {@code folder}, against {@code game}. */
    public static synchronized List<String> load(DataFiles.Source bundledSource, File folder,
                                                 LoreCharacterFiles.Game game) {
        DataFiles.Kind<LoreCharacterDefinition> kind = LoreCharacterFiles.kind(game);
        DataFiles.Result<LoreCharacterDefinition> bundled = DataFiles.readBundled(
                bundledSource, LoreCharacterFiles.INDEX, LoreCharacterFiles.LIST_KEY, kind);
        DataFiles.Result<LoreCharacterDefinition> server = folder == null
                ? new DataFiles.Result<LoreCharacterDefinition>(
                        new ArrayList<LoreCharacterDefinition>(), new ArrayList<String>())
                : DataFiles.readFolder(folder, kind);
        List<String> found = new ArrayList<String>();
        note(found, "Bundled lore character", bundled.problems);
        note(found, "Server lore character", server.problems);

        List<String> clashes = new ArrayList<String>();
        Map<String, String> names = new LinkedHashMap<String, String>();
        List<LoreCharacterDefinition> bundledKept = new ArrayList<LoreCharacterDefinition>();
        for (LoreCharacterDefinition definition : bundled.values) {
            if (takeName(names, definition, clashes)) {
                bundledKept.add(definition);
            }
        }
        note(found, "Bundled lore character", clashes);
        clashes.clear();
        List<LoreCharacterDefinition> serverKept = new ArrayList<LoreCharacterDefinition>();
        for (LoreCharacterDefinition definition : server.values) {
            if (takeName(names, definition, clashes)) {
                serverKept.add(definition);
            }
        }
        note(found, "Server lore character", clashes);

        Map<String, LoreCharacterDefinition> ids =
                new LinkedHashMap<String, LoreCharacterDefinition>();
        Map<String, LoreCharacterDefinition> keys =
                new LinkedHashMap<String, LoreCharacterDefinition>();
        for (LoreCharacterDefinition definition
                : DataFiles.merge(bundledKept, serverKept, kind)) {
            ids.put(definition.getId(), definition);
            keys.put(normalizeName(definition.getName()), definition);
        }
        byId = Collections.unmodifiableMap(ids);
        byName = Collections.unmodifiableMap(keys);
        sorted = sortedByName(ids.values());
        problems = Collections.unmodifiableList(found);
        loaded = true;
        logInfo("[%s] %d lore characters, %d of them the server's own; %d warnings left files out",
                LostTalesMetaData.MOD_ID, Integer.valueOf(ids.size()),
                Integer.valueOf(serverKept.size()), Integer.valueOf(found.size()));
        return problems;
    }

    /** Forgets every figure, as the server stops. */
    public static synchronized void clear() {
        byId = Collections.<String, LoreCharacterDefinition>emptyMap();
        byName = Collections.<String, LoreCharacterDefinition>emptyMap();
        sorted = Collections.<LoreCharacterDefinition>emptyList();
        problems = Collections.<String>emptyList();
        loaded = false;
    }

    /** Whether the files have been read since the server started. */
    public static synchronized boolean isLoaded() {
        return loaded;
    }

    public static synchronized LoreCharacterDefinition get(String id) {
        return byId.get(LostTalesIdentifiers.normalize(id));
    }

    public static synchronized LoreCharacterDefinition getByName(String name) {
        return byName.get(normalizeName(name));
    }

    /** Every figure, by name. */
    public static synchronized Collection<LoreCharacterDefinition> getAll() {
        return sorted;
    }

    /** Why each file left out at the last read was left out. */
    public static synchronized List<String> getLoadErrors() {
        return problems;
    }

    static String normalizeName(String name) {
        return CharacterNames.key(name);
    }

    /**
     * Gives {@code definition} its name unless another figure holds it;
     * a server's figure of a bundled id lets go of the bundled one's name.
     */
    private static boolean takeName(Map<String, String> names,
                                    LoreCharacterDefinition definition,
                                    List<String> clashes) {
        String key = normalizeName(definition.getName());
        String holder = names.get(key);
        if (holder != null && !holder.equals(definition.getId())) {
            clashes.add(definition.getId() + ": its name " + definition.getName()
                    + " is the lore character " + holder + "'s");
            return false;
        }
        Iterator<Map.Entry<String, String>> held = names.entrySet().iterator();
        while (held.hasNext()) {
            if (held.next().getValue().equals(definition.getId())) {
                held.remove();
            }
        }
        names.put(key, definition.getId());
        return true;
    }

    private static List<LoreCharacterDefinition> sortedByName(
            Collection<LoreCharacterDefinition> definitions) {
        List<LoreCharacterDefinition> values =
                new ArrayList<LoreCharacterDefinition>(definitions);
        Collections.sort(values, new Comparator<LoreCharacterDefinition>() {
            @Override
            public int compare(LoreCharacterDefinition left,
                               LoreCharacterDefinition right) {
                int name = left.getName().compareToIgnoreCase(right.getName());
                return name != 0 ? name
                        : left.getId().compareToIgnoreCase(right.getId());
            }
        });
        return Collections.unmodifiableList(values);
    }

    private static void note(List<String> problems, String what, List<String> found) {
        for (String problem : found) {
            problems.add(problem);
            LostTalesLog.warning("%s left out: %s", what, problem);
        }
    }

    private static void logInfo(String format, Object... arguments) {
        try {
            FMLLog.info(format, arguments);
        } catch (RuntimeException unavailable) {
            // FML's logger is not set up outside a running game.
        }
    }
}
