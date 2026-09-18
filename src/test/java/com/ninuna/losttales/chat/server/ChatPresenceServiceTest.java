package com.ninuna.losttales.chat.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

/**
 * What a player's identities show everyone else: the identities in use
 * with their own choices, Away over Online while idle, nothing for
 * Invisible, and nothing for an identity not in use.
 */
public final class ChatPresenceServiceTest {
    private static final ChatPresenceIdentity ALDRIC =
            ChatPresenceIdentity.character(new UUID(1L, 1L));
    private static final ChatPresenceIdentity BERIC =
            ChatPresenceIdentity.character(new UUID(2L, 2L));
    private static final ChatPresenceIdentity CAROL =
            ChatPresenceIdentity.character(new UUID(3L, 3L));
    private static final List<ChatPresenceIdentity> IN_USE = Arrays.asList(
            ChatPresenceIdentity.ACCOUNT, ALDRIC, BERIC);

    @Test
    public void everyIdentityInUseIsOnlineUntilChosenOtherwise() {
        Map<ChatPresenceIdentity, ChatPresence> expected =
                new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
        expected.put(ChatPresenceIdentity.ACCOUNT, ChatPresence.ONLINE);
        expected.put(ALDRIC, ChatPresence.ONLINE);
        expected.put(BERIC, ChatPresence.ONLINE);
        assertEquals(expected, ChatPresenceService.shown(
                Collections.<ChatPresenceIdentity, ChatPresence>emptyMap(),
                false, IN_USE));
    }

    @Test
    public void eachIdentityShowsItsOwnChoice() {
        Map<ChatPresenceIdentity, ChatPresence> chosen =
                new HashMap<ChatPresenceIdentity, ChatPresence>();
        chosen.put(ChatPresenceIdentity.ACCOUNT, ChatPresence.DO_NOT_DISTURB);
        chosen.put(ALDRIC, ChatPresence.AWAY);
        Map<ChatPresenceIdentity, ChatPresence> shown =
                ChatPresenceService.shown(chosen, false, IN_USE);
        assertEquals(ChatPresence.DO_NOT_DISTURB,
                shown.get(ChatPresenceIdentity.ACCOUNT));
        assertEquals(ChatPresence.AWAY, shown.get(ALDRIC));
        assertEquals(ChatPresence.ONLINE, shown.get(BERIC));
    }

    @Test
    public void anIdentityNotInUseShowsNothingWhateverItsChoice() {
        Map<ChatPresenceIdentity, ChatPresence> chosen =
                Collections.singletonMap(CAROL, ChatPresence.DO_NOT_DISTURB);
        assertTrue(!ChatPresenceService.shown(chosen, false, IN_USE)
                .containsKey(CAROL));
    }

    @Test
    public void invisibleShowsNothingAtAll() {
        Map<ChatPresenceIdentity, ChatPresence> chosen =
                Collections.singletonMap(ALDRIC, ChatPresence.INVISIBLE);
        Map<ChatPresenceIdentity, ChatPresence> shown =
                ChatPresenceService.shown(chosen, true, IN_USE);
        assertTrue(!shown.containsKey(ALDRIC));
        assertEquals(2, shown.size());
    }

    @Test
    public void idleTurnsOnlineAwayAndLeavesAHandChosenStatus() {
        Map<ChatPresenceIdentity, ChatPresence> chosen =
                Collections.singletonMap(ALDRIC, ChatPresence.DO_NOT_DISTURB);
        Map<ChatPresenceIdentity, ChatPresence> shown =
                ChatPresenceService.shown(chosen, true, IN_USE);
        assertEquals(ChatPresence.AWAY, shown.get(ChatPresenceIdentity.ACCOUNT));
        assertEquals(ChatPresence.DO_NOT_DISTURB, shown.get(ALDRIC));
        assertEquals(ChatPresence.AWAY, shown.get(BERIC));
    }
}
