package com.ninuna.losttales.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** The channel a tab id names, read as the client writes ids. */
public final class ChatTabIdsTest {

    @Test
    public void aTabIdNamesItsChannelOrNone() {
        assertEquals(ChatChannel.ALL, ChatTabIds.channelOf("all"));
        assertEquals(ChatChannel.ALL, ChatTabIds.channelOf(" ALL "));
        assertEquals(ChatChannel.FACTION, ChatTabIds.channelOf("faction|in:gondor"));
        assertEquals(ChatChannel.OOC, ChatTabIds.channelOf("ooc|own:abc"));
        assertNull(ChatTabIds.channelOf("whisper:Alex"));
        assertNull(ChatTabIds.channelOf("Whisper:Alex|Aldric|own:abc"));
        assertNull(ChatTabIds.channelOf("npc:Gandalf"));
        assertNull(ChatTabIds.channelOf("whisper"));
        assertNull(ChatTabIds.channelOf("nothing-of-the-kind"));
        assertNull(ChatTabIds.channelOf(""));
        assertNull(ChatTabIds.channelOf(null));
    }

    /**
     * A whisper conversation's id: the partner, their identity where it is
     * not the account's own, and the character it is held as where it is
     * held as one — the same on both sides.
     */
    @Test
    public void aWhisperConversationIsNamedTheSameWayOnBothSides() {
        assertEquals("whisper:Steve", ChatTabIds.whisperConversationId("Steve", "", ""));
        assertEquals("whisper:Steve", ChatTabIds.whisperConversationId("Steve", "steve", ""));
        assertEquals("whisper:Steve|Aldric",
                ChatTabIds.whisperConversationId("Steve", "Aldric", ""));
        assertEquals("whisper:Steve|Steve|own:abc",
                ChatTabIds.whisperConversationId("Steve", "", "ABC"));
        assertEquals("whisper:Steve|Aldric|own:00000000-0000-0000-0000-0000000000c1",
                ChatTabIds.whisperConversationId("Steve", "Aldric",
                        java.util.UUID.fromString("00000000-0000-0000-0000-0000000000C1")));
        assertEquals("whisper:Steve|Aldric",
                ChatTabIds.whisperConversationId(" Steve ", " Aldric ", (String)null));
    }
}
