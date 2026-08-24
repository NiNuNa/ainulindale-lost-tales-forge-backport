package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A window's own height: continuous, bounded, remembered, and written
 * down beside its position. Only the model is exercised here — the
 * corner handle itself needs a screen.
 */
public final class ChatWindowResizeTest {

    @Before
    public void reset() {
        ChatWindowLayout.reset();
    }

    @After
    public void cleanUp() {
        ChatWindowLayout.reset();
    }

    @Test
    public void aWindowFollowsTheGameSettingUntilItIsResized() {
        ChatWindow window = ChatWindowLayout.firstWindow();
        assertEquals(0.0D, window.getMaxLines(), 0.0D);
        assertTrue(ChatWindowLayout.setWindowLines(window.getId(), 12, true));
        assertEquals(12.0D, window.getMaxLines(), 0.0D);
        // Zero gives the window back to the game's chat-height setting.
        assertTrue(ChatWindowLayout.setWindowLines(window.getId(), 0, true));
        assertEquals(0.0D, window.getMaxLines(), 0.0D);
        assertFalse(ChatWindowLayout.setWindowLines("nowhere", 12, true));
    }

    /**
     * Height is continuous: a drag that lands part-way through a line
     * keeps the fraction instead of snapping to the nearest whole line.
     */
    @Test
    public void heightsKeepTheFractionTheyWereGiven() {
        ChatWindow window = ChatWindowLayout.firstWindow();
        ChatWindowLayout.setWindowLines(window.getId(), 12.37D, true);
        assertEquals(12.37D, window.getMaxLines(), 1.0E-9D);
    }

    @Test
    public void heightsAreBounded() {
        ChatWindow window = ChatWindowLayout.firstWindow();
        ChatWindowLayout.setWindowLines(window.getId(), 9999, true);
        assertEquals(ChatWindowLayout.MAX_WINDOW_LINES,
                window.getMaxLines(), 0.0D);
        ChatWindowLayout.setWindowLines(window.getId(), -4, true);
        assertEquals(0.0D, window.getMaxLines(), 0.0D);
        assertEquals(0.0D, ChatWindowLayout.clampWindowLines(0), 0.0D);
        assertEquals(ChatWindowLayout.MIN_WINDOW_LINES,
                ChatWindowLayout.clampWindowLines(0.25D), 0.0D);
    }

    @Test
    public void heightIsWrittenAndReadBack() {
        ChatWindowLayout.detach(ChatChannel.PARTY, 40.0D, 20.0D);
        ChatWindowLayout.setWindowLines("w3", 14.25D, true);
        List<String> lines = ChatWindowLayoutStore.describe();
        boolean found = false;
        for (String line : lines) {
            if (line.startsWith("window w3 ")) {
                found = line.contains(" lines=14.25 ");
            }
            // A window at the game's own height says nothing about it.
            if (line.startsWith("window w1 ")) {
                assertFalse(line.contains("lines="));
            }
        }
        assertTrue("w3 did not record its height", found);

        ChatWindowLayout.reset();
        ChatWindowLayoutStore.load(lines);
        assertEquals(14.25D, ChatWindowLayout.window("w3").getMaxLines(),
                1.0E-9D);
        assertEquals(0.0D, ChatWindowLayout.window("w1").getMaxLines(), 0.0D);
    }

