package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class ChatWindowLayoutStoreTest {

    @Before
    public void reset() {
        ChatWindowLayout.reset();
    }

    @After
    public void cleanUp() {
        ChatWindowLayout.reset();
    }

    @Test
    public void describeRoundTripsThroughLoad() {
        ChatWindowLayout.detach(ChatChannel.PARTY, 62.5D, 8.0D);
        ChatWindowLayout.moveTab(ChatChannel.FACTION, "w3", 1);
        ChatWindowLayout.setLocked("w3", true);
        ChatWindowLayout.close(ChatChannel.ADMIN);
        ChatWindowLayout.setMuted(ChatChannel.OOC, true);
        ChatWindowLayout.setPingsMuted(ChatTab.of(ChatChannel.PARTY), true);
        ChatWindowLayout.setHidden(ChatTab.of(ChatChannel.ADMIN), true);
        ChatWindowLayout.setActiveTab(ChatChannel.OOC);
        ChatWindowLayout.setPosition("w2", 3.0D, 97.5D, true);
        ChatWindowLayout.setFeedPosition(12.25D, 88.0D, true);
        ChatWindowLayout.link("w3", "w2", true);
        ChatWindowLayout.setToolbarCollapsed(true);
        List<String> lines = ChatWindowLayoutStore.describe();
        assertTrue(lines.contains("feed x=12.25 y=88.00"));
        assertTrue(lines.contains("toolbar collapsed=true"));
        assertTrue(lines.contains("window w1 locked=false x=0.00 y=0.00 "
                + "active=console tabs=console"));
        assertTrue(lines.contains("window w2 locked=false x=3.00 y=97.50 "
                + "active=ooc tabs=all,proximity,ooc,discord"));
        assertTrue(lines.contains("window w3 locked=true x=62.50 y=8.00 "
                + "active=faction link=w2:above tabs=party,faction"));
        assertTrue(lines.contains("closed admin"));
        assertTrue(lines.contains("muted ooc"));
        assertTrue(lines.contains("noping party"));
        assertTrue(lines.contains("hidden admin"));

        ChatWindowLayout.reset();
        ChatWindowLayoutStore.load(lines);
        assertEquals(3, ChatWindowLayout.windows().size());
        assertEquals(12.25D, ChatWindowLayout.feedOffsetX(), 0.0001D);
        assertEquals(88.0D, ChatWindowLayout.feedOffsetY(), 0.0001D);
        ChatWindow w2 = ChatWindowLayout.window("w2");
        assertEquals(Arrays.asList(ChatChannel.ALL, ChatChannel.PROXIMITY,
                ChatChannel.OOC, ChatChannel.DISCORD), w2.getChannels());
        assertEquals(3.0D, w2.getOffsetX(), 0.0001D);
        assertEquals(97.5D, w2.getOffsetY(), 0.0001D);
        assertEquals(ChatChannel.OOC, w2.getActiveChannel());
        assertTrue(ChatWindowLayout.isToolbarCollapsed());
        ChatWindow w3 = ChatWindowLayout.window("w3");
        assertNotNull(w3);
        assertEquals(Arrays.asList(ChatChannel.PARTY, ChatChannel.FACTION),
                w3.getChannels());
        assertEquals(ChatChannel.FACTION, w3.getActiveChannel());
        assertTrue(w3.isLocked());
        assertEquals("w2", w3.getLinkTarget());
        assertTrue(w3.isLinkedAbove());
        assertEquals(62.5D, w3.getOffsetX(), 0.0001D);
        assertEquals(8.0D, w3.getOffsetY(), 0.0001D);
        assertEquals(Collections.singletonList(ChatChannel.ADMIN),
                ChatWindowLayout.closedChannels());
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.OOC));
        assertFalse(ChatWindowLayout.isPingsMuted(ChatChannel.OOC));
        assertTrue(ChatWindowLayout.isPingsMuted(ChatChannel.PARTY));
        assertFalse(ChatWindowLayout.isMuted(ChatChannel.PARTY));
        assertTrue(ChatWindowLayout.isHidden(ChatChannel.ADMIN));
        assertFalse(ChatWindowLayout.isHidden(ChatChannel.OOC));
        assertEquals(lines, ChatWindowLayoutStore.describe());
    }

    @Test
    public void malformedLinesAreSkippedAndTheLayoutRepaired() {
        ChatWindowLayoutStore.load(Arrays.asList(
                "# comment",
                "",
                "window main locked=maybe active=nope tabs=all,,unknown,ooc",
                "window w2 x=abc y=12 tabs=party,party",
                "window  badid tabs=faction",
                "window w9",
                "closed",
                "closed admin extra",
                "closed admin",
                "muted nothing",
                "muted console",
                "nofeed faction",
                "input y=40 x=oops",
                "feed y=40 x=oops",
                "garbage line here"));
        assertEquals(0.0D, ChatWindowLayout.feedOffsetX(), 0.0D);
        assertEquals(40.0D, ChatWindowLayout.feedOffsetY(), 0.0D);
        // The legacy main window becomes an ordinary window, at vanilla's
        // chat spot when the line carries no position. A line from a
        // layout version with a separate input bar is simply skipped.
        ChatWindow main = ChatWindowLayout.firstWindow();
        // Numbered past the highest id the file names, the empty "w9".
        assertEquals("w10", main.getId());
        assertEquals(0.0D, main.getOffsetX(), 0.0D);
        assertEquals(100.0D, main.getOffsetY(), 0.0D);
        // Unknown ids dropped, unplaced channels appended, Admin closed.
        assertEquals(Arrays.asList(ChatChannel.ALL, ChatChannel.OOC,
                ChatChannel.PROXIMITY, ChatChannel.FACTION,
                ChatChannel.DISCORD, ChatChannel.CONSOLE), main.getChannels());
        assertEquals(ChatChannel.ALL, main.getActiveChannel());
        assertEquals(2, ChatWindowLayout.windows().size());
        ChatWindow w2 = ChatWindowLayout.window("w2");
        assertNotNull(w2);
        assertEquals(Collections.singletonList(ChatChannel.PARTY),
                w2.getChannels());
        assertEquals(0.0D, w2.getOffsetX(), 0.0D);
        assertEquals(12.0D, w2.getOffsetY(), 0.0D);
        assertEquals(Collections.singletonList(ChatChannel.ADMIN),
                ChatWindowLayout.closedChannels());
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.CONSOLE));
        // An older file's feed-only mute reads as today's mute.
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.FACTION));
    }
}
