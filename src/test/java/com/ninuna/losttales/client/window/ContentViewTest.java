package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.chat.ChatLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A page filling its window: the window is laid out with its row, strip
 * and bar past its edges while the page fills it, its own fill untouched;
 * each window has its own, and gives it up once another page comes in
 * front there or the window is gone.
 */
public final class ContentViewTest {
    private final BarLeadTest.Tab map = new BarLeadTest.Tab("Map", true);
    private final BarLeadTest.Tab journal = new BarLeadTest.Tab("Journal", true);
    private final BarLeadTest.Tab global = new BarLeadTest.Tab("Global", true);

    @Before
    public void reset() {
        ChatLayout.reset();
        WindowLayout.load(Collections.<WindowLayout.WindowSpec>emptyList());
        ContentView.leaveAll();
    }

    @After
    public void cleanUp() {
        ContentView.leaveAll();
        ChatLayout.reset();
    }

    private static List<WindowTab> row(WindowTab... tabs) {
        return new ArrayList<WindowTab>(Arrays.asList(tabs));
    }

    @Test
    public void aPageFillsItsWindowUntilAnotherComesInFront() {
        Window window = WindowLayout.addWindow(row(this.map, this.journal),
                this.map);
        assertTrue(ContentView.enter(window));
        assertTrue(ContentView.isOn(window));
        assertEquals(Window.ScreenFill.CONTENT, ContentView.fillOf(window));
        assertEquals("never the window's own fill", Window.ScreenFill.NONE,
                window.getFill());
        ContentView.follow();
        assertTrue(ContentView.isOn(window));
        WindowLayout.setActiveTab(this.journal);
        ContentView.follow();
        assertFalse(ContentView.isOn(window));
        assertEquals(Window.ScreenFill.NONE, ContentView.fillOf(window));
    }

    /**
     * While playing, a window pinned to the HUD shows its page filling it,
     * whether or not the player let it fill the window on the screen.
     */
    @Test
    public void aPinnedWindowShowsItsPageFillingItWhilePlaying() {
        Window window = WindowLayout.addWindow(row(this.map), this.map);
        assertEquals(Window.ScreenFill.NONE, ContentView.fillOf(window));
        WindowView.beginPinnedPass();
        try {
            assertEquals(Window.ScreenFill.CONTENT,
                    ContentView.fillOf(window));
        } finally {
            WindowView.endPinnedPass();
        }
        assertEquals(Window.ScreenFill.NONE, ContentView.fillOf(window));
    }

    @Test
    public void eachWindowHasItsOwn() {
        Window first = WindowLayout.addWindow(row(this.global), this.global);
        Window second = WindowLayout.addWindow(row(this.map), this.map);
        assertTrue(ContentView.enter(first));
        assertTrue(ContentView.enter(second));
        assertTrue(ContentView.isOn(first));
        assertTrue(ContentView.isOn(second.getId()));
        assertTrue(ContentView.leave(first));
        assertFalse("given back once", ContentView.leave(first));
        assertTrue("the other keeps its own", ContentView.isOn(second));
        ContentView.leaveAll();
        assertFalse(ContentView.isOn(second));
    }

    @Test
    public void aClosedWindowGivesItsPageBack() {
        Window window = WindowLayout.addWindow(row(this.map), this.map);
        assertTrue(ContentView.enter(window));
        WindowLayout.load(Collections.<WindowLayout.WindowSpec>emptyList());
        ContentView.follow();
        assertFalse(ContentView.isOn(window));
        assertFalse("nothing to fill", ContentView.enter(null));
        assertFalse(ContentView.leave(null));
    }
}
