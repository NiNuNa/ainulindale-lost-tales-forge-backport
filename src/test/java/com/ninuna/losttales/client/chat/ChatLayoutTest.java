package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.Tearing;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowLayoutStore;
import com.ninuna.losttales.client.window.WindowPlacement;
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
        ConversationPage steve = ConversationPage.whisper("Steve", "Aldric");
        ConversationPage bob = ConversationPage.whisper("Bob", "Bob");
        assertNotNull(WindowLayout.openTab(steve, "w2"));
        assertNotNull(WindowLayout.openTab(bob, "w2"));
        assertTrue(ChatLayout.close(bob));
        assertFalse("closed by hand: a replay may not reopen it",
                ChatLayout.opensByItself(bob));
        ChatLayout.closeConversations();
        assertFalse(WindowLayout.isOpen(steve));
        assertTrue(ChatLayout.opensByItself(bob));
        ChatLayout.restoreConversations("server:b");
        assertFalse("another place has no such tab", WindowLayout.isOpen(steve));
        ChatLayout.closeConversations();
        ChatLayout.restoreConversations("server:a");
        assertTrue(WindowLayout.isOpen(steve));
        assertEquals("w2", WindowLayout.windowOf(steve).getId());
        assertFalse(WindowLayout.isOpen(bob));
        assertFalse(ChatLayout.opensByItself(bob));
        assertNotNull(ChatLayout.reopenConversation(bob, "w2"));
        assertTrue(WindowLayout.isOpen(bob));
        assertTrue(ChatLayout.opensByItself(bob));
    }

    /**
     * A new player starts with one window of Global and OOC, Global in
     * front, in the middle of the screen at two thirds of it. The conversations left
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
        assertEquals(Window.ScreenFill.NONE, window.getFill());
        assertTrue("in the middle, at two thirds",
                WindowPlacement.atDefaultPlace(window));
        assertTrue(window.isLocked());
        assertEquals(Arrays.asList(ChatChannel.PROXIMITY, ChatChannel.FACTION,
                ChatChannel.FELLOWSHIP, ChatChannel.OPERATOR,
                ChatChannel.CLIENT_CONSOLE, ChatChannel.SERVER_CONSOLE),
                ChatLayout.closedChannels());
        assertTrue(ChatLayout.opensByItself(ConversationPage.of(ChatChannel.PROXIMITY)));
        assertTrue(ChatLayout.opensByItself(ConversationPage.of(ChatChannel.FACTION)));
        assertTrue(ChatLayout.opensByItself(ConversationPage.of(ChatChannel.FELLOWSHIP)));
        assertFalse(ChatLayout.opensByItself(ConversationPage.of(ChatChannel.OPERATOR)));
        assertFalse(ChatLayout.opensByItself(ConversationPage.of(ChatChannel.CLIENT_CONSOLE)));
        assertFalse(ChatLayout.opensByItself(ConversationPage.of(ChatChannel.SERVER_CONSOLE)));
        // The chat feed starts at its default place, over the hotbar,
        // and no line in the file says where it stands.
        assertFalse(ChatLayout.isFeedPlaced());
        for (String line : WindowLayoutStore.describe()) {
            assertFalse(line, line.startsWith("feed "));
        }

        List<String> written = WindowLayoutStore.describe();
        WindowLayoutStore.load(written);
        assertEquals(1, WindowLayout.windows().size());
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.OOC),
                ChatLayoutViews.channelsOf(WindowLayout.firstWindow()));
        assertTrue(WindowPlacement.atDefaultPlace(WindowLayout.firstWindow()));
        assertTrue(WindowLayout.firstWindow().isLocked());
        assertFalse(ChatLayout.opensByItself(ConversationPage.of(ChatChannel.SERVER_CONSOLE)));
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
                ChatChannel.FELLOWSHIP), ChatLayoutViews.channelsOf(conversation));
        assertEquals(ChatChannel.GLOBAL, ChatLayoutViews.frontChannelOf(conversation));
        assertTrue(ChatLayout.closedChannels().isEmpty());
        ChatLayout.setFeedPosition(40.0D, -3.0D, true);
        assertEquals(40.0D, ChatLayout.feedOffsetX(), 0.0D);
        assertEquals(0.0D, ChatLayout.feedOffsetY(), 0.0D);
    }

    @Test
    public void closeRestoreAndMuteKeepIdentityAndNotifyTheStore() {
        assertTrue(ChatLayout.close(ConversationPage.of(ChatChannel.OPERATOR)));
        assertFalse(ChatLayout.isOpen(ChatChannel.OPERATOR));
        assertNull(WindowLayout.windowOf(ConversationPage.of(ChatChannel.OPERATOR)));
        assertEquals(Collections.singletonList(ChatChannel.OPERATOR),
                ChatLayout.closedChannels());
        assertEquals(1, this.changes);
        // Closing again is a no-op; restoring lands in a window of the
        // channels, never among the consoles.
        assertFalse(ChatLayout.close(ConversationPage.of(ChatChannel.OPERATOR)));
        assertTrue(ChatLayoutViews.reopen(ChatChannel.OPERATOR));
        Window channels = WindowLayout.windows().get(1);
        List<ChatChannel> tabs = ChatLayoutViews.channelsOf(channels);
        assertEquals(ChatChannel.OPERATOR, tabs.get(tabs.size() - 1));
        assertEquals("the front tab is left alone", ChatChannel.GLOBAL,
                ChatLayoutViews.frontChannelOf(channels));
        assertFalse(ChatLayoutViews.reopen(ChatChannel.OPERATOR));
        assertEquals(2, this.changes);

        ChatLayout.setNotification(ConversationPage.of(ChatChannel.OOC), ChatLineChoice.NOTHING);
        assertTrue(ChatLayout.isMuted(ConversationPage.of(ChatChannel.OOC)));
        assertTrue(ChatLayout.isOpen(ChatChannel.OOC));
        ChatLayout.setNotification(ConversationPage.of(ChatChannel.OOC),
                ChatLineChoice.EVERYTHING);
        assertFalse(ChatLayout.isMuted(ConversationPage.of(ChatChannel.OOC)));
        assertEquals(4, this.changes);
    }

    /**
     * Open, the notification choice and closed are separate things:
     * closing a tab leaves its choice alone, and the choice is still
     * there when the channel comes back.
     */
    @Test
    public void closingNeverTouchesTheChoiceAndItSurvivesRestore() {
        ChatLayout.setNotification(ConversationPage.of(ChatChannel.FELLOWSHIP), ChatLineChoice.NOTHING);
        assertTrue(ChatLayout.close(ConversationPage.of(ChatChannel.FELLOWSHIP)));
        assertEquals(ChatLineChoice.NOTHING,
                ChatLayout.notification(ConversationPage.of(ChatChannel.FELLOWSHIP)));
        assertFalse(ChatLayout.isOpen(ChatChannel.FELLOWSHIP));
        assertTrue(ChatLayoutViews.reopen(ChatChannel.FELLOWSHIP));
        assertEquals(ChatLineChoice.NOTHING,
                ChatLayout.notification(ConversationPage.of(ChatChannel.FELLOWSHIP)));
        assertTrue(ChatLayout.close(ConversationPage.of(ChatChannel.OOC)));
        assertEquals(ChatLineChoice.ONLY_MENTIONS,
                ChatLayout.notification(ConversationPage.of(ChatChannel.OOC)));
        assertTrue(ChatLayoutViews.reopen(ChatChannel.OOC));
        assertEquals(ChatLineChoice.ONLY_MENTIONS,
                ChatLayout.notification(ConversationPage.of(ChatChannel.OOC)));
        // Choosing for a closed channel is allowed and kept for its return.
        assertTrue(ChatLayout.close(ConversationPage.of(ChatChannel.OOC)));
        ChatLayout.setNotification(ConversationPage.of(ChatChannel.OOC),
                ChatLineChoice.EVERYTHING);
        ChatLayout.setFeedChoice(ConversationPage.of(ChatChannel.OOC),
                ChatLineChoice.NOTHING);
        assertTrue(ChatLayoutViews.reopen(ChatChannel.OOC));
        assertEquals(ChatLineChoice.EVERYTHING,
                ChatLayout.notification(ConversationPage.of(ChatChannel.OOC)));
        assertEquals(ChatLineChoice.NOTHING,
                ChatLayout.feedChoice(ConversationPage.of(ChatChannel.OOC)));
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
        double consoleX = console.getOffsetX();
        double consoleY = console.getOffsetY();
        double conversationX = conversation.getOffsetX();
        double conversationY = conversation.getOffsetY();
        this.changes = 0;

        ChatLayout.setFeedPosition(35.0D, 60.0D, false);
        assertEquals(0, this.changes);
        ChatLayout.setFeedPosition(40.0D, 55.0D, true);
        assertEquals(1, this.changes);

        assertEquals(40.0D, ChatLayout.feedOffsetX(), 0.0D);
        assertEquals(55.0D, ChatLayout.feedOffsetY(), 0.0D);
        assertEquals(2, WindowLayout.windows().size());
        assertEquals(consoleX, console.getOffsetX(), 0.0D);
        assertEquals(consoleY, console.getOffsetY(), 0.0D);
        assertEquals(conversationX, conversation.getOffsetX(), 0.0D);
        assertEquals(conversationY, conversation.getOffsetY(), 0.0D);
    }

    /**
     * Every conversation starts with the Notifications of its kind: a
     * whisper, with a player or an NPC, chimes for every line, and every
     * channel, the consoles and Operator included, only for a line
     * addressed to the player. Every one starts showing all its
     * lines in the feed. A line's own tab asks as its row entry does.
     */
    @Test
    public void eachKindOfConversationStartsWithItsOwnNotifications() {
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            ConversationPage tab = ConversationPage.of(channel);
            assertEquals(channel.getId(), ChatLineChoice.ONLY_MENTIONS,
                    ChatLayout.notification(tab));
            assertEquals(channel.getId(), ChatLineChoice.EVERYTHING,
                    ChatLayout.feedChoice(tab));
        }
        ConversationPage whisper = ConversationPage.whisper("Bilbo", "");
        ConversationPage asCharacter = ConversationPage.whisper("Bilbo", "", "aldric");
        ConversationPage npc = ConversationPage.npc("Grey Wanderer");
        assertEquals(ChatLineChoice.EVERYTHING, ChatLayout.notification(whisper));
        assertEquals(ChatLineChoice.EVERYTHING,
                ChatLayout.notification(asCharacter));
        assertEquals(ChatLineChoice.EVERYTHING, ChatLayout.notification(npc));
        assertEquals(ChatLineChoice.EVERYTHING, ChatLayout.feedChoice(whisper));
        assertEquals(ChatLineChoice.EVERYTHING, ChatLayout.feedChoice(npc));
        assertTrue(ChatLayout.chimes(whisper, false));
        assertTrue(ChatLayout.chimes(npc, false));
        assertTrue(ChatLayout.chimes(npc, true));
        assertFalse(ChatLayout.chimes(ConversationPage.of(ChatChannel.GLOBAL), false));
        assertTrue(ChatLayout.chimes(ConversationPage.of(ChatChannel.GLOBAL), true));
        assertTrue(ChatLayout.notificationTabs().isEmpty());
        assertTrue(ChatLayout.feedChoiceTabs().isEmpty());
    }

    /**
     * Notifications decides which new lines chime and nothing else:
     * Everything every line, Only Mentions a line addressed to the
     * player, Nothing none, which reads as muted. Setting what is already
     * set is no change; a whisper keeps its choice past the session, an
     * NPC conversation's goes with it.
     */
    @Test
    public void notificationsDecideWhichLinesChime() {
        ConversationPage fellowship = ConversationPage.of(ChatChannel.FELLOWSHIP);
        ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);
        ChatLayout.setNotification(fellowship, ChatLineChoice.NOTHING);
        assertTrue(ChatLayout.isMuted(fellowship));
        assertFalse(ChatLayout.chimes(fellowship, true));
        assertEquals(ChatLineChoice.EVERYTHING, ChatLayout.feedChoice(fellowship));
        ChatLayout.setNotification(ooc, ChatLineChoice.EVERYTHING);
        assertFalse(ChatLayout.isMuted(ooc));
        assertTrue(ChatLayout.chimes(ooc, true));
        assertTrue(ChatLayout.chimes(ooc, false));
        assertEquals(2, this.changes);
        // Setting what is already set is not a change.
        ChatLayout.setNotification(fellowship, ChatLineChoice.NOTHING);
        assertEquals(2, this.changes);
        assertTrue(ChatLayout.close(ConversationPage.of(ChatChannel.FELLOWSHIP)));
        assertEquals(ChatLineChoice.NOTHING, ChatLayout.notification(fellowship));
        assertEquals(Arrays.asList(fellowship, ooc), ChatLayout.notificationTabs());
        // Picking the default again is no choice of the conversation's own.
        ChatLayout.setNotification(ooc, ChatLineChoice.ONLY_MENTIONS);
        assertEquals(Collections.singletonList(fellowship),
                ChatLayout.notificationTabs());
        ChatLayout.setNotification(ooc, ChatLineChoice.EVERYTHING);
        ConversationPage whisper = ChatLayout.openWhisper("Bilbo", "", null);
        ChatLayout.setNotification(whisper, ChatLineChoice.ONLY_MENTIONS);
        assertFalse(ChatLayout.chimes(whisper, false));
        assertTrue(ChatLayout.chimes(whisper, true));
        ConversationPage wanderer = ChatLayout.openTab(ConversationPage.npc("Grey Wanderer"),
                "w2");
        ChatLayout.setNotification(wanderer, ChatLineChoice.NOTHING);
        ChatLayout.setFeedChoice(wanderer, ChatLineChoice.NOTHING);
        assertTrue(ChatLayout.isMuted(wanderer));
        assertFalse(ChatLayout.notificationTabs().contains(wanderer));
        assertFalse(ChatLayout.feedChoiceTabs().contains(wanderer));
        ChatLayout.closeConversations();
        assertEquals(ChatLineChoice.ONLY_MENTIONS,
                ChatLayout.notification(whisper));
        assertEquals(ChatLineChoice.EVERYTHING,
                ChatLayout.notification(wanderer));
        assertEquals(ChatLineChoice.EVERYTHING, ChatLayout.feedChoice(wanderer));
        assertEquals(ChatLineChoice.NOTHING, ChatLayout.notification(fellowship));
    }

    /**
     * The feed choice is a choice of its own: it never mutes the
     * conversation nor changes what chimes, and Notifications never
     * changes what reaches the feed.
     */
    @Test
    public void showInFeedIsItsOwnChoice() {
        ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);
        ChatLayout.setFeedChoice(ooc, ChatLineChoice.NOTHING);
        assertEquals(ChatLineChoice.NOTHING, ChatLayout.feedChoice(ooc));
        assertFalse(ChatLayout.isMuted(ooc));
        assertEquals(ChatLineChoice.ONLY_MENTIONS, ChatLayout.notification(ooc));
        assertTrue(ChatLayout.chimes(ooc, true));
        ChatLayout.setNotification(ooc, ChatLineChoice.NOTHING);
        assertEquals(ChatLineChoice.NOTHING, ChatLayout.feedChoice(ooc));
        assertEquals(2, this.changes);
        ChatLayout.setFeedChoice(ooc, ChatLineChoice.NOTHING);
        assertEquals(2, this.changes);
        ChatLayout.setFeedChoice(ooc, ChatLineChoice.EVERYTHING);
        assertTrue(ChatLayout.feedChoiceTabs().isEmpty());
        ChatLayout.setFeedChoice(ooc, ChatLineChoice.ONLY_MENTIONS);
        assertEquals(Collections.singletonList(ooc), ChatLayout.feedChoiceTabs());
        assertEquals(ChatLineChoice.NOTHING, ChatLayout.notification(ooc));
    }

    /**
     * The layout file names a conversation's choice only where it is not
     * the conversation's default: a whisper at Only Mentions and a
     * channel at Everything are written, a channel at Only Mentions and a
     * whisper at Everything are not; the feed choice only where it is not
     * Everything. An NPC conversation's choices are never written, and
     * a file naming one reads as nothing.
     */
    @Test
    public void onlyChoicesAwayFromTheDefaultAreWritten() {
        ConversationPage alex = ChatLayout.openWhisper("Alex", "", "w2");
        ConversationPage bob = ChatLayout.openWhisper("Bob", "", "w2");
        ConversationPage wanderer = ChatLayout.openTab(ConversationPage.npc("Grey Wanderer"),
                "w2");
        ChatLayout.setNotification(alex, ChatLineChoice.ONLY_MENTIONS);
        ChatLayout.setNotification(bob, ChatLineChoice.EVERYTHING);
        ChatLayout.setNotification(ConversationPage.of(ChatChannel.GLOBAL),
                ChatLineChoice.EVERYTHING);
        ChatLayout.setNotification(ConversationPage.of(ChatChannel.OOC),
                ChatLineChoice.ONLY_MENTIONS);
        ChatLayout.setFeedChoice(ConversationPage.of(ChatChannel.SERVER_CONSOLE),
                ChatLineChoice.NOTHING);
        ChatLayout.setFeedChoice(alex, ChatLineChoice.ONLY_MENTIONS);
        ChatLayout.setFeedChoice(ConversationPage.of(ChatChannel.FELLOWSHIP),
                ChatLineChoice.EVERYTHING);
        ChatLayout.setNotification(wanderer, ChatLineChoice.NOTHING);
        ChatLayout.setFeedChoice(wanderer, ChatLineChoice.NOTHING);
        List<String> lines = WindowLayoutStore.describe();
        List<String> choices = new ArrayList<String>();
        for (String line : lines) {
            if (line.startsWith("notify\t") || line.startsWith("feedchoice\t")) {
                choices.add(line);
            }
        }
        assertEquals(Arrays.asList(
                "notify\teverything\tglobal",
                "notify\tmentions\twhisper:Alex",
                "feedchoice\tnothing\tserver_console",
                "feedchoice\tmentions\twhisper:Alex"), choices);
        lines.add("notify\tnothing\tnpc:Grey Wanderer");
        lines.add("feedchoice\tnothing\tnpc:Grey Wanderer");
        TwoWindowLayout.reset();
        WindowLayoutStore.load(lines);
        assertEquals(ChatLineChoice.ONLY_MENTIONS, ChatLayout.notification(alex));
        assertEquals(ChatLineChoice.ONLY_MENTIONS, ChatLayout.feedChoice(alex));
        assertEquals(ChatLineChoice.EVERYTHING, ChatLayout.notification(bob));
        assertEquals(ChatLineChoice.EVERYTHING,
                ChatLayout.notification(ConversationPage.of(ChatChannel.GLOBAL)));
        assertEquals(ChatLineChoice.NOTHING,
                ChatLayout.feedChoice(ConversationPage.of(ChatChannel.SERVER_CONSOLE)));
        assertEquals(ChatLineChoice.EVERYTHING,
                ChatLayout.notification(wanderer));
        assertEquals(ChatLineChoice.EVERYTHING, ChatLayout.feedChoice(wanderer));
        assertEquals(choices.size(), ChatLayout.notificationTabs().size()
                + ChatLayout.feedChoiceTabs().size());
    }

    /**
     * The NPC pages that close to keep the count at the limit are the
     * ones quiet longest, never one exempt; with too many exempt, fewer
     * close, and a count within the limit closes none.
     */
    @Test
    public void theNpcPagesQuietLongestCloseFirst() {
        ConversationPage bilbo = ConversationPage.npc("Bilbo");
        ConversationPage frodo = ConversationPage.npc("Frodo");
        ConversationPage sam = ConversationPage.npc("Sam");
        ConversationPage merry = ConversationPage.npc("Merry");
        java.util.Map<ConversationPage, Long> open =
                new java.util.LinkedHashMap<ConversationPage, Long>();
        open.put(sam, Long.valueOf(3L));
        open.put(bilbo, Long.valueOf(1L));
        open.put(merry, Long.valueOf(4L));
        open.put(frodo, Long.valueOf(2L));
        java.util.Set<ConversationPage> none = Collections.<ConversationPage>emptySet();
        assertEquals(Arrays.asList(bilbo, frodo),
                ChatLayout.npcPagesToClose(open, none, 2));
        assertEquals(Collections.singletonList(bilbo),
                ChatLayout.npcPagesToClose(open, none, 3));
        assertTrue(ChatLayout.npcPagesToClose(open, none, 4).isEmpty());
        assertTrue(ChatLayout.npcPagesToClose(open, none, 10).isEmpty());
        java.util.Set<ConversationPage> exempt = new java.util.HashSet<ConversationPage>(
                Arrays.asList(bilbo, merry));
        assertEquals(Arrays.asList(frodo, sam),
                ChatLayout.npcPagesToClose(open, exempt, 2));
        assertEquals(Arrays.asList(frodo, sam),
                ChatLayout.npcPagesToClose(open, exempt, 1));
    }

    /**
     * When an NPC's line opens its page by itself and more NPC pages
     * stand open than the limit, the ones quiet longest close, from a
     * locked window too; the page that spoke, a page in front of its
     * window and a page holding a draft stay. Closed ones wait in the
     * {@code +}, the one that spoke last first, until the session ends.
     */
    @Test
    public void onlyTheLastNpcConversationsStayOpenByThemselves() {
        List<ConversationPage> npcs = new ArrayList<ConversationPage>();
        for (String name : Arrays.asList("Bilbo", "Frodo", "Sam", "Merry",
                "Pippin")) {
            ConversationPage npc = ConversationPage.npc(name);
            npcs.add(npc);
            ChatLayout.noteNpcSpoke(npc);
            assertNotNull(ChatLayout.openTab(npc, "w2"));
        }
        assertFalse(ChatLayout.hasClosedNpcConversation());
        // Frodo stands behind the consoles in their window, locked.
        assertTrue(WindowLayout.moveTab(npcs.get(1), "w1", 0));
        WindowLayout.setActiveTab(ConversationPage.of(ChatChannel.CLIENT_CONSOLE));
        WindowLayout.setActiveTab(ConversationPage.of(ChatChannel.GLOBAL));
        assertTrue(WindowLayout.setLocked("w1", true));
        ChatLayout.closeQuietNpcConversations(npcs.get(4), 3);
        // Bilbo and Frodo were quiet longest: both close, the locked
        // window no bar to it.
        assertFalse(ChatLayout.isOpen(npcs.get(0)));
        assertFalse(ChatLayout.isOpen(npcs.get(1)));
        assertTrue(ChatLayout.isOpen(npcs.get(2)));
        assertTrue(ChatLayout.isOpen(npcs.get(4)));
        assertEquals(Arrays.asList(npcs.get(1), npcs.get(0)),
                ChatLayout.closedNpcConversations());
        assertTrue(ChatLayout.hasClosedNpcConversation());
        // Sam, the quietest left, is in front of its window, and Merry
        // holds a draft: at a limit of one, only Pippin could go, and he
        // just spoke.
        WindowLayout.setActiveTab(npcs.get(2));
        ClientChatChannelState.setDraft(npcs.get(3), "Well met");
        try {
            ChatLayout.closeQuietNpcConversations(npcs.get(4), 1);
        } finally {
            ClientChatChannelState.setDraft(npcs.get(3), "");
        }
        assertTrue(ChatLayout.isOpen(npcs.get(2)));
        assertTrue(ChatLayout.isOpen(npcs.get(3)));
        assertTrue(ChatLayout.isOpen(npcs.get(4)));
        // Speaking again makes a closed one the latest in the +.
        ChatLayout.noteNpcSpoke(npcs.get(0));
        assertEquals(Arrays.asList(npcs.get(0), npcs.get(1)),
                ChatLayout.closedNpcConversations());
        // A player's whisper is never counted.
        ChatLayout.openWhisper("Alex", "", "w2");
        ChatLayout.closeQuietNpcConversations(npcs.get(4), 1);
        assertTrue(ChatLayout.isOpen(ConversationPage.whisper("Alex", "")));
        ChatLayout.closeConversations();
        assertTrue(ChatLayout.closedNpcConversations().isEmpty());
        assertFalse(ChatLayout.hasClosedNpcConversation());
    }

    /** The session remembers the NPC conversations that spoke last, and no more. */
    @Test
    public void theSessionRemembersTheLatestNpcConversationsOnly() {
        for (int index = 0; index < ChatLayout.MAX_NPC_CONVERSATIONS + 6;
                index++) {
            ChatLayout.noteNpcSpoke(ConversationPage.npc("Orc " + index));
        }
        ChatLayout.noteNpcSpoke(ConversationPage.whisper("Alex", ""));
        List<ConversationPage> closed = ChatLayout.closedNpcConversations();
        assertEquals(ChatLayout.MAX_NPC_CONVERSATIONS, closed.size());
        assertEquals(ConversationPage.npc("Orc " + (ChatLayout.MAX_NPC_CONVERSATIONS + 5)),
                closed.get(0));
        assertEquals(ConversationPage.npc("Orc 6"), closed.get(closed.size() - 1));
        ChatLayout.reset();
        assertTrue(ChatLayout.closedNpcConversations().isEmpty());
    }

    /**
     * Operator Chat and the consoles open only by hand, however often they
     * were opened and closed: a line arriving never brings them back. Every
     * other channel closed by hand opens again with its next line.
     */
    @Test
    public void staffTalkAndTheConsolesOpenOnlyByHand() {
        ConversationPage operator = ConversationPage.of(ChatChannel.OPERATOR);
        assertTrue(ChatLayout.close(operator));
        assertFalse(ChatLayout.opensByItself(operator));
        assertTrue(ChatLayoutViews.reopen(ChatChannel.OPERATOR));
        assertTrue(ChatLayout.close(operator));
        assertFalse(ChatLayout.opensByItself(operator));
        assertFalse(ChatLayout.opensByItself(
                ConversationPage.of(ChatChannel.CLIENT_CONSOLE)));
        ConversationPage fellowship = ConversationPage.of(ChatChannel.FELLOWSHIP);
        assertTrue(ChatLayout.close(fellowship));
        assertTrue(ChatLayout.opensByItself(fellowship));
        assertEquals(ChatLineChoice.ONLY_MENTIONS,
                ChatLayout.notification(fellowship));
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
        Window w3 = Tearing.off(ConversationPage.of(ChatChannel.FELLOWSHIP), 50.0D,
                50.0D);
        assertNotNull(w3);
        assertEquals(Arrays.asList(w3, w1, w2), WindowLayout.stacked());
        WindowLayout.raise(w3.getId());
        assertEquals(Arrays.asList(w1, w2, w3), WindowLayout.stacked());
        assertTrue(WindowLayout.moveTab(ConversationPage.of(ChatChannel.FELLOWSHIP), "w2", 0));
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
            assertTrue(WindowLayout.isClosable(ConversationPage.of(order.get(index))));
            assertTrue(ChatLayout.close(ConversationPage.of(order.get(index))));
        }
        assertEquals(0, WindowLayout.order().size());
        assertTrue(WindowLayout.isEmpty());
        assertTrue(WindowLayout.windows().isEmpty());
        assertNull(WindowLayout.firstWindow());
        assertEquals(order.size(),
                ChatLayout.closedChannels().size());
        // A closed tab is not closable, and a message finds no window to
        // open itself in: the channel keeps receiving, closed.
        assertFalse(WindowLayout.isClosable(ConversationPage.of(order.get(0))));
        assertFalse(ChatLayout.close(ConversationPage.of(order.get(0))));
        assertFalse(ChatLayoutViews.reopen(order.get(0)));
        assertNull(ChatLayout.openTab(ConversationPage.whisper("Someone", ""),
                null));
        assertTrue(WindowLayout.isEmpty());
        // The + opens one back into a window of its own, unlocked as
        // every window but a new player's first.
        assertNotNull(WindowLayout.openInNewWindow(
                ConversationPage.of(order.get(0))));
        assertTrue(WindowLayout.isClosable(ConversationPage.of(order.get(0))));
        assertEquals(1, WindowLayout.windows().size());
        assertEquals(Collections.singletonList(order.get(0)),
                ChatLayoutViews.orderChannels());
        assertEquals(order.get(0),
                ChatLayoutViews.frontChannelOf(WindowLayout.firstWindow()));
        // Repeated closing and reopening leaves a consistent layout.
        for (int round = 0; round < 5; round++) {
            assertTrue(ChatLayout.close(ConversationPage.of(order.get(0))));
            assertTrue(WindowLayout.isEmpty());
            assertNotNull(WindowLayout.openInNewWindow(
                    ConversationPage.of(order.get(0))));
        }
        assertEquals(1, WindowLayout.order().size());
    }

    /** A whole window closes at once, unless its padlock holds it, and its channels survive it. */
    @Test
    public void closingAWindowKeepsItsChannels() {
        assertFalse(WindowLayout.closeWindow("nope"));
        assertTrue(WindowLayout.setLocked("w1", true));
        assertFalse("a locked window stays", WindowLayout.closeWindow("w1"));
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
        List<ConversationPage> group = Arrays.asList(
                ConversationPage.of(start.get(3)), ConversationPage.of(start.get(1)));
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
        assertFalse(WindowLayout.window("w2").contains(ConversationPage.of(start.get(1))));
        // A group detaches into a window of its own the same way.
        Window detached = Tearing.off(group, 20.0D, 30.0D);
        assertNotNull(detached);
        assertEquals(Arrays.asList(start.get(1), start.get(3)),
                ChatLayoutViews.channelsOf(detached));
        assertEquals(Arrays.asList(ChatChannel.CLIENT_CONSOLE,
                ChatChannel.SERVER_CONSOLE, ChatChannel.OPERATOR),
                ChatLayoutViews.channelsOf(WindowLayout.window("w1")));
        // Tabs from two windows are not a group, and neither is a closed
        // one: both are refused whole rather than half-applied.
        assertFalse(WindowLayout.moveTabs(Arrays.asList(
                ConversationPage.of(start.get(1)), ConversationPage.of(ChatChannel.CLIENT_CONSOLE)),
                "w1", 0));
        assertEquals(Arrays.asList(start.get(1), start.get(3)),
                ChatLayoutViews.channelsOf(detached));
    }

    /**
     * A tab torn off by hand stands where it was dropped, at the size it
     * is given (the window it came from), and unlocked: the player placed
     * it.
     */
    @Test
    public void aTornOffWindowStandsWhereItWasDroppedAndUnlocked() {
        Window source = WindowLayout.window("w1");
        Window detached = Tearing.off(ConversationPage.of(ChatChannel.CLIENT_CONSOLE),
                20.0D, 30.0D);
        assertNotNull(detached);
        assertTrue(detached != source);
        assertEquals(Tearing.HEIGHT, detached.getOwnHeight(), 0.0D);
        assertEquals(Tearing.WIDTH, detached.getOwnWidth());
        assertEquals(20.0D, detached.getOffsetX(), 0.0D);
        assertFalse(detached.isLocked());
    }

    @Test
    public void everyWindowIsEqualAndTheLastOneIsNoDifferent() {
        // Emptying the console window by docking drops it.
        assertTrue(WindowLayout.moveTab(ConversationPage.of(ChatChannel.CLIENT_CONSOLE), "w2", 0));
        assertTrue(WindowLayout.moveTab(ConversationPage.of(ChatChannel.SERVER_CONSOLE),
                "w2", 99));
        assertTrue(WindowLayout.moveTab(ConversationPage.of(ChatChannel.OPERATOR), "w2", 99));
        assertNull(WindowLayout.window("w1"));
        assertEquals(1, WindowLayout.windows().size());
        assertEquals(ChatChannel.CLIENT_CONSOLE,
                ChatLayoutViews.channelsOf(WindowLayout.firstWindow()).get(0));
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (channel != ChatChannel.CLIENT_CONSOLE) {
                ChatLayout.close(ConversationPage.of(channel));
            }
        }
        assertEquals(1, ChatLayoutViews.channelsOf(WindowLayout.firstWindow()).size());
        ChatChannel last = ChatLayoutViews.channelsOf(WindowLayout.firstWindow()).get(0);
        // Its only tab dragged out would move the window, which its
        // padlock holds; unlocked, it moves like any other.
        WindowLayout.setLocked(WindowLayout.firstWindow().getId(), true);
        assertNull(Tearing.off(ConversationPage.of(last), 10.0D, 20.0D));
        WindowLayout.setLocked(WindowLayout.firstWindow().getId(), false);
        assertSame(WindowLayout.firstWindow(),
                Tearing.off(ConversationPage.of(last), 10.0D, 20.0D));
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
        assertTrue(WindowLayout.moveTab(ConversationPage.of(ChatChannel.OOC), "w2", 0));
        assertEquals(ChatChannel.OOC,
                ChatLayoutViews.channelsOf(WindowLayout.window("w2")).get(0));
        assertFalse(WindowLayout.moveTab(ConversationPage.of(ChatChannel.OOC), "w2", 0));

        // Detach Fellowship into its own window.
        Window fellowship = Tearing.off(ConversationPage.of(ChatChannel.FELLOWSHIP),
                60.0D, 120.0D);
        assertNotNull(fellowship);
        assertEquals("w3", fellowship.getId());
        assertEquals(Collections.singletonList(ChatChannel.FELLOWSHIP),
                ChatLayoutViews.channelsOf(fellowship));
        assertEquals(ChatChannel.FELLOWSHIP, ChatLayoutViews.frontChannelOf(fellowship));
        assertEquals(60.0D, fellowship.getOffsetX(), 0.0D);
        // A percent past the margin stands: the window may hang off the
        // screen by up to its own size.
        assertEquals(120.0D, fellowship.getOffsetY(), 0.0D);
        assertEquals(3, WindowLayout.windows().size());
        assertSame(fellowship, WindowLayout.windowOf(ConversationPage.of(ChatChannel.FELLOWSHIP)));
        assertFalse(WindowLayout.window("w2").contains(ConversationPage.of(ChatChannel.FELLOWSHIP)));
        List<ChatChannel> order = ChatLayoutViews.orderChannels();
        assertEquals(ChatChannel.FELLOWSHIP, order.get(order.size() - 1));

        // Dock Faction into the fellowship window, at the front.
        assertTrue(WindowLayout.moveTab(ConversationPage.of(ChatChannel.FACTION), "w3", 0));
        assertEquals(Arrays.asList(ChatChannel.FACTION, ChatChannel.FELLOWSHIP),
                ChatLayoutViews.channelsOf(fellowship));
        assertEquals(ChatChannel.FACTION, ChatLayoutViews.frontChannelOf(fellowship));

        // A window's only tab dragged out just moves the window.
        Window moved = Tearing.off(ConversationPage.of(ChatChannel.FACTION),
                5.0D, 5.0D);
        assertNotNull(moved);
        assertEquals("w4", moved.getId());
        assertEquals(4, WindowLayout.windows().size());
        assertSame(fellowship, Tearing.off(ConversationPage.of(ChatChannel.FELLOWSHIP),
                1.0D, 2.0D));
        assertEquals(1.0D, fellowship.getOffsetX(), 0.0D);
        assertEquals(4, WindowLayout.windows().size());

        // Docking the last tab elsewhere empties the window away.
        assertTrue(WindowLayout.moveTab(ConversationPage.of(ChatChannel.FELLOWSHIP), "w2", 99));
        assertNull(WindowLayout.window("w3"));
        assertEquals(3, WindowLayout.windows().size());
        List<ChatChannel> tabs = ChatLayoutViews.channelsOf(WindowLayout.window("w2"));
        assertEquals(ChatChannel.FELLOWSHIP, tabs.get(tabs.size() - 1));
        // Closing a window's last tab drops the window too.
        assertTrue(ChatLayout.close(ConversationPage.of(ChatChannel.FACTION)));
        assertNull(WindowLayout.window("w4"));
        assertEquals(Collections.singletonList(ChatChannel.FACTION),
                ChatLayout.closedChannels());
    }

    /**
     * The padlock holds a window's place, its size and its tabs: by hand
     * none of them closes, leaves, arrives or changes places, and the
     * window neither closes nor resets. What is inside a tab stays the
     * player's to set.
     */
    @Test
    public void aLockedWindowKeepsItsPlaceItsSizeAndItsTabs() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);
        ConversationPage operator = ConversationPage.of(ChatChannel.OPERATOR);
        assertTrue(WindowLayout.setLocked("w2", true));
        assertFalse(WindowLayout.setLocked("w2", true));
        List<ChatChannel> held = ChatLayoutViews.channelsOf(
                WindowLayout.window("w2"));
        assertFalse(WindowLayout.isClosable(global));
        assertFalse(ChatLayout.close(global));
        assertFalse("no reorder", WindowLayout.moveTab(ooc, "w2", 0));
        assertFalse("none leaves", WindowLayout.moveTab(ooc, "w1", 0));
        assertFalse("none arrives", WindowLayout.moveTab(operator, "w2", 0));
        assertFalse(WindowLayout.addTab("w2", ConversationPage.whisper("Bilbo", "")));
        assertNull(Tearing.off(ooc, 1.0D, 1.0D));
        assertFalse(WindowLayout.closeWindow("w2"));
        assertFalse(WindowLayout.resetWindow("w2"));
        assertEquals(held, ChatLayoutViews.channelsOf(WindowLayout.window("w2")));
        ChatLayout.setNotification(ooc, ChatLineChoice.NOTHING);
        assertTrue(ChatLayout.isMuted(ConversationPage.of(ChatChannel.OOC)));
        assertTrue(WindowLayout.setLocked("w2", false));
        assertTrue(ChatLayout.close(global));
        assertFalse(WindowLayout.setPosition("nope", 1.0D, 1.0D, true));
    }

    /**
     * A locked window takes no tab, whoever opens it, the player from a
     * menu or a whisper by itself: asked of one, the tab opens in an
     * unlocked window holding its kind, and with every such window locked
     * in a window of its own, unlocked, a step on from the window asked
     * for and in front of it. The ones after it join that window, and the
     * chat's key shows the one waiting there unread first.
     */
    @Test
    public void aTabOpeningPassesLockedWindowsBy() {
        WindowLayout.setLocked("w2", true);
        ConversationPage bilbo = ConversationPage.whisper("Bilbo", "");
        assertNotNull(WindowLayout.openTab(bilbo, "w2"));
        assertEquals("w1", WindowLayout.windowOf(bilbo).getId());
        WindowLayout.setLocked("w1", true);
        WindowLayout.raise("w2");
        int before = WindowLayout.windows().size();
        ConversationPage frodo = ChatLayout.openTab(ConversationPage.whisper("Frodo", ""),
                "w2");
        assertNotNull(frodo);
        assertEquals(before + 1, WindowLayout.windows().size());
        Window own = WindowLayout.windowOf(frodo);
        assertEquals(1, own.getTabs().size());
        assertFalse(own.isLocked());
        assertFalse("a step on from the window asked for",
                WindowPlacement.atDefaultPlace(own));
        List<Window> stacked = WindowLayout.stacked();
        assertSame("it opens in front of the window asked for", own,
                stacked.get(stacked.size() - 1));
        // The next one joins it.
        ConversationPage sam = ChatLayout.openTab(ConversationPage.whisper("Sam", ""), null);
        assertEquals(before + 1, WindowLayout.windows().size());
        assertSame(own, WindowLayout.windowOf(sam));
        assertNull("nothing waits unread there yet",
                ChatLayout.waitingInOwnWindow());
        ChatLayout.closeConversations();
        assertNull(ChatLayout.waitingInOwnWindow());
    }

    /**
     * A tab that ends by itself leaves a locked window: a whisper as the
     * session ends, as a page of the world does as the player walks away.
     * Coming back to the server puts it back where it stood, locked
     * window or not, since that is the layout as it was left.
     */
    @Test
    public void aTabThatEndsByItselfLeavesALockedWindowAndComesBackToIt() {
        ChatLayout.restoreConversations("server:a");
        ConversationPage whisper = ChatLayout.openWhisper("Bilbo", "", "w2");
        assertEquals("w2", WindowLayout.windowOf(whisper).getId());
        WindowLayout.setLocked("w2", true);
        assertFalse(ChatLayout.close(whisper));
        ChatLayout.closeConversations();
        assertFalse(WindowLayout.isOpen(whisper));
        ChatLayout.restoreConversations("server:a");
        assertEquals("w2", WindowLayout.windowOf(whisper).getId());
        assertTrue(WindowLayout.window("w2").isLocked());
    }

    /**
     * A conversation that opens by itself joins an unlocked window that
     * already holds a conversation, the one last brought to the front,
     * rather than opening a window of its own.
     */
    @Test
    public void aConversationOpeningByItselfJoinsAnUnlockedConversationWindow() {
        int before = WindowLayout.windows().size();
        WindowLayout.raise("w2");
        ConversationPage whisper = ChatLayout.openWhisper("Bilbo", "", null);
        assertNotNull(whisper);
        assertEquals("w2", WindowLayout.windowOf(whisper).getId());
        WindowLayout.setLocked("w2", true);
        ConversationPage frodo = ChatLayout.openWhisper("Frodo", "", null);
        assertEquals("the front one locked, the other takes it", "w1",
                WindowLayout.windowOf(frodo).getId());
        assertEquals(before, WindowLayout.windows().size());
    }

    /**
     * With no window holding a conversation, one that opens by itself
     * waits; the chat's first window brings it back with Global and OOC.
     * One the player asks for opens that first window at once.
     */
    @Test
    public void withNoConversationWindowAConversationWaitsForTheFirstWindow() {
        assertTrue(WindowLayout.closeWindow("w1"));
        assertTrue(WindowLayout.closeWindow("w2"));
        assertFalse(ChatLayout.hasConversationWindow());
        assertNull(ChatLayout.openTab(ConversationPage.of(ChatChannel.PROXIMITY), null));
        Window first = ChatLayout.openFirstWindow();
        assertNotNull(first);
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.OOC,
                ChatChannel.PROXIMITY), ChatLayoutViews.channelsOf(first));
        assertEquals(ChatChannel.GLOBAL, ChatLayoutViews.frontChannelOf(first));
        assertTrue(WindowPlacement.atDefaultPlace(first));
        assertTrue(first.isLocked());
        WindowLayout.setLocked(first.getId(), false);
        assertTrue(WindowLayout.closeWindow(first.getId()));
        ConversationPage whisper = ChatLayout.openWhisper("Frodo", "", null);
        assertNotNull("asked for, it opens the first window", whisper);
        assertTrue(ChatLayout.hasConversationWindow());
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
        // The window asked for wins over the front one, while unlocked.
        assertEquals("w1", WindowLayout.windowOf(
                ChatLayout.openWhisper("Sam", "", "w1")).getId());
        assertTrue(WindowLayout.setLocked("w1", true));
        assertEquals("w2", WindowLayout.windowOf(
                ChatLayout.openWhisper("Merry", "", "w1")).getId());
    }

    /**
     * A new window opened from the {@code +} stands in front of the rest,
     * unlocked, a step right and down from the window in front, at its
     * size.
     */
    @Test
    public void aNewWindowStandsInFrontAStepOnFromTheFrontWindow() {
        WindowLayout.setWindowHeight("w2", 150.0D, true);
        WindowLayout.setWindowWidth("w2", 200, true);
        WindowLayout.raise("w2");
        ConversationPage plus = ConversationPage.whisper("Frodo", "");
        assertNotNull(WindowLayout.openInNewWindow(plus));
        Window opened = WindowLayout.windowOf(plus);
        java.util.List<Window> order = WindowLayout.stacked();
        assertSame(opened, order.get(order.size() - 1));
        assertFalse(opened.isLocked());
        assertEquals(150.0D, opened.getOwnHeight(), 0.0D);
        assertEquals(200, opened.getOwnWidth());
    }

    @Test
    public void onlyTheTabsBoundHowManyWindowsStand() {
        List<ChatChannel> order = ChatChannel.presentationOrder();
        for (ChatChannel channel : order) {
            Window source = WindowLayout.windowOf(ConversationPage.of(channel));
            if (ChatLayoutViews.channelsOf(source).size() > 1) {
                assertNotNull(Tearing.off(ConversationPage.of(channel), 0.0D, 0.0D));
            }
        }
        for (Window window : WindowLayout.windows()) {
            assertEquals(1, ChatLayoutViews.channelsOf(window).size());
        }
        // Past every channel's own window, each whisper gets one too:
        // no count of windows refuses a tab its own.
        int before = WindowLayout.windows().size();
        for (int index = 0; index < 12; index++) {
            ConversationPage friend = ChatLayout.openTab(
                    ConversationPage.whisper("Friend" + index, ""), "w2");
            assertNotNull(Tearing.off(friend, 0.0D, 0.0D));
        }
        assertEquals(before + 12, WindowLayout.windows().size());
        // A window holds at least one tab and a tab stands once, so
        // there are never more windows than tabs.
        assertTrue(WindowLayout.windows().size() <= WindowLayout.order().size());
    }

    @Test
    public void aWindowAddedWithAnOpenTabLeavesItWhereItIs() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        Window holding = WindowLayout.windowOf(global);
        assertNull("nothing new to hold", WindowLayout.addWindow(
                Collections.singletonList(global), global));
        assertSame(holding, WindowLayout.windowOf(global));
    }

    @Test
    public void loadRepairsStaleDuplicateAndMissingEntries() {
        // Fellowship listed twice, an unknown window id, an empty window,
        // percents out of range, Faction and the consoles placed nowhere.
        WindowLayoutStore.load(Arrays.asList(
                "window w3 locked=true x=250 y=-5 active=ooc tabs=fellowship,ooc",
                "window w8 locked=false x=0 y=100 active=fellowship tabs=global,fellowship,proximity",
                "window bogus x=0 y=0 tabs=faction",
                "window w7 x=0 y=0 tabs=",
                "window w3 x=0 y=0 tabs=faction",
                "closed operator",
                "notify\tnothing\tooc",
                "notify\teverything\toperator",
                "feedchoice\tmentions\tglobal",
                "muted fellowship",
                "feed x=120 y=33"));
        assertEquals(100.0D, ChatLayout.feedOffsetX(), 0.0D);
        assertEquals(33.0D, ChatLayout.feedOffsetY(), 0.0D);

        assertEquals(2, WindowLayout.windows().size());
        Window w3 = WindowLayout.firstWindow();
        assertEquals("w3", w3.getId());
        // Fellowship keeps its first placement; the unplaced channels land
        // in the first window, the consoles aside: they wait in the +,
        // hidden, as for a new player.
        assertEquals(Arrays.asList(ChatChannel.FELLOWSHIP, ChatChannel.OOC,
                ChatChannel.FACTION), ChatLayoutViews.channelsOf(w3));
        assertFalse(ChatLayout.opensByItself(ConversationPage.of(ChatChannel.CLIENT_CONSOLE)));
        assertFalse(ChatLayout.opensByItself(ConversationPage.of(ChatChannel.SERVER_CONSOLE)));
        assertEquals(ChatChannel.OOC, ChatLayoutViews.frontChannelOf(w3));
        assertTrue(w3.isLocked());
        // A window's percent is bounded a whole window past the margins
        // (250 to 200); a little past the top is kept as stored.
        assertEquals(200.0D, w3.getOffsetX(), 0.0D);
        assertEquals(-5.0D, w3.getOffsetY(), 0.0D);
        // The second window keeps its id and its place; its front tab,
        // Fellowship, went to the first window, so the first tab left stands.
        Window second = WindowLayout.windows().get(1);
        assertEquals("w8", second.getId());
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.PROXIMITY),
                ChatLayoutViews.channelsOf(second));
        assertEquals(ChatChannel.GLOBAL, ChatLayoutViews.frontChannelOf(second));
        assertEquals(100.0D, second.getOffsetY(), 0.0D);
        assertEquals(Arrays.asList(ChatChannel.OPERATOR,
                ChatChannel.CLIENT_CONSOLE, ChatChannel.SERVER_CONSOLE),
                ChatLayout.closedChannels());
        assertEquals(ChatLineChoice.NOTHING,
                ChatLayout.notification(ConversationPage.of(ChatChannel.OOC)));
        assertEquals(ChatLineChoice.EVERYTHING,
                ChatLayout.notification(ConversationPage.of(ChatChannel.OPERATOR)));
        assertEquals(ChatLineChoice.ONLY_MENTIONS,
                ChatLayout.feedChoice(ConversationPage.of(ChatChannel.GLOBAL)));
        // A mute line names nothing the layout reads.
        assertEquals(ChatLineChoice.ONLY_MENTIONS,
                ChatLayout.notification(ConversationPage.of(ChatChannel.FELLOWSHIP)));
        // New windows number on from the highest id seen.
        assertEquals("w9", Tearing.off(ConversationPage.of(ChatChannel.PROXIMITY),
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
        assertFalse(ChatLayout.isFeedPlaced());
        WindowLayoutStore.load(Arrays.asList("closed operator"));
        // No windows at all: one window with everything still open but
        // the consoles, which wait in the + as for a new player.
        assertEquals(1, WindowLayout.windows().size());
        Window only = WindowLayout.firstWindow();
        assertEquals("w1", only.getId());
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC, ChatChannel.FELLOWSHIP),
                ChatLayoutViews.channelsOf(only));
        assertFalse(ChatLayout.opensByItself(ConversationPage.of(ChatChannel.CLIENT_CONSOLE)));
        assertEquals(ChatChannel.GLOBAL, ChatLayoutViews.frontChannelOf(only));
        assertTrue(WindowPlacement.atDefaultPlace(only));
    }
}
