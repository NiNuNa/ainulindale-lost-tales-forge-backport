package com.ninuna.losttales.client.window;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.chat.ChatLayout;
import com.ninuna.losttales.client.chat.ClientChatChannelViews;
import com.ninuna.losttales.client.chat.ConversationPage;
import com.ninuna.losttales.client.chat.TwoWindowLayout;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A page may stand open more than once, each copy a tab with a view of its
 * own: asked about a page, the layout answers with the copy used last; a
 * new copy opens from the menu, Duplicate Page and Page Search; a page
 * picked on a menu tab takes its place; what the page is the copies share,
 * and a conversation's copies share its unread lines and settings.
 */
public final class PageCopiesTest {
    private static final String PAGE = "copies_page";
    private static final String ONCE = "copies_once";
    private static final String WORLD = "copies_world";
    private static final ConversationPage GLOBAL = ConversationPage.of(ChatChannel.GLOBAL);

    @BeforeClass
    public static void registerPages() {
        WindowPages.Factory empty = new WindowPages.Factory() {
            @Override
            public PageContent create() {
                return new EmptyPage();
            }
        };
        if (WindowPages.byId(PAGE) == null) {
            WindowPages.register(PAGE, "gui.test.copies.page",
                    new ItemStack(Items.book), null, PageCategory.QUEST_JOURNAL,
                    empty);
        }
        if (WindowPages.byId(ONCE) == null) {
            WindowPages.registerOnce(ONCE, "gui.test.copies.once",
                    new ItemStack(Items.map), null, PageCategory.SETTINGS, empty);
        }
        if (WindowPages.byId(WORLD) == null) {
            WindowPages.registerGamePage(WORLD, "gui.test.copies.world",
                    new ItemStack(Items.compass), PageCategory.MAP, empty);
        }
    }

    @Before
    public void reset() {
        TwoWindowLayout.reset();
        WindowView.clear();
    }

    @After
    public void cleanUp() {
        WindowView.clear();
        TwoWindowLayout.reset();
        ClientChatChannelViews.clear();
    }

    @Test
    public void aCopysIdNamesItsNumberAndReadsBack() {
        OtherPage page = WindowPages.tab(PAGE);
        WindowPage second = page.withInstance(2);
        assertEquals("page:" + PAGE + "#2", second.id());
        assertEquals(second, WindowPage.fromId(second.id()));
        assertEquals(page, WindowPage.fromId(page.id()));
        assertTrue(second.isCopyOf(page));
        assertFalse(second.equals(page));
        ConversationPage copy = GLOBAL.withInstance(3);
        assertEquals("global#3", copy.id());
        assertEquals(copy, WindowPage.fromId("global#3"));
        assertEquals(copy, ConversationPage.fromId("global#3"));
        assertSame(GLOBAL, copy.conversation());
        assertEquals(1, WindowPage.instanceIn("global"));
        assertEquals("global", WindowPage.pageIdIn("global#3"));
        assertEquals("a number that is no copy's stays part of the id",
                "npc:Bob#x", WindowPage.pageIdIn("npc:Bob#x"));
    }

    @Test
    public void aPageOpensAsManyCopiesAsAskedEachWithItsOwnContent() {
        OtherPage page = WindowPages.tab(PAGE);
        WindowPage first = WindowLayout.openCopy(page, null);
        WindowPage second = WindowLayout.openCopy(page, null);
        assertEquals(page, first);
        assertEquals(2, second.instance());
        assertEquals(Arrays.asList(first, second), WindowLayout.copiesOf(page));
        assertNotSame(((OtherPage)first).content(), ((OtherPage)second).content());
        assertEquals(second, ((OtherPage)second).content().tab());
    }

    @Test
    public void askedAboutAPageTheLayoutAnswersWithTheCopyUsedLast() {
        OtherPage page = WindowPages.tab(PAGE);
        WindowLayout.openCopy(page, null);
        WindowPage second = WindowLayout.duplicate(page);
        assertNotNull(second);
        assertSame(second, WindowLayout.lastUsed(page));
        // The page's first window opened locked, as a category's first does.
        Unlocking.all();
        assertTrue(WindowLayout.close(page));
        assertTrue("the second copy keeps the page open", WindowLayout.isOpen(page));
        assertSame(second, WindowLayout.lastUsed(page));
        assertSame("a key brings the copy open forward, opening none",
                second, WindowLayout.openInView(page,
                        WindowLayout.viewOf(WindowLayout.windowOf(second))));
        assertEquals(1, WindowLayout.copiesOf(page).size());
    }

