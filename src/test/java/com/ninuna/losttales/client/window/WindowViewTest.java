package com.ninuna.losttales.client.window;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.chat.ConversationPage;
import com.ninuna.losttales.client.chat.TwoWindowLayout;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.Arrays;
import java.util.List;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A key shows its category's pages and hides the rest in their windows; a
 * page opens in a window of its category, else in its category's first
 * window; a window pinned to the GUI shows in every view, one pinned to the
 * HUD stays while playing; closing a window closes what the view shows of
 * it.
 */
public final class WindowViewTest {
    private static final String PAGE = "view_page";
    private static final String OTHER = "view_other";
    private static final String WORLD = "view_world";

    private static final ConversationPage GLOBAL = ConversationPage.of(ChatChannel.GLOBAL);
    private static final ConversationPage OOC = ConversationPage.of(ChatChannel.OOC);

    @BeforeClass
    public static void registerPages() {
        WindowPages.Factory empty = new WindowPages.Factory() {
            @Override
            public PageContent create() {
                return new EmptyPage();
            }
        };
        if (WindowPages.byId(PAGE) == null) {
            WindowPages.register(PAGE, "gui.test.view.page",
                    new ItemStack(Items.book), null,
                    PageCategory.QUEST_JOURNAL, empty);
        }
        if (WindowPages.byId(OTHER) == null) {
            WindowPages.register(OTHER, "gui.test.view.other",
                    new ItemStack(Items.map), null, PageCategory.MAP, empty);
        }
        if (WindowPages.byId(WORLD) == null) {
            WindowPages.registerWorldPage(WORLD, "gui.test.view.world",
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
        WindowLayout.setChangeListener(null);
        ChatChannel.resetToBuiltIn();
    }

    @Test
    public void withNoScreenOpenEveryTabCountsAsShown() {
        OtherPage page = WindowPages.tab(PAGE);
        WindowLayout.showPage(page);
        assertNull(WindowView.category());
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue(WindowView.shows(page));
        assertFalse("no tab is no tab in any view", WindowView.shows(null));
    }

    @Test
    public void theChatsKeyShowsTheConversationsAndNoPage() {
        OtherPage page = WindowPages.tab(PAGE);
        Window window = WindowLayout.showPage(page);
        WindowView.forChat();
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue(WindowView.shows(OOC));
        assertFalse(WindowView.shows(page));
        assertTrue("the page waits in its window",
                WindowLayout.isOpen(page));
        assertTrue(WindowFrame.visibleTabs(window).isEmpty());
    }

    /** The command key shows the consoles alone; the chat's key everything else but them. */
    @Test
    public void theConsolesAreAViewOfTheirOwn() {
        ConversationPage console = ConversationPage.of(ChatChannel.CLIENT_CONSOLE);
        ConversationPage serverLog = ConversationPage.of(ChatChannel.SERVER_CONSOLE);
        WindowView.forChat();
        assertTrue(WindowView.shows(GLOBAL));
        assertFalse(WindowView.shows(console));
        assertFalse(WindowView.shows(serverLog));
        WindowView.forConsole();
        assertTrue(WindowView.shows(console));
        assertTrue(WindowView.shows(serverLog));
        assertFalse(WindowView.shows(GLOBAL));
        assertFalse(WindowView.shows(WindowPages.tab(PAGE)));
    }

    /** A page's key shows its category: its pages, and no other's. */
    @Test
    public void aPagesKeyShowsItsCategory() {
        OtherPage page = WindowPages.tab(PAGE);
        OtherPage other = WindowPages.tab(OTHER);
        OtherPage world = WindowPages.tab(WORLD);
        WindowLayout.showPage(page);
        WindowLayout.showPage(other);
        WindowLayout.showPage(world);
        WindowView.forPage(page);
        assertTrue(WindowView.isFor(page));
        assertFalse(WindowView.isFor(other));
        assertTrue(WindowView.shows(page));
        assertFalse(WindowView.shows(other));
        assertFalse(WindowView.shows(GLOBAL));
        WindowView.forPage(other);
        assertTrue("one key for the whole category", WindowView.isFor(world));
        assertTrue(WindowView.shows(world));
        assertFalse(WindowView.shows(page));
    }

    @Test
    public void aTabOpenedByHandJoinsTheViewUntilTheScreenCloses() {
        OtherPage page = WindowPages.tab(PAGE);
        WindowLayout.showPage(page);
        WindowView.forChat();
        WindowView.show(page);
        assertTrue(WindowView.shows(page));
        assertEquals("the view is still the chat's",
                PageCategory.CHANNELS, WindowView.category());
        WindowView.clear();
        WindowView.forChat();
        assertFalse("its own key shows it alone next time",
                WindowView.shows(page));
    }

    @Test
    public void turningToAnotherViewLetsGoOfWhatWasOpenedByHand() {
        OtherPage page = WindowPages.tab(PAGE);
        OtherPage other = WindowPages.tab(OTHER);
        WindowLayout.showPage(page);
        WindowLayout.showPage(other);
        WindowView.forChat();
        WindowView.show(page);
        WindowView.forPage(other);
        assertFalse(WindowView.shows(page));
        assertTrue(WindowView.shows(other));
    }

    @Test
    public void aWindowPinnedToTheGuiShowsInEveryView() {
        OtherPage page = WindowPages.tab(PAGE);
        WindowLayout.showPage(page);
        String chat = WindowLayout.windowOf(GLOBAL).getId();
        assertTrue(WindowLayout.setPinnedToGui(chat, true));
        assertFalse("pinned already", WindowLayout.setPinnedToGui(chat, true));
        WindowView.forPage(page);
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue("every page the window holds", WindowView.shows(OOC));
        assertFalse("another window's pages wait for their key",
                WindowView.shows(ConversationPage.of(ChatChannel.CLIENT_CONSOLE)));
        assertTrue(WindowLayout.setPinnedToGui(chat, false));
        assertFalse(WindowView.shows(GLOBAL));
    }

    @Test
    public void aWindowPinnedToTheHudAloneStaysOutOfOtherViews() {
        OtherPage page = WindowPages.tab(PAGE);
        WindowLayout.showPage(page);
        String chat = WindowLayout.windowOf(GLOBAL).getId();
        assertTrue(WindowLayout.setPinnedToHud(chat, true));
        WindowView.forPage(page);
        assertFalse(WindowView.shows(GLOBAL));
        WindowView.forChat();
        assertTrue("its own key still shows it", WindowView.shows(GLOBAL));
    }

    @Test
    public void whilePlayingOnlyTheWindowsPinnedToTheHudShow() {
        Window chat = WindowLayout.windowOf(GLOBAL);
        WindowLayout.setPinnedToHud(chat.getId(), true);
        WindowLayout.setPinnedToGui(WindowLayout.windowOf(
                ConversationPage.of(ChatChannel.CLIENT_CONSOLE)).getId(), true);
        assertEquals(Arrays.asList(chat), WindowLayout.hudWindows());
        WindowView.beginPinnedPass();
        try {
            assertTrue(WindowView.shows(GLOBAL));
            assertTrue(WindowView.shows(OOC));
            assertFalse("pinned to the GUI only",
                    WindowView.shows(ConversationPage.of(ChatChannel.CLIENT_CONSOLE)));
        } finally {
            WindowView.endPinnedPass();
        }
        assertTrue(WindowView.shows(ConversationPage.of(ChatChannel.CLIENT_CONSOLE)));
    }

    @Test
    public void theTwoPinsAreSwitchedApart() {
        Window chat = WindowLayout.windowOf(GLOBAL);
        assertTrue(WindowLayout.setPinnedToHud(chat.getId(), true));
        assertFalse(chat.isPinnedToGui());
        assertTrue(WindowLayout.setPinnedToGui(chat.getId(), true));
        assertTrue(WindowLayout.setPinnedToHud(chat.getId(), false));
        assertTrue("letting the HUD go leaves the GUI's pin",
                chat.isPinnedToGui());
        assertTrue(WindowLayout.hudWindows().isEmpty());
        assertFalse(WindowLayout.setPinnedToHud("nope", true));
        assertFalse(WindowLayout.setPinnedToGui("nope", true));
    }

    @Test
    public void aPageOfTheWorldNeverShowsPinned() {
        OtherPage world = WindowPages.tab(WORLD);
        assertNotNull(world);
        assertFalse(WindowLayout.staysPut(world));
        assertFalse(WindowLayout.staysPut(null));
        assertTrue(WindowLayout.staysPut(GLOBAL));
        Window window = WindowLayout.showPage(world);
        assertNotNull(window);
        WindowLayout.setPinnedToHud(window.getId(), true);
        WindowLayout.setPinnedToGui(window.getId(), true);
        assertFalse(WindowLayout.isOnHud(world));
        assertFalse(WindowLayout.isOnGui(world));
    }

    @Test
    public void closingAWindowLeavesTheTabsTheViewHides() {
        OtherPage page = WindowPages.tab(PAGE);
        Window window = WindowLayout.windowOf(GLOBAL);
        assertTrue(WindowLayout.addTab(window.getId(), page));
        List<WindowPage> conversations =
                WindowFrame.visibleTabs(window);
        conversations.remove(page);
        WindowView.forChat();
        assertTrue(WindowLayout.closeWindow(window.getId()));
        assertNotNull("the window stays for the page",
                WindowLayout.window(window.getId()));
        assertEquals(Arrays.asList((WindowPage)page), window.getTabs());
        for (WindowPage tab : conversations) {
            assertFalse(tab.id(), WindowLayout.isOpen(tab));
        }
        assertFalse("the page waits hidden", WindowView.shows(page));
        WindowView.forPage(page);
        assertTrue(WindowLayout.closeWindow(window.getId()));
        assertNull("nothing hidden was left in it",
                WindowLayout.window(window.getId()));
    }

    @Test
    public void theWindowsPinsRoundTripThroughTheLayoutFile() {
        Window chat = WindowLayout.windowOf(GLOBAL);
        Window consoles = WindowLayout.windowOf(
                ConversationPage.of(ChatChannel.CLIENT_CONSOLE));
        WindowLayout.setPinnedToHud(chat.getId(), true);
        WindowLayout.setPinnedToGui(consoles.getId(), true);
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.toString(),
                described.toString().contains(" hud=true"));
        assertTrue(described.toString(),
                described.toString().contains(" gui=true"));
        TwoWindowLayout.reset();
        assertTrue(WindowLayout.hudWindows().isEmpty());
        WindowLayoutStore.load(described);
        assertTrue(WindowLayout.window(chat.getId()).isPinnedToHud());
        assertFalse(WindowLayout.window(chat.getId()).isPinnedToGui());
        assertTrue(WindowLayout.window(consoles.getId()).isPinnedToGui());
        assertFalse(WindowLayout.window(consoles.getId()).isPinnedToHud());
        assertEquals(described, WindowLayoutStore.describe());
    }

    /**
     * A category with no window opens its first window, locked: the map
     * filling the screen, the rest at the default place; the next page of
     * a category whose window is locked opens a step on from it, unlocked.
     * A page closed comes back in its category's first window, its old
     * window's pins gone with it.
     */
    @Test
    public void aCategoryOpensItsFirstWindowLocked() {
        OtherPage page = WindowPages.tab(PAGE);
        OtherPage other = WindowPages.tab(OTHER);
        OtherPage world = WindowPages.tab(WORLD);
        Window journal = WindowLayout.showPage(page);
        assertTrue(journal.isLocked());
        assertEquals(Window.ScreenFill.NONE, journal.getFill());
        Window map = WindowLayout.showPage(other);
        assertTrue(map.isLocked());
        assertEquals(Window.ScreenFill.FULL, map.getFill());
        Window beside = WindowLayout.showPage(world);
        assertFalse("the map's own window is locked", beside == map);
        assertFalse(beside.isLocked());
        journal.setLocked(false);
        WindowLayout.setPinnedToHud(journal.getId(), true);
        assertTrue(WindowLayout.close(page));
        Window again = WindowLayout.showPage(page);
        assertTrue(again.isLocked());
        assertFalse(again.isPinnedToHud());
        assertFalse(described().contains("place "));
    }

    /**
     * A page picked from another category's window opens in a window of its
     * own category, never in the window it was picked in.
     */
    @Test
    public void aPageOpensInItsOwnCategorysWindow() {
        OtherPage page = WindowPages.tab(PAGE);
        Window chat = WindowLayout.windowOf(GLOBAL);
        chat.setLocked(false);
        assertEquals(page, WindowLayout.openInCategory(page, chat.getId()));
        assertFalse(chat.contains(page));
        Window journal = WindowLayout.windowOf(page);
        journal.setLocked(false);
        OtherPage world = WindowPages.tab(WORLD);
        assertEquals(world, WindowLayout.openInCategory(world, journal.getId()));
        assertFalse("a waystone is the map's", journal.contains(world));
        ConversationPage whisper = ConversationPage.whisper("Steve", "");
        assertEquals("a whisper stands with the channels", whisper,
                WindowLayout.openInCategory(whisper, chat.getId()));
        assertTrue(chat.contains(whisper));
    }

    private static String described() {
        return WindowLayoutStore.describe().toString();
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
