package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Which of the player's identities the chat speaks as: the Minecraft
 * account, or any character of their roster — ordinary or lore. Every
 * channel has a default — the account channels the account, the
 * role-playing channels the active character — and switching channels
 * falls back to that default, unless the choice is locked, in which case
 * it holds until it is unlocked. The choice is presentation only and
 * client-held: the server validates the character against the sender's
 * own roster before signing a line with it, so nothing here is trusted.
 */
final class ClientChatAppearances {
    /** One choosable identity: the account, or one roster character. */
    static final class Appearance {
        /** True for the Minecraft account. */
        final boolean account;
        /** Roster character id; null for the account. */
        final UUID characterId;
        final String name;
        /** Skin snapshot for the head; empty for the account. */
        final String skinId;

        Appearance(boolean account, UUID characterId, String name,
                   String skinId) {
            this.account = account;
            this.characterId = characterId;
            this.name = name == null ? "" : name;
            this.skinId = skinId == null ? "" : skinId;
        }
    }

    /** The explicit choice, or null while the channel default applies. */
    private static Appearance selected;
    /** A locked choice survives channel switches until it is unlocked. */
    private static boolean locked;

    private ClientChatAppearances() {}

    static synchronized void select(Appearance appearance) {
        selected = appearance;
    }

    static synchronized boolean isLocked() {
        return locked;
    }

    /**
     * Locking holds the current appearance across channel switches; to
     * make the hold predictable, locking with no explicit choice pins
     * whatever the selected channel currently defaults to.
     */
    static synchronized void toggleLocked(ChatTab tab) {
        locked = !locked;
        if (locked && selected == null) {
            selected = effectiveFor(tab);
        }
    }

    /** A channel switch drops an unlocked choice back to the default. */
    static synchronized void onChannelSwitched() {
        if (!locked) {
            selected = null;
        }
    }

    /**
     * The identity the given tab would currently speak as: the explicit
     * choice while it still names a character the roster holds, else the
     * channel's default — the active character on the role-playing
     * channels, the account everywhere else and whenever no character is
     * active.
     */
    static synchronized Appearance effectiveFor(ChatTab tab) {
        Appearance explicit = validSelection();
        if (explicit != null) {
            return explicit;
        }
        if (tab != null && !tab.isNpc() && tab.getChannel() != null
                && tab.getChannel().getIdentityType()
                        == ChatIdentityType.CHARACTER) {
            CharacterSummary active = activeCharacter();
            if (active != null) {
                return of(active);
            }
        }
        return accountAppearance();
    }

    /** The explicit choice for the wire, as the send packet encodes it. */
    static synchronized int wireKind() {
        Appearance explicit = validSelection();
        if (explicit == null) {
            return LostTalesChatSendPacket.APPEARANCE_DEFAULT;
        }
        return explicit.account
                ? LostTalesChatSendPacket.APPEARANCE_ACCOUNT
                : LostTalesChatSendPacket.APPEARANCE_CHARACTER;
    }

    static synchronized UUID wireCharacterId() {
        Appearance explicit = validSelection();
        return explicit == null ? null : explicit.characterId;
    }

    /** Whether the appearance is this tab's current effective one. */
    static synchronized boolean isEffective(Appearance appearance,
                                            ChatTab tab) {
        Appearance effective = effectiveFor(tab);
        if (appearance == null || effective == null) {
            return false;
        }
        if (appearance.account || effective.account) {
            return appearance.account == effective.account;
        }
        return effective.characterId.equals(appearance.characterId);
    }

    /**
     * A choice whose character has left the roster — deleted, or a lore
     * character handed on — silently gives way to the default.
     */
    private static Appearance validSelection() {
        if (selected == null || selected.account) {
            return selected;
        }
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        if (roster == null
                || roster.getCharacter(selected.characterId) == null) {
            return null;
        }
        return selected;
    }

    static Appearance accountAppearance() {
        Minecraft minecraft = Minecraft.getMinecraft();
        String name = minecraft == null || minecraft.thePlayer == null
                ? "" : minecraft.thePlayer.getCommandSenderName();
        return new Appearance(true, null, name, "");
    }

    /** The roster's ordinary characters, the lore-owned ones left out. */
    static List<Appearance> characterAppearances() {
        List<Appearance> result = new ArrayList<Appearance>();
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        if (roster == null) {
            return result;
        }
        for (CharacterSummary summary : roster.getCharacters()) {
            if (summary != null && ClientLoreCharacterCache
                    .findOwnedCharacter(summary.getCharacterId()) == null) {
                result.add(of(summary));
            }
        }
        return result;
    }

    /** The lore characters this player currently holds. */
    static List<Appearance> loreAppearances() {
        List<Appearance> result = new ArrayList<Appearance>();
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        if (roster == null) {
            return result;
        }
        for (CharacterSummary summary : roster.getCharacters()) {
            if (summary != null && ClientLoreCharacterCache
                    .findOwnedCharacter(summary.getCharacterId()) != null) {
                result.add(of(summary));
            }
        }
        return result;
    }

    private static Appearance of(CharacterSummary summary) {
        return new Appearance(false, summary.getCharacterId(),
                summary.getName(), summary.getSkinId());
    }

    private static CharacterSummary activeCharacter() {
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        return roster == null ? null : roster.getActiveCharacter();
    }

    /** The conversation ends with the world, and so does the choice. */
    static synchronized void clear() {
        selected = null;
        locked = false;
    }
}
