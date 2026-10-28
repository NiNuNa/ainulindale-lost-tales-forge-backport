package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.util.LostTalesLog;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneHouseFiles;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * The markers the map marker files hold (MOD-17): the bundled ones, read
 * off the mod's own classpath, and while a server runs its own from
 * {@code config/losttales/map_markers/}, each in place of a bundled one of
 * its id or beside them. The server checks quests, commands and waystones
 * against them and seeds its store with them. Each client reads the
 * bundled files through its resource packs with the same reader
 * ({@link #readBundled}), and gets a server's own markers only in their
 * snapshots. The bundled markers alone register LOTR waypoints and stand
 * on the main menu ({@link #getBundledMarkers}).
 */
public final class LostTalesMapMarkerCatalog {
    private static final Comparator<LostTalesMapMarkerDefinition> BY_NAME =
            new Comparator<LostTalesMapMarkerDefinition>() {
                @Override
                public int compare(LostTalesMapMarkerDefinition left,
                                   LostTalesMapMarkerDefinition right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };

    private static Map<String, LostTalesMapMarkerDefinition> bundledById =
            Collections.<String, LostTalesMapMarkerDefinition>emptyMap();
    private static List<LostTalesMapMarkerDefinition> bundledSorted =
            Collections.<LostTalesMapMarkerDefinition>emptyList();
    private static Map<String, LostTalesMapMarkerDefinition> serverById =
            Collections.<String, LostTalesMapMarkerDefinition>emptyMap();
    private static Map<String, LostTalesMapMarkerDefinition> mergedById =
            Collections.<String, LostTalesMapMarkerDefinition>emptyMap();
    private static List<LostTalesMapMarkerDefinition> mergedSorted =
            Collections.<LostTalesMapMarkerDefinition>emptyList();
    private static boolean loaded;
    private static boolean serverRead;
    private static int revision;

    private LostTalesMapMarkerCatalog() {}

    public static synchronized void ensureLoaded() {
        if (!loaded) {
            reloadFromClasspath();
        }
    }

    /** Reads the bundled files again; answers why each file left out was left out. */
    public static synchronized List<String> reloadFromClasspath() {
        List<String> problems = new ArrayList<String>();
        List<LostTalesMapMarkerDefinition> bundled =
                readBundled(LostTalesMapMarkerFiles.CLASSPATH, problems);
        for (String problem : problems) {
            LostTalesLog.warning("Bundled map marker file left out: %s", problem);
        }
        Map<String, LostTalesMapMarkerDefinition> byId =
                new LinkedHashMap<String, LostTalesMapMarkerDefinition>();
        for (LostTalesMapMarkerDefinition marker : bundled) {
            byId.put(marker.getId(), marker);
        }
        bundledById = Collections.unmodifiableMap(byId);
        bundledSorted = sorted(byId.values());
        loaded = true;
        merge();
        return problems;
    }

    /**
     * The bundled markers {@code source} holds, checked against the houses
     * its structure index lists; why each file was left out goes to
     * {@code problems}.
     */
    public static List<LostTalesMapMarkerDefinition> readBundled(DataFiles.Source source,
                                                                 List<String> problems) {
        Set<String> houses = LostTalesWaystoneHouseFiles.bundledIds(source);
        DataFiles.Result<LostTalesMapMarkerFiles.MarkerFile> files = DataFiles.readBundled(
                source, LostTalesMapMarkerFiles.INDEX, LostTalesMapMarkerFiles.LIST_KEY,
                LostTalesMapMarkerFiles.bundled(houses));
        problems.addAll(files.problems);
        return new ArrayList<LostTalesMapMarkerDefinition>(
                LostTalesMapMarkerFiles.join(files.values, problems).values());
    }

    /**
     * Reads a server's own files from {@code folder}, each marker checked
     * against {@code houses}, the houses that loaded; answers why each file
     * left out was left out. Called as the server starts.
     */
    public static synchronized List<String> loadServerFiles(File folder, Set<String> houses) {
        ensureLoaded();
        List<String> problems = new ArrayList<String>();
        DataFiles.Result<LostTalesMapMarkerFiles.MarkerFile> files = DataFiles.readFolder(
                folder, LostTalesMapMarkerFiles.server(
                        houses == null ? Collections.<String>emptySet() : houses,
                        bundledById.keySet()));
        List<String> said = new ArrayList<String>(files.problems);
        Map<String, LostTalesMapMarkerDefinition> own =
                LostTalesMapMarkerFiles.join(files.values, said);
        for (String problem : said) {
            LostTalesLog.warning("Server map marker file left out: %s", problem);
            problems.add("Server map marker file: " + problem);
        }
        serverById = Collections.unmodifiableMap(own);
        serverRead = true;
        merge();
        return problems;
    }

    /** What a read of every marker file found, bundled and a server's own, not yet in place. */
    public static final class Read {
        private final Map<String, LostTalesMapMarkerDefinition> bundled;
        private final Map<String, LostTalesMapMarkerDefinition> server;
        private final List<String> problems;

        private Read(Map<String, LostTalesMapMarkerDefinition> bundled,
                     Map<String, LostTalesMapMarkerDefinition> server, List<String> problems) {
            this.bundled = bundled;
            this.server = server;
            this.problems = Collections.unmodifiableList(problems);
        }

        /** Why each file left out was left out, the bundled files' first. */
        public List<String> getProblems() {
            return this.problems;
        }
    }

    /**
     * Reads the bundled files and a server's own from {@code folder}, each
     * marker checked against {@code houses}, without putting them in place
     * ({@link #put}); every file left out is logged.
     */
    public static Read read(File folder, Set<String> houses) {
        List<String> problems = new ArrayList<String>();
        List<String> bundledSaid = new ArrayList<String>();
        Map<String, LostTalesMapMarkerDefinition> bundled =
                new LinkedHashMap<String, LostTalesMapMarkerDefinition>();
        for (LostTalesMapMarkerDefinition marker
                : readBundled(LostTalesMapMarkerFiles.CLASSPATH, bundledSaid)) {
            bundled.put(marker.getId(), marker);
        }
        for (String problem : bundledSaid) {
            LostTalesLog.warning("Bundled map marker file left out: %s", problem);
            problems.add(problem);
        }
        DataFiles.Result<LostTalesMapMarkerFiles.MarkerFile> files = DataFiles.readFolder(
                folder, LostTalesMapMarkerFiles.server(
                        houses == null ? Collections.<String>emptySet() : houses,
                        bundled.keySet()));
        List<String> said = new ArrayList<String>(files.problems);
        Map<String, LostTalesMapMarkerDefinition> own =
                LostTalesMapMarkerFiles.join(files.values, said);
        for (String problem : said) {
            LostTalesLog.warning("Server map marker file left out: %s", problem);
            problems.add("Server map marker file: " + problem);
        }
        return new Read(bundled, own, problems);
    }

    /** Puts what {@link #read} found in place, bundled and the server's own at once. */
    public static synchronized void put(Read read) {
        bundledById = Collections.unmodifiableMap(
                new LinkedHashMap<String, LostTalesMapMarkerDefinition>(read.bundled));
        bundledSorted = sorted(bundledById.values());
        serverById = Collections.unmodifiableMap(
                new LinkedHashMap<String, LostTalesMapMarkerDefinition>(read.server));
        loaded = true;
        serverRead = true;
        merge();
    }

    /** Forgets a server's own markers as it stops; the bundled ones stay. */
    public static synchronized void clearServerFiles() {
        serverById = Collections.<String, LostTalesMapMarkerDefinition>emptyMap();
        serverRead = false;
        merge();
    }

    /** Whether the server's own files were read since it started: the store seeds itself only then. */
    public static synchronized boolean isServerRead() {
        return serverRead;
    }

    /** Counts up with every read, so the store seeds itself again after one. */
    public static synchronized int revision() {
        return revision;
    }

    /** Every marker, bundled and the server's own, by name. */
    public static synchronized Collection<LostTalesMapMarkerDefinition> getMarkers() {
        ensureLoaded();
        return mergedSorted;
    }

    public static synchronized Set<String> getMarkerIds() {
        ensureLoaded();
        return Collections.unmodifiableSet(new LinkedHashSet<String>(mergedById.keySet()));
    }

    public static synchronized LostTalesMapMarkerDefinition getMarker(String markerId) {
        ensureLoaded();
        return mergedById.get(LostTalesQuestMarkerHelper.normalizeMarkerId(markerId));
    }

    public static synchronized boolean containsMarker(String markerId) {
        ensureLoaded();
        return mergedById.containsKey(LostTalesQuestMarkerHelper.normalizeMarkerId(markerId));
    }

    /** The bundled markers alone, by name: the ones LOTR waypoints and the main menu know. */
    public static synchronized Collection<LostTalesMapMarkerDefinition> getBundledMarkers() {
        ensureLoaded();
        return bundledSorted;
    }

    /** A bundled marker as its file has it; null for any other. */
    public static synchronized LostTalesMapMarkerDefinition getBundledMarker(String markerId) {
        ensureLoaded();
        return bundledById.get(LostTalesQuestMarkerHelper.normalizeMarkerId(markerId));
    }

    /**
     * A marker by its id and its name, {@code losttales:mossy_cave
     * (Mossy Cave)}, the name in each reader's language; any other id alone.
     */
    public static IChatComponent getDisplayName(String markerId) {
        LostTalesMapMarkerDefinition marker = getMarker(markerId);
        if (marker == null) {
            return new ChatComponentText(
                    LostTalesQuestMarkerHelper.normalizeMarkerId(markerId));
        }
        return new ChatComponentTranslation("chat.losttales.marker.with_id",
                marker.getId(), LostTalesMapMarkerNames.component(
                        marker.getId(), marker.getName()));
    }

    public static boolean isVisibleByDefault(String markerId) {
        LostTalesMapMarkerDefinition marker = getMarker(markerId);
        // This method describes discovery secrecy only. Region visibility is
        // character-specific and must not invalidate an otherwise valid track.
        return marker != null && (!marker.isDiscoverable()
                || !marker.isHiddenUntilDiscovered());
    }

    public static void logQuestMarkerWarnings(Collection<LostTalesQuestDefinition> quests) {
        if (quests == null || quests.isEmpty()) {
            return;
        }
        ensureLoaded();
        for (LostTalesQuestDefinition quest : quests) {
            if (quest == null) {
                continue;
            }
            for (String markerId : LostTalesQuestMarkerHelper.collectStaticQuestMarkerIds(quest)) {
                if (!containsMarker(markerId)) {
                    LostTalesLog.warning("Quest %s references missing map marker id: %s",
                            quest.getId(), markerId);
                }
            }
        }
    }

    private static void merge() {
        Map<String, LostTalesMapMarkerDefinition> byId =
                new LinkedHashMap<String, LostTalesMapMarkerDefinition>(bundledById);
        byId.putAll(serverById);
        mergedById = Collections.unmodifiableMap(byId);
        mergedSorted = sorted(byId.values());
        revision++;
    }

    private static List<LostTalesMapMarkerDefinition> sorted(
            Collection<LostTalesMapMarkerDefinition> markers) {
        List<LostTalesMapMarkerDefinition> sorted =
                new ArrayList<LostTalesMapMarkerDefinition>(markers);
        Collections.sort(sorted, BY_NAME);
        return Collections.unmodifiableList(sorted);
    }
}
