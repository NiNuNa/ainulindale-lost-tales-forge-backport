package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatMessageIds;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A message id has to be something every client can agree on, so the
 * one thing it must never do is repeat.
 */
public final class ChatMessageIdAllocatorTest {

    @Before
    public void setUp() {
        ChatMessageIdAllocator.reset();
    }

    @After
    public void tearDown() {
        ChatMessageIdAllocator.reset();
    }

    /** Zero is reserved for a line nobody can name. */
    @Test
    public void everyIdIsAnIdAtAll() {
        for (int index = 0; index < 100; index++) {
            assertTrue(ChatMessageIdAllocator.next() > ChatMessageIds.NONE);
        }
    }

    /**
     * A burst inside one millisecond still gets an id each, in the order
     * the server accepted them.
     */
    @Test
    public void idsStrictlyIncreaseThroughABurst() {
        long previous = ChatMessageIdAllocator.next();
        for (int index = 0; index < 10000; index++) {
            long id = ChatMessageIdAllocator.next();
            assertTrue("id " + id + " did not follow " + previous,
                    id > previous);
            previous = id;
        }
    }

    /** Ids are roughly the time the message was sent. */
    @Test
    public void idsTrackTheClock() {
        long before = System.currentTimeMillis();
        long id = ChatMessageIdAllocator.next();
        long after = System.currentTimeMillis();
        assertTrue(id >= before && id <= after);
    }

    /**
     * A clock stepping backwards mid-run must not hand out an id twice:
     * the counter carries on from the last one instead. Simulated by
     * allocating an id far in the future and checking the next one still
     * follows it.
     */
    @Test
    public void aBackwardClockCannotRepeatAnId() {
        // The first call takes the clock; the burst then runs the
        // counter well past it, which is the same state a clock that
        // has since stepped backwards leaves behind.
        long ahead = ChatMessageIdAllocator.next();
        for (int index = 0; index < 5000; index++) {
            ahead = ChatMessageIdAllocator.next();
        }
        assertTrue(ahead > System.currentTimeMillis());
        assertEquals(ahead + 1L, ChatMessageIdAllocator.next());
    }
}
