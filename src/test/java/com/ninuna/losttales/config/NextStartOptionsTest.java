package com.ninuna.losttales.config;

import com.ninuna.losttales.config.server.ServerConfigApplyResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The one list of the server options only the next start puts in force
 * (DATA-12): each names an option the server's file holds, and a save's
 * answer tells them apart from what it applied.
 */
public final class NextStartOptionsTest {

    @Test
    public void everyOptionListedIsAServerOptionWithItsWords() {
        Configuration definitions = new Configuration();
        LostTalesConfig.defineOptions(definitions);
        List<String> problems = new ArrayList<String>();
        for (String name : NextStartOptions.keys()) {
            int point = name.indexOf('.');
            String category = name.substring(0, point);
            String key = name.substring(point + 1);
            if (LostTalesConfig.CLIENT_CATEGORIES.contains(category)) {
                problems.add(name + " is a client option");
            }
            ConfigCategory defined = definitions.hasCategory(category)
                    ? definitions.getCategory(category) : null;
            if (defined == null || !defined.containsKey(key)) {
                problems.add(name + " is no option the mod defines");
            }
            if (!LostTalesConfigWords.hasEnglish(LostTalesConfigWords.nameKey(category, key))) {
                problems.add(name + " has no name line");
            }
        }
        assertEquals(new ArrayList<String>(), problems);
    }

    @Test
    public void theRecipesAndTheBundledTemplatesWaitAndAnyCaseIsRead() {
        assertTrue(NextStartOptions.waitsForNextStart("waystones", "enableWaystoneRecipe"));
        assertTrue(NextStartOptions.waitsForNextStart("missives.bundledMissiveTemplates"));
        assertTrue(NextStartOptions.waitsForNextStart("MISSIVES.EnableMissiveBoardRecipe"));
        assertFalse(NextStartOptions.waitsForNextStart("chat", "typingIndicators"));
        assertFalse(NextStartOptions.waitsForNextStart(null));
        assertFalse(NextStartOptions.waitsForNextStart("waystones", null));
    }

    @Test
    public void aSavesAnswerPartsWhatWaitsFromWhatApplied() {
        ServerConfigApplyResult result = new ServerConfigApplyResult(
                Arrays.asList("chat.typingIndicators", "waystones.enableWaystoneRecipe",
                        "chat.historyPersisted", "quests.enableQuestRewards"),
                null, null, "");
        assertEquals(Arrays.asList("chat.typingIndicators", "quests.enableQuestRewards"),
                result.getAppliedNow());
        assertEquals(Arrays.asList("waystones.enableWaystoneRecipe", "chat.historyPersisted"),
                result.getWaiting());
        assertEquals(4, result.getApplied().size());
    }
}
