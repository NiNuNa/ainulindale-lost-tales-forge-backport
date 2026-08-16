package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
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
        selected = isAvailable(channel) ? channel : fallbackChannel();
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
            selected = fallbackChannel();
        }
    }

    public static synchronized boolean isAvailable(ChatChannel channel) {
        if (channel == null) {
            return false;
        }
        CharacterSummary active = activeCharacter();
        if (channel.getIdentityType() == ChatIdentityType.CHARACTER
                && active == null) {
            return false;
        }
        if (channel == ChatChannel.FACTION) {
            return LotrCharacterAdapter.normalizeFactionId(
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

    public static synchronized int displayColor(ChatChannel channel) {
        if (channel != ChatChannel.FACTION) {
            return channel == null ? 0xFFFFFF : channel.getDisplayColor();
        }
        CharacterSummary active = activeCharacter();
        return active == null ? channel.getDisplayColor()
                : LotrCharacterAdapter.getInstance().getFactionColor(
                        active.getStartingFactionId(),
                        channel.getDisplayColor());
    }

    public static synchronized void clear() {
        selected = ChatChannel.ALL;
    }

    private static CharacterSummary activeCharacter() {
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        return roster == null ? null : roster.getActiveCharacter();
    }

    private static ChatChannel fallbackChannel() {
        return isAvailable(ChatChannel.ALL)
                ? ChatChannel.ALL : ChatChannel.OOC;
    }
}
