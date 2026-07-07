package com.ninuna.losttales.quest;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import java.io.IOException;
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
    private static final String INDEX_FILE = "quest/index.json";
    private static final String[] FALLBACK_QUEST_FILES = new String[] {
            "quest/test_quest.json",
            "quest/missive/gather_gold_ore.json",
            "quest/missive/kill_orc.json",
            "quest/path/blacksmith/test_crafting_quest.json"
    };

    private static final Map<String, LostTalesQuestDefinition> QUESTS_BY_ID = new LinkedHashMap<String, LostTalesQuestDefinition>();
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

        List<LostTalesQuestDefinition> sorted = new ArrayList<LostTalesQuestDefinition>(loadedQuests.values());
        Collections.sort(sorted, new Comparator<LostTalesQuestDefinition>() {
            @Override
            public int compare(LostTalesQuestDefinition left, LostTalesQuestDefinition right) {
                return left.getTitle().compareToIgnoreCase(right.getTitle());
            }
        });

        QUESTS_BY_ID.clear();
        QUESTS_BY_ID.putAll(loadedQuests);
        sortedQuests = Collections.unmodifiableList(sorted);
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

    public static LostTalesQuestDefinition getQuest(String questId) {
        ensureLoaded();
        return QUESTS_BY_ID.get(questId);
    }

    public static Collection<LostTalesQuestDefinition> getQuests() {
        ensureLoaded();
        return sortedQuests;
    }

    public static boolean containsQuest(String questId) {
        return getQuest(questId) != null;
    }

    private static List<String> loadQuestIndexFromClasspath() {
        List<String> files = new ArrayList<String>();
        Reader reader = null;
        try {
            reader = openClasspathReader(INDEX_FILE);
            if (reader != null) {
                files.addAll(LostTalesQuestDefinitionJsonParser.parseQuestIndex(reader));
            }
        } catch (RuntimeException ignored) {
            // Broken quest indexes should not prevent a server from starting.
        } finally {
            closeQuietly(reader);
        }

        if (files.isEmpty()) {
            Collections.addAll(files, FALLBACK_QUEST_FILES);
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
            closeQuietly(reader);
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

    private static void closeQuietly(Reader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {}
        }
    }
}
