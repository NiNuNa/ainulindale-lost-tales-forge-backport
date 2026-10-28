package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowPage;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.permission.LostTalesCapability;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
 * the feed shows always follows the character played.
 *
 * <p>The voices are kept per place in the layout file, one {@code voice}
 * line each ({@link #describeVoices}), and come back at the next visit. A
 * character the roster no longer holds, or a Narrator's voice the player
 * may no longer take up, reads as the character played; the server checks
 * whatever a line claims anyway.</p>
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
    /** Each place's voices as the layout file keeps them, by row id. */
    private static final Map<String, Map<String, Voice>> KEPT =
            new LinkedHashMap<String, Map<String, Voice>>();
    /** What a {@code voice} line begins with. */
    static final String VOICE_LINE = "voice";
    /** A {@code voice} line's word for no character, or for no Narrator. */
    private static final String NONE = "-";
    /** A {@code voice} line's word for the Narrator's voice taken up. */
    private static final String NARRATOR = "narrator";

    private ClientChatIdentities() {}

    /** What a copy's voice is kept by: the id of the row entry its window holds, its copy's number in it. */
    private static String voiceKey(ConversationPage page) {
        ConversationPage row = ConversationPage.row(page);
        return row == null ? null : row.id();
    }

    private static synchronized Voice voiceOf(ConversationPage page) {
        String key = voiceKey(page);
        return key == null ? null : VOICES.get(key);
    }

    private static synchronized void setVoice(ConversationPage page, Voice voice) {
        String key = voiceKey(page);
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
    static void select(ConversationPage page, Identity identity) {
        if (choose(page, identity)) {
            WindowLayout.persist();
        }
    }

    private static synchronized boolean choose(ConversationPage page, Identity identity) {
        if (identity == null || identity.account || !isHeld(identity.characterId)) {
            return false;
        }
        setVoice(page, new Voice(identity.characterId, false));
        return true;
    }

    /**
     * Has the copy speak as the character {@code ownerKey} names: a whisper
     * that arrived for one of this player's characters opens a copy held as
     * that character. The played character, the account and one the roster
     * no longer holds leave the copy following the character played.
     */
    static synchronized void holdAs(ConversationPage page, String ownerKey) {
        UUID characterId = characterOf(ownerKey);
        if (characterId != null && !ownerKey.equals(activeIdentityKey())) {
            setVoice(page, new Voice(characterId, false));
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
    static synchronized Identity viewing(ConversationPage page) {
        UUID chosen = chosenCharacter(page);
        if (chosen != null) {
            return of(ClientCharacterRosterCache.getSnapshot().getCharacter(chosen));
        }
        return played();
    }

    /** The key of the identity the copy reads as ({@link ConversationPage#ownerKeyOf}). */
    static synchronized String viewIdentityKey(ConversationPage page) {
        UUID chosen = chosenCharacter(page);
        return chosen != null ? ConversationPage.ownerKeyOf(chosen)
                : activeIdentityKey();
    }

    /** The character the copy chose and the roster still holds; null to follow the one played. */
    private static UUID chosenCharacter(ConversationPage page) {
        if (page != null && page.isWhisper() && page.getOwnerKey().length() > 0) {
            return characterOf(page.getOwnerKey());
        }
        Voice voice = voiceOf(page);
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

    /**
     * Whether the copy has the Narrator's voice taken up: its lines sign
     * as the Narrator, on its identity's own routing. The server says no
     * to a player who may not narrate.
     */
    static synchronized boolean isNarrating(ConversationPage page) {
        Voice voice = voiceOf(page);
        return voice != null && voice.narrating
                && ClientChatChannelState.holds(LostTalesCapability.CHAT_NARRATE);
    }

    /** Takes the copy's Narrator's voice up or puts it down. */
    static void setNarrating(ConversationPage page, boolean on) {
        if (narrate(page, on)) {
            WindowLayout.persist();
        }
    }

    private static synchronized boolean narrate(ConversationPage page, boolean on) {
        if (isNarrating(page) == on) {
            return false;
        }
        Voice voice = voiceOf(page);
        setVoice(page, new Voice(voice == null ? null : voice.characterId, on));
        return true;
    }

    /**
     * Who the player is on a page: on a conversation, who its copy speaks
     * as; on a page, the character played, whom every page is about.
     */
    static synchronized Identity effectiveFor(WindowPage page) {
        if (page != null && !(page instanceof ConversationPage)) {
            return played();
        }
        ConversationPage chat = (ConversationPage)page;
        if (!speaksInCharacter(chat)) {
            return accountIdentity();
        }
        return speaksAsPlayed(chat) ? played() : viewing(chat);
    }

    /** Whether a page's identity button shows the Narrator's mark: its copy's voice is taken up and it speaks in character. */
    static boolean narratesOn(WindowPage page) {
        return page instanceof ConversationPage
                && speaksInCharacter((ConversationPage)page)
                && isNarrating((ConversationPage)page);
    }

    /** Whether the identity button's menu chooses who the page speaks as: Global, Faction, whispers. */
    static boolean picksIdentity(WindowPage page) {
        return page instanceof ConversationPage && picksIdentity((ConversationPage)page);
    }

    /**
     * Whether the page speaks as the character played, whatever the head
     * button chose: Proximity and Fellowship
     * ({@link ChatRolePresentation#speaksAsPlayedCharacter}).
     */
    static boolean speaksAsPlayed(ConversationPage page) {
        return page != null && ChatRolePresentation.speaksAsPlayedCharacter(page.getChannel());
    }

    /** Whether the identity button chooses who the page speaks as: Global, Faction, whispers. */
    static boolean picksIdentity(ConversationPage page) {
        return speaksInCharacter(page) && !speaksAsPlayed(page);
    }

    /** The character played, or the account playing as itself. */
    static synchronized Identity played() {
        CharacterSummary active = activeCharacter();
        return active == null ? accountIdentity() : of(active);
    }

    /**
     * Whether the page's channel speaks as a character rather than the
     * account: where the identity button chooses, and where a choice shows.
     */
    static boolean speaksInCharacter(ConversationPage page) {
        return page != null && ChatRolePresentation.isInCharacter(page.getChannel());
    }

    /** How a line sent from the copy names who it speaks as. */
    static synchronized int wireKind(ConversationPage page) {
        if (!speaksInCharacter(page)) {
            return LostTalesChatSendPacket.IDENTITY_ACCOUNT;
        }
        return speaksAsPlayed(page) || chosenCharacter(page) == null
                ? LostTalesChatSendPacket.IDENTITY_DEFAULT
                : LostTalesChatSendPacket.IDENTITY_CHARACTER;
    }

    static synchronized UUID wireCharacterId(ConversationPage page) {
        return wireKind(page) == LostTalesChatSendPacket.IDENTITY_CHARACTER
                ? chosenCharacter(page) : null;
    }

    /**
     * The id of the identity the copy reacts as: the character it speaks
     * as, or the account's own id on an account channel and while no
     * character is held; for no copy, the feed's, the identity
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
    static synchronized boolean wireNarrating(ConversationPage page) {
        return narratesOn(page);
    }

    static synchronized boolean isSelected(ConversationPage page, Identity identity) {
        return identity != null && ConversationPage.ownerKeyOf(identity.characterId)
                .equals(viewIdentityKey(page));
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

    /**
     * Keeps the voices of the place's copies the layout holds, each one
     * that speaks in character, as the layout file is to hold them.
     */
    static synchronized void rememberVoices(String place) {
        if (place == null || place.length() == 0) {
            return;
        }
        Map<String, Voice> kept = new LinkedHashMap<String, Voice>();
        for (String id : new java.util.TreeSet<String>(VOICES.keySet())) {
            ConversationPage copy = ConversationPage.fromId(id);
            if (copy != null && !copy.isNpc() && speaksInCharacter(copy)
                    && WindowLayout.holds(copy)) {
                kept.put(id, VOICES.get(id));
            }
        }
        if (kept.isEmpty()) {
            KEPT.remove(place);
        } else {
            KEPT.put(place, kept);
        }
    }

    /** Gives the place's copies the voices kept for them; a copy that has one already keeps it. */
    static synchronized void restoreVoices(String place) {
        Map<String, Voice> kept = place == null ? null : KEPT.get(place);
        if (kept == null) {
            return;
        }
        for (Map.Entry<String, Voice> entry : kept.entrySet()) {
            if (!VOICES.containsKey(entry.getKey())) {
                VOICES.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Reads one {@code voice} line: the place, the copy's row id, the
     * character's id or {@code -}, and {@code narrator} or {@code -}. A line
     * that says anything else is skipped; whether its character or its
     * Narrator still stands is asked as the voice is used.
     */
    static synchronized void readVoice(String[] fields) {
        if (fields.length != 5 || !VOICE_LINE.equals(fields[0])
                || fields[1].length() == 0) {
            return;
        }
        ConversationPage copy = ConversationPage.fromId(fields[2]);
        if (copy == null || copy.isNpc() || !copy.equals(ConversationPage.row(copy))) {
            return;
        }
        UUID characterId = null;
        if (!NONE.equals(fields[3])) {
            try {
                characterId = UUID.fromString(fields[3]);
            } catch (IllegalArgumentException notAnId) {
                return;
            }
        }
        boolean narrating = NARRATOR.equals(fields[4]);
        if ((!narrating && !NONE.equals(fields[4]))
                || (characterId == null && !narrating)) {
            return;
        }
        Map<String, Voice> kept = KEPT.get(fields[1]);
        if (kept == null) {
            kept = new LinkedHashMap<String, Voice>();
            KEPT.put(fields[1], kept);
        }
        kept.put(copy.id(), new Voice(characterId, narrating));
    }

    /** Writes every place's kept voices, a page-separated line each, since a place may hold spaces. */
    static synchronized void describeVoices(List<String> lines) {
        for (Map.Entry<String, Map<String, Voice>> place : KEPT.entrySet()) {
            for (Map.Entry<String, Voice> entry : place.getValue().entrySet()) {
                Voice voice = entry.getValue();
                lines.add(VOICE_LINE + "\t" + place.getKey() + "\t" + entry.getKey()
                        + "\t" + (voice.characterId == null ? NONE : voice.characterId.toString())
                        + "\t" + (voice.narrating ? NARRATOR : NONE));
            }
        }
    }

    /** Forgets every place's kept voices, as the layout file is read afresh. */
    static synchronized void clearKept() {
        KEPT.clear();
    }
}
