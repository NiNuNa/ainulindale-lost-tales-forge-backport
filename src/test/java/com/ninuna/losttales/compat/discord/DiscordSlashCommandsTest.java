package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The slash commands' definitions and wording, and the interaction they arrive as. */
public final class DiscordSlashCommandsTest {

    private static final List<DiscordSlashCommands.Player> PLAYERS = Arrays.asList(
            new DiscordSlashCommands.Player("steve", "Aragorn", "Human", "Gondor", 7),
            new DiscordSlashCommands.Player("Alex", "", "", "", 0));

    @Test
    public void theDefinitionsNameThreeCommandsAndWhoTakesAName() {
        JsonArray commands = new JsonParser().parse(DiscordSlashCommands.definitionsBody())
                .getAsJsonArray();
        assertEquals(3, commands.size());
        JsonObject who = commands.get(1).getAsJsonObject();
        assertEquals("who", who.get("name").getAsString());
        JsonObject option = who.getAsJsonArray("options").get(0).getAsJsonObject();
        assertEquals("name", option.get("name").getAsString());
        assertEquals(3, option.get("type").getAsInt());
        assertTrue(option.get("required").getAsBoolean());
    }

    @Test
    public void onlineListsEveryoneAlphabeticallyWithTheirCharacter() {
        assertEquals("**2 online:** Alex, steve (as Aragorn)",
                DiscordSlashCommands.online(PLAYERS));
        assertEquals("**Nobody is online.**",
                DiscordSlashCommands.online(Collections.<DiscordSlashCommands.Player>emptyList()));
    }

    @Test
    public void whoFindsByAccountOrCharacterAndDescribes() {
        assertEquals("**Aragorn** — steve's character: Human, of Gondor, level 7.",
                DiscordSlashCommands.who(PLAYERS, "aragorn"));
        assertEquals("**Alex** is online, playing as themselves.",
                DiscordSlashCommands.who(PLAYERS, "ALEX"));
        assertEquals("Nobody online is called **Bob**.",
                DiscordSlashCommands.who(PLAYERS, "Bob"));
        assertEquals("Say whom: `/who name`.", DiscordSlashCommands.who(PLAYERS, " "));
        // Markdown in a name stays inert.
        assertEquals("Nobody online is called **\\*\\*Bob\\*\\***.",
                DiscordSlashCommands.who(PLAYERS, "**Bob**"));
    }

    @Test
    public void theServerLineNamesTheModPlayersAndUptime() {
        String status = DiscordSlashCommands.serverStatus(3, 20, 1000L, 1000L + 2L * 3600000L
                + 15L * 60000L);
        assertTrue(status, status.endsWith(" • 3/20 players • up 2h 15m"));
        assertTrue(status.startsWith("Ainulindalë: Lost Tales"));
        assertEquals("2d 3h", DiscordSlashCommands.uptime(2L * 86400000L + 3L * 3600000L));
        assertEquals("15m", DiscordSlashCommands.uptime(15L * 60000L));
        assertEquals("40s", DiscordSlashCommands.uptime(40000L));
        assertEquals("Half troll", DiscordSlashCommands.raceName("losttales:half_troll"));
    }

    @Test
    public void anInteractionIsReadAndAnythingElseIsNot() {
        JsonObject interaction = new JsonParser().parse("{\"id\":\"i1\",\"token\":\"tok\","
                + "\"type\":2,\"application_id\":\"app\",\"channel_id\":\"c\","
                + "\"guild_id\":\"g\",\"member\":{\"user\":{\"username\":\"nils\"}},"
                + "\"data\":{\"name\":\"who\",\"options\":[{\"name\":\"name\","
                + "\"value\":\"Aragorn\"}]}}").getAsJsonObject();
        DiscordJson.Interaction parsed = DiscordJson.parseInteraction(interaction);
        assertNotNull(parsed);
        assertEquals("i1", parsed.id);
        assertEquals("tok", parsed.token);
        assertEquals("app", parsed.applicationId);
        assertEquals("who", parsed.name);
        assertEquals("Aragorn", parsed.options.get("name"));
        assertEquals("nils", parsed.userName);
        assertEquals("g", parsed.guildId);
        // A ping (type 1) or a component is not a command.
        JsonObject ping = new JsonParser().parse("{\"id\":\"i2\",\"token\":\"t\",\"type\":1}")
                .getAsJsonObject();
        assertNull(DiscordJson.parseInteraction(ping));
        assertEquals("{\"type\":5,\"data\":{\"flags\":64}}", DiscordJson.deferredReplyBody(true));
        assertEquals("{\"content\":\"hi\",\"allowed_mentions\":{\"parse\":[]}}",
                DiscordJson.followUpBody("hi"));
        assertEquals("wss://gateway.discord.gg",
                DiscordJson.parseGatewayUrl("{\"url\":\"wss://gateway.discord.gg\"}"));
        DiscordJson.Message message = DiscordJson.parseMessage(new JsonParser().parse(
                "{\"id\":\"m\",\"channel_id\":\"c9\",\"content\":\"x\","
                        + "\"author\":{\"id\":\"u\",\"username\":\"n\"}}").getAsJsonObject());
        assertNotNull(message);
        assertEquals("c9", message.channelId);
    }
}
