package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.chat.server.ChatServerStatus;
import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesWords;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The slash commands' definitions and wording, and the interaction they arrive as. */
public final class DiscordSlashCommandsTest {

    private static final EnglishWords ENGLISH = EnglishWords.INSTANCE;

    private static final List<DiscordSlashCommands.Player> PLAYERS = Arrays.asList(
            new DiscordSlashCommands.Player("steve", "Aragorn", "Human", "Gondor"),
            new DiscordSlashCommands.Player("Alex", "", "", ""));

    @Test
    public void theDefinitionsNameFiveCommandsAndWhatEachTakes() {
        JsonArray commands = new JsonParser().parse(
                DiscordSlashCommands.definitionsBody(ENGLISH)).getAsJsonArray();
        assertEquals(5, commands.size());
        assertEquals("Who is playing on the server right now",
                commands.get(0).getAsJsonObject().get("description").getAsString());
        JsonObject who = commands.get(1).getAsJsonObject();
        assertEquals("who", who.get("name").getAsString());
        JsonObject option = who.getAsJsonArray("options").get(0).getAsJsonObject();
        assertEquals("name", option.get("name").getAsString());
        assertEquals("An account or character name",
                option.get("description").getAsString());
        assertEquals(3, option.get("type").getAsInt());
        assertTrue(option.get("required").getAsBoolean());
        // Linking is offered to those who may manage the channel's webhooks.
        JsonObject link = commands.get(3).getAsJsonObject();
        assertEquals("link", link.get("name").getAsString());
        assertEquals("code", link.getAsJsonArray("options").get(0).getAsJsonObject()
                .get("name").getAsString());
        assertEquals(String.valueOf(1L << 29),
                link.get("default_member_permissions").getAsString());
        JsonObject unlink = commands.get(4).getAsJsonObject();
        assertEquals("unlink", unlink.get("name").getAsString());
        assertEquals(String.valueOf(1L << 29),
                unlink.get("default_member_permissions").getAsString());
    }

    @Test
    public void onlineListsEveryoneAlphabeticallyWithTheirCharacter() {
        assertEquals("**2 online:** Alex, steve (as Aragorn)",
                DiscordSlashCommands.online(ENGLISH, PLAYERS));
        assertEquals("**Nobody is online.**",
                DiscordSlashCommands.online(ENGLISH,
                        Collections.<DiscordSlashCommands.Player>emptyList()));
    }

    @Test
    public void whoFindsByAccountOrCharacterAndDescribes() {
        assertEquals("**Aragorn** — steve's character: Human, of Gondor.",
                DiscordSlashCommands.who(ENGLISH, PLAYERS, "aragorn"));
        assertEquals("**Alex** is online, playing as themselves.",
                DiscordSlashCommands.who(ENGLISH, PLAYERS, "ALEX"));
        assertEquals("Nobody online is called **Bob**.",
                DiscordSlashCommands.who(ENGLISH, PLAYERS, "Bob"));
        assertEquals("Say whom: `/who name`.",
                DiscordSlashCommands.who(ENGLISH, PLAYERS, " "));
        // Markdown in a name stays inert.
        assertEquals("Nobody online is called **\\*\\*Bob\\*\\***.",
                DiscordSlashCommands.who(ENGLISH, PLAYERS, "**Bob**"));
        // A character with no race or faction known is the account's alone.
        assertEquals("**Boromir** — bob's character.",
                DiscordSlashCommands.who(ENGLISH, Collections.singletonList(
                        new DiscordSlashCommands.Player("bob", "Boromir", "", "")),
                        "boromir"));
    }

