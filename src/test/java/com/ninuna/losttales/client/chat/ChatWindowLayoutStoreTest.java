package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.chat.ChatChannelDescriptor;
import com.ninuna.losttales.chat.ChatChannelAccess;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatWindowLayoutStoreTest {

    @Before
    public void reset() {
        ChatWindowLayout.reset();
    }

    @After
    public void cleanUp() {
        ChatWindowLayout.reset();
        ChatWindowLayout.setChangeListener(null);
        ChatChannel.resetToBuiltIn();
    }

    /**
     * A window filling a part of the screen names it in its line and
     * keeps its own place and size beside it, which is what it goes
     * back to; a window without the word is at its own size, and a part
     * the build does not know reads as none.
     */
    /**
     * A place's whisper tabs, open or closed by hand, are lines of their
     * own that survive the round trip and come back for that place only.
     */
    @Test
    public void rememberedConversationsRoundTripPerPlace() {
        ChatWindowLayoutStore.load(Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 active=console tabs=console",
                "window w2 locked=false x=0.00 y=100.00 active=all tabs=all,ooc",
                "conversation\tserver:a\tw2\twhisper:Steve|Aldric",
                "closedconversation\tserver:a\twhisper:Bob",
                "conversation\tworld:My World\tw2\twhisper:Sam|Sam Gamgee"));
        List<String> described = ChatWindowLayoutStore.describe();
        assertTrue(described.contains("conversation\tserver:a\tw2\twhisper:Steve|Aldric"));
        assertTrue(described.contains("closedconversation\tserver:a\twhisper:Bob"));
        assertTrue(described.contains(
                "conversation\tworld:My World\tw2\twhisper:Sam|Sam Gamgee"));
        ChatWindowLayout.restoreConversations("server:a");
        assertTrue(ChatWindowLayout.isOpen(ChatTab.whisper("Steve", "Aldric")));
        assertFalse(ChatWindowLayout.isOpen(ChatTab.whisper("Sam", "Sam Gamgee")));
        assertTrue(ChatWindowLayout.isHidden(ChatTab.whisper("Bob", "Bob")));
        ChatWindowLayoutStore.load(described);
        assertEquals(described, ChatWindowLayoutStore.describe());
    }

    @Test
    public void aWindowFillingTheScreenRoundTrips() {
        ChatWindowLayoutStore.load(Arrays.asList(
                "window w1 locked=false x=10.00 y=20.00 lines=6.00"
                        + " fill=full active=console tabs=console,admin",
                "window w2 locked=false x=0.00 y=100.00 active=all tabs=all",
                "window w3 locked=false x=0.00 y=50.00 fill=top_right"
                        + " active=ooc tabs=ooc",
                "window w4 locked=false x=0.00 y=60.00 fill=sideways"
                        + " active=party tabs=party"));
        ChatWindow first = ChatWindowLayout.window("w1");
        assertTrue(first.isFullscreen());
        assertEquals(ChatWindow.ScreenFill.FULL, first.getFill());
        assertEquals(10.0D, first.getOffsetX(), 0.0001D);
        assertEquals(6.0D, first.getMaxLines(), 0.0001D);
        assertEquals(ChatWindow.ScreenFill.NONE,
                ChatWindowLayout.window("w2").getFill());
        assertEquals(ChatWindow.ScreenFill.TOP_RIGHT,
                ChatWindowLayout.window("w3").getFill());
        assertFalse(ChatWindowLayout.window("w3").isFullscreen());
        assertEquals(ChatWindow.ScreenFill.NONE,
                ChatWindowLayout.window("w4").getFill());
        List<String> described = ChatWindowLayoutStore.describe();
        assertTrue(described.get(1).contains(" fill=full"));
        assertFalse(described.get(2).contains("fill"));
        assertTrue(described.get(3).contains(" fill=top_right"));
        assertFalse(described.get(4).contains("fill"));
        ChatWindowLayoutStore.load(described);
        assertEquals(described, ChatWindowLayoutStore.describe());
    }

    /**
     * A window arranged around a channel the server defines is read at
     * start-up, when only the built-in channels exist, so its tab cannot
     * be resolved and is skipped. Once the server's channels are in
     * force the file is worth reading again — otherwise the next thing
     * the player moves writes the layout back without that tab, and the
     * arrangement is gone for good.
     */
    @Test
    public void aTabOnAServerDefinedChannelComesBackWhenItsChannelDoes() {
        // Trade has a window of its own, so where it went is the point
        // and not merely that it is somewhere.
        List<String> lines = Arrays.asList(
                "window w1 x=0.00 y=0.00 active=all tabs=all",
                "window w2 x=50.00 y=50.00 active=trade tabs=trade");
        ChatWindowLayoutStore.load(lines);

        ChatChannel.installDefined(Collections.singletonList(
                new ChatChannelDescriptor("trade", "Trade",
                        ChatPresentationMode.IN_CHARACTER,
                        ChatRecipientRule.GLOBAL, ChatChannelAccess.NONE,
                        0xC9A227, false)), null);
        ChatChannel trade = ChatChannel.fromId("trade");
        assertNotNull(trade);
        assertFalse("the window was dropped when the file was read,"
                        + " because its only tab was on no channel yet",
                holdsTrade(trade));

        ChatWindowLayoutStore.reloadForNewChannels();

        assertTrue("the window it was arranged into is back",
                holdsTrade(trade));
    }

    /** Whether some window holds the trade channel and nothing else. */
    /** The layout is one file per account, and nothing is written before a change. */
    @Test
    public void theLayoutIsTheAccountsOwn() throws IOException {
        UUID steve = UUID.fromString("c6000000-0000-0000-0000-00000000006c");
        assertNull(ChatWindowLayoutStore.fileFor(null, steve));
        assertNull(ChatWindowLayoutStore.fileFor(new File("client"), null));
        assertEquals(new File(new File("client", "chat/layouts"), steve + ".txt"),
                ChatWindowLayoutStore.fileFor(new File("client"), steve));

        File folder = File.createTempFile("losttales-layout", "");
        assertTrue(folder.delete());
        assertTrue(folder.mkdirs());
        try {
            File own = ChatWindowLayoutStore.fileFor(folder, steve);
            write(own, "window w2 locked=false x=50.00 y=25.00 active=ooc tabs=ooc\n");
            ChatWindowLayoutStore.initialize(folder, steve);
            ChatWindow window = ChatWindowLayout.window("w2");
            assertNotNull("the account's own file is read", window);
            assertEquals(50.0D, window.getOffsetX(), 0.0D);
            UUID alex = UUID.fromString("d6000000-0000-0000-0000-00000000006d");
            ChatWindowLayoutStore.initialize(folder, alex);
            assertFalse("nothing is written before the account changes anything",
                    ChatWindowLayoutStore.fileFor(folder, alex).isFile());
        } finally {
            ChatWindowLayoutStore.initialize(null);
            deleteTree(folder);
        }
    }

    /**
     * Moving the feed before a server's channels are in force rewrites
     * only the file's feed line: a window on one of that server's
     * channels, which the layout could not place yet, stays in the file
     * and comes back once the channels do, with the feed where it was
     * moved to.
     */
    @Test
    public void movingTheFeedEarlyKeepsAWindowOnAServersChannel()
            throws IOException {
        UUID steve = UUID.fromString("c6000000-0000-0000-0000-00000000006c");
        File folder = File.createTempFile("losttales-layout", "");
        assertTrue(folder.delete());
        assertTrue(folder.mkdirs());
        try {
            File own = ChatWindowLayoutStore.fileFor(folder, steve);
            String trading =
                    "window w3 locked=false x=50.00 y=50.00 active=trade tabs=trade";
            write(own, "window w1 locked=false x=0.00 y=0.00 active=all tabs=all\n"
                    + trading + "\n"
                    + "feed x=0.00 y=100.00\n");
            ChatWindowLayoutStore.initialize(folder, steve);
            ChatWindowLayout.setFeedPosition(25.0D, 75.0D, false);
            ChatWindowLayoutStore.saveFeedPosition();

            List<String> written = Files.readAllLines(own.toPath(),
                    Charset.forName("UTF-8"));
            assertTrue(written.toString(), written.contains(trading));
            assertTrue(written.toString(), written.contains("feed x=25.00 y=75.00"));
            assertFalse(written.toString(), written.contains("feed x=0.00 y=100.00"));

            ChatChannel.installDefined(Collections.singletonList(
                    new ChatChannelDescriptor("trade", "Trade",
                            ChatPresentationMode.IN_CHARACTER,
                            ChatRecipientRule.GLOBAL, ChatChannelAccess.NONE,
                            0xC9A227, false)), null);
            ChatWindowLayoutStore.reloadForNewChannels();
            assertTrue("the window is back with its channel",
                    holdsTrade(ChatChannel.fromId("trade")));
            assertEquals(25.0D, ChatWindowLayout.feedOffsetX(), 0.0D);
            assertEquals(75.0D, ChatWindowLayout.feedOffsetY(), 0.0D);
        } finally {
            ChatWindowLayoutStore.initialize(null);
            deleteTree(folder);
        }
    }

    private static void write(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            assertTrue(parent.mkdirs());
        }
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        file.delete();
    }

    private static boolean holdsTrade(ChatChannel trade) {
        for (ChatWindow window : ChatWindowLayout.windows()) {
            if (Collections.singletonList(trade).equals(window.getChannels())) {
                return true;
            }
        }
        return false;
    }

    /**
     * What the player has moved is newer than the file and is never
     * replaced by it.
     */
    @Test
    public void aLayoutThePlayerHasMovedIsNotReplacedByTheFile() {
        ChatWindowLayoutStore.load(Collections.singletonList(
                "window w1 x=0.00 y=0.00 active=all tabs=all,trade"));
        // The store marks the layout moved through this listener; a save
        // with no file behind it writes nothing.
        ChatWindowLayout.setChangeListener(null);
        ChatWindowLayoutStore.initialize(null);
        ChatWindowLayoutStore.load(Collections.singletonList(
                "window w1 x=0.00 y=0.00 active=all tabs=all,trade"));
        ChatWindowLayout.close(ChatChannel.ALL);

        ChatChannel.installDefined(Collections.singletonList(
                new ChatChannelDescriptor("trade", "Trade",
                        ChatPresentationMode.IN_CHARACTER,
                        ChatRecipientRule.GLOBAL, ChatChannelAccess.NONE,
                        0xC9A227, false)), null);
        ChatWindowLayoutStore.reloadForNewChannels();

        assertFalse("the closed channel stays closed",
                ChatWindowLayout.isOpen(ChatChannel.ALL));
    }

    /**
     * A layout with nothing open describes itself as one — no window
     * lines, every channel closed — and comes back the same way rather
     * than as the defaults.
     */
    @Test
    public void theEmptyLayoutRoundTripsThroughLoad() {
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            ChatWindowLayout.close(channel);
        }
        ChatWindowLayout.setMuted(ChatChannel.OOC, true);
        assertTrue(ChatWindowLayout.isEmpty());
        List<String> described = ChatWindowLayoutStore.describe();
        for (String line : described) {
            assertFalse(line.startsWith("window "));
        }
        ChatWindowLayout.reset();
        assertFalse(ChatWindowLayout.isEmpty());
        ChatWindowLayoutStore.load(described);
        assertTrue(ChatWindowLayout.isEmpty());
        assertEquals(ChatChannel.presentationOrder().size(),
                ChatWindowLayout.closedChannels().size());
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.OOC));
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
                + "active=console tabs=console,server_console"));
        assertTrue(lines.contains("window w2 locked=false x=3.00 y=97.50 "
                + "active=ooc tabs=all,proximity,ooc"));
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
                ChatChannel.OOC), w2.getChannels());
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
                "window w1 locked=maybe active=nope tabs=all,,unknown,ooc",
                "window w2 x=abc y=12 tabs=party,party",
                "window  badid tabs=faction",
                "window w9",
                "closed",
                "closed admin extra",
                "closed admin",
                "muted nothing",
                "muted console",
                "muted faction",
                "input y=40 x=oops",
                "feed y=40 x=oops",
                "garbage line here"));
        assertEquals(0.0D, ChatWindowLayout.feedOffsetX(), 0.0D);
        assertEquals(40.0D, ChatWindowLayout.feedOffsetY(), 0.0D);
        // A window line with no position stands at the origin; an input
        // line names nothing the layout has and is skipped.
        ChatWindow main = ChatWindowLayout.firstWindow();
        assertEquals("w1", main.getId());
        assertEquals(0.0D, main.getOffsetX(), 0.0D);
        assertEquals(0.0D, main.getOffsetY(), 0.0D);
        // Unknown ids dropped, unplaced channels appended, Admin closed.
        assertEquals(Arrays.asList(ChatChannel.ALL, ChatChannel.OOC,
                ChatChannel.PROXIMITY, ChatChannel.FACTION,
                ChatChannel.CONSOLE, ChatChannel.SERVER_CONSOLE),
                main.getChannels());
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

    /**
     * A file naming one conversation of a scoped channel is repaired:
     * the row holds one Faction tab, and which conversation it shows
     * follows the identity being read, so a stored conversation would
     * only ever be a second Faction tab beside the first.
     */
    @Test
    public void aStoredConversationOfAScopedChannelIsDropped() {
        ChatWindowLayoutStore.load(Arrays.asList(
                "window w1 x=0.00 y=100.00 active=all "
                        + "tabs=all,faction,faction|own:"
                        + "00000000-0000-0000-0000-0000000000c1,"
                        + "faction|in:lotr:gondor"));
        ChatWindow window = ChatWindowLayout.window("w1");
        assertNotNull(window);
        int factionTabs = 0;
        for (ChatTab tab : window.tabs()) {
            if (tab.getChannel() == ChatChannel.FACTION) {
                factionTabs++;
                assertTrue("the row entry, not a conversation",
                        tab.getOwnerKey().isEmpty());
            }
        }
        assertEquals("one Faction tab in the row", 1, factionTabs);
        // And nothing writes one back out.
        for (String line : ChatWindowLayoutStore.describe()) {
            assertFalse(line, line.contains("faction|"));
        }
    }

}
