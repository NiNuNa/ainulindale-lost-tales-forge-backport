package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Session-local selected channel with deterministic availability fallback. */
public final class ClientChatChannelState {
    private static ChatChannel selected = ChatChannel.ALL;

    private ClientChatChannelState() {}

    public static synchronized ChatChannel getSelected() {
        ensureAvailable();
        return selected;
    }

    public static synchronized void select(ChatChannel channel) {
        selected = isAvailable(channel) ? channel : ChatChannel.ALL;
    }

    public static synchronized ChatChannel cycle() {
        List<ChatChannel> available = getAvailableChannels();
        int index = available.indexOf(getSelected());
        selected = available.get((index + 1) % available.size());
        return selected;
    }

    public static synchronized List<ChatChannel> getAvailableChannels() {
        ArrayList<ChatChannel> result = new ArrayList<ChatChannel>();
        for (ChatChannel channel : ChatChannel.values()) {
            if (isAvailable(channel)) {
                result.add(channel);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized void ensureAvailable() {
        if (!isAvailable(selected)) {
            selected = ChatChannel.ALL;
        }
    }

    public static synchronized boolean isAvailable(ChatChannel channel) {
        if (channel == null) {
            return false;
        }
        if (channel == ChatChannel.FACTION) {
            CharacterRosterSnapshot roster =
                    ClientCharacterRosterCache.getSnapshot();
            CharacterSummary active = roster == null
                    ? null : roster.getActiveCharacter();
            return active != null && LotrCharacterAdapter.normalizeFactionId(
                    active.getStartingFactionId()).length() > 0;
        }
        if (channel != ChatChannel.PARTY) {
            return true;
        }
        PartyStateSnapshot snapshot = ClientPartyStateCache.getSnapshot();
        return snapshot != null && snapshot.isAvailable()
                && snapshot.getActiveCharacterId() != null
                && snapshot.getParty() != null
                && snapshot.getParty().containsMember(
                        snapshot.getActiveCharacterId());
    }

    public static synchronized void clear() {
        selected = ChatChannel.ALL;
    }
}
