package com.ninuna.losttales.chat.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.ChatRoleplayStatus;
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

    /**
     * Everyone is told the lines of the identities they are shown, and
     * no other: an identity not in use, or Invisible, keeps its line to
     * itself. A line is kept cleaned, and an empty one not at all.
     */
    @Test
    public void onlyAShownIdentityTellsItsLine() {
        Map<ChatPresenceIdentity, String> set =
                new HashMap<ChatPresenceIdentity, String>();
        set.put(ALDRIC, "  Out hunting ");
        set.put(BERIC, "Hiding");
        set.put(CAROL, "Not here");
        set.put(ChatPresenceIdentity.ACCOUNT, " ");
        Map<ChatPresenceIdentity, String> kept = ChatPresenceService.cleaned(set);
        assertEquals(3, kept.size());
        assertEquals("Out hunting", kept.get(ALDRIC));
        Map<ChatPresenceIdentity, ChatPresence> shown =
                ChatPresenceService.shown(Collections.singletonMap(BERIC,
                        ChatPresence.INVISIBLE), false, IN_USE);
        Map<ChatPresenceIdentity, String> told =
                ChatPresenceService.shownLines(shown, kept);
        assertEquals(Collections.singletonMap(ALDRIC, "Out hunting"), told);
    }

    /**
     * Every identity shown tells its role-play status: the one chosen,
     * else its own default — a character in character, the account out
     * of it — and a hidden identity tells none.
     */
    @Test
    public void aShownIdentityTellsItsRolePlayStatus() {
        Map<ChatPresenceIdentity, ChatPresence> shown =
                ChatPresenceService.shown(Collections.singletonMap(BERIC,
                        ChatPresence.INVISIBLE), false, IN_USE);
        Map<ChatPresenceIdentity, ChatRoleplayStatus> chosen =
                new HashMap<ChatPresenceIdentity, ChatRoleplayStatus>();
        chosen.put(ALDRIC, ChatRoleplayStatus.LOOKING_FOR_SCENE);
        chosen.put(BERIC, ChatRoleplayStatus.OUT_OF_CHARACTER);
        Map<ChatPresenceIdentity, ChatRoleplayStatus> told =
                ChatPresenceService.shownRoleplay(shown, chosen);
        assertEquals(2, told.size());
        assertEquals(ChatRoleplayStatus.LOOKING_FOR_SCENE, told.get(ALDRIC));
        assertEquals(ChatRoleplayStatus.OUT_OF_CHARACTER,
                told.get(ChatPresenceIdentity.ACCOUNT));
        assertEquals(ChatRoleplayStatus.IN_CHARACTER, ChatPresenceService
                .shownRoleplay(shown, null).get(ALDRIC));
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
