package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
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
 * a mention does anywhere else. A mention wears the colour of the
 * identity it names, exactly as that identity signs its own lines: a
 * role its own colour, an account its primary role's, and a mention that
 * names a character — by the character's name, or by the account on a
 * channel whose lines are signed in character — the character's faction
 * colour. Only when none of that can be resolved does the shared mention
 * honey stand in, so a mention is never invisible.</p>
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
    /** The stand-in accent when no identity colour can be resolved. */
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
     * in every channel: an operator is worth calling wherever the call
     * is made.
     */
    static int colorOf(String name, ChatChannel channel) {
        if (!LostTalesConfig.enableChatPings || name == null
                || name.length() == 0) {
            return -1;
        }
        for (ChatAccountRole role : ChatAccountRole.mentionable()) {
            if (name.equalsIgnoreCase(StatCollector.translateToLocal(
                    role.getNameKey()))) {
                return role.getColor();
            }
        }
        String account = accountFor(name);
        if (account == null) {
            return -1;
        }
        int roleColor = roleColorFor(account);
        int accountColor = roleColor >= 0 ? roleColor : PLAYER_RGB;
        // The mention names the character when it uses the character's
        // name, and on the channels whose lines are signed in character
        // even when it uses the account's: either way the identity on
        // display is the character, so its faction colour is the
        // mention's — with the account's colour standing in when this
        // client knows no character for the name.
        boolean characterIdentity = !name.equalsIgnoreCase(account)
                || (channel != null && channel.getIdentityType()
                        == ChatIdentityType.CHARACTER);
        return characterIdentity
                ? characterColorFor(account, accountColor) : accountColor;
    }

    /**
     * The account's primary role colour, or -1 for none: what the roles
     * store has seen the name signed with, what the server's role
     * roster lists it as, and for the local player what the server
     * granted with the chat access — the first of them that answers, so
     * a role holder is coloured before they have said anything.
     */
    private static int roleColorFor(String account) {
        int known = ClientChatAccountRoles.colorOf(account);
        if (known >= 0) {
            return known;
        }
        ChatAccountRole listed = ChatAccountRole.primary(
                ClientChatChannelState.rosterRolesOf(account));
        if (listed != ChatAccountRole.NONE) {
            return listed.getColor();
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.thePlayer != null
                && account.equalsIgnoreCase(
                        minecraft.thePlayer.getCommandSenderName())) {
            ChatAccountRole primary = ChatAccountRole.primary(
                    ClientChatChannelState.getRoleMask());
            return primary == ChatAccountRole.NONE ? -1 : primary.getColor();
        }
        return -1;
    }

    /**
     * The colour a mention marker is drawn with right now: its baked
     * colour, upgraded from the shared fallback once the roles behind
     * the name are known — a line built moments before the access
     * roster or the holder's first line arrived would otherwise keep
     * honey forever. Cheap map lookups only, since the renderer asks
     * every frame; the character-colour half of the resolution stays
     * baked.
     */
    static Integer liveMentionColor(ChatMentionMarker.Data mention) {
        if (mention == null) {
            return null;
        }
        if (mention.color == PLAYER_RGB && mention.role() == null) {
            int role = roleColorFor(mention.account);
            if (role >= 0) {
                return Integer.valueOf(role);
            }
        }
        return Integer.valueOf(mention.color);
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
            if (name.equalsIgnoreCase(StatCollector.translateToLocal(
                    role.getNameKey()))) {
                return role;
            }
        }
        return null;
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

    /**
     * The active role-playing character's name for an online account, or
     * null when this client knows none. The local player's own roster is
     * the authority for their own character; everyone else's comes from
     * the appearance the server syncs for every online player. The
     * character channels sign their lines with this identity, so a
     * system line naming the account is shown the same way.
     */
    static String characterNameFor(String account) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (account == null || account.length() == 0) {
            return null;
        }
        if (minecraft != null && minecraft.thePlayer != null
                && account.equalsIgnoreCase(
                        minecraft.thePlayer.getCommandSenderName())) {
            CharacterRosterSnapshot snapshot =
                    ClientCharacterRosterCache.getSnapshot();
            CharacterSummary active = snapshot == null
                    ? null : snapshot.getActiveCharacter();
            return active == null ? null : normalized(active.getName());
        }
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance != null && appearance.isPresent()
                    && account.equalsIgnoreCase(
                            appearance.getAccountName())) {
                return normalized(appearance.getCharacterName());
            }
        }
        return null;
    }

    private static String normalized(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    /**
     * The colour the account's active role-playing character signs its
     * lines with — its starting faction's, exactly as the server
     * resolves it for the character's own messages — or {@code fallback}
     * when this client knows no active character or no faction for the
     * account. A system line shown under the character's name wears
     * this, so the mention and the character's own lines read alike.
     */
    private static int characterColorFor(String account, int fallback) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (account == null || account.length() == 0) {
            return fallback;
        }
        if (minecraft != null && minecraft.thePlayer != null
                && account.equalsIgnoreCase(
                        minecraft.thePlayer.getCommandSenderName())) {
            CharacterRosterSnapshot snapshot =
                    ClientCharacterRosterCache.getSnapshot();
            CharacterSummary active = snapshot == null
                    ? null : snapshot.getActiveCharacter();
            return active == null ? fallback
                    : LotrCharacterAdapter.getInstance().getFactionColor(
                            active.getStartingFactionId(), fallback);
        }
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance != null && appearance.isPresent()
                    && account.equalsIgnoreCase(
                            appearance.getAccountName())) {
                return LotrCharacterAdapter.getInstance().getFactionColor(
                        appearance.getStartingFactionId(), fallback);
            }
        }
        return fallback;
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
            if (appearance != null && appearance.isPresent()
                    && appearance.getCharacterName()
                            .toLowerCase(Locale.ROOT).equals(wanted)
                    && appearance.getAccountName().length() > 0) {
                return appearance.getAccountName();
            }
        }
        return null;
    }
}
