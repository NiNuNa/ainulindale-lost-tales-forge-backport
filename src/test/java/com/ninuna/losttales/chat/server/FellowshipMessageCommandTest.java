package com.ninuna.losttales.chat.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * /fmsg reads as LOTR's does: a fellowship named in quotes and the words,
 * the words alone for the bound fellowship, bind with a name in quotes and
 * nothing after it, and unbind.
 */
public final class FellowshipMessageCommandTest {

    @Test
    public void aNameInQuotesThenTheWords() {
        FellowshipMessageCommand.Request request =
                FellowshipMessageCommand.Request.parse("\"Grey Company\" ride at dawn");
        assertEquals("Grey Company", request.name);
        assertEquals("ride at dawn", request.words);
    }

    @Test
    public void theWordsAloneGoToTheBoundFellowship() {
        FellowshipMessageCommand.Request request =
                FellowshipMessageCommand.Request.parse("ride at dawn");
        assertNull(request.name);
        assertEquals("ride at dawn", request.words);
    }

    @Test
    public void bindNamesOneAndUnbindLetsGo() {
        FellowshipMessageCommand.Request bind =
                FellowshipMessageCommand.Request.parse("bind \"Grey Company\"");
        assertTrue(bind.bind);
        assertEquals("Grey Company", bind.name);
        assertTrue(FellowshipMessageCommand.Request.parse("unbind").unbind);
    }

    @Test
    public void anythingElseIsNoRequest() {
        assertNull(FellowshipMessageCommand.Request.parse(""));
        assertNull("a name and nothing to say",
                FellowshipMessageCommand.Request.parse("\"Grey Company\""));
        assertNull("an unclosed name",
                FellowshipMessageCommand.Request.parse("\"Grey Company ride"));
        assertNull("bind needs a name in quotes",
                FellowshipMessageCommand.Request.parse("bind Grey Company"));
        assertNull("and nothing after it",
                FellowshipMessageCommand.Request.parse("bind \"Grey\" now"));
        assertNull("an empty name", FellowshipMessageCommand.Request.parse("\"\" ride"));
    }
}
