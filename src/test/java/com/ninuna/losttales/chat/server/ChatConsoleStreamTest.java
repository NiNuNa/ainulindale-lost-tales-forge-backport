package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The console keeps the newest entries in order and repeats no secret. */
public final class ChatConsoleStreamTest {

    @Before
    public void setUp() {
        ChatConsoleStream.clear();
        ChatMessageIdAllocator.reset();
    }

    @After
    public void tearDown() {
        ChatConsoleStream.clear();
        ChatMessageIdAllocator.reset();
    }

    @Test
    public void entriesReplayOldestFirstAndTheOldestGoFirst() {
        long first = record("first");
        for (int index = 0; index < ChatConsoleStream.MAX_EVENTS; index++) {
            record("entry " + index);
        }
        assertEquals(ChatConsoleStream.MAX_EVENTS, ChatConsoleStream.size());
        List<ChatConsoleEvent> replay = ChatConsoleStream.replay(0L);
        assertEquals(ChatConsoleStream.MAX_REPLAY, replay.size());
        assertTrue(replay.get(0).getId() > first);
        for (int index = 1; index < replay.size(); index++) {
            assertTrue(replay.get(index - 1).getId() < replay.get(index).getId());
        }
        assertEquals("entry " + (ChatConsoleStream.MAX_EVENTS - 1),
                replay.get(replay.size() - 1).getText());
        long since = replay.get(replay.size() - 4).getId();
        assertEquals(3, ChatConsoleStream.replay(since).size());
        ChatConsoleStream.record(null);
        ChatConsoleStream.clear();
        assertEquals(0, ChatConsoleStream.size());
    }

    @Test
    public void anEventHasAKindASeverityAndSomethingToSay() {
        try {
            new ChatConsoleEvent(1L, 1L, null, ChatConsoleEvent.Severity.INFO, "a", "b");
            assertTrue(false);
        } catch (IllegalArgumentException expected) {
            // no kind
        }
        try {
            new ChatConsoleEvent(1L, 1L, ChatConsoleEvent.Kind.COMMAND,
                    ChatConsoleEvent.Severity.INFO, "a", "   ");
            assertTrue(false);
        } catch (IllegalArgumentException expected) {
            // nothing said
        }
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < ChatConsoleEvent.MAX_TEXT_LENGTH + 50; index++) {
            long_.append('x');
        }
        ChatConsoleEvent clipped = new ChatConsoleEvent(1L, 1L, ChatConsoleEvent.Kind.COMMAND,
                ChatConsoleEvent.Severity.INFO, null, long_.toString());
        assertEquals(ChatConsoleEvent.MAX_TEXT_LENGTH, clipped.getText().length());
        assertEquals("", clipped.getActor());
        // Only a command has a context, and only a printable one.
        assertEquals("all", new ChatConsoleEvent(1L, 1L, ChatConsoleEvent.Kind.COMMAND,
                ChatConsoleEvent.Severity.INFO, "a", "/x", " all ").getContext());
        assertEquals("", new ChatConsoleEvent(1L, 1L, ChatConsoleEvent.Kind.SERVER,
                ChatConsoleEvent.Severity.INFO, "a", "started", "all").getContext());
        assertEquals("", new ChatConsoleEvent(1L, 1L, ChatConsoleEvent.Kind.COMMAND,
                ChatConsoleEvent.Severity.INFO, "a", "/x", "all\nooc").getContext());
        assertEquals("", new ChatConsoleEvent(1L, 1L, ChatConsoleEvent.Kind.COMMAND,
                ChatConsoleEvent.Severity.INFO, "a", "/x", null).getContext());
        assertEquals(ChatConsoleEvent.Kind.WARNING, ChatConsoleEvent.Kind.fromOrdinal(5));
        assertEquals(null, ChatConsoleEvent.Kind.fromOrdinal(99));
        assertEquals(null, ChatConsoleEvent.Severity.fromOrdinal(-1));
    }

    /** What a command carried is repeated only when it is safe to. */
    @Test
    public void commandsAreDescribedWithoutTheirSecrets() {
        assertEquals("/gamemode 1 Steve",
                ChatConsoleStream.describeCommand("gamemode", new String[] {"1", "Steve"}));
        assertEquals("/losttales chat mute Bob 15m spam",
                ChatConsoleStream.describeCommand("losttales",
                        new String[] {"chat", "mute", "Bob", "15m", "spam"}));
        // A private message keeps whom it went to, never what it said.
        assertEquals("/msg Bob …",
                ChatConsoleStream.describeCommand("msg", new String[] {"Bob", "the", "key"}));
        assertEquals("/tell Bob",
                ChatConsoleStream.describeCommand("Tell", new String[] {"Bob"}));
        // A config change keeps the key, never the value.
        assertEquals("/losttales config set discord botToken …",
                ChatConsoleStream.describeCommand("losttales", new String[] {
                        "config", "set", "discord", "botToken", "abc.def.ghi"}));
        assertEquals("/losttales config get discord botToken",
                ChatConsoleStream.describeCommand("losttales", new String[] {
                        "config", "get", "discord", "botToken"}));
        // A binding keeps its channel and direction, never the addresses.
        String bind = ChatConsoleStream.describeCommand("losttales", new String[] {
                "discord", "bind", "ooc", "BIDIRECTIONAL",
                "channel=123456789012345678",
                "webhook=https://discord.com/api/webhooks/1/secret"});
        assertEquals("/losttales discord bind ooc BIDIRECTIONAL channel=… webhook=…", bind);
        assertFalse(bind.contains("secret"));
        assertEquals("/say", ChatConsoleStream.describeCommand("say", null));
        // Cut to a line.
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < 400; index++) {
            long_.append('y');
        }
        String cut = ChatConsoleStream.describeCommand("say", new String[] {long_.toString()});
        assertTrue(cut.length() <= 256);
        assertTrue(cut.endsWith("…"));
    }

    private static long record(String text) {
        long id = ChatMessageIdAllocator.next();
        ChatConsoleStream.record(new ChatConsoleEvent(id, id, ChatConsoleEvent.Kind.COMMAND,
                ChatConsoleEvent.Severity.INFO, "Steve", text));
        return id;
    }
}
