package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.chat.ChatRolePresentation;
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
 * <p>The channel decides the default. An in-character channel — Global,
 * Proximity, Faction, Party — speaks as the identity the chat is read
 * as, which is the roster's active character until another is picked;
 * an out-of-character channel — OOC &amp; Discord, Operator, the Console
 * — speaks as the account. A choice made on a tab without locking it
 * is a <em>passing</em> one: it holds for that tab until another tab is
 * selected. A tab can be <em>locked</em> to any of the player's
 * identities, the account or any character of their roster, and then
 * keeps that identity however the active character changes, until it
 * is unlocked and follows its channel's default again. A lock belongs
 * to the tab it was set on and to no other: Global locked to one
 * character leaves Proximity following the active one.</p>
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
     * Locks read from the layout file and not yet resolved against a
     * roster: the tab, and the character id it was pinned to, or null
     * for the account. Resolved into {@link #LOCKS} the moment a roster
     * can name the character, and kept as they are until one can.
     */
    private static final Map<ChatTab, UUID> PENDING_LOCKS =
            new HashMap<ChatTab, UUID>();
    /** The locks as the file holds them, kept so a new session starts from them. */
    private static final Map<String, String> STORED_LOCKS =
            new java.util.TreeMap<String, String>();
    /** The key a stored lock names the account by. */
    static final String ACCOUNT_KEY = "account";
    /** Whether the hint about the channel's default identity has been shown. */
    private static boolean identityHintShown;
    /**
     * The identity the chat is read as; null while it follows the
     * character being played.
     */
    private static Appearance viewing;
    /** The tab a passing choice was made on, and the identity chosen. */
    private static ChatTab passingTab;
    private static Appearance passing;

    private ClientChatAppearances() {}

    /**
     * Chooses the identity the chat is read as, and the one the tab it
     * was picked on speaks as. A locked tab takes the new identity as
     * its lock; any other tab keeps it as a passing choice, which holds
     * until another tab is selected. Every other unlocked tab keeps its
     * channel's default: the in-character ones follow the identity now
     * being read as, the out-of-character ones stay with the account.
     */
    static synchronized void select(Appearance appearance, ChatTab tab) {
        if (appearance == null || tab == null) {
            return;
        }
        // Picking an identity is picking who the player is in the chat:
        // the conversations shown are that identity's from here on.
        viewing = appearance;
        if (LOCKS.containsKey(tab)) {
            LOCKS.put(tab, appearance);
            return;
        }
        passingTab = tab;
        passing = appearance;
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
            PENDING_LOCKS.remove(tab);
            storeLocks();
            return;
        }
        LOCKS.put(tab, effectiveFor(tab));
        if (tab.equals(passingTab)) {
            passingTab = null;
            passing = null;
        }
        storeLocks();
    }

    /**
     * Writes the locks into the layout file's picture of them and asks
     * for the file to be written: a lock outlives the session, like the
     * rest of the layout. Only a tab the file can name is kept — a
     * conversation holds its identity of its own and needs no lock.
     */
    private static void storeLocks() {
        STORED_LOCKS.clear();
        for (Map.Entry<ChatTab, Appearance> entry : LOCKS.entrySet()) {
            ChatTab tab = entry.getKey();
            if (tab == null || tab.isWhisper() || entry.getValue() == null
                    || ChatTab.fromId(tab.id()) == null) {
                continue;
            }
            STORED_LOCKS.put(tab.id(), entry.getValue().account ? ACCOUNT_KEY
                    : ChatTab.ownerKeyOf(entry.getValue().characterId));
        }
        for (Map.Entry<ChatTab, UUID> entry : PENDING_LOCKS.entrySet()) {
            if (entry.getKey() != null
                    && !STORED_LOCKS.containsKey(entry.getKey().id())) {
                STORED_LOCKS.put(entry.getKey().id(), entry.getValue() == null
                        ? ACCOUNT_KEY : ChatTab.ownerKeyOf(entry.getValue()));
            }
        }
        ChatWindowLayout.persist();
    }

    /**
     * The locks as the layout file writes them: one {@code tab key}
     * pair per locked tab, sorted by tab id, the key {@link #ACCOUNT_KEY}
     * or a character id.
     */
    static synchronized List<String[]> describeLocks() {
        List<String[]> lines = new ArrayList<String[]>();
        for (Map.Entry<String, String> entry : STORED_LOCKS.entrySet()) {
            lines.add(new String[] {entry.getKey(), entry.getValue()});
        }
        return lines;
    }

    /**
     * Takes the locks the layout file holds, replacing whatever locks
     * stand: each is pinned to its tab at once, resolved to a name and
     * skin as soon as a roster can name its character, and dropped only
     * once a roster is known not to hold it.
     */
    static synchronized void restoreLocks(List<String[]> stored) {
        LOCKS.clear();
        PENDING_LOCKS.clear();
        STORED_LOCKS.clear();
        if (stored == null) {
            return;
        }
        for (String[] pair : stored) {
            if (pair == null || pair.length != 2 || pair[0] == null
                    || pair[1] == null) {
                continue;
            }
            ChatTab tab = ChatTab.fromId(pair[0]);
            if (tab == null || tab.isWhisper()) {
                continue;
            }
            UUID characterId = null;
            if (!ACCOUNT_KEY.equals(pair[1])) {
                try {
                    characterId = UUID.fromString(pair[1]);
                } catch (IllegalArgumentException malformed) {
                    continue;
                }
            }
            PENDING_LOCKS.put(tab, characterId);
            STORED_LOCKS.put(tab.id(), characterId == null ? ACCOUNT_KEY
                    : ChatTab.ownerKeyOf(characterId));
        }
    }

    static synchronized boolean wasIdentityHintShown() {
        return identityHintShown;
    }

    /** Remembers the hint was shown, and asks the layout file to keep it. */
    static synchronized void setIdentityHintShown(boolean shown) {
        boolean changed = identityHintShown != shown;
        identityHintShown = shown;
        if (changed && shown) {
            ChatWindowLayout.persist();
        }
    }

    /** As above without writing: what the layout file says. */
    static synchronized void restoreIdentityHintShown(boolean shown) {
        identityHintShown = shown;
    }

    /**
     * A tab switch ends the passing choice: the tab it was made on
     * follows its channel's default again. Who the chat is read as, and
     * every lock, stay as they are.
     */
    static synchronized void onChannelSwitched() {
        passingTab = null;
        passing = null;
    }

    /**
     * The identity the tab speaks as right now: its lock, else the
     * passing choice made on it, else its channel's default — the
     * identity the chat is read as on an in-character channel, the
     * account on an out-of-character one.
     */
    static synchronized Appearance effectiveFor(ChatTab tab) {
        Appearance explicit = explicitFor(tab);
        if (explicit != null) {
            return explicit;
        }
        Appearance chosen = passingFor(tab);
        if (chosen != null) {
            return chosen;
        }
        if (speaksInCharacter(tab)) {
            return viewing();
        }
        return accountAppearance();
    }

    /**
     * Whether the tab's channel is spoken in character, so it follows
     * the identity being read as rather than the account.
     */
    private static boolean speaksInCharacter(ChatTab tab) {
        return tab != null && !tab.isNpc() && tab.getChannel() != null
                && ChatRolePresentation.isInCharacter(tab.getChannel());
    }

    /**
     * The passing choice made on this tab while the roster still holds
     * its identity; else none, and it is forgotten.
     */
    private static Appearance passingFor(ChatTab tab) {
        if (tab == null || passing == null || !tab.equals(passingTab)) {
            return null;
        }
        if (!isHeld(passing)) {
            passingTab = null;
            passing = null;
            return null;
        }
        return passing;
    }

    /**
     * The identity the wire states for a line typed here: the tab's own
     * when it holds one, else the passing choice made on it, else the
     * account on an out-of-character channel, else the identity the
     * player picked. Left unpicked on an in-character channel it states
     * none, and the server signs the line with the character being
     * played — which is also what keeps a send safe when the roster
     * cannot be read, since only the server can say then whether there
     * is a character at all.
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
        Appearance chosen = passingFor(tab);
        if (chosen != null) {
            return chosen;
        }
        if (!speaksInCharacter(tab)) {
            return accountAppearance();
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

    /**
     * The tab's lock while the roster still holds its character; else
     * none, and it is forgotten. A lock read from the file waits here
     * until a roster can name its character, speaking as the id alone
     * meanwhile, and is dropped only by a roster that lacks it.
     */
    private static Appearance validLock(ChatTab tab) {
        Appearance lock = LOCKS.get(tab);
        if (lock == null && PENDING_LOCKS.containsKey(tab)) {
            UUID characterId = PENDING_LOCKS.get(tab);
            if (characterId == null) {
                lock = accountAppearance();
                LOCKS.put(tab, lock);
                PENDING_LOCKS.remove(tab);
                return lock;
            }
            CharacterRosterSnapshot roster =
                    ClientCharacterRosterCache.getSnapshot();
            if (roster == null) {
                return new Appearance(false, characterId, "", "");
            }
            CharacterSummary summary = roster.getCharacter(characterId);
            PENDING_LOCKS.remove(tab);
            if (summary == null) {
                storeLocks();
                return null;
            }
            lock = of(summary);
            LOCKS.put(tab, lock);
            return lock;
        }
        if (lock == null) {
            return null;
        }
        if (!isHeld(lock)) {
            LOCKS.remove(tab);
            storeLocks();
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

    /**
     * The conversation ends with the world, and so do the picks; the
     * locks are the layout file's and start the next session as it
     * holds them.
     */
    static synchronized void clear() {
        viewing = null;
        passingTab = null;
        passing = null;
        List<String[]> stored = describeLocks();
        restoreLocks(stored);
    }

    /** Forgets the locks and the hint flag too: what a layout with no file starts from. */
    static synchronized void forgetStored() {
        restoreLocks(null);
        identityHintShown = false;
        clear();
    }
}
