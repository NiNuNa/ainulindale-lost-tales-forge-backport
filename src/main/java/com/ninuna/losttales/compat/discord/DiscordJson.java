package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The two shapes the bridge exchanges with Discord, with the bundled
 * Gson: the message list a channel returns, and the body a webhook is
 * given. Nothing else of the API is modelled. Parsing never throws:
 * anything that is not the expected shape yields an empty list.
 */
public final class DiscordJson {
    /** Messages read at a time; Discord allows up to a hundred. */
    public static final int PAGE_SIZE = 50;

    private DiscordJson() {}

    /** One Discord message, reduced to what the chat needs. */
    public static final class Message {
        public final String id;
        public final String authorName;
        public final boolean bot;
        public final String content;
        /** User id to display name for every {@code <@id>} in the content. */
        public final Map<String, String> mentionNames;

        Message(String id, String authorName, boolean bot, String content,
                Map<String, String> mentionNames) {
            this.id = id;
            this.authorName = authorName;
            this.bot = bot;
            this.content = content;
            this.mentionNames = mentionNames;
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
        return new Message(id, author == null ? "" : displayName(author),
                author != null && bool(author, "bot"),
                string(object, "content"),
                Collections.unmodifiableMap(mentions));
    }

    /** The name Discord shows: the global display name, else the username. */
    private static String displayName(JsonObject user) {
        String global = string(user, "global_name");
        return global.length() > 0 ? global : string(user, "username");
    }

    /**
     * The body of a webhook post: the text under a name and, when given,
     * a picture, pinging nobody.
     */
    public static String webhookBody(String username, String avatarUrl,
                                     String content) {
        JsonObject body = new JsonObject();
        body.addProperty("content", content == null ? "" : content);
        if (username != null && username.length() > 0) {
            body.addProperty("username", username);
        }
        if (avatarUrl != null && avatarUrl.length() > 0) {
            body.addProperty("avatar_url", avatarUrl);
        }
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());
        body.add("allowed_mentions", allowedMentions);
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
