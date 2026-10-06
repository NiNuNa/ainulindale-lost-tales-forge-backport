package com.ninuna.losttales.quest;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import cpw.mods.fml.common.FMLLog;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * Every quest the logical server knows, from three places: the quest files
 * bundled with the mod, read from its classpath; the files a server writes
 * in its own folder ({@link ServerQuestFiles}), read as it starts; and the
 * quests made while the game runs, missives. A bundled quest's id is never
 * taken by the other two, nor a server file's by a missive.
 */
public final class LostTalesQuestRegistry {
    private static final Map<String, LostTalesQuestDefinition> STATIC_QUESTS_BY_ID = new LinkedHashMap<String, LostTalesQuestDefinition>();
    private static final Map<String, LostTalesQuestDefinition> SERVER_QUESTS_BY_ID = new LinkedHashMap<String, LostTalesQuestDefinition>();
    /** Counts the reads of the server's quest files, so what was built from one read knows when it is stale. */
    private static int serverQuestRevision;
    private static final Map<String, LostTalesQuestDefinition> RUNTIME_QUESTS_BY_ID = new LinkedHashMap<String, LostTalesQuestDefinition>();
    private static List<LostTalesQuestDefinition> sortedQuests = Collections.emptyList();
    private static boolean loaded;

    private LostTalesQuestRegistry() {}

    public static synchronized void loadFromClasspath() {
        BundledQuestFiles.Result read = BundledQuestFiles.read(CLASSPATH);
        for (String problem : read.problems) {
            FMLLog.warning("[%s] Bundled quest left out: %s",
                    LostTalesMetaData.MOD_ID, problem);
        }

        STATIC_QUESTS_BY_ID.clear();
        for (LostTalesQuestDefinition quest : read.quests) {
            STATIC_QUESTS_BY_ID.put(quest.getId(), quest);
        }
        rebuildSortedQuests();
        loaded = true;

        LostTalesMapMarkerCatalog.reloadFromClasspath();
        LostTalesMapMarkerCatalog.logQuestMarkerWarnings(sortedQuests);
        LostTalesQuestDefinitionValidator.logWarnings(sortedQuests);
    }

    public static synchronized void ensureLoaded() {
        if (!loaded) {
            loadFromClasspath();
        }
    }

    public static synchronized LostTalesQuestDefinition getQuest(String questId) {
        ensureLoaded();
        LostTalesQuestDefinition fileQuest = STATIC_QUESTS_BY_ID.get(questId);
        if (fileQuest != null) {
            return fileQuest;
        }
        LostTalesQuestDefinition serverQuest = SERVER_QUESTS_BY_ID.get(questId);
        return serverQuest != null ? serverQuest : RUNTIME_QUESTS_BY_ID.get(questId);
    }

    /**
     * Reads the server's own quest files again ({@link ServerQuestFiles}),
     * in place of those read before, and logs every file left out and why.
     * Answers what the read found.
     */
    public static synchronized ServerQuestFiles.Result loadServerQuests() {
        ensureLoaded();
        ServerQuestFiles.Result result = ServerQuestFiles.read(
                ServerQuestFiles.directory(), STATIC_QUESTS_BY_ID.keySet());
        serverQuestRevision++;
        SERVER_QUESTS_BY_ID.clear();
        for (LostTalesQuestDefinition quest : result.quests) {
            SERVER_QUESTS_BY_ID.put(quest.getId(), quest);
            // A server file's quest stands over a runtime quest of its id.
            RUNTIME_QUESTS_BY_ID.remove(quest.getId());
        }
        rebuildSortedQuests();
        for (String problem : result.problems) {
            FMLLog.warning("[%s] Server quest left out: %s",
                    LostTalesMetaData.MOD_ID, problem);
        }
        if (!result.quests.isEmpty()) {
            FMLLog.info("[%s] Read %d server quest files",
                    LostTalesMetaData.MOD_ID,
                    Integer.valueOf(result.quests.size()));
            LostTalesMapMarkerCatalog.logQuestMarkerWarnings(result.quests);
        }
        return result;
    }

    /** Forgets the server's own quests as the server stops. */
    public static synchronized void clearServerQuests() {
        serverQuestRevision++;
        if (!SERVER_QUESTS_BY_ID.isEmpty()) {
            SERVER_QUESTS_BY_ID.clear();
            rebuildSortedQuests();
        }
    }