    /** A hand-edited or older file must never leave a broken height. */
    @Test
    public void unreadableHeightsFallBackToTheGameSetting() {
        ChatWindowLayoutStore.load(java.util.Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 lines=zzz "
                        + "active=all tabs=all",
                "window w2 locked=false x=0.00 y=50.00 lines=9999 "
                        + "active=ooc tabs=ooc",
                "window w3 locked=false x=0.00 y=90.00 active=party "
                        + "tabs=party",
                // A whole-number height from a file written before the
                // height was continuous still reads as it always did.
                "window w4 locked=false x=0.00 y=30.00 lines=8 "
                        + "active=faction tabs=faction"));
        assertEquals(0.0D, ChatWindowLayout.window("w1").getMaxLines(), 0.0D);
        assertEquals(ChatWindowLayout.MAX_WINDOW_LINES,
                ChatWindowLayout.window("w2").getMaxLines(), 0.0D);
        assertEquals(0.0D, ChatWindowLayout.window("w3").getMaxLines(), 0.0D);
        assertEquals(8.0D, ChatWindowLayout.window("w4").getMaxLines(), 0.0D);
    }

    /**
     * A width belongs to one window: bounded, written down beside its
     * height, and read back for that window alone. The closed-chat feed
     * and any window without one keep the game's own chat width.
     */
    @Test
    public void aWindowCarriesItsOwnWidth() {
        assertEquals(0, ChatWindowLayout.clampChatWidth(0));
        assertEquals(0, ChatWindowLayout.clampChatWidth(-20));
        assertEquals(ChatWindowLayout.MIN_CHAT_WIDTH,
                ChatWindowLayout.clampChatWidth(1));
        assertEquals(ChatWindowLayout.MAX_CHAT_WIDTH,
                ChatWindowLayout.clampChatWidth(99999));
        assertEquals(320, ChatWindowLayout.clampChatWidth(320));

        ChatWindowLayout.detach(ChatChannel.PARTY, 40.0D, 20.0D);
        assertTrue(ChatWindowLayout.setWindowWidth("w3", 420, true));
        assertEquals(420, ChatWindowLayout.window("w3").getWidth());
        // Its neighbours are untouched: widths are not shared.
        assertEquals(0, ChatWindowLayout.window("w1").getWidth());
        assertFalse(ChatWindowLayout.setWindowWidth("nowhere", 420, true));

        List<String> lines = ChatWindowLayoutStore.describe();
        boolean found = false;
        for (String line : lines) {
            if (line.startsWith("window w3 ")) {
                found = line.contains(" width=420 ");
            }
            if (line.startsWith("window w1 ")) {
                assertFalse(line.contains("width="));
            }
        }
        assertTrue("w3 did not record its width", found);

        ChatWindowLayout.reset();
        ChatWindowLayoutStore.load(lines);
        assertEquals(420, ChatWindowLayout.window("w3").getWidth());
        assertEquals(0, ChatWindowLayout.window("w1").getWidth());
    }

    @Test
    public void anUnreadableWidthFollowsTheGameSetting() {
        ChatWindowLayoutStore.load(java.util.Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 width=zzz "
                        + "active=all tabs=all",
                "window w2 locked=false x=0.00 y=50.00 width=3 "
                        + "active=ooc tabs=ooc"));
        assertEquals(0, ChatWindowLayout.window("w1").getWidth());
        assertEquals(ChatWindowLayout.MIN_CHAT_WIDTH,
                ChatWindowLayout.window("w2").getWidth());
    }

    /** A window sticks to any of four sides, and the file remembers which. */
    @Test
    public void windowsStickToAnySideAndAreReadBack() {
        ChatWindowLayout.detach(ChatChannel.PARTY, 40.0D, 20.0D);
        assertTrue(ChatWindowLayout.link("w3", "w2",
                ChatWindow.LinkSide.RIGHT));
        assertEquals(ChatWindow.LinkSide.RIGHT,
                ChatWindowLayout.window("w3").getLinkSide());
        assertFalse(ChatWindowLayout.window("w3").isLinkedAbove());
        assertTrue(ChatWindowLayout.window("w3").isLinked());

        List<String> lines = ChatWindowLayoutStore.describe();
        boolean found = false;
        for (String line : lines) {
            if (line.startsWith("window w3 ")) {
                found = line.contains(" link=w2:right ");
            }
        }
        assertTrue("w3 did not record which side it is stuck to", found);
        ChatWindowLayout.reset();
        ChatWindowLayoutStore.load(lines);
        assertEquals(ChatWindow.LinkSide.RIGHT,
                ChatWindowLayout.window("w3").getLinkSide());
    }

    /** A file written before sides existed still reads as above or below. */
    @Test
    public void olderFilesKeepTheirTopAndBottomLinks() {
        ChatWindowLayoutStore.load(java.util.Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 active=all tabs=all",
                "window w2 locked=false x=0.00 y=50.00 link=w1:above "
                        + "active=ooc tabs=ooc",
                "window w3 locked=false x=0.00 y=90.00 link=w1:below "
                        + "active=party tabs=party"));
        assertEquals(ChatWindow.LinkSide.ABOVE,
                ChatWindowLayout.window("w2").getLinkSide());
        assertEquals(ChatWindow.LinkSide.BELOW,
                ChatWindowLayout.window("w3").getLinkSide());
    }

    /** Dragging any of a stuck pair moves the pair: the chain has one root. */
    @Test
    public void aStuckChainHasOneRoot() {
        ChatWindowLayout.detach(ChatChannel.PARTY, 40.0D, 20.0D);
        ChatWindowLayout.link("w3", "w2", ChatWindow.LinkSide.RIGHT);
        assertEquals(ChatWindowLayout.window("w2"),
                ChatWindowLayout.linkRoot(ChatWindowLayout.window("w3")));
        assertEquals(ChatWindowLayout.window("w2"),
                ChatWindowLayout.linkRoot(ChatWindowLayout.window("w2")));
        // A window sticking to one that sticks back never loops.
        ChatWindowLayout.link("w2", "w3", ChatWindow.LinkSide.LEFT);
        assertNotNull(ChatWindowLayout.linkRoot(
                ChatWindowLayout.window("w3")));
    }
}
