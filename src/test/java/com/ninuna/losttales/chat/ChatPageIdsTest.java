package com.ninuna.losttales.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** The channel a page id names, read as the client writes ids. */
public final class ChatPageIdsTest {

    @Test
    public void aPageIdNamesItsChannelOrNone() {
        assertEquals(ChatChannel.GLOBAL, ChatPageIds.channelOf("global"));
        assertEquals(ChatChannel.GLOBAL, ChatPageIds.channelOf(" GLOBAL "));
        assertEquals(ChatChannel.FACTION, ChatPageIds.channelOf("faction|in:gondor"));
        assertEquals(ChatChannel.OOC, ChatPageIds.channelOf("ooc|own:abc"));
        assertNull(ChatPageIds.channelOf("whisper:Alex"));
        assertNull(ChatPageIds.channelOf("Whisper:Alex|Aldric|own:abc"));
        assertNull(ChatPageIds.channelOf("npc:Gandalf"));
        assertNull(ChatPageIds.channelOf("whisper"));
        assertNull(ChatPageIds.channelOf("nothing-of-the-kind"));
        assertNull(ChatPageIds.channelOf(""));
        assertNull(ChatPageIds.channelOf(null));
    }

    /**
     * A whisper conversation's id: the partner, their identity where it is
     * not the account's own, and the character it is held as where it is
     * held as one — the same on both sides.
     */
    @Test
    public void aWhisperConversationIsNamedTheSameWayOnBothSides() {
        assertEquals("whisper:Steve", ChatPageIds.whisperConversationId("Steve", "", ""));
        assertEquals("whisper:Steve", ChatPageIds.whisperConversationId("Steve", "steve", ""));
        assertEquals("whisper:Steve|Aldric",
                ChatPageIds.whisperConversationId("Steve", "Aldric", ""));
        assertEquals("whisper:Steve|Steve|own:abc",
                ChatPageIds.whisperConversationId("Steve", "", "ABC"));
        assertEquals("whisper:Steve|Aldric|own:00000000-0000-0000-0000-0000000000c1",
                ChatPageIds.whisperConversationId("Steve", "Aldric",
                        java.util.UUID.fromString("00000000-0000-0000-0000-0000000000C1")));
        assertEquals("whisper:Steve|Aldric",
                ChatPageIds.whisperConversationId(" Steve ", " Aldric ", (String)null));
    }
}
