package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

        Message(String id, String authorId, String authorName, boolean bot,
                String content, Map<String, String> mentionNames,
                String referencedMessageId, String editedTimestamp) {
            this.id = id;
            this.authorId = authorId;
            this.authorName = authorName;
            this.bot = bot;
            this.content = content;
            this.mentionNames = mentionNames;
            this.referencedMessageId = referencedMessageId;
            this.editedTimestamp = editedTimestamp;
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
                string(object, "edited_timestamp"));
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

    /** Where a webhook posts: the ids a jump link is built from. */
    public static final class WebhookInfo {
        public final String guildId;
        public final String channelId;

        WebhookInfo(String guildId, String channelId) {
            this.guildId = guildId;
            this.channelId = channelId;
        }
    }

    /**
     * The guild and channel a webhook belongs to, read from the webhook
     * object its own URL answers with, or null for anything else.
     */
    public static WebhookInfo parseWebhookInfo(String json) {
        JsonObject object = parseObject(json);
        if (object == null) {
            return null;
        }
        String guildId = string(object, "guild_id");
        String channelId = string(object, "channel_id");
        return guildId.length() == 0 || channelId.length() == 0 ? null
                : new WebhookInfo(guildId, channelId);
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
     * What a game line's card says besides the line: the channel it was
     * spoken in (its mark and name, a faction's own name for Faction
     * chat) as the embed's heading, the colour down its edge, the account
     * behind a character's name as its footer (empty on an account line,
     * which is posted under the account's name already), the moment it
     * was said as its timestamp, and the reply it answers as the block
     * quote its text opens with (empty for none). Kept beside a posted
     * line's id so an edit draws the same card again around the new text.
     */
    public static final class LineCard {
        /** A card that says nothing but the line. */
        public static final LineCard NONE = new LineCard("", 0, "", 0L, "");

        public final String channelLabel;
        public final int color;
        public final String accountName;
        public final long postedMillis;
        public final String header;

        public LineCard(String channelLabel, int color, String accountName,
                        long postedMillis, String header) {
            this.channelLabel = channelLabel == null ? "" : channelLabel;
            this.color = color;
            this.accountName = accountName == null ? "" : accountName;
            this.postedMillis = postedMillis;
            this.header = header == null ? "" : header;
        }

        /** The same card opening with {@code header}. */
        public LineCard withHeader(String header) {
            return new LineCard(this.channelLabel, this.color,
                    this.accountName, this.postedMillis, header);
        }
    }

    /**
     * The body of a game line: the sender's name and picture on the
     * post, and the line itself as one embed drawn as {@code card}
     * — the channel's colour, the channel's name at its head, the reply
     * it answers quoted above the text, the account and the time at its
     * foot — so a Discord channel that several game channels share
     * still says where, by whom and when each line was spoken. Pings
     * nobody.
     */
    public static String webhookLineBody(String username, String avatarUrl,
                                         LineCard card, String message) {
        JsonObject body = new JsonObject();
        if (username != null && username.length() > 0) {
            body.addProperty("username", username);
        }
        if (avatarUrl != null && avatarUrl.length() > 0) {
            body.addProperty("avatar_url", avatarUrl);
        }
        body.add("embeds", lineEmbeds(card, message));
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());
        body.add("allowed_mentions", allowedMentions);
        return body.toString();
    }

    /**
     * The body of a game line's edit: the same card drawn again around
     * the new text. The name and picture belong to the post and cannot
     * change.
     */
    public static String webhookLineEditBody(LineCard card, String message) {
        JsonObject body = new JsonObject();
        body.add("embeds", lineEmbeds(card, message));
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());
        body.add("allowed_mentions", allowedMentions);
        return body.toString();
    }

    private static JsonArray lineEmbeds(LineCard card, String message) {
        LineCard drawn = card == null ? LineCard.NONE : card;
        JsonObject embed = new JsonObject();
        embed.addProperty("color", Integer.valueOf(drawn.color & 0xFFFFFF));
        if (drawn.channelLabel.length() > 0) {
            JsonObject author = new JsonObject();
            author.addProperty("name", drawn.channelLabel);
            embed.add("author", author);
        }
        embed.addProperty("description",
                drawn.header + (message == null ? "" : message));
        if (drawn.accountName.length() > 0) {
            JsonObject footer = new JsonObject();
            footer.addProperty("text", drawn.accountName);
            embed.add("footer", footer);
        }
        if (drawn.postedMillis > 0L) {
            embed.addProperty("timestamp",
                    Instant.ofEpochMilli(drawn.postedMillis).toString());
        }
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        return embeds;
    }

    /** The body of a channel modification that sets only the topic. */
    public static String channelTopicBody(String topic) {
        JsonObject body = new JsonObject();
        body.addProperty("topic", topic == null ? "" : topic);
        return body.toString();
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
