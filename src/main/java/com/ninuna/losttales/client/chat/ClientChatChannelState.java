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
import net.minecraft.util.EnumChatFormatting;

/**
 * Session-local selected channel with deterministic availability fallback.
 * A channel can be <em>viewable</em> without being <em>sendable</em>: Global
 * is always readable because achievements and other vanilla lines live in
 * its tab, but only an active role-playing character may talk there. The
 * server enforces the same rule; this only keeps the client honest.
 */
public final class ClientChatChannelState {
    /** How often an unavailable faction-name lookup is retried. */
    private static final long FACTION_NAME_RETRY_NANOS = 5000L * 1000000L;

    private static ChatChannel selected = ChatChannel.ALL;
    private static String cachedFactionId = "";
    private static String cachedFactionName = "";
    private static long cachedFactionNanos;

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

    /** Whether the channel's tab is shown and its history readable. */
    public static synchronized boolean isAvailable(ChatChannel channel) {
        if (channel == null) {
            return false;
        }
        if (channel == ChatChannel.ALL || channel == ChatChannel.OOC) {
            return true;
        }
        return canSend(channel);
    }

    /** Whether the local player may currently send into the channel. */
    public static synchronized boolean canSend(ChatChannel channel) {
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

    /**
     * Visible label for a channel. Faction shows the active character's
     * LOTR faction name ("Gondor") so the tab, indicator, and message prefix
     * all agree; the logical channel id is untouched. The LOTR lookup is
     * cached per faction id, and an unavailable lookup is retried on an
     * interval rather than every frame, falling back to the catalogue name.
     */
    public static synchronized String displayName(ChatChannel channel) {
        if (channel == null) {
            return "";
        }
        if (channel != ChatChannel.FACTION) {
            return channel.getDisplayName();
        }
        CharacterSummary active = activeCharacter();
        String factionId = active == null ? ""
                : LotrCharacterAdapter.normalizeFactionId(
                        active.getStartingFactionId());
        if (factionId.length() == 0) {
            return channel.getDisplayName();
        }
        long now = System.nanoTime();
        if (!factionId.equals(cachedFactionId)
                || (cachedFactionName.length() == 0
                        && now - cachedFactionNanos
                        > FACTION_NAME_RETRY_NANOS)) {
            String name = LotrCharacterAdapter.getInstance()
                    .getFactionDisplayName(factionId);
            String plain = name == null ? null
                    : EnumChatFormatting.getTextWithoutFormattingCodes(name);
            cachedFactionId = factionId;
            cachedFactionName = plain == null ? "" : plain.trim();
            cachedFactionNanos = now;
        }
        return cachedFactionName.length() == 0
                ? channel.getDisplayName() : cachedFactionName;
    }

    public static synchronized void clear() {
        selected = ChatChannel.ALL;
        cachedFactionId = "";
        cachedFactionName = "";
        cachedFactionNanos = 0L;
    }

    private static CharacterSummary activeCharacter() {
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        return roster == null ? null : roster.getActiveCharacter();
    }

    /** Without a character the player lands where they can actually talk. */
    private static ChatChannel fallbackChannel() {
        return canSend(ChatChannel.ALL) ? ChatChannel.ALL : ChatChannel.OOC;
    }
}
