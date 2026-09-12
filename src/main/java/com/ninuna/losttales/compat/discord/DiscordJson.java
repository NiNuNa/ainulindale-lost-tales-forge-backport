package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The shapes the bridge exchanges with Discord, with the bundled Gson:
 * the message list a channel returns, the bodies a webhook is given — a
 * chat line as text under a name, a server notice as one embed — and
 * the body that sets a channel's topic. Nothing else of the API is
 * modelled. Parsing never throws: anything that is not the expected
 * shape yields an empty list.
 */
public final class DiscordJson {
    /** Messages read at a time; Discord allows up to a hundred. */
    public static final int PAGE_SIZE = 50;
    /** Discord's error code for an emoji it does not know or the bot may not use. */
    public static final int ERROR_UNKNOWN_EMOJI = 10014;

    private DiscordJson() {}

    /** One Discord message, reduced to what the chat needs. */
    public static final class Message {
        public final String id;
        /** The author's own Discord id; empty when the message had no author. */
        public final String authorId;
        public final String authorName;
        public final boolean bot;
        public final String content;
        /** User id to display name for every {@code <@id>} in the content. */
        public final Map<String, String> mentionNames;
        /**
         * The Discord id of the message this one replies to, empty for
         * an ordinary message. Only a real reply carries it: a forward
         * also has a message reference, but of its own kind, and is not
         * an answer to anything.
         */
        public final String referencedMessageId;
        /**
         * When the message was last edited, empty for never. The value
         * itself is opaque here — what matters is that it changes with
         * every edit, which is how a re-read page betrays one.
         */
        public final String editedTimestamp;
        /** The channel the message is in; empty when the listing did not say. */
        public final String channelId;

        Message(String id, String authorId, String authorName, boolean bot,
                String content, Map<String, String> mentionNames,
                String referencedMessageId, String editedTimestamp) {
            this(id, authorId, authorName, bot, content, mentionNames,
                    referencedMessageId, editedTimestamp, "");
        }

        Message(String id, String authorId, String authorName, boolean bot,
                String content, Map<String, String> mentionNames,
                String referencedMessageId, String editedTimestamp,
                String channelId) {
            this.channelId = channelId == null ? "" : channelId;
            this.id = id;
            this.authorId = authorId;
            this.authorName = authorName;
            this.bot = bot;
            this.content = content;
            this.mentionNames = mentionNames;
            this.referencedMessageId = referencedMessageId;
            this.editedTimestamp = editedTimestamp;
        }

        /**
         * Whether a member rewrote the message. Discord announces a
         * message update for more than an edit — a link's embed
         * unfurling after the post, a pin, a suppressed embed — and
         * only a rewrite stamps the edit time, so the stamp is what
         * separates a member's edit from Discord's own bookkeeping.
         */
        public boolean isEdited() {
            return this.editedTimestamp != null
                    && this.editedTimestamp.length() > 0;
        }
    }

