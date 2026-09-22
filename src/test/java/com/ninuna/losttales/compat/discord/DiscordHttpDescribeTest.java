package com.ninuna.losttales.compat.discord;

import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * A failure is logged by its kind and message, never with a webhook's
 * token or a slash command's: whoever reads a webhook's token can post
 * through the webhook, and a command's can answer for the bot.
 */
public final class DiscordHttpDescribeTest {

    @Test
    public void aWebhookTokenNeverReachesTheLog() {
        String described = DiscordHttp.describe(new IOException(
                "Server returned HTTP response code: 500 for URL: "
                        + "https://discord.com/api/webhooks/1234/sEcReT-token_1?wait=true"));
        assertFalse(described, described.contains("sEcReT"));
        assertEquals("IOException: Server returned HTTP response code: 500 for URL: "
                + "https://discord.com/api/webhooks/1234/***?wait=true", described);
    }

    @Test
    public void aSlashCommandsTokenNeverReachesTheLog() {
        String described = DiscordHttp.describe(new IOException(
                "Server returned HTTP response code: 400 for URL: "
                        + "https://discord.com/api/v10/interactions/5678/aW50ZXJhY3Rpb24.sEcReT/callback"));
        assertFalse(described, described.contains("sEcReT"));
        assertEquals("IOException: Server returned HTTP response code: 400 for URL: "
                + "https://discord.com/api/v10/interactions/5678/***/callback", described);
    }

    @Test
    public void aFailureWithoutAnAddressReadsAsItIs() {
        assertEquals("IOException: Read timed out",
                DiscordHttp.describe(new IOException("Read timed out")));
        assertEquals("IllegalStateException",
                DiscordHttp.describe(new IllegalStateException()));
        assertEquals("", DiscordHttp.describe(null));
    }
}
