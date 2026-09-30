package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.quest.BundledQuestFiles;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinitionJsonParser;
import com.ninuna.losttales.quest.LostTalesQuestDefinitionValidator;
import cpw.mods.fml.common.FMLLog;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

/**
 * The bundled quest files as the client reads them: from its resources,
 * a resource pack's included, the same way the server reads its own
 * ({@link BundledQuestFiles}). A file left out and a warning the checker
 * raises are logged, as the server logs them.
 */
final class LostTalesQuestDefinitionResourceLoader {

    private LostTalesQuestDefinitionResourceLoader() {}

    static List<LostTalesQuestDefinition> loadQuests(final IResourceManager resourceManager) {
        if (resourceManager == null) {
            return Collections.emptyList();
        }
        BundledQuestFiles.Result read = BundledQuestFiles.read(
                new BundledQuestFiles.Source() {
                    @Override
                    public Reader open(String path) throws IOException {
                        try {
                            return new InputStreamReader(resourceManager
                                    .getResource(toResourceLocation(path))
                                    .getInputStream(), StandardCharsets.UTF_8);
                        } catch (FileNotFoundException missing) {
                            return null;
                        }
                    }
                });
        for (String problem : read.problems) {
            FMLLog.warning("[%s] Bundled quest left out: %s",
                    LostTalesMetaData.MOD_ID, problem);
        }
        LostTalesQuestDefinitionValidator.logWarnings(read.quests);
        return read.quests;
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
}
