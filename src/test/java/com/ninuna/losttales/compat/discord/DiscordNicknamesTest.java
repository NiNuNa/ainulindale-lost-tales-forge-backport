package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A Discord member goes by their nickname in each server, everywhere the
 * game names them, and a custom status becomes their status line.
 */
public final class DiscordNicknamesTest {

    private static JsonObject json(String text) {
        return new JsonParser().parse(text).getAsJsonObject();
    }

    /** A nickname first, then the global display name, then the username. */
    @Test
    public void aMemberGoesByTheirNickname() {
        JsonObject user = json("{\"id\":\"1\",\"username\":\"nils\",\"global_name\":\"Nils\"}");
        assertEquals("Ninuna", DiscordJson.memberName(json("{\"nick\":\"Ninuna\"}"), user));
        assertEquals("Nils", DiscordJson.memberName(json("{\"nick\":null}"), user));
        assertEquals("Nils", DiscordJson.memberName(null, user));
        assertEquals("nils", DiscordJson.memberName(null, json("{\"username\":\"nils\"}")));
        assertEquals("", DiscordJson.memberName(null, null));
    }

    /** A message from the gateway names its author and mentions by their nicknames. */
    @Test
    public void aGatewayMessageCarriesTheNicknames() {
        DiscordJson.Message message = DiscordJson.parseMessage(json("{\"id\":\"9\","
                + "\"channel_id\":\"200\",\"content\":\"hi <@2>\","
                + "\"author\":{\"id\":\"1\",\"username\":\"nils\",\"global_name\":\"Nils\"},"
                + "\"member\":{\"nick\":\"Ninuna\"},"
                + "\"mentions\":[{\"id\":\"2\",\"username\":\"sam\",\"member\":{\"nick\":\"Samwise\"}}]}"));
        assertEquals("Ninuna", message.authorName);
        assertEquals("Samwise", message.mentionNames.get("2"));
    }

    /**
     * The directory remembers the nicknames the gateway tells, so a
     * message read through the REST API, which comes without them, is
     * named the same; a nickname taken away is forgotten.
     */
    @Test
    public void theDirectoryRemembersNicknamesForMessagesReadWithoutThem() {
        DiscordGuildDirectory directory = new DiscordGuildDirectory();
        directory.onEvent("GUILD_CREATE", json("{\"id\":\"100\",\"name\":\"The Shire\","
                + "\"channels\":[{\"id\":\"200\",\"name\":\"general\"}],"
                + "\"members\":[{\"user\":{\"id\":\"2\"},\"nick\":\"Samwise\"}]}"));
        directory.onEvent("MESSAGE_CREATE", json("{\"id\":\"9\",\"channel_id\":\"200\","
                + "\"guild_id\":\"100\",\"author\":{\"id\":\"1\"},\"member\":{\"nick\":\"Ninuna\"}}"));
        assertEquals("Ninuna", directory.nameIn("200", "1", "Nils"));
        assertEquals("Samwise", directory.nameIn("200", "2", "sam"));
        assertEquals("Frodo", directory.nameIn("200", "3", "Frodo"));
        assertEquals("Nils", directory.nameIn("999", "1", "Nils"));
        directory.onEvent("MESSAGE_CREATE", json("{\"id\":\"10\",\"channel_id\":\"200\","
                + "\"guild_id\":\"100\",\"author\":{\"id\":\"1\"},\"member\":{\"nick\":null}}"));
        assertEquals("Nils", directory.nameIn("200", "1", "Nils"));
    }

    /** The nicknames kept are bounded; the least used goes first. */
    @Test
    public void theNicknamesAreBounded() {
        DiscordGuildDirectory directory = new DiscordGuildDirectory();
        directory.noteChannel("200", "100");
        for (int index = 1; index <= DiscordGuildDirectory.MAX_NICKNAMES + 1; index++) {
            directory.noteNickname("100", Integer.toString(index), "Nick" + index);
        }
        assertEquals("first", directory.nameIn("200", "1", "first"));
        assertEquals("Nick2", directory.nameIn("200", "2", "second"));
    }

    /** A custom status becomes a status line: the chat's emoji as its shortcode, the words cleaned. */
    @Test
    public void aCustomStatusBecomesAStatusLine() {
        assertEquals(":cheese: Out on the hills",
                DiscordMessageSanitizer.inboundStatusLine("  Out on the\nhills ",
                        "🧀", false));
        assertEquals(":bee: busy", DiscordMessageSanitizer.inboundStatusLine("busy", "Bee", true));
        // An emoji the chat lacks keeps its name, a server's own and a Unicode one alike.
        assertEquals(":pepe: busy", DiscordMessageSanitizer.inboundStatusLine("busy", "pepe", true));
        assertEquals(":melting_face: busy", DiscordMessageSanitizer.inboundStatusLine("busy",
                "🫠", false));
        assertEquals(":unicorn: off to :unicorn: land",
                DiscordMessageSanitizer.inboundStatusLine("off to 🦄 land", "🦄", false));
        // A name Discord would never send is left out.
        assertEquals("busy", DiscordMessageSanitizer.inboundStatusLine("busy", "a b", true));
        assertEquals("", DiscordMessageSanitizer.inboundStatusLine(null, null, false));
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < 60; index++) {
            long_.append('a');
        }
        assertEquals(48, DiscordMessageSanitizer.inboundStatusLine(long_.toString(), "",
                false).length());
    }

    /** The member cache reads the custom status among a presence's activities. */
    @Test
    public void theMemberCacheKeepsTheCustomStatus() {
        DiscordMemberDirectory members = new DiscordMemberDirectory();
        members.watch(Collections.singletonList("200"));
        members.onEvent("GUILD_CREATE", json("{\"id\":\"100\",\"name\":\"The Shire\","
                + "\"roles\":[{\"id\":\"100\",\"permissions\":\"1024\"}],"
                + "\"channels\":[{\"id\":\"200\",\"permission_overwrites\":[]}],"
                + "\"members\":[{\"user\":{\"id\":\"1\",\"username\":\"nils\"},"
                + "\"nick\":\"Ninuna\",\"roles\":[]}],"
                + "\"presences\":[{\"user\":{\"id\":\"1\"},\"status\":\"online\","
                + "\"activities\":[{\"type\":4,\"name\":\"Custom Status\","
                + "\"state\":\"Riding to Bree\"}]}]}"), 0L);
        assertEquals("Ninuna", members.seeing(Collections.singletonList("200")).get(0).name);
        assertEquals("Riding to Bree", members.lineOf("1"));
        members.takeListsChanged();
        members.takeChangedStatuses();
        // A new line reaches the players; the member list stays as it is.
        members.onEvent("PRESENCE_UPDATE", json("{\"guild_id\":\"100\",\"user\":{\"id\":\"1\"},"
                + "\"status\":\"online\",\"activities\":[{\"type\":4,\"state\":\"In Bree\"}]}"),
                0L);
        assertEquals("In Bree", members.lineOf("1"));
        assertEquals(Collections.singleton("1"), members.takeChangedStatuses());
        assertFalse(members.takeListsChanged());
        // Offline keeps no line.
        members.onEvent("PRESENCE_UPDATE", json("{\"guild_id\":\"100\",\"user\":{\"id\":\"1\"},"
                + "\"status\":\"offline\",\"activities\":[{\"type\":4,\"state\":\"In Bree\"}]}"),
                0L);
        assertEquals("", members.lineOf("1"));
        assertTrue(members.takeListsChanged());
    }
}
