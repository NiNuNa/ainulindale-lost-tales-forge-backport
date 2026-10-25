package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.QuestFellowCount;
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
 * the server sent them, each saying whether the member travels with the
 * player, which the journal dims by.
 */
public final class ClientQuestFellowsTest {
    private static final String QUEST = "losttales:server/cheese_cache";

    @After
    public void forget() {
        ClientQuestFellows.clear();
    }

    @Test
    public void eachMemberIsAPartThatKnowsWhetherItTravelsWithYou() {
        ClientQuestFellows.set(Arrays.asList(
                new QuestFellowCount(QUEST, "Tobin", false, true,
                        Collections.singletonMap("wheels", Integer.valueOf(2))),
                new QuestFellowCount(QUEST, "Dior", true, false, null),
                new QuestFellowCount("losttales:server/other", "Nia", true,
                        true, null)));
        List<ClientQuestFellows.Part> parts =
                ClientQuestFellows.partsFor(QUEST, "wheels", 3);
        assertEquals(2, parts.size());
        assertTrue(parts.get(0).isTravelling());
        assertTrue(parts.get(0).getText().length() > 0);
        assertFalse("dimmed in the journal", parts.get(1).isTravelling());
        assertTrue(ClientQuestFellows.partsFor("losttales:server/none",
                "wheels", 3).isEmpty());
    }
}
