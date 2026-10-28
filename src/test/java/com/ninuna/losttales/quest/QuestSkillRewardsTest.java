package com.ninuna.losttales.quest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A quest may reward skill experience, written skill*amount (PROG-04, QUEST-19). */
public final class QuestSkillRewardsTest {

    @Test
    public void eachSkillAndItsAmountIsRead() {
        Map<String, String> rewards = new LinkedHashMap<String, String>();
        rewards.put(LostTalesQuestRewards.SKILLS, "losttales:smithing*50, losttales:mining*20");
        List<LostTalesQuestRewards.SkillReward> skills = LostTalesQuestRewards.skills(rewards);
        assertEquals(2, skills.size());
        assertEquals("losttales:smithing", skills.get(0).getSkillId());
        assertEquals(50, skills.get(0).getAmount());
        assertEquals(20, skills.get(1).getAmount());
    }

    @Test
    public void anAmountOutOfBoundsOrNoSkillIdIsWarned() {
        assertTrue(warnings("losttales:smithing*0").contains("needs an amount"));
        assertTrue(warnings("losttales:smithing").contains("needs an amount"));
        assertTrue(warnings("smithing*50").contains("no skill's id"));
        assertEquals("", warnings("losttales:smithing*50"));
    }

    private static String warnings(String skills) {
        String file = "{\"id\": \"losttales:test/skill_reward\", \"title\": \"T\","
                + " \"stages\": [{\"id\": \"one\", \"objectives\": [{\"id\": \"go\","
                + " \"type\": \"goto\", \"params\": {\"x\": \"0\", \"y\": \"64\", \"z\": \"0\"}}]}],"
                + " \"rewards\": {\"skills\": \"" + skills + "\"}}";
        LostTalesQuestDefinition quest = LostTalesQuestDefinitionJsonParser.parseQuest(
                new java.io.StringReader(file), "quests/test/skill_reward.json",
                new java.util.ArrayList<String>());
        List<String> warnings = LostTalesQuestDefinitionValidator.warningsOf(quest,
                new java.util.ArrayList<String>());
        StringBuilder all = new StringBuilder();
        for (String warning : warnings) {
            if (warning.contains("skills")) {
                all.append(warning);
            }
        }
        return all.toString();
    }
}
