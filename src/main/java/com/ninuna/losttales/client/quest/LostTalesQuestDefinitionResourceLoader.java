package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinitionJsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

final class LostTalesQuestDefinitionResourceLoader {
    private static final String INDEX_FILE = "quests/index.json";
    private static final String[] FALLBACK_QUEST_FILES = new String[] {
            "quests/test_quest.json",
            "quests/missive/gather_gold_ore.json",
            "quests/missive/kill_orc.json",
            "quests/path/blacksmith/test_crafting_quest.json"
    };

    private LostTalesQuestDefinitionResourceLoader() {}

    static List<LostTalesQuestDefinition> loadQuests(IResourceManager resourceManager) {
        if (resourceManager == null) {
            return Collections.emptyList();
        }

        List<String> questFiles = loadQuestIndex(resourceManager);
        Map<String, LostTalesQuestDefinition> byId = new LinkedHashMap<String, LostTalesQuestDefinition>();

        for (String questFile : questFiles) {
            LostTalesQuestDefinition quest = loadQuestFile(resourceManager, questFile);
            if (quest != null) {
                byId.put(quest.getId(), quest);
            }
        }

        List<LostTalesQuestDefinition> quests = new ArrayList<LostTalesQuestDefinition>(byId.values());
        Collections.sort(quests, new Comparator<LostTalesQuestDefinition>() {
            @Override
            public int compare(LostTalesQuestDefinition left, LostTalesQuestDefinition right) {
                return left.getTitle().compareToIgnoreCase(right.getTitle());
            }
        });
        return quests;
    }

    private static List<String> loadQuestIndex(IResourceManager resourceManager) {
        List<String> files = new ArrayList<String>();
        Reader reader = null;
        try {
            IResource resource = resourceManager.getResource(new ResourceLocation(LostTalesMetaData.MOD_ID, INDEX_FILE));
            reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
            files.addAll(LostTalesQuestDefinitionJsonParser.parseQuestIndex(reader));
        } catch (IOException ignored) {
            // Missing index is allowed; fall back to the built-in files copied from the modern branch.
        } catch (RuntimeException ignored) {
            // Broken index files should not make the journal unusable.
        } finally {
            closeQuietly(reader);
        }

        if (files.isEmpty()) {
            Collections.addAll(files, FALLBACK_QUEST_FILES);
        }
        return files;
    }

    private static LostTalesQuestDefinition loadQuestFile(IResourceManager resourceManager, String questFile) {
        Reader reader = null;
        try {
            IResource resource = resourceManager.getResource(toResourceLocation(questFile));
            reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
            return LostTalesQuestDefinitionJsonParser.parseQuest(reader, questFile);
        } catch (IOException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            closeQuietly(reader);
        }
    }

    private static ResourceLocation toResourceLocation(String questFile) {
        String normalized = LostTalesQuestDefinitionJsonParser.normalizeQuestFile(questFile);
        int colonIndex = normalized.indexOf(':');
        if (colonIndex > 0) {
            String domain = normalized.substring(0, colonIndex);
            String path = normalized.substring(colonIndex + 1);
            return new ResourceLocation(domain, path);
        }
        return new ResourceLocation(LostTalesMetaData.MOD_ID, normalized);
    }

    private static void closeQuietly(Reader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {}
        }
    }
}