    @Test
    public void duplicateOpensRightAfterThePageInItsWindow() {
        Window window = WindowLayout.windowOf(GLOBAL);
        int index = window.getTabs().indexOf(GLOBAL);
        WindowPage copy = WindowLayout.duplicate(GLOBAL);
        assertEquals(GLOBAL.withInstance(2), copy);
        assertSame(window, WindowLayout.windowOf(copy));
        assertEquals(index + 1, window.getTabs().indexOf(copy));
        assertSame(copy, window.getActiveTab());
    }

    @Test
    public void aLockedWindowsDuplicateOpensInAWindowOfItsOwn() {
        Window window = WindowLayout.windowOf(GLOBAL);
        WindowLayout.setLocked(window.getId(), true);
        WindowPage copy = WindowLayout.duplicate(GLOBAL);
        assertNotNull(copy);
        assertNotSame(window, WindowLayout.windowOf(copy));
        assertFalse(window.contains(copy));
    }

    @Test
    public void aPagePickedOnANewPageTabTurnsIntoIt() {
        Window window = WindowLayout.windowOf(GLOBAL);
        OtherPage menu = WindowPages.tab(PAGE);
        WindowPage menuTab = WindowLayout.openCopyIn(menu, window.getId());
        assertSame(window, WindowLayout.windowOf(menuTab));
        int index = window.getTabs().indexOf(menuTab);
        WindowPage picked = WindowLayout.replaceTab(menuTab, GLOBAL);
        assertEquals("Global is open already: a copy of it", 2, picked.instance());
        assertEquals(index, window.getTabs().indexOf(picked));
        assertFalse(window.contains(menuTab));
        assertSame(picked, window.getActiveTab());
        assertEquals("the row draws it on from the tab it replaced",
                menuTab, WindowLayout.replacedBy(picked));
    }

    @Test
    public void aLockedWindowsNewPageTabStaysAsItIs() {
        Window window = WindowLayout.windowOf(GLOBAL);
        WindowPage menuTab = WindowLayout.openCopyIn(WindowPages.tab(PAGE),
                window.getId());
        WindowLayout.setLocked(window.getId(), true);
        assertNull(WindowLayout.replaceTab(menuTab, GLOBAL));
        assertTrue(window.contains(menuTab));
    }

    @Test
    public void aLockedWindowsNewPageOpensInAWindowOfItsOwn() {
        Window window = WindowLayout.windowOf(GLOBAL);
        WindowLayout.setLocked(window.getId(), true);
        WindowPage menuTab = WindowLayout.openCopyIn(WindowPages.tab(PAGE),
                window.getId());
        assertNotNull(menuTab);
        assertNotSame(window, WindowLayout.windowOf(menuTab));
        assertFalse(WindowLayout.windowOf(menuTab).isLocked());
    }

    /**
     * WIN-18: everything opened from a locked window, its {@code +}, a
     * Duplicate Page and a New Page's pick, opens in another unlocked
     * window of its view with room first.
     */
    @Test
    public void whatALockedWindowOpensGoesToAnotherUnlockedWindowOfItsView() {
        Window locked = WindowLayout.windowOf(GLOBAL);
        ConversationPage friend = ConversationPage.whisper("friend", "");
        Window other = Unlocking.of(WindowLayout.addWindow(
                Arrays.asList(friend), friend));
        assertSame("both stand in the Chat view", WindowLayout.viewOf(locked),
                WindowLayout.viewOf(other));
        WindowLayout.setLocked(locked.getId(), true);
        WindowPage plus = WindowLayout.openCopyIn(WindowPages.tab(PAGE),
                locked.getId());
        assertSame("the +", other, WindowLayout.windowOf(plus));
        WindowPage duplicated = WindowLayout.duplicate(GLOBAL);
        assertSame("Duplicate Page", other, WindowLayout.windowOf(duplicated));
        // A pick on a New Page standing in the locked window cannot turn
        // that tab into it, and opens as the window's + does.
        WindowPage picked = WindowLayout.openCopyIn(GLOBAL, locked.getId());
        assertSame("a New Page's pick", other, WindowLayout.windowOf(picked));
        assertFalse(locked.contains(plus) || locked.contains(duplicated)
                || locked.contains(picked));
    }

    /** WIN-18: with no unlocked window of its view taking it, a new window a step on. */
    @Test
    public void whatALockedWindowOpensWithNoRoomElseGetsANewWindowAStepOn() {
        Window locked = WindowLayout.windowOf(GLOBAL);
        ConversationPage friend = ConversationPage.whisper("friend", "");
        Window other = WindowLayout.addWindow(Arrays.asList(friend), friend);
        WindowLayout.setLocked(locked.getId(), true);
        assertTrue(other.isLocked());
        WindowPage plus = WindowLayout.openCopyIn(WindowPages.tab(PAGE),
                locked.getId());
        Window created = WindowLayout.windowOf(plus);
        assertNotSame(locked, created);
        assertNotSame(other, created);
        assertFalse("unlocked", created.isLocked());
        assertSame("in the locked window's view", WindowLayout.viewOf(locked),
                WindowLayout.viewOf(created));
    }

