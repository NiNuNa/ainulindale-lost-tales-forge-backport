package com.ninuna.losttales.client.window;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.chat.ChatLayout;
import com.ninuna.losttales.client.chat.ChatTab;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Marking several tabs of one row, and what that marking is scoped to. */
public final class TabSelectionTest {
    private Window window;
    private List<WindowTab> tabs;

    @Before
    public void setUp() {
        ChatLayout.reset();
        TabSelection.clear();
        this.window = WindowLayout.window("w2");
        // A snapshot: the window's own list is live, and these tests
        // close and move tabs out of it.
        this.tabs = new ArrayList<WindowTab>(this.window.getTabs());
    }

    @After
    public void tearDown() {
        TabSelection.clear();
        ChatLayout.reset();
    }

    @Test
    public void nothingIsMarkedToBeginWith() {
        assertNull(TabSelection.windowId());
        assertFalse(TabSelection.isGroup());
        assertTrue(TabSelection.selectedIn(this.window).isEmpty());
        assertFalse(TabSelection.isSelected(this.tabs.get(0)));
    }

    @Test
    public void aPlainPickLeavesExactlyOneTabMarked() {
        TabSelection.toggle("w2", this.tabs.get(0), this.tabs.get(2));
        assertTrue(TabSelection.isGroup());
        TabSelection.selectOnly("w2", this.tabs.get(1));
        assertEquals(java.util.Collections.singletonList(this.tabs.get(1)),
                TabSelection.selectedIn(this.window));
        assertFalse(TabSelection.isGroup());
    }

    @Test
    public void togglingAddsAndRemovesAndKeepsRowOrder() {
        WindowTab anchor = this.tabs.get(0);
        // Marked back to front; read back in the row's own order, with
        // the anchor the set was seeded from.
        TabSelection.toggle("w2", anchor, this.tabs.get(3));
        TabSelection.toggle("w2", anchor, this.tabs.get(1));
        assertEquals(Arrays.asList(anchor, this.tabs.get(1),
                this.tabs.get(3)),
                TabSelection.selectedIn(this.window));
        assertTrue(TabSelection.isSelected(this.tabs.get(1)));
        TabSelection.toggle("w2", anchor, this.tabs.get(1));
        assertEquals(Arrays.asList(anchor, this.tabs.get(3)),
                TabSelection.selectedIn(this.window));
        assertFalse(TabSelection.isSelected(this.tabs.get(1)));
        TabSelection.toggle("w2", anchor, this.tabs.get(3));
        assertEquals(java.util.Collections.singletonList(anchor),
                TabSelection.selectedIn(this.window));
    }

    /**
     * The marks always hold the tab in front: a set starts from it, and
     * Shift+clicking it does not take it out. Without that, closing or
     * dragging a group would leave the tab being typed in behind.
     */
    @Test
    public void theAnchorIsAlwaysMarkedAndNeverUnmarked() {
        WindowTab anchor = this.tabs.get(2);
        TabSelection.toggle("w2", anchor, this.tabs.get(0));
        assertTrue(TabSelection.isSelected(anchor));
        assertEquals(Arrays.asList(this.tabs.get(0), anchor),
                TabSelection.selectedIn(this.window));
        // Shift-clicking the anchor changes nothing.
        TabSelection.toggle("w2", anchor, anchor);
        assertEquals(Arrays.asList(this.tabs.get(0), anchor),
                TabSelection.selectedIn(this.window));
        // And it is still the anchor once the set has grown.
        TabSelection.toggle("w2", anchor, this.tabs.get(1));
        TabSelection.toggle("w2", anchor, anchor);
        assertTrue(TabSelection.isSelected(anchor));
    }

    @Test
    public void marksBelongToOneRowAtATime() {
        TabSelection.toggle("w2", this.tabs.get(0), this.tabs.get(1));
        // A tab of another window starts that window's marks instead of
        // joining the ones already made.
        TabSelection.toggle("w1", ChatTab.of(ChatChannel.CLIENT_CONSOLE),
                ChatTab.of(ChatChannel.OPERATOR));
        assertEquals("w1", TabSelection.windowId());
        assertTrue(TabSelection.selectedIn(this.window).isEmpty());
        assertEquals(Arrays.asList(ChatTab.of(ChatChannel.CLIENT_CONSOLE),
                ChatTab.of(ChatChannel.OPERATOR)),
                TabSelection.selectedIn(WindowLayout.window("w1")));
    }

    @Test
    public void pruningDropsMarksTheLayoutNoLongerHolds() {
        TabSelection.toggle("w2", this.tabs.get(0), this.tabs.get(1));
        assertTrue(WindowLayout.close(this.tabs.get(0)));
        TabSelection.prune();
        assertEquals(java.util.Collections.singletonList(this.tabs.get(1)),
                TabSelection.selectedIn(this.window));
        // A tab moved out of the row is not in that row any more.
        assertTrue(WindowLayout.moveTab(this.tabs.get(1), "w1", 0));
        TabSelection.prune();
        assertNull(TabSelection.windowId());
        // And a window that has gone takes its marks with it.
        TabSelection.toggle("w1", null, ChatTab.of(ChatChannel.CLIENT_CONSOLE));
        assertTrue(WindowLayout.closeWindow("w1"));
        TabSelection.prune();
        assertNull(TabSelection.windowId());
    }

    @Test
    public void marksNameTabsAndOwnNothing() {
        TabSelection.toggle("w2", this.tabs.get(0), this.tabs.get(1));
        List<ChatChannel> before = ChatTab.channelsOf(this.window);
        TabSelection.clear();
        // Forgetting the marks changes no channel and no window.
        assertEquals(before, ChatTab.channelsOf(this.window));
        assertEquals(2, WindowLayout.windows().size());
    }
}
