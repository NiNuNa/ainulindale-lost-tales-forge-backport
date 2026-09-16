package com.ninuna.losttales.chat;

import java.util.Arrays;
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
        ChatNamedPlayer sam = new ChatNamedPlayer("Sam", "Samwise", 0x123456);
        ChatNamedPlayer frodo = new ChatNamedPlayer("Frodo", "", 0);
        assertEquals(sam, ChatNamedPlayer.find(Arrays.asList(frodo, sam), "sam"));
        assertEquals("a line the server writes names the character",
                sam, ChatNamedPlayer.find(Arrays.asList(frodo, sam), "samwise"));
        assertEquals("Frodo", ChatNamedPlayer.find(
                Arrays.asList(frodo, sam), "Frodo").getIdentityName());
        assertNull(ChatNamedPlayer.find(Arrays.asList(frodo, sam), "Merry"));
        assertNull(ChatNamedPlayer.find(null, "Sam"));
        assertFalse(new ChatNamedPlayer("", "x", 0).isValid());
        assertEquals(0x123456, sam.getNameColor());
    }
}
