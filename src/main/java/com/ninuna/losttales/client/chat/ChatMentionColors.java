package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.compat.lotr.LotrFactionColors;
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
     * The colour a mention of an account this client cannot place is
     * drawn in: its primary role's colour where its roles are known, the
     * shared accent until they are.
     */
    static int accountColorOf(String account) {
        int roleColor = roleColorFor(account);
        return roleColor >= 0 ? roleColor : PLAYER_RGB;
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
            if (name.equalsIgnoreCase(role.getDisplayName())) {
                return role.getColor();
            }
        }
        String account = accountFor(name);
        if (account == null) {
            return -1;
        }
        int roleColor = roleColorFor(account);
        int accountColor = roleColor >= 0 ? roleColor : PLAYER_RGB;
        // The same rule a sender's own name follows on the channel: out
        // of character the primary role colours the name, in character
        // the character's faction does — with the account's colour
        // standing in when this client knows no character for the name.
        return ChatRolePresentation.showsRoles(channel)
                ? accountColor : characterColorFor(account, accountColor);
    }

    /**
     * The colour a completion row is drawn in: a role its own; a player
     * the same colour {@link #colorOf(String, ChatChannel)} gives their
     * name on the channel, except that in character the faction is read
     * from the synced appearance the candidate's ids name, so two
     * characters of one name, or a name the appearance store has not
     * indexed, still colour right. -1 when nothing resolves.
     */
    static int colorOf(ChatMentionCandidate candidate, ChatChannel channel) {
        if (candidate == null) {
            return -1;
        }
        if (candidate.isRole()) {
            return candidate.getRoleColor();
        }
        if (!LostTalesConfig.enableChatPings
                || ChatRolePresentation.showsRoles(channel)
                || candidate.getCharacterId().length() == 0) {
            return colorOf(candidate.getDisplayName(), channel);
        }
        CharacterAppearance appearance = appearanceOf(candidate);
        if (appearance == null) {
            return colorOf(candidate.getDisplayName(), channel);
        }
        int roleColor = roleColorFor(candidate.getAccountName(),
                appearance.getCharacterId());
        return LotrFactionColors.forFactionId(appearance.getStartingFactionId(),
                roleColor >= 0 ? roleColor : PLAYER_RGB);
    }

    /**
     * The synced appearance the candidate's ids name — the account's,
     * while it still wears the candidate's character — or null.
     */
    private static CharacterAppearance appearanceOf(ChatMentionCandidate candidate) {
        UUID accountId;
        try {
            accountId = UUID.fromString(candidate.getAccountId());
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
        CharacterAppearance appearance = ClientCharacterAppearanceCache.get(accountId);
        return appearance != null && appearance.hasCharacter()
                && appearance.getCharacterId() != null
                && candidate.getCharacterId().equalsIgnoreCase(
                        appearance.getCharacterId().toString())
                ? appearance : null;
    }

    /**
     * The account's primary role colour, or -1 for none; see
     * {@link #rolesFor}.
     */
    private static int roleColorFor(String account) {
        ChatAccountRole primary = ChatAccountRole.primary(rolesFor(account));
        return primary.isNone() ? -1 : primary.getColor();
    }

    /** A character identity's primary role colour, or -1 for none. */
    private static int roleColorFor(String account, UUID characterId) {
        ChatAccountRole primary = ChatAccountRole.primary(
                rolesFor(account, characterId));
        return primary.isNone() ? -1 : primary.getColor();
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
     * mention colours and the player card both read this.
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
                    : LotrFactionColors.forFactionId(
                            active.getStartingFactionId(), fallback);
        }
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance != null && appearance.hasCharacter()
                    && account.equalsIgnoreCase(
                            appearance.getAccountName())) {
                return LotrFactionColors.forFactionId(
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
