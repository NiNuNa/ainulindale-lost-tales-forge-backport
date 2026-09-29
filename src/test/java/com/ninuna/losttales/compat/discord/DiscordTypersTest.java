package com.ninuna.losttales.compat.discord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class DiscordTypersTest {
    @Test
    public void aTyperIsToldAtOnceThenEveryFewSeconds() {
        DiscordTypers typers = new DiscordTypers();
        assertNotNull(typers.typing("c", "u", "Ann", 0L));
        // Discord says it again before the game needs telling.
        assertNull(typers.typing("c", "u", "Ann", 1000L));
        assertTrue(typers.due(4999L).isEmpty());
        List<DiscordTypers.Typer> due = typers.due(DiscordTypers.RESEND_MILLIS);
        assertEquals(1, due.size());
        assertEquals("Ann", due.get(0).name);
    }

    @Test
    public void aTyperIsLetGoWhenDiscordStopsSayingSo() {
        DiscordTypers typers = new DiscordTypers();
        typers.typing("c", "u", "Ann", 0L);
        assertTrue(typers.due(DiscordTypers.SHOWN_MILLIS).isEmpty());
        assertNull("gone once let go", typers.stopped("c", "u"));
    }

    @Test
    public void aMessageEndsTheTyping() {
        DiscordTypers typers = new DiscordTypers();
        typers.typing("c", "u", "Ann", 0L);
        assertNotNull(typers.stopped("c", "u"));
        assertTrue(typers.due(DiscordTypers.RESEND_MILLIS).isEmpty());
        assertNotNull("typing again is told at once", typers.typing("c", "u", "Ann", 1L));
    }

    @Test
    public void theOldestTyperGoesFirst() {
        DiscordTypers typers = new DiscordTypers();
        for (int index = 0; index <= DiscordTypers.MAX_TYPERS; index++) {
            typers.typing("c", "u" + index, "N" + index, 0L);
        }
        assertNull(typers.stopped("c", "u0"));
        assertNotNull(typers.stopped("c", "u" + DiscordTypers.MAX_TYPERS));
    }

    @Test
    public void theSameMemberTypesApartInTwoChannels() {
        DiscordTypers typers = new DiscordTypers();
        assertNotNull(typers.typing("a", "u", "Ann", 0L));
        assertNotNull(typers.typing("b", "u", "Ann", 0L));
        assertEquals(2, typers.due(DiscordTypers.RESEND_MILLIS).size());
    }
}