    /** Counts the reads of the server's quest files: it changes whenever what players are sent does. */
    public static synchronized int serverQuestRevision() {
        return serverQuestRevision;
    }

    /** The server's own quests, in the order their files were read: what every player is sent. */
    public static synchronized List<LostTalesQuestDefinition> getServerQuests() {
        return Collections.unmodifiableList(
                new ArrayList<LostTalesQuestDefinition>(SERVER_QUESTS_BY_ID.values()));
    }

    public static synchronized Collection<LostTalesQuestDefinition> getQuests() {
        ensureLoaded();
        return sortedQuests;
    }

    /**
     * Registers or replaces a quest made while the game runs, a missive's.
     * It never takes an id a quest file uses: such a quest is refused, so
     * nothing made in the game can stand in for a written quest. The owning
     * system keeps the definition itself and registers it again after a load.
     */
    public static synchronized boolean registerRuntimeQuest(LostTalesQuestDefinition quest) {
        ensureLoaded();
        if (!mayRegisterRuntime(quest)) {
            return false;
        }
        RUNTIME_QUESTS_BY_ID.put(quest.getId(), quest);
        rebuildSortedQuests();
        return true;
    }

    public static synchronized int registerRuntimeQuests(Collection<LostTalesQuestDefinition> quests) {
        ensureLoaded();
        if (quests == null || quests.isEmpty()) {
            return 0;
        }
        int registered = 0;
        for (LostTalesQuestDefinition quest : quests) {
            if (mayRegisterRuntime(quest)) {
                RUNTIME_QUESTS_BY_ID.put(quest.getId(), quest);
                registered++;
            }
        }
        if (registered > 0) {
            rebuildSortedQuests();
        }
        return registered;
    }

    private static boolean mayRegisterRuntime(LostTalesQuestDefinition quest) {
        return quest != null && LostTalesQuestIds.fits(quest.getId())
                && !STATIC_QUESTS_BY_ID.containsKey(quest.getId())
                && !SERVER_QUESTS_BY_ID.containsKey(quest.getId());
    }

    /**
     * Keeps, of the quests made while the game runs, only those {@code held}
     * names: the missives some quest log still holds. What every log has
     * forgotten goes, so the list stays as long as the logs are.
     */
    public static synchronized void retainRuntimeQuests(Set<String> held) {
        ensureLoaded();
        if (held != null && RUNTIME_QUESTS_BY_ID.keySet().retainAll(held)) {
            rebuildSortedQuests();
        }
    }

    public static synchronized void clearRuntimeQuests() {
        ensureLoaded();
        if (!RUNTIME_QUESTS_BY_ID.isEmpty()) {
            RUNTIME_QUESTS_BY_ID.clear();
            rebuildSortedQuests();
        }
    }

    private static void rebuildSortedQuests() {
        LinkedHashMap<String, LostTalesQuestDefinition> merged = new LinkedHashMap<String, LostTalesQuestDefinition>();
        merged.putAll(STATIC_QUESTS_BY_ID);
        merged.putAll(SERVER_QUESTS_BY_ID);
        merged.putAll(RUNTIME_QUESTS_BY_ID);

        List<LostTalesQuestDefinition> sorted = new ArrayList<LostTalesQuestDefinition>(merged.values());
        Collections.sort(sorted, new Comparator<LostTalesQuestDefinition>() {
            @Override
            public int compare(LostTalesQuestDefinition left, LostTalesQuestDefinition right) {
                String leftTitle = LostTalesQuestWords.title(left);
                String rightTitle = LostTalesQuestWords.title(right);
                int titleCompare = leftTitle.compareToIgnoreCase(rightTitle);
                if (titleCompare != 0) {
                    return titleCompare;
                }
                String leftId = left == null || left.getId() == null ? "" : left.getId();
                String rightId = right == null || right.getId() == null ? "" : right.getId();
                return leftId.compareToIgnoreCase(rightId);
            }
        });
        sortedQuests = Collections.unmodifiableList(sorted);
    }

    /** The bundled quest files as the server reads them: from the mod's own classpath. */
    private static final BundledQuestFiles.Source CLASSPATH =
            new BundledQuestFiles.Source() {
                @Override
                public Reader open(String path) {
                    InputStream stream = LostTalesQuestRegistry.class
                            .getClassLoader().getResourceAsStream(
                                    BundledQuestFiles.classpathPath(path));
                    return stream == null ? null
                            : new InputStreamReader(stream, StandardCharsets.UTF_8);
                }
            };
}
