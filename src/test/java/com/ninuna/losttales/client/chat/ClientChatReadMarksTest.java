package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A read mark is the newest message this account was shown in a view
 * on a server: it only moves forward, it is kept apart per server, and
 * the file it is written to reads back to the same marks.
 */
public final class ClientChatReadMarksTest {
    private static final ChatTab GLOBAL = ChatTab.of(ChatChannel.GLOBAL);
    private static final ChatTab OOC = ChatTab.of(ChatChannel.OOC);

    @Before
    public void setUp() {
        ClientChatReadMarks.initialize(null, null);
    }

    @After
    public void tearDown() {
        ClientChatReadMarks.initialize(null, null);
    }

    @Test
    public void aMarkOnlyMovesForwardAndIsKeptPerServer() {
        assertEquals(ChatMessageIds.NONE,
                ClientChatReadMarks.lastRead("server:play.example", GLOBAL));
        ClientChatReadMarks.markRead("server:play.example", GLOBAL, 500L);
        ClientChatReadMarks.markRead("server:play.example", GLOBAL, 400L);
        assertEquals(500L, ClientChatReadMarks.lastRead("server:play.example", GLOBAL));
        // Another server, another view: nothing read there yet.
        assertEquals(ChatMessageIds.NONE,
                ClientChatReadMarks.lastRead("world:Ainulindale", GLOBAL));
        assertEquals(ChatMessageIds.NONE,
                ClientChatReadMarks.lastRead("server:play.example", OOC));
        // A local id, or no server at all, marks nothing.
        ClientChatReadMarks.markRead("server:play.example", OOC, -3L);
        ClientChatReadMarks.markRead("", OOC, 9L);
        assertEquals(ChatMessageIds.NONE,
                ClientChatReadMarks.lastRead("server:play.example", OOC));
        // The heading and the one mark made.
        assertEquals(2, ClientChatReadMarks.describe().size());
    }

    @Test
    public void theFileReadsBackToTheSameMarks() {
        ClientChatReadMarks.markRead("server:play.example", GLOBAL, 500L);
        ClientChatReadMarks.markRead("world:My World", OOC, 77L);
        List<String> lines = ClientChatReadMarks.describe();
        assertTrue(lines.get(0).startsWith("#"));
        assertEquals(3, lines.size());

        ClientChatReadMarks.initialize(null, null);
        ClientChatReadMarks.load(lines);
        assertEquals(500L, ClientChatReadMarks.lastRead("server:play.example", GLOBAL));
        assertEquals(77L, ClientChatReadMarks.lastRead("world:My World", OOC));

        // A line nobody can read is a mark nobody had.
        ClientChatReadMarks.initialize(null, null);
        ClientChatReadMarks.load(Arrays.asList("garbage", "a\tb\tnot-a-number",
                "server:x\tglobal\t-5", "server:x\tglobal\t12"));
        assertEquals(12L, ClientChatReadMarks.lastRead("server:x", GLOBAL));
        assertEquals(Arrays.asList(lines.get(0), "server:x\tglobal\t12"),
                ClientChatReadMarks.describe());
    }

    @Test
    public void theOldestTouchedMarksGoFirstPastTheBound() {
        for (int index = 0; index < ClientChatReadMarks.MAX_MARKS + 10; index++) {
            ClientChatReadMarks.markRead("server:" + index, GLOBAL, 1L + index);
        }
        // The heading and a bound's worth of marks.
        assertEquals(ClientChatReadMarks.MAX_MARKS + 1,
                ClientChatReadMarks.describe().size());
        assertEquals(ChatMessageIds.NONE,
                ClientChatReadMarks.lastRead("server:0", GLOBAL));
        assertEquals(ChatMessageIds.NONE,
                ClientChatReadMarks.lastRead("server:9", GLOBAL));
        assertEquals(11L, ClientChatReadMarks.lastRead("server:10", GLOBAL));
        assertEquals(ClientChatReadMarks.MAX_MARKS + 10L, ClientChatReadMarks.lastRead(
                "server:" + (ClientChatReadMarks.MAX_MARKS + 9), GLOBAL));
    }
}
