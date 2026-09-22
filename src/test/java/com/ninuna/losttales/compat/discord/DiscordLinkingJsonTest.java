package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What linking reads off Discord: whether the member who typed
 * {@code /link} may manage the channel's webhooks, the webhook the bot
 * made, and the names of the servers and channels it links.
 */
public final class DiscordLinkingJsonTest {

    private static DiscordJson.Interaction interaction(String permissions) {
        JsonObject data = new JsonParser().parse("{\"type\":2,\"id\":\"1\",\"token\":\"t\","
                + "\"guild_id\":\"9\",\"channel_id\":\"5\",\"data\":{\"name\":\"link\","
                + "\"options\":[{\"name\":\"code\",\"value\":\"ABCD-EFGH\"}]},"
                + "\"member\":{\"permissions\":\"" + permissions + "\","
                + "\"user\":{\"username\":\"nils\"}}}").getAsJsonObject();
        return DiscordJson.parseInteraction(data);
    }

    @Test
    public void aMemberMayLinkWithManageWebhooksOrAsAnAdministrator() {
        assertTrue(interaction(String.valueOf(1L << 29))
                .memberMay(DiscordJson.Interaction.MANAGE_WEBHOOKS));
        assertTrue(interaction("8").memberMay(DiscordJson.Interaction.MANAGE_WEBHOOKS));
        assertFalse(interaction(String.valueOf(1L << 11))
                .memberMay(DiscordJson.Interaction.MANAGE_WEBHOOKS));
        assertFalse(interaction("").memberMay(DiscordJson.Interaction.MANAGE_WEBHOOKS));
        assertFalse(interaction("not a number")
                .memberMay(DiscordJson.Interaction.MANAGE_WEBHOOKS));
        assertEquals("ABCD-EFGH", interaction("8").options.get("code"));
    }

    @Test
    public void aMadeWebhookIsReadAsItsAddress() {
        assertEquals("https://discord.com/api/webhooks/123/a_B-c",
                DiscordJson.parseCreatedWebhookUrl(
                        "{\"id\":\"123\",\"token\":\"a_B-c\",\"type\":1}"));
        assertEquals("", DiscordJson.parseCreatedWebhookUrl("{\"id\":\"123\"}"));
        assertEquals("", DiscordJson.parseCreatedWebhookUrl(
                "{\"id\":\"12x\",\"token\":\"abc\"}"));
        assertEquals("", DiscordJson.parseCreatedWebhookUrl(
                "{\"id\":\"123\",\"token\":\"a/b\"}"));
        assertEquals("", DiscordJson.parseCreatedWebhookUrl("not json"));
    }

    @Test
    public void theGatewayNamesTheServersAndTheirChannels() {
        DiscordGuildDirectory directory = new DiscordGuildDirectory();
        directory.onEvent("GUILD_CREATE", new JsonParser().parse("{\"id\":\"9\","
                + "\"name\":\"The Shire\",\"channels\":[{\"id\":\"5\",\"name\":\"general\"}]}")
                .getAsJsonObject());
        assertEquals("The Shire", directory.guildNameOfChannel("5"));
        assertEquals("general", directory.channelName("5"));
        directory.onEvent("GUILD_UPDATE", new JsonParser().parse(
                "{\"id\":\"9\",\"name\":\"Bree\"}").getAsJsonObject());
        assertEquals("Bree", directory.guildNameOfChannel("5"));
        directory.onEvent("CHANNEL_CREATE", new JsonParser().parse(
                "{\"id\":\"6\",\"guild_id\":\"9\",\"name\":\"rp\"}").getAsJsonObject());
        assertEquals("Bree", directory.guildNameOfChannel("6"));
        directory.onEvent("GUILD_DELETE", new JsonParser().parse(
                "{\"id\":\"9\"}").getAsJsonObject());
        assertEquals("", directory.guildNameOfChannel("5"));
        assertEquals("", directory.channelName("6"));
        assertEquals("a name is cut to what the chat shows",
                DiscordGuildDirectory.MAX_NAME_LENGTH,
                DiscordGuildDirectory.clean("A very long Discord server name, longer than"
                        + " any title should be").length());
    }
}
