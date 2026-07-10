package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.IResourceManager;
/**
 * Client cache for bundled and server-synced quest definitions.
 *
 * Bundled quests still come from client resources. Dynamic definitions are synced
 * from the logical server so generated missives can appear in the journal and HUD
 * after acceptance.
 */
public final class LostTalesClientQuestDefinitionStore {
    private static final Map<String, LostTalesQuestDefinition> STATIC_QUESTS = new LinkedHashMap<String, LostTalesQuestDefinition>();
    private static final Map<String, LostTalesQuestDefinition> DYNAMIC_QUESTS = new LinkedHashMap<String, LostTalesQuestDefinition>();
    private static volatile List<LostTalesQuestDefinition> quests = Collections.emptyList();
    private static volatile boolean loaded;

    private LostTalesClientQuestDefinitionStore() {}

    public static synchronized List<LostTalesQuestDefinition> getQuests() {
        return quests;
    }

    public static synchronized LostTalesQuestDefinition getQuest(String id) {
        if (id == null) return null;
        LostTalesQuestDefinition dynamicQuest = DYNAMIC_QUESTS.get(id);
        if (dynamicQuest != null) {
            return dynamicQuest;
        }
        return STATIC_QUESTS.get(id);
    }

    public static synchronized void ensureLoaded(IResourceManager resourceManager) {
        if (!loaded) {
            reloadFromResources(resourceManager);
        }
    }

    public static synchronized void reloadFromResources(IResourceManager resourceManager) {
        List<LostTalesQuestDefinition> loadedQuests = LostTalesQuestDefinitionResourceLoader.loadQuests(resourceManager);
        STATIC_QUESTS.clear();
        for (LostTalesQuestDefinition quest : loadedQuests) {
            if (quest != null && quest.getId() != null && quest.getId().length() > 0) {
                STATIC_QUESTS.put(quest.getId(), quest);
            }
        }
        rebuildQuestList();
        loaded = true;
        logMissingMarkerWarnings(quests);
    }

    public static synchronized void setDynamicQuestDefinitions(Collection<LostTalesQuestDefinition> dynamicQuests) {
        DYNAMIC_QUESTS.clear();
        if (dynamicQuests != null) {
            for (LostTalesQuestDefinition quest : dynamicQuests) {
                if (quest != null && quest.getId() != null && quest.getId().length() > 0) {
                    DYNAMIC_QUESTS.put(quest.getId(), quest);
                }
            }
        }
        rebuildQuestList();
    }

    public static synchronized void clearDynamicQuestDefinitions() {
        if (!DYNAMIC_QUESTS.isEmpty()) {
            DYNAMIC_QUESTS.clear();
            rebuildQuestList();
        }
    }

    private static void rebuildQuestList() {
        LinkedHashMap<String, LostTalesQuestDefinition> merged = new LinkedHashMap<String, LostTalesQuestDefinition>();
        merged.putAll(STATIC_QUESTS);
        merged.putAll(DYNAMIC_QUESTS);

        ArrayList<LostTalesQuestDefinition> rebuilt = new ArrayList<LostTalesQuestDefinition>(merged.values());
        Collections.sort(rebuilt, new Comparator<LostTalesQuestDefinition>() {
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
        quests = Collections.unmodifiableList(rebuilt);
    }

    private static void logMissingMarkerWarnings(List<LostTalesQuestDefinition> loadedQuests) {
        Set<String> knownMarkerIds = LostTalesClientMapMarkerStore.getSharedMarkerIds();
        if (loadedQuests == null || loadedQuests.isEmpty() || knownMarkerIds.isEmpty()) {
            return;
        }

        for (LostTalesQuestDefinition quest : loadedQuests) {
            if (quest == null) {
                continue;
            }
            for (String markerId : LostTalesQuestMarkerHelper.collectStaticQuestMarkerIds(quest)) {
                if (!knownMarkerIds.contains(markerId)) {
                    FMLLog.warning("[%s] Client quest %s references missing visible map marker id: %s", LostTalesMetaData.MOD_ID, quest.getId(), markerId);
                }
            }
        }
    }
}
