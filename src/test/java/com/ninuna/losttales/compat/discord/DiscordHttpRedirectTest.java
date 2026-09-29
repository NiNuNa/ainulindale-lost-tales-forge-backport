package com.ninuna.losttales.compat.discord;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * No request to Discord follows a redirect, so the token it carries goes
 * to the address it was written for or nowhere, and a redirect is a
 * failure rather than a reply. Opening a connection does not connect, and
 * the exchange is fed a connection that answers without a network.
 */
public final class DiscordHttpRedirectTest {

    /** A connection that answers with a fixed status and a small body. */
    private static final class Answering extends HttpURLConnection {
        private final int status;
        boolean disconnected;

        Answering(int status) throws IOException {
            super(new URL("https://discord.com/api/webhooks/1/sEcReT"));
            this.status = status;
        }

        @Override
        public int getResponseCode() {
            return this.status;
        }

        @Override
        public String getHeaderField(String name) {
            return null;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream("{}".getBytes());
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream("{}".getBytes());
        }

        @Override
        public void disconnect() {
            this.disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }
    }

    @Test
    public void everyConnectionIsOpenedWithoutFollowingRedirects() throws IOException {
        assertFalse(DiscordHttp.open(DiscordHttp.API_BASE + "/gateway/bot", "GET")
                .getInstanceFollowRedirects());
        assertFalse(DiscordHttp.open("https://discord.com/api/webhooks/1/abc", "POST")
                .getInstanceFollowRedirects());
    }

    @Test
    public void aRedirectIsAFailureAndItsConnectionIsDropped() throws IOException {
        for (int status : new int[] {301, 302, 307, 308}) {
            Answering redirected = new Answering(status);
            try {
                DiscordHttp.exchange(redirected, null);
                fail("HTTP " + status + " should fail");
            } catch (IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("redirect"));
                assertFalse(DiscordHttp.describe(expected).contains("sEcReT"));
            }
            assertTrue(redirected.disconnected);
        }
        Answering ok = new Answering(200);
        DiscordHttp.Reply reply = DiscordHttp.exchange(ok, null);
        assertEquals(200, reply.status);
        assertEquals("{}", reply.body);
        assertFalse("a reply read to its end keeps its connection", ok.disconnected);
    }
}
