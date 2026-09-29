package com.ninuna.losttales.quest;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.util.LostTalesCloseables;
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
/**
 * Common quest definition registry used by the logical server.
 *
 * This deliberately reads bundled assets from the mod classpath instead of using
 * modern datapacks. That keeps the runtime compatible with Forge 1.7.10 and makes
 * quest definitions available on dedicated servers.
 */
public final class LostTalesQuestRegistry {
    private static final String INDEX_FILE = "quests/index.json";

    private static final Map<String, LostTalesQuestDefinition> STATIC_QUESTS_BY_ID = new LinkedHashMap<String, LostTalesQuestDefinition>();
    private static final Map<String, LostTalesQuestDefinition> RUNTIME_QUESTS_BY_ID = new LinkedHashMap<String, LostTalesQuestDefinition>();
    private static List<LostTalesQuestDefinition> sortedQuests = Collections.emptyList();
    private static boolean loaded;

    private LostTalesQuestRegistry() {}

    public static synchronized void loadFromClasspath() {
        Map<String, LostTalesQuestDefinition> loadedQuests = new LinkedHashMap<String, LostTalesQuestDefinition>();
        List<String> questFiles = loadQuestIndexFromClasspath();

        for (String questFile : questFiles) {
            LostTalesQuestDefinition quest = loadQuestFromClasspath(questFile);
            if (quest != null) {
                loadedQuests.put(quest.getId(), quest);
            }
        }

        STATIC_QUESTS_BY_ID.clear();
        STATIC_QUESTS_BY_ID.putAll(loadedQuests);
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
        return fileQuest != null ? fileQuest : RUNTIME_QUESTS_BY_ID.get(questId);
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
        return quest != null && quest.getId() != null && quest.getId().length() > 0
                && !STATIC_QUESTS_BY_ID.containsKey(quest.getId());
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
        merged.putAll(RUNTIME_QUESTS_BY_ID);

        List<LostTalesQuestDefinition> sorted = new ArrayList<LostTalesQuestDefinition>(merged.values());
        Collections.sort(sorted, new Comparator<LostTalesQuestDefinition>() {
            @Override
            public int compare(LostTalesQuestDefinition left, LostTalesQuestDefinition right) {
                String leftTitle = left == null || left.getTitle() == null ? "" : left.getTitle();
                String rightTitle = right == null || right.getTitle() == null ? "" : right.getTitle();
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

    private static List<String> loadQuestIndexFromClasspath() {
        List<String> files = new ArrayList<String>();
        Reader reader = null;
        try {
            reader = openClasspathReader(INDEX_FILE);
            if (reader == null) {
                FMLLog.warning("[%s] No quest index at %s: no bundled quests load.",
                        LostTalesMetaData.MOD_ID, INDEX_FILE);
            } else {
                files.addAll(LostTalesQuestDefinitionJsonParser.parseQuestIndex(reader));
            }
        } catch (RuntimeException exception) {
            // A broken index leaves the server without bundled quests, not
            // without a start.
            FMLLog.warning("[%s] The quest index %s cannot be read, so no bundled quests load: %s",
                    LostTalesMetaData.MOD_ID, INDEX_FILE, exception);
        } finally {
            LostTalesCloseables.closeQuietly(reader);
        }
        return files;
    }

    private static LostTalesQuestDefinition loadQuestFromClasspath(String questFile) {
        Reader reader = null;
        try {
            reader = openClasspathReader(questFile);
            if (reader == null) {
                return null;
            }
            return LostTalesQuestDefinitionJsonParser.parseQuest(reader, questFile);
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            LostTalesCloseables.closeQuietly(reader);
        }
    }

    private static Reader openClasspathReader(String questFile) {
        String path = toClasspathResourcePath(questFile);
        InputStream stream = LostTalesQuestRegistry.class.getClassLoader().getResourceAsStream(path);
        return stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private static String toClasspathResourcePath(String questFile) {
        String normalized = LostTalesQuestDefinitionJsonParser.normalizeQuestFile(questFile);
        int colonIndex = normalized.indexOf(':');
        if (colonIndex > 0) {
            String domain = normalized.substring(0, colonIndex);
            String path = normalized.substring(colonIndex + 1);
            return "assets/" + domain + "/" + path;
        }
        return "assets/" + LostTalesMetaData.MOD_ID + "/" + normalized;
    }
}