    /**
     * The messages in a channel listing, oldest first, or an empty list
     * for anything that does not parse as one.
     */
    public static List<Message> parseMessages(String json) {
        if (json == null || json.trim().length() == 0) {
            return Collections.emptyList();
        }
        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        } catch (RuntimeException exception) {
            return Collections.emptyList();
        }
        if (root == null || !root.isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray array = root.getAsJsonArray();
        List<Message> messages = new ArrayList<Message>(array.size());
        // Discord lists newest first; the chat wants them as they came.
        for (int index = array.size() - 1; index >= 0; index--) {
            Message message = parseMessage(array.get(index));
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    private static Message parseMessage(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String id = string(object, "id");
        if (id.length() == 0) {
            return null;
        }
        JsonObject author = object.has("author")
                && object.get("author").isJsonObject()
                ? object.getAsJsonObject("author") : null;
        Map<String, String> mentions = new HashMap<String, String>();
        if (object.has("mentions") && object.get("mentions").isJsonArray()) {
            for (JsonElement value : object.getAsJsonArray("mentions")) {
                if (value.isJsonObject()) {
                    JsonObject user = value.getAsJsonObject();
                    String userId = string(user, "id");
                    if (userId.length() > 0) {
                        mentions.put(userId, displayName(user));
                    }
                }
            }
        }
        return new Message(id, author == null ? "" : string(author, "id"),
                author == null ? "" : displayName(author),
                author != null && bool(author, "bot"),
                string(object, "content"),
                Collections.unmodifiableMap(mentions),
                referencedMessageId(object),
                string(object, "edited_timestamp"),
                string(object, "channel_id"));
    }

    /** One message object as the gateway delivers it, or null for anything else. */
    public static Message parseMessage(JsonObject object) {
        return parseMessage((JsonElement)object);
    }

    /**
     * A slash command being used, as {@code INTERACTION_CREATE} carries
     * it: what to answer, and the id and token the answer goes back by.
     */
    public static final class Interaction {
        public final String id;
        public final String token;
        public final String applicationId;
        public final String name;
        /** Option name to its value as text. */
        public final Map<String, String> options;
        public final String channelId;
        public final String guildId;
        public final String userName;

        Interaction(String id, String token, String applicationId, String name,
                    Map<String, String> options, String channelId, String guildId,
                    String userName) {
            this.id = id;
            this.token = token;
            this.applicationId = applicationId;
            this.name = name;
            this.options = options;
            this.channelId = channelId;
            this.guildId = guildId;
            this.userName = userName;
        }
    }

    /** The interaction when it is a slash command with its name and token; else null. */
    public static Interaction parseInteraction(JsonObject object) {
        if (object == null) {
            return null;
        }
        JsonElement typeValue = object.get("type");
        int type;
        try {
            type = typeValue != null && typeValue.isJsonPrimitive() ? typeValue.getAsInt() : 0;
        } catch (RuntimeException malformed) {
            return null;
        }
        String id = string(object, "id");
        String token = string(object, "token");
        JsonObject data = object.has("data") && object.get("data").isJsonObject()
                ? object.getAsJsonObject("data") : null;
        if (type != 2 || id.length() == 0 || token.length() == 0 || data == null) {
            return null;
        }
        String name = string(data, "name");
        if (name.length() == 0) {
            return null;
        }
        Map<String, String> options = new HashMap<String, String>();
        if (data.has("options") && data.get("options").isJsonArray()) {
            for (JsonElement option : data.getAsJsonArray("options")) {
                if (option.isJsonObject()) {
                    JsonObject entry = option.getAsJsonObject();
                    String optionName = string(entry, "name");
                    if (optionName.length() > 0) {
                        options.put(optionName, string(entry, "value"));
                    }
                }
            }
        }
        JsonObject user = object.has("user") && object.get("user").isJsonObject()
                ? object.getAsJsonObject("user") : null;
        if (user == null && object.has("member") && object.get("member").isJsonObject()) {
            JsonObject member = object.getAsJsonObject("member");
            user = member.has("user") && member.get("user").isJsonObject()
                    ? member.getAsJsonObject("user") : null;
        }
        return new Interaction(id, token, string(object, "application_id"), name,
                Collections.unmodifiableMap(options), string(object, "channel_id"),
                string(object, "guild_id"), user == null ? "" : displayName(user));
    }

    /** The gateway URL {@code GET /gateway/bot} answers with; empty for anything else. */
    public static String parseGatewayUrl(String json) {
        JsonObject object = parseObject(json);
        return object == null ? "" : string(object, "url");
    }

    /** "Working on it": the deferred answer to a command, only the asker to see. */
    public static String deferredReplyBody(boolean ephemeral) {
        JsonObject data = new JsonObject();
        if (ephemeral) {
            data.addProperty("flags", Integer.valueOf(64));
        }
        JsonObject body = new JsonObject();
        body.addProperty("type", Integer.valueOf(5));
        body.add("data", data);
        return body.toString();
    }

    /** The answer itself, filled into the deferred reply; pings nobody. */
    public static String followUpBody(String content) {
        JsonObject body = new JsonObject();
        body.addProperty("content", content == null ? "" : content);
        body.add("allowed_mentions", noMentions());
        return body.toString();
    }

    /**
     * The Discord id a reply's {@code message_reference} names, or empty
     * for anything else. A reference of any type but the default reply —
     * a forward is type 1 — is not an answer and yields nothing.
     */
    private static String referencedMessageId(JsonObject object) {
        if (!object.has("message_reference")
                || !object.get("message_reference").isJsonObject()) {
            return "";
        }
        JsonObject reference = object.getAsJsonObject("message_reference");
        JsonElement type = reference.get("type");
        try {
            if (type != null && type.isJsonPrimitive()
                    && type.getAsInt() != 0) {
                return "";
            }
        } catch (RuntimeException exception) {
            return "";
        }
        return string(reference, "message_id");
    }

    /**
     * The id of the message a {@code wait=true} webhook post answers
     * with, or empty for anything that does not parse as one.
     */
    public static String parseCreatedMessageId(String json) {
        JsonObject object = parseObject(json);
        return object == null ? "" : string(object, "id");
    }

    /** Where a Discord channel is: the guild that holds it and its own id. */
    public static final class ChannelInfo {
        public final String guildId;
        public final String channelId;

        ChannelInfo(String guildId, String channelId) {
            this.guildId = guildId;
            this.channelId = channelId;
        }
    }

    /**
     * The guild and channel a webhook posts into, read from the webhook
     * object its own URL answers with, or null for anything else.
     */
    public static ChannelInfo parseWebhookInfo(String json) {
        return channelInfo(parseObject(json), "channel_id");
    }

    /**
     * The guild and id of a channel, read from the channel object the
     * bot is answered with, or null for anything else — a channel with
     * no guild (a direct message) included, since nothing is bound there.
     */
    public static ChannelInfo parseChannelInfo(String json) {
        return channelInfo(parseObject(json), "id");
    }

    private static ChannelInfo channelInfo(JsonObject object, String channelKey) {
        if (object == null) {
            return null;
        }
        String guildId = string(object, "guild_id");
        String channelId = string(object, channelKey);
        return guildId.length() == 0 || channelId.length() == 0 ? null
                : new ChannelInfo(guildId, channelId);
    }

    private static JsonObject parseObject(String json) {
        if (json == null || json.trim().length() == 0) {
            return null;
        }
        try {
            JsonElement root = new JsonParser().parse(json);
            return root != null && root.isJsonObject()
                    ? root.getAsJsonObject() : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * A reaction as the gateway reports one being added or taken back,
     * or every reaction — or every one with an emoji — being cleared.
     * The member and the user are empty where the event does not carry
     * them: a removal names the user by id alone, a clear names nobody.
     */
    public static final class Reaction {
        public final String userId;
        public final String channelId;
        public final String messageId;
        /** A custom emoji's id; empty for a Unicode one. */
        public final String emojiId;
        /** A Unicode emoji itself, or a custom emoji's name. */
        public final String emojiName;
        /** The name the member shows in the server, when the event says. */
        public final String memberName;
        public final boolean bot;

        Reaction(String userId, String channelId, String messageId,
                 String emojiId, String emojiName, String memberName,
                 boolean bot) {
            this.userId = userId;
            this.channelId = channelId;
            this.messageId = messageId;
            this.emojiId = emojiId;
            this.emojiName = emojiName;
            this.memberName = memberName;
            this.bot = bot;
        }

        /**
         * The chat's emoji the reaction is: a Unicode emoji the registry
         * carries — trailing variation selectors aside, which Discord
         * adds to some and not others — or a custom emoji named as one
         * of the registry's names; null for any other, which reaches the
         * chat by its foreign key ({@link #reactionKey()}).
         */
        public ChatEmoji emoji() {
            if (this.emojiName.length() == 0) {
                return null;
            }
            if (this.emojiId.length() > 0) {
                return ChatEmoji.fromInputName(
                        this.emojiName.toLowerCase(Locale.ROOT));
            }
            ChatEmoji.UnicodeMatch match =
                    ChatEmoji.matchUnicode(this.emojiName, 0);
            if (match == null) {
                return null;
            }
            int end = match.length;
            while (end < this.emojiName.length()
                    && this.emojiName.charAt(end) == '\uFE0F') {
                end++;
            }
            return end == this.emojiName.length() ? match.emoji : null;
        }

        /**
         * The reaction's key in the chat: the registry's name for an
         * emoji it carries ({@link #emoji()}), else the foreign key of
         * what Discord calls it — {@code name:id} for a custom emoji,
         * the Unicode itself for any other. Null when Discord names it
         * in no shape a key holds, such as a deleted custom emoji, which
         * Discord sends without a name.
         */
        public String reactionKey() {
            ChatEmoji known = emoji();
            if (known != null) {
                return known.getName();
            }
            return this.emojiId.length() > 0
                    ? ChatForeignEmoji.customKey(this.emojiName, this.emojiId)
                    : ChatForeignEmoji.unicodeKey(this.emojiName);
        }
    }

    /** One reaction event's payload, or null when it names no message. */
    public static Reaction parseReaction(JsonObject data) {
        if (data == null) {
            return null;
        }
        String channelId = string(data, "channel_id");
        String messageId = string(data, "message_id");
        if (channelId.length() == 0 || messageId.length() == 0) {
            return null;
        }
        JsonObject emoji = data.has("emoji") && data.get("emoji").isJsonObject()
                ? data.getAsJsonObject("emoji") : null;
        JsonObject member = data.has("member") && data.get("member").isJsonObject()
                ? data.getAsJsonObject("member") : null;
        JsonObject user = member != null && member.has("user")
                && member.get("user").isJsonObject()
                ? member.getAsJsonObject("user") : null;
        String nick = member == null ? "" : string(member, "nick");
        return new Reaction(string(data, "user_id"), channelId, messageId,
                emoji == null ? "" : string(emoji, "id"),
                emoji == null ? "" : string(emoji, "name"),
                nick.length() > 0 ? nick : user == null ? "" : displayName(user),
                user != null && bool(user, "bot"));
    }

    /** The name Discord shows: the global display name, else the username. */
    private static String displayName(JsonObject user) {
        String global = string(user, "global_name");
        return global.length() > 0 ? global : string(user, "username");
    }

    /**
     * The body of one of the server's own notices: no text, one embed
     * with the notice's colour down its edge and the notice's line on
     * the author row, the player's head beside it when there is one.
     * Posted under the webhook's own name and picture. The author row
     * renders no markdown and reaches nobody's mentions; the mention
     * block is sent all the same, so the post can ping nobody whatever
     * Discord makes of it.
     */
    public static String webhookEmbedBody(DiscordNotice notice) {
        JsonObject author = new JsonObject();
        author.addProperty("name", notice == null ? "" : notice.getText());
        if (notice != null && notice.getIconUrl().length() > 0) {
            author.addProperty("icon_url", notice.getIconUrl());
        }
        JsonObject embed = new JsonObject();
        embed.addProperty("color", Integer.valueOf(
                notice == null ? 0 : notice.getColor()));
        embed.add("author", author);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject body = new JsonObject();
        body.add("embeds", embeds);
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());
        body.add("allowed_mentions", allowedMentions);
        return body.toString();
    }

    /**
     * The body of a game line: the text under the sender's name and,
     * when given, a picture, pinging nobody. {@code content} is the line
     * as it is to read on Discord — a reply's header and the message
     * after it.
     */
    public static String webhookLineBody(String username, String avatarUrl,
                                         String content) {
        JsonObject body = new JsonObject();
        body.addProperty("content", content == null ? "" : content);
        if (username != null && username.length() > 0) {
            body.addProperty("username", username);
        }
        if (avatarUrl != null && avatarUrl.length() > 0) {
            body.addProperty("avatar_url", avatarUrl);
        }
        body.add("allowed_mentions", noMentions());
        return body.toString();
    }

    /**
     * The body of a game line's edit: the new text, still pinging
     * nobody. The name and picture belong to the post and cannot change.
     */
    public static String webhookLineEditBody(String content) {
        JsonObject body = new JsonObject();
        body.addProperty("content", content == null ? "" : content);
        body.add("allowed_mentions", noMentions());
        return body.toString();
    }

    /** The mention block that lets a post ping nobody. */
    private static JsonObject noMentions() {
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());
        return allowedMentions;
    }

    /** The body of a channel modification that sets only the topic. */
    public static String channelTopicBody(String topic) {
        JsonObject body = new JsonObject();
        body.addProperty("topic", topic == null ? "" : topic);
        return body.toString();
    }

    /**
     * The {@code code} of an error reply, such as
     * {@link #ERROR_UNKNOWN_EMOJI}; 0 for anything that does not carry
     * one.
     */
    public static int errorCode(String json) {
        JsonObject object = parseObject(json);
        JsonElement code = object == null ? null : object.get("code");
        try {
            return code != null && code.isJsonPrimitive()
                    && code.getAsJsonPrimitive().isNumber()
                    ? code.getAsInt() : 0;
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    /** {@code retry_after} of a rate-limit reply, in milliseconds; 0 if absent. */
    public static long retryAfterMillis(String json) {
        if (json == null) {
            return 0L;
        }
        try {
            JsonElement root = new JsonParser().parse(json);
            if (root != null && root.isJsonObject()
                    && root.getAsJsonObject().has("retry_after")) {
                double seconds = root.getAsJsonObject()
                        .get("retry_after").getAsDouble();
                return seconds > 0.0D ? (long)Math.ceil(seconds * 1000.0D) : 0L;
            }
        } catch (RuntimeException ignored) {
            // Not the shape Discord documents; back off by default instead.
        }
        return 0L;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return "";
        }
        try {
            return value.getAsString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        try {
            return value != null && value.isJsonPrimitive()
                    && value.getAsBoolean();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