    @Test
    public void aPageThatOpensOnceIsBroughtForwardInsteadOfCopied() {
        OtherPage once = WindowPages.tab(ONCE);
        WindowPage first = WindowLayout.openCopy(once, null);
        assertEquals(once, first);
        assertFalse(once.opensMoreThanOnce());
        assertSame(first, WindowLayout.openCopy(once, null));
        assertNull(WindowLayout.duplicate(once));
        assertEquals(1, WindowLayout.copiesOf(once).size());
        assertFalse(WindowPages.tab(WORLD).opensMoreThanOnce());
    }

    /**
     * Copies of a conversation share its unread lines and its divider
     * (CHAT-02): a line waits unread in both, and reading it in one reads
     * it in the other.
     */
    @Test
    public void copiesOfAConversationShareItsUnread() {
        ConversationPage copy = (ConversationPage)WindowLayout.duplicate(GLOBAL);
        ConversationPage elsewhere = ConversationPage.of(ChatChannel.OOC);
        ClientChatChannelViews.record(-1, GLOBAL, elsewhere, false,
                ChatMessageIds.NONE, System.currentTimeMillis(), false);
        assertEquals(1, ClientChatChannelViews.unreadCount(GLOBAL));
        assertEquals(1, ClientChatChannelViews.unreadCount(copy));
        assertEquals(ClientChatChannelViews.unreadDividerLine(GLOBAL),
                ClientChatChannelViews.unreadDividerLine(copy));
        ClientChatChannelViews.markViewed(copy);
        assertEquals(0, ClientChatChannelViews.unreadCount(copy));
        assertEquals("read in the first copy too",
                0, ClientChatChannelViews.unreadCount(GLOBAL));
    }

    /** A line in front in one copy, at its newest line, is read in every copy. */
    @Test
    public void aLineShownInOneCopyIsReadInAll() {
        ConversationPage copy = (ConversationPage)WindowLayout.duplicate(GLOBAL);
        ClientChatChannelViews.record(-2, GLOBAL, copy, false,
                ChatMessageIds.NONE, System.currentTimeMillis(), false);
        assertEquals(0, ClientChatChannelViews.unreadCount(GLOBAL));
        assertEquals(0, ClientChatChannelViews.unreadCount(copy));
    }

    @Test
    public void eachCopyOfAConversationKeepsItsOwnPanels() {
        ConversationPage copy = (ConversationPage)WindowLayout.duplicate(GLOBAL);
        assertTrue(ChatLayout.setMembersHidden(copy, true));
        assertTrue(ChatLayout.isMembersHidden(copy));
        assertFalse("the first copy keeps its list",
                ChatLayout.isMembersHidden(GLOBAL));
        assertFalse(GLOBAL.equals(copy));
        assertEquals(GLOBAL.title(), copy.title());
    }

    /**
     * A conversation's two settings are one for all its copies (CHAT-02):
     * a choice made in one copy holds in the other.
     */
    @Test
    public void copiesShareTheConversationsSettings() {
        ConversationPage copy = (ConversationPage)WindowLayout.duplicate(GLOBAL);
        ChatLayout.setNotification(copy,
                com.ninuna.losttales.client.chat.ChatLineChoice.NOTHING);
        assertTrue(copy.isMuted());
        assertTrue("the first copy is muted too", ChatLayout.isMuted(GLOBAL));
        assertFalse(ChatLayout.chimes(GLOBAL, true));
        ChatLayout.setNotification(GLOBAL,
                com.ninuna.losttales.client.chat.ChatLineChoice.EVERYTHING);
        assertTrue(ChatLayout.chimes(copy, false));
        ChatLayout.setFeedChoice(copy,
                com.ninuna.losttales.client.chat.ChatLineChoice.ONLY_MENTIONS);
        assertEquals(com.ninuna.losttales.client.chat.ChatLineChoice.ONLY_MENTIONS,
                ChatLayout.feedChoice(GLOBAL));
        ChatLayout.setFeedChoice(GLOBAL,
                com.ninuna.losttales.client.chat.ChatLineChoice.EVERYTHING);
    }

    /** A page with nothing on it. */
    private static final class EmptyPage extends PageContent {
        @Override
        public void draw(Minecraft minecraft, LostTalesUiHitBox box,
                         double clipX, double clipY, double pointerX,
                         double pointerY, float partialTicks, int alpha) {
        }
    }
}
