package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowPage;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Who each copy of a conversation reads and speaks as in the roleplaying
 * channels: one of this player's characters, chosen on the copy's head
 * button, else the character played. Each copy is its own person: two
 * copies of Global may speak as two characters, a Faction copy reads its
 * character's faction, and a whisper copy holds the conversation with
 * that character. The Narrator's voice is taken up per copy too. What
 * the closed feed shows always follows the character played.
 */
final class ClientChatIdentities {
    /** One choosable identity: the account, or one roster character. */
    static final class Identity {
        /** True for the Minecraft account. */
        final boolean account;
        /** Roster character id; null for the account. */
        final UUID characterId;
        final String name;
        /** Skin snapshot for the head; empty for the account. */
        final String skinId;

        Identity(boolean account, UUID characterId, String name,
                   String skinId) {
            this.account = account;
            this.characterId = characterId;
            this.name = name == null ? "" : name;
            this.skinId = skinId == null ? "" : skinId;
        }
    }

    /** A copy's own choice: the character it speaks as, null to follow the one played, and the Narrator's voice. */
    private static final class Voice {
        final UUID characterId;
        final boolean narrating;

        Voice(UUID characterId, boolean narrating) {
            this.characterId = characterId;
            this.narrating = narrating;
        }
    }

    /** Each copy's voice, by the id of the row entry its window holds; a copy left out follows the character played. */
    private static final Map<String, Voice> VOICES = new HashMap<String, Voice>();

    private ClientChatIdentities() {}

    /** What a copy's voice is kept by: the id of the row entry its window holds, its copy's number in it. */
    private static String voiceKey(ConversationPage tab) {
        ConversationPage row = ConversationPage.row(tab);
        return row == null ? null : row.id();
    }

    private static synchronized Voice voiceOf(ConversationPage tab) {
        String key = voiceKey(tab);
        return key == null ? null : VOICES.get(key);
    }

    private static synchronized void setVoice(ConversationPage tab, Voice voice) {
        String key = voiceKey(tab);
        if (key == null) {
            return;
        }
        if (voice == null || (voice.characterId == null && !voice.narrating)) {
            VOICES.remove(key);
        } else {
            VOICES.put(key, voice);
        }
    }

    /**
     * Has the copy speak as {@code identity}. Choosing never changes the
     * character played, and only a character can be chosen: the account
     * is what the roleplaying channels fall back to while no character is
     * held, never a choice. Choosing one puts the Narrator's voice down.
     */
    static synchronized void select(ConversationPage tab, Identity identity) {
        if (identity != null && !identity.account && isHeld(identity.characterId)) {
            setVoice(tab, new Voice(identity.characterId, false));
        }
    }

    /**
     * Has the copy speak as the character {@code ownerKey} names: a whisper
     * that arrived for one of this player's characters opens a copy held as
     * that character. The played character, the account and one the roster
     * no longer holds leave the copy following the character played.
     */
    static synchronized void holdAs(ConversationPage tab, String ownerKey) {
        UUID characterId = characterOf(ownerKey);
        if (characterId != null && !ownerKey.equals(activeIdentityKey())) {
            setVoice(tab, new Voice(characterId, false));
        }
    }

    /** A duplicated copy speaks as the copy it was made from. */
    static synchronized void inherit(ConversationPage source, ConversationPage copy) {
        Voice voice = voiceOf(source);
        if (voice != null) {
            setVoice(copy, voice);
        }
    }

    /** A copy opening anew follows the character played, whatever an earlier copy of its number chose. */
    static synchronized void forget(ConversationPage copy) {
        String key = voiceKey(copy);
        if (key != null) {
            VOICES.remove(key);
        }
    }

    /**
     * Who the copy reads and speaks as: the character it chose while the
     * roster still holds it, else the character played, else the account.
     * A whisper conversation held as one character is that character's.
     */
    static synchronized Identity viewing(ConversationPage tab) {
        UUID chosen = chosenCharacter(tab);
        if (chosen != null) {
            return of(ClientCharacterRosterCache.getSnapshot().getCharacter(chosen));
        }
        return played();
    }

    /** The key of the identity the copy reads as ({@link ConversationPage#ownerKeyOf}). */
    static synchronized String viewIdentityKey(ConversationPage tab) {
        UUID chosen = chosenCharacter(tab);
        return chosen != null ? ConversationPage.ownerKeyOf(chosen)
                : activeIdentityKey();
    }

