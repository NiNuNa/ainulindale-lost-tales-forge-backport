package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.LostTalesMetaData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * The three HTTPS calls the bridge makes, on plain {@link HttpURLConnection}
 * so the mod carries no library: a bot-authorised GET of a channel's
 * messages, a webhook POST, and a bot-authorised PATCH of the channel's
 * topic. Bodies are bounded, timeouts are short, and a reply is returned
 * as status plus text — the caller decides what a status means. The
 * token and the webhook URL never reach a log.
 *
 * <p>Java 8's {@code HttpURLConnection} refuses {@code PATCH} as a
 * method name, so the topic write opens as a POST and sets the method
 * on the connection's own field, the way every library-free client on
 * this JVM does; when that cannot be done the write fails with
 * {@link PatchUnsupportedException} and the caller stops trying.</p>
 */
final class DiscordHttp {
    static final String API_BASE = "https://discord.com/api/v10";
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int READ_TIMEOUT_MILLIS = 8000;
    /** A page of fifty messages is far below this; anything bigger is wrong. */
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String USER_AGENT = "DiscordBot (losttales, "
            + LostTalesMetaData.MOD_VERSION + ")";

    private DiscordHttp() {}

    /** A reply: the HTTP status and the body, empty when there was none. */
    static final class Reply {
        final int status;
        final String body;

        Reply(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }

        boolean isSuccess() {
            return this.status >= 200 && this.status < 300;
        }
    }

    /** Reads the messages after {@code afterId} (every message when empty). */
    static Reply getMessages(String botToken, String channelId,
                             String afterId, int limit) throws IOException {
        StringBuilder url = new StringBuilder(API_BASE)
                .append("/channels/").append(channelId)
                .append("/messages?limit=").append(limit);
        if (afterId != null && afterId.length() > 0) {
            url.append("&after=").append(afterId);
        }
        HttpURLConnection connection = open(url.toString(), "GET");
        connection.setRequestProperty("Authorization", "Bot " + botToken);
        return exchange(connection, null);
    }

    /** Posts a webhook body; Discord answers 204 with nothing on success. */
    /**
     * Tells Discord the bot is typing in the channel, which shows its
     * own indicator there for about ten seconds. Presence only: no text
     * crosses with it, and the call carries no body at all.
     */
    static Reply postTyping(String botToken, String channelId)
            throws IOException {
        HttpURLConnection connection = open(
                API_BASE + "/channels/" + channelId + "/typing", "POST");
        connection.setRequestProperty("Authorization", "Bot " + botToken);
        // An empty body rather than none: the exchange sets the length
        // from it, which is what a POST with nothing to say needs.
        return exchange(connection, "");
    }

    static Reply postWebhook(String webhookUrl, String body) throws IOException {
        HttpURLConnection connection = open(webhookUrl, "POST");
        connection.setRequestProperty("Content-Type", "application/json");
        return exchange(connection, body);
    }

    /**
     * Modifies the channel with a JSON body (the topic, here); the bot
     * needs Manage Channels. Discord answers 200 with the channel.
     */
    static Reply patchChannel(String botToken, String channelId, String body)
            throws IOException {
        HttpURLConnection connection = open(
                API_BASE + "/channels/" + channelId, "POST");
        DiscordHttpPatch.apply(connection);
        connection.setRequestProperty("Authorization", "Bot " + botToken);
        connection.setRequestProperty("Content-Type", "application/json");
        return exchange(connection, body);
    }

    /** This JVM's HTTP client cannot send a PATCH; the caller should stop asking. */
    static final class PatchUnsupportedException extends IOException {
        private static final long serialVersionUID = 1L;

        PatchUnsupportedException(String reason) {
            super(reason);
        }
    }

    private static HttpURLConnection open(String url, String method)
            throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection)new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static Reply exchange(HttpURLConnection connection, String body)
            throws IOException {
        try {
            if (body != null) {
                byte[] bytes = body.getBytes(UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream out = connection.getOutputStream();
                try {
                    out.write(bytes);
                } finally {
                    out.close();
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            return new Reply(status, readBounded(stream));
        } finally {
            connection.disconnect();
        }
    }

    private static String readBounded(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) >= 0) {
                if (buffer.size() + read > MAX_RESPONSE_BYTES) {
                    throw new IOException("Discord reply exceeds "
                            + MAX_RESPONSE_BYTES + " bytes");
                }
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), UTF_8);
        } finally {
            stream.close();
        }
    }
}
