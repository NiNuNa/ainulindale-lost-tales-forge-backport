package com.ninuna.losttales.client.chat;

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
 * <p>A lock naming a character the roster no longer holds — deleted,
 * or a lore character handed on — gives way to the default the
 * moment it is read.</p>
 *
 * <p>Beside the tab it speaks as, the chat is <em>read</em> as one
 * identity: the one whose conversations are on screen. Picking an
 * identity sets it, and it decides which whispers are shown and which
 * conversation of a scoped channel — the Faction tab above all — is
 * the one being read. It is not the character being played: choosing
 * it changes nothing in the world, and the character switch screen is
 * the only thing that does. Left alone it follows the character being
 * played, which is what it was before anyone chose.</p>
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
    /**
     * The identity the chat is read as; null while it follows the
     * character being played.
     */
    private static Appearance viewing;

    private ClientChatAppearances() {}

    /**
     * Chooses the identity the chat is read and spoken as. Every tab
     * follows it but the locked ones, which keep the identity they were
     * pinned to; a locked tab picked on takes the new identity as its
     * lock, since choosing on a tab is choosing for that tab first.
     */
    static synchronized void select(Appearance appearance, ChatTab tab) {
        if (appearance == null || tab == null) {
            return;
        }
        // Picking an identity is picking who the player is in the chat:
        // the conversations shown are that identity's from here on. The
        // tab it was picked on speaks as it too, unless the tab holds an
        // identity of its own.
        viewing = appearance;
        if (LOCKS.containsKey(tab)) {
            LOCKS.put(tab, appearance);
        }
    }

    /**
     * The identity the chat is read as: the one last picked while the
     * roster still holds it, else the character being played, else the
     * account. What decides which conversations are on screen.
     */
    static synchronized Appearance viewing() {
        if (viewing != null && isHeld(viewing)) {
            return viewing;
        }
        viewing = null;
        CharacterSummary active = activeCharacter();
        return active == null ? accountAppearance() : of(active);
    }

    /**
     * The key of the identity the chat is read as: a character's id, or
     * empty for the account. A tab holding another identity is not this
     * player's to read right now.
     */
    public static synchronized String viewIdentityKey() {
        // The roster alone answers this: a tab is resolved through it on
        // every draw, so it must never reach for the running game.
        if (viewing != null && isHeld(viewing)) {
            return viewing.account ? ""
                    : ChatTab.ownerKeyOf(viewing.characterId);
        }
        viewing = null;
        return activeIdentityKey();
    }

    /** Reads the chat as the character being played again. */
    static synchronized void followThePlayedIdentity() {
        viewing = null;
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
    }

    /**
     * A tab switch changes nothing about who the player is: the
     * identity they read and speak as is theirs until they pick another,
     * and every lock stays where it is.
     */
    static synchronized void onChannelSwitched() {
    }

    /**
     * The identity the tab speaks as right now: its lock, else the
     * passing choice made on it, else the identity the chat is being
     * read as — which is the character being played until one is
     * picked, and the account whenever there is none.
     */
    static synchronized Appearance effectiveFor(ChatTab tab) {
        Appearance explicit = explicitFor(tab);
        if (explicit != null) {
            return explicit;
        }
        if (tab != null && !tab.isNpc() && tab.getChannel() != null) {
            return viewing();
        }
        return accountAppearance();
    }

    /**
     * The identity the wire states for a line typed here: the tab's own
     * when it holds one, else the identity the player picked. Left
     * unpicked it states none, and the server signs the line with the
     * character being played — which is also what keeps a send safe
     * when the roster cannot be read, since only the server can say
     * then whether there is a character at all.
     */
    static synchronized int wireKind(ChatTab tab) {
        Appearance stated = statedFor(tab);
        if (stated == null) {
            return LostTalesChatSendPacket.APPEARANCE_DEFAULT;
        }
        return stated.account
                ? LostTalesChatSendPacket.APPEARANCE_ACCOUNT
                : LostTalesChatSendPacket.APPEARANCE_CHARACTER;
    }

    static synchronized UUID wireCharacterId(ChatTab tab) {
        Appearance stated = statedFor(tab);
        return stated == null ? null : stated.characterId;
    }

    /** The identity chosen for this line, or null to follow the played one. */
    private static Appearance statedFor(ChatTab tab) {
        Appearance explicit = explicitFor(tab);
        if (explicit != null) {
            return explicit;
        }
        return viewing != null && isHeld(viewing) ? viewing : null;
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
     * The identity the tab holds of its own — a conversation's own, or
     * a lock — or null when it follows the identity being read as. A
     * lock whose character has left the roster is dropped here, so it
     * never outlives the character it named.
     */
    private static Appearance explicitFor(ChatTab tab) {
        if (tab == null) {
            return null;
        }
        if (tab.isWhisper() && !tab.isNpc()) {
            // A conversation is held as one identity and spoken in as
            // that identity: the tab's own key decides, not a choice.
            Appearance held = heldAs(tab.getOwnerKey());
            if (held != null) {
                return held;
            }
        }
        return validLock(tab);
    }

    /**
     * The identity a whisper tab's own key names: the account for an
     * empty key, the roster's character for its id, or null when the
     * roster no longer holds that character.
     */
    private static Appearance heldAs(String ownerKey) {
        if (ownerKey == null || ownerKey.length() == 0) {
            return accountAppearance();
        }
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        if (roster == null) {
            return null;
        }
        UUID characterId;
        try {
            characterId = UUID.fromString(ownerKey);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
        CharacterSummary summary = roster.getCharacter(characterId);
        return summary == null ? null : of(summary);
    }

    /**
     * The key of the character being played, or empty for the account.
     * Read from the roster cache alone, so any lock may be held while
     * calling it. What a conversation is held under is
     * {@link #viewIdentityKey}, which is this until an identity is
     * picked.
     */
    public static String activeIdentityKey() {
        CharacterSummary active = activeCharacter();
        return ChatTab.ownerKeyOf(active == null ? null
                : active.getCharacterId());
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
        viewing = null;
    }
}
