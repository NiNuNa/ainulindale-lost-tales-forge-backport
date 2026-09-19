package com.ninuna.losttales.chat;

import java.util.Arrays;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** A server line's named players: matched whole, found by account. */
public final class ChatNamedPlayerTest {

    @Test
    public void aNameIsMatchedWholeAndInAnyCase() {
        assertTrue(ChatNamedPlayer.names("Sam has just earned [Foo]", "Sam"));
        assertTrue(ChatNamedPlayer.names("sam joined the game", "Sam"));
        assertTrue(ChatNamedPlayer.names("Frodo was slain by Sam", "Sam"));
        assertFalse(ChatNamedPlayer.names("Samwise joined the game", "Sam"));
        assertFalse(ChatNamedPlayer.names("Sam_Gamgee joined", "Sam"));
        assertFalse(ChatNamedPlayer.names("", "Sam"));
        assertFalse(ChatNamedPlayer.names("Sam", ""));
    }

    @Test
    public void anEntryIsFoundByAccountWhateverTheCase() {
        ChatNamedPlayer sam = new ChatNamedPlayer(UUID.randomUUID(), "Sam",
                UUID.randomUUID(), "Samwise", "hobbit/male/1");
        ChatNamedPlayer frodo = new ChatNamedPlayer(UUID.randomUUID(), "Frodo",
                null, "", "");
        assertEquals(sam, ChatNamedPlayer.find(Arrays.asList(frodo, sam), "sam"));
        assertEquals("a line the server writes names the character",
                sam, ChatNamedPlayer.find(Arrays.asList(frodo, sam), "samwise"));
        assertEquals("Frodo", ChatNamedPlayer.find(
                Arrays.asList(frodo, sam), "Frodo").getIdentityName());
        assertNull(ChatNamedPlayer.find(Arrays.asList(frodo, sam), "Merry"));
        assertNull(ChatNamedPlayer.find(null, "Sam"));
        assertFalse(new ChatNamedPlayer(null, "", null, "x", "").isValid());
    }

    /**
     * A named player keeps who they were: the account's id, and the
     * character with its skin — none for the account, whose own skin a
     * head draws.
     */
    @Test
    public void anEntryKeepsTheHeadItNamesThePlayerWith() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        ChatNamedPlayer sam = new ChatNamedPlayer(account, "Sam", character,
                "Samwise", "hobbit/male/1");
        assertEquals(account, sam.getPlayerId());
        assertEquals(character, sam.getCharacterId());
        assertEquals("hobbit/male/1", sam.getSkinId());
        ChatNamedPlayer asAccount = new ChatNamedPlayer(account, "Sam", null,
                "", "hobbit/male/1");
        assertNull(asAccount.getCharacterId());
        assertEquals("an account wears its own skin", "", asAccount.getSkinId());
        assertEquals("Sam", asAccount.getIdentityName());
    }
}
