package com.ninuna.losttales.chat;

import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.util.LostTalesWords;
import java.util.UUID;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * The chat's own names in words: each channel's, a faction's chat, and
 * the Server's, the Client's and the Narrator's. They are the lang file's,
 * so a game passes {@link LostTalesWords#LANG} and reads them in its
 * player's language, and the server passes the same and writes them in
 * its own; a test passes the English lines. A channel a server defined
 * has no lang line and reads as its config names it.
 *
 * <p>Only what is shown reads these. Ids, code names and the name a line
 * is signed with ({@code Server}, {@code Narrator}) travel and are kept as
 * they are; a game shows the voice behind such a line by its own words
 * ({@link #sender}).</p>
 */
public final class ChatNames {
    /** The Server's name, the voice of the server's own lines. */
    public static final String SERVER_KEY = "chat.losttales.server.name";
    /** The Client's name, the voice of what a game prints for itself. */
    public static final String CLIENT_KEY = "chat.losttales.client.name";
    /** The Narrator's name. */
    public static final String NARRATOR_KEY = "chat.losttales.narrator";
    /** A faction's chat, the faction's name its argument ({@code Gondor Chat}). */
    public static final String FACTION_CHAT_KEY = "chat.losttales.channel.faction.of";

    private ChatNames() {}

    /**
     * A channel's name in {@code words}: a built-in channel's lang line
     * ({@link ChatChannel#getNameKey}), or its display name where the lang
     * file has none; a channel a server defined by its display name.
     */
    public static String channel(LostTalesWords words, ChatChannel channel) {
        if (channel == null) {
            return "";
        }
        String key = channel.getNameKey();
        if (key.length() == 0) {
            return channel.getDisplayName();
        }
        String said = words.format(key);
        return said == null || said.length() == 0 || said.equals(key)
                ? channel.getDisplayName() : said;
    }

    /**
     * A channel's name as words the reader's game translates, for a line
     * the server sends: a built-in channel's lang line, a channel a server
     * defined by its display name.
     */
    public static IChatComponent channelComponent(ChatChannel channel) {
        if (channel == null) {
            return new ChatComponentText("");
        }
        String key = channel.getNameKey();
        return key.length() == 0 ? new ChatComponentText(channel.getDisplayName())
                : new ChatComponentTranslation(key);
    }

    /** A faction's chat in {@code words}, named by the faction ({@code Gondor Chat}). */
    public static String factionChat(LostTalesWords words, String factionName) {
        return words.format(FACTION_CHAT_KEY, factionName);
    }

    public static String server(LostTalesWords words) {
        return words.format(SERVER_KEY);
    }

    public static String client(LostTalesWords words) {
        return words.format(CLIENT_KEY);
    }

    public static String narrator(LostTalesWords words) {
        return words.format(NARRATOR_KEY);
    }

    /**
     * The name a line's sender is shown by, in {@code words}: the Server,
     * the Client and the Narrator by theirs, told by the sender's id and,
     * for the Narrator's own copy of its line, by the Narrator's mark
     * ({@code skinId}); anyone else by {@code signed}, the name the line
     * was signed with. A player whose account is called Server is shown
     * by their account.
     */
    public static String sender(LostTalesWords words, UUID senderId,
                                String skinId, String signed) {
        if (LostTalesChatMessagePacket.isServerSender(senderId)) {
            return server(words);
        }
        if (LostTalesChatMessagePacket.isClientSender(senderId)) {
            return client(words);
        }
        if (ChatNarrator.SENDER_ID.equals(senderId)
                || ChatNarrator.isNarratorSkin(skinId)) {
            return narrator(words);
        }
        return signed == null ? "" : signed;
    }
}
