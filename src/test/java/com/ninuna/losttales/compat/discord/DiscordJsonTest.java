package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DiscordJsonTest {

    @Test
    public void channelListingsParseOldestFirstWithAuthorsAndMentions() {
        String json = "["
                + "{\"id\":\"30\",\"content\":\"later <@7>\",\"author\":"
                + "{\"id\":\"1\",\"username\":\"sam\",\"global_name\":\"Sam\","
                + "\"bot\":false},\"mentions\":[{\"id\":\"7\","
                + "\"username\":\"frodo\",\"global_name\":null}]},"
                + "{\"id\":\"20\",\"content\":\"relayed\",\"author\":"
                + "{\"id\":\"2\",\"username\":\"hook\",\"bot\":true}},"
                + "{\"id\":\"10\",\"content\":\"first\"},"
                + "\"not a message\","
                + "{\"content\":\"no id\"}"
                + "]";
        List<DiscordJson.Message> messages = DiscordJson.parseMessages(json);
        assertEquals(3, messages.size());
        assertEquals("10", messages.get(0).id);
        assertEquals("", messages.get(0).authorName);
        assertEquals("20", messages.get(1).id);
        assertTrue(messages.get(1).bot);
        assertEquals("hook", messages.get(1).authorName);
        DiscordJson.Message latest = messages.get(2);
        assertEquals("30", latest.id);
        assertEquals("Sam", latest.authorName);
        assertFalse(latest.bot);
        assertEquals("later <@7>", latest.content);
        // A null global name falls back to the username.
        assertEquals("frodo", latest.mentionNames.get("7"));
    }

    @Test
    public void anythingElseParsesToNothing() {
        assertTrue(DiscordJson.parseMessages(null).isEmpty());
        assertTrue(DiscordJson.parseMessages("").isEmpty());
        assertTrue(DiscordJson.parseMessages("{\"message\":\"401\"}").isEmpty());
        assertTrue(DiscordJson.parseMessages("not json at all").isEmpty());
    }

    @Test
    public void webhookBodiesCarryTheNameAndPingNobody() {
        JsonObject body = new JsonParser().parse(
                DiscordJson.webhookBody("Aragorn", "https://heads/Aragorn",
                        "@everyone hello")).getAsJsonObject();
        assertEquals("@everyone hello", body.get("content").getAsString());
        assertEquals("Aragorn", body.get("username").getAsString());
        assertEquals("https://heads/Aragorn",
                body.get("avatar_url").getAsString());
        assertEquals(0, body.getAsJsonObject("allowed_mentions")
                .getAsJsonArray("parse").size());
        JsonObject nameless = new JsonParser().parse(
                DiscordJson.webhookBody("", "", "hi")).getAsJsonObject();
        assertFalse(nameless.has("username"));
        assertFalse(nameless.has("avatar_url"));
    }

    @Test
    public void rateLimitWaitsAreReadInMilliseconds() {
        assertEquals(1500L, DiscordJson.retryAfterMillis(
                "{\"message\":\"slow down\",\"retry_after\":1.5}"));
        assertEquals(0L, DiscordJson.retryAfterMillis("{}"));
        assertEquals(0L, DiscordJson.retryAfterMillis("garbage"));
        assertEquals(0L, DiscordJson.retryAfterMillis(null));
    }
}
