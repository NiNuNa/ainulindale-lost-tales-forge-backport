package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ninuna.losttales.chat.ChatPresence;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who is in the Discord servers that hold a linked channel, while the
 * server lists Discord members: each member's name (their nickname in
 * the server where they have one) and roles, what each role and each
 * linked channel's overwrites allow, which is what decides who can see a
 * channel, and each member's status with their custom status as its
 * status line. Everything comes from
 * the gateway's events. A server of up to
 * {@code DiscordGatewayProtocol.LARGE_THRESHOLD} members arrives whole
 * with its GUILD_CREATE; a larger one arrives with its online members,
 * and the bridge asks for the rest, which come in chunks. Every join,
 * leave, rename, role change and status change then arrives as its own
 * event.
 *
 * <p>Written on the gateway's thread and read on the server's, all under
 * one lock. Everything is bounded: {@link #MAX_GUILDS} servers,
 * {@link #MAX_MEMBERS_PER_GUILD} members in each, {@link #MAX_ROLES}
 * roles, and {@link #MAX_OVERWRITES} overwrites on a channel. Anything
 * that cannot be read counts against the member: a permission that is no
 * number allows nothing and denies everything, so nobody is listed in a
 * channel they may not see. Bots are never listed.</p>
 */
public final class DiscordMemberDirectory implements DiscordMemberStatuses.Source {
    static final int MAX_GUILDS = 8;
    static final int MAX_MEMBERS_PER_GUILD = 2500;
    /** Discord's own most per server. */
    static final int MAX_ROLES = 250;
    static final int MAX_OVERWRITES = 512;
    static final long VIEW_CHANNEL = 1L << 10;
    static final long ADMINISTRATOR = 1L << 3;
    /** The longest the bridge waits before asking for a server's members again. */
    static final long MAX_RETRY_MILLIS = 60000L;
    /** The activity type Discord gives a custom status. */
    static final int CUSTOM_STATUS = 4;

    /** A member who can see a linked channel, as a member list shows them. */
    public static final class Seen {
        public final String userId;
        public final String name;
        public final String guildId;
        public final String guildName;
        /** What they are doing; null while they are offline. */
        public final ChatPresence status;

        Seen(String userId, String name, String guildId, String guildName,
             ChatPresence status) {
            this.userId = userId;
            this.name = name;
            this.guildId = guildId;
            this.guildName = guildName;
            this.status = status;
        }
    }

    /** One member: the name the list shows and the roles that decide what they see. */
    private static final class Member {
        final String name;
        final List<String> roles;

        Member(String name, List<String> roles) {
            this.name = name;
            this.roles = roles;
        }
    }

    /** One overwrite of a channel: what it allows and what it denies. */
    static final class Overwrite {
        final long allow;
        final long deny;

        Overwrite(long allow, long deny) {
            this.allow = allow;
            this.deny = deny;
        }
    }

    /** A channel's overwrites, by who they are for. */
    static final class Rules {
        /** The {@code @everyone} overwrite, or null. */
        final Overwrite everyone;
        final Map<String, Overwrite> roles;
        final Map<String, Overwrite> members;

        Rules(Overwrite everyone, Map<String, Overwrite> roles,
              Map<String, Overwrite> members) {
            this.everyone = everyone;
            this.roles = roles;
            this.members = members;
        }
    }

    private static final class Guild {
        final String id;
        String name = "";
        String ownerId = "";
        final Map<String, Long> roles = new HashMap<String, Long>();
        /** The rules of each linked channel in the server; a channel not here is not known yet. */
        final Map<String, Rules> channels = new HashMap<String, Rules>();
        final Map<String, Member> members = new HashMap<String, Member>();
        /** Whether the members past the bound have been reported as left out. */
        boolean overflowed;
        /** When its members may be asked for again after Discord said to wait; 0 for no ask waiting. */
        long retryAt;
        /** Who can see each linked channel, worked out again after any change of roles, members or rules. */
        final Map<String, Set<String>> seeing = new HashMap<String, Set<String>>();

        Guild(String id) {
            this.id = id;
        }
    }

    /** The linked Discord channels, whose servers are followed. */
    private final Set<String> watched = new HashSet<String>();
    private final Map<String, Guild> guilds = new LinkedHashMap<String, Guild>();
    /** Each listed member's status; a member missing from here is offline. */
    private final Map<String, ChatPresence> statuses = new HashMap<String, ChatPresence>();
    /** Each online member's custom status as a status line; none for one without. */
    private final Map<String, String> lines = new HashMap<String, String>();
    private final Set<String> changedStatuses = new LinkedHashSet<String>();
    /** The servers found to have more members than are kept, waiting to be reported. */
    private final List<String> overflowed = new ArrayList<String>();
    private boolean listsChanged;
    private boolean seeingChanged;

    /** Follows the servers holding these Discord channels, forgetting all heard before. */
    synchronized void watch(Collection<String> channelIds) {
        clear();
        if (channelIds != null) {
            for (String id : channelIds) {
                if (DiscordChannelBindings.isSnowflake(id)) {
                    this.watched.add(id);
                }
            }
        }
    }

    synchronized void clear() {
        this.watched.clear();
        this.guilds.clear();
        this.statuses.clear();
        this.lines.clear();
        this.changedStatuses.clear();
        this.overflowed.clear();
        this.listsChanged = false;
        this.seeingChanged = false;
    }

    /**
     * A gateway event, taken for what it says of members, roles, the
     * linked channels' overwrites and statuses; any other is ignored.
     * Answers whether the members of the server a GUILD_CREATE is about
     * are to be asked for now, because it left some out.
     */
    synchronized boolean onEvent(String name, JsonObject data, long now) {
        if (name == null || data == null || this.watched.isEmpty()) {
            return false;
        }
        if ("GUILD_CREATE".equals(name)) {
            return onGuild(data, true);
        }
        if ("RATE_LIMITED".equals(name)) {
            onRateLimited(data, now);
            return false;
        }
        Guild guild = this.guilds.get(string(data, "guild_id"));
        if ("GUILD_UPDATE".equals(name)) {
            onGuild(data, false);
        } else if ("GUILD_DELETE".equals(name)) {
            // A server in an outage comes back; one the bot left does not.
            if (!bool(data, "unavailable")) {
                forgetGuild(string(data, "id"));
            }
        } else if (guild == null) {
            return false;
        } else if ("GUILD_ROLE_CREATE".equals(name) || "GUILD_ROLE_UPDATE".equals(name)) {
            JsonObject role = object(data, "role");
            if (role != null) {
                putRole(guild, role);
                changed(guild);
            }
        } else if ("GUILD_ROLE_DELETE".equals(name)) {
            if (guild.roles.remove(string(data, "role_id")) != null) {
                changed(guild);
            }
        } else if ("CHANNEL_CREATE".equals(name) || "CHANNEL_UPDATE".equals(name)) {
            if (putChannel(guild, data)) {
                changed(guild);
            }
        } else if ("CHANNEL_DELETE".equals(name)) {
            if (guild.channels.remove(string(data, "id")) != null) {
                changed(guild);
            }
        } else if ("GUILD_MEMBER_ADD".equals(name) || "GUILD_MEMBER_UPDATE".equals(name)) {
            putMember(guild, data);
        } else if ("GUILD_MEMBER_REMOVE".equals(name)) {
            removeMember(guild, string(object(data, "user"), "id"));
        } else if ("GUILD_MEMBERS_CHUNK".equals(name)) {
            for (JsonObject member : objects(data, "members")) {
                putMember(guild, member);
            }
            for (JsonObject presence : objects(data, "presences")) {
                putStatus(guild, presence);
            }
        } else if ("PRESENCE_UPDATE".equals(name)) {
            putStatus(guild, data);
        }
        return false;
    }

    /** The servers whose members Discord said to wait for and may be asked for again now. */
    synchronized List<String> dueRequests(long now) {
        List<String> due = new ArrayList<String>();
        for (Guild guild : this.guilds.values()) {
            if (guild.retryAt != 0L && now >= guild.retryAt) {
                guild.retryAt = 0L;
                due.add(guild.id);
            }
        }
        return due;
    }

    /** Whether anything a member list shows has changed since the last ask; clears it. */
    synchronized boolean takeListsChanged() {
        boolean was = this.listsChanged;
        this.listsChanged = false;
        return was;
    }

    /** Whether who can see a linked channel may have changed since the last ask; clears it. */
    synchronized boolean takeSeeingChanged() {
        boolean was = this.seeingChanged;
        this.seeingChanged = false;
        return was;
    }

    /**
     * The names of the servers found since the last ask to have more
     * members than {@link #MAX_MEMBERS_PER_GUILD}, each reported once;
     * clears them.
     */
    synchronized List<String> takeOverflowed() {
        List<String> taken = new ArrayList<String>(this.overflowed);
        this.overflowed.clear();
        return taken;
    }

    /** The members whose status or custom status changed since the last ask; clears them. */
    synchronized Set<String> takeChangedStatuses() {
        Set<String> taken = new LinkedHashSet<String>(this.changedStatuses);
        this.changedStatuses.clear();
        return taken;
    }

    /** What a member is doing; null for one offline or not listed. */
    @Override
    public synchronized ChatPresence statusOf(String userId) {
        return userId == null ? null : this.statuses.get(userId);
    }

    /** An online member's custom status as a status line; empty for none. */
    @Override
    public synchronized String lineOf(String userId) {
        String line = userId == null ? null : this.lines.get(userId);
        return line == null ? "" : line;
    }

    /**
     * The members who can see any of {@code channelIds}, each once, under
     * the server of the first such channel.
     */
    synchronized List<Seen> seeing(List<String> channelIds) {
        List<Seen> seen = new ArrayList<Seen>();
        Set<String> listed = new HashSet<String>();
        for (String channelId : channelIds) {
            Guild guild = guildOf(channelId);
            if (guild == null) {
                continue;
            }
            for (String userId : seeingChannel(guild, channelId)) {
                if (listed.add(userId)) {
                    seen.add(new Seen(userId, guild.members.get(userId).name, guild.id,
                            guild.name, this.statuses.get(userId)));
                }
            }
        }
        return seen;
    }

    /** Everyone who can see any of {@code channelIds}, by Discord id. */
    @Override
    public synchronized Set<String> usersSeeing(List<String> channelIds) {
        Set<String> users = new HashSet<String>();
        for (String channelId : channelIds) {
            Guild guild = guildOf(channelId);
            if (guild != null) {
                users.addAll(seeingChannel(guild, channelId));
            }
        }
        return users;
    }

    /**
     * Whether a member can see a channel as Discord decides it: the
     * server's owner and an administrator always can. For anyone else,
     * {@code @everyone}'s permissions and those of each of their roles
     * are taken together, and then the channel's overwrites are applied
     * in order: {@code @everyone}'s, their roles' together, and their
     * own, each one's deny before its allow.
     */
    static boolean canSee(String guildId, String ownerId, Map<String, Long> roles,
                          Rules rules, String userId, List<String> memberRoles) {
        if (userId.equals(ownerId)) {
            return true;
        }
        Long everyone = roles.get(guildId);
        long permissions = everyone == null ? 0L : everyone.longValue();
        for (String role : memberRoles) {
            Long granted = roles.get(role);
            if (granted != null) {
                permissions |= granted.longValue();
            }
        }
        if ((permissions & ADMINISTRATOR) != 0L) {
            return true;
        }
        if (rules.everyone != null) {
            permissions = (permissions & ~rules.everyone.deny) | rules.everyone.allow;
        }
        long allow = 0L;
        long deny = 0L;
        for (String role : memberRoles) {
            Overwrite overwrite = rules.roles.get(role);
            if (overwrite != null) {
                allow |= overwrite.allow;
                deny |= overwrite.deny;
            }
        }
        permissions = (permissions & ~deny) | allow;
        Overwrite own = rules.members.get(userId);
        if (own != null) {
            permissions = (permissions & ~own.deny) | own.allow;
        }
        return (permissions & VIEW_CHANNEL) != 0L;
    }

    /** Who can see one linked channel, worked out afresh after a change. */
    private static Set<String> seeingChannel(Guild guild, String channelId) {
        Set<String> known = guild.seeing.get(channelId);
        if (known != null) {
            return known;
        }
        Rules rules = guild.channels.get(channelId);
        Set<String> seeing = new LinkedHashSet<String>();
        for (Map.Entry<String, Member> entry : guild.members.entrySet()) {
            if (canSee(guild.id, guild.ownerId, guild.roles, rules, entry.getKey(),
                    entry.getValue().roles)) {
                seeing.add(entry.getKey());
            }
        }
        known = Collections.unmodifiableSet(seeing);
        guild.seeing.put(channelId, known);
        return known;
    }

    /** The followed server a linked channel is in, or null while it is not known. */
    private Guild guildOf(String channelId) {
        for (Guild guild : this.guilds.values()) {
            if (guild.channels.containsKey(channelId)) {
                return guild;
            }
        }
        return null;
    }

    /**
     * A server arriving (GUILD_CREATE) or changed (GUILD_UPDATE). One
     * arriving is followed when it holds a linked channel, and brings its
     * members and their statuses with it: whoever it leaves out of its
     * statuses is offline. Answers whether it arrived with members left
     * out, which then are to be asked for.
     */
    private boolean onGuild(JsonObject data, boolean created) {
        String guildId = string(data, "id");
        if (guildId.length() == 0 || bool(data, "unavailable")) {
            return false;
        }
        Guild guild = this.guilds.get(guildId);
        if (guild == null) {
            if (!created || this.guilds.size() >= MAX_GUILDS || !holdsWatched(data)) {
                return false;
            }
            guild = new Guild(guildId);
            this.guilds.put(guildId, guild);
        }
        if (data.has("name")) {
            guild.name = DiscordGuildDirectory.clean(string(data, "name"));
        }
        if (data.has("owner_id")) {
            guild.ownerId = string(data, "owner_id");
        }
        if (data.has("roles")) {
            guild.roles.clear();
            for (JsonObject role : objects(data, "roles")) {
                putRole(guild, role);
            }
        }
        for (JsonObject channel : objects(data, "channels")) {
            putChannel(guild, channel);
        }
        boolean partial = false;
        if (created) {
            Set<String> before = new HashSet<String>(guild.members.keySet());
            guild.members.clear();
            List<JsonObject> members = objects(data, "members");
            for (JsonObject member : members) {
                putMember(guild, member);
            }
            Set<String> online = new HashSet<String>();
            for (JsonObject presence : objects(data, "presences")) {
                online.add(string(object(presence, "user"), "id"));
                putStatus(guild, presence);
            }
            for (String userId : guild.members.keySet()) {
                if (!online.contains(userId)) {
                    setStatus(userId, null);
                }
            }
            for (String userId : before) {
                if (!listedAnywhere(userId)) {
                    setStatus(userId, null);
                }
            }
            partial = bool(data, "large")
                    || integer(data, "member_count") > members.size();
        }
        changed(guild);
        return partial;
    }

    private boolean holdsWatched(JsonObject data) {
        for (JsonObject channel : objects(data, "channels")) {
            if (this.watched.contains(string(channel, "id"))) {
                return true;
            }
        }
        return false;
    }

    private void forgetGuild(String guildId) {
        Guild gone = this.guilds.remove(guildId);
        if (gone == null) {
            return;
        }
        for (String userId : gone.members.keySet()) {
            if (!listedAnywhere(userId)) {
                setStatus(userId, null);
            }
        }
        this.listsChanged = true;
        this.seeingChanged = true;
    }

    /** Keeps a role's permissions; one whose permissions cannot be read grants nothing. */
    private static void putRole(Guild guild, JsonObject role) {
        String id = string(role, "id");
        if (id.length() > 0
                && (guild.roles.containsKey(id) || guild.roles.size() < MAX_ROLES)) {
            guild.roles.put(id, Long.valueOf(bits(string(role, "permissions"), 0L)));
        }
    }

    /** Keeps a linked channel's overwrites; answers whether it is one. */
    private boolean putChannel(Guild guild, JsonObject channel) {
        String channelId = string(channel, "id");
        if (!this.watched.contains(channelId)) {
            return false;
        }
        Overwrite everyone = null;
        Map<String, Overwrite> roles = new HashMap<String, Overwrite>();
        Map<String, Overwrite> members = new HashMap<String, Overwrite>();
        List<JsonObject> overwrites = objects(channel, "permission_overwrites");
        for (JsonObject entry : overwrites.subList(0,
                Math.min(MAX_OVERWRITES, overwrites.size()))) {
            String id = string(entry, "id");
            // What an overwrite allows but cannot be read allows nothing,
            // and what it denies but cannot be read denies everything.
            Overwrite overwrite = new Overwrite(bits(string(entry, "allow"), 0L),
                    bits(string(entry, "deny"), -1L));
            if (integer(entry, "type") == 1) {
                members.put(id, overwrite);
            } else if (id.equals(guild.id)) {
                everyone = overwrite;
            } else {
                roles.put(id, overwrite);
            }
        }
        guild.channels.put(channelId, new Rules(everyone, roles, members));
        return true;
    }

    private void putMember(Guild guild, JsonObject member) {
        JsonObject user = object(member, "user");
        String userId = string(user, "id");
        if (!DiscordChannelBindings.isSnowflake(userId) || bool(user, "bot")) {
            return;
        }
        // Named as their lines are named in the game: as the server shows them.
        String name = DiscordMessageSanitizer.inboundName(DiscordJson.memberName(member, user));
        if (name.length() == 0) {
            removeMember(guild, userId);
            return;
        }
        if (!guild.members.containsKey(userId)
                && guild.members.size() >= MAX_MEMBERS_PER_GUILD) {
            if (!guild.overflowed) {
                guild.overflowed = true;
                this.overflowed.add(guild.name);
            }
            return;
        }
        List<String> roles = new ArrayList<String>();
        for (JsonElement role : array(member, "roles")) {
            if (role.isJsonPrimitive() && roles.size() < MAX_ROLES) {
                roles.add(role.getAsString());
            }
        }
        guild.members.put(userId, new Member(name, roles));
        changed(guild);
    }

    private void removeMember(Guild guild, String userId) {
        if (guild.members.remove(userId) != null) {
            if (!listedAnywhere(userId)) {
                setStatus(userId, null);
            }
            changed(guild);
        }
    }

    private boolean listedAnywhere(String userId) {
        for (Guild guild : this.guilds.values()) {
            if (guild.members.containsKey(userId)) {
                return true;
            }
        }
        return false;
    }

    /** A status Discord reports, with the custom status among its activities, kept for a member the server lists. */
    private void putStatus(Guild guild, JsonObject presence) {
        String userId = string(object(presence, "user"), "id");
        if (guild.members.containsKey(userId)) {
            setStatus(userId, presenceOf(string(presence, "status")),
                    customStatusOf(presence));
        }
    }

    private void setStatus(String userId, ChatPresence status) {
        setStatus(userId, status, "");
    }

    /**
     * Keeps what a member is doing and the line under it; a member gone
     * offline keeps no line. Only coming or going changes a member list;
     * any change goes to the players who may see the member.
     */
    private void setStatus(String userId, ChatPresence status, String line) {
        ChatPresence was = status == null ? this.statuses.remove(userId)
                : this.statuses.put(userId, status);
        String kept = status == null || line == null ? "" : line;
        String wasLine = kept.length() == 0 ? this.lines.remove(userId)
                : this.lines.put(userId, kept);
        if (was != status || !kept.equals(wasLine == null ? "" : wasLine)) {
            this.changedStatuses.add(userId);
        }
        if ((was == null) != (status == null)) {
            this.listsChanged = true;
        }
    }

    /**
     * A member's custom status as their status line, from the activities
     * Discord's presence lists: the custom status's words and emoji,
     * cleaned as a player's line is; empty for none.
     */
    static String customStatusOf(JsonObject presence) {
        for (JsonObject activity : objects(presence, "activities")) {
            if (integer(activity, "type") == CUSTOM_STATUS) {
                JsonObject emoji = object(activity, "emoji");
                return DiscordMessageSanitizer.inboundStatusLine(
                        string(activity, "state"), string(emoji, "name"),
                        string(emoji, "id").length() > 0);
            }
        }
        return "";
    }

    /**
     * Discord's word for what a member is doing, as the chat says it:
     * online, idle as Away, and do not disturb; null for anything else,
     * which is offline.
     */
    static ChatPresence presenceOf(String discordStatus) {
        if ("online".equals(discordStatus)) {
            return ChatPresence.ONLINE;
        }
        if ("idle".equals(discordStatus)) {
            return ChatPresence.AWAY;
        }
        return "dnd".equals(discordStatus) ? ChatPresence.DO_NOT_DISTURB : null;
    }

    /**
     * Discord said to wait before asking for a server's members again:
     * they are asked for once the wait is over, and never later than
     * {@link #MAX_RETRY_MILLIS} from now.
     */
    private void onRateLimited(JsonObject data, long now) {
        if (integer(data, "opcode") != 8) {
            return;
        }
        Guild guild = this.guilds.get(string(object(data, "meta"), "guild_id"));
        if (guild == null) {
            return;
        }
        double seconds;
        try {
            JsonElement value = data.get("retry_after");
            seconds = value != null && value.isJsonPrimitive() ? value.getAsDouble() : 0.0D;
        } catch (RuntimeException unreadable) {
            seconds = 0.0D;
        }
        long wait = seconds >= 0.0D && seconds <= MAX_RETRY_MILLIS / 1000.0D
                ? (long)Math.ceil(Math.max(1.0D, seconds) * 1000.0D) : MAX_RETRY_MILLIS;
        guild.retryAt = now + wait;
    }

    private void changed(Guild guild) {
        guild.seeing.clear();
        this.listsChanged = true;
        this.seeingChanged = true;
    }

    /** A permission bitfield as Discord writes it, a decimal number of up to 64 bits; {@code unreadable} for text that is none. */
    static long bits(String text, long unreadable) {
        if (text == null || text.length() == 0 || text.length() > 20) {
            return unreadable;
        }
        try {
            return Long.parseUnsignedLong(text);
        } catch (NumberFormatException notANumber) {
            return unreadable;
        }
    }

    private static List<JsonObject> objects(JsonObject parent, String key) {
        List<JsonObject> found = new ArrayList<JsonObject>();
        for (JsonElement element : array(parent, key)) {
            if (element.isJsonObject()) {
                found.add(element.getAsJsonObject());
            }
        }
        return found;
    }

    private static Iterable<JsonElement> array(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray()
                : Collections.<JsonElement>emptyList();
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return "";
        }
        try {
            return value.getAsString();
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : 0;
        } catch (RuntimeException unreadable) {
            return 0;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        try {
            return value != null && value.isJsonPrimitive() && value.getAsBoolean();
        } catch (RuntimeException unreadable) {
            return false;
        }
    }
}
