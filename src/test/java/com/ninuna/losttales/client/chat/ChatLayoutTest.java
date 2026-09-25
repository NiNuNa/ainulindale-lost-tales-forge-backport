package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowLayoutStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ChatLayoutTest {
    private int changes;

    @Before
    public void reset() {
        ChatLayout.reset();
        this.changes = 0;
        WindowLayout.setChangeListener(new Runnable() {
            @Override
            public void run() {
                changes++;
            }
        });
    }

    @After
    public void cleanUp() {
        WindowLayout.setChangeListener(null);
        ChatLayout.reset();
    }

    /**
     * A place's whisper tabs are remembered where they were and come back
     * on the next visit there, not elsewhere; one closed by hand stays
     * closed until somebody speaks in it again.
     */
    @Test
    public void whisperTabsAreRememberedPerPlaceAndClosedOnesStayClosed() {
        ChatLayout.restoreConversations("server:a");
        ChatTab steve = ChatTab.whisper("Steve", "Aldric");
        ChatTab bob = ChatTab.whisper("Bob", "Bob");
        assertNotNull(WindowLayout.openTab(steve, "w2"));
        assertNotNull(WindowLayout.openTab(bob, "w2"));
        assertTrue(ChatLayout.close(bob));
        assertTrue("closed by hand: a replay may not reopen it",
                ChatLayout.isHidden(bob));
        ChatLayout.closeConversations();
        assertFalse(WindowLayout.isOpen(steve));
        assertFalse(ChatLayout.isHidden(bob));
        ChatLayout.restoreConversations("server:b");
        assertFalse("another place has no such tab", WindowLayout.isOpen(steve));
        ChatLayout.closeConversations();
        ChatLayout.restoreConversations("server:a");
        assertTrue(WindowLayout.isOpen(steve));
        assertEquals("w2", WindowLayout.windowOf(steve).getId());
        assertFalse(WindowLayout.isOpen(bob));
        assertTrue(ChatLayout.isHidden(bob));
        assertNotNull(ChatLayout.reopenConversation(bob, "w2"));
        assertTrue(WindowLayout.isOpen(bob));
        assertFalse(ChatLayout.isHidden(bob));
    }

    @Test
    public void defaultLayoutIsAConsoleWindowAndAConversationWindow() {
        assertEquals(2, WindowLayout.windows().size());
        Window console = WindowLayout.firstWindow();
        assertEquals("w1", console.getId());
        assertEquals(Arrays.asList(ChatChannel.CLIENT_CONSOLE,
                ChatChannel.SERVER_CONSOLE, ChatChannel.OPERATOR),
                ChatTab.channelsOf(console));
        assertEquals(ChatChannel.CLIENT_CONSOLE, ChatTab.frontChannelOf(console));
        assertEquals(0.0D, console.getOffsetX(), 0.0D);
        assertEquals(0.0D, console.getOffsetY(), 0.0D);
        Window conversation = WindowLayout.windows().get(1);
        assertEquals("w2", conversation.getId());
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC,
                ChatChannel.PARTY), ChatTab.channelsOf(conversation));
        assertEquals(ChatChannel.GLOBAL, ChatTab.frontChannelOf(conversation));
        assertEquals(0.0D, conversation.getOffsetX(), 0.0D);
        assertEquals(100.0D, conversation.getOffsetY(), 0.0D);
        // The closed-chat feed starts where vanilla draws the chat.
        assertEquals(0.0D, ChatLayout.feedOffsetX(), 0.0D);
        assertEquals(100.0D, ChatLayout.feedOffsetY(), 0.0D);
        ChatLayout.setFeedPosition(40.0D, -3.0D, true);
        assertEquals(40.0D, ChatLayout.feedOffsetX(), 0.0D);
        assertEquals(0.0D, ChatLayout.feedOffsetY(), 0.0D);
        assertTrue(ChatLayout.closedChannels().isEmpty());
        assertEquals(Arrays.asList(ChatChannel.CLIENT_CONSOLE,
                ChatChannel.SERVER_CONSOLE, ChatChannel.OPERATOR,
                ChatChannel.GLOBAL, ChatChannel.PROXIMITY, ChatChannel.FACTION,
                ChatChannel.OOC, ChatChannel.PARTY),
                ChatLayout.orderChannels());
    }

    @Test
    public void closeRestoreAndMuteKeepIdentityAndNotifyTheStore() {
        assertTrue(ChatLayout.close(ChatChannel.OPERATOR));
        assertFalse(ChatLayout.isOpen(ChatChannel.OPERATOR));
        assertNull(ChatLayout.windowOf(ChatChannel.OPERATOR));
        assertEquals(Collections.singletonList(ChatChannel.OPERATOR),
                ChatLayout.closedChannels());
        assertEquals(1, this.changes);
        // Closing again is a no-op; restoring lands in the first window.
        assertFalse(ChatLayout.close(ChatChannel.OPERATOR));
        assertTrue(ChatLayout.restore(ChatChannel.OPERATOR));
        List<ChatChannel> tabs = ChatTab.channelsOf(WindowLayout.firstWindow());
        assertEquals(ChatChannel.OPERATOR, tabs.get(tabs.size() - 1));
        assertEquals(ChatChannel.OPERATOR,
                ChatTab.frontChannelOf(WindowLayout.firstWindow()));
        assertFalse(ChatLayout.restore(ChatChannel.OPERATOR));
        assertEquals(2, this.changes);

        ChatLayout.setMuted(ChatChannel.OOC, true);
        assertTrue(ChatLayout.isMuted(ChatChannel.OOC));
        assertTrue(ChatLayout.isOpen(ChatChannel.OOC));
        ChatLayout.setMuted(ChatChannel.OOC, false);
        assertFalse(ChatLayout.isMuted(ChatChannel.OOC));
        assertEquals(4, this.changes);
    }

    /**
     * Open, muted and closed are three separate things: closing a tab
     * neither mutes nor unmutes it, and the setting is still there when
     * the channel comes back.
     */
    @Test
    public void closingNeverTouchesMuteAndMuteSurvivesRestore() {
        ChatLayout.setMuted(ChatChannel.PARTY, true);
        assertTrue(ChatLayout.close(ChatChannel.PARTY));
        assertTrue(ChatLayout.isMuted(ChatChannel.PARTY));
        assertFalse(ChatLayout.isOpen(ChatChannel.PARTY));
        assertTrue(ChatLayout.restore(ChatChannel.PARTY));
        assertTrue(ChatLayout.isMuted(ChatChannel.PARTY));
        assertTrue(ChatLayout.close(ChatChannel.OOC));
        assertFalse(ChatLayout.isMuted(ChatChannel.OOC));
        assertTrue(ChatLayout.restore(ChatChannel.OOC));
        assertFalse(ChatLayout.isMuted(ChatChannel.OOC));
        // Muting a closed channel is allowed and is kept for its return.
        assertTrue(ChatLayout.close(ChatChannel.OOC));
        ChatLayout.setMuted(ChatChannel.OOC, true);
        assertTrue(ChatLayout.isMuted(ChatChannel.OOC));
        assertTrue(ChatLayout.restore(ChatChannel.OOC));
        assertTrue(ChatLayout.isMuted(ChatChannel.OOC));
    }

    /**
     * Moving the closed-chat feed, which is all the HUD placement editor
     * does with the chat layout, moves no window and breaks no link, and
     * reaches the store only when the caller asks for it.
     */
    @Test
    public void movingTheFeedLeavesEveryWindowAlone() {
        Window console = WindowLayout.window("w1");
        Window conversation = WindowLayout.window("w2");
        assertTrue(WindowLayout.link("w2", "w1", false));
        this.changes = 0;

        ChatLayout.setFeedPosition(35.0D, 60.0D, false);
        assertEquals(0, this.changes);
        ChatLayout.setFeedPosition(40.0D, 55.0D, true);
        assertEquals(1, this.changes);

        assertEquals(40.0D, ChatLayout.feedOffsetX(), 0.0D);
        assertEquals(55.0D, ChatLayout.feedOffsetY(), 0.0D);
        assertEquals(2, WindowLayout.windows().size());
        assertEquals(0.0D, console.getOffsetX(), 0.0D);
        assertEquals(0.0D, console.getOffsetY(), 0.0D);
        assertEquals(0.0D, conversation.getOffsetX(), 0.0D);
        assertEquals(100.0D, conversation.getOffsetY(), 0.0D);
        assertEquals("w1", conversation.getLinkTarget());
        assertFalse(console.isLinked());
    }

    /**
     * Mute (out of the feed) and mention-mute (cue silent) are two
     * independent preferences: each changes only its own half, both
     * survive closing, and both travel with the store.
     */
    @Test
    public void muteAndMentionMuteAreIndependentPreferences() {
        ChatTab party = ChatTab.of(ChatChannel.PARTY);
        ChatTab ooc = ChatTab.of(ChatChannel.OOC);
        assertTrue(ChatLayout.isInFeed(party));
        assertTrue(ChatLayout.isPingAudible(party));
        ChatLayout.setMuted(party, true);
        assertFalse(ChatLayout.isInFeed(party));
        assertTrue(ChatLayout.isPingAudible(party));
        assertFalse(ChatLayout.isPingsMuted(party));
        ChatLayout.setPingsMuted(ooc, true);
        assertTrue(ChatLayout.isInFeed(ooc));
        assertFalse(ChatLayout.isPingAudible(ooc));
        assertFalse(ChatLayout.isMuted(ooc));
        assertEquals(2, this.changes);
        // Setting what is already set is not a change.
        ChatLayout.setMuted(party, true);
        assertEquals(2, this.changes);
        assertTrue(ChatLayout.close(ChatChannel.PARTY));
        assertTrue(ChatLayout.isMuted(party));
        assertEquals(Collections.singletonList(party),
                ChatLayout.mutedTabs());
        assertEquals(Collections.singletonList(ooc),
                ChatLayout.pingsMutedTabs());
        // Conversations drop their preferences with their tabs.
        ChatTab whisper = ChatLayout.openWhisper("Bilbo", null);
        ChatLayout.setMuted(whisper, true);
        ChatLayout.setPingsMuted(whisper, true);
        assertTrue(ChatLayout.mutedTabs().size() == 1);
        ChatLayout.closeConversations();
        assertFalse(ChatLayout.isMuted(whisper));
        assertFalse(ChatLayout.isPingsMuted(whisper));
        assertTrue(ChatLayout.isMuted(party));
    }

    /**
     * Hidden is its own concept: it neither mutes nor closes, it holds
     * while the tab is open, survives closing and restoring, and a
     * conversation drops it with its tab like every other preference.
     */
    @Test
    public void hiddenIsIndependentOfMuteAndSurvivesRestore() {
        ChatTab party = ChatTab.of(ChatChannel.PARTY);
        assertFalse(ChatLayout.isHidden(party));
        ChatLayout.setHidden(party, true);
        assertTrue(ChatLayout.isHidden(party));
        assertTrue(WindowLayout.isOpen(party));
        assertFalse(ChatLayout.isMuted(party));
        assertTrue(ChatLayout.isInFeed(party));
        assertTrue(ChatLayout.isPingAudible(party));
        assertEquals(1, this.changes);
        // Setting what is already set is not a change.
        ChatLayout.setHidden(party, true);
        assertEquals(1, this.changes);
        assertTrue(ChatLayout.close(ChatChannel.PARTY));
        assertTrue(ChatLayout.isHidden(party));
        assertEquals(Collections.singletonList(party),
                ChatLayout.hiddenTabs());
        assertTrue(ChatLayout.restore(ChatChannel.PARTY));
        assertTrue(ChatLayout.isHidden(party));
        ChatLayout.setHidden(party, false);
        assertFalse(ChatLayout.isHidden(party));
        // Conversations drop the preference with their tabs.
        ChatTab whisper = ChatLayout.openWhisper("Bilbo", null);
        ChatLayout.setHidden(whisper, true);
        assertTrue(ChatLayout.isHidden(whisper));
        assertTrue(ChatLayout.hiddenTabs().isEmpty());
        ChatLayout.closeConversations();
        assertFalse(ChatLayout.isHidden(whisper));
    }

    /**
     * The window in use is drawn last and hit first. Raising is session
     * state: it is not a layout change and does not survive a reset.
     */
    @Test
    public void raisingAWindowBringsItToTheFrontOfTheStack() {
        Window w1 = WindowLayout.window("w1");
        Window w2 = WindowLayout.window("w2");
        assertEquals(Arrays.asList(w1, w2), WindowLayout.stacked());
        WindowLayout.raise("w1");
        assertEquals(Arrays.asList(w2, w1), WindowLayout.stacked());
        WindowLayout.raise("w2");
        assertEquals(Arrays.asList(w1, w2), WindowLayout.stacked());
        WindowLayout.raise("nope");
        assertEquals(Arrays.asList(w1, w2), WindowLayout.stacked());
        assertEquals(0, this.changes);
        // A new window starts at the back; a window that goes leaves the
        // stack with it.
        Window w3 = ChatLayout.detach(ChatChannel.PARTY, 50.0D,
                50.0D);
        assertNotNull(w3);
        assertEquals(Arrays.asList(w3, w1, w2), WindowLayout.stacked());
        WindowLayout.raise(w3.getId());
        assertEquals(Arrays.asList(w1, w2, w3), WindowLayout.stacked());
        assertTrue(ChatLayout.moveTab(ChatChannel.PARTY, "w2", 0));
        assertEquals(Arrays.asList(w1, w2), WindowLayout.stacked());
        ChatLayout.reset();
        assertEquals(WindowLayout.windows(), WindowLayout.stacked());
    }

    /** Every tab closes, down to no tab and no window at all. */
    @Test
    public void everyTabClosesAndTheEmptyLayoutIsValid() {
        assertEquals(8, WindowLayout.openTabCount());
        List<ChatChannel> order = new ArrayList<ChatChannel>(
                ChatLayout.orderChannels());
        for (int index = 0; index < order.size(); index++) {
            assertTrue(ChatLayout.isClosable(order.get(index)));
            assertTrue(ChatLayout.close(order.get(index)));
        }
        assertEquals(0, WindowLayout.openTabCount());
        assertTrue(WindowLayout.isEmpty());
        assertTrue(WindowLayout.windows().isEmpty());
        assertNull(WindowLayout.firstWindow());
        assertEquals(order.size(),
                ChatLayout.closedChannels().size());
        // A closed tab is not closable, and a message finds no window to
        // open itself in: the channel keeps receiving, closed.
        assertFalse(ChatLayout.isClosable(order.get(0)));
        assertFalse(ChatLayout.close(order.get(0)));
        assertFalse(ChatLayout.restore(order.get(0)));
        assertNull(ChatLayout.openTab(ChatTab.whisper("Someone"),
                null));
        assertTrue(WindowLayout.isEmpty());
        // The + opens one back into a window of its own.
        assertNotNull(WindowLayout.openInNewWindow(
                ChatTab.of(order.get(0))));
        assertEquals(1, WindowLayout.windows().size());
        assertEquals(Collections.singletonList(order.get(0)),
                ChatLayout.orderChannels());
        assertEquals(order.get(0),
                ChatTab.frontChannelOf(WindowLayout.firstWindow()));
        // Repeated closing and reopening leaves a consistent layout.
        for (int round = 0; round < 5; round++) {
            assertTrue(ChatLayout.close(order.get(0)));
            assertTrue(WindowLayout.isEmpty());
            assertNotNull(WindowLayout.openInNewWindow(
                    ChatTab.of(order.get(0))));
        }
        assertEquals(1, WindowLayout.openTabCount());
    }

    /** A whole window closes at once, and its channels survive it. */
    @Test
    public void closingAWindowKeepsItsChannels() {
        assertFalse(WindowLayout.closeWindow("nope"));
        assertTrue(WindowLayout.setLocked("w1", true));
        assertFalse(WindowLayout.closeWindow("w1"));
        assertTrue(WindowLayout.setLocked("w1", false));
        assertTrue(WindowLayout.link("w2", "w1", true));
        assertTrue(WindowLayout.closeWindow("w1"));
        assertNull(WindowLayout.window("w1"));
        assertEquals(1, WindowLayout.windows().size());
        // The window that was stuck to it lets go.
        assertFalse(WindowLayout.window("w2").isLinked());
        // Its channels are closed, not gone: still restorable, and
        // still carrying whatever preferences they had.
        assertTrue(ChatLayout.closedChannels().contains(ChatChannel.CLIENT_CONSOLE));
        assertTrue(ChatLayout.restore(ChatChannel.CLIENT_CONSOLE, "w2"));
        assertTrue(WindowLayout.closeWindow("w2"));
        assertTrue(WindowLayout.isEmpty());
    }

    /** A marked group moves as one and keeps its order. */
    @Test
    public void groupsOfTabsMoveTogetherAndKeepTheirOrder() {
        List<ChatChannel> start = ChatTab.channelsOf(WindowLayout.window("w2"));
        // A non-contiguous pair from the conversation window, moved to
        // its front: they arrive as one run, in row order.
        List<ChatTab> group = Arrays.asList(
                ChatTab.of(start.get(3)), ChatTab.of(start.get(1)));
        assertTrue(WindowLayout.moveTabs(group, "w2", 0));
        List<ChatChannel> moved = ChatTab.channelsOf(WindowLayout.window("w2"));
        assertEquals(start.size(), moved.size());
        assertEquals(start.get(1), moved.get(0));
        assertEquals(start.get(3), moved.get(1));
        assertEquals(start.get(0), moved.get(2));
        // Across windows: the group leaves one row and joins another.
        assertTrue(WindowLayout.moveTabs(group, "w1", 0));
        assertEquals(Arrays.asList(start.get(1), start.get(3),
                ChatChannel.CLIENT_CONSOLE, ChatChannel.SERVER_CONSOLE,
                ChatChannel.OPERATOR),
                ChatTab.channelsOf(WindowLayout.window("w1")));
        assertEquals(start.get(3),
                ChatTab.frontChannelOf(WindowLayout.window("w1")));
        assertFalse(WindowLayout.window("w2").contains(ChatTab.of(start.get(1))));
        // A group detaches into a window of its own the same way.
        Window detached = WindowLayout.detach(group, 20.0D, 30.0D);
        assertNotNull(detached);
        assertEquals(Arrays.asList(start.get(1), start.get(3)),
                ChatTab.channelsOf(detached));
        assertEquals(Arrays.asList(ChatChannel.CLIENT_CONSOLE,
                ChatChannel.SERVER_CONSOLE, ChatChannel.OPERATOR),
                ChatTab.channelsOf(WindowLayout.window("w1")));
        // Tabs from two windows are not a group, and neither is a closed
        // one: both are refused whole rather than half-applied.
        assertFalse(WindowLayout.moveTabs(Arrays.asList(
                ChatTab.of(start.get(1)), ChatTab.of(ChatChannel.CLIENT_CONSOLE)),
                "w1", 0));
        assertEquals(Arrays.asList(start.get(1), start.get(3)),
                ChatTab.channelsOf(detached));
    }

    /**
     * A tab taken out of a window is read in a window the same shape:
     * the height and width the player gave the one it came from, not
     * whatever the game's own chat settings say.
     */
    @Test
    public void aDetachedWindowKeepsTheSizeOfTheOneItCameFrom() {
        assertTrue(WindowLayout.setWindowHeight("w1", 200.5D, false));
        assertTrue(WindowLayout.setWindowWidth("w1", 240, false));
        Window source = WindowLayout.window("w1");
        Window detached = ChatLayout.detach(ChatChannel.CLIENT_CONSOLE,
                20.0D, 30.0D);
        assertNotNull(detached);
        assertTrue(detached != source);
        assertEquals(source.getOwnHeight(), detached.getOwnHeight(), 0.0D);
        assertEquals(source.getOwnWidth(), detached.getOwnWidth());
    }

    @Test
    public void closedChannelsRestoreIntoTheWindowThatAsked() {
        ChatLayout.close(ChatChannel.OOC);
        assertTrue(ChatLayout.restore(ChatChannel.OOC, "w2"));
        List<ChatChannel> tabs = ChatTab.channelsOf(WindowLayout.window("w2"));
        assertEquals(ChatChannel.OOC, tabs.get(tabs.size() - 1));
        assertFalse(ChatLayout.restore(ChatChannel.OOC, "w2"));
        ChatLayout.close(ChatChannel.OOC);
        WindowLayout.setLocked("w2", true);
        assertFalse(ChatLayout.restore(ChatChannel.OOC, "w2"));
        assertFalse(ChatLayout.restore(ChatChannel.OOC, "nope"));
        assertTrue(ChatLayout.restore(ChatChannel.OOC));
        assertTrue(WindowLayout.firstWindow().contains(ChatTab.of(ChatChannel.OOC)));
    }

    @Test
    public void everyWindowIsEqualAndTheLastOneIsNoDifferent() {
        // Emptying the console window by docking drops it.
        assertTrue(ChatLayout.moveTab(ChatChannel.CLIENT_CONSOLE, "w2", 0));
        assertTrue(ChatLayout.moveTab(ChatChannel.SERVER_CONSOLE,
                "w2", 99));
        assertTrue(ChatLayout.moveTab(ChatChannel.OPERATOR, "w2", 99));
        assertNull(WindowLayout.window("w1"));
        assertEquals(1, WindowLayout.windows().size());
        assertEquals(ChatChannel.CLIENT_CONSOLE,
                ChatTab.channelsOf(WindowLayout.firstWindow()).get(0));
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (channel != ChatChannel.CLIENT_CONSOLE) {
                ChatLayout.close(channel);
            }
        }
        assertEquals(1, ChatTab.channelsOf(WindowLayout.firstWindow()).size());
        ChatChannel last = ChatTab.channelsOf(WindowLayout.firstWindow()).get(0);
        // Its only tab dragged out moves the window like any other.
        assertSame(WindowLayout.firstWindow(),
                ChatLayout.detach(last, 10.0D, 20.0D));
        assertEquals(1, WindowLayout.windows().size());
        assertEquals(10.0D, WindowLayout.firstWindow().getOffsetX(), 0.0D);
        assertEquals(20.0D, WindowLayout.firstWindow().getOffsetY(), 0.0D);
        assertTrue(WindowLayout.setPosition(
                WindowLayout.firstWindow().getId(), 1.0D, 2.0D, true));
        assertEquals(1.0D, WindowLayout.firstWindow().getOffsetX(), 0.0D);
    }

    @Test
    public void reorderDetachDockAndEmptyWindowLifecycle() {
        // Reorder within the conversation window: OOC to the front.
        assertTrue(ChatLayout.moveTab(ChatChannel.OOC, "w2", 0));
        assertEquals(ChatChannel.OOC,
                ChatTab.channelsOf(WindowLayout.window("w2")).get(0));
        assertFalse(ChatLayout.moveTab(ChatChannel.OOC, "w2", 0));

        // Detach Party into its own window.
        Window party = ChatLayout.detach(ChatChannel.PARTY,
                60.0D, 120.0D);
        assertNotNull(party);
        assertEquals("w3", party.getId());
        assertEquals(Collections.singletonList(ChatChannel.PARTY),
                ChatTab.channelsOf(party));
        assertEquals(ChatChannel.PARTY, ChatTab.frontChannelOf(party));
        assertEquals(60.0D, party.getOffsetX(), 0.0D);
        // A percent past the margin stands: the window may hang off the
        // screen by up to its own size.
        assertEquals(120.0D, party.getOffsetY(), 0.0D);
        assertEquals(3, WindowLayout.windows().size());
        assertSame(party, ChatLayout.windowOf(ChatChannel.PARTY));
        assertFalse(WindowLayout.window("w2").contains(ChatTab.of(ChatChannel.PARTY)));
        List<ChatChannel> order = ChatLayout.orderChannels();
        assertEquals(ChatChannel.PARTY, order.get(order.size() - 1));

        // Dock Faction into the party window, at the front.
        assertTrue(ChatLayout.moveTab(ChatChannel.FACTION, "w3", 0));
        assertEquals(Arrays.asList(ChatChannel.FACTION, ChatChannel.PARTY),
                ChatTab.channelsOf(party));
        assertEquals(ChatChannel.FACTION, ChatTab.frontChannelOf(party));

        // A window's only tab dragged out just moves the window.
        Window moved = ChatLayout.detach(ChatChannel.FACTION,
                5.0D, 5.0D);
        assertNotNull(moved);
        assertEquals("w4", moved.getId());
        assertEquals(4, WindowLayout.windows().size());
        assertSame(party, ChatLayout.detach(ChatChannel.PARTY,
                1.0D, 2.0D));
        assertEquals(1.0D, party.getOffsetX(), 0.0D);
        assertEquals(4, WindowLayout.windows().size());

        // Docking the last tab elsewhere empties the window away.
        assertTrue(ChatLayout.moveTab(ChatChannel.PARTY, "w2", 99));
        assertNull(WindowLayout.window("w3"));
        assertEquals(3, WindowLayout.windows().size());
        List<ChatChannel> tabs = ChatTab.channelsOf(WindowLayout.window("w2"));
        assertEquals(ChatChannel.PARTY, tabs.get(tabs.size() - 1));
        // Closing a window's last tab drops the window too.
        assertTrue(ChatLayout.close(ChatChannel.FACTION));
        assertNull(WindowLayout.window("w4"));
        assertEquals(Collections.singletonList(ChatChannel.FACTION),
                ChatLayout.closedChannels());
    }

    @Test
    public void lockRefusesLayoutChangesButNotPreferences() {
        Window party = ChatLayout.detach(ChatChannel.PARTY,
                0.0D, 0.0D);
        assertTrue(WindowLayout.setLocked("w3", true));
        assertFalse(WindowLayout.setLocked("w3", true));
        assertTrue(party.isLocked());
        // A locked window keeps the tabs it has: nothing moves out of
        // it, and nothing closes in it either.
        assertNull(ChatLayout.detach(ChatChannel.PARTY, 1.0D, 1.0D));
        assertFalse(ChatLayout.isClosable(ChatChannel.PARTY));
        assertFalse(ChatLayout.close(ChatChannel.PARTY));
        assertFalse(ChatLayout.moveTab(ChatChannel.OOC, "w3", 0));
        assertFalse(ChatLayout.moveTab(ChatChannel.PARTY, "w2", 0));
        // Position and mute are not layout movement.
        assertTrue(WindowLayout.setPosition("w3", 30.0D, 40.0D, false));
        assertEquals(30.0D, party.getOffsetX(), 0.0D);
        ChatLayout.setMuted(ChatChannel.PARTY, true);
        assertTrue(ChatLayout.isMuted(ChatChannel.PARTY));
        WindowLayout.setLocked("w2", true);
        assertFalse(ChatLayout.moveTab(ChatChannel.OOC, "w2", 0));
        assertFalse(ChatLayout.close(ChatChannel.OOC));
        // Unlocking gives the row its cross back.
        assertTrue(WindowLayout.setLocked("w2", false));
        assertTrue(ChatLayout.close(ChatChannel.OOC));
        assertFalse(WindowLayout.setPosition("nope", 1.0D, 1.0D, true));
    }

    /**
     * A conversation that opens by itself when every window is locked
     * gets a window of its own, cascaded from the front window: one step
     * right and down from it, at the size the player gave that window.
     */
    @Test
    public void aWindowOpenedForAConversationCascadesFromTheFrontWindow() {
        for (Window window : WindowLayout.windows()) {
            WindowLayout.setLocked(window.getId(), true);
        }
        WindowLayout.setWindowHeight("w1", 180.0D, true);
        WindowLayout.setWindowWidth("w1", 320, true);
        WindowLayout.raise("w1");
        int before = WindowLayout.windows().size();
        ChatTab whisper = ChatLayout.openWhisper("Bilbo", null);
        assertNotNull(whisper);
        Window opened = WindowLayout.windowOf(whisper);
        assertNotNull(opened);
        // A locked window keeps the tabs it has; this one is new.
        assertEquals(before + 1, WindowLayout.windows().size());
        Window front = WindowLayout.window("w1");
        assertEquals(180.0D, opened.getOwnHeight(), 0.0D);
        assertEquals(320, opened.getOwnWidth());
        assertTrue("right of the front window",
                opened.getOffsetX() > front.getOffsetX());
        assertTrue("below the front window",
                opened.getOffsetY() > front.getOffsetY());
    }

    @Test
    public void theSameArrangementCascadesToTheSamePlace() {
        double[] first = cascadeFromLockedLayout();
        ChatLayout.reset();
        double[] second = cascadeFromLockedLayout();
        assertEquals(first[0], second[0], 0.0D);
        assertEquals(first[1], second[1], 0.0D);
    }

    private static double[] cascadeFromLockedLayout() {
        for (Window window : WindowLayout.windows()) {
            WindowLayout.setLocked(window.getId(), true);
        }
        WindowLayout.raise("w2");
        Window opened = WindowLayout.windowOf(
                ChatLayout.openWhisper("Bilbo", null));
        return new double[] {opened.getOffsetX(), opened.getOffsetY()};
    }

    /**
     * A conversation opens in the window it was asked for, else in the
     * window last brought to the front that has room.
     */
    @Test
    public void anArrivingConversationPrefersTheWindowLastBroughtToTheFront() {
        WindowLayout.raise("w1");
        assertEquals("w1", WindowLayout.windowOf(
                ChatLayout.openWhisper("Bilbo", null)).getId());
        WindowLayout.raise("w2");
        assertEquals("w2", WindowLayout.windowOf(
                ChatLayout.openWhisper("Frodo", null)).getId());
        // The window asked for wins over the front one.
        assertEquals("w1", WindowLayout.windowOf(
                ChatLayout.openWhisper("Sam", "w1")).getId());
        // A locked window asked for hands the tab to the front unlocked one.
        WindowLayout.setLocked("w1", true);
        assertEquals("w2", WindowLayout.windowOf(
                ChatLayout.openWhisper("Merry", "w1")).getId());
    }

    @Test
    public void aWindowThatOpensByItselfStandsInFront() {
        for (Window window : WindowLayout.windows()) {
            WindowLayout.setLocked(window.getId(), true);
        }
        WindowLayout.raise("w2");
        Window opened = WindowLayout.windowOf(
                ChatLayout.openWhisper("Bilbo", null));
        java.util.List<Window> order = WindowLayout.stacked();
        assertSame(opened, order.get(order.size() - 1));
        ChatTab plus = ChatTab.whisper("Frodo");
        WindowLayout.openInNewWindow(plus);
        order = WindowLayout.stacked();
        assertSame(WindowLayout.windowOf(plus), order.get(order.size() - 1));
    }

    @Test
    public void theEmptyStateOpensAWindowCascadedFromTheFrontOne() {
        WindowLayout.setWindowHeight("w2", 150.0D, true);
        WindowLayout.raise("w2");
        ChatTab whisper = ChatTab.whisper("Pippin");
        assertNotNull(WindowLayout.openInNewWindow(whisper));
        Window opened = WindowLayout.windowOf(whisper);
        assertEquals(150.0D, opened.getOwnHeight(), 0.0D);
        assertTrue(opened.getOffsetX() > WindowLayout.window("w2").getOffsetX());
    }

    @Test
    public void linksFollowTheirTargetAndNeverHoldEachOther() {
        assertTrue(WindowLayout.link("w1", "w2", true));
        assertTrue(WindowLayout.window("w1").isLinked());
        assertTrue(WindowLayout.window("w1").isLinkedAbove());
        assertEquals("w2", WindowLayout.window("w1").getLinkTarget());
        assertEquals(1, WindowLayout.linkedTo("w2").size());
        assertFalse(WindowLayout.link("w1", "w1", true));
        assertFalse(WindowLayout.link("w1", "nope", true));
        // Linking the target back lets the first link go.
        assertTrue(WindowLayout.link("w2", "w1", false));
        assertFalse(WindowLayout.window("w1").isLinked());
        assertTrue(WindowLayout.window("w2").isLinked());
        assertFalse(WindowLayout.window("w2").isLinkedAbove());
        assertTrue(WindowLayout.unlink("w2"));
        assertFalse(WindowLayout.unlink("w2"));
        // A link dies with its target window.
        ChatLayout.detach(ChatChannel.PARTY, 0.0D, 0.0D);
        assertTrue(WindowLayout.link("w3", "w1", true));
        ChatLayout.moveTab(ChatChannel.CLIENT_CONSOLE, "w2", 0);
        ChatLayout.moveTab(ChatChannel.SERVER_CONSOLE, "w2", 0);
        ChatLayout.moveTab(ChatChannel.OPERATOR, "w2", 0);
        assertNull(WindowLayout.window("w1"));
        assertFalse(WindowLayout.window("w3").isLinked());
        // Loading validates links: a missing target, self, and cycles.
        WindowLayoutStore.load(Arrays.asList(
                "window w1 x=0 y=0 link=w2:above tabs=global",
                "window w2 x=0 y=0 link=w1:below tabs=ooc",
                "window w3 x=0 y=0 link=w9:below tabs=party",
                "window w4 x=0 y=0 link=w4:above tabs=client_console"));
        assertTrue(WindowLayout.window("w1").isLinked());
        assertFalse(WindowLayout.window("w2").isLinked());
        assertFalse(WindowLayout.window("w3").isLinked());
        assertFalse(WindowLayout.window("w4").isLinked());
    }

    @Test
    public void windowCountIsBounded() {
        List<ChatChannel> order = ChatChannel.presentationOrder();
        int detached = 0;
        for (ChatChannel channel : order) {
            Window source = ChatLayout.windowOf(channel);
            if (ChatTab.channelsOf(source).size() > 1
                    && ChatLayout.detach(channel, 0.0D, 0.0D) != null) {
                detached++;
            }
        }
        // Every window that could be split was, once: six new windows,
        // one per channel, which with eight channels fills the cap.
        assertEquals(6, detached);
        assertEquals(WindowLayout.MAX_WINDOWS,
                WindowLayout.windows().size());
        for (Window window : WindowLayout.windows()) {
            assertEquals(1, ChatTab.channelsOf(window).size());
        }
        // A place freed by closing a tab is taken by a conversation, and
        // the cap refuses the next one a window of its own.
        assertTrue(ChatLayout.close(ChatChannel.SERVER_CONSOLE));
        assertEquals(WindowLayout.MAX_WINDOWS - 1,
                WindowLayout.windows().size());
        ChatTab frodo = ChatLayout.openTab(ChatTab.whisper("Frodo"), "w2");
        assertNotNull(WindowLayout.detach(frodo, 0.0D, 0.0D));
        assertEquals(WindowLayout.MAX_WINDOWS,
                WindowLayout.windows().size());
        ChatTab sam = ChatLayout.openTab(ChatTab.whisper("Sam"), "w2");
        assertNull(WindowLayout.detach(sam, 0.0D, 0.0D));
        assertEquals(WindowLayout.MAX_WINDOWS,
                WindowLayout.windows().size());
    }

    @Test
    public void loadRepairsStaleDuplicateAndMissingEntries() {
        // Party listed twice, an unknown window id, an empty window,
        // percents out of range, Console placed nowhere.
        WindowLayoutStore.load(Arrays.asList(
                "window w3 locked=true x=250 y=-5 active=ooc tabs=party,ooc",
                "window w8 x=0 y=100 active=party tabs=global,party,proximity",
                "window bogus x=0 y=0 tabs=faction",
                "window w7 x=0 y=0 tabs=",
                "window w3 x=0 y=0 tabs=faction",
                "closed operator",
                "muted ooc",
                "muted operator",
                "feed x=120 y=33"));
        assertEquals(100.0D, ChatLayout.feedOffsetX(), 0.0D);
        assertEquals(33.0D, ChatLayout.feedOffsetY(), 0.0D);

        assertEquals(2, WindowLayout.windows().size());
        Window w3 = WindowLayout.firstWindow();
        assertEquals("w3", w3.getId());
        // Party keeps its first placement; the unplaced channels land
        // in the first window.
        assertEquals(Arrays.asList(ChatChannel.PARTY, ChatChannel.OOC,
                ChatChannel.FACTION, ChatChannel.CLIENT_CONSOLE,
                ChatChannel.SERVER_CONSOLE),
                ChatTab.channelsOf(w3));
        assertEquals(ChatChannel.OOC, ChatTab.frontChannelOf(w3));
        assertTrue(w3.isLocked());
        // A window's percent is bounded a whole window past the margins
        // (250 to 200); a little past the top is kept as stored.
        assertEquals(200.0D, w3.getOffsetX(), 0.0D);
        assertEquals(-5.0D, w3.getOffsetY(), 0.0D);
        // The second window keeps its id and its place; its front tab,
        // Party, went to the first window, so the first tab left stands.
        Window second = WindowLayout.windows().get(1);
        assertEquals("w8", second.getId());
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.PROXIMITY),
                ChatTab.channelsOf(second));
        assertEquals(ChatChannel.GLOBAL, ChatTab.frontChannelOf(second));
        assertEquals(100.0D, second.getOffsetY(), 0.0D);
        assertEquals(Collections.singletonList(ChatChannel.OPERATOR),
                ChatLayout.closedChannels());
        assertTrue(ChatLayout.isMuted(ChatChannel.OOC));
        assertTrue(ChatLayout.isMuted(ChatChannel.OPERATOR));
        // New windows number on from the highest id seen.
        assertEquals("w9", ChatLayout.detach(ChatChannel.PROXIMITY,
                0.0D, 0.0D).getId());
        assertEquals(1, this.changes);
    }

    @Test
    public void loadWithNothingUsableFallsBackSensibly() {
        List<String> everythingClosed = new ArrayList<String>();
        for (ChatChannel channel : ChatChannel.values()) {
            everythingClosed.add("closed " + channel.getId());
        }
        WindowLayoutStore.load(everythingClosed);
        // Everything closed is a layout of its own: the file described
        // no window, so none is opened.
        assertTrue(WindowLayout.isEmpty());
        assertNull(WindowLayout.firstWindow());
        assertEquals(0.0D, ChatLayout.feedOffsetX(), 0.0D);
        WindowLayoutStore.load(Arrays.asList("closed operator"));
        // No windows at all: one window with everything still open.
        assertEquals(1, WindowLayout.windows().size());
        Window only = WindowLayout.firstWindow();
        assertEquals("w1", only.getId());
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC, ChatChannel.PARTY,
                ChatChannel.CLIENT_CONSOLE, ChatChannel.SERVER_CONSOLE),
                ChatTab.channelsOf(only));
        assertEquals(ChatChannel.GLOBAL, ChatTab.frontChannelOf(only));
        assertEquals(100.0D, only.getOffsetY(), 0.0D);
    }
}
