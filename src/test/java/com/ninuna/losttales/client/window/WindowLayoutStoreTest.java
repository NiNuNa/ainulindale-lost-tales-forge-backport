package com.ninuna.losttales.client.window;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.chat.ChatChannelDescriptor;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.client.chat.ChatLayout;
import com.ninuna.losttales.client.chat.ChatTab;
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

public final class WindowLayoutStoreTest {

    @Before
    public void reset() {
        ChatLayout.reset();
    }

    @After
    public void cleanUp() {
        ChatLayout.reset();
        WindowLayout.setChangeListener(null);
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
        WindowLayoutStore.load(Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 active=client_console tabs=client_console",
                "window w2 locked=false x=0.00 y=100.00 active=global tabs=global,ooc",
                "conversation\tserver:a\tw2\twhisper:Steve|Aldric",
                "closedconversation\tserver:a\twhisper:Bob",
                "conversation\tworld:My World\tw2\twhisper:Sam|Sam Gamgee"));
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.contains("conversation\tserver:a\tw2\twhisper:Steve|Aldric"));
        assertTrue(described.contains("closedconversation\tserver:a\twhisper:Bob"));
        assertTrue(described.contains(
                "conversation\tworld:My World\tw2\twhisper:Sam|Sam Gamgee"));
        ChatLayout.restoreConversations("server:a");
        assertTrue(ChatLayout.isOpen(ChatTab.whisper("Steve", "Aldric")));
        assertFalse(ChatLayout.isOpen(ChatTab.whisper("Sam", "Sam Gamgee")));
        assertTrue(ChatLayout.isHidden(ChatTab.whisper("Bob", "Bob")));
        WindowLayoutStore.load(described);
        assertEquals(described, WindowLayoutStore.describe());
    }

    @Test
    public void aWindowFillingTheScreenRoundTrips() {
        WindowLayoutStore.load(Arrays.asList(
                "window w1 locked=false x=10.00 y=20.00 height=150.00"
                        + " fill=full active=client_console tabs=client_console,operator",
                "window w2 locked=false x=0.00 y=100.00 active=global tabs=global",
                "window w3 locked=false x=0.00 y=50.00 fill=top_right"
                        + " active=ooc tabs=ooc",
                "window w4 locked=false x=0.00 y=60.00 fill=sideways"
                        + " active=party tabs=party",
                "window w5 locked=false x=0.00 y=70.00"
                        + " fill=free:0.25000,0.00000,0.75000,1.00000"
                        + " active=faction tabs=faction"));
        Window first = WindowLayout.window("w1");
        assertTrue(first.isFullscreen());
        assertEquals(Window.ScreenFill.FULL, first.getFill());
        assertEquals(10.0D, first.getOffsetX(), 0.0001D);
        assertEquals(150.0D, first.getOwnHeight(), 0.0001D);
        assertEquals(Window.ScreenFill.NONE,
                WindowLayout.window("w2").getFill());
        assertEquals(Window.ScreenFill.TOP_RIGHT,
                WindowLayout.window("w3").getFill());
        assertFalse(WindowLayout.window("w3").isFullscreen());
        assertEquals(Window.ScreenFill.NONE,
                WindowLayout.window("w4").getFill());
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.get(1).contains(" fill=full"));
        assertFalse(described.get(2).contains("fill"));
        assertTrue(described.get(3).contains(" fill=top_right"));
        assertFalse(described.get(4).contains("fill"));
        // A part the player shaped keeps its edges.
        assertEquals(Window.ScreenFill.free(0.25D, 0.0D, 0.75D, 1.0D),
                WindowLayout.window("w5").getFill());
        assertTrue(described.get(5).contains(
                " fill=free:0.25000,0.00000,0.75000,1.00000"));
        WindowLayoutStore.load(described);
        assertEquals(described, WindowLayoutStore.describe());
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
                "window w1 x=0.00 y=0.00 active=global tabs=global",
                "window w2 x=50.00 y=50.00 active=trade tabs=trade");
        WindowLayoutStore.load(lines);

        ChatChannel.installDefined(Collections.singletonList(
                new ChatChannelDescriptor("trade", "Trade",
                        ChatPresentationMode.IN_CHARACTER,
                        ChatRecipientRule.EVERYONE, ChatChannelAccess.NONE,
                        0xC9A227, false)), null);
        ChatChannel trade = ChatChannel.fromId("trade");
        assertNotNull(trade);
        assertFalse("the window was dropped when the file was read,"
                        + " because its only tab was on no channel yet",
                holdsTrade(trade));

        WindowLayoutStore.reload();

        assertTrue("the window it was arranged into is back",
                holdsTrade(trade));
    }

    /** Whether some window holds the trade channel and nothing else. */
    /** The layout is one file per account, and nothing is written before a change. */
    @Test
    public void theLayoutIsTheAccountsOwn() throws IOException {
        UUID steve = UUID.fromString("c6000000-0000-0000-0000-00000000006c");
        assertNull(WindowLayoutStore.fileFor(null, steve));
        assertNull(WindowLayoutStore.fileFor(new File("client"), null));
        assertEquals(new File(new File("client", "windows"), steve + ".txt"),
                WindowLayoutStore.fileFor(new File("client"), steve));

        File folder = File.createTempFile("losttales-layout", "");
        assertTrue(folder.delete());
        assertTrue(folder.mkdirs());
        try {
            File own = WindowLayoutStore.fileFor(folder, steve);
            write(own, "window w2 locked=false x=50.00 y=25.00 active=ooc tabs=ooc\n");
            WindowLayoutStore.initialize(folder, steve);
            Window window = WindowLayout.window("w2");
            assertNotNull("the account's own file is read", window);
            assertEquals(50.0D, window.getOffsetX(), 0.0D);
            UUID alex = UUID.fromString("d6000000-0000-0000-0000-00000000006d");
            WindowLayoutStore.initialize(folder, alex);
            assertFalse("nothing is written before the account changes anything",
                    WindowLayoutStore.fileFor(folder, alex).isFile());
        } finally {
            WindowLayoutStore.initialize(null);
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
            File own = WindowLayoutStore.fileFor(folder, steve);
            String trading =
                    "window w3 locked=false x=50.00 y=50.00 active=trade tabs=trade";
            write(own, "window w1 locked=false x=0.00 y=0.00 active=global tabs=global\n"
                    + trading + "\n"
                    + "feed x=0.00 y=100.00\n");
            WindowLayoutStore.initialize(folder, steve);
            ChatLayout.setFeedPosition(25.0D, 75.0D, false);
            ChatLayout.saveFeedPosition();

            List<String> written = Files.readAllLines(own.toPath(),
                    Charset.forName("UTF-8"));
            assertTrue(written.toString(), written.contains(trading));
            assertTrue(written.toString(), written.contains("feed x=25.00 y=75.00"));
            assertFalse(written.toString(), written.contains("feed x=0.00 y=100.00"));

            ChatChannel.installDefined(Collections.singletonList(
                    new ChatChannelDescriptor("trade", "Trade",
                            ChatPresentationMode.IN_CHARACTER,
                            ChatRecipientRule.EVERYONE, ChatChannelAccess.NONE,
                            0xC9A227, false)), null);
            WindowLayoutStore.reload();
            assertTrue("the window is back with its channel",
                    holdsTrade(ChatChannel.fromId("trade")));
            assertEquals(25.0D, ChatLayout.feedOffsetX(), 0.0D);
            assertEquals(75.0D, ChatLayout.feedOffsetY(), 0.0D);
        } finally {
            WindowLayoutStore.initialize(null);
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
        for (Window window : WindowLayout.windows()) {
            if (Collections.singletonList(trade).equals(ChatTab.channelsOf(window))) {
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
        WindowLayoutStore.load(Collections.singletonList(
                "window w1 x=0.00 y=0.00 active=global tabs=global,trade"));
        // The store marks the layout moved through this listener; a save
        // with no file behind it writes nothing.
        WindowLayout.setChangeListener(null);
        WindowLayoutStore.initialize(null);
        WindowLayoutStore.load(Collections.singletonList(
                "window w1 x=0.00 y=0.00 active=global tabs=global,trade"));
        ChatLayout.close(ChatChannel.GLOBAL);

        ChatChannel.installDefined(Collections.singletonList(
                new ChatChannelDescriptor("trade", "Trade",
                        ChatPresentationMode.IN_CHARACTER,
                        ChatRecipientRule.EVERYONE, ChatChannelAccess.NONE,
                        0xC9A227, false)), null);
        WindowLayoutStore.reload();

        assertFalse("the closed channel stays closed",
                ChatLayout.isOpen(ChatChannel.GLOBAL));
    }

    /**
     * A layout with nothing open describes itself as one — no window
     * lines, every channel closed — and comes back the same way rather
     * than as the defaults.
     */
    @Test
    public void theEmptyLayoutRoundTripsThroughLoad() {
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            ChatLayout.close(channel);
        }
        ChatLayout.setMuted(ChatChannel.OOC, true);
        assertTrue(WindowLayout.isEmpty());
        List<String> described = WindowLayoutStore.describe();
        for (String line : described) {
            assertFalse(line.startsWith("window "));
        }
        ChatLayout.reset();
        assertFalse(WindowLayout.isEmpty());
        WindowLayoutStore.load(described);
        assertTrue(WindowLayout.isEmpty());
        assertEquals(ChatChannel.presentationOrder().size(),
                ChatLayout.closedChannels().size());
        assertTrue(ChatLayout.isMuted(ChatChannel.OOC));
    }

    @Test
    public void describeRoundTripsThroughLoad() {
        ChatLayout.detach(ChatChannel.PARTY, 62.5D, 8.0D);
        ChatLayout.moveTab(ChatChannel.FACTION, "w3", 1);
        WindowLayout.setLocked("w3", true);
        ChatLayout.close(ChatChannel.OPERATOR);
        ChatLayout.setMuted(ChatChannel.OOC, true);
        ChatLayout.setPingsMuted(ChatTab.of(ChatChannel.PARTY), true);
        ChatLayout.setHidden(ChatTab.of(ChatChannel.OPERATOR), true);
        ChatLayout.setActiveTab(ChatChannel.OOC);
        WindowLayout.setPosition("w2", 3.0D, 97.5D, true);
        ChatLayout.setFeedPosition(12.25D, 88.0D, true);
        WindowLayout.link("w3", "w2", true);
        ChatLayout.setToolbarCollapsed(true);
        List<String> lines = WindowLayoutStore.describe();
        assertTrue(lines.contains("feed x=12.25 y=88.00"));
        assertTrue(lines.contains("toolbar collapsed=true"));
        assertTrue(lines.contains("window w1 locked=false x=0.00 y=0.00 "
                + "active=client_console tabs=client_console,server_console"));
        assertTrue(lines.contains("window w2 locked=false x=3.00 y=97.50 "
                + "active=ooc tabs=global,proximity,ooc"));
        assertTrue(lines.contains("window w3 locked=true x=62.50 y=8.00 "
                + "active=faction link=w2:above tabs=party,faction"));
        assertTrue(lines.contains("closed operator"));
        assertTrue(lines.contains("muted ooc"));
        assertTrue(lines.contains("noping party"));
        assertTrue(lines.contains("hidden operator"));

        ChatLayout.reset();
        WindowLayoutStore.load(lines);
        assertEquals(3, WindowLayout.windows().size());
        assertEquals(12.25D, ChatLayout.feedOffsetX(), 0.0001D);
        assertEquals(88.0D, ChatLayout.feedOffsetY(), 0.0001D);
        Window w2 = WindowLayout.window("w2");
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.OOC), ChatTab.channelsOf(w2));
        assertEquals(3.0D, w2.getOffsetX(), 0.0001D);
        assertEquals(97.5D, w2.getOffsetY(), 0.0001D);
        assertEquals(ChatChannel.OOC, ChatTab.frontChannelOf(w2));
        assertTrue(ChatLayout.isToolbarCollapsed());
        Window w3 = WindowLayout.window("w3");
        assertNotNull(w3);
        assertEquals(Arrays.asList(ChatChannel.PARTY, ChatChannel.FACTION),
                ChatTab.channelsOf(w3));
        assertEquals(ChatChannel.FACTION, ChatTab.frontChannelOf(w3));
        assertTrue(w3.isLocked());
        assertEquals("w2", w3.getLinkTarget());
        assertTrue(w3.isLinkedAbove());
        assertEquals(62.5D, w3.getOffsetX(), 0.0001D);
        assertEquals(8.0D, w3.getOffsetY(), 0.0001D);
        assertEquals(Collections.singletonList(ChatChannel.OPERATOR),
                ChatLayout.closedChannels());
        assertTrue(ChatLayout.isMuted(ChatChannel.OOC));
        assertFalse(ChatLayout.isPingsMuted(ChatChannel.OOC));
        assertTrue(ChatLayout.isPingsMuted(ChatChannel.PARTY));
        assertFalse(ChatLayout.isMuted(ChatChannel.PARTY));
        assertTrue(ChatLayout.isHidden(ChatChannel.OPERATOR));
        assertFalse(ChatLayout.isHidden(ChatChannel.OOC));
        assertEquals(lines, WindowLayoutStore.describe());
    }

    @Test
    public void malformedLinesAreSkippedAndTheLayoutRepaired() {
        WindowLayoutStore.load(Arrays.asList(
                "# comment",
                "",
                "window w1 locked=maybe active=nope tabs=global,,unknown,ooc",
                "window w2 x=abc y=12 tabs=party,party",
                "window  badid tabs=faction",
                "window w9",
                "closed",
                "closed operator extra",
                "closed operator",
                "muted nothing",
                "muted client_console",
                "muted faction",
                "input y=40 x=oops",
                "feed y=40 x=oops",
                "garbage line here"));
        assertEquals(0.0D, ChatLayout.feedOffsetX(), 0.0D);
        assertEquals(40.0D, ChatLayout.feedOffsetY(), 0.0D);
        // A window line with no position stands at the origin; an input
        // line names nothing the layout has and is skipped.
        Window main = WindowLayout.firstWindow();
        assertEquals("w1", main.getId());
        assertEquals(0.0D, main.getOffsetX(), 0.0D);
        assertEquals(0.0D, main.getOffsetY(), 0.0D);
        // Unknown ids dropped, unplaced channels appended, Operator closed.
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.OOC,
                ChatChannel.PROXIMITY, ChatChannel.FACTION,
                ChatChannel.CLIENT_CONSOLE, ChatChannel.SERVER_CONSOLE),
                ChatTab.channelsOf(main));
        assertEquals(ChatChannel.GLOBAL, ChatTab.frontChannelOf(main));
        assertEquals(2, WindowLayout.windows().size());
        Window w2 = WindowLayout.window("w2");
        assertNotNull(w2);
        assertEquals(Collections.singletonList(ChatChannel.PARTY),
                ChatTab.channelsOf(w2));
        assertEquals(0.0D, w2.getOffsetX(), 0.0D);
        assertEquals(12.0D, w2.getOffsetY(), 0.0D);
        assertEquals(Collections.singletonList(ChatChannel.OPERATOR),
                ChatLayout.closedChannels());
        assertTrue(ChatLayout.isMuted(ChatChannel.CLIENT_CONSOLE));
        // An older file's feed-only mute reads as today's mute.
        assertTrue(ChatLayout.isMuted(ChatChannel.FACTION));
    }

    /**
     * A file naming one conversation of a scoped channel is repaired:
     * the row holds one Faction tab, and which conversation it shows
     * follows the identity being read, so a stored conversation would
     * only ever be a second Faction tab beside the first.
     */
    @Test
    public void aStoredConversationOfAScopedChannelIsDropped() {
        WindowLayoutStore.load(Arrays.asList(
                "window w1 x=0.00 y=100.00 active=global "
                        + "tabs=global,faction,faction|own:"
                        + "00000000-0000-0000-0000-0000000000c1,"
                        + "faction|in:lotr:gondor"));
        Window window = WindowLayout.window("w1");
        assertNotNull(window);
        int factionTabs = 0;
        for (WindowTab each : window.tabs()) {
            ChatTab tab = (ChatTab)each;
            if (tab.getChannel() == ChatChannel.FACTION) {
                factionTabs++;
                assertTrue("the row entry, not a conversation",
                        tab.getOwnerKey().isEmpty());
            }
        }
        assertEquals("one Faction tab in the row", 1, factionTabs);
        // And nothing writes one back out.
        for (String line : WindowLayoutStore.describe()) {
            assertFalse(line, line.contains("faction|"));
        }
    }

    /**
     * A window whose timestamp area is driven out, or whose member list is
     * put away, says so in its line and keeps it across a reload; a window
     * whose line says neither shows both.
     */
    @Test
    public void aDrivenOutAreaAndAPutAwayListRoundTrip() {
        WindowLayoutStore.load(Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 area=hidden members=hidden active=global tabs=global",
                "window w2 locked=false x=0.00 y=100.00 active=ooc tabs=ooc"));
        assertTrue(ChatLayout.isAreaHidden(WindowLayout.window("w1")));
        assertTrue(ChatLayout.isMembersHidden(WindowLayout.window("w1")));
        assertFalse(ChatLayout.isAreaHidden(WindowLayout.window("w2")));
        assertFalse(ChatLayout.isMembersHidden(WindowLayout.window("w2")));
        List<String> lines = WindowLayoutStore.describe();
        boolean hiddenLine = false;
        boolean plainLine = false;
        for (String line : lines) {
            if (line.startsWith("window w1 ")) {
                hiddenLine = line.contains(" area=hidden")
                        && line.contains(" members=hidden");
            } else if (line.startsWith("window w2 ")) {
                plainLine = !line.contains("area=") && !line.contains("members=");
            }
        }
        assertTrue(hiddenLine);
        assertTrue(plainLine);
        assertTrue(ChatLayout.setAreaHidden("w2", true));
        assertFalse("asking for what stands already changes nothing",
                ChatLayout.setAreaHidden("w2", true));
        assertTrue(ChatLayout.setMembersHidden("w1", false));
        assertFalse(ChatLayout.isMembersHidden(WindowLayout.window("w1")));
    }
}
