package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/** One session-wide identity for reading and speaking in roleplaying channels. */
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

    private static Identity viewing;
    /** Whether the Narrator's voice is taken up over the identity. */
    private static boolean narrating;

    private ClientChatIdentities() {}

    /**
     * Choosing a chat identity never changes the character being played.
     * Only a character can be chosen: the account is what the roleplaying
     * channels fall back to while no character is held, never a choice.
     */
    static synchronized void select(Identity identity) {
        if (identity != null && !identity.account && isHeld(identity)) {
            viewing = identity;
            narrating = false;
            ClientChatTypingState.clear();
            ChatSpeechBubbles.clear();
        }
    }

    /** Follows the played character until an identity is explicitly selected. */
    static synchronized Identity viewing() {
        if (viewing != null && isHeld(viewing)) {
            return of(ClientCharacterRosterCache.getSnapshot()
                    .getCharacter(viewing.characterId));
        }
        viewing = null;
        CharacterSummary active = activeCharacter();
        return active == null ? accountIdentity() : of(active);
    }

    /** The account's own name, which is what an account channel speaks as. */
    static synchronized String accountName() {
        return accountIdentity().name;
    }

    /**
     * Whether the Narrator's voice is chosen over the identity: the
     * roleplaying channels sign as the Narrator while it is, on the
     * identity's own routing. Choosing a character puts the voice down.
     */
    static synchronized boolean isNarrating() {
        return narrating;
    }

    /** Takes the Narrator's voice up or puts it down; the server confirms or refuses. */
    static synchronized void setNarrating(boolean on) {
        if (narrating != on) {
            narrating = on;
            ClientChatTypingState.clear();
            ChatSpeechBubbles.clear();
        }
    }

    /** The server's answer on the voice: refused, it is put down here too. */
    static synchronized void confirmNarrating(boolean on) {
        narrating = on;
    }

    public static synchronized String viewIdentityKey() {
        if (viewing != null && isHeld(viewing)) {
            return ChatTab.ownerKeyOf(viewing.characterId);
        }
        viewing = null;
        return activeIdentityKey();
    }

    public static String activeIdentityKey() {
        CharacterSummary active = activeCharacter();
        return ChatTab.ownerKeyOf(active == null ? null : active.getCharacterId());
    }

    static synchronized Identity effectiveFor(ChatTab tab) {
        return speaksInCharacter(tab) ? viewing() : accountIdentity();
    }

    /**
     * Whether the tab's channel speaks as the chat identity rather than
     * the account: where the head button chooses, and where a choice
     * shows.
     */
    static boolean speaksInCharacter(ChatTab tab) {
        return tab != null && ChatRolePresentation.isInCharacter(tab.getChannel());
    }

    static synchronized int wireKind(ChatTab tab) {
        if (!speaksInCharacter(tab)) {
            return LostTalesChatSendPacket.IDENTITY_ACCOUNT;
        }
        return viewing == null || !isHeld(viewing)
                ? LostTalesChatSendPacket.IDENTITY_DEFAULT
                : LostTalesChatSendPacket.IDENTITY_CHARACTER;
    }

    static synchronized UUID wireCharacterId(ChatTab tab) {
        return wireKind(tab) == LostTalesChatSendPacket.IDENTITY_CHARACTER
                ? viewing.characterId : null;
    }

    static synchronized boolean isSelected(Identity identity) {
        return identity != null && ChatTab.ownerKeyOf(identity.characterId)
                .equals(viewIdentityKey());
    }

    /** Whether the roster still holds the character. */
    private static boolean isHeld(Identity identity) {
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        return roster != null && roster.getCharacter(identity.characterId) != null;
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
        viewing = null;
        narrating = false;
    }
}
