package com.ninuna.losttales.quest;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.StringTranslate;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What a quest may pay beyond experience and items, and how every place
 * names it (QUEST-33): LOTR alignment and coins, a pick of up to three
 * items, and each optional objective's own reward, shown before the quest
 * is taken.
 */
public final class LostTalesQuestRewardsTest {

    @BeforeClass
    public static void loadTheModsWords() {
        StringTranslate.inject(LostTalesQuestRewardsTest.class
                .getResourceAsStream("/assets/losttales/lang/en_US.lang"));
        StringTranslate.inject(new ByteArrayInputStream(
                "lotr.faction.GONDOR.name=Gondor\n".getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> rewards(String... pairs) {
        Map<String, String> rewards = new LinkedHashMap<String, String>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            rewards.put(pairs[index], pairs[index + 1]);
        }
        return rewards;
    }

    @Test
    public void alignmentCoinsAndAPickAreRead() {
        Map<String, String> rewards = rewards("alignment",
                "lotr:gondor*50, lotr:rohan*20, lotr:bree*0, lotr:dale",
                "coins", "40",
                "pick", "minecraft:iron_sword,minecraft:bow*1,minecraft:shield,minecraft:apple");
        List<LostTalesQuestRewards.Alignment> alignments =
                LostTalesQuestRewards.alignments(rewards);
        assertEquals("an entry without an amount of 1 or more is left out",
                2, alignments.size());
        assertEquals("lotr:gondor", alignments.get(0).getFactionId());
        assertEquals(50, alignments.get(0).getAmount());
        assertEquals(40, LostTalesQuestRewards.coins(rewards));
        assertEquals("a pick holds three at most", 3,
                LostTalesQuestRewards.picks(rewards).size());
        assertEquals("minecraft:bow", LostTalesQuestRewards.picks(rewards)
                .get(1).getItemId());
        assertEquals(LostTalesQuestRewards.MAX_COINS, LostTalesQuestRewards.coins(
                rewards("coins", "999999999")));
    }

    @Test
    public void everyRewardIsNamedTheOptionalOnesMarked() {
        LostTalesQuestObjectiveDefinition extra = new LostTalesQuestObjectiveDefinition(
                "scouts", "kill", "", true, rewards("group", "hostile",
                        "reward.experience", "10", "reward.coins", "5"));
        LostTalesQuestDefinition quest = new LostTalesQuestDefinition(
                "losttales:test/pay", "Pay", "", false, false, "locked", null,
                rewards("alignment", "lotr:gondor*50", "coins", "1",
                        "pick", "losttales_test:a_blade,losttales_test:a_bow"),
                null, null, null, null, null, Collections.singletonList(
                        new LostTalesQuestStageDefinition("10",
                                Collections.singletonList(extra))),
                false, null, null);
        Map<String, String> all = LostTalesQuestRewards.withOptionals(quest);
        assertEquals("10", all.get("optional.scouts.experience"));
        assertEquals(Arrays.asList("+50 alignment with Gondor", "1 silver coin",
                "One of: A blade, A bow", "10 experience (optional)",
                "5 silver coins (optional)"), LostTalesQuestRewardText.phrases(all));
    }

    @Test
    public void theCheckerHoldsEveryNewReward() {
        String head = "{\"id\":\"losttales:test/q\",\"startMode\":\"locked\","
                + "\"interaction\":{\"entity\":\"losttales.Nia\"},\"returnTo\":\"giver\","
                + "\"dialogue\":{\"handIn\":\"Thank you.\"},"
                + "\"stages\":[{\"id\":\"10\",\"objectives\":[{\"id\":\"a\","
                + "\"type\":\"gather\",\"params\":{\"item\":\"minecraft:log\"}}]}],";
        assertEquals(Collections.<String>emptyList(), warnings(head
                + "\"rewards\":{\"alignment\":\"lotr:gondor*50\",\"coins\":\"30\","
                + "\"pick\":\"minecraft:bow,minecraft:arrow*16\"}}"));
        assertOne(head + "\"rewards\":{\"alignment\":\"lotr:nowhere*5\"}}",
                "'lotr:nowhere', which is no faction");
        assertOne(head + "\"rewards\":{\"alignment\":\"lotr:hostile*5\"}}",
                "'lotr:hostile', which is no faction");
        assertOne(head + "\"rewards\":{\"alignment\":\"lotr:gondor\"}}",
                "needs an amount of 1 to 1000");
        assertOne(head + "\"rewards\":{\"coins\":\"0\"}}", "'coins' 0");
        assertOne(head + "\"rewards\":{\"pick\":\"a,b,c,d\"}}", "more than 3 items");
    }

    @Test
    public void onlyTheQuestsOwnRewardsOfferAPick() {
        String json = "{\"id\":\"losttales:test/q\",\"startMode\":\"locked\","
                + "\"stages\":[{\"id\":\"10\",\"objectives\":[{\"id\":\"a\","
                + "\"type\":\"gather\",\"params\":{\"item\":\"minecraft:log\"}},"
                + "{\"id\":\"b\",\"type\":\"gather\",\"optional\":true,\"params\":{"
                + "\"item\":\"minecraft:log\",\"reward.pick\":\"minecraft:bow\"}}]}]}";
        assertOne(json, "names a 'pick', which only the quest's own");
    }

    private static void assertOne(String json, String about) {
        List<String> found = warnings(json);
        assertEquals(found.toString(), 1, found.size());
        assertTrue(found.get(0), found.get(0).contains(about));
    }

    private static List<String> warnings(String json) {
        List<String> unread = new ArrayList<String>();
        LostTalesQuestDefinition quest = LostTalesQuestDefinitionJsonParser
                .parseQuest(new StringReader(json), "quests/test/q.json", unread);
        return LostTalesQuestDefinitionValidator.warningsOf(quest, unread);
    }
}
