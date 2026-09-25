package com.ninuna.losttales.client.window;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.chat.ChatLayout;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A window's own height and width, in GUI pixels: continuous, bounded,
 * remembered, and written down beside its position. Only the model is exercised here — the
 * corner handle itself needs a screen.
 */
public final class WindowResizeTest {

    @Before
    public void reset() {
        ChatLayout.reset();
    }

    @After
    public void cleanUp() {
        ChatLayout.reset();
    }

    @Test
    public void aWindowFollowsTheGameSettingUntilItIsResized() {
        Window window = WindowLayout.firstWindow();
        assertEquals(0.0D, window.getOwnHeight(), 0.0D);
        assertTrue(WindowLayout.setWindowHeight(window.getId(), 180.0D, true));
        assertEquals(180.0D, window.getOwnHeight(), 0.0D);
        // Zero gives the window back to the game's chat-height setting.
        assertTrue(WindowLayout.setWindowHeight(window.getId(), 0.0D, true));
        assertEquals(0.0D, window.getOwnHeight(), 0.0D);
        assertFalse(WindowLayout.setWindowHeight("nowhere", 180.0D, true));
    }

    /**
     * Height is continuous: a drag that lands part-way through a pixel
     * keeps the fraction instead of snapping to a whole one.
     */
    @Test
    public void heightsKeepTheFractionTheyWereGiven() {
        Window window = WindowLayout.firstWindow();
        WindowLayout.setWindowHeight(window.getId(), 180.37D, true);
        assertEquals(180.37D, window.getOwnHeight(), 1.0E-9D);
    }

    @Test
    public void heightsAreBounded() {
        Window window = WindowLayout.firstWindow();
        WindowLayout.setWindowHeight(window.getId(), 99999.0D, true);
        assertEquals(WindowLayout.MAX_WINDOW_SIZE, window.getOwnHeight(),
                0.0D);
        WindowLayout.setWindowHeight(window.getId(), -4.0D, true);
        assertEquals(0.0D, window.getOwnHeight(), 0.0D);
        assertEquals(0.0D, WindowLayout.clampWindowHeight(0.0D), 0.0D);
        assertEquals(WindowLayout.MIN_WINDOW_SIZE,
                WindowLayout.clampWindowHeight(0.25D), 0.0D);
    }

    @Test
    public void heightIsWrittenAndReadBack() {
        ChatLayout.detach(ChatChannel.PARTY, 40.0D, 20.0D);
        WindowLayout.setWindowHeight("w3", 214.25D, true);
        List<String> lines = WindowLayoutStore.describe();
        boolean found = false;
        for (String line : lines) {
            if (line.startsWith("window w3 ")) {
                found = line.contains(" height=214.25 ");
            }
            // A window at the game's own height says nothing about it.
            if (line.startsWith("window w1 ")) {
                assertFalse(line.contains("height="));
            }
        }
        assertTrue("w3 did not record its height", found);

        ChatLayout.reset();
        WindowLayoutStore.load(lines);
        assertEquals(214.25D, WindowLayout.window("w3").getOwnHeight(),
                1.0E-9D);
        assertEquals(0.0D, WindowLayout.window("w1").getOwnHeight(), 0.0D);
    }