    /** What /link and /unlink answer, one lang line a sentence. */
    @Test
    public void linkingIsAnsweredInTheServersWords() {
        assertEquals("This channel is not linked to the game.",
                DiscordSlashCommands.notLinked(ENGLISH));
        assertEquals("Use this in a channel of your Discord server.",
                DiscordSlashCommands.linkNeedsServer(ENGLISH));
        assertEquals("You need the Manage Webhooks permission in this channel.",
                DiscordSlashCommands.linkNeedsPermission(ENGLISH));
        assertEquals("That code is unknown or has run out. Ask for a new one in"
                        + " the game with `/losttales discord link <channel>`.",
                DiscordSlashCommands.linkUnknownCode(ENGLISH));
        assertEquals("I need the Manage Webhooks permission in this channel to link it.",
                DiscordSlashCommands.linkBotNeedsPermission(ENGLISH));
        assertEquals("The link could not be saved on the game server. Ask for a new code.",
                DiscordSlashCommands.linkNotSaved(ENGLISH));
        assertEquals("This channel is already linked to **OOC Chat**. A Discord"
                        + " channel holds one game channel: use `/unlink` first.",
                DiscordSlashCommands.linkTaken(ENGLISH, "OOC Chat"));
        assertEquals("This channel is already linked to **OOC Chat**.",
                DiscordSlashCommands.linkAlready(ENGLISH, "OOC Chat"));
        assertEquals("Discord did not make the webhook (HTTP 500). Ask for a new code.",
                DiscordSlashCommands.linkFailed(ENGLISH, 500));
        assertEquals("Discord could not be reached. Ask for a new code.",
                DiscordSlashCommands.linkFailed(ENGLISH, 0));
        assertEquals("Linked to **Global Chat**. Messages cross both ways.",
                DiscordSlashCommands.linked(ENGLISH, "Global Chat",
                        DiscordBridgeDirection.BIDIRECTIONAL, false));
        assertEquals("Linked to **Operator Chat**. Lines from the game come here."
                        + " Only some players read **Operator Chat** in the game;"
                        + " here, everyone who can see this channel reads it.",
                DiscordSlashCommands.linked(ENGLISH, "Operator Chat",
                        DiscordBridgeDirection.GAME_TO_DISCORD, true));
        assertEquals("Linked to **Global Chat**. Messages here go to the game.",
                DiscordSlashCommands.linked(ENGLISH, "Global Chat",
                        DiscordBridgeDirection.DISCORD_TO_GAME, false));
        // A channel's name is escaped where it is named.
        assertEquals("Unlinked from **x\\_y Chat**.",
                DiscordSlashCommands.unlinked(ENGLISH, "x_y Chat"));
    }

    /**
     * Discord takes no answer past two thousand characters, and an option
     * may be longer than that before its markdown is escaped.
     */
    @Test
    public void everyAnswerFitsDiscordsTwoThousandCharacters() {
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 3000; index++) {
            name.append('*');
        }
        String answer = DiscordSlashCommands.who(ENGLISH, PLAYERS, name.toString());
        assertTrue(answer.length() + " characters", answer.length() <= 2000);
        assertTrue(answer.endsWith("..."));
        StringBuilder emoji = new StringBuilder();
        for (int index = 0; index < 1500; index++) {
            emoji.append("😄");
        }
        String cut = DiscordSlashCommands.bound(emoji.toString());
        assertTrue(cut.length() <= 2000);
        assertFalse("never half an emoji", Character.isHighSurrogate(
                cut.charAt(cut.length() - 4)));
        assertEquals("short", DiscordSlashCommands.bound("short"));
        assertEquals("", DiscordSlashCommands.bound(null));
    }

    @Test
    public void theServerLineNamesTheModPlayersAndUptime() {
        String status = DiscordSlashCommands.serverStatus(ChatServerStatus.partsOf(
                ENGLISH, 3, 20, "", 20, 2L * 3600000L + 15L * 60000L));
        assertTrue(status, status.endsWith(" • 3/20 players • 20 TPS • up 2h 15m"));
        assertTrue(status.startsWith("Ainulindalë: Lost Tales"));
    }

    /** A race reads as the game names it, and as its id where no lang line names it. */
    @Test
    public void aRaceReadsAsTheGameNamesIt() {
        assertEquals("Half-troll",
                DiscordSlashCommands.raceName(ENGLISH, "losttales:half_troll"));
        // The game's lang lookup answers a key it lacks with the key itself.
        LostTalesWords lacking = new LostTalesWords() {
            @Override
            public String format(String key, Object... arguments) {
                return key;
            }
        };
        assertEquals("Half troll",
                DiscordSlashCommands.raceName(lacking, "losttales:half_troll"));
        assertEquals("", DiscordSlashCommands.raceName(ENGLISH, ""));
        assertEquals("", DiscordSlashCommands.raceName(ENGLISH, null));
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
