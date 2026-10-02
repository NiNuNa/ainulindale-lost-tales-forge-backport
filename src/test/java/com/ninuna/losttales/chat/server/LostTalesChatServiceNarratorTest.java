package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatNarrator;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The Narrator is anonymous. Every copy of its line a reader other than
 * the narrator is handed — live, replayed, quoted, forwarded, edited —
 * names no account, no account id and no character; the narrator's own
 * copy is still theirs to edit; and the Server Log alone is told who
 * narrated, and where.
 */
public final class LostTalesChatServiceNarratorTest {
    private static final UUID STEVE = UUID.fromString(
            "00000000-0000-0000-0000-00000000057e");
    private static final UUID ALDRIC = UUID.fromString(
            "00000000-0000-0000-0000-0000000a1d71");
    private static final UUID BOB = UUID.fromString(
            "00000000-0000-0000-0000-000000000b0b");
    private static final List<ChatNamedPlayer> NOBODY =
            Collections.<ChatNamedPlayer>emptyList();

    @Before
    public void setUp() {
        ChatHistory.clear();
        ChatConsoleStream.clear();
        ChatMessageIdAllocator.reset();
    }

    @After
    public void tearDown() {
        ChatHistory.clear();
        ChatConsoleStream.clear();
        ChatMessageIdAllocator.reset();
    }

    /** Steve narrating while he plays Aldric, as the server builds his own copy. */
    private static LostTalesChatMessagePacket told(long messageId) {
        return new LostTalesChatMessagePacket(ChatChannel.GLOBAL, STEVE,
                ChatNarrator.NAME, "Steve", "", 0xFFFFFF, ChatNarrator.color(),
                "The gates groan open.", 1000L, ChatNarrator.SKIN_ID, null, "",
                "", 0, false, messageId, ChatReplyReference.NONE, "", 0L,
                ALDRIC);
    }

    @Test
    public void anotherPlayersCopyCarriesNothingOfTheNarrator() throws Exception {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatMessagePacket forOthers = told(id).narratedForOthers();
        assertNothingOfSteve(forOthers);
        assertEquals("The gates groan open.", forOthers.getMessage());
        assertEquals(id, forOthers.getMessageId());
        assertTrue(forOthers.isNarrator());

        ByteBuf buffer = Unpooled.buffer();
        forOthers.toBytes(buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(0, bytes);
        assertEquals(-1, indexOf(bytes, "Steve".getBytes(Charset.forName("UTF-8"))));
        assertEquals(-1, indexOf(bytes, bytesOf(STEVE)));
        assertEquals(-1, indexOf(bytes, bytesOf(ALDRIC)));
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(Unpooled.wrappedBuffer(bytes));
        assertNothingOfSteve(decoded);

        // A line of anyone else's is handed on as it is.
        LostTalesChatMessagePacket said = new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, STEVE, "Aldric", "Steve", "", 0xFFFFFF,
                0xFFFFFF, "Well met.", 1000L, "", null, "", "", 0, false, id,
                ChatReplyReference.NONE);
        assertSame(said, said.narratedForOthers());
    }

    @Test(expected = IllegalArgumentException.class)
    public void theNarratorsIdSignsNothingButANarratorLine() {
        new LostTalesChatMessagePacket(ChatChannel.GLOBAL, ChatNarrator.SENDER_ID,
                "Aldric", "Steve", "", 0xFFFFFF, 0xFFFFFF, "Well met.", 1000L,
                "", null, "", "", 0, false, ChatMessageIdAllocator.next(),
                ChatReplyReference.NONE);
    }

