package com.ninuna.losttales.client.window;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.chat.ChatLayout;
import com.ninuna.losttales.client.chat.ChatTab;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The stacking order overlapping windows are cycled through: raising
 * brings a window to the front, lowering sends it behind every other
 * one, and pressing the same spot over and over therefore walks the
 * whole stack and comes back round to where it started.
 */
public final class WindowStackTest {

    @Before
    public void reset() {
        ChatLayout.reset();
    }

    @After
    public void cleanUp() {
        ChatLayout.reset();
    }

    /** The ids of every window, back to front. */
    private static String order() {
        List<Window> stacked = WindowLayout.stacked();
        StringBuilder built = new StringBuilder();
        for (int index = 0; index < stacked.size(); index++) {
            if (built.length() > 0) {
                built.append(' ');
            }
            built.append(stacked.get(index).getId());
        }
        return built.toString();
    }

    private static Window front() {
        List<Window> stacked = WindowLayout.stacked();
        return stacked.get(stacked.size() - 1);
    }

    private static void detach(ChatChannel channel) {
        WindowLayout.detach(
                Collections.singletonList(ChatTab.of(channel)), 0.5D, 0.5D);
    }

    @Test
    public void loweringSendsAWindowBehindEveryOther() {
        detach(ChatChannel.OOC);
        detach(ChatChannel.PARTY);
        List<Window> before = WindowLayout.stacked();
        assertTrue("the layout needs a stack to test", before.size() >= 2);
        Window wasInFront = before.get(before.size() - 1);

        WindowLayout.lower(wasInFront.getId());
        List<Window> after = WindowLayout.stacked();
        assertEquals("it is now the very back one",
                wasInFront.getId(), after.get(0).getId());
        assertEquals("and nothing else moved relative to the rest",
                before.size(), after.size());
        for (int index = 1; index < after.size(); index++) {
            assertEquals(before.get(index - 1).getId(),
                    after.get(index).getId());
        }
    }

    @Test
    public void loweringTheFrontWindowWalksTheWholeStack() {
        detach(ChatChannel.OOC);
        detach(ChatChannel.PARTY);
        String start = order();
        int count = WindowLayout.stacked().size();
        assertTrue(count >= 2);
        // One press per window sends each in turn to the back, and the
        // last of them leaves the stack exactly as it began.
        for (int press = 0; press < count; press++) {
            WindowLayout.lower(front().getId());
        }
        assertEquals(start, order());
    }


    @Test
    public void loweringAWindowThatIsGoneChangesNothing() {
        detach(ChatChannel.OOC);
        String before = order();
        WindowLayout.lower("nowhere");
        assertEquals(before, order());
        assertNotNull(WindowLayout.firstWindow());
    }
}
