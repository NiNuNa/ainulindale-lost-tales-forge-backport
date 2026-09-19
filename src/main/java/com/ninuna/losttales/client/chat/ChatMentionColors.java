package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerInfo;

/**
 * What colour an {@code @mention} inside a message is drawn in.
 *
 * <p>Only the mention itself is coloured, never the words around it: a
 * line reads as ordinary text with the names in it standing out, the way
 * a mention does anywhere else. A mention of a player wears the one
 * mention colour, the palette's honey, whoever it names — an account or a
 * character, with a role or without — and a mention of a role wears that
 * role's own colour exactly (Nils, 2026-09-19): what marks a mention is
 * its colour, never the colour of the identity it reaches. The completion
 * rows say it the same way: a role in its colour, every player in ivory
 * ({@link #rowColorOf}).</p>
 *
 * <p>Resolution is local and at display time: each client asks its own
 * player list and appearance cache, exactly as it asks its own names when
 * deciding whether a line mentions it. Nothing about a mention travels on
 * the wire, so no client can make another client colour a word. Every
 * mention path — the message body, system lines, the input bar's preview
 * — resolves through {@link #colorOf}, so one name reads the same
 * wherever it appears.</p>
 */
final class ChatMentionColors {
    /** The one colour a mention of a player is drawn in. */
    static final int PLAYER_RGB = LostTalesColors.rgb(LostTalesColors.HONEY);

    private ChatMentionColors() {}

    /** Whether a character may be part of the name after an {@code @}. */
    static boolean isMentionCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    /**
     * The colour the named mention is drawn in, or -1 when the name
     * reaches nobody and the text stays as it was typed: a role's own
     * colour, and the one mention colour for any player. Roles answer in
     * every channel: an operator is worth calling wherever the call is
     * made.
     */
    static int colorOf(String name) {
        if (!LostTalesConfig.enableChatPings || name == null
                || name.length() == 0) {
            return -1;
        }
        ChatAccountRole role = roleFor(name);
        if (role != null) {
            return role.getColor();
        }
        return accountFor(name) == null ? -1 : PLAYER_RGB;
    }

    /**
     * The colour a completion row names its candidate in: a role its own
     * colour, and every player ivory, as a mention of any player is one
     * colour whoever it names (Nils, 2026-09-19: "for pings only roles
     * have colours").
     */
    static int rowColorOf(ChatMentionCandidate candidate) {
        return candidate != null && candidate.isRole()
                ? candidate.getRoleColor()
                : LostTalesColors.rgb(LostTalesColors.IVORY);
    }

    /**
     * Every role this client knows the account to hold on its own — worn
     * by the account and by every character of it — or zero: what the
     * server's role roster lists it as, else what the roles store has
     * seen the name signed with, and for the local player what the
     * server granted with the chat access — the first of them that
     * answers, so a role holder is coloured before they have said
     * anything. The roster comes first because it is the server's
     * current word, while a remembered line may be an evening old. The
     * completion rows and the player card both read this.
     */
    static int rolesFor(String account) {
        if (account == null || account.trim().length() == 0) {
            return 0;
        }
        int listed = ClientChatChannelState.rosterAccountRolesOf(account);
        if (listed != 0) {
            return listed;
        }
        int known = ClientChatAccountRoles.rolesOf(account);
        if (known != 0) {
            return known;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.thePlayer != null
                && account.trim().equalsIgnoreCase(
                        minecraft.thePlayer.getCommandSenderName())) {
            return ClientChatChannelState.getAccountRoleMask();
        }
        return 0;
    }

    /**
     * Every role this client knows a character identity to wear: the
     * account's own together with the character's — for the local
     * player's own characters, and for the character each roster holder
     * is playing, as the server stated them. For any other character of
     * another player only the account's roles can be known, and those
     * are what it shows.
     */
    static int rolesFor(String account, UUID characterId) {
        if (characterId == null) {
            return rolesFor(account);
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.thePlayer != null
                && account != null
                && account.trim().equalsIgnoreCase(
                        minecraft.thePlayer.getCommandSenderName())) {
            return ClientChatChannelState.ownCharacterRoles(characterId);
        }
        int worn = ClientChatChannelState.rosterRolesOf(account, characterId);
        return worn != 0 ? worn : rolesFor(account);
    }

    /**
     * The name of the character with the id, as the synced appearances
     * know it; null for none.
     */
    static String characterNameOf(UUID characterId) {
        if (characterId == null) {
            return null;
        }
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance != null && appearance.hasCharacter()
                    && characterId.equals(appearance.getCharacterId())) {
                return normalized(appearance.getCharacterName());
            }
        }
        return null;
    }

    /**
     * The mentionable role the name addresses, or null — in every
     * channel, the same rule {@link #colorOf} colours them by.
     */
    static ChatAccountRole roleFor(String name) {
        if (name == null || name.length() == 0) {
            return null;
        }
        for (ChatAccountRole role : ChatAccountRole.mentionable()) {
            if (name.equalsIgnoreCase(role.getDisplayName())) {
                return role;
            }
        }
        return null;
    }

    /**
     * The active role-playing character's name for an online account, or
     * null when this client knows none. The character channels sign
     * their lines with this identity, so a system line naming the
     * account is shown the same way.
     */
    static String characterNameFor(String account) {
        return ClientCharacterAppearanceCache.characterNameFor(account);
    }

    private static String normalized(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    /**
     * The account the name reaches — the name itself when it is an
     * online account, the owning account when it is a synced character
     * name — or null when it names nobody this client can place.
     */
    static String accountFor(String name) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null
                || name == null || name.length() == 0) {
            return null;
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
                    return ((GuiPlayerInfo)value).name;
                }
            }
        }
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance != null && appearance.hasCharacter()
                    && appearance.getCharacterName()
                            .toLowerCase(Locale.ROOT).equals(wanted)
                    && appearance.getAccountName().length() > 0) {
                return appearance.getAccountName();
            }
        }
        return null;
    }
}
