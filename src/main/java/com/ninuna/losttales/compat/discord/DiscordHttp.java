package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

/**
 * The three HTTPS calls the bridge makes, on plain {@link HttpURLConnection}
 * so the mod carries no library: a bot-authorised GET of a channel's
 * messages, a webhook POST, and a bot-authorised PATCH of the channel's
 * topic. Bodies are bounded, timeouts are short, and a reply is returned
 * as status, text and what its headers say of the rate limit — the
 * caller decides what a status means. A reply read to its end leaves its
 * connection in the JVM's keep-alive pool, so the next call to Discord
 * skips the TCP and TLS handshake. The token and the webhook URL never
 * reach a log.
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
    /** A webhook's token in an address, which is all its secret is. */
    private static final Pattern WEBHOOK_TOKEN =
            Pattern.compile("(/webhooks/\\d+/)[^/?#\\s]+");

    private DiscordHttp() {}

    /**
     * A failure as a log may show it: its kind and its message, with the
     * token of any webhook address in it blanked, since whoever holds a
     * webhook's address can post through it.
     */
    static String describe(Throwable failure) {
        if (failure == null) {
            return "";
        }
        String message = failure.getMessage();
        String text = failure.getClass().getSimpleName()
                + (message == null ? "" : ": " + message);
        return WEBHOOK_TOKEN.matcher(text).replaceAll("$1***");
    }

    /**
     * A reply: the HTTP status, the body (empty when there was none), and
     * what the reply's headers say of the rate limit it was answered
     * under ({@link DiscordRateLimit#NONE} when they say nothing).
     */
    static final class Reply {
        final int status;
        final String body;
        final DiscordRateLimit limit;

        Reply(int status, String body) {
            this(status, body, DiscordRateLimit.NONE);
        }

        Reply(int status, String body, DiscordRateLimit limit) {
            this.status = status;
            this.body = body == null ? "" : body;
            this.limit = limit == null ? DiscordRateLimit.NONE : limit;
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
        // wait=true has Discord answer with the created message instead
        // of an empty 204: its id is what links the post to the game
        // line it carries, so a later reply can point back at it.
        HttpURLConnection connection = open(webhookUrl
                + (webhookUrl.indexOf('?') < 0 ? "?" : "&") + "wait=true",
                "POST");
        connection.setRequestProperty("Content-Type", "application/json");
        return exchange(connection, body);
    }

    /**
     * Reads the webhook object its own URL answers with — no bot, the
     * token in the URL is the authorisation — which is where the guild
     * and channel ids a jump link needs come from.
     */
    static Reply getWebhookInfo(String webhookUrl) throws IOException {
        return exchange(open(webhookUrl, "GET"), null);
    }

    /**
     * Reads a channel object by its id with the bot: what says at start
     * whether a channel the bindings read exists and is the bot's to
     * see, and which guild holds it.
     */
    static Reply getChannel(String botToken, String channelId) throws IOException {
        HttpURLConnection connection = open(
                API_BASE + "/channels/" + channelId, "GET");
        connection.setRequestProperty("Authorization", "Bot " + botToken);
        return exchange(connection, null);
    }

    /**
     * Rewrites a message the webhook itself posted; Discord answers 200
     * with the message. Only the webhook's own posts can be rewritten,
     * which is exactly the set the bridge ever asks about.
     */
    static Reply editWebhookMessage(String webhookUrl, String messageId,
                                    String body) throws IOException {
        HttpURLConnection connection =
                open(webhookMessageUrl(webhookUrl, messageId), "POST");
        DiscordHttpPatch.apply(connection);
        connection.setRequestProperty("Content-Type", "application/json");
        return exchange(connection, body);
    }

    /**
     * Takes back a message the webhook itself posted; Discord answers
     * 204 with nothing.
     */
    static Reply deleteWebhookMessage(String webhookUrl, String messageId)
            throws IOException {
        return exchange(open(webhookMessageUrl(webhookUrl, messageId),
                "DELETE"), null);
    }

    /**
     * Makes a webhook in a channel for the bridge to post through, with
     * the bot's token; the bot needs Manage Webhooks there. Discord
     * answers with the webhook, its id and its token among it.
     * {@code channelId} is a snowflake the caller has checked.
     */
    static Reply createWebhook(String botToken, String channelId, String body)
            throws IOException {
        HttpURLConnection connection = open(
                API_BASE + "/channels/" + channelId + "/webhooks", "POST");
        connection.setRequestProperty("Authorization", "Bot " + botToken);
        connection.setRequestProperty("Content-Type", "application/json");
        return exchange(connection, body);
    }

    /**
     * Deletes a webhook through its own address, whose token authorises
     * it: the bridge's clean-up once a link is taken away. Discord
     * answers 204, or 404 when it is gone already.
     */
    static Reply deleteWebhook(String webhookUrl) throws IOException {
        return exchange(open(webhookUrl, "DELETE"), null);
    }

    /**
     * Puts the bot's own reaction on a message; Discord answers 204. The
     * bot needs Add Reactions and Read Message History in the channel.
     * A PUT with an empty body, so Discord is told its length is zero.
     */
    static Reply putOwnReaction(String botToken, String channelId,
                                String messageId, String emoji)
            throws IOException {
        HttpURLConnection connection = open(
                ownReactionUrl(channelId, messageId, emoji), "PUT");
        connection.setRequestProperty("Authorization", "Bot " + botToken);
        return exchange(connection, "");
    }

    /** Takes the bot's own reaction off a message; Discord answers 204. */
    static Reply deleteOwnReaction(String botToken, String channelId,
                                   String messageId, String emoji)
            throws IOException {
        HttpURLConnection connection = open(
                ownReactionUrl(channelId, messageId, emoji), "DELETE");
        connection.setRequestProperty("Authorization", "Bot " + botToken);
        return exchange(connection, null);
    }

    /**
     * The bot's own reaction with an emoji. A custom emoji goes as its
     * {@code name:id}, which Discord asks for in that form and which is
     * safe in a path as it stands: its name is letters, digits and
     * underscores, its id digits. A Unicode emoji is percent-encoded.
     */
    static String ownReactionUrl(String channelId, String messageId,
                                 String emoji) throws IOException {
        String segment = ChatForeignEmoji.isCustom(emoji) ? emoji
                : URLEncoder.encode(emoji, "UTF-8");
        return API_BASE + "/channels/" + channelId + "/messages/" + messageId
                + "/reactions/" + segment + "/@me";
    }

    /** One message of the webhook, any query on the base URL set aside. */
    private static String webhookMessageUrl(String webhookUrl,
                                            String messageId) {
        int query = webhookUrl.indexOf('?');
        String base = query < 0 ? webhookUrl
                : webhookUrl.substring(0, query);
        return base + "/messages/" + messageId;
    }

    /** Where the gateway is, and how many sessions the bot may open. */
    static Reply getGateway(String botToken) throws IOException {
        HttpURLConnection connection = open(API_BASE + "/gateway/bot", "GET");
        connection.setRequestProperty("Authorization", "Bot " + botToken);
        return exchange(connection, null);
    }

    /**
     * Replaces the bot's slash commands in one guild with the given
     * list; guild commands are usable at once. Discord answers 200.
     */
    static Reply putGuildCommands(String botToken, String applicationId, String guildId,
                                  String body) throws IOException {
        HttpURLConnection connection = open(API_BASE + "/applications/" + applicationId
                + "/guilds/" + guildId + "/commands", "PUT");
        connection.setRequestProperty("Authorization", "Bot " + botToken);
        connection.setRequestProperty("Content-Type", "application/json");
        return exchange(connection, body);
    }

    /**
     * The first answer to an interaction, within Discord's three seconds:
     * here always the deferred one, which buys the time the real answer
     * needs. No bot header: the interaction's own token authorises it.
     */
    static Reply postInteractionCallback(String interactionId, String interactionToken,
                                         String body) throws IOException {
        HttpURLConnection connection = open(API_BASE + "/interactions/" + interactionId
                + "/" + interactionToken + "/callback", "POST");
        connection.setRequestProperty("Content-Type", "application/json");
        return exchange(connection, body);
    }

    /** Fills the deferred answer in; the interaction's token authorises it. */
    static Reply patchInteractionOriginal(String applicationId, String interactionToken,
                                          String body) throws IOException {
        HttpURLConnection connection = open(API_BASE + "/webhooks/" + applicationId + "/"
                + interactionToken + "/messages/@original", "POST");
        DiscordHttpPatch.apply(connection);
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
        // A reply read to its end hands its socket back to the JVM's
        // keep-alive pool for the next call; only an exchange that broke
        // off part way closes it, since what is left on it cannot be
        // trusted.
        boolean complete = false;
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
            DiscordRateLimit limit = rateLimitOf(connection);
            InputStream stream = status >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            Reply reply = new Reply(status, readBounded(stream), limit);
            complete = true;
            return reply;
        } finally {
            if (!complete) {
                connection.disconnect();
            }
        }
    }

    /**
     * What a reply's headers say of the rate limit it was answered
     * under. The headers are advice: a reply whose headers cannot be
     * read stands without them.
     */
    private static DiscordRateLimit rateLimitOf(HttpURLConnection connection) {
        try {
            return DiscordRateLimit.parse(
                    connection.getHeaderField("X-RateLimit-Remaining"),
                    connection.getHeaderField("X-RateLimit-Reset-After"),
                    connection.getHeaderField("X-RateLimit-Bucket"),
                    connection.getHeaderField("X-RateLimit-Global"),
                    connection.getHeaderField("X-RateLimit-Scope"));
        } catch (RuntimeException unreadable) {
            return DiscordRateLimit.NONE;
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
