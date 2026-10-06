package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/** Requesters for the history's tests. */
final class ChatHistoryRequesters {
    private ChatHistoryRequesters() {}

    /** An account with no character, reading every channel. */
    static ChatHistory.Requester reader(UUID accountId) {
        return new ChatHistory.Requester(accountId,
                Collections.<String, Long>emptyMap(), null,
                java.util.Arrays.asList(ChatChannel.values()), null);
    }

    /**
     * An account with one character, in that faction, made then; a
     * faction id of nothing is an account with no character in any.
     */
    static ChatHistory.Requester oneFaction(UUID accountId, String factionId,
                                            long characterCreatedAt,
                                            UUID fellowshipId,
                                            Collection<ChatChannel> readable) {
        Map<String, Long> owned = factionId == null || factionId.length() == 0
                ? Collections.<String, Long>emptyMap()
                : Collections.singletonMap(factionId,
                        Long.valueOf(characterCreatedAt));
        return new ChatHistory.Requester(accountId, owned, fellowshipId == null
                ? Collections.<UUID>emptySet() : Collections.singleton(fellowshipId),
                readable, null);
    }
}
