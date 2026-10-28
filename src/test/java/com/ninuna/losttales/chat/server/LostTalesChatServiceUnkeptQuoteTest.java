package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatReplyReference;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

/**
 * A quote of a line no server holds a record of reads as a message no
 * longer kept everywhere it is shown; on Discord each linked channel
 * words it in its own language (the bridge's {@code quoteAuthor}).
 */
public final class LostTalesChatServiceUnkeptQuoteTest {

    @Test
    public void aQuoteNoLongerKeptWearsNoHeadAndIsNoAction() {
        ChatReplyReference unkept = ChatReplyReference.UNKEPT;
        assertSame(unkept, unkept.withHead(java.util.UUID.randomUUID(), true, ""));
        assertSame(unkept, unkept.withNpcHead(java.util.UUID.randomUUID(), "x.png"));
        assertSame(unkept, unkept.asAction(true));
        assertFalse(unkept.isForward());
    }
}
