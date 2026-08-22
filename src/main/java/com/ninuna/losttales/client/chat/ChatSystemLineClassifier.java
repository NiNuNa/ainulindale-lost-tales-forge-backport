package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * Decides which channel a chat line that Lost Tales did not route belongs
 * to, from the translation key the server built it with rather than from
 * its rendered text. Lines the whole server sees — achievements, death
 * messages, joins and leaves, {@code /say} and {@code /me} — are
 * conversation everyone shares and go to Global; everything else (command
 * output, fast-travel countdowns, LOTR notices, other mods' lines, plain
 * text components) is the player's private console.
 *
 * <p>Keys are the senders' own: {@code EntityPlayerMP},
 * {@code StatisticsFile}, {@code ServerConfigurationManager},
 * {@code CommandBroadcast} and {@code CommandEmote} send vanilla's, and
 * {@code LOTRAchievement} broadcasts Middle-earth achievements with
 * {@code chat.lotr.achievement}; all are {@code ChatComponentTranslation}s,
 * so the classification does not depend on the language the client runs
 * in. Adding a category means adding a key here, nowhere else.</p>
 */
final class ChatSystemLineClassifier {
    private static final String[] GLOBAL_KEYS = {
            "chat.type.achievement",
            "chat.type.achievement.taken",
            "chat.type.announcement",
            "chat.type.emote",
            "chat.type.text",
            "multiplayer.player.joined",
            "multiplayer.player.joined.renamed",
            "multiplayer.player.left",
            "chat.lotr.achievement",
    };
    private static final String[] GLOBAL_KEY_PREFIXES = {
            "death.",
    };

    private ChatSystemLineClassifier() {}

    /** The channel to file the line under; null only for no line at all. */
    static ChatChannel classify(IChatComponent message) {
        if (message == null) {
            return null;
        }
        String key = translationKey(message);
        if (key == null) {
            return ChatChannel.CONSOLE;
        }
        for (String global : GLOBAL_KEYS) {
            if (global.equals(key)) {
                return ChatChannel.ALL;
            }
        }
        for (String prefix : GLOBAL_KEY_PREFIXES) {
            if (key.startsWith(prefix)) {
                return ChatChannel.ALL;
            }
        }
        return ChatChannel.CONSOLE;
    }

    /** The root component's translation key, or null for anything else. */
    static String translationKey(IChatComponent message) {
        if (!(message instanceof ChatComponentTranslation)) {
            return null;
        }
        String key = ((ChatComponentTranslation)message).getKey();
        return key == null || key.length() == 0 ? null : key;
    }
}
