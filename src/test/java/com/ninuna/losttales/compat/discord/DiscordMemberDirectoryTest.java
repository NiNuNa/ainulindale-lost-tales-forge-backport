package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.chat.ChatPresence;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Who the bridge lists for a linked channel, from the gateway's events alone. */
public class DiscordMemberDirectoryTest {
    private static final String GUILD = "100";
    private static final String LINKED = "200";
    private static final String OTHER = "201";
    private static final String MODS = "300";
    private static final long VIEW = DiscordMemberDirectory.VIEW_CHANNEL;

    private DiscordMemberDirectory directory;

    @Before
    public void watchTheLinkedChannel() {
        this.directory = new DiscordMemberDirectory();
        this.directory.watch(Collections.singletonList(LINKED));
    }

    /**
     * A server arrives with its roles, channels, members and statuses:
     * whoever may view the linked channel is listed, the bot never, and a
     * member it names no status for is offline.
     */
    @Test
    public void aServerArrivesWithWhoCanSeeTheLinkedChannel() {
        assertFalse(this.directory.onEvent("GUILD_CREATE", guild(
                "[" + member("1", "Nils", "") + "," + member("2", "Sam", "\"" + MODS + "\"")
                        + "," + bot("3") + "]",
                "[" + presence("1", "online") + "," + presence("3", "online") + "]",
                // Only the Moderators may see the linked channel.
                "[{\"id\":\"" + GUILD + "\",\"type\":0,\"allow\":\"0\",\"deny\":\"" + VIEW
                        + "\"},{\"id\":\"" + MODS + "\",\"type\":0,\"allow\":\"" + VIEW
                        + "\",\"deny\":\"0\"}]", 3, false), 0L));
        List<DiscordMemberDirectory.Seen> seen = this.directory.seeing(
                Collections.singletonList(LINKED));
        assertEquals(1, seen.size());
        assertEquals("2", seen.get(0).userId);
        assertEquals("Sam", seen.get(0).name);
        assertEquals("The Shire", seen.get(0).guildName);
        assertNull(seen.get(0).status);
        assertEquals(ChatPresence.ONLINE, this.directory.statusOf("1"));
        assertTrue(this.directory.takeListsChanged());
        assertFalse(this.directory.takeListsChanged());
    }

    /** A server with no linked channel is not followed at all. */
    @Test
    public void aServerWithoutALinkedChannelIsIgnored() {
        DiscordMemberDirectory elsewhere = new DiscordMemberDirectory();
        elsewhere.watch(Collections.singletonList(OTHER + "9"));
        elsewhere.onEvent("GUILD_CREATE", guild("[" + member("1", "Nils", "") + "]",
                "[" + presence("1", "online") + "]", "[]", 1, false), 0L);
        assertNull(elsewhere.statusOf("1"));
        assertTrue(elsewhere.usersSeeing(Collections.singletonList(OTHER + "9")).isEmpty());
    }

    /** A large server's members are asked for, and arrive in chunks with their statuses. */
    @Test
    public void aLargeServersMembersAreAskedForAndArriveInChunks() {
        assertTrue(this.directory.onEvent("GUILD_CREATE", guild(
                "[" + member("1", "Nils", "") + "]", "[" + presence("1", "online") + "]",
                "[]", 400, true), 0L));
        this.directory.onEvent("GUILD_MEMBERS_CHUNK", json("{\"guild_id\":\"" + GUILD
                + "\",\"members\":[" + member("2", "Sam", "") + "],\"presences\":["
                + presence("2", "dnd") + "]}"), 0L);
        assertEquals(ChatPresence.DO_NOT_DISTURB, this.directory.statusOf("2"));
        assertEquals(2, this.directory.seeing(Collections.singletonList(LINKED)).size());
    }

    /** Statuses, joins and leaves follow as events of their own. */
    @Test
    public void statusesJoinsAndLeavesFollow() {
        this.directory.onEvent("GUILD_CREATE", guild("[" + member("1", "Nils", "") + "]",
                "[]", "[]", 1, false), 0L);
        this.directory.takeChangedStatuses();
        this.directory.onEvent("PRESENCE_UPDATE", json("{\"guild_id\":\"" + GUILD
                + "\",\"user\":{\"id\":\"1\"},\"status\":\"idle\"}"), 0L);
        assertEquals(ChatPresence.AWAY, this.directory.statusOf("1"));
        assertEquals(Collections.singleton("1"), this.directory.takeChangedStatuses());
        // Somebody the server does not list has no status kept.
        this.directory.onEvent("PRESENCE_UPDATE", json("{\"guild_id\":\"" + GUILD
                + "\",\"user\":{\"id\":\"9\"},\"status\":\"online\"}"), 0L);
        assertNull(this.directory.statusOf("9"));
        this.directory.onEvent("GUILD_MEMBER_ADD", json("{\"guild_id\":\"" + GUILD
                + "\"," + member("4", "Frodo", "").substring(1)), 0L);
        assertEquals(2, this.directory.seeing(Collections.singletonList(LINKED)).size());
        this.directory.onEvent("GUILD_MEMBER_REMOVE", json("{\"guild_id\":\"" + GUILD
                + "\",\"user\":{\"id\":\"1\"}}"), 0L);
        assertNull(this.directory.statusOf("1"));
        assertEquals(1, this.directory.seeing(Collections.singletonList(LINKED)).size());
    }

