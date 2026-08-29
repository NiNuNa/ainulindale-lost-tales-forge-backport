package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What the log is for: a reply may quote a message only if the server
 * actually sent that message to the player replying to it, and a
 * message may be rewritten or taken back only by the account that wrote
 * it.
 */
public final class ChatMessageLogTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();

    @Before
    public void setUp() {
        ChatMessageLog.clear();
        ChatMessageIdAllocator.reset();
    }

    @After
    public void tearDown() {
        ChatMessageLog.clear();
        ChatMessageIdAllocator.reset();
    }

    @Test
    public void aRecipientIsQuotedTheMessageTheyWereSent() {
        long id = ChatMessageIdAllocator.next();
        ChatMessageLog.record(id, ALICE, "Aldric", "meet me at the gate",
                Arrays.asList(ALICE, BOB));
        ChatReplyReference quote = ChatMessageLog.quoteFor(id, BOB);
        assertTrue(quote.exists());
        assertEquals(id, quote.getMessageId());
        assertEquals("Aldric", quote.getAuthor());
        assertEquals("meet me at the gate", quote.getExcerpt());
    }

    /**
     * The check that matters: naming the id of a whisper you were not in
     * quotes nothing, so a reply can never echo a private message into a
     * channel of the replier's choosing.
     */
    @Test
    public void someoneWhoWasNotSentItIsQuotedNothing() {
        long id = ChatMessageIdAllocator.next();
        ChatMessageLog.record(id, ALICE, "Aldric", "the key is under the barrel",
                Arrays.asList(ALICE, BOB));
        assertFalse(ChatMessageLog.quoteFor(id, CAROL).exists());
        assertFalse(ChatMessageLog.quoteFor(id, null).exists());
    }

    /** An id nobody ever recorded quotes nothing. */
    @Test
    public void anUnknownMessageQuotesNothing() {
        assertFalse(ChatMessageLog.quoteFor(1234L, ALICE).exists());
        assertFalse(ChatMessageLog.quoteFor(ChatMessageIds.NONE, ALICE)
                .exists());
    }

    /** A line nobody can name is not worth recording. */
    @Test
    public void unnamedOrUnsignedMessagesAreNotRecorded() {
        ChatMessageLog.record(ChatMessageIds.NONE, ALICE, "Aldric", "hello",
                Collections.singletonList(ALICE));
        ChatMessageLog.record(ChatMessageIdAllocator.next(), ALICE, "  ", "hello",
                Collections.singletonList(ALICE));
        assertEquals(0, ChatMessageLog.size());
    }

    /** A quote is one glanceable line, not the message over again. */
    @Test
    public void aLongMessageIsQuotedAsAnExcerpt() {
        StringBuilder long_ = new StringBuilder();
        while (long_.length()
                < ChatReplyReference.MAX_EXCERPT_CHARACTERS + 40) {
            long_.append('x');
        }
        long id = ChatMessageIdAllocator.next();
        ChatMessageLog.record(id, ALICE, "Aldric", long_.toString(),
                Collections.singletonList(ALICE));
        String excerpt = ChatMessageLog.quoteFor(id, ALICE).getExcerpt();
        assertTrue(excerpt.length()
                < ChatReplyReference.MAX_EXCERPT_CHARACTERS + 5);
        assertTrue(excerpt.endsWith("..."));
    }

    /** A message old enough to fall out of reach is no longer replyable. */
    @Test
    public void theLogReachesOnlySoFarBack() {
        long oldest = ChatMessageIdAllocator.next();
        ChatMessageLog.record(oldest, ALICE, "Aldric", "the first thing said",
                Collections.singletonList(ALICE));
        for (int index = 0; index < 400; index++) {
            ChatMessageLog.record(ChatMessageIdAllocator.next(), ALICE, "Aldric",
                    "and another", Collections.singletonList(ALICE));
        }
        assertFalse(ChatMessageLog.quoteFor(oldest, ALICE).exists());
        assertTrue(ChatMessageLog.size() < 400);

        ChatMessageLog.clear();
        assertEquals(0, ChatMessageLog.size());
    }

    /**
     * The check that matters for editing: a message can only be changed
     * by the account that wrote it. Being sent one is not enough — every
     * recipient knows its id.
     */
    @Test
    public void onlyTheAuthorMayChangeAMessage() {
        long id = ChatMessageIdAllocator.next();
        ChatMessageLog.record(id, ALICE, "Aldric", "meet me at the gate",
                Arrays.asList(ALICE, BOB));
        assertNull(ChatMessageLog.applyEdit(id, BOB, "meet me at the tower"));
        assertNull(ChatMessageLog.remove(id, BOB));
        assertNull(ChatMessageLog.applyEdit(id, null, "nobody at all"));
        // Untouched by any of it.
        assertEquals("meet me at the gate",
                ChatMessageLog.quoteFor(id, BOB).getExcerpt());
    }

    /** An edit reaches exactly the accounts the original reached. */
    @Test
    public void anEditIsToldToEveryoneWhoWasSentTheMessage() {
        long id = ChatMessageIdAllocator.next();
        ChatMessageLog.record(id, ALICE, "Aldric", "meet me at the gate",
                Arrays.asList(ALICE, BOB));
        Set<UUID> told = ChatMessageLog.applyEdit(id, ALICE,
                "meet me at the tower");
        assertNotNull(told);
        assertTrue(told.contains(ALICE));
        assertTrue(told.contains(BOB));
        assertFalse(told.contains(CAROL));
        // And a reply made afterwards quotes what it says now.
        assertEquals("meet me at the tower",
                ChatMessageLog.quoteFor(id, BOB).getExcerpt());
    }

    /** A removed message is no longer there to be quoted or changed. */
    @Test
    public void aRemovedMessageIsGoneForGood() {
        long id = ChatMessageIdAllocator.next();
        ChatMessageLog.record(id, ALICE, "Aldric", "forget I said that",
                Arrays.asList(ALICE, BOB));
        Set<UUID> told = ChatMessageLog.remove(id, ALICE);
        assertNotNull(told);
        assertTrue(told.contains(BOB));
        assertFalse(ChatMessageLog.quoteFor(id, BOB).exists());
        assertNull(ChatMessageLog.applyEdit(id, ALICE, "or that"));
    }

    /** Out of the log's reach is out of reach for editing too. */
    @Test
    public void aMessageTooOldToQuoteIsTooOldToEdit() {
        long oldest = ChatMessageIdAllocator.next();
        ChatMessageLog.record(oldest, ALICE, "Aldric", "the first thing said",
                Collections.singletonList(ALICE));
        for (int index = 0; index < 400; index++) {
            ChatMessageLog.record(ChatMessageIdAllocator.next(), ALICE,
                    "Aldric", "and another",
                    Collections.singletonList(ALICE));
        }
        assertNull(ChatMessageLog.applyEdit(oldest, ALICE, "on reflection"));
        assertNull(ChatMessageLog.remove(oldest, ALICE));
    }
}
