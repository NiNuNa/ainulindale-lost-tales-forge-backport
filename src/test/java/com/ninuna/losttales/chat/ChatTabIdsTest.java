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
}
