package com.ninuna.losttales.chat;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * Decides what a chat line that Lost Tales did not route is, from the
 * translation key the server built it with rather than from its rendered
 * text. The client asks which channel a line belongs to: lines the whole
 * server sees — achievements, death messages, joins and leaves,
 * {@code /say} and {@code /me} — are conversation everyone shares and go
 * to Global; everything else (command output, fast-travel countdowns,
 * LOTR notices, other mods' lines, plain text components) is the
 * player's private console. The server's Discord bridge asks the finer
 * question of {@link #kindOf}: which of those shared lines is a death,
 * an achievement, a join or a leave, so each can be posted in its own
 * dress.
 *
 * <p>Keys are the senders' own: {@code EntityPlayerMP},
 * {@code StatisticsFile}, {@code ServerConfigurationManager},
 * {@code CommandBroadcast} and {@code CommandEmote} send vanilla's, and
 * {@code LOTRAchievement} broadcasts Middle-earth achievements with
 * {@code chat.lotr.achievement}; all are {@code ChatComponentTranslation}s,
 * so the classification does not depend on the language the client runs
 * in. Adding a category means adding a key here, nowhere else. The class
 * has no client dependency: a dedicated server classifies with it too.</p>
 */
public final class ChatSystemLineClassifier {
    /** What a server-visible line announces; {@link #OTHER} for the rest. */
    public enum Kind { ACHIEVEMENT, DEATH, JOIN, LEAVE, OTHER }

    private static final String[] ACHIEVEMENT_KEYS = {
            "chat.type.achievement",
            "chat.type.achievement.taken",
            "chat.lotr.achievement",
    };
    private static final String[] JOIN_KEYS = {
            "multiplayer.player.joined",
            "multiplayer.player.joined.renamed",
    };
    private static final String[] LEAVE_KEYS = {
            "multiplayer.player.left",
    };
    private static final String DEATH_KEY_PREFIX = "death.";
    /** Shared lines that are neither of the kinds above. */
    private static final String[] OTHER_GLOBAL_KEYS = {
            "chat.type.announcement",
            "chat.type.emote",
            "chat.type.text",
            // LOTR's travelling-trader notices go to every player in the
            // world (LOTRSpeech.messageAllPlayersInWorld), so they are
            // shared conversation, not private console output.
            "lotr.travellingTrader.arrive",
            "lotr.travellingTrader.arriveMP",
            "lotr.travellingTrader.depart",
    };
    /**
     * Lines whose mention of a player is the server announcing them, not
     * somebody addressing them: the name still highlights, but the cue
     * stays silent — an achievement is not a conversation waiting for an
     * answer.
     */
    private static final String[] SILENT_MENTION_KEYS = ACHIEVEMENT_KEYS;

    private ChatSystemLineClassifier() {}

    /** The channel to file the line under; null only for no line at all. */
    public static ChatChannel classify(IChatComponent message) {
        if (message == null) {
            return null;
        }
        String key = translationKey(message);
        if (key == null) {
            return ChatChannel.CONSOLE;
        }
        if (kindOfKey(key) != Kind.OTHER || contains(OTHER_GLOBAL_KEYS, key)) {
            return ChatChannel.ALL;
        }
        return ChatChannel.CONSOLE;
    }

    /**
     * What the line announces. {@link Kind#OTHER} for a line that is not
     * an announcement of one of the named kinds — a command's output, a
     * {@code /say}, plain text, or no line at all.
     */
    public static Kind kindOf(IChatComponent message) {
        String key = translationKey(message);
        return key == null ? Kind.OTHER : kindOfKey(key);
    }

    private static Kind kindOfKey(String key) {
        if (contains(ACHIEVEMENT_KEYS, key)) {
            return Kind.ACHIEVEMENT;
        }
        if (key.startsWith(DEATH_KEY_PREFIX)) {
            return Kind.DEATH;
        }
        if (contains(JOIN_KEYS, key)) {
            return Kind.JOIN;
        }
        if (contains(LEAVE_KEYS, key)) {
            return Kind.LEAVE;
        }
        return Kind.OTHER;
    }

    /** Whether a mention inside the line highlights without sounding. */
    public static boolean isMentionCueSilent(IChatComponent message) {
        String key = translationKey(message);
        return key != null && contains(SILENT_MENTION_KEYS, key);
    }

    /** The root component's translation key, or null for anything else. */
    public static String translationKey(IChatComponent message) {
        if (!(message instanceof ChatComponentTranslation)) {
            return null;
        }
        String key = ((ChatComponentTranslation)message).getKey();
        return key == null || key.length() == 0 ? null : key;
    }

    private static boolean contains(String[] keys, String key) {
        for (String candidate : keys) {
            if (candidate.equals(key)) {
                return true;
            }
        }
        return false;
    }
}
