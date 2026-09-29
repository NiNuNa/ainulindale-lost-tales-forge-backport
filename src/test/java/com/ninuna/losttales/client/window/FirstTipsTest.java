package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.chat.ChatLayout;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A new player's three tips (W9 a): shown in turn as the chat first
 * opens, each gone with a click, and never again once the last has gone,
 * which the account's window layout file remembers.
 */
public final class FirstTipsTest {

    @Before
    public void reset() {
        ChatLayout.reset();
        FirstTips.load(0);
    }

    @After
    public void cleanUp() {
        FirstTips.load(0);
        ChatLayout.reset();
    }

    @Test
    public void theThreeTipsShowInTurnAndThenNeverAgain() {
        assertNull("nothing before the chat opens", FirstTips.current());
        FirstTips.chatOpened();
        assertSame(FirstTips.Tip.PLUS, FirstTips.current());
        assertTrue(FirstTips.dismiss());
        assertSame(FirstTips.Tip.HEAD, FirstTips.current());
        assertTrue(FirstTips.dismiss());
        assertSame(FirstTips.Tip.SWITCHER, FirstTips.current());
        assertTrue(FirstTips.dismiss());
        assertNull(FirstTips.current());
        assertFalse("a click with no tip showing is the screen's",
                FirstTips.dismiss());
        FirstTips.screenClosed();
        FirstTips.chatOpened();
        assertNull("never shown again", FirstTips.current());
    }

    @Test
    public void aTipLeftShowingComesBackWithTheNextOpening() {
        FirstTips.chatOpened();
        FirstTips.dismiss();
        FirstTips.screenClosed();
        assertNull(FirstTips.current());
        FirstTips.chatOpened();
        assertSame(FirstTips.Tip.HEAD, FirstTips.current());
    }

    @Test
    public void theLayoutFileRemembersHowManyHaveGone() {
        FirstTips.chatOpened();
        FirstTips.dismiss();
        FirstTips.dismiss();
        FirstTips.dismiss();
        List<String> lines = WindowLayoutStore.describe();
        assertTrue(lines.toString(), lines.contains("tips seen=3"));
        FirstTips.load(0);
        WindowLayoutStore.load(lines);
        assertEquals(3, FirstTips.seen());
        FirstTips.chatOpened();
        assertNull("read back, none shows", FirstTips.current());
    }

    @Test
    public void aNewPlayersFileSaysNothingAndABrokenLineIsIgnored() {
        assertFalse(WindowLayoutStore.describe().toString().contains("tips"));
        WindowLayoutStore.load(java.util.Arrays.asList("tips seen=many"));
        assertEquals(0, FirstTips.seen());
        WindowLayoutStore.load(java.util.Arrays.asList("tips seen=99"));
        assertEquals("never more than there are",
                FirstTips.Tip.values().length, FirstTips.seen());
    }
}
