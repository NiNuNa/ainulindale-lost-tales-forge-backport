package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
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
public final class ChatTabSelectionTest {
    private ChatWindow window;
    private List<ChatTab> tabs;

    @Before
    public void setUp() {
        ChatWindowLayout.reset();
        ChatTabSelection.clear();
        this.window = ChatWindowLayout.window("w2");
        // A snapshot: the window's own list is live, and these tests
        // close and move tabs out of it.
        this.tabs = new ArrayList<ChatTab>(this.window.getTabs());
    }

    @After
    public void tearDown() {
        ChatTabSelection.clear();
        ChatWindowLayout.reset();
    }

    @Test
    public void nothingIsMarkedToBeginWith() {
        assertNull(ChatTabSelection.windowId());
        assertFalse(ChatTabSelection.isGroup());
        assertTrue(ChatTabSelection.selectedIn(this.window).isEmpty());
        assertFalse(ChatTabSelection.isSelected(this.tabs.get(0)));
    }

    @Test
    public void aPlainPickLeavesExactlyOneTabMarked() {
        ChatTabSelection.toggle("w2", this.tabs.get(0), this.tabs.get(2));
        assertTrue(ChatTabSelection.isGroup());
        ChatTabSelection.selectOnly("w2", this.tabs.get(1));
        assertEquals(java.util.Collections.singletonList(this.tabs.get(1)),
                ChatTabSelection.selectedIn(this.window));
        assertFalse(ChatTabSelection.isGroup());
    }

    @Test
    public void togglingAddsAndRemovesAndKeepsRowOrder() {
        ChatTab anchor = this.tabs.get(0);
        // Marked back to front; read back in the row's own order, with
        // the anchor the set was seeded from.
        ChatTabSelection.toggle("w2", anchor, this.tabs.get(3));
        ChatTabSelection.toggle("w2", anchor, this.tabs.get(1));
        assertEquals(Arrays.asList(anchor, this.tabs.get(1),
                this.tabs.get(3)),
                ChatTabSelection.selectedIn(this.window));
        assertTrue(ChatTabSelection.isSelected(this.tabs.get(1)));
        ChatTabSelection.toggle("w2", anchor, this.tabs.get(1));
        assertEquals(Arrays.asList(anchor, this.tabs.get(3)),
                ChatTabSelection.selectedIn(this.window));
        assertFalse(ChatTabSelection.isSelected(this.tabs.get(1)));
        ChatTabSelection.toggle("w2", anchor, this.tabs.get(3));
        assertEquals(java.util.Collections.singletonList(anchor),
                ChatTabSelection.selectedIn(this.window));
    }

    /**
     * The marks always hold the tab in front: a set starts from it, and
     * Shift+clicking it does not take it out. Without that, closing or
     * dragging a group would leave the tab being typed in behind.
     */
    @Test
    public void theAnchorIsAlwaysMarkedAndNeverUnmarked() {
        ChatTab anchor = this.tabs.get(2);
        ChatTabSelection.toggle("w2", anchor, this.tabs.get(0));
        assertTrue(ChatTabSelection.isSelected(anchor));
        assertEquals(Arrays.asList(this.tabs.get(0), anchor),
                ChatTabSelection.selectedIn(this.window));
        // Shift-clicking the anchor changes nothing.
        ChatTabSelection.toggle("w2", anchor, anchor);
        assertEquals(Arrays.asList(this.tabs.get(0), anchor),
                ChatTabSelection.selectedIn(this.window));
        // And it is still the anchor once the set has grown.
        ChatTabSelection.toggle("w2", anchor, this.tabs.get(1));
        ChatTabSelection.toggle("w2", anchor, anchor);
        assertTrue(ChatTabSelection.isSelected(anchor));
    }

    @Test
    public void marksBelongToOneRowAtATime() {
        ChatTabSelection.toggle("w2", this.tabs.get(0), this.tabs.get(1));
        // A tab of another window starts that window's marks instead of
        // joining the ones already made.
        ChatTabSelection.toggle("w1", ChatTab.of(ChatChannel.CONSOLE),
                ChatTab.of(ChatChannel.ADMIN));
        assertEquals("w1", ChatTabSelection.windowId());
        assertTrue(ChatTabSelection.selectedIn(this.window).isEmpty());
        assertEquals(Arrays.asList(ChatTab.of(ChatChannel.CONSOLE),
                ChatTab.of(ChatChannel.ADMIN)),
                ChatTabSelection.selectedIn(ChatWindowLayout.window("w1")));
    }

    @Test
    public void pruningDropsMarksTheLayoutNoLongerHolds() {
        ChatTabSelection.toggle("w2", this.tabs.get(0), this.tabs.get(1));
        assertTrue(ChatWindowLayout.close(this.tabs.get(0)));
        ChatTabSelection.prune();
        assertEquals(java.util.Collections.singletonList(this.tabs.get(1)),
                ChatTabSelection.selectedIn(this.window));
        // A tab moved out of the row is not in that row any more.
        assertTrue(ChatWindowLayout.moveTab(this.tabs.get(1), "w1", 0));
        ChatTabSelection.prune();
        assertNull(ChatTabSelection.windowId());
        // And a window that has gone takes its marks with it.
        ChatTabSelection.toggle("w1", null, ChatTab.of(ChatChannel.CONSOLE));
        assertTrue(ChatWindowLayout.closeWindow("w1"));
        ChatTabSelection.prune();
        assertNull(ChatTabSelection.windowId());
    }

    @Test
    public void marksNameTabsAndOwnNothing() {
        ChatTabSelection.toggle("w2", this.tabs.get(0), this.tabs.get(1));
        List<ChatChannel> before = this.window.getChannels();
        ChatTabSelection.clear();
        // Forgetting the marks changes no channel and no window.
        assertEquals(before, this.window.getChannels());
        assertEquals(2, ChatWindowLayout.windows().size());
    }
}
