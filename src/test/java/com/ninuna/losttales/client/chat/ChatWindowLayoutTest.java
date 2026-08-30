package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
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

public final class ChatWindowLayoutTest {
    private int changes;

    @Before
    public void reset() {
        ChatWindowLayout.reset();
        this.changes = 0;
        ChatWindowLayout.setChangeListener(new Runnable() {
            @Override
            public void run() {
                changes++;
            }
        });
    }

    @After
    public void cleanUp() {
        ChatWindowLayout.setChangeListener(null);
        ChatWindowLayout.reset();
    }

    @Test
    public void defaultLayoutIsAConsoleWindowAndAConversationWindow() {
        assertEquals(2, ChatWindowLayout.windows().size());
        ChatWindow console = ChatWindowLayout.firstWindow();
        assertEquals("w1", console.getId());
        assertEquals(Arrays.asList(ChatChannel.CONSOLE, ChatChannel.ADMIN),
                console.getChannels());
        assertEquals(ChatChannel.CONSOLE, console.getActiveChannel());
        assertEquals(0.0D, console.getOffsetX(), 0.0D);
        assertEquals(0.0D, console.getOffsetY(), 0.0D);
        ChatWindow conversation = ChatWindowLayout.windows().get(1);
        assertEquals("w2", conversation.getId());
        assertEquals(Arrays.asList(ChatChannel.ALL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC, ChatChannel.DISCORD,
                ChatChannel.PARTY), conversation.getChannels());
        assertEquals(ChatChannel.ALL, conversation.getActiveChannel());
        assertEquals(0.0D, conversation.getOffsetX(), 0.0D);
        assertEquals(100.0D, conversation.getOffsetY(), 0.0D);
        // The closed-chat feed starts where vanilla draws the chat.
        assertEquals(0.0D, ChatWindowLayout.feedOffsetX(), 0.0D);
        assertEquals(100.0D, ChatWindowLayout.feedOffsetY(), 0.0D);
        ChatWindowLayout.setFeedPosition(40.0D, -3.0D, true);
        assertEquals(40.0D, ChatWindowLayout.feedOffsetX(), 0.0D);
        assertEquals(0.0D, ChatWindowLayout.feedOffsetY(), 0.0D);
        assertTrue(ChatWindowLayout.closedChannels().isEmpty());
        assertEquals(Arrays.asList(ChatChannel.CONSOLE, ChatChannel.ADMIN,
                ChatChannel.ALL, ChatChannel.PROXIMITY, ChatChannel.FACTION,
                ChatChannel.OOC, ChatChannel.DISCORD, ChatChannel.PARTY),
                ChatWindowLayout.orderChannels());
    }

