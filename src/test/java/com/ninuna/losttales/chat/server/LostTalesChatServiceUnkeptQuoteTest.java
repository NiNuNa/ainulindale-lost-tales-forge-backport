package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatReplyReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A quote of a line no server holds a record of reads as a message no
 * longer kept everywhere it is shown, Discord included: there it stands
 * in an author's place, with no words and no message to point at.
 */
public final class LostTalesChatServiceUnkeptQuoteTest {

    @Test
    public void discordIsShownAMessageNoLongerKept() {
        ChatReplyReference posted = LostTalesChatService.forDiscord(
                ChatReplyReference.UNKEPT);
        assertTrue(posted.exists());
        assertFalse(posted.isAnchored());
        assertFalse(posted.hasHead());
        assertEquals(ChatReplyReference.UNKEPT_WORDS, posted.getAuthor());
        assertEquals("", posted.getExcerpt());
    }

    @Test
    public void everyOtherQuoteGoesToDiscordAsItIs() {
        ChatReplyReference quote = ChatReplyReference.of(1234L, "Aldric",
                "Well met.");
        assertSame(quote, LostTalesChatService.forDiscord(quote));
        assertSame(ChatReplyReference.NONE,
                LostTalesChatService.forDiscord(ChatReplyReference.NONE));
        assertNull(LostTalesChatService.forDiscord(null));
    }

    @Test
    public void aQuoteNoLongerKeptWearsNoHeadAndIsNoAction() {
        ChatReplyReference unkept = ChatReplyReference.UNKEPT;
        assertSame(unkept, unkept.withHead(java.util.UUID.randomUUID(), true, ""));
        assertSame(unkept, unkept.withNpcHead(java.util.UUID.randomUUID(), "x.png"));
        assertSame(unkept, unkept.asAction(true));
        assertFalse(unkept.isForward());
    }
}
