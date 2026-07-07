package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.client.resources.IResourceManager;
/**
 * Client cache for bundled quest definitions.
 *
 * This is the 1.7.10 replacement for the modern datapack quest definition loader.
 * It is definition-only for now; player quest progress and server sync are separate
 * follow-up work.
 */
public final class LostTalesClientQuestDefinitionStore {
    private static volatile List<LostTalesQuestDefinition> quests = Collections.emptyList();
    private static volatile boolean loaded;

    private LostTalesClientQuestDefinitionStore() {}

    public static List<LostTalesQuestDefinition> getQuests() {
        return quests;
    }

    public static LostTalesQuestDefinition getQuest(String id) {
        if (id == null) return null;
        for (LostTalesQuestDefinition quest : quests) {
            if (id.equals(quest.getId())) {
                return quest;
            }
        }
        return null;
    }

    public static void ensureLoaded(IResourceManager resourceManager) {
        if (!loaded) {
            reloadFromResources(resourceManager);
        }
    }

    public static void reloadFromResources(IResourceManager resourceManager) {
        List<LostTalesQuestDefinition> loadedQuests = LostTalesQuestDefinitionResourceLoader.loadQuests(resourceManager);
        quests = Collections.unmodifiableList(new ArrayList<LostTalesQuestDefinition>(loadedQuests));
        loaded = true;
        logMissingMarkerWarnings(quests);
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
