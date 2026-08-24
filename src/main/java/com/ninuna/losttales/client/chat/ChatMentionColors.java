package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.util.StatCollector;

/**
 * What colour an {@code @mention} inside a message is drawn in.
 *
 * <p>Only the mention itself is coloured, never the words around it: a
 * line reads as ordinary text with the names in it standing out, the way
 * a mention does anywhere else. A role is drawn in its own colour; a
 * player in the shared mention honey, since a line carries the sender's
 * colours and not the colours of everyone it names.</p>
 *
 * <p>Resolution is local and at display time: each client asks its own
 * player list and appearance cache, exactly as it asks its own names when
 * deciding whether a line mentions it. Nothing about a mention travels on
 * the wire, so no client can make another client colour a word.</p>
 */
final class ChatMentionColors {
    /** Every player mention shares one accent; roles wear their own. */
    private static final int PLAYER_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);

    private ChatMentionColors() {}

    /** Whether a character may be part of the name after an {@code @}. */
    static boolean isMentionCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    /**
     * The colour the named mention is drawn in, or -1 when the name
     * reaches nobody and the text stays as it was typed. Roles answer
     * only on the account channels, where they mean something.
     */
    static int colorOf(String name, ChatChannel channel) {
        if (!LostTalesConfig.enableChatPings || name == null
                || name.length() == 0) {
            return -1;
        }
        if (channel != null
                && channel.getIdentityType() == ChatIdentityType.ACCOUNT) {
            for (ChatAccountRole role : ChatAccountRole.mentionable()) {
                if (name.equalsIgnoreCase(StatCollector.translateToLocal(
                        role.getNameKey()))) {
                    return role.getColor();
                }
            }
        }
        if (!namesAPlayer(name)) {
            return -1;
        }
        // A player who wears a role is named in it, so one name reads
        // the same in a message, in the completion list and in the bar.
        int role = ClientChatAccountRoles.colorOf(name);
        return role >= 0 ? role : PLAYER_RGB;
    }

    /**
     * The colour a name already known to the chat is drawn in, without
     * asking whether it is online: what the completion list and the
     * input bar need for a name they are already showing.
     */
    static int colorOfKnown(String name) {
        int role = ClientChatAccountRoles.colorOf(name);
        return role >= 0 ? role : LostTalesChatVisualStyle.IVORY;
    }

    /** Whether the name is an online player's account or character name. */
    private static boolean namesAPlayer(String name) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return false;
        }
        String wanted = name.toLowerCase(Locale.ROOT);
        if (minecraft.thePlayer.sendQueue != null
                && minecraft.thePlayer.sendQueue.playerInfoList != null) {
            for (Object value
                    : minecraft.thePlayer.sendQueue.playerInfoList) {
                if (value instanceof GuiPlayerInfo
                        && ((GuiPlayerInfo)value).name != null
                        && ((GuiPlayerInfo)value).name
                                .toLowerCase(Locale.ROOT).equals(wanted)) {
                    return true;
                }
            }
        }
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance != null && appearance.isPresent()
                    && appearance.getCharacterName()
                            .toLowerCase(Locale.ROOT).equals(wanted)) {
                return true;
            }
        }
        return false;
    }
}