    @Test
    public void closeRestoreAndMuteKeepIdentityAndNotifyTheStore() {
        assertTrue(ChatWindowLayout.close(ChatChannel.ADMIN));
        assertFalse(ChatWindowLayout.isOpen(ChatChannel.ADMIN));
        assertNull(ChatWindowLayout.windowOf(ChatChannel.ADMIN));
        assertEquals(Collections.singletonList(ChatChannel.ADMIN),
                ChatWindowLayout.closedChannels());
        assertEquals(1, this.changes);
        // Closing again is a no-op; restoring lands in the first window.
        assertFalse(ChatWindowLayout.close(ChatChannel.ADMIN));
        assertTrue(ChatWindowLayout.restore(ChatChannel.ADMIN));
        List<ChatChannel> tabs = ChatWindowLayout.firstWindow().getChannels();
        assertEquals(ChatChannel.ADMIN, tabs.get(tabs.size() - 1));
        assertEquals(ChatChannel.ADMIN,
                ChatWindowLayout.firstWindow().getActiveChannel());
        assertFalse(ChatWindowLayout.restore(ChatChannel.ADMIN));
        assertEquals(2, this.changes);

        ChatWindowLayout.setMuted(ChatChannel.OOC, true);
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.OOC));
        assertTrue(ChatWindowLayout.isOpen(ChatChannel.OOC));
        ChatWindowLayout.setMuted(ChatChannel.OOC, false);
        assertFalse(ChatWindowLayout.isMuted(ChatChannel.OOC));
        assertEquals(4, this.changes);
    }

    /**
     * Open, muted and closed are three separate things: closing a tab
     * neither mutes nor unmutes it, and the setting is still there when
     * the channel comes back.
     */
    @Test
    public void closingNeverTouchesMuteAndMuteSurvivesRestore() {
        ChatWindowLayout.setMuted(ChatChannel.PARTY, true);
        assertTrue(ChatWindowLayout.close(ChatChannel.PARTY));
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.PARTY));
        assertFalse(ChatWindowLayout.isOpen(ChatChannel.PARTY));
        assertTrue(ChatWindowLayout.restore(ChatChannel.PARTY));
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.PARTY));
        assertTrue(ChatWindowLayout.close(ChatChannel.OOC));
        assertFalse(ChatWindowLayout.isMuted(ChatChannel.OOC));
        assertTrue(ChatWindowLayout.restore(ChatChannel.OOC));
        assertFalse(ChatWindowLayout.isMuted(ChatChannel.OOC));
        // Muting a closed channel is allowed and is kept for its return.
        assertTrue(ChatWindowLayout.close(ChatChannel.OOC));
        ChatWindowLayout.setMuted(ChatChannel.OOC, true);
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.OOC));
        assertTrue(ChatWindowLayout.restore(ChatChannel.OOC));
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.OOC));
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
        assertTrue(ChatWindowLayout.isInFeed(party));
        assertTrue(ChatWindowLayout.isPingAudible(party));
        ChatWindowLayout.setMuted(party, true);
        assertFalse(ChatWindowLayout.isInFeed(party));
        assertTrue(ChatWindowLayout.isPingAudible(party));
        assertFalse(ChatWindowLayout.isPingsMuted(party));
        ChatWindowLayout.setPingsMuted(ooc, true);
        assertTrue(ChatWindowLayout.isInFeed(ooc));
        assertFalse(ChatWindowLayout.isPingAudible(ooc));
        assertFalse(ChatWindowLayout.isMuted(ooc));
        assertEquals(2, this.changes);
        // Setting what is already set is not a change.
        ChatWindowLayout.setMuted(party, true);
        assertEquals(2, this.changes);
        assertTrue(ChatWindowLayout.close(ChatChannel.PARTY));
        assertTrue(ChatWindowLayout.isMuted(party));
        assertEquals(Collections.singletonList(party),
                ChatWindowLayout.mutedTabs());
        assertEquals(Collections.singletonList(ooc),
                ChatWindowLayout.pingsMutedTabs());
        // Conversations drop their preferences with their tabs.
        ChatTab whisper = ChatWindowLayout.openWhisper("Bilbo", null);
        ChatWindowLayout.setMuted(whisper, true);
        ChatWindowLayout.setPingsMuted(whisper, true);
        assertTrue(ChatWindowLayout.mutedTabs().size() == 1);
        ChatWindowLayout.closeConversations();
        assertFalse(ChatWindowLayout.isMuted(whisper));
        assertFalse(ChatWindowLayout.isPingsMuted(whisper));
        assertTrue(ChatWindowLayout.isMuted(party));
    }

    /**
     * Hidden is its own concept: it neither mutes nor closes, it holds
     * while the tab is open, survives closing and restoring, and a
     * conversation drops it with its tab like every other preference.
     */
    @Test
    public void hiddenIsIndependentOfMuteAndSurvivesRestore() {
        ChatTab party = ChatTab.of(ChatChannel.PARTY);
        assertFalse(ChatWindowLayout.isHidden(party));
        ChatWindowLayout.setHidden(party, true);
        assertTrue(ChatWindowLayout.isHidden(party));
        assertTrue(ChatWindowLayout.isOpen(party));
        assertFalse(ChatWindowLayout.isMuted(party));
        assertTrue(ChatWindowLayout.isInFeed(party));
        assertTrue(ChatWindowLayout.isPingAudible(party));
        assertEquals(1, this.changes);
        // Setting what is already set is not a change.
        ChatWindowLayout.setHidden(party, true);
        assertEquals(1, this.changes);
        assertTrue(ChatWindowLayout.close(ChatChannel.PARTY));
        assertTrue(ChatWindowLayout.isHidden(party));
        assertEquals(Collections.singletonList(party),
                ChatWindowLayout.hiddenTabs());
        assertTrue(ChatWindowLayout.restore(ChatChannel.PARTY));
        assertTrue(ChatWindowLayout.isHidden(party));
        ChatWindowLayout.setHidden(party, false);
        assertFalse(ChatWindowLayout.isHidden(party));
        // Conversations drop the preference with their tabs.
        ChatTab whisper = ChatWindowLayout.openWhisper("Bilbo", null);
        ChatWindowLayout.setHidden(whisper, true);
        assertTrue(ChatWindowLayout.isHidden(whisper));
        assertTrue(ChatWindowLayout.hiddenTabs().isEmpty());
        ChatWindowLayout.closeConversations();
        assertFalse(ChatWindowLayout.isHidden(whisper));
    }

    /**
     * The window in use is drawn last and hit first. Raising is session
     * state: it is not a layout change and does not survive a reset.
     */
    @Test
    public void raisingAWindowBringsItToTheFrontOfTheStack() {
        ChatWindow w1 = ChatWindowLayout.window("w1");
        ChatWindow w2 = ChatWindowLayout.window("w2");
        assertEquals(Arrays.asList(w1, w2), ChatWindowLayout.stacked());
        ChatWindowLayout.raise("w1");
        assertEquals(Arrays.asList(w2, w1), ChatWindowLayout.stacked());
        ChatWindowLayout.raise("w2");
        assertEquals(Arrays.asList(w1, w2), ChatWindowLayout.stacked());
        ChatWindowLayout.raise("nope");
        assertEquals(Arrays.asList(w1, w2), ChatWindowLayout.stacked());
        assertEquals(0, this.changes);
        // A new window starts at the back; a window that goes leaves the
        // stack with it.
        ChatWindow w3 = ChatWindowLayout.detach(ChatChannel.PARTY, 50.0D,
                50.0D);
        assertNotNull(w3);
        assertEquals(Arrays.asList(w3, w1, w2), ChatWindowLayout.stacked());
        ChatWindowLayout.raise(w3.getId());
        assertEquals(Arrays.asList(w1, w2, w3), ChatWindowLayout.stacked());
        assertTrue(ChatWindowLayout.moveTab(ChatChannel.PARTY, "w2", 0));
        assertEquals(Arrays.asList(w1, w2), ChatWindowLayout.stacked());
        ChatWindowLayout.reset();
        assertEquals(ChatWindowLayout.windows(), ChatWindowLayout.stacked());
    }

    /** Every tab closes, down to no tab and no window at all. */
    @Test
    public void everyTabClosesAndTheEmptyLayoutIsValid() {
        assertEquals(8, ChatWindowLayout.openTabCount());
        List<ChatChannel> order = new ArrayList<ChatChannel>(
                ChatWindowLayout.orderChannels());
        for (int index = 0; index < order.size(); index++) {
            assertTrue(ChatWindowLayout.isClosable(order.get(index)));
            assertTrue(ChatWindowLayout.close(order.get(index)));
        }
        assertEquals(0, ChatWindowLayout.openTabCount());
        assertTrue(ChatWindowLayout.isEmpty());
        assertTrue(ChatWindowLayout.windows().isEmpty());
        assertNull(ChatWindowLayout.firstWindow());
        assertEquals(order.size(),
                ChatWindowLayout.closedChannels().size());
        // A closed tab is not closable, and a message finds no window to
        // open itself in: the channel keeps receiving, closed.
        assertFalse(ChatWindowLayout.isClosable(order.get(0)));
        assertFalse(ChatWindowLayout.close(order.get(0)));
        assertFalse(ChatWindowLayout.restore(order.get(0)));
        assertNull(ChatWindowLayout.openTab(ChatTab.whisper("Someone"),
                null));
        assertTrue(ChatWindowLayout.isEmpty());
        // The + opens one back into a window of its own.
        assertNotNull(ChatWindowLayout.openInNewWindow(
                ChatTab.of(order.get(0))));
        assertEquals(1, ChatWindowLayout.windows().size());
        assertEquals(Collections.singletonList(order.get(0)),
                ChatWindowLayout.orderChannels());
        assertEquals(order.get(0),
                ChatWindowLayout.firstWindow().getActiveChannel());
        // Repeated closing and reopening leaves a consistent layout.
        for (int round = 0; round < 5; round++) {
            assertTrue(ChatWindowLayout.close(order.get(0)));
            assertTrue(ChatWindowLayout.isEmpty());
            assertNotNull(ChatWindowLayout.openInNewWindow(
                    ChatTab.of(order.get(0))));
        }
        assertEquals(1, ChatWindowLayout.openTabCount());
    }

    /** A whole window closes at once, and its channels survive it. */
    @Test
    public void closingAWindowKeepsItsChannels() {
        assertFalse(ChatWindowLayout.closeWindow("nope"));
        assertTrue(ChatWindowLayout.setLocked("w1", true));
        assertFalse(ChatWindowLayout.closeWindow("w1"));
        assertTrue(ChatWindowLayout.setLocked("w1", false));
        assertTrue(ChatWindowLayout.link("w2", "w1", true));
        assertTrue(ChatWindowLayout.closeWindow("w1"));
        assertNull(ChatWindowLayout.window("w1"));
        assertEquals(1, ChatWindowLayout.windows().size());
        // The window that was stuck to it lets go.
        assertFalse(ChatWindowLayout.window("w2").isLinked());
        // Its channels are closed, not gone: still restorable, and
        // still carrying whatever preferences they had.
        assertTrue(ChatWindowLayout.closedChannels()
                .contains(ChatChannel.CONSOLE));
        assertTrue(ChatWindowLayout.restore(ChatChannel.CONSOLE, "w2"));
        assertTrue(ChatWindowLayout.closeWindow("w2"));
        assertTrue(ChatWindowLayout.isEmpty());
    }

    /** A marked group moves as one and keeps its order. */
    @Test
    public void groupsOfTabsMoveTogetherAndKeepTheirOrder() {
        List<ChatChannel> start = ChatWindowLayout.window("w2")
                .getChannels();
        // A non-contiguous pair from the conversation window, moved to
        // its front: they arrive as one run, in row order.
        List<ChatTab> group = Arrays.asList(
                ChatTab.of(start.get(3)), ChatTab.of(start.get(1)));
        assertTrue(ChatWindowLayout.moveTabs(group, "w2", 0));
        List<ChatChannel> moved = ChatWindowLayout.window("w2")
                .getChannels();
        assertEquals(start.size(), moved.size());
        assertEquals(start.get(1), moved.get(0));
        assertEquals(start.get(3), moved.get(1));
        assertEquals(start.get(0), moved.get(2));
        // Across windows: the group leaves one row and joins another.
        assertTrue(ChatWindowLayout.moveTabs(group, "w1", 0));
        assertEquals(Arrays.asList(start.get(1), start.get(3),
                ChatChannel.CONSOLE, ChatChannel.ADMIN),
                ChatWindowLayout.window("w1").getChannels());
        assertEquals(start.get(3),
                ChatWindowLayout.window("w1").getActiveChannel());
        assertFalse(ChatWindowLayout.window("w2").contains(start.get(1)));
        // A group detaches into a window of its own the same way.
        ChatWindow detached = ChatWindowLayout.detach(group, 20.0D, 30.0D);
        assertNotNull(detached);
        assertEquals(Arrays.asList(start.get(1), start.get(3)),
                detached.getChannels());
        assertEquals(Arrays.asList(ChatChannel.CONSOLE, ChatChannel.ADMIN),
                ChatWindowLayout.window("w1").getChannels());
        // Tabs from two windows are not a group, and neither is a closed
        // one: both are refused whole rather than half-applied.
        assertFalse(ChatWindowLayout.moveTabs(Arrays.asList(
                ChatTab.of(start.get(1)), ChatTab.of(ChatChannel.CONSOLE)),
                "w1", 0));
        assertEquals(Arrays.asList(start.get(1), start.get(3)),
                detached.getChannels());
    }

    /**
     * A tab taken out of a window is read in a window the same shape:
     * the height and width the player gave the one it came from, not
     * whatever the game's own chat settings say.
     */
    @Test
    public void aDetachedWindowKeepsTheSizeOfTheOneItCameFrom() {
        assertTrue(ChatWindowLayout.setWindowLines("w1", 27.5D, false));
        assertTrue(ChatWindowLayout.setWindowWidth("w1", 240, false));
        ChatWindow source = ChatWindowLayout.window("w1");
        ChatWindow detached = ChatWindowLayout.detach(ChatChannel.CONSOLE,
                20.0D, 30.0D);
        assertNotNull(detached);
        assertTrue(detached != source);
        assertEquals(source.getMaxLines(), detached.getMaxLines(), 0.0D);
        assertEquals(source.getWidth(), detached.getWidth());
    }

    @Test
    public void closedChannelsRestoreIntoTheWindowThatAsked() {
        ChatWindowLayout.close(ChatChannel.OOC);
        assertTrue(ChatWindowLayout.restore(ChatChannel.OOC, "w2"));
        List<ChatChannel> tabs = ChatWindowLayout.window("w2").getChannels();
        assertEquals(ChatChannel.OOC, tabs.get(tabs.size() - 1));
        assertFalse(ChatWindowLayout.restore(ChatChannel.OOC, "w2"));
        ChatWindowLayout.close(ChatChannel.OOC);
        ChatWindowLayout.setLocked("w2", true);
        assertFalse(ChatWindowLayout.restore(ChatChannel.OOC, "w2"));
        assertFalse(ChatWindowLayout.restore(ChatChannel.OOC, "nope"));
        assertTrue(ChatWindowLayout.restore(ChatChannel.OOC));
        assertTrue(ChatWindowLayout.firstWindow().contains(ChatChannel.OOC));
    }

    @Test
    public void everyWindowIsEqualAndTheLastOneIsNoDifferent() {
        // Emptying the console window by docking drops it.
        assertTrue(ChatWindowLayout.moveTab(ChatChannel.CONSOLE, "w2", 0));
        assertTrue(ChatWindowLayout.moveTab(ChatChannel.ADMIN, "w2", 99));
        assertNull(ChatWindowLayout.window("w1"));
        assertEquals(1, ChatWindowLayout.windows().size());
        assertEquals(ChatChannel.CONSOLE,
                ChatWindowLayout.firstWindow().getChannels().get(0));
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (channel != ChatChannel.CONSOLE) {
                ChatWindowLayout.close(channel);
            }
        }
        assertEquals(1, ChatWindowLayout.firstWindow().getChannels().size());
        ChatChannel last = ChatWindowLayout.firstWindow().getChannels().get(0);
        // Its only tab dragged out moves the window like any other.
        assertSame(ChatWindowLayout.firstWindow(),
                ChatWindowLayout.detach(last, 10.0D, 20.0D));
        assertEquals(1, ChatWindowLayout.windows().size());
        assertEquals(10.0D, ChatWindowLayout.firstWindow().getOffsetX(), 0.0D);
        assertEquals(20.0D, ChatWindowLayout.firstWindow().getOffsetY(), 0.0D);
        assertTrue(ChatWindowLayout.setPosition(
                ChatWindowLayout.firstWindow().getId(), 1.0D, 2.0D, true));
        assertEquals(1.0D, ChatWindowLayout.firstWindow().getOffsetX(), 0.0D);
    }

    @Test
    public void reorderDetachDockAndEmptyWindowLifecycle() {
        // Reorder within the conversation window: OOC to the front.
        assertTrue(ChatWindowLayout.moveTab(ChatChannel.OOC, "w2", 0));
        assertEquals(ChatChannel.OOC,
                ChatWindowLayout.window("w2").getChannels().get(0));
        assertFalse(ChatWindowLayout.moveTab(ChatChannel.OOC, "w2", 0));

        // Detach Party into its own window.
        ChatWindow party = ChatWindowLayout.detach(ChatChannel.PARTY,
                60.0D, 120.0D);
        assertNotNull(party);
        assertEquals("w3", party.getId());
        assertEquals(Collections.singletonList(ChatChannel.PARTY),
                party.getChannels());
        assertEquals(ChatChannel.PARTY, party.getActiveChannel());
        assertEquals(60.0D, party.getOffsetX(), 0.0D);
        // Percents are clamped on the way in.
        assertEquals(100.0D, party.getOffsetY(), 0.0D);
        assertEquals(3, ChatWindowLayout.windows().size());
        assertSame(party, ChatWindowLayout.windowOf(ChatChannel.PARTY));
        assertFalse(ChatWindowLayout.window("w2").contains(ChatChannel.PARTY));
        List<ChatChannel> order = ChatWindowLayout.orderChannels();
        assertEquals(ChatChannel.PARTY, order.get(order.size() - 1));

        // Dock Faction into the party window, at the front.
        assertTrue(ChatWindowLayout.moveTab(ChatChannel.FACTION, "w3", 0));
        assertEquals(Arrays.asList(ChatChannel.FACTION, ChatChannel.PARTY),
                party.getChannels());
        assertEquals(ChatChannel.FACTION, party.getActiveChannel());

        // A window's only tab dragged out just moves the window.
        ChatWindow moved = ChatWindowLayout.detach(ChatChannel.FACTION,
                5.0D, 5.0D);
        assertNotNull(moved);
        assertEquals("w4", moved.getId());
        assertEquals(4, ChatWindowLayout.windows().size());
        assertSame(party, ChatWindowLayout.detach(ChatChannel.PARTY,
                1.0D, 2.0D));
        assertEquals(1.0D, party.getOffsetX(), 0.0D);
        assertEquals(4, ChatWindowLayout.windows().size());

        // Docking the last tab elsewhere empties the window away.
        assertTrue(ChatWindowLayout.moveTab(ChatChannel.PARTY, "w2", 99));
        assertNull(ChatWindowLayout.window("w3"));
        assertEquals(3, ChatWindowLayout.windows().size());
        List<ChatChannel> tabs = ChatWindowLayout.window("w2").getChannels();
        assertEquals(ChatChannel.PARTY, tabs.get(tabs.size() - 1));
        // Closing a window's last tab drops the window too.
        assertTrue(ChatWindowLayout.close(ChatChannel.FACTION));
        assertNull(ChatWindowLayout.window("w4"));
        assertEquals(Collections.singletonList(ChatChannel.FACTION),
                ChatWindowLayout.closedChannels());
    }

    @Test
    public void lockRefusesLayoutChangesButNotPreferences() {
        ChatWindow party = ChatWindowLayout.detach(ChatChannel.PARTY,
                0.0D, 0.0D);
        assertTrue(ChatWindowLayout.setLocked("w3", true));
        assertFalse(ChatWindowLayout.setLocked("w3", true));
        assertTrue(party.isLocked());
        // A locked window keeps the tabs it has: nothing moves out of
        // it, and nothing closes in it either.
        assertNull(ChatWindowLayout.detach(ChatChannel.PARTY, 1.0D, 1.0D));
        assertFalse(ChatWindowLayout.isClosable(ChatChannel.PARTY));
        assertFalse(ChatWindowLayout.close(ChatChannel.PARTY));
        assertFalse(ChatWindowLayout.moveTab(ChatChannel.OOC, "w3", 0));
        assertFalse(ChatWindowLayout.moveTab(ChatChannel.PARTY, "w2", 0));
        // Position and mute are not layout movement.
        assertTrue(ChatWindowLayout.setPosition("w3", 30.0D, 40.0D, false));
        assertEquals(30.0D, party.getOffsetX(), 0.0D);
        ChatWindowLayout.setMuted(ChatChannel.PARTY, true);
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.PARTY));
        ChatWindowLayout.setLocked("w2", true);
        assertFalse(ChatWindowLayout.moveTab(ChatChannel.OOC, "w2", 0));
        assertFalse(ChatWindowLayout.close(ChatChannel.OOC));
        // Unlocking gives the row its cross back.
        assertTrue(ChatWindowLayout.setLocked("w2", false));
        assertTrue(ChatWindowLayout.close(ChatChannel.OOC));
        assertFalse(ChatWindowLayout.setPosition("nope", 1.0D, 1.0D, true));
    }

    /**
     * A conversation that opens by itself when every window is locked
     * gets a window of its own, and that window is put where it covers
     * none of them.
     */
    @Test
    public void aWindowOpenedForAConversationKeepsClearOfTheOthers() {
        for (ChatWindow window : ChatWindowLayout.windows()) {
            ChatWindowLayout.setLocked(window.getId(), true);
        }
        int before = ChatWindowLayout.windows().size();
        ChatTab whisper = ChatWindowLayout.openWhisper("Bilbo", null);
        assertNotNull(whisper);
        ChatWindow opened = ChatWindowLayout.windowOf(whisper);
        assertNotNull(opened);
        // A locked window keeps the tabs it has; this one is new.
        assertEquals(before + 1, ChatWindowLayout.windows().size());
        for (ChatWindow other : ChatWindowLayout.windows()) {
            if (other == opened) {
                continue;
            }
            double dx = opened.getOffsetX() - other.getOffsetX();
            double dy = opened.getOffsetY() - other.getOffsetY();
            assertTrue("the new window sits on top of " + other.getId(),
                    Math.sqrt(dx * dx + dy * dy) >= 20.0D);
        }
    }

    @Test
    public void linksFollowTheirTargetAndNeverHoldEachOther() {
        assertTrue(ChatWindowLayout.link("w1", "w2", true));
        assertTrue(ChatWindowLayout.window("w1").isLinked());
        assertTrue(ChatWindowLayout.window("w1").isLinkedAbove());
        assertEquals("w2", ChatWindowLayout.window("w1").getLinkTarget());
        assertEquals(1, ChatWindowLayout.linkedTo("w2").size());
        assertFalse(ChatWindowLayout.link("w1", "w1", true));
        assertFalse(ChatWindowLayout.link("w1", "nope", true));
        // Linking the target back lets the first link go.
        assertTrue(ChatWindowLayout.link("w2", "w1", false));
        assertFalse(ChatWindowLayout.window("w1").isLinked());
        assertTrue(ChatWindowLayout.window("w2").isLinked());
        assertFalse(ChatWindowLayout.window("w2").isLinkedAbove());
        assertTrue(ChatWindowLayout.unlink("w2"));
        assertFalse(ChatWindowLayout.unlink("w2"));
        // A link dies with its target window.
        ChatWindowLayout.detach(ChatChannel.PARTY, 0.0D, 0.0D);
        assertTrue(ChatWindowLayout.link("w3", "w1", true));
        ChatWindowLayout.moveTab(ChatChannel.CONSOLE, "w2", 0);
        ChatWindowLayout.moveTab(ChatChannel.ADMIN, "w2", 0);
        assertNull(ChatWindowLayout.window("w1"));
        assertFalse(ChatWindowLayout.window("w3").isLinked());
        // Loading validates links: a missing target, self, and cycles.
        List<ChatWindowLayout.WindowSpec> specs =
                new ArrayList<ChatWindowLayout.WindowSpec>();
        specs.add(new ChatWindowLayout.WindowSpec("w1",
                Arrays.asList(ChatChannel.ALL), null, false, 0.0D, 0.0D,
                "w2", true));
        specs.add(new ChatWindowLayout.WindowSpec("w2",
                Arrays.asList(ChatChannel.OOC), null, false, 0.0D, 0.0D,
                "w1", false));
        specs.add(new ChatWindowLayout.WindowSpec("w3",
                Arrays.asList(ChatChannel.PARTY), null, false, 0.0D, 0.0D,
                "w9", false));
        specs.add(new ChatWindowLayout.WindowSpec("w4",
                Arrays.asList(ChatChannel.CONSOLE), null, false, 0.0D, 0.0D,
                "w4", true));
        ChatWindowLayout.load(specs, null, null, 0.0D, 100.0D);
        assertTrue(ChatWindowLayout.window("w1").isLinked());
        assertFalse(ChatWindowLayout.window("w2").isLinked());
        assertFalse(ChatWindowLayout.window("w3").isLinked());
        assertFalse(ChatWindowLayout.window("w4").isLinked());
    }

    @Test
    public void windowCountIsBounded() {
        List<ChatChannel> order = ChatChannel.presentationOrder();
        int detached = 0;
        for (ChatChannel channel : order) {
            ChatWindow source = ChatWindowLayout.windowOf(channel);
            if (source.getChannels().size() > 1
                    && ChatWindowLayout.detach(channel, 0.0D, 0.0D) != null) {
                detached++;
            }
        }
        // Every window that could be split was, once: six new windows,
        // one per channel, which with eight channels meets the cap exactly.
        assertEquals(6, detached);
        assertEquals(8, ChatWindowLayout.windows().size());
        assertTrue(ChatWindowLayout.windows().size()
                <= ChatWindowLayout.MAX_WINDOWS);
        for (ChatWindow window : ChatWindowLayout.windows()) {
            assertEquals(1, window.getChannels().size());
        }
    }

    @Test
    public void loadRepairsStaleDuplicateAndMissingEntries() {
        List<ChatWindowLayout.WindowSpec> specs =
                new ArrayList<ChatWindowLayout.WindowSpec>();
        // Party listed twice, a legacy main window, an unknown window id,
        // an empty window, percents out of range, Console placed nowhere.
        specs.add(new ChatWindowLayout.WindowSpec("w3",
                Arrays.asList(ChatChannel.PARTY, ChatChannel.OOC),
                ChatChannel.OOC, true, 250.0D, -5.0D));
        specs.add(new ChatWindowLayout.WindowSpec("main",
                Arrays.asList(ChatChannel.ALL, ChatChannel.PARTY,
                        ChatChannel.PROXIMITY),
                ChatChannel.PARTY, false, 0.0D, 100.0D));
        specs.add(new ChatWindowLayout.WindowSpec("bogus",
                Arrays.asList(ChatChannel.FACTION), null, false, 0.0D, 0.0D));
        specs.add(new ChatWindowLayout.WindowSpec("w7",
                Collections.<ChatChannel>emptyList(), null, false, 0.0D, 0.0D));
        specs.add(new ChatWindowLayout.WindowSpec("w3",
                Arrays.asList(ChatChannel.FACTION), null, false, 0.0D, 0.0D));
        ChatWindowLayout.load(specs, EnumSet.of(ChatChannel.ADMIN),
                EnumSet.of(ChatChannel.OOC, ChatChannel.ADMIN), 120.0D,
                33.0D);
        assertEquals(100.0D, ChatWindowLayout.feedOffsetX(), 0.0D);
        assertEquals(33.0D, ChatWindowLayout.feedOffsetY(), 0.0D);

        assertEquals(2, ChatWindowLayout.windows().size());
        ChatWindow w3 = ChatWindowLayout.firstWindow();
        assertEquals("w3", w3.getId());
        // Party keeps its first placement; the unplaced channels land
        // in the first window.
        assertEquals(Arrays.asList(ChatChannel.PARTY, ChatChannel.OOC,
                ChatChannel.FACTION, ChatChannel.DISCORD, ChatChannel.CONSOLE),
                w3.getChannels());
        assertEquals(ChatChannel.OOC, w3.getActiveChannel());
        assertTrue(w3.isLocked());
        assertEquals(100.0D, w3.getOffsetX(), 0.0D);
        assertEquals(0.0D, w3.getOffsetY(), 0.0D);
        // The legacy main window becomes an ordinary one, numbered on.
        ChatWindow legacy = ChatWindowLayout.windows().get(1);
        assertEquals("w8", legacy.getId());
        assertEquals(Arrays.asList(ChatChannel.ALL, ChatChannel.PROXIMITY),
                legacy.getChannels());
        assertEquals(ChatChannel.ALL, legacy.getActiveChannel());
        assertEquals(100.0D, legacy.getOffsetY(), 0.0D);
        assertEquals(Collections.singletonList(ChatChannel.ADMIN),
                ChatWindowLayout.closedChannels());
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.OOC));
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.ADMIN));
        // New windows number on from the highest id seen.
        assertEquals("w9", ChatWindowLayout.detach(ChatChannel.PROXIMITY,
                0.0D, 0.0D).getId());
        assertEquals(1, this.changes);
    }

    @Test
    public void loadWithNothingUsableFallsBackSensibly() {
        ChatWindowLayout.load(Collections.<ChatWindowLayout.WindowSpec>emptyList(),
                EnumSet.allOf(ChatChannel.class), null, 0.0D, 100.0D);
        // Everything closed is a layout of its own: the file described
        // no window, so none is opened.
        assertTrue(ChatWindowLayout.isEmpty());
        assertNull(ChatWindowLayout.firstWindow());
        assertEquals(0.0D, ChatWindowLayout.feedOffsetX(), 0.0D);
        ChatWindowLayout.load(Collections.<ChatWindowLayout.WindowSpec>emptyList(),
                EnumSet.of(ChatChannel.ADMIN), null, 0.0D, 100.0D);
        // No windows at all: one window with everything still open.
        assertEquals(1, ChatWindowLayout.windows().size());
        ChatWindow only = ChatWindowLayout.firstWindow();
        assertEquals("w1", only.getId());
        assertEquals(Arrays.asList(ChatChannel.ALL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC, ChatChannel.DISCORD, ChatChannel.PARTY,
                ChatChannel.CONSOLE), only.getChannels());
        assertEquals(ChatChannel.ALL, only.getActiveChannel());
        assertEquals(100.0D, only.getOffsetY(), 0.0D);
    }
}
