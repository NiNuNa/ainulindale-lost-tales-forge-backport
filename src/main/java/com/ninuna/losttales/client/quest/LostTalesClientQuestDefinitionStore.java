package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.quest.ServerQuestFiles;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.IResourceManager;
/**
 * Every quest definition the client knows, from three places: the quests
 * bundled with the mod, read from its own resources; the quests the server
 * wrote in its own folder, sent as the player joins; and the missives the
 * player took, sent with their quest log. A bundled quest's id is never
 * taken by the other two.
 */
public final class LostTalesClientQuestDefinitionStore {
    private static final Map<String, LostTalesQuestDefinition> STATIC_QUESTS = new LinkedHashMap<String, LostTalesQuestDefinition>();
    private static final Map<String, LostTalesQuestDefinition> SERVER_QUESTS = new LinkedHashMap<String, LostTalesQuestDefinition>();
    private static final Map<String, LostTalesQuestDefinition> DYNAMIC_QUESTS = new LinkedHashMap<String, LostTalesQuestDefinition>();
    private static volatile List<LostTalesQuestDefinition> quests = Collections.emptyList();
    private static volatile boolean loaded;

    private LostTalesClientQuestDefinitionStore() {}

    public static synchronized List<LostTalesQuestDefinition> getQuests() {
        return quests;
    }

    public static synchronized LostTalesQuestDefinition getQuest(String id) {
        if (id == null) return null;
        LostTalesQuestDefinition staticQuest = STATIC_QUESTS.get(id);
        if (staticQuest != null) {
            return staticQuest;
        }
        LostTalesQuestDefinition serverQuest = SERVER_QUESTS.get(id);
        return serverQuest != null ? serverQuest : DYNAMIC_QUESTS.get(id);
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

    /**
     * The server's own quests, as one packet of them carries them:
     * {@code first} starts the list afresh. A quest that takes a bundled
     * quest's id is left out, and so is every quest past as many as a
     * server may write.
     */
    public static synchronized void addServerQuestDefinitions(boolean first,
            Collection<LostTalesQuestDefinition> serverQuests) {
        if (first) {
            SERVER_QUESTS.clear();
        }
        if (serverQuests != null) {
            for (LostTalesQuestDefinition quest : serverQuests) {
                if (quest != null && quest.getId() != null
                        && quest.getId().length() > 0
                        && !STATIC_QUESTS.containsKey(quest.getId())
                        && (SERVER_QUESTS.containsKey(quest.getId())
                                || SERVER_QUESTS.size()
                                        < ServerQuestFiles.MAX_FILES)) {
                    SERVER_QUESTS.put(quest.getId(), quest);
                }
            }
        }
        rebuildQuestList();
    }

    /** Forgets what the server sent, its own quests and the missives, as the player leaves it. */
    public static synchronized void clearServerSentDefinitions() {
        if (!DYNAMIC_QUESTS.isEmpty() || !SERVER_QUESTS.isEmpty()) {
            DYNAMIC_QUESTS.clear();
            SERVER_QUESTS.clear();
            rebuildQuestList();
        }
    }

    private static void rebuildQuestList() {
        LinkedHashMap<String, LostTalesQuestDefinition> merged = new LinkedHashMap<String, LostTalesQuestDefinition>();
        merged.putAll(DYNAMIC_QUESTS);
        merged.putAll(SERVER_QUESTS);
        merged.putAll(STATIC_QUESTS);

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
        if (loadedQuests == null || loadedQuests.isEmpty()
                || LostTalesClientMapMarkerStore.getAllMarkers().isEmpty()) {
            return;
        }

        for (LostTalesQuestDefinition quest : loadedQuests) {
            if (quest == null) {
                continue;
            }
            for (String markerId : LostTalesQuestMarkerHelper.collectStaticQuestMarkerIds(quest)) {
                if (!LostTalesClientMapMarkerStore.hasSharedMarker(markerId)) {
                    FMLLog.warning("[%s] Client quest %s references missing visible map marker id: %s", LostTalesMetaData.MOD_ID, quest.getId(), markerId);
                }
            }
        }
    }
}
