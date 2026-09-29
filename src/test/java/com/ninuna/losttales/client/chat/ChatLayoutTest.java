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
        TwoWindowLayout.reset();
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

    /**
     * A new player starts with one window of Global and OOC, Global in
     * front, in the screen's bottom-left quarter. The conversations left
     * closed open with their first line; Operator and the consoles wait
     * to be opened by hand. The file written from it reads back the same.
     */
    @Test
    public void aNewPlayerStartsWithOneWindowOfGlobalAndOoc() {
        ChatLayout.reset();
        assertEquals(1, WindowLayout.windows().size());
        Window window = WindowLayout.firstWindow();
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.OOC),
                ChatLayoutViews.channelsOf(window));
        assertEquals(ChatChannel.GLOBAL, ChatLayoutViews.frontChannelOf(window));
        assertEquals(Window.ScreenFill.BOTTOM_LEFT, window.getFill());
        assertEquals(Arrays.asList(ChatChannel.PROXIMITY, ChatChannel.FACTION,
                ChatChannel.PARTY, ChatChannel.OPERATOR,
                ChatChannel.CLIENT_CONSOLE, ChatChannel.SERVER_CONSOLE),
                ChatLayout.closedChannels());
        assertFalse(ChatLayout.isHidden(ChatTab.of(ChatChannel.PROXIMITY)));
        assertFalse(ChatLayout.isHidden(ChatTab.of(ChatChannel.FACTION)));
        assertFalse(ChatLayout.isHidden(ChatTab.of(ChatChannel.PARTY)));
        assertTrue(ChatLayout.isHidden(ChatTab.of(ChatChannel.OPERATOR)));
        assertTrue(ChatLayout.isHidden(ChatTab.of(ChatChannel.CLIENT_CONSOLE)));
        assertTrue(ChatLayout.isHidden(ChatTab.of(ChatChannel.SERVER_CONSOLE)));
        // The closed-chat feed starts where vanilla draws the chat.
        assertEquals(0.0D, ChatLayout.feedOffsetX(), 0.0D);
        assertEquals(100.0D, ChatLayout.feedOffsetY(), 0.0D);

        List<String> written = WindowLayoutStore.describe();
        WindowLayoutStore.load(written);
        assertEquals(1, WindowLayout.windows().size());
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.OOC),
                ChatLayoutViews.channelsOf(WindowLayout.firstWindow()));
        assertEquals(Window.ScreenFill.BOTTOM_LEFT,
                WindowLayout.firstWindow().getFill());
        assertTrue(ChatLayout.isHidden(ChatTab.of(ChatChannel.SERVER_CONSOLE)));
        assertFalse(ChatLayout.isOpen(ChatChannel.PROXIMITY));
        assertEquals(written, WindowLayoutStore.describe());
    }

    /** The two windows the other tests work in: the consoles, then the rest. */
    @Test
    public void theTestsTwoWindowsHoldEveryChannel() {
        assertEquals(2, WindowLayout.windows().size());
        Window console = WindowLayout.firstWindow();
        assertEquals("w1", console.getId());
        assertEquals(Arrays.asList(ChatChannel.CLIENT_CONSOLE,
                ChatChannel.SERVER_CONSOLE, ChatChannel.OPERATOR),
                ChatLayoutViews.channelsOf(console));
        Window conversation = WindowLayout.windows().get(1);
        assertEquals("w2", conversation.getId());
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC,
                ChatChannel.PARTY), ChatLayoutViews.channelsOf(conversation));
        assertEquals(ChatChannel.GLOBAL, ChatLayoutViews.frontChannelOf(conversation));
        assertTrue(ChatLayout.closedChannels().isEmpty());
        ChatLayout.setFeedPosition(40.0D, -3.0D, true);
        assertEquals(40.0D, ChatLayout.feedOffsetX(), 0.0D);
        assertEquals(0.0D, ChatLayout.feedOffsetY(), 0.0D);
    }

    @Test
    public void closeRestoreAndMuteKeepIdentityAndNotifyTheStore() {
        assertTrue(ChatLayout.close(ChatTab.of(ChatChannel.OPERATOR)));
        assertFalse(ChatLayout.isOpen(ChatChannel.OPERATOR));
        assertNull(WindowLayout.windowOf(ChatTab.of(ChatChannel.OPERATOR)));
        assertEquals(Collections.singletonList(ChatChannel.OPERATOR),
                ChatLayout.closedChannels());
        assertEquals(1, this.changes);
        // Closing again is a no-op; restoring lands in the first window.
        assertFalse(ChatLayout.close(ChatTab.of(ChatChannel.OPERATOR)));
        assertTrue(ChatLayoutViews.reopen(ChatChannel.OPERATOR));
        List<ChatChannel> tabs = ChatLayoutViews.channelsOf(WindowLayout.firstWindow());
        assertEquals(ChatChannel.OPERATOR, tabs.get(tabs.size() - 1));
        assertEquals(ChatChannel.OPERATOR,
                ChatLayoutViews.frontChannelOf(WindowLayout.firstWindow()));
        assertFalse(ChatLayoutViews.reopen(ChatChannel.OPERATOR));
        assertEquals(2, this.changes);

        ChatLayout.setNotification(ChatTab.of(ChatChannel.OOC), ChatNotification.NOTHING);
        assertTrue(ChatLayout.isMuted(ChatChannel.OOC));
        assertTrue(ChatLayout.isOpen(ChatChannel.OOC));
        ChatLayout.setNotification(ChatTab.of(ChatChannel.OOC),
                ChatNotification.EVERYTHING);
        assertFalse(ChatLayout.isMuted(ChatChannel.OOC));
        assertEquals(4, this.changes);
    }

    /**
     * Open, the notification choice and closed are separate things:
     * closing a tab leaves its choice alone, and the choice is still
     * there when the channel comes back.
     */
    @Test
    public void closingNeverTouchesTheChoiceAndItSurvivesRestore() {
        ChatLayout.setNotification(ChatTab.of(ChatChannel.PARTY), ChatNotification.NOTHING);
        assertTrue(ChatLayout.close(ChatTab.of(ChatChannel.PARTY)));
        assertEquals(ChatNotification.NOTHING,
                ChatLayout.notification(ChatTab.of(ChatChannel.PARTY)));
        assertFalse(ChatLayout.isOpen(ChatChannel.PARTY));
        assertTrue(ChatLayoutViews.reopen(ChatChannel.PARTY));
        assertEquals(ChatNotification.NOTHING,
                ChatLayout.notification(ChatTab.of(ChatChannel.PARTY)));
        assertTrue(ChatLayout.close(ChatTab.of(ChatChannel.OOC)));
        assertEquals(ChatNotification.EVERYTHING,
                ChatLayout.notification(ChatTab.of(ChatChannel.OOC)));
        assertTrue(ChatLayoutViews.reopen(ChatChannel.OOC));
        assertEquals(ChatNotification.EVERYTHING,
                ChatLayout.notification(ChatTab.of(ChatChannel.OOC)));
        // Choosing for a closed channel is allowed and kept for its return.
        assertTrue(ChatLayout.close(ChatTab.of(ChatChannel.OOC)));
        ChatLayout.setNotification(ChatTab.of(ChatChannel.OOC),
                ChatNotification.ONLY_MENTIONS);
        assertTrue(ChatLayoutViews.reopen(ChatChannel.OOC));
        assertEquals(ChatNotification.ONLY_MENTIONS,
                ChatLayout.notification(ChatTab.of(ChatChannel.OOC)));
    }

    /**
     * Moving the closed-chat feed, which is all the HUD placement editor
     * does with the chat layout, moves no window, and reaches the store
     * only when the caller asks for it.
     */
    @Test
    public void movingTheFeedLeavesEveryWindowAlone() {
        Window console = WindowLayout.window("w1");
        Window conversation = WindowLayout.window("w2");
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
    }

    /**
     * One choice per conversation decides what reaches the player while
     * they are not reading it: Everything by default, Only Mentions or
     * Nothing. It answers for the conversation's row entry, so a line's
     * own tab asks the same question; setting what is already set is no
     * change; a whisper keeps its choice past the session, an NPC
     * conversation's goes with it.
     */
    @Test
    public void oneChoicePerConversationDecidesFeedAndChime() {
        ChatTab party = ChatTab.of(ChatChannel.PARTY);
        ChatTab ooc = ChatTab.of(ChatChannel.OOC);
        assertEquals(ChatNotification.EVERYTHING, ChatLayout.notification(party));
        assertTrue(ChatLayout.isPingAudible(party));
        ChatLayout.setNotification(party, ChatNotification.NOTHING);
        assertTrue(ChatLayout.isMuted(party));
        assertFalse(ChatLayout.isPingAudible(party));
        assertFalse(ChatLayout.chimes(party, true));
        ChatLayout.setNotification(ooc, ChatNotification.ONLY_MENTIONS);
        assertFalse(ChatLayout.isMuted(ooc));
        assertTrue(ChatLayout.chimes(ooc, true));
        assertFalse(ChatLayout.chimes(ooc, false));
        assertEquals(2, this.changes);
        // Setting what is already set is not a change.
        ChatLayout.setNotification(party, ChatNotification.NOTHING);
        assertEquals(2, this.changes);
        assertTrue(ChatLayout.close(ChatTab.of(ChatChannel.PARTY)));
        assertEquals(ChatNotification.NOTHING, ChatLayout.notification(party));
        assertEquals(Arrays.asList(ooc, party), ChatLayout.notificationTabs());
        // The steps go round the three, a right-click back.
        assertEquals(ChatNotification.NOTHING,
                ChatLayout.stepNotification(ooc, false));
        assertEquals(ChatNotification.ONLY_MENTIONS,
                ChatLayout.stepNotification(ooc, true));
        // A whisper's every line chimes where everything reaches the
        // player, only one addressed to them where only mentions do.
        ChatTab whisper = ChatLayout.openWhisper("Bilbo", "", null);
        assertTrue(ChatLayout.chimes(whisper, false));
        ChatLayout.setNotification(whisper, ChatNotification.ONLY_MENTIONS);
        assertFalse(ChatLayout.chimes(whisper, false));
        assertTrue(ChatLayout.chimes(whisper, true));
        ChatTab wanderer = ChatLayout.openTab(ChatTab.npc("Grey Wanderer"),
                "w2");
        ChatLayout.setNotification(wanderer, ChatNotification.NOTHING);
        assertFalse(ChatLayout.notificationTabs().contains(wanderer));
        ChatLayout.closeConversations();
        assertEquals(ChatNotification.ONLY_MENTIONS,
                ChatLayout.notification(whisper));
        assertEquals(ChatNotification.EVERYTHING,
                ChatLayout.notification(wanderer));
        assertEquals(ChatNotification.NOTHING, ChatLayout.notification(party));
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
        assertEquals(ChatNotification.EVERYTHING,
                ChatLayout.notification(party));
        assertEquals(1, this.changes);
        // Setting what is already set is not a change.
        ChatLayout.setHidden(party, true);
        assertEquals(1, this.changes);
        assertTrue(ChatLayout.close(ChatTab.of(ChatChannel.PARTY)));
        assertTrue(ChatLayout.isHidden(party));
        assertEquals(Collections.singletonList(party),
                ChatLayout.hiddenTabs());
        assertTrue(ChatLayoutViews.reopen(ChatChannel.PARTY));
        assertTrue(ChatLayout.isHidden(party));
        ChatLayout.setHidden(party, false);
        assertFalse(ChatLayout.isHidden(party));
        // Conversations drop the preference with their tabs.
        ChatTab whisper = ChatLayout.openWhisper("Bilbo", "", null);
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
        Window w3 = WindowLayout.detach(ChatTab.of(ChatChannel.PARTY), 50.0D,
                50.0D);
        assertNotNull(w3);
        assertEquals(Arrays.asList(w3, w1, w2), WindowLayout.stacked());
        WindowLayout.raise(w3.getId());
        assertEquals(Arrays.asList(w1, w2, w3), WindowLayout.stacked());
        assertTrue(WindowLayout.moveTab(ChatTab.of(ChatChannel.PARTY), "w2", 0));
        assertEquals(Arrays.asList(w1, w2), WindowLayout.stacked());
        ChatLayout.reset();
        assertEquals(WindowLayout.windows(), WindowLayout.stacked());
    }

    /** Every tab closes, down to no tab and no window at all. */
    @Test
    public void everyTabClosesAndTheEmptyLayoutIsValid() {
        assertEquals(8, WindowLayout.order().size());
        List<ChatChannel> order = new ArrayList<ChatChannel>(
                ChatLayoutViews.orderChannels());
        for (int index = 0; index < order.size(); index++) {
            assertTrue(WindowLayout.isClosable(ChatTab.of(order.get(index))));
            assertTrue(ChatLayout.close(ChatTab.of(order.get(index))));
        }
        assertEquals(0, WindowLayout.order().size());
        assertTrue(WindowLayout.isEmpty());
        assertTrue(WindowLayout.windows().isEmpty());
        assertNull(WindowLayout.firstWindow());
        assertEquals(order.size(),
                ChatLayout.closedChannels().size());
        // A closed tab is not closable, and a message finds no window to
        // open itself in: the channel keeps receiving, closed.
        assertFalse(WindowLayout.isClosable(ChatTab.of(order.get(0))));
        assertFalse(ChatLayout.close(ChatTab.of(order.get(0))));
        assertFalse(ChatLayoutViews.reopen(order.get(0)));
        assertNull(ChatLayout.openTab(ChatTab.whisper("Someone", ""),
                null));
        assertTrue(WindowLayout.isEmpty());
        // The + opens one back into a window of its own.
        assertNotNull(WindowLayout.openInNewWindow(
                ChatTab.of(order.get(0))));
        assertEquals(1, WindowLayout.windows().size());
        assertEquals(Collections.singletonList(order.get(0)),
                ChatLayoutViews.orderChannels());
        assertEquals(order.get(0),
                ChatLayoutViews.frontChannelOf(WindowLayout.firstWindow()));
        // Repeated closing and reopening leaves a consistent layout.
        for (int round = 0; round < 5; round++) {
            assertTrue(ChatLayout.close(ChatTab.of(order.get(0))));
            assertTrue(WindowLayout.isEmpty());
            assertNotNull(WindowLayout.openInNewWindow(
                    ChatTab.of(order.get(0))));
        }
        assertEquals(1, WindowLayout.order().size());
    }

    /** A whole window closes at once, and its channels survive it. */
    @Test
    public void closingAWindowKeepsItsChannels() {
        assertFalse(WindowLayout.closeWindow("nope"));
        assertTrue(WindowLayout.setLocked("w1", true));
        assertFalse(WindowLayout.closeWindow("w1"));
        assertTrue(WindowLayout.setLocked("w1", false));
        assertTrue(WindowLayout.closeWindow("w1"));
        assertNull(WindowLayout.window("w1"));
        assertEquals(1, WindowLayout.windows().size());
        // Its channels are closed, not gone: still restorable, and
        // still carrying whatever preferences they had.
        assertTrue(ChatLayout.closedChannels().contains(ChatChannel.CLIENT_CONSOLE));
        assertTrue(ChatLayoutViews.reopen(ChatChannel.CLIENT_CONSOLE, "w2"));
        assertTrue(WindowLayout.closeWindow("w2"));
        assertTrue(WindowLayout.isEmpty());
    }

    /** A marked group moves as one and keeps its order. */
    @Test
    public void groupsOfTabsMoveTogetherAndKeepTheirOrder() {
        List<ChatChannel> start = ChatLayoutViews.channelsOf(WindowLayout.window("w2"));
        // A non-contiguous pair from the conversation window, moved to
        // its front: they arrive as one run, in row order.
        List<ChatTab> group = Arrays.asList(
                ChatTab.of(start.get(3)), ChatTab.of(start.get(1)));
        assertTrue(WindowLayout.moveTabs(group, "w2", 0));
        List<ChatChannel> moved = ChatLayoutViews.channelsOf(WindowLayout.window("w2"));
        assertEquals(start.size(), moved.size());
        assertEquals(start.get(1), moved.get(0));
        assertEquals(start.get(3), moved.get(1));
        assertEquals(start.get(0), moved.get(2));
        // Across windows: the group leaves one row and joins another.
        assertTrue(WindowLayout.moveTabs(group, "w1", 0));
        assertEquals(Arrays.asList(start.get(1), start.get(3),
                ChatChannel.CLIENT_CONSOLE, ChatChannel.SERVER_CONSOLE,
                ChatChannel.OPERATOR),
                ChatLayoutViews.channelsOf(WindowLayout.window("w1")));
        assertEquals(start.get(3),
                ChatLayoutViews.frontChannelOf(WindowLayout.window("w1")));
        assertFalse(WindowLayout.window("w2").contains(ChatTab.of(start.get(1))));
        // A group detaches into a window of its own the same way.
        Window detached = WindowLayout.detach(group, 20.0D, 30.0D);
        assertNotNull(detached);
        assertEquals(Arrays.asList(start.get(1), start.get(3)),
                ChatLayoutViews.channelsOf(detached));
        assertEquals(Arrays.asList(ChatChannel.CLIENT_CONSOLE,
                ChatChannel.SERVER_CONSOLE, ChatChannel.OPERATOR),
                ChatLayoutViews.channelsOf(WindowLayout.window("w1")));
        // Tabs from two windows are not a group, and neither is a closed
        // one: both are refused whole rather than half-applied.
        assertFalse(WindowLayout.moveTabs(Arrays.asList(
                ChatTab.of(start.get(1)), ChatTab.of(ChatChannel.CLIENT_CONSOLE)),
                "w1", 0));
        assertEquals(Arrays.asList(start.get(1), start.get(3)),
                ChatLayoutViews.channelsOf(detached));
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
        Window detached = WindowLayout.detach(ChatTab.of(ChatChannel.CLIENT_CONSOLE),
                20.0D, 30.0D);
        assertNotNull(detached);
        assertTrue(detached != source);
        assertEquals(source.getOwnHeight(), detached.getOwnHeight(), 0.0D);
        assertEquals(source.getOwnWidth(), detached.getOwnWidth());
    }

    @Test
    public void everyWindowIsEqualAndTheLastOneIsNoDifferent() {
        // Emptying the console window by docking drops it.
        assertTrue(WindowLayout.moveTab(ChatTab.of(ChatChannel.CLIENT_CONSOLE), "w2", 0));
        assertTrue(WindowLayout.moveTab(ChatTab.of(ChatChannel.SERVER_CONSOLE),
                "w2", 99));
        assertTrue(WindowLayout.moveTab(ChatTab.of(ChatChannel.OPERATOR), "w2", 99));
        assertNull(WindowLayout.window("w1"));
        assertEquals(1, WindowLayout.windows().size());
        assertEquals(ChatChannel.CLIENT_CONSOLE,
                ChatLayoutViews.channelsOf(WindowLayout.firstWindow()).get(0));
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (channel != ChatChannel.CLIENT_CONSOLE) {
                ChatLayout.close(ChatTab.of(channel));
            }
        }
        assertEquals(1, ChatLayoutViews.channelsOf(WindowLayout.firstWindow()).size());
        ChatChannel last = ChatLayoutViews.channelsOf(WindowLayout.firstWindow()).get(0);
        // Its only tab dragged out moves the window like any other.
        assertSame(WindowLayout.firstWindow(),
                WindowLayout.detach(ChatTab.of(last), 10.0D, 20.0D));
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
        assertTrue(WindowLayout.moveTab(ChatTab.of(ChatChannel.OOC), "w2", 0));
        assertEquals(ChatChannel.OOC,
                ChatLayoutViews.channelsOf(WindowLayout.window("w2")).get(0));
        assertFalse(WindowLayout.moveTab(ChatTab.of(ChatChannel.OOC), "w2", 0));

        // Detach Party into its own window.
        Window party = WindowLayout.detach(ChatTab.of(ChatChannel.PARTY),
                60.0D, 120.0D);
        assertNotNull(party);
        assertEquals("w3", party.getId());
        assertEquals(Collections.singletonList(ChatChannel.PARTY),
                ChatLayoutViews.channelsOf(party));
        assertEquals(ChatChannel.PARTY, ChatLayoutViews.frontChannelOf(party));
        assertEquals(60.0D, party.getOffsetX(), 0.0D);
        // A percent past the margin stands: the window may hang off the
        // screen by up to its own size.
        assertEquals(120.0D, party.getOffsetY(), 0.0D);
        assertEquals(3, WindowLayout.windows().size());
        assertSame(party, WindowLayout.windowOf(ChatTab.of(ChatChannel.PARTY)));
        assertFalse(WindowLayout.window("w2").contains(ChatTab.of(ChatChannel.PARTY)));
        List<ChatChannel> order = ChatLayoutViews.orderChannels();
        assertEquals(ChatChannel.PARTY, order.get(order.size() - 1));

        // Dock Faction into the party window, at the front.
        assertTrue(WindowLayout.moveTab(ChatTab.of(ChatChannel.FACTION), "w3", 0));
        assertEquals(Arrays.asList(ChatChannel.FACTION, ChatChannel.PARTY),
                ChatLayoutViews.channelsOf(party));
        assertEquals(ChatChannel.FACTION, ChatLayoutViews.frontChannelOf(party));

        // A window's only tab dragged out just moves the window.
        Window moved = WindowLayout.detach(ChatTab.of(ChatChannel.FACTION),
                5.0D, 5.0D);
        assertNotNull(moved);
        assertEquals("w4", moved.getId());
        assertEquals(4, WindowLayout.windows().size());
        assertSame(party, WindowLayout.detach(ChatTab.of(ChatChannel.PARTY),
                1.0D, 2.0D));
        assertEquals(1.0D, party.getOffsetX(), 0.0D);
        assertEquals(4, WindowLayout.windows().size());

        // Docking the last tab elsewhere empties the window away.
        assertTrue(WindowLayout.moveTab(ChatTab.of(ChatChannel.PARTY), "w2", 99));
        assertNull(WindowLayout.window("w3"));
        assertEquals(3, WindowLayout.windows().size());
        List<ChatChannel> tabs = ChatLayoutViews.channelsOf(WindowLayout.window("w2"));
        assertEquals(ChatChannel.PARTY, tabs.get(tabs.size() - 1));
        // Closing a window's last tab drops the window too.
        assertTrue(ChatLayout.close(ChatTab.of(ChatChannel.FACTION)));
        assertNull(WindowLayout.window("w4"));
        assertEquals(Collections.singletonList(ChatChannel.FACTION),
                ChatLayout.closedChannels());
    }

    @Test
    public void lockRefusesLayoutChangesButNotPreferences() {
        Window party = WindowLayout.detach(ChatTab.of(ChatChannel.PARTY),
                0.0D, 0.0D);
        assertTrue(WindowLayout.setLocked("w3", true));
        assertFalse(WindowLayout.setLocked("w3", true));
        assertTrue(party.isLocked());
        // A locked window keeps the tabs it has: nothing moves out of
        // it, and nothing closes in it either.
        assertNull(WindowLayout.detach(ChatTab.of(ChatChannel.PARTY), 1.0D, 1.0D));
        assertFalse(WindowLayout.isClosable(ChatTab.of(ChatChannel.PARTY)));
        assertFalse(ChatLayout.close(ChatTab.of(ChatChannel.PARTY)));
        assertFalse(WindowLayout.moveTab(ChatTab.of(ChatChannel.OOC), "w3", 0));
        assertFalse(WindowLayout.moveTab(ChatTab.of(ChatChannel.PARTY), "w2", 0));
        // Position and the notification choice are not layout movement.
        assertTrue(WindowLayout.setPosition("w3", 30.0D, 40.0D, false));
        assertEquals(30.0D, party.getOffsetX(), 0.0D);
        ChatLayout.setNotification(ChatTab.of(ChatChannel.PARTY), ChatNotification.NOTHING);
        assertTrue(ChatLayout.isMuted(ChatChannel.PARTY));
        WindowLayout.setLocked("w2", true);
        assertFalse(WindowLayout.moveTab(ChatTab.of(ChatChannel.OOC), "w2", 0));
        assertFalse(ChatLayout.close(ChatTab.of(ChatChannel.OOC)));
        // Unlocking gives the row its cross back.
        assertTrue(WindowLayout.setLocked("w2", false));
        assertTrue(ChatLayout.close(ChatTab.of(ChatChannel.OOC)));
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
        ChatTab whisper = ChatLayout.openWhisper("Bilbo", "", null);
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
                ChatLayout.openWhisper("Bilbo", "", null));
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
                ChatLayout.openWhisper("Bilbo", "", null)).getId());
        WindowLayout.raise("w2");
        assertEquals("w2", WindowLayout.windowOf(
                ChatLayout.openWhisper("Frodo", "", null)).getId());
        // The window asked for wins over the front one.
        assertEquals("w1", WindowLayout.windowOf(
                ChatLayout.openWhisper("Sam", "", "w1")).getId());
        // A locked window asked for hands the tab to the front unlocked one.
        WindowLayout.setLocked("w1", true);
        assertEquals("w2", WindowLayout.windowOf(
                ChatLayout.openWhisper("Merry", "", "w1")).getId());
    }

    @Test
    public void aWindowThatOpensByItselfStandsInFront() {
        for (Window window : WindowLayout.windows()) {
            WindowLayout.setLocked(window.getId(), true);
        }
        WindowLayout.raise("w2");
        Window opened = WindowLayout.windowOf(
                ChatLayout.openWhisper("Bilbo", "", null));
        java.util.List<Window> order = WindowLayout.stacked();
        assertSame(opened, order.get(order.size() - 1));
        ChatTab plus = ChatTab.whisper("Frodo", "");
        WindowLayout.openInNewWindow(plus);
        order = WindowLayout.stacked();
        assertSame(WindowLayout.windowOf(plus), order.get(order.size() - 1));
    }

    @Test
    public void theEmptyStateOpensAWindowCascadedFromTheFrontOne() {
        WindowLayout.setWindowHeight("w2", 150.0D, true);
        WindowLayout.raise("w2");
        ChatTab whisper = ChatTab.whisper("Pippin", "");
        assertNotNull(WindowLayout.openInNewWindow(whisper));
        Window opened = WindowLayout.windowOf(whisper);
        assertEquals(150.0D, opened.getOwnHeight(), 0.0D);
        assertTrue(opened.getOffsetX() > WindowLayout.window("w2").getOffsetX());
    }

    @Test
    public void onlyTheTabsBoundHowManyWindowsStand() {
        List<ChatChannel> order = ChatChannel.presentationOrder();
        for (ChatChannel channel : order) {
            Window source = WindowLayout.windowOf(ChatTab.of(channel));
            if (ChatLayoutViews.channelsOf(source).size() > 1) {
                assertNotNull(WindowLayout.detach(ChatTab.of(channel), 0.0D, 0.0D));
            }
        }
        for (Window window : WindowLayout.windows()) {
            assertEquals(1, ChatLayoutViews.channelsOf(window).size());
        }
        // Past every channel's own window, each whisper gets one too:
        // no count of windows refuses a tab its own.
        int before = WindowLayout.windows().size();
        for (int index = 0; index < 12; index++) {
            ChatTab friend = ChatLayout.openTab(
                    ChatTab.whisper("Friend" + index, ""), "w2");
            assertNotNull(WindowLayout.detach(friend, 0.0D, 0.0D));
        }
        assertEquals(before + 12, WindowLayout.windows().size());
        // A window holds at least one tab and a tab stands once, so
        // there are never more windows than tabs.
        assertTrue(WindowLayout.windows().size() <= WindowLayout.order().size());
    }

    @Test
    public void aWindowAddedWithAnOpenTabLeavesItWhereItIs() {
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        Window holding = WindowLayout.windowOf(global);
        assertNull("nothing new to hold", WindowLayout.addWindow(
                Collections.singletonList(global), global, 0.0D, 0.0D));
        assertSame(holding, WindowLayout.windowOf(global));
    }

    @Test
    public void loadRepairsStaleDuplicateAndMissingEntries() {
        // Party listed twice, an unknown window id, an empty window,
        // percents out of range, Faction and the consoles placed nowhere.
        WindowLayoutStore.load(Arrays.asList(
                "window w3 locked=true x=250 y=-5 active=ooc tabs=party,ooc",
                "window w8 x=0 y=100 active=party tabs=global,party,proximity",
                "window bogus x=0 y=0 tabs=faction",
                "window w7 x=0 y=0 tabs=",
                "window w3 x=0 y=0 tabs=faction",
                "closed operator",
                "notify\tnothing\tooc",
                "notify\tmentions\toperator",
                "muted party",
                "feed x=120 y=33"));
        assertEquals(100.0D, ChatLayout.feedOffsetX(), 0.0D);
        assertEquals(33.0D, ChatLayout.feedOffsetY(), 0.0D);

        assertEquals(2, WindowLayout.windows().size());
        Window w3 = WindowLayout.firstWindow();
        assertEquals("w3", w3.getId());
        // Party keeps its first placement; the unplaced channels land
        // in the first window, the consoles aside: they wait in the +,
        // hidden, as for a new player.
        assertEquals(Arrays.asList(ChatChannel.PARTY, ChatChannel.OOC,
                ChatChannel.FACTION), ChatLayoutViews.channelsOf(w3));
        assertTrue(ChatLayout.isHidden(ChatTab.of(ChatChannel.CLIENT_CONSOLE)));
        assertTrue(ChatLayout.isHidden(ChatTab.of(ChatChannel.SERVER_CONSOLE)));
        assertEquals(ChatChannel.OOC, ChatLayoutViews.frontChannelOf(w3));
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
                ChatLayoutViews.channelsOf(second));
        assertEquals(ChatChannel.GLOBAL, ChatLayoutViews.frontChannelOf(second));
        assertEquals(100.0D, second.getOffsetY(), 0.0D);
        assertEquals(Arrays.asList(ChatChannel.OPERATOR,
                ChatChannel.CLIENT_CONSOLE, ChatChannel.SERVER_CONSOLE),
                ChatLayout.closedChannels());
        assertEquals(ChatNotification.NOTHING,
                ChatLayout.notification(ChatTab.of(ChatChannel.OOC)));
        assertEquals(ChatNotification.ONLY_MENTIONS,
                ChatLayout.notification(ChatTab.of(ChatChannel.OPERATOR)));
        // The old mute lines are read no more.
        assertEquals(ChatNotification.EVERYTHING,
                ChatLayout.notification(ChatTab.of(ChatChannel.PARTY)));
        // New windows number on from the highest id seen.
        assertEquals("w9", WindowLayout.detach(ChatTab.of(ChatChannel.PROXIMITY),
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
        // No windows at all: one window with everything still open but
        // the consoles, which wait in the + as for a new player.
        assertEquals(1, WindowLayout.windows().size());
        Window only = WindowLayout.firstWindow();
        assertEquals("w1", only.getId());
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC, ChatChannel.PARTY),
                ChatLayoutViews.channelsOf(only));
        assertTrue(ChatLayout.isHidden(ChatTab.of(ChatChannel.CLIENT_CONSOLE)));
        assertEquals(ChatChannel.GLOBAL, ChatLayoutViews.frontChannelOf(only));
        assertEquals(100.0D, only.getOffsetY(), 0.0D);
    }
}
