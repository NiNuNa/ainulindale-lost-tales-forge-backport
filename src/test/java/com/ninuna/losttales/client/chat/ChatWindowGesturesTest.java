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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatWindowGesturesTest {
    private ChatWindowFrame frame;

    @Before
    public void setUp() {
        ChatWindowLayout.reset();
        ClientChatChannelState.clear();
        this.frame = ChatWindowFrame.of(ChatWindowLayout.windows().get(0));
        this.frame.drawn = true;
        this.frame.boxLeft = 100;
        this.frame.boxTop = 50;
        this.frame.boxRight = 300;
        this.frame.boxBottom = 150;
    }

    @After
    public void tearDown() {
        ChatWindowLayout.reset();
        ClientChatChannelState.clear();
    }

    @Test
    public void theBorderOutsideAWindowAnswersWithItsEdgeOrCorner() {
        int border = ChatWindowGestures.RESIZE_BORDER;
        int corner = ChatWindowGestures.RESIZE_CORNER;
        // Just outside each edge, mid-way along it.
        assertEquals(ChatWindowGestures.ResizeEdge.LEFT,
                ChatWindowGestures.edgeAt(this.frame, 100 - border, 100));
        assertEquals(ChatWindowGestures.ResizeEdge.RIGHT,
                ChatWindowGestures.edgeAt(this.frame, 300 + border, 100));
        assertEquals(ChatWindowGestures.ResizeEdge.TOP,
                ChatWindowGestures.edgeAt(this.frame, 200, 50 - border));
        assertEquals(ChatWindowGestures.ResizeEdge.BOTTOM,
                ChatWindowGestures.edgeAt(this.frame, 200, 150 + border));
        // On an edge but near its end: the corner reaches along it.
        assertEquals(ChatWindowGestures.ResizeEdge.TOP_LEFT,
                ChatWindowGestures.edgeAt(this.frame, 100 + corner, 50 - 1));
        assertEquals(ChatWindowGestures.ResizeEdge.TOP_RIGHT,
                ChatWindowGestures.edgeAt(this.frame, 300 - corner, 50 - 1));
        assertEquals(ChatWindowGestures.ResizeEdge.BOTTOM_LEFT,
                ChatWindowGestures.edgeAt(this.frame, 100 - 1, 150 - corner));
        assertEquals(ChatWindowGestures.ResizeEdge.BOTTOM_RIGHT,
                ChatWindowGestures.edgeAt(this.frame, 300 + 1, 150 - corner));
        // Beyond the border, and inside the window, is nobody's edge.
        assertNull(ChatWindowGestures.edgeAt(this.frame, 100 - border - 1,
                100));
        assertNull(ChatWindowGestures.edgeAt(this.frame, 200, 100));
        assertTrue(ChatWindowGestures.coversPoint(this.frame, 200, 100));
        assertFalse(ChatWindowGestures.coversPoint(this.frame, 100, 100));
        assertFalse(ChatWindowGestures.coversPoint(this.frame, 200, 160));
    }

    @Test
    public void pullsAreMeasuredAsOneStraightLine() {
        assertEquals(0, ChatWindowGestures.overhangOf(5, 0, 10));
        assertEquals(3, ChatWindowGestures.overhangOf(-3, 0, 10));
        assertEquals(0, ChatWindowGestures.overhangOf(10, 0, 10));
        assertEquals(1, ChatWindowGestures.overhangOf(11, 0, 10));
        assertTrue(ChatWindowGestures.pulledBeyond(3, 4, 5));
        assertFalse(ChatWindowGestures.pulledBeyond(3, 3, 5));
        assertTrue(ChatWindowGestures.pulledBeyond(0, 5, 5));
    }

    @Test
    public void aDraggedHeightStaysBetweenTheSmallestAndLargestWindow() {
        double stride = 11.0D;
        double chrome = 30.0D;
        double smallest = chrome + ChatWindowLayout.MIN_WINDOW_LINES * stride;
        double largest = chrome + ChatWindowLayout.MAX_WINDOW_LINES * stride;
        assertEquals(smallest,
                ChatWindowGestures.clampedHeight(0.0D, stride, chrome), 0.0D);
        assertEquals(largest,
                ChatWindowGestures.clampedHeight(99999.0D, stride, chrome),
                0.0D);
        assertEquals(smallest + 5.5D,
                ChatWindowGestures.clampedHeight(smallest + 5.5D, stride,
                        chrome), 0.0D);
    }

    @Test
    public void aVisibleSlotIsTranslatedPastHiddenTabsAndTheMovingOnes() {
        ChatWindow window = ChatWindowLayout.windows().get(1);
        List<ChatTab> tabs = window.getTabs();
        // The conversation window holds open tabs the player cannot
        // see — Party outside a party — sitting between the others.
        assertTrue(tabs.contains(ChatTab.of(ChatChannel.PARTY)));
        assertFalse(ClientChatChannelState.isAvailable(
                ChatTab.of(ChatChannel.PARTY)));
        // The k-th visible slot lands on the list index of the k-th
        // available tab, hidden ones between counted past.
        int visible = 0;
        for (int index = 0; index < tabs.size(); index++) {
            if (ClientChatChannelState.isAvailable(tabs.get(index))) {
                assertEquals(index, ChatWindowGestures.listPosition(window,
                        Collections.<ChatTab>emptyList(), visible));
                visible++;
            }
        }
        // Past the end: the position after the last tab.
        assertEquals(tabs.size(), ChatWindowGestures.listPosition(window,
                Collections.<ChatTab>emptyList(), visible));
        // A tab on its way out is neither counted nor stood on: with the
        // first available tab moving, slot zero lands where the second
        // available tab stands, less the moving tab's own place.
        ChatTab first = null;
        int firstIndex = -1;
        int secondIndex = -1;
        for (int index = 0; index < tabs.size(); index++) {
            if (!ClientChatChannelState.isAvailable(tabs.get(index))) {
                continue;
            }
            if (first == null) {
                first = tabs.get(index);
                firstIndex = index;
            } else {
                secondIndex = index;
                break;
            }
        }
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex);
        assertEquals(secondIndex - 1, ChatWindowGestures.listPosition(window,
                Arrays.asList(first), 0));
    }
}
