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
        assertEquals("1", latest.authorId);
        assertEquals("", messages.get(0).authorId);
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
    public void noticesAreOneEmbedWithTheLineOnTheAuthorRow() {
        JsonObject body = new JsonParser().parse(DiscordJson.webhookEmbedBody(
                DiscordServerNotices.playerDied("Steve fell from a high place",
                        "https://heads/Steve"))).getAsJsonObject();
        assertFalse(body.has("content"));
        assertFalse(body.has("username"));
        assertEquals(1, body.getAsJsonArray("embeds").size());
        JsonObject embed = body.getAsJsonArray("embeds").get(0)
                .getAsJsonObject();
        assertEquals(0x5D4550, embed.get("color").getAsInt());
        JsonObject author = embed.getAsJsonObject("author");
        assertEquals("💀 Steve fell from a high place",
                author.get("name").getAsString());
        assertEquals("https://heads/Steve", author.get("icon_url").getAsString());
        assertEquals(0, body.getAsJsonObject("allowed_mentions")
                .getAsJsonArray("parse").size());

        // No picture, no icon field; the colour is never negative.
        JsonObject plain = new JsonParser().parse(DiscordJson.webhookEmbedBody(
                DiscordServerNotices.serverStarted())).getAsJsonObject();
        JsonObject plainEmbed = plain.getAsJsonArray("embeds").get(0)
                .getAsJsonObject();
        assertFalse(plainEmbed.getAsJsonObject("author").has("icon_url"));
        assertTrue(plainEmbed.get("color").getAsInt() >= 0);
        assertTrue(plainEmbed.get("color").getAsInt() <= 0xFFFFFF);
    }

    @Test
    public void repliesCarryTheReferencedIdAndForwardsDoNot() {
        List<DiscordJson.Message> messages = DiscordJson.parseMessages("["
                + "{\"id\":\"40\",\"content\":\"a reply\","
                + "\"message_reference\":{\"message_id\":\"30\",\"type\":0}},"
                + "{\"id\":\"41\",\"content\":\"typeless reply\","
                + "\"message_reference\":{\"message_id\":\"30\"}},"
                + "{\"id\":\"42\",\"content\":\"a forward\","
                + "\"message_reference\":{\"message_id\":\"30\",\"type\":1}},"
                + "{\"id\":\"43\",\"content\":\"plain\"}"
                + "]");
        assertEquals(4, messages.size());
        // Oldest first: the listing above arrives reversed.
        assertEquals("", messages.get(0).referencedMessageId);
        assertEquals("", messages.get(1).referencedMessageId);
        assertEquals("30", messages.get(2).referencedMessageId);
        assertEquals("30", messages.get(3).referencedMessageId);
    }

    @Test
    public void editStampsAreReadAndEmptyForUneditedMessages() {
        List<DiscordJson.Message> messages = DiscordJson.parseMessages("["
                + "{\"id\":\"50\",\"content\":\"edited\","
                + "\"edited_timestamp\":\"2026-09-01T00:00:00Z\"},"
                + "{\"id\":\"40\",\"content\":\"plain\","
                + "\"edited_timestamp\":null},"
                + "{\"id\":\"30\",\"content\":\"bare\"}"
                + "]");
        assertEquals(3, messages.size());
        assertEquals("", messages.get(0).editedTimestamp);
        assertEquals("", messages.get(1).editedTimestamp);
        assertEquals("2026-09-01T00:00:00Z", messages.get(2).editedTimestamp);
    }

    @Test
    public void createdMessageIdsAreReadFromWaitReplies() {
        assertEquals("123456", DiscordJson.parseCreatedMessageId(
                "{\"id\":\"123456\",\"content\":\"hi\"}"));
        assertEquals("", DiscordJson.parseCreatedMessageId("{}"));
        assertEquals("", DiscordJson.parseCreatedMessageId("[]"));
        assertEquals("", DiscordJson.parseCreatedMessageId("garbage"));
        assertEquals("", DiscordJson.parseCreatedMessageId(null));
        assertEquals("", DiscordJson.parseCreatedMessageId(""));
    }

    @Test
    public void webhookInfoNeedsBothIds() {
        DiscordJson.ChannelInfo info = DiscordJson.parseWebhookInfo(
                "{\"guild_id\":\"9\",\"channel_id\":\"8\",\"name\":\"hook\"}");
        assertEquals("9", info.guildId);
        assertEquals("8", info.channelId);
        assertEquals(null, DiscordJson.parseWebhookInfo(
                "{\"channel_id\":\"8\"}"));
        assertEquals(null, DiscordJson.parseWebhookInfo("{}"));
        assertEquals(null, DiscordJson.parseWebhookInfo("garbage"));
        assertEquals(null, DiscordJson.parseWebhookInfo(null));
    }

    @Test
    public void channelInfoIsReadFromTheChannelObject() {
        DiscordJson.ChannelInfo info = DiscordJson.parseChannelInfo(
                "{\"id\":\"8\",\"guild_id\":\"9\",\"type\":0,\"name\":\"ooc\"}");
        assertEquals("9", info.guildId);
        assertEquals("8", info.channelId);
        // A channel outside any guild is nothing the bridge binds.
        assertEquals(null, DiscordJson.parseChannelInfo("{\"id\":\"8\",\"type\":1}"));
        assertEquals(null, DiscordJson.parseChannelInfo("{\"message\":\"Unknown Channel\"}"));
        assertEquals(null, DiscordJson.parseChannelInfo("[]"));
        assertEquals(null, DiscordJson.parseChannelInfo(null));
    }

    @Test
    public void lineBodiesAreTextUnderTheSendersNameAndPicture() {
        JsonObject body = new JsonParser().parse(
                DiscordJson.webhookLineBody("Aragorn, the Gondor Farmer",
                        "https://heads/Aragorn", "-# header\n@everyone hi"))
                .getAsJsonObject();
        assertEquals("Aragorn, the Gondor Farmer", body.get("username").getAsString());
        assertEquals("https://heads/Aragorn", body.get("avatar_url").getAsString());
        assertEquals("the reply opens the text",
                "-# header\n@everyone hi", body.get("content").getAsString());
        assertFalse("a line is text, never a card", body.has("embeds"));
        assertEquals(0, body.getAsJsonObject("allowed_mentions")
                .getAsJsonArray("parse").size());
    }

    @Test
    public void aBareLineNamesNobodyAndShowsNoPicture() {
        JsonObject body = new JsonParser().parse(
                DiscordJson.webhookLineBody("", "", "hi")).getAsJsonObject();
        assertFalse(body.has("username"));
        assertFalse(body.has("avatar_url"));
        assertFalse(body.has("embeds"));
        assertEquals("hi", body.get("content").getAsString());
        // Nothing at all still makes a well-formed body.
        assertEquals("", new JsonParser().parse(
                DiscordJson.webhookLineBody(null, null, null))
                .getAsJsonObject().get("content").getAsString());
    }

    @Test
    public void lineEditBodiesCarryOnlyTheNewTextAndPingNobody() {
        JsonObject body = new JsonParser().parse(
                DiscordJson.webhookLineEditBody("-# q\n@everyone corrected"))
                .getAsJsonObject();
        assertFalse(body.has("username"));
        assertFalse(body.has("avatar_url"));
        assertFalse(body.has("embeds"));
        assertEquals("-# q\n@everyone corrected", body.get("content").getAsString());
        assertEquals(0, body.getAsJsonObject("allowed_mentions")
                .getAsJsonArray("parse").size());
        assertEquals("", new JsonParser().parse(
                DiscordJson.webhookLineEditBody(null))
                .getAsJsonObject().get("content").getAsString());
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
