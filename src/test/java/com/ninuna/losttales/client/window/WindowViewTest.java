package com.ninuna.losttales.client.window;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.chat.ChatTab;
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
 * A key shows its own tabs and hides the rest in their windows; a window
 * pinned to the GUI shows in every view, one pinned to the HUD stays while
 * playing; closing a window closes what the view shows of it.
 */
public final class WindowViewTest {
    private static final String PAGE = "view_page";
    private static final String OTHER = "view_other";
    private static final String WORLD = "view_world";

    private static final ChatTab GLOBAL = ChatTab.of(ChatChannel.GLOBAL);
    private static final ChatTab OOC = ChatTab.of(ChatChannel.OOC);

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
                    new ItemStack(Items.book), null, empty);
        }
        if (WindowPages.byId(OTHER) == null) {
            WindowPages.register(OTHER, "gui.test.view.other",
                    new ItemStack(Items.map), null, empty);
        }
        if (WindowPages.byId(WORLD) == null) {
            WindowPages.registerWorldPage(WORLD, "gui.test.view.world",
                    new ItemStack(Items.compass), empty);
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
        PageTab page = WindowPages.tab(PAGE);
        WindowLayout.showPage(page);
        assertEquals(WindowView.Kind.NONE, WindowView.kind());
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue(WindowView.shows(page));
        assertFalse(WindowLayout.hasHidden());
        assertFalse("no tab is no tab in any view", WindowView.shows(null));
    }

    @Test
    public void theChatsKeyShowsTheConversationsAndNoPage() {
        PageTab page = WindowPages.tab(PAGE);
        Window window = WindowLayout.showPage(page);
        WindowView.forChat();
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue(WindowView.shows(OOC));
        assertFalse(WindowView.shows(page));
        assertTrue("the page waits in its window",
                WindowLayout.isOpen(page));
        assertTrue(WindowFrame.visibleTabs(window).isEmpty());
        assertTrue(WindowLayout.hasHidden());
    }

    @Test
    public void aPagesKeyShowsThatPageAlone() {
        PageTab page = WindowPages.tab(PAGE);
        PageTab other = WindowPages.tab(OTHER);
        WindowLayout.showPage(page);
        WindowLayout.showPage(other);
        WindowView.forPage(page);
        assertTrue(WindowView.isFor(page));
        assertFalse(WindowView.isFor(other));
        assertTrue(WindowView.shows(page));
        assertFalse(WindowView.shows(other));
        assertFalse(WindowView.shows(GLOBAL));
    }

    @Test
    public void aTabOpenedByHandJoinsTheViewUntilTheScreenCloses() {
        PageTab page = WindowPages.tab(PAGE);
        WindowLayout.showPage(page);
        WindowView.forChat();
        WindowView.show(page);
        assertTrue(WindowView.shows(page));
        assertEquals("the view is still the chat's",
                WindowView.Kind.CHAT, WindowView.kind());
        WindowView.clear();
        WindowView.forChat();
        assertFalse("its own key shows it alone next time",
                WindowView.shows(page));
    }

    @Test
    public void turningToAnotherViewLetsGoOfWhatWasOpenedByHand() {
        PageTab page = WindowPages.tab(PAGE);
        PageTab other = WindowPages.tab(OTHER);
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
        PageTab page = WindowPages.tab(PAGE);
        WindowLayout.showPage(page);
        String chat = WindowLayout.windowOf(GLOBAL).getId();
        assertTrue(WindowLayout.setPinnedToGui(chat, true));
        assertFalse("pinned already", WindowLayout.setPinnedToGui(chat, true));
        WindowView.forPage(page);
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue("every page the window holds", WindowView.shows(OOC));
        assertFalse("another window's pages wait for their key",
                WindowView.shows(ChatTab.of(ChatChannel.CLIENT_CONSOLE)));
        assertTrue(WindowLayout.setPinnedToGui(chat, false));
        assertFalse(WindowView.shows(GLOBAL));
    }

    @Test
    public void aWindowPinnedToTheHudAloneStaysOutOfOtherViews() {
        PageTab page = WindowPages.tab(PAGE);
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
                ChatTab.of(ChatChannel.CLIENT_CONSOLE)).getId(), true);
        assertEquals(Arrays.asList(chat), WindowLayout.hudWindows());
        WindowView.beginPinnedPass();
        try {
            assertTrue(WindowView.shows(GLOBAL));
            assertTrue(WindowView.shows(OOC));
            assertFalse("pinned to the GUI only",
                    WindowView.shows(ChatTab.of(ChatChannel.CLIENT_CONSOLE)));
        } finally {
            WindowView.endPinnedPass();
        }
        assertTrue(WindowView.shows(ChatTab.of(ChatChannel.CLIENT_CONSOLE)));
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
        PageTab world = WindowPages.tab(WORLD);
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
        PageTab page = WindowPages.tab(PAGE);
        Window window = WindowLayout.windowOf(GLOBAL);
        assertTrue(WindowLayout.addTab(window.getId(), page));
        List<WindowTab> conversations =
                WindowFrame.visibleTabs(window);
        conversations.remove(page);
        WindowView.forChat();
        assertTrue(WindowLayout.closeWindow(window.getId()));
        assertNotNull("the window stays for the page",
                WindowLayout.window(window.getId()));
        assertEquals(Arrays.asList((WindowTab)page), window.getTabs());
        for (WindowTab tab : conversations) {
            assertFalse(tab.id(), WindowLayout.isOpen(tab));
        }
        assertTrue(WindowLayout.hasHidden());
        WindowView.forPage(page);
        assertTrue(WindowLayout.closeWindow(window.getId()));
        assertNull("nothing hidden was left in it",
                WindowLayout.window(window.getId()));
    }

    @Test
    public void theWindowsPinsRoundTripThroughTheLayoutFile() {
        Window chat = WindowLayout.windowOf(GLOBAL);
        Window consoles = WindowLayout.windowOf(
                ChatTab.of(ChatChannel.CLIENT_CONSOLE));
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

    @Test
    public void aPageComesBackWithTheWindowsPinsItLeft() {
        PageTab page = WindowPages.tab(PAGE);
        Window window = WindowLayout.showPage(page);
        WindowLayout.setPinnedToHud(window.getId(), true);
        assertTrue(WindowLayout.close(page));
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.toString(), described.toString().contains(
                "place " + page.id() + " locked=false hud=true x="));
        TwoWindowLayout.reset();
        WindowLayoutStore.load(described);
        assertTrue(WindowLayout.showPage(page).isPinnedToHud());
        assertFalse(WindowLayout.windowOf(page).isPinnedToGui());
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
