package com.ninuna.losttales.compat.discord;

/**
 * The words of the bridge's own posts and of the channel topic: short,
 * one line each, and the same every time, so a Discord reader learns
 * them at a glance. Names are escaped for Discord's markdown (an
 * underscore in {@code x_y_z} would otherwise italicise) and nothing
 * here can ping anyone; the webhook post disallows mentions anyway.
 */
public final class DiscordServerNotices {
    /** Green and red circles, as Discord draws them. */
    private static final String ONLINE = "🟢";
    private static final String OFFLINE = "🔴";
    private static final String SEPARATOR = " • ";
    /** Discord caps a channel topic at 1024 characters; ours is far shorter. */
    private static final int MAX_TOPIC_LENGTH = 1024;

    private DiscordServerNotices() {}

    public static String serverStarted() {
        return ONLINE + " Server started";
    }

    public static String serverStopping() {
        return OFFLINE + " Server shutting down";
    }

    public static String playerJoined(String name) {
        return "**" + escape(name) + "** joined the game";
    }

    public static String playerLeft(String name) {
        return "**" + escape(name) + "** left the game";
    }

    /** {@code Server online • 3/20 players}; without a cap, {@code 3 players}. */
    public static String onlineTopic(int players, int maxPlayers) {
        int online = Math.max(0, players);
        StringBuilder topic = new StringBuilder("Server online")
                .append(SEPARATOR).append(online);
        if (maxPlayers > 0) {
            topic.append('/').append(maxPlayers);
        }
        topic.append(online == 1 && maxPlayers <= 0 ? " player" : " players");
        return bound(topic.toString());
    }

    public static String offlineTopic() {
        return "Server offline";
    }

    /** Backslash-escapes every character Discord's markdown gives meaning to. */
    static String escape(String name) {
        String value = name == null ? "" : name.trim();
        StringBuilder escaped = new StringBuilder(value.length() + 4);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '*' || character == '_'
                    || character == '~' || character == '`' || character == '|'
                    || character == '>' || character == '@' || character == '#') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    private static String bound(String topic) {
        return topic.length() <= MAX_TOPIC_LENGTH ? topic
                : topic.substring(0, MAX_TOPIC_LENGTH);
    }
}
