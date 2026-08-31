package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Per-window line layouts are let go with their windows. A view's lines
 * are held against its window's id and nothing asks for them once the
 * window is closed, so an entry left behind is never refreshed and never
 * replaced — window ids are handed out and never reused. Without the
 * sweep, every detached window closed in a session kept a whole
 * re-wrapped copy of the history alive until the world was left.
 */
public final class ChatWindowLinesPruneTest {

    @Before
    public void reset() {
        ChatWindowLayout.reset();
    }

    @After
    public void cleanUp() {
        ChatWindowLayout.reset();
    }

    private static Map<String, Object> views(String... viewIds) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int index = 0; index < viewIds.length; index++) {
            map.put(viewIds[index], new Object());
        }
        return map;
    }

    @Test
    public void aClosedWindowsLayoutIsDropped() {
        List<ChatWindow> windows = ChatWindowLayout.windows();
        assertFalse("the layout needs a window to test", windows.isEmpty());
        String live = windows.get(0).getId();

        Map<String, Object> cache = views(live, "w404");
        ChatWindowLines.pruneViews(cache, windows);
        assertTrue("the live window keeps its layout",
                cache.containsKey(live));
        assertFalse("the window that has gone does not",
                cache.containsKey("w404"));
    }

    @Test
    public void theFeedIsNotAWindowAndIsNeverSwept() {
        String feedId = ChatWindowFrame.feed().windowId;
        Map<String, Object> cache = views(feedId, "w404");
        ChatWindowLines.pruneViews(cache, ChatWindowLayout.windows());
        assertTrue(cache.containsKey(feedId));
        assertFalse(cache.containsKey("w404"));
    }

    @Test
    public void everyWindowStillOpenKeepsItsLayout() {
        ChatWindowLayout.detach(
                Collections.singletonList(ChatTab.of(ChatChannel.OOC)),
                0.5D, 0.5D);
        List<ChatWindow> windows = ChatWindowLayout.windows();
        assertTrue(windows.size() >= 2);
        Map<String, Object> cache =
                new LinkedHashMap<String, Object>();
        for (int index = 0; index < windows.size(); index++) {
            cache.put(windows.get(index).getId(), new Object());
        }
        int before = cache.size();
        ChatWindowLines.pruneViews(cache, windows);
        assertEquals("nothing open is ever swept", before, cache.size());
    }

    @Test
    public void withNoWindowsLeftOnlyTheFeedSurvives() {
        Map<String, Object> cache = views(
                ChatWindowFrame.feed().windowId, "w1", "w2", "w3");
        ChatWindowLines.pruneViews(cache,
                Collections.<ChatWindow>emptyList());
        assertEquals(1, cache.size());
        assertTrue(cache.containsKey(ChatWindowFrame.feed().windowId));
    }

    @Test
    public void sweepingTwiceChangesNothingTheSecondTime() {
        List<ChatWindow> windows = ChatWindowLayout.windows();
        Map<String, Object> cache = views(windows.get(0).getId(), "w404");
        ChatWindowLines.pruneViews(cache, windows);
        int after = cache.size();
        ChatWindowLines.pruneViews(cache, windows);
        assertEquals(after, cache.size());
    }
}
