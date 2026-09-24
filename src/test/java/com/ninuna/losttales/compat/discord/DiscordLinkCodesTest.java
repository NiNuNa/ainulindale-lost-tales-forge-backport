package com.ninuna.losttales.compat.discord;

import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A link code names what it links, works once, runs out, reads the same
 * however it is typed, and never piles up.
 */
public final class DiscordLinkCodesTest {
    private static final long NOW = 1000000L;

    @After
    public void clear() {
        DiscordLinkCodes.clear();
    }

    @Test
    public void aCodeLinksOnceWhatItWasAskedFor() {
        UUID operator = UUID.randomUUID();
        String code = DiscordLinkCodes.issue("ooc", DiscordBridgeDirection.BIDIRECTIONAL,
                operator, "Nils", NOW);
        assertTrue(code, code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        DiscordLinkCodes.Pending peeked = DiscordLinkCodes.peek(code, NOW);
        assertNotNull(peeked);
        assertEquals("ooc", peeked.gameKey);
        assertEquals("peeking spends nothing", "ooc",
                DiscordLinkCodes.peek(code, NOW).gameKey);
        DiscordLinkCodes.Pending taken = DiscordLinkCodes.take(
                " " + code.toLowerCase(java.util.Locale.ROOT).replace("-", ""), NOW);
        assertNotNull("case, spaces and the dash do not matter", taken);
        assertEquals(operator, taken.issuer);
        assertEquals("Nils", taken.issuerName);
        assertEquals(DiscordBridgeDirection.BIDIRECTIONAL, taken.direction);
        assertNull("a code works once", DiscordLinkCodes.take(code, NOW));
    }

    @Test
    public void aCodeRunsOut() {
        String code = DiscordLinkCodes.issue("global", DiscordBridgeDirection.GAME_TO_DISCORD,
                null, "Server", NOW);
        assertNull(DiscordLinkCodes.take(code, NOW + DiscordLinkCodes.LIFETIME_MILLIS));
    }

    @Test
    public void nothingElseReadsAsACode() {
        DiscordLinkCodes.issue("ooc", DiscordBridgeDirection.BIDIRECTIONAL, null, "", NOW);
        assertNull(DiscordLinkCodes.take(null, NOW));
        assertNull(DiscordLinkCodes.take("", NOW));
        assertNull(DiscordLinkCodes.take("ABCD-EFG!", NOW));
        assertNull(DiscordLinkCodes.take("OOOO-0000", NOW));
        assertEquals("", DiscordLinkCodes.normalized("ABCD-EFGHJ"));
        assertEquals("ABCDEFGH", DiscordLinkCodes.normalized("abcd efgh"));
    }

    @Test
    public void theOldestCodeGoesWhenTooManyWait() {
        String first = DiscordLinkCodes.issue("ooc", DiscordBridgeDirection.BIDIRECTIONAL,
                null, "", NOW);
        for (int index = 0; index < DiscordLinkCodes.MAX_PENDING; index++) {
            DiscordLinkCodes.issue("global", DiscordBridgeDirection.BIDIRECTIONAL, null, "", NOW);
        }
        assertNull(DiscordLinkCodes.peek(first, NOW));
    }
}
