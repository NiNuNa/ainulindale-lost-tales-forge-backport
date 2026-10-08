package com.ninuna.losttales.client.quest;

import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** This session's quest news, as the inbox lists and counts it. */
public final class ClientQuestNewsTest {

    @After
    public void forget() {
        ClientQuestNews.clear();
    }

    @Test
    public void newsIsListedNewestFirstAndCountedUntilSeen() {
        ClientQuestNews.clear();
        ClientQuestNews.record("first", "Quest started: First");
        ClientQuestNews.record("second", "Quest completed: Second");
        List<ClientQuestNews.News> all = ClientQuestNews.all();
        assertEquals(2, all.size());
        assertEquals("second", all.get(0).questId);
        assertEquals("Quest started: First", all.get(1).line);
        assertEquals(2, ClientQuestNews.unseen());
        ClientQuestNews.markSeen();
        assertEquals(0, ClientQuestNews.unseen());
        ClientQuestNews.record("third", "Quest failed: Third");
        assertEquals(1, ClientQuestNews.unseen());
    }

    @Test
    public void onlyTheNewestNewsIsKept() {
        ClientQuestNews.clear();
        for (int index = 0; index < ClientQuestNews.MAX_NEWS + 5; index++) {
            ClientQuestNews.record("quest" + index, "News " + index);
        }
        List<ClientQuestNews.News> all = ClientQuestNews.all();
        assertEquals(ClientQuestNews.MAX_NEWS, all.size());
        assertEquals("quest" + (ClientQuestNews.MAX_NEWS + 4), all.get(0).questId);
    }

    @Test
    public void newsWithoutAQuestOrWordsIsNotKept() {
        ClientQuestNews.clear();
        ClientQuestNews.record("", "Words");
        ClientQuestNews.record("quest", "");
        ClientQuestNews.record(null, null);
        assertTrue(ClientQuestNews.all().isEmpty());
    }

    @Test
    public void leavingTheWorldForgetsTheNewsAndCountsNothing() {
        ClientQuestNews.record("quest", "Quest started: Quest");
        ClientQuestNews.clear();
        assertTrue(ClientQuestNews.all().isEmpty());
        assertEquals(0, ClientQuestNews.unseen());
    }
}
