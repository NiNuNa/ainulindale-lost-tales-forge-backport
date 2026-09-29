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
 * A tab standing alone in full screen: its window is laid out past the
 * screen's edges while it stands, its own fill untouched, and it goes back
 * into its window once another tab comes in front there, the window is
 * gone, or the keys go to another window.
 */
public final class ContentViewTest {
    private final BarLeadTest.Tab map = new BarLeadTest.Tab("Map", true);
    private final BarLeadTest.Tab journal = new BarLeadTest.Tab("Journal", true);
    private final BarLeadTest.Tab global = new BarLeadTest.Tab("Global", true);

    @Before
    public void reset() {
        ChatLayout.reset();
        WindowLayout.load(Collections.<WindowLayout.WindowSpec>emptyList());
        ContentView.leave();
    }

    @After
    public void cleanUp() {
        ContentView.leave();
        ChatLayout.reset();
    }

    private static List<WindowTab> row(WindowTab... tabs) {
        return new ArrayList<WindowTab>(Arrays.asList(tabs));
    }

    @Test
    public void aTabStandsAloneUntilAnotherComesInFront() {
        Window window = WindowLayout.addWindow(row(this.map, this.journal),
                this.map, 0.0D, 0.0D);
        assertTrue(ContentView.enter(window));
        assertTrue(ContentView.isOn(window));
        assertEquals(Window.ScreenFill.CONTENT, ContentView.fillOf(window));
        assertEquals("never the window's own fill", Window.ScreenFill.NONE,
                window.getFill());
        ContentView.follow(window, false);
        ContentView.follow(null, false);
        assertTrue("keys in its window, or nowhere, keep it", ContentView.isOn());
        WindowLayout.setActiveTab(this.journal);
        ContentView.follow(window, false);
        assertFalse(ContentView.isOn());
        assertEquals(Window.ScreenFill.NONE, ContentView.fillOf(window));
    }

    @Test
    public void theKeysGoingToAnotherWindowPutItBack() {
        Window first = WindowLayout.addWindow(row(this.global), this.global,
                0.0D, 0.0D);
        Window second = WindowLayout.addWindow(row(this.map), this.map,
                50.0D, 50.0D);
        assertTrue(ContentView.enter(first));
        ContentView.follow(second, false);
        assertFalse(ContentView.isOn());
        assertTrue(ContentView.enter(first));
        assertTrue(ContentView.leave());
        assertFalse("put back once", ContentView.leave());
    }

    @Test
    public void aClosedWindowPutsItsTabBack() {
        Window window = WindowLayout.addWindow(row(this.map), this.map, 0.0D,
                0.0D);
        assertTrue(ContentView.enter(window));
        WindowLayout.load(Collections.<WindowLayout.WindowSpec>emptyList());
        ContentView.follow(null, false);
        assertFalse(ContentView.isOn());
        assertFalse("nothing to stand alone", ContentView.enter(null));
    }
}
