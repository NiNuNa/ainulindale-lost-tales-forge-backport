package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The names of the Discord servers the bot is in and of their channels,
 * as the gateway tells them: a server arrives with its channels when the
 * session starts or the bot is invited, and each rename, new channel and
 * removal follows as an event of its own. What a Discord member's line is
 * titled by in the game ({@code Nils, of The Shire}) and what a link is
 * reported by ({@code #general of The Shire}). It also remembers the
 * nicknames the members it hears of go by in each server, so a message
 * read while the gateway is down, which Discord sends without them, is
 * still named as the server shows its author. Written on the gateway's
 * thread and read on any; a name is cleaned and bounded as it is kept,
 * and the directory never holds more than {@link #MAX_GUILDS} servers,
 * {@link #MAX_CHANNELS} channels and {@link #MAX_NICKNAMES} nicknames,
 * the longest unused going first, so a bot in very many servers cannot
 * grow it without end.
 */
final class DiscordGuildDirectory {
    static final int MAX_GUILDS = 256;
    static final int MAX_CHANNELS = 8192;
    /** The longest server or channel name kept; Discord allows 100. */
    static final int MAX_NAME_LENGTH = 32;
    static final int MAX_NICKNAMES = 4096;

    private final Map<String, String> guildNames = new ConcurrentHashMap<String, String>();
    private final Map<String, String> channelGuilds = new ConcurrentHashMap<String, String>();
    private final Map<String, String> channelNames = new ConcurrentHashMap<String, String>();
    /** Each member's nickname in a server, by server and member id; the least used goes first. */
    private final Map<String, String> nicknames =
            new LinkedHashMap<String, String>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_NICKNAMES;
                }
            };

    /**
     * A gateway event, taken for what it says of names: a server with
     * its channels, a server renamed or left, a channel made, renamed or
     * removed. Any other event is ignored.
     */
    void onEvent(String name, JsonObject data) {
        if (name == null || data == null) {
            return;
        }
        if ("GUILD_CREATE".equals(name) || "GUILD_UPDATE".equals(name)) {
            String guildId = string(data, "id");
            if (guildId.length() == 0) {
                return;
            }
            if (!data.has("unavailable") || !data.get("unavailable").isJsonPrimitive()
                    || !data.get("unavailable").getAsBoolean()) {
                putGuild(guildId, string(data, "name"));
            }
            if (data.has("channels") && data.get("channels").isJsonArray()) {
                for (JsonElement channel : data.getAsJsonArray("channels")) {
                    if (channel.isJsonObject()) {
                        putChannel(guildId, channel.getAsJsonObject());
                    }
                }
            }
        } else if ("GUILD_DELETE".equals(name)) {
            forgetGuild(string(data, "id"));
        } else if ("CHANNEL_CREATE".equals(name) || "CHANNEL_UPDATE".equals(name)) {
            putChannel(string(data, "guild_id"), data);
        } else if ("CHANNEL_DELETE".equals(name)) {
            String channelId = string(data, "id");
            this.channelGuilds.remove(channelId);
            this.channelNames.remove(channelId);
        }
        noteNicknames(name, data);
    }

    /**
     * The nicknames an event tells: a message's author and a reaction's
     * member, a member joining or changed, and a server's members as it
     * arrives or in the chunks the bridge asks for.
     */
    private void noteNicknames(String name, JsonObject data) {
        String guildId = string(data, "guild_id");
        if ("MESSAGE_CREATE".equals(name) || "MESSAGE_UPDATE".equals(name)) {
            JsonObject member = object(data, "member");
            JsonObject author = object(data, "author");
            if (member != null && author != null) {
                noteNickname(guildId, string(author, "id"), string(member, "nick"));
            }
        } else if ("MESSAGE_REACTION_ADD".equals(name)) {
            JsonObject member = object(data, "member");
            if (member != null) {
                noteNickname(guildId, string(data, "user_id"), string(member, "nick"));
            }
        } else if ("GUILD_MEMBER_ADD".equals(name) || "GUILD_MEMBER_UPDATE".equals(name)) {
            noteNickname(guildId, string(object(data, "user"), "id"),
                    string(data, "nick"));
        } else if ("GUILD_MEMBERS_CHUNK".equals(name) || "GUILD_CREATE".equals(name)) {
            String server = "GUILD_CREATE".equals(name) ? string(data, "id") : guildId;
            if (data.has("members") && data.get("members").isJsonArray()) {
                for (JsonElement member : data.getAsJsonArray("members")) {
                    if (member.isJsonObject()) {
                        JsonObject entry = member.getAsJsonObject();
                        noteNickname(server, string(object(entry, "user"), "id"),
                                string(entry, "nick"));
                    }
                }
            }
        }
    }

    /** Keeps a member's nickname in a server; an empty one is forgotten, the member going by their own name. */
    void noteNickname(String guildId, String userId, String nick) {
        if (guildId.length() == 0 || !DiscordChannelBindings.isSnowflake(userId)) {
            return;
        }
        String key = guildId + ':' + userId;
        String kept = DiscordMessageSanitizer.inboundName(nick);
        synchronized (this.nicknames) {
            if (kept.length() == 0) {
                this.nicknames.remove(key);
            } else {
                this.nicknames.put(key, kept);
            }
        }
    }

    /**
     * The name the author of a message in {@code channelId} goes by
     * there: their nickname in the channel's server where it is known,
     * else {@code fallback}, the name the message came with.
     */
    String nameIn(String channelId, String userId, String fallback) {
        String guildId = channelId == null ? null : this.channelGuilds.get(channelId);
        if (guildId == null || userId == null) {
            return fallback;
        }
        synchronized (this.nicknames) {
            String nick = this.nicknames.get(guildId + ':' + userId);
            return nick == null ? fallback : nick;
        }
    }

    /** Learns which server a channel is in, as a message from it says. */
    void noteChannel(String channelId, String guildId) {
        if (channelId != null && channelId.length() > 0 && guildId != null
                && guildId.length() > 0 && (this.channelGuilds.containsKey(channelId)
                        || this.channelGuilds.size() < MAX_CHANNELS)) {
            this.channelGuilds.put(channelId, guildId);
        }
    }

    /** The name of the server a channel is in; empty when not known. */
    String guildNameOfChannel(String channelId) {
        String guildId = channelId == null ? null : this.channelGuilds.get(channelId);
        String name = guildId == null ? null : this.guildNames.get(guildId);
        return name == null ? "" : name;
    }

    /** A server's name; empty when not known. */
    String guildName(String guildId) {
        String name = guildId == null ? null : this.guildNames.get(guildId);
        return name == null ? "" : name;
    }

    /** A channel's name, without its '#'; empty when not known. */
    String channelName(String channelId) {
        String name = channelId == null ? null : this.channelNames.get(channelId);
        return name == null ? "" : name;
    }

    void clear() {
        this.guildNames.clear();
        this.channelGuilds.clear();
        this.channelNames.clear();
        synchronized (this.nicknames) {
            this.nicknames.clear();
        }
    }

    private void putGuild(String guildId, String rawName) {
        String name = clean(rawName);
        if (name.length() > 0 && (this.guildNames.containsKey(guildId)
                || this.guildNames.size() < MAX_GUILDS)) {
            this.guildNames.put(guildId, name);
        }
    }

    private void putChannel(String guildId, JsonObject channel) {
        String channelId = string(channel, "id");
        if (channelId.length() == 0) {
            return;
        }
        noteChannel(channelId, guildId);
        String name = clean(string(channel, "name"));
        if (name.length() > 0 && (this.channelNames.containsKey(channelId)
                || this.channelNames.size() < MAX_CHANNELS)) {
            this.channelNames.put(channelId, name);
        }
    }

    private void forgetGuild(String guildId) {
        if (guildId.length() == 0) {
            return;
        }
        this.guildNames.remove(guildId);
        for (Map.Entry<String, String> entry : this.channelGuilds.entrySet()) {
            if (guildId.equals(entry.getValue())) {
                this.channelGuilds.remove(entry.getKey());
                this.channelNames.remove(entry.getKey());
            }
        }
    }

    /**
     * A name as the game and Discord may show it: what an inbound name
     * becomes ({@link DiscordMessageSanitizer#inboundName}), cut to
     * {@link #MAX_NAME_LENGTH}.
     */
    static String clean(String name) {
        String cleaned = DiscordMessageSanitizer.inboundName(name == null ? "" : name).trim();
        return cleaned.length() <= MAX_NAME_LENGTH ? cleaned
                : cleaned.substring(0, MAX_NAME_LENGTH).trim();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
