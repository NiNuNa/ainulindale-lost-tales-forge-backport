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
     * Mute is the whole preference; the feed and the cue can also be
     * switched off one at a time, independently of each other and of
     * mute, and all three survive closing and travel with the store.
     */
    @Test
    public void feedAndPingPreferencesAreIndependentHalvesOfMute() {
        ChatTab party = ChatTab.of(ChatChannel.PARTY);
        ChatTab ooc = ChatTab.of(ChatChannel.OOC);
        assertTrue(ChatWindowLayout.isInFeed(party));
        assertTrue(ChatWindowLayout.isPingAudible(party));
        ChatWindowLayout.setFeedHidden(party, true);
        assertFalse(ChatWindowLayout.isInFeed(party));
        assertTrue(ChatWindowLayout.isPingAudible(party));
        assertFalse(ChatWindowLayout.isMuted(party));
        ChatWindowLayout.setPingSilenced(ooc, true);
        assertTrue(ChatWindowLayout.isInFeed(ooc));
        assertFalse(ChatWindowLayout.isPingAudible(ooc));
        assertFalse(ChatWindowLayout.isMuted(ooc));
        ChatWindowLayout.setMuted(ooc, true);
        assertFalse(ChatWindowLayout.isInFeed(ooc));
        assertFalse(ChatWindowLayout.isPingAudible(ooc));
        ChatWindowLayout.setMuted(ooc, false);
        // Unmuting leaves the finer preference where it was.
        assertFalse(ChatWindowLayout.isPingAudible(ooc));
        assertTrue(ChatWindowLayout.isInFeed(ooc));
        assertEquals(4, this.changes);
        // Setting what is already set is not a change.
        ChatWindowLayout.setFeedHidden(party, true);
        assertEquals(4, this.changes);
        assertTrue(ChatWindowLayout.close(ChatChannel.PARTY));
        assertTrue(ChatWindowLayout.isFeedHidden(party));
        assertEquals(Collections.singletonList(party),
                ChatWindowLayout.feedHiddenTabs());
        assertEquals(Collections.singletonList(ooc),
                ChatWindowLayout.pingSilencedTabs());
        // Conversations drop their preferences with their tabs.
        ChatTab whisper = ChatWindowLayout.openWhisper("Bilbo", null);
        ChatWindowLayout.setFeedHidden(whisper, true);
        ChatWindowLayout.setPingSilenced(whisper, true);
        assertTrue(ChatWindowLayout.feedHiddenTabs().size() == 1);
        ChatWindowLayout.closeConversations();
        assertFalse(ChatWindowLayout.isFeedHidden(whisper));
        assertFalse(ChatWindowLayout.isPingSilenced(whisper));
        assertTrue(ChatWindowLayout.isFeedHidden(party));
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

    /** There is always at least one open tab, however the layout is cut. */
    @Test
    public void theLastOpenTabOfAllIsNeverClosable() {
        assertEquals(8, ChatWindowLayout.openTabCount());
        List<ChatChannel> order = new ArrayList<ChatChannel>(
                ChatWindowLayout.orderChannels());
        for (int index = 0; index < order.size() - 1; index++) {
            assertTrue(ChatWindowLayout.isClosable(order.get(index)));
            assertTrue(ChatWindowLayout.close(order.get(index)));
            assertTrue(ChatWindowLayout.openTabCount() >= 1);
        }
        ChatChannel last = order.get(order.size() - 1);
        assertEquals(1, ChatWindowLayout.openTabCount());
        assertEquals(1, ChatWindowLayout.windows().size());
        assertFalse(ChatWindowLayout.isClosable(last));
        assertFalse(ChatWindowLayout.close(last));
        assertTrue(ChatWindowLayout.isOpen(last));
        // A closed tab is not closable either; a second open one makes
        // both closable again.
        assertFalse(ChatWindowLayout.isClosable(order.get(0)));
        assertTrue(ChatWindowLayout.restore(order.get(0)));
        assertTrue(ChatWindowLayout.isClosable(last));
        assertTrue(ChatWindowLayout.isClosable(order.get(0)));
        // Repeated closing and reopening leaves a consistent layout.
        for (int round = 0; round < 5; round++) {
            assertTrue(ChatWindowLayout.close(order.get(0)));
            assertFalse(ChatWindowLayout.close(last));
            assertTrue(ChatWindowLayout.restore(order.get(0)));
        }
        assertEquals(2, ChatWindowLayout.openTabCount());
        assertEquals(Arrays.asList(last, order.get(0)),
                ChatWindowLayout.orderChannels());
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
    public void everyWindowIsEqualAndOnlyTheLastOneKeepsItsLastTab() {
        // Emptying the console window by docking drops it.
        assertTrue(ChatWindowLayout.moveTab(ChatChannel.CONSOLE, "w2", 0));
        assertTrue(ChatWindowLayout.moveTab(ChatChannel.ADMIN, "w2", 99));
        assertNull(ChatWindowLayout.window("w1"));
        assertEquals(1, ChatWindowLayout.windows().size());
        assertEquals(ChatChannel.CONSOLE,
                ChatWindowLayout.firstWindow().getChannels().get(0));
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            ChatWindowLayout.close(channel);
        }
        assertEquals(1, ChatWindowLayout.firstWindow().getChannels().size());
        ChatChannel last = ChatWindowLayout.firstWindow().getChannels().get(0);
        assertFalse(ChatWindowLayout.close(last));
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
        // The lock guards movement; closing is still allowed.
        assertNull(ChatWindowLayout.detach(ChatChannel.PARTY, 1.0D, 1.0D));
        assertFalse(ChatWindowLayout.moveTab(ChatChannel.OOC, "w3", 0));
        assertFalse(ChatWindowLayout.moveTab(ChatChannel.PARTY, "w2", 0));
        // Position and mute are not layout movement.
        assertTrue(ChatWindowLayout.setPosition("w3", 30.0D, 40.0D, false));
        assertEquals(30.0D, party.getOffsetX(), 0.0D);
        ChatWindowLayout.setMuted(ChatChannel.PARTY, true);
        assertTrue(ChatWindowLayout.isMuted(ChatChannel.PARTY));
        ChatWindowLayout.setLocked("w2", true);
        assertFalse(ChatWindowLayout.moveTab(ChatChannel.OOC, "w2", 0));
        assertTrue(ChatWindowLayout.close(ChatChannel.OOC));
        assertFalse(ChatWindowLayout.setPosition("nope", 1.0D, 1.0D, true));
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
        // Everything closed is no layout: the default comes back.
        assertEquals(2, ChatWindowLayout.windows().size());
        assertEquals(ChatChannel.CONSOLE,
                ChatWindowLayout.firstWindow().getActiveChannel());
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