    /** A hand-edited file must never leave a broken height. */
    @Test
    public void unreadableHeightsFallBackToTheGameSetting() {
        WindowLayoutStore.load(java.util.Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 height=zzz "
                        + "active=global tabs=global",
                "window w2 locked=false x=0.00 y=50.00 height=99999 "
                        + "active=ooc tabs=ooc",
                "window w3 locked=false x=0.00 y=90.00 active=party "
                        + "tabs=party"));
        assertEquals(0.0D, WindowLayout.window("w1").getOwnHeight(), 0.0D);
        assertEquals(WindowLayout.MAX_WINDOW_SIZE,
                WindowLayout.window("w2").getOwnHeight(), 0.0D);
        assertEquals(0.0D, WindowLayout.window("w3").getOwnHeight(), 0.0D);
    }

    /**
     * A width belongs to one window: bounded, written down beside its
     * height, and read back for that window alone. The closed-chat feed
     * and any window without one keep the game's own chat width.
     */
    @Test
    public void aWindowCarriesItsOwnWidth() {
        assertEquals(0, WindowLayout.clampWindowWidth(0));
        assertEquals(0, WindowLayout.clampWindowWidth(-20));
        assertEquals(WindowLayout.MIN_WINDOW_SIZE,
                WindowLayout.clampWindowWidth(1));
        assertEquals(WindowLayout.MAX_WINDOW_SIZE,
                WindowLayout.clampWindowWidth(99999));
        assertEquals(320, WindowLayout.clampWindowWidth(320));

        ChatLayout.detach(ChatChannel.PARTY, 40.0D, 20.0D);
        assertTrue(WindowLayout.setWindowWidth("w3", 420, true));
        assertEquals(420, WindowLayout.window("w3").getOwnWidth());
        // Its neighbours are untouched: widths are not shared.
        assertEquals(0, WindowLayout.window("w1").getOwnWidth());
        assertFalse(WindowLayout.setWindowWidth("nowhere", 420, true));

        List<String> lines = WindowLayoutStore.describe();
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

        ChatLayout.reset();
        WindowLayoutStore.load(lines);
        assertEquals(420, WindowLayout.window("w3").getOwnWidth());
        assertEquals(0, WindowLayout.window("w1").getOwnWidth());
    }

    @Test
    public void anUnreadableWidthFollowsTheGameSetting() {
        WindowLayoutStore.load(java.util.Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 width=zzz "
                        + "active=global tabs=global",
                "window w2 locked=false x=0.00 y=50.00 width=3 "
                        + "active=ooc tabs=ooc"));
        assertEquals(0, WindowLayout.window("w1").getOwnWidth());
        assertEquals(WindowLayout.MIN_WINDOW_SIZE,
                WindowLayout.window("w2").getOwnWidth());
    }

    /** A window sticks to any of four sides, and the file remembers which. */
    @Test
    public void windowsStickToAnySideAndAreReadBack() {
        ChatLayout.detach(ChatChannel.PARTY, 40.0D, 20.0D);
        assertTrue(WindowLayout.link("w3", "w2",
                Window.LinkSide.RIGHT));
        assertEquals(Window.LinkSide.RIGHT,
                WindowLayout.window("w3").getLinkSide());
        assertFalse(WindowLayout.window("w3").isLinkedAbove());
        assertTrue(WindowLayout.window("w3").isLinked());

        List<String> lines = WindowLayoutStore.describe();
        boolean found = false;
        for (String line : lines) {
            if (line.startsWith("window w3 ")) {
                found = line.contains(" link=w2:right ");
            }
        }
        assertTrue("w3 did not record which side it is stuck to", found);
        ChatLayout.reset();
        WindowLayoutStore.load(lines);
        assertEquals(Window.LinkSide.RIGHT,
                WindowLayout.window("w3").getLinkSide());
    }

    /** A file written before sides existed still reads as above or below. */
    @Test
    public void olderFilesKeepTheirTopAndBottomLinks() {
        WindowLayoutStore.load(java.util.Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 active=global tabs=global",
                "window w2 locked=false x=0.00 y=50.00 link=w1:above "
                        + "active=ooc tabs=ooc",
                "window w3 locked=false x=0.00 y=90.00 link=w1:below "
                        + "active=party tabs=party"));
        assertEquals(Window.LinkSide.ABOVE,
                WindowLayout.window("w2").getLinkSide());
        assertEquals(Window.LinkSide.BELOW,
                WindowLayout.window("w3").getLinkSide());
    }

    /** Dragging any of a stuck pair moves the pair: the chain has one root. */
    @Test
    public void aStuckChainHasOneRoot() {
        ChatLayout.detach(ChatChannel.PARTY, 40.0D, 20.0D);
        WindowLayout.link("w3", "w2", Window.LinkSide.RIGHT);
        assertEquals(WindowLayout.window("w2"),
                WindowLayout.linkRoot(WindowLayout.window("w3")));
        assertEquals(WindowLayout.window("w2"),
                WindowLayout.linkRoot(WindowLayout.window("w2")));
        // A window sticking to one that sticks back never loops.
        WindowLayout.link("w2", "w3", Window.LinkSide.LEFT);
        assertNotNull(WindowLayout.linkRoot(
                WindowLayout.window("w3")));
    }
}
