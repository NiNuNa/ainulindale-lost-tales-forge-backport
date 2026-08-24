package com.ninuna.losttales.compat.discord;

import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The method override must take on this JVM's HTTPS connection — the
 * wrapper and its delegate — without touching the network; opening a
 * connection does not connect.
 */
public final class DiscordHttpPatchTest {

    @Test
    public void httpsConnectionTakesThePatchMethod() throws Exception {
        assertTrue(DiscordHttpPatch.isAvailable());
        HttpURLConnection connection = (HttpURLConnection)new URL(
                DiscordHttp.API_BASE + "/channels/1").openConnection();
        connection.setRequestMethod("POST");
        DiscordHttpPatch.apply(connection);
        assertEquals("PATCH", connection.getRequestMethod());
    }

    @Test
    public void plainHttpConnectionTakesThePatchMethod() throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(
                "http://127.0.0.1:9/").openConnection();
        connection.setRequestMethod("POST");
        DiscordHttpPatch.apply(connection);
        assertEquals("PATCH", connection.getRequestMethod());
    }

    @Test(expected = DiscordHttp.PatchUnsupportedException.class)
    public void nothingToPatchIsRefused() throws Exception {
        DiscordHttpPatch.apply(null);
    }
}
