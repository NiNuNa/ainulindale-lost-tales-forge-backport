package com.ninuna.losttales.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;

/**
 * A presence travels by its place and is written by its id; Offline is
 * only shown, never chosen, and Invisible is shown to others as Offline.
 * An identity is the account or one character, told apart by kind even
 * where a character's id is the account's own.
 */
public final class ChatPresenceTest {

    @Test
    public void everyPresenceRoundTripsByItsCodeAndItsId() {
        for (ChatPresence presence : ChatPresence.values()) {
            assertSame(presence, ChatPresence.fromCode(presence.code()));
            assertSame(presence, ChatPresence.fromId(presence.getId()));
        }
        assertEquals(0, ChatPresence.ONLINE.code());
        assertEquals(1, ChatPresence.AWAY.code());
        assertEquals(2, ChatPresence.DO_NOT_DISTURB.code());
        assertEquals(3, ChatPresence.INVISIBLE.code());
        assertEquals(4, ChatPresence.OFFLINE.code());
    }

    @Test
    public void anUnknownCodeOrIdNamesNothing() {
        assertNull(ChatPresence.fromCode(-1));
        assertNull(ChatPresence.fromCode(ChatPresence.values().length));
        assertNull(ChatPresence.fromId("busy"));
        assertNull(ChatPresence.fromId(null));
    }

    @Test
    public void offlineIsShownAndNeverChosen() {
        assertFalse(ChatPresence.OFFLINE.isChoosable());
        assertTrue(ChatPresence.ONLINE.isChoosable());
        assertTrue(ChatPresence.AWAY.isChoosable());
        assertTrue(ChatPresence.DO_NOT_DISTURB.isChoosable());
        assertTrue(ChatPresence.INVISIBLE.isChoosable());
    }

    @Test
    public void invisibleIsShownToOthersAsOffline() {
        assertSame(ChatPresence.OFFLINE, ChatPresence.INVISIBLE.shownToOthers());
        assertSame(ChatPresence.ONLINE, ChatPresence.ONLINE.shownToOthers());
        assertSame(ChatPresence.AWAY, ChatPresence.AWAY.shownToOthers());
        assertSame(ChatPresence.DO_NOT_DISTURB,
                ChatPresence.DO_NOT_DISTURB.shownToOthers());
    }

    @Test
    public void theLabelKeyFollowsTheId() {
        assertEquals("gui.losttales.chat.status.do_not_disturb",
                ChatPresence.DO_NOT_DISTURB.labelKey());
        assertEquals("gui.losttales.chat.status.invisible",
                ChatPresence.INVISIBLE.labelKey());
        assertEquals("away", ChatPresence.AWAY.getId());
    }

    @Test
    public void theAccountAndItsDefaultCharacterAreTwoIdentities() {
        UUID account = UUID.randomUUID();
        ChatPresenceIdentity defaultCharacter =
                ChatPresenceIdentity.character(account);
        assertNotEquals(ChatPresenceIdentity.ACCOUNT, defaultCharacter);
        assertTrue(ChatPresenceIdentity.ACCOUNT.isAccount());
        assertFalse(defaultCharacter.isAccount());
        assertEquals(account, defaultCharacter.getCharacterId());
        assertSame(ChatPresenceIdentity.ACCOUNT,
                ChatPresenceIdentity.character(null));
        assertEquals(ChatPresenceIdentity.character(account),
                defaultCharacter);
        assertEquals(defaultCharacter.hashCode(),
                ChatPresenceIdentity.character(account).hashCode());
    }

    @Test
    public void anIdentityRoundTripsAsText() {
        UUID character = UUID.randomUUID();
        assertSame(ChatPresenceIdentity.ACCOUNT, ChatPresenceIdentity.fromText(
                ChatPresenceIdentity.ACCOUNT.toText()));
        assertEquals(ChatPresenceIdentity.character(character),
                ChatPresenceIdentity.fromText(character.toString()));
        assertNull(ChatPresenceIdentity.fromText("nobody"));
        assertNull(ChatPresenceIdentity.fromText(""));
        assertNull(ChatPresenceIdentity.fromText(null));
    }
}
