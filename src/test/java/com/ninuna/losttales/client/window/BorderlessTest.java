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
 * front there or the window is gone. Held by its dent's padlock, it is
 * kept whatever comes in front and through the screen closing, until it
 * is let go.
 */
public final class BorderlessTest {
    private final BarLeadTest.Tab map = new BarLeadTest.Tab("Map", true);
    private final BarLeadTest.Tab journal = new BarLeadTest.Tab("Journal", true);
    private final BarLeadTest.Tab global = new BarLeadTest.Tab("Global", true);

    @Before
    public void reset() {
        ChatLayout.reset();
        WindowLayout.load(Collections.<WindowLayout.WindowSpec>emptyList());
        Borderless.leaveAll();
    }

    @After
    public void cleanUp() {
        Borderless.leaveAll();
        ChatLayout.reset();
    }

    private static List<WindowPage> row(WindowPage... tabs) {
        return new ArrayList<WindowPage>(Arrays.asList(tabs));
    }

    @Test
    public void aPageFillsItsWindowUntilAnotherComesInFront() {
        Window window = WindowLayout.addWindow(row(this.map, this.journal),
                this.map);
        assertTrue(Borderless.enter(window));
        assertTrue(Borderless.isOn(window));
        assertEquals(Window.ScreenFill.CONTENT, Borderless.fillOf(window));
        assertEquals("never the window's own fill", Window.ScreenFill.NONE,
                window.getFill());
        Borderless.follow();
        assertTrue(Borderless.isOn(window));
        WindowLayout.setActiveTab(this.journal);
        Borderless.follow();
        assertFalse(Borderless.isOn(window));
        assertEquals(Window.ScreenFill.NONE, Borderless.fillOf(window));
    }

    /**
     * While playing, a window pinned to the HUD shows its page filling it,
     * whether or not the player let it fill the window on the screen.
     */
    @Test
    public void aPinnedWindowShowsItsPageFillingItWhilePlaying() {
        Window window = WindowLayout.addWindow(row(this.map), this.map);
        assertEquals(Window.ScreenFill.NONE, Borderless.fillOf(window));
        WindowView.beginPinnedPass();
        try {
            assertEquals(Window.ScreenFill.CONTENT,
                    Borderless.fillOf(window));
        } finally {
            WindowView.endPinnedPass();
        }
        assertEquals(Window.ScreenFill.NONE, Borderless.fillOf(window));
    }

    @Test
    public void eachWindowHasItsOwn() {
        Window first = WindowLayout.addWindow(row(this.global), this.global);
        Window second = WindowLayout.addWindow(row(this.map), this.map);
        assertTrue(Borderless.enter(first));
        assertTrue(Borderless.enter(second));
        assertTrue(Borderless.isOn(first));
        assertTrue(Borderless.isOn(second));
        assertTrue(Borderless.leave(first));
        assertFalse("given back once", Borderless.leave(first));
        assertTrue("the other keeps its own", Borderless.isOn(second));
        Borderless.leaveAll();
        assertFalse(Borderless.isOn(second));
    }

    @Test
    public void aClosedWindowGivesItsPageBack() {
        Window window = WindowLayout.addWindow(row(this.map), this.map);
        assertTrue(Borderless.enter(window));
        WindowLayout.load(Collections.<WindowLayout.WindowSpec>emptyList());
        Borderless.follow();
        assertFalse(Borderless.isOn(window));
        assertFalse("nothing to fill", Borderless.enter(null));
        assertFalse(Borderless.leave(null));
    }

    /** WIN-41: nothing holds Borderless; the screen closing ends it, and the layout never keeps it. */
    @Test
    public void borderlessEndsWithTheScreenAndIsNeverSaved() {
        Window window = WindowLayout.addWindow(row(this.map, this.journal),
                this.map);
        assertTrue(Borderless.enter(window));
        for (String line : WindowLayoutStore.describe()) {
            assertFalse(line, line.contains("borderless"));
        }
        Borderless.leaveAll();
        assertFalse("the screen closing ends it", Borderless.isOn(window));
        assertTrue(Borderless.enter(window));
        assertTrue("the exit or Escape ends it", Borderless.leave(window));
        assertFalse(Borderless.isOn(window));
    }
}