    /** A channel's overwrites changing changes who can see it. */
    @Test
    public void anOverwriteChangeChangesWhoCanSee() {
        this.directory.onEvent("GUILD_CREATE", guild("[" + member("1", "Nils", "") + "]",
                "[]", "[]", 1, false), 0L);
        assertEquals(1, this.directory.usersSeeing(Collections.singletonList(LINKED)).size());
        this.directory.takeSeeingChanged();
        this.directory.onEvent("CHANNEL_UPDATE", json("{\"id\":\"" + LINKED
                + "\",\"guild_id\":\"" + GUILD + "\",\"permission_overwrites\":[{\"id\":\"1\","
                + "\"type\":1,\"allow\":\"0\",\"deny\":\"" + VIEW + "\"}]}"), 0L);
        assertTrue(this.directory.takeSeeingChanged());
        assertTrue(this.directory.usersSeeing(Collections.singletonList(LINKED)).isEmpty());
    }

    /** Discord's word to wait is kept, and the ask is due once the wait is over. */
    @Test
    public void aRefusedAskIsMadeAgainOnceTheWaitIsOver() {
        this.directory.onEvent("GUILD_CREATE", guild("[]", "[]", "[]", 900, true), 0L);
        this.directory.onEvent("RATE_LIMITED", json("{\"opcode\":8,\"retry_after\":12.5,"
                + "\"meta\":{\"guild_id\":\"" + GUILD + "\"}}"), 1000L);
        assertTrue(this.directory.dueRequests(13000L).isEmpty());
        assertEquals(Collections.singletonList(GUILD), this.directory.dueRequests(13500L));
        assertTrue(this.directory.dueRequests(20000L).isEmpty());
        // A wait past the bound is cut to it.
        this.directory.onEvent("RATE_LIMITED", json("{\"opcode\":8,\"retry_after\":1e9,"
                + "\"meta\":{\"guild_id\":\"" + GUILD + "\"}}"), 0L);
        assertEquals(Collections.singletonList(GUILD), this.directory.dueRequests(
                DiscordMemberDirectory.MAX_RETRY_MILLIS));
    }

    /** Discord's own permission rules, in its order. */
    @Test
    public void whoCanSeeFollowsDiscordsRules() {
        Map<String, Long> roles = new HashMap<String, Long>();
        roles.put(GUILD, Long.valueOf(VIEW));
        roles.put(MODS, Long.valueOf(0L));
        roles.put("admin", Long.valueOf(DiscordMemberDirectory.ADMINISTRATOR));
        DiscordMemberDirectory.Rules hidden = rules(
                new DiscordMemberDirectory.Overwrite(0L, VIEW), MODS, null);
        List<String> none = Collections.emptyList();
        List<String> mods = Collections.singletonList(MODS);
        // @everyone may view the server's channels, but not this one.
        assertTrue(DiscordMemberDirectory.canSee(GUILD, "", roles, rules(null, null, null),
                "1", none));
        assertFalse(DiscordMemberDirectory.canSee(GUILD, "", roles, hidden, "1", none));
        // A role's allow comes after @everyone's deny.
        assertTrue(DiscordMemberDirectory.canSee(GUILD, "", roles, hidden, "1", mods));
        // A member's own deny comes last of all.
        DiscordMemberDirectory.Rules banned = rules(
                new DiscordMemberDirectory.Overwrite(0L, VIEW), MODS, "1");
        assertFalse(DiscordMemberDirectory.canSee(GUILD, "", roles, banned, "1", mods));
        // The owner and an administrator see everything.
        assertTrue(DiscordMemberDirectory.canSee(GUILD, "1", roles, banned, "1", none));
        assertTrue(DiscordMemberDirectory.canSee(GUILD, "", roles, banned, "1",
                Arrays.asList(MODS, "admin")));
    }

