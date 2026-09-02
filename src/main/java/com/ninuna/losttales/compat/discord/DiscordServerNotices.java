package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.regex.Pattern;

/**
 * The words, colours and icons of the bridge's own posts, and the words
 * of the channel topic: short, one line each, and the same every time,
 * so a Discord reader learns them at a glance. Each kind of notice has a
 * fixed icon and a fixed edge colour from the mod's own palette — greens
 * for the server coming up and a player arriving, reds for going down
 * and leaving, a subdued plum for a death, honey for an achievement — so
 * a glance at the channel says what happened before the words are read.
 * Notice text renders as plain text and can ping nobody; the topic is
 * bounded at Discord's own limit.
 */
public final class DiscordServerNotices {
    /** Green and red circles, as Discord draws them. */
    private static final String ONLINE = "🟢";
    private static final String OFFLINE = "🔴";
    private static final String JOINED = "✅";
    private static final String LEFT = "👋";
    private static final String DIED = "💀";
    private static final String ACHIEVED = "🏆";
    private static final String SEPARATOR = " • ";
    /** Discord caps a channel topic at 1024 characters; ours is far shorter. */
    private static final int MAX_TOPIC_LENGTH = 1024;
    /**
     * Vanilla's own formatting-code pattern: a team colour on a name, a
     * coloured item name in a death message. Discord shows the codes as
     * text, so they go. Not
     * {@code EnumChatFormatting.getTextWithoutFormattingCodes}, which is
     * client-only in 1.7.10.
     */
    private static final Pattern FORMATTING_CODES =
            Pattern.compile("(?i)§[0-9A-FK-OR]");

    private DiscordServerNotices() {}

    public static DiscordNotice serverStarted() {
        return new DiscordNotice(DiscordNotice.Kind.SERVER_STARTED,
                ONLINE + " Server started", "",
                LostTalesColors.rgb(LostTalesColors.FERN_GREEN));
    }

    public static DiscordNotice serverStopping() {
        return new DiscordNotice(DiscordNotice.Kind.SERVER_STOPPING,
                OFFLINE + " Server shutting down", "",
                LostTalesColors.rgb(LostTalesColors.CRIMSON));
    }

    /** {@code ✅ Name joined the game}, with the account's head. */
    public static DiscordNotice playerJoined(String name, String iconUrl) {
        return new DiscordNotice(DiscordNotice.Kind.PLAYER_JOINED,
                JOINED + " " + plain(name) + " joined the game", iconUrl,
                LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN));
    }

    /** {@code 👋 Name left the game}, with the account's head. */
    public static DiscordNotice playerLeft(String name, String iconUrl) {
        return new DiscordNotice(DiscordNotice.Kind.PLAYER_LEFT,
                LEFT + " " + plain(name) + " left the game", iconUrl,
                LostTalesColors.rgb(LostTalesColors.SALMON));
    }

    /**
     * The death message exactly as the game broadcast it — vanilla's,
     * LOTR's, another mod's, and naming the character when the identity
     * patch renamed the victim — behind the skull, with the victim's
     * head when the account could be told.
     */
    public static DiscordNotice playerDied(String deathMessage,
                                           String iconUrl) {
        return new DiscordNotice(DiscordNotice.Kind.PLAYER_DIED,
                DIED + " " + plain(deathMessage), iconUrl,
                LostTalesColors.rgb(LostTalesColors.PLUM_GRAY));
    }

    /**
     * The achievement line exactly as the game broadcast it — vanilla's
     * {@code Name has just earned the achievement [Title]} or LOTR's
     * Middle-earth form — behind the trophy.
     */
    public static DiscordNotice achievement(String announcement,
                                            String iconUrl) {
        return new DiscordNotice(DiscordNotice.Kind.ACHIEVEMENT,
                ACHIEVED + " " + plain(announcement), iconUrl,
                LostTalesColors.rgb(LostTalesColors.HONEY));
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

    /**
     * The text as Discord should show it: formatting codes dropped, line
     * breaks and runs of whitespace folded to one space, trimmed.
     */
    static String plain(String text) {
        if (text == null) {
            return "";
        }
        String stripped = FORMATTING_CODES.matcher(text).replaceAll("");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    private static String bound(String topic) {
        return topic.length() <= MAX_TOPIC_LENGTH ? topic
                : topic.substring(0, MAX_TOPIC_LENGTH);
    }
}