    /** The character the copy chose and the roster still holds; null to follow the one played. */
    private static UUID chosenCharacter(ConversationPage tab) {
        if (tab != null && tab.isWhisper() && tab.getOwnerKey().length() > 0) {
            return characterOf(tab.getOwnerKey());
        }
        Voice voice = voiceOf(tab);
        return voice != null && voice.characterId != null
                && isHeld(voice.characterId) ? voice.characterId : null;
    }

    /** The key of the identity played: the character's id, or empty for the account. */
    public static String activeIdentityKey() {
        CharacterSummary active = activeCharacter();
        return ConversationPage.ownerKeyOf(active == null ? null : active.getCharacterId());
    }

    /**
     * The characters the open copies read as besides the one played, each
     * once and in order: what the server is told this player reads, so
     * their factions' lines and the whispers sent to them arrive.
     */
    static synchronized List<UUID> readCharacters() {
        Set<UUID> read = new TreeSet<UUID>();
        String played = activeIdentityKey();
        Iterator<Map.Entry<String, Voice>> entries = VOICES.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Voice> entry = entries.next();
            ConversationPage copy = ConversationPage.fromId(entry.getKey());
            UUID characterId = entry.getValue().characterId;
            if (copy == null || !WindowLayout.holds(copy)) {
                continue;
            }
            if (characterId != null && isHeld(characterId)
                    && !ConversationPage.ownerKeyOf(characterId).equals(played)) {
                read.add(characterId);
            }
        }
        return new ArrayList<UUID>(read);
    }

    /** Whether some open copy, or the character played, reads as the identity {@code ownerKey} names. */
    static synchronized boolean isRead(String ownerKey) {
        if (ownerKey == null) {
            return false;
        }
        if (ownerKey.equals(activeIdentityKey())) {
            return true;
        }
        for (UUID characterId : readCharacters()) {
            if (ConversationPage.ownerKeyOf(characterId).equals(ownerKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The identities in use as one word: the one played and those the
     * copies read as. A member list asked for under another word is asked
     * for again, since it lists every one of them.
     */
    static synchronized String inUseKey() {
        return activeIdentityKey() + readCharacters();
    }

    /** The account's own name, which is what an account channel speaks as. */
    static synchronized String accountName() {
        return accountIdentity().name;
    }

    /**
     * Whether the copy has the Narrator's voice taken up: its lines sign
     * as the Narrator, on its identity's own routing. The server says no
     * to a player who may not narrate.
     */
    static synchronized boolean isNarrating(ConversationPage tab) {
        Voice voice = voiceOf(tab);
        return voice != null && voice.narrating;
    }

    /** Takes the copy's Narrator's voice up or puts it down. */
    static synchronized void setNarrating(ConversationPage tab, boolean on) {
        if (isNarrating(tab) == on) {
            return;
        }
        Voice voice = voiceOf(tab);
        setVoice(tab, new Voice(voice == null ? null : voice.characterId, on));
    }

    /**
     * Who the player is on a tab: on a conversation, who its copy speaks
     * as; on a page, the character played, whom every page is about.
     */
    static synchronized Identity effectiveFor(WindowPage tab) {
        if (tab != null && !(tab instanceof ConversationPage)) {
            return played();
        }
        ConversationPage chat = (ConversationPage)tab;
        if (!speaksInCharacter(chat)) {
            return accountIdentity();
        }
        return speaksAsPlayed(chat) ? played() : viewing(chat);
    }

    /** Whether a tab's identity button shows the Narrator's mark: its copy's voice is taken up and it speaks in character. */
    static boolean narratesOn(WindowPage tab) {
        return tab instanceof ConversationPage
                && speaksInCharacter((ConversationPage)tab)
                && isNarrating((ConversationPage)tab);
    }

    /** Whether the identity button's menu chooses who the tab speaks as: Global, Faction, whispers. */
    static boolean picksIdentity(WindowPage tab) {
        return tab instanceof ConversationPage && picksIdentity((ConversationPage)tab);
    }

    /**
     * Whether the tab speaks as the character played, whatever the head
     * button chose: Proximity and Fellowship
     * ({@link ChatRolePresentation#speaksAsPlayedCharacter}).
     */
    static boolean speaksAsPlayed(ConversationPage tab) {
        return tab != null && ChatRolePresentation.speaksAsPlayedCharacter(tab.getChannel());
    }

    /** Whether the head button chooses who the tab speaks as: Global, Faction, whispers. */
    static boolean picksIdentity(ConversationPage tab) {
        return speaksInCharacter(tab) && !speaksAsPlayed(tab);
    }

    /** The character played, or the account playing as itself. */
    static synchronized Identity played() {
        CharacterSummary active = activeCharacter();
        return active == null ? accountIdentity() : of(active);
    }

    /**
     * Whether the tab's channel speaks as a character rather than the
     * account: where the head button chooses, and where a choice shows.
     */
    static boolean speaksInCharacter(ConversationPage tab) {
        return tab != null && ChatRolePresentation.isInCharacter(tab.getChannel());
    }

    /** How a line sent from the copy names who it speaks as. */
    static synchronized int wireKind(ConversationPage tab) {
        if (!speaksInCharacter(tab)) {
            return LostTalesChatSendPacket.IDENTITY_ACCOUNT;
        }
        return speaksAsPlayed(tab) || chosenCharacter(tab) == null
                ? LostTalesChatSendPacket.IDENTITY_DEFAULT
                : LostTalesChatSendPacket.IDENTITY_CHARACTER;
    }

    static synchronized UUID wireCharacterId(ConversationPage tab) {
        return wireKind(tab) == LostTalesChatSendPacket.IDENTITY_CHARACTER
                ? chosenCharacter(tab) : null;
    }

    /**
     * The id of the identity the copy reacts as: the character it speaks
     * as, or the account's own id on an account channel and while no
     * character is held; for no copy, the closed feed's, the identity
     * played.
     */
    static synchronized UUID reactorIdOf(ConversationPage copy) {
        Identity identity = copy == null ? played() : effectiveFor(copy);
        if (identity.account) {
            Minecraft minecraft = Minecraft.getMinecraft();
            return minecraft == null || minecraft.thePlayer == null ? null
                    : minecraft.thePlayer.getUniqueID();
        }
        return identity.characterId;
    }

    /** Whether a line sent from the copy signs as the Narrator. */
    static synchronized boolean wireNarrating(ConversationPage tab) {
        return narratesOn(tab);
    }

    static synchronized boolean isSelected(ConversationPage tab, Identity identity) {
        return identity != null && ConversationPage.ownerKeyOf(identity.characterId)
                .equals(viewIdentityKey(tab));
    }

    /** The character the owner key names, while the roster holds it; null for the account or one gone. */
    private static UUID characterOf(String ownerKey) {
        if (ownerKey == null || ownerKey.length() == 0) {
            return null;
        }
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        if (roster == null) {
            return null;
        }
        for (CharacterSummary character : roster.getCharacters()) {
            if (character != null && ownerKey.equals(
                    ConversationPage.ownerKeyOf(character.getCharacterId()))) {
                return character.getCharacterId();
            }
        }
        return null;
    }

    /** Whether the roster still holds the character. */
    private static boolean isHeld(UUID characterId) {
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        return characterId != null && roster != null
                && roster.getCharacter(characterId) != null;
    }

    static Identity accountIdentity() {
        Minecraft minecraft = Minecraft.getMinecraft();
        String name = minecraft == null || minecraft.thePlayer == null
                ? "" : minecraft.thePlayer.getCommandSenderName();
        return new Identity(true, null, name, "");
    }

    /** The roster's ordinary characters, the lore-owned ones left out. */
    static List<Identity> characterIdentities() {
        List<Identity> result = new ArrayList<Identity>();
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
    static List<Identity> loreIdentities() {
        List<Identity> result = new ArrayList<Identity>();
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

    /** Every identity of this player's in use: the one played and those the open copies read as. */
    static synchronized List<Identity> inUse() {
        List<Identity> identities = new ArrayList<Identity>();
        identities.add(played());
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        for (UUID characterId : readCharacters()) {
            CharacterSummary summary = roster == null ? null
                    : roster.getCharacter(characterId);
            if (summary != null) {
                identities.add(of(summary));
            }
        }
        return identities;
    }

    private static Identity of(CharacterSummary summary) {
        return new Identity(false, summary.getCharacterId(),
                summary.getName(), summary.getSkinId());
    }

    private static CharacterSummary activeCharacter() {
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        return roster == null ? null : roster.getActiveCharacter();
    }

    static synchronized void clear() {
        VOICES.clear();
    }
}
