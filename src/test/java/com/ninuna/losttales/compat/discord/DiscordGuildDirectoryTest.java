package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The names a member's mention of a role or a channel reads by: each
 * server's own, followed through renames and removals, never another
 * server's, and never more than the directory holds.
 */
public final class DiscordGuildDirectoryTest {

    @Test
    public void aServersRolesAndChannelsAreNamedForItsOwnMessages() {
        DiscordGuildDirectory directory = new DiscordGuildDirectory();
        directory.onEvent("GUILD_CREATE", guild("1", role("11", "Moderators"),
                channel("21", "general")));
        directory.onEvent("GUILD_CREATE", guild("2", role("12", "Elders"),
                channel("22", "council")));
        DiscordMessageSanitizer.Places shire = directory.placesIn("1");
        assertEquals("Moderators", shire.roleName("11"));
        assertEquals("general", shire.channelName("21"));
        // Another server's role or channel names nothing here.
        assertEquals("", shire.roleName("12"));
        assertEquals("", shire.channelName("22"));
        assertEquals("", shire.roleName("99"));
        assertEquals("", directory.placesIn("").roleName("11"));

        JsonObject renamed = new JsonObject();
        renamed.addProperty("guild_id", "1");
        renamed.add("role", role("11", "Wardens"));
        directory.onEvent("GUILD_ROLE_UPDATE", renamed);
        assertEquals("Wardens", shire.roleName("11"));

        // A removal names the server it is in; another server's word is ignored.
        JsonObject elsewhere = new JsonObject();
        elsewhere.addProperty("guild_id", "2");
        elsewhere.addProperty("role_id", "11");
        directory.onEvent("GUILD_ROLE_DELETE", elsewhere);
        assertEquals("Wardens", shire.roleName("11"));
        JsonObject removed = new JsonObject();
        removed.addProperty("guild_id", "1");
        removed.addProperty("role_id", "11");
        directory.onEvent("GUILD_ROLE_DELETE", removed);
        assertEquals("", shire.roleName("11"));

        // A server arriving again names its whole set; one it left is forgotten.
        directory.onEvent("GUILD_CREATE", guild("2", role("13", "Scribes"),
                channel("22", "council")));
        assertEquals("", directory.placesIn("2").roleName("12"));
        assertEquals("Scribes", directory.placesIn("2").roleName("13"));
        JsonObject left = new JsonObject();
        left.addProperty("id", "2");
        directory.onEvent("GUILD_DELETE", left);
        assertEquals("", directory.placesIn("2").roleName("13"));
        assertEquals("", directory.placesIn("2").channelName("22"));
    }

    @Test
    public void rolesAreBounded() {
        DiscordGuildDirectory directory = new DiscordGuildDirectory();
        JsonArray roles = new JsonArray();
        for (int index = 0; index < DiscordGuildDirectory.MAX_ROLES + 10; index++) {
            roles.add(role(Integer.toString(100000 + index), "Role " + index));
        }
        JsonObject guild = new JsonObject();
        guild.addProperty("id", "1");
        guild.addProperty("name", "The Shire");
        guild.add("roles", roles);
        directory.onEvent("GUILD_CREATE", guild);
        DiscordMessageSanitizer.Places shire = directory.placesIn("1");
        assertEquals("Role 0", shire.roleName("100000"));
        assertEquals("", shire.roleName(Integer.toString(
                100000 + DiscordGuildDirectory.MAX_ROLES)));
    }

    private static JsonObject guild(String id, JsonObject role, JsonObject channel) {
        JsonObject guild = new JsonObject();
        guild.addProperty("id", id);
        guild.addProperty("name", "Server " + id);
        JsonArray roles = new JsonArray();
        roles.add(role);
        guild.add("roles", roles);
        JsonArray channels = new JsonArray();
        channels.add(channel);
        guild.add("channels", channels);
        return guild;
    }

    private static JsonObject role(String id, String name) {
        JsonObject role = new JsonObject();
        role.addProperty("id", id);
        role.addProperty("name", name);
        role.addProperty("permissions", "0");
        return role;
    }

    private static JsonObject channel(String id, String name) {
        JsonObject channel = new JsonObject();
        channel.addProperty("id", id);
        channel.addProperty("name", name);
        return channel;
    }
}
