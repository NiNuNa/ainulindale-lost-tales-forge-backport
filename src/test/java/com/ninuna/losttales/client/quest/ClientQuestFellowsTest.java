package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinitionJsonParser;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestRules;
import com.ninuna.losttales.quest.QuestFellowCount;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The members under an objective (QUEST-25): one part each, in the order
 * the server sent them, each saying whether its count helps the player,
 * which the journal dims by, and why not: the server shares no progress,
 * the objective is not shared, or the member does not travel with the
 * player.
 */
public final class ClientQuestFellowsTest {
    private static final String QUEST = "losttales:server/cheese_cache";

    @After
    public void forget() {
        ClientQuestFellows.clear();
    }

    private static void sent(boolean sharing) {
        ClientQuestFellows.set(Arrays.asList(
                new QuestFellowCount(QUEST, "Tobin", false, true,
                        Collections.singletonMap("wheels", Integer.valueOf(2))),
                new QuestFellowCount(QUEST, "Dior", true, false, null),
                new QuestFellowCount("losttales:server/other", "Nia", true,
                        true, null)), sharing);
    }

    @Test
    public void eachMemberIsAPartThatKnowsWhetherItsCountHelpsYou() {
        sent(true);
        List<ClientQuestFellows.Part> parts =
                ClientQuestFellows.partsFor(QUEST, "wheels", 3, true);
        assertEquals(2, parts.size());
        assertTrue(parts.get(0).isCounted());
        assertTrue(parts.get(0).getText().length() > 0);
        assertFalse("dimmed in the journal", parts.get(1).isCounted());
        assertEquals(ClientQuestFellows.TIP_AWAY, parts.get(1).getTipKey());
        assertTrue(ClientQuestFellows.partsFor("losttales:server/none",
                "wheels", 3, true).isEmpty());
    }

    @Test
    public void withSharingOffEveryMemberIsDimmed() {
        sent(false);
        for (ClientQuestFellows.Part part
                : ClientQuestFellows.partsFor(QUEST, "wheels", 3, true)) {
            assertFalse(part.isCounted());
            assertEquals(ClientQuestFellows.TIP_SHARING_OFF, part.getTipKey());
        }
        ClientQuestFellows.clear();
        assertTrue("a new world shares until the server says otherwise",
                ClientQuestFellows.isShared());
    }

    @Test
    public void underAnObjectiveNotSharedEveryMemberIsDimmed() {
        sent(true);
        for (ClientQuestFellows.Part part
                : ClientQuestFellows.partsFor(QUEST, "wheels", 3, false)) {
            assertFalse(part.isCounted());
            assertEquals(ClientQuestFellows.TIP_NOT_SHARED, part.getTipKey());
        }
        assertEquals("the server's setting is named first",
                ClientQuestFellows.TIP_SHARING_OFF,
                ClientQuestFellows.whyNotCounted(false, false, false));
        assertEquals("", ClientQuestFellows.whyNotCounted(true, true, true));
    }

    private static LostTalesQuestObjectiveDefinition objective(String type, String params) {
        LostTalesQuestDefinition quest = quest(type, params, "\"id\":\"losttales:server/x\"");
        return quest.getStages().get(0).getObjectives().get(0);
    }

    private static LostTalesQuestDefinition quest(String type, String params, String id) {
        return LostTalesQuestDefinitionJsonParser.parseQuest(new StringReader("{" + id + ","
                + "\"stages\":[{\"id\":\"1\",\"objectives\":[{\"id\":\"a\",\"type\":\""
                + type + "\",\"params\":{" + params + "}}]}]}"), "quests/test.json",
                new ArrayList<String>());
    }

    /** An objective is shared unless it says otherwise. */
    @Test
    public void anObjectiveIsSharedUnlessItSaysOtherwise() {
        LostTalesQuestDefinition quest = quest("kill", "\"group\":\"hostile\"",
                "\"id\":\"losttales:server/x\"");
        assertTrue(LostTalesQuestRules.sharesWithFellowship(quest,
                objective("kill", "\"group\":\"hostile\"")));
        assertTrue(LostTalesQuestRules.sharesWithFellowship(quest,
                objective("goto", "\"x\":\"1\",\"y\":\"2\",\"z\":\"3\"")));
        assertFalse(LostTalesQuestRules.sharesWithFellowship(quest,
                objective("kill", "\"group\":\"hostile\",\"fellowshipShared\":\"false\"")));
        assertTrue(LostTalesQuestRules.sharesWithFellowship(quest,
                objective("kill", "\"group\":\"hostile\",\"fellowshipShared\":\"true\"")));
        assertFalse(LostTalesQuestRules.sharesWithFellowship(null,
                objective("kill", "\"group\":\"hostile\"")));
    }
}
