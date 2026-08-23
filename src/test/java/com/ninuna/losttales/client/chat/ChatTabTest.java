package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ChatTabTest {

    @After
    public void cleanUp() {
        ChatWindowLayout.reset();
        ClientChatChannelState.clear();
    }

    @Test
    public void plainTabsAreSharedAndWhispersCompareByPartner() {
        assertSame(ChatTab.of(ChatChannel.OOC), ChatTab.of(ChatChannel.OOC));
        assertFalse(ChatTab.of(ChatChannel.OOC).isWhisper());
        assertEquals("ooc", ChatTab.of(ChatChannel.OOC).id());
        assertSame(ChatTab.of(ChatChannel.OOC), ChatTab.fromId("ooc"));
        ChatTab steve = ChatTab.whisper("Steve");
        assertNotNull(steve);
        assertTrue(steve.isWhisper());
        assertEquals(ChatChannel.WHISPER, steve.getChannel());
        assertEquals("Steve", steve.getPartner());
        assertEquals(steve, ChatTab.whisper("steve "));
        assertEquals(steve.hashCode(), ChatTab.whisper("STEVE").hashCode());
        assertFalse(steve.equals(ChatTab.whisper("Alex")));
        assertEquals("whisper:Steve", steve.id());
        assertEquals(steve, ChatTab.fromId("whisper:Steve"));
        assertNull(ChatTab.whisper(" "));
        assertNull(ChatTab.fromId("whisper:"));
        assertNull(ChatTab.fromId("whisper"));
        assertNull(ChatTab.fromId("nope"));
        assertNull(ChatTab.fromId(null));
        assertEquals("Steve", ClientChatChannelState.displayName(steve));
        // An NPC of the same name is a conversation of its own.
        ChatTab npc = ChatTab.npc("Steve");
        assertTrue(npc.isNpc());
        assertTrue(npc.isWhisper());
        assertFalse(npc.equals(steve));
        assertEquals("npc:Steve", npc.id());
        assertEquals(npc, ChatTab.fromId("npc:steve"));
        assertFalse(steve.isNpc());
        assertEquals("Steve", ClientChatChannelState.displayName(npc));
        assertTrue(ClientChatChannelState.canSend(npc));
        assertTrue(ClientChatChannelState.isAvailable(steve));
        assertTrue(ClientChatChannelState.canSend(steve));
    }

    @Test
    public void whispersOpenOnceInTheAskingWindowAndPersist() {
        ChatTab steve = ChatWindowLayout.openWhisper("Steve", "w2");
        assertNotNull(steve);
        assertTrue(ChatWindowLayout.window("w2").contains(steve));
        // Opened again, the same tab with its original casing, wherever
        // it was asked for; the front tab is left alone.
        ChatTab again = ChatWindowLayout.openWhisper("steve", "w1");
        assertEquals(steve, again);
        assertEquals("Steve", again.getPartner());
        assertFalse(ChatWindowLayout.window("w1").contains(steve));
        assertEquals(ChatTab.of(ChatChannel.ALL),
                ChatWindowLayout.window("w2").getActiveTab());
        assertEquals(1, countWhispers());
        // A locked preferred window is passed over for an unlocked one.
        ChatWindowLayout.setLocked("w1", true);
        ChatTab alex = ChatWindowLayout.openWhisper("Alex", "w1");
        assertTrue(ChatWindowLayout.window("w2").contains(alex));
        // Whispers cycle like any tab and never count as closed channels.
        ClientChatChannelState.select(steve);
        assertEquals(steve, ClientChatChannelState.getSelected());
        assertTrue(ChatWindowLayout.closedChannels().isEmpty());
        ChatWindowLayout.close(steve);
        assertTrue(ChatWindowLayout.closedChannels().isEmpty());
        assertFalse(ChatWindowLayout.isOpen(steve));
        assertNull(ChatWindowLayout.openWhisper("", "w2"));
        // Conversations are not layout: neither the tab nor its mute is
        // written, and an older file's whisper tab is dropped on load.
        ChatWindowLayout.setMuted(alex, true);
        assertTrue(ChatWindowLayout.isMuted(alex));
        List<String> lines = ChatWindowLayoutStore.describe();
        assertFalse(lines.contains("muted whisper:Alex"));
        for (String line : lines) {
            assertFalse(line.contains("whisper:"));
        }
        lines.add("muted npc:Grey Wanderer");
        ChatWindowLayout.reset();
        ChatWindowLayoutStore.load(lines);
        assertFalse(ChatWindowLayout.isOpen(alex));
        assertFalse(ChatWindowLayout.isMuted(alex));
        assertEquals(Arrays.asList(ChatChannel.ALL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC, ChatChannel.DISCORD,
                ChatChannel.PARTY), ChatWindowLayout.window("w2").getChannels());
        // They also end with the session: closed along with the history.
        ChatTab wanderer = ChatWindowLayout.openTab(
                ChatTab.npc("Grey Wanderer"), "w2");
        ChatWindowLayout.detach(wanderer, 0.0D, 0.0D);
        assertEquals(3, ChatWindowLayout.windows().size());
        ChatWindowLayout.closeConversations();
        assertEquals(2, ChatWindowLayout.windows().size());
        assertFalse(ChatWindowLayout.isOpen(wanderer));
        assertEquals(Arrays.asList(ChatChannel.ALL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC, ChatChannel.DISCORD,
                ChatChannel.PARTY), ChatWindowLayout.window("w2").getChannels());
    }

    private static int countWhispers() {
        int count = 0;
        for (ChatTab tab : ChatWindowLayout.order()) {
            if (tab.isWhisper()) {
                count++;
            }
        }
        return count;
    }
}
