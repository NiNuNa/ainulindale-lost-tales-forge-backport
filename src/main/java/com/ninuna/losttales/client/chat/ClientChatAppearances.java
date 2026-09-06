package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Which of the player's identities the chat speaks as, tab by tab.
 *
 * <p>There is one identity being played — the roster's active character,
 * or the account — and every tab follows it by default: the
 * role-playing channels speak as the active character, the account
 * channels as the account. A tab can be <em>locked</em> to another of
 * the player's identities, the account or any character of their
 * roster, and then keeps that identity however the active character
 * changes, until it is unlocked and follows the active one again. A
 * lock belongs to the tab it was set on and to no other: Global locked
 * to one character leaves Proximity following the active one.</p>
 *
 * <p>A choice made without locking is a passing one: it holds for the
 * tab it was made on until another tab is selected, then the default
 * applies again. A lock or a choice naming a character the roster no
 * longer holds — deleted, or a lore character handed on — gives way to
 * the default the moment it is read.</p>
 *
 * <p>Presentation only and client-held: the server validates the
 * character against the sender's own roster before signing a line with
 * it, so nothing here is trusted. The state ends with the world.</p>
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

    /** The identity each locked tab holds. */
    private static final Map<ChatTab, Appearance> LOCKS =
            new HashMap<ChatTab, Appearance>();
    /** A passing choice, and the tab it was made on; null for none. */
    private static Appearance pending;
    private static ChatTab pendingTab;

    private ClientChatAppearances() {}

    /**
     * Chooses the identity the tab speaks as. On a locked tab the lock
     * itself takes the new identity; on any other the choice holds until
     * the tab is switched.
     */
    static synchronized void select(Appearance appearance, ChatTab tab) {
        if (appearance == null || tab == null) {
            return;
        }
        if (LOCKS.containsKey(tab)) {
            LOCKS.put(tab, appearance);
            return;
        }
        pending = appearance;
        pendingTab = tab;
    }

    /** Whether the tab is locked to an identity of its own. */
    static synchronized boolean isLocked(ChatTab tab) {
        return tab != null && validLock(tab) != null;
    }

    /**
     * Locks the tab to whatever it speaks as right now, so the identity
     * holds however the active character changes; or unlocks it, so it
     * follows the active identity again. A passing choice on the tab
     * becomes the lock, and is no longer passing.
     */
    static synchronized void toggleLocked(ChatTab tab) {
        if (tab == null) {
            return;
        }
        if (validLock(tab) != null) {
            LOCKS.remove(tab);
            return;
        }
        LOCKS.put(tab, effectiveFor(tab));
        if (tab.equals(pendingTab)) {
            pending = null;
            pendingTab = null;
        }
    }

    /** A tab switch ends a passing choice; every lock stays where it is. */
    static synchronized void onChannelSwitched() {
        pending = null;
        pendingTab = null;
    }

    /**
     * The identity the tab speaks as right now: its lock, else the
     * passing choice made on it, else the default — the active
     * character on the role-playing channels, the account everywhere
     * else and whenever no character is active.
     */
    static synchronized Appearance effectiveFor(ChatTab tab) {
        Appearance explicit = explicitFor(tab);
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

    /** The tab's explicit identity for the wire, as the send packet encodes it. */
    static synchronized int wireKind(ChatTab tab) {
        Appearance explicit = explicitFor(tab);
        if (explicit == null) {
            return LostTalesChatSendPacket.APPEARANCE_DEFAULT;
        }
        return explicit.account
                ? LostTalesChatSendPacket.APPEARANCE_ACCOUNT
                : LostTalesChatSendPacket.APPEARANCE_CHARACTER;
    }

    static synchronized UUID wireCharacterId(ChatTab tab) {
        Appearance explicit = explicitFor(tab);
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
     * The identity the tab was explicitly given, lock first, or null
     * when the default applies. A choice whose character has left the
     * roster is dropped here, so a stale lock never outlives the
     * character it named.
     */
    private static Appearance explicitFor(ChatTab tab) {
        if (tab == null) {
            return null;
        }
        Appearance lock = validLock(tab);
        if (lock != null) {
            return lock;
        }
        if (tab.equals(pendingTab)) {
            if (isHeld(pending)) {
                return pending;
            }
            pending = null;
            pendingTab = null;
        }
        return null;
    }

    /** The tab's lock while the roster still holds its character; else none, and it is forgotten. */
    private static Appearance validLock(ChatTab tab) {
        Appearance lock = LOCKS.get(tab);
        if (lock == null) {
            return null;
        }
        if (!isHeld(lock)) {
            LOCKS.remove(tab);
            return null;
        }
        return lock;
    }

    /** Whether the appearance still names an identity the player has. */
    private static boolean isHeld(Appearance appearance) {
        if (appearance == null) {
            return false;
        }
        if (appearance.account) {
            return true;
        }
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        return roster != null
                && roster.getCharacter(appearance.characterId) != null;
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

    /** The conversation ends with the world, and so do the locks. */
    static synchronized void clear() {
        LOCKS.clear();
        pending = null;
        pendingTab = null;
    }
}