    /** A permission that is no number allows nothing and denies everything. */
    @Test
    public void anUnreadablePermissionCountsAgainstTheMember() {
        assertEquals(0L, DiscordMemberDirectory.bits("lots", 0L));
        assertEquals(-1L, DiscordMemberDirectory.bits("", -1L));
        assertEquals(-1L, DiscordMemberDirectory.bits("123456789012345678901", -1L));
        assertEquals(VIEW, DiscordMemberDirectory.bits(Long.toString(VIEW), 0L));
        this.directory.onEvent("GUILD_CREATE", guild("[" + member("1", "Nils", "") + "]",
                "[]", "[{\"id\":\"" + GUILD + "\",\"type\":0,\"allow\":\"0\","
                        + "\"deny\":\"not a number\"}]", 1, false), 0L);
        assertTrue(this.directory.usersSeeing(Collections.singletonList(LINKED)).isEmpty());
    }

    /** Discord's statuses as the chat says them; invisible reads as offline. */
    @Test
    public void discordsStatusesReadAsTheChats() {
        assertEquals(ChatPresence.ONLINE, DiscordMemberDirectory.presenceOf("online"));
        assertEquals(ChatPresence.AWAY, DiscordMemberDirectory.presenceOf("idle"));
        assertEquals(ChatPresence.DO_NOT_DISTURB, DiscordMemberDirectory.presenceOf("dnd"));
        assertNull(DiscordMemberDirectory.presenceOf("offline"));
        assertNull(DiscordMemberDirectory.presenceOf("invisible"));
        assertNull(DiscordMemberDirectory.presenceOf(null));
    }

    /** A server's members are bounded; the rest are left out. */
    @Test
    public void aServersMembersAreBounded() {
        StringBuilder members = new StringBuilder("[");
        for (int index = 1; index <= DiscordMemberDirectory.MAX_MEMBERS_PER_GUILD + 10; index++) {
            if (index > 1) {
                members.append(',');
            }
            members.append(member(Integer.toString(index), "Member", ""));
        }
        members.append(']');
        this.directory.onEvent("GUILD_CREATE", guild(members.toString(), "[]", "[]",
                DiscordMemberDirectory.MAX_MEMBERS_PER_GUILD + 10, false), 0L);
        assertEquals(DiscordMemberDirectory.MAX_MEMBERS_PER_GUILD,
                this.directory.usersSeeing(Collections.singletonList(LINKED)).size());
        // The server is reported once.
        assertEquals(Collections.singletonList("The Shire"), this.directory.takeOverflowed());
        assertTrue(this.directory.takeOverflowed().isEmpty());
    }

    private static DiscordMemberDirectory.Rules rules(
            DiscordMemberDirectory.Overwrite everyone, String allowedRole,
            String deniedMember) {
        Map<String, DiscordMemberDirectory.Overwrite> roles =
                new HashMap<String, DiscordMemberDirectory.Overwrite>();
        if (allowedRole != null) {
            roles.put(allowedRole, new DiscordMemberDirectory.Overwrite(VIEW, 0L));
        }
        Map<String, DiscordMemberDirectory.Overwrite> members =
                new HashMap<String, DiscordMemberDirectory.Overwrite>();
        if (deniedMember != null) {
            members.put(deniedMember, new DiscordMemberDirectory.Overwrite(0L, VIEW));
        }
        return new DiscordMemberDirectory.Rules(everyone, roles, members);
    }

    /** A server holding the linked channel, whose @everyone may view channels. */
    private static JsonObject guild(String members, String presences, String overwrites,
                                    int memberCount, boolean large) {
        return json("{\"id\":\"" + GUILD + "\",\"name\":\"The Shire\",\"owner_id\":\"999\","
                + "\"large\":" + large + ",\"member_count\":" + memberCount + ","
                + "\"roles\":[{\"id\":\"" + GUILD + "\",\"permissions\":\"" + VIEW + "\"},"
                + "{\"id\":\"" + MODS + "\",\"permissions\":\"0\"}],"
                + "\"channels\":[{\"id\":\"" + LINKED + "\",\"permission_overwrites\":"
                + overwrites + "},{\"id\":\"" + OTHER + "\",\"permission_overwrites\":[]}],"
                + "\"members\":" + members + ",\"presences\":" + presences + "}");
    }

    private static String member(String id, String name, String roles) {
        return "{\"user\":{\"id\":\"" + id + "\",\"username\":\"" + name.toLowerCase()
                + "\",\"global_name\":\"" + name + "\"},\"roles\":[" + roles + "]}";
    }

    private static String bot(String id) {
        return "{\"user\":{\"id\":\"" + id + "\",\"username\":\"bot\",\"bot\":true},"
                + "\"roles\":[]}";
    }

    private static String presence(String id, String status) {
        return "{\"user\":{\"id\":\"" + id + "\"},\"status\":\"" + status + "\"}";
    }

    private static JsonObject json(String text) {
        return new JsonParser().parse(text).getAsJsonObject();
    }
}
