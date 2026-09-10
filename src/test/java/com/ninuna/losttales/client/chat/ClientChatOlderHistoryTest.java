package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The rule for paging a channel's older history in: one ask per oldest
 * line, a wait on an ask still young, and done for good once an ask
 * changed nothing in its time or the game's own list has no room left.
 */
public final class ClientChatOlderHistoryTest {
    private static final ChatTab GLOBAL = ChatTab.of(ChatChannel.ALL);
    private static final ChatTab OOC = ChatTab.of(ChatChannel.OOC);
    private static final long NOW = 1000L * 1000000L;
    private static final int ROOMY = 1000;

    @Before
    public void setUp() {
        ClientChatOlderHistory.clear();
    }

    @After
    public void tearDown() {
        ClientChatOlderHistory.clear();
    }

    @Test
    public void oneAskPerOldestLineThenAWaitThenDone() {
        assertEquals(ClientChatOlderHistory.Decision.ASK,
                ClientChatOlderHistory.decide(GLOBAL, 500L, NOW, 10, ROOMY));
        // The same frame again, and every frame after: the ask stands.
        assertEquals(ClientChatOlderHistory.Decision.WAIT,
                ClientChatOlderHistory.decide(GLOBAL, 500L, NOW + 1L, 10, ROOMY));
        assertEquals(ClientChatOlderHistory.Decision.WAIT,
                ClientChatOlderHistory.decide(GLOBAL, 500L,
                        NOW + ClientChatOlderHistory.ANSWER_NANOS - 1L, 10, ROOMY));
        // Nothing older arrived in time: the channel is done.
        assertEquals(ClientChatOlderHistory.Decision.DONE,
                ClientChatOlderHistory.decide(GLOBAL, 500L,
                        NOW + ClientChatOlderHistory.ANSWER_NANOS, 10, ROOMY));
        assertEquals(ClientChatOlderHistory.Decision.DONE,
                ClientChatOlderHistory.decide(GLOBAL, 400L,
                        NOW + ClientChatOlderHistory.ANSWER_NANOS * 2L, 10, ROOMY));
    }

    @Test
    public void anAnsweredAskLeadsToTheNextPage() {
        assertEquals(ClientChatOlderHistory.Decision.ASK,
                ClientChatOlderHistory.decide(GLOBAL, 500L, NOW, 10, ROOMY));
        // The page arrived: the view's oldest line is older now, and the
        // next page is asked for from it, however soon.
        assertEquals(ClientChatOlderHistory.Decision.ASK,
                ClientChatOlderHistory.decide(GLOBAL, 450L, NOW + 1L, 60, ROOMY));
        assertEquals(ClientChatOlderHistory.Decision.WAIT,
                ClientChatOlderHistory.decide(GLOBAL, 450L, NOW + 2L, 60, ROOMY));
    }

    @Test
    public void viewsAreAskedAboutApart() {
        assertEquals(ClientChatOlderHistory.Decision.ASK,
                ClientChatOlderHistory.decide(GLOBAL, 500L, NOW, 10, ROOMY));
        assertEquals(ClientChatOlderHistory.Decision.ASK,
                ClientChatOlderHistory.decide(OOC, 500L, NOW, 10, ROOMY));
        assertEquals(ClientChatOlderHistory.Decision.WAIT,
                ClientChatOlderHistory.decide(OOC, 500L, NOW + 1L, 10, ROOMY));
    }

    @Test
    public void nothingIsAskedWithoutANamedLineOrWithoutRoom() {
        assertEquals(ClientChatOlderHistory.Decision.DONE,
                ClientChatOlderHistory.decide(GLOBAL, ChatMessageIds.NONE, NOW, 10, ROOMY));
        assertEquals(ClientChatOlderHistory.Decision.DONE,
                ClientChatOlderHistory.decide(GLOBAL, -4L, NOW, 10, ROOMY));
        assertEquals(ClientChatOlderHistory.Decision.DONE,
                ClientChatOlderHistory.decide(null, 500L, NOW, 10, ROOMY));
        // The game's list trims its oldest lines past its capacity: a
        // page that would only be trimmed is not asked for.
        assertEquals(ClientChatOlderHistory.Decision.DONE,
                ClientChatOlderHistory.decide(GLOBAL, 500L, NOW,
                        ROOMY - ClientChatOlderHistory.CAPACITY_MARGIN + 1, ROOMY));
        assertEquals(ClientChatOlderHistory.Decision.ASK,
                ClientChatOlderHistory.decide(GLOBAL, 500L, NOW,
                        ROOMY - ClientChatOlderHistory.CAPACITY_MARGIN, ROOMY));
    }

    @Test
    public void aClearedHistoryStartsOver() {
        assertEquals(ClientChatOlderHistory.Decision.ASK,
                ClientChatOlderHistory.decide(GLOBAL, 500L, NOW, 10, ROOMY));
        assertEquals(ClientChatOlderHistory.Decision.DONE,
                ClientChatOlderHistory.decide(GLOBAL, 500L,
                        NOW + ClientChatOlderHistory.ANSWER_NANOS, 10, ROOMY));
        ClientChatOlderHistory.clear();
        assertEquals(ClientChatOlderHistory.Decision.ASK,
                ClientChatOlderHistory.decide(GLOBAL, 500L, NOW, 10, ROOMY));
        // The view's oldest line got newer without a clear: the game
        // emptied its list under it, and the ask starts again as well.
        assertEquals(ClientChatOlderHistory.Decision.ASK,
                ClientChatOlderHistory.decide(GLOBAL, 900L, NOW + 1L, 10, ROOMY));
    }
}