    @Test
    public void theHistoryHandsOthersTheNarratorAloneAndTheNarratorTheirOwn() {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatMessagePacket own = told(id);
        ChatHistory.record(id, STEVE, ChatNarrator.NAME, own,
                own.narratedForOthers(), Arrays.asList(STEVE, BOB),
                ChatHistory.Audience.everyone());

        List<LostTalesChatMessagePacket> bobs = ChatHistory.replayFor(
                ChatHistoryRequesters.reader(BOB), 0L);
        assertEquals(1, bobs.size());
        assertNothingOfSteve(bobs.get(0));
        List<LostTalesChatMessagePacket> steves = ChatHistory.replayFor(
                ChatHistoryRequesters.reader(STEVE), 0L);
        assertEquals(STEVE, steves.get(0).getSenderId());
        assertEquals("Steve", steves.get(0).getAccountName());

        ChatReplyReference quote = ChatHistory.quoteFor(id,
                ChatHistoryRequesters.reader(BOB), ChatChannel.GLOBAL, "");
        assertTrue(quote.exists());
        assertEquals(ChatNarrator.NAME, quote.getAuthor());
        assertEquals(ChatNarrator.SENDER_ID, quote.getSenderId());
        ChatHistory.Forwardable forward = ChatHistory.forwardable(id,
                ChatHistoryRequesters.reader(BOB));
        assertNotNull(forward);
        assertEquals(ChatNarrator.NAME, forward.reference.getAuthor());
        assertEquals(ChatNarrator.SENDER_ID, forward.reference.getSenderId());

        // The narrator may still rewrite it, and the rewrite names nobody.
        assertNotNull(ChatHistory.applyEdit(id, STEVE, "The gates hold.", NOBODY));
        LostTalesChatMessagePacket edited = ChatHistory.replayFor(
                ChatHistoryRequesters.reader(BOB), 0L).get(0);
        assertEquals("The gates hold.", edited.getMessage());
        assertNothingOfSteve(edited);

        // Staff are told who narrated it when it is reported.
        ChatHistory.Reportable reported = ChatHistory.reportable(id,
                ChatHistoryRequesters.reader(BOB));
        assertNotNull(reported);
        assertEquals("Steve", reported.author);
    }

    @Test
    public void theServerConsoleNamesWhoNarratedAndWhere() {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatService.noteNarration("Steve", ChatChannel.GLOBAL, "", id,
                null);
        long fellowshipLine = ChatMessageIdAllocator.next();
        LostTalesChatService.noteNarration("Steve", ChatChannel.FELLOWSHIP,
                UUID.randomUUID().toString(), fellowshipLine, "The Grey Company");

        List<ChatConsoleEvent> entries = ChatConsoleStream.replay(0L);
        assertEquals(2, entries.size());
        ChatConsoleEvent global = entries.get(0);
        assertEquals("Steve", global.getActor());
        assertEquals(ChatConsoleEvent.Kind.MODERATION, global.getKind());
        assertEquals("narrated #global/" + id, global.getText());
        ChatConsoleEvent fellowship = entries.get(1);
        assertEquals("Steve", fellowship.getActor());
        assertTrue(fellowship.getText(), fellowship.getText().startsWith("narrated #"));
        assertTrue(fellowship.getText(),
                fellowship.getText().contains("/" + fellowshipLine));
        assertTrue(fellowship.getText(),
                fellowship.getText().endsWith(" in The Grey Company"));
    }

    private static void assertNothingOfSteve(LostTalesChatMessagePacket line) {
        assertEquals(ChatNarrator.SENDER_ID, line.getSenderId());
        assertEquals(ChatNarrator.NAME, line.getAccountName());
        assertEquals(ChatNarrator.NAME, line.getIdentityName());
        assertNull(line.getIdentityCharacterId());
        assertEquals(0, line.getRoles());
        assertEquals("", line.getTitle());
        assertEquals("", line.getFactionName());
    }

    private static byte[] bytesOf(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits()).array();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int from = 0; from + needle.length <= haystack.length; from++) {
            boolean found = true;
            for (int index = 0; index < needle.length && found; index++) {
                found = haystack[from + index] == needle[index];
            }
            if (found) {
                return from;
            }
        }
        return -1;
    }
}
