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
 * A key shows its own tabs and hides the rest in their windows; a kept tab
 * shows in every view, a pinned one besides stays while playing; closing a
 * window closes what the view shows of it.
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
    public void settingsShowsNothingOfItsOwn() {
        PageTab page = WindowPages.tab(PAGE);
        WindowLayout.showPage(page);
        WindowView.forSettings();
        assertFalse(WindowView.shows(page));
        assertFalse(WindowView.shows(GLOBAL));
        WindowLayout.setKept(GLOBAL, true);
        assertTrue("a kept tab stands behind Settings too",
                WindowView.shows(GLOBAL));
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
    public void aKeptTabShowsInEveryView() {
        PageTab page = WindowPages.tab(PAGE);
        WindowLayout.showPage(page);
        assertTrue(WindowLayout.setKept(GLOBAL, true));
        assertFalse("kept already", WindowLayout.setKept(GLOBAL, true));
        WindowView.forPage(page);
        assertTrue(WindowView.shows(GLOBAL));
        assertFalse(WindowView.shows(OOC));
        assertTrue(WindowLayout.setKept(GLOBAL, false));
        assertFalse(WindowView.shows(GLOBAL));
    }

    @Test
    public void aPinnedTabShowsInEveryView() {
        PageTab page = WindowPages.tab(PAGE);
        WindowLayout.showPage(page);
        assertTrue(WindowLayout.setPinned(GLOBAL, true));
        WindowView.forPage(page);
        assertTrue(WindowView.shows(GLOBAL));
        assertFalse(WindowView.shows(OOC));
    }

    @Test
    public void whilePlayingOnlyThePinnedTabsShow() {
        WindowLayout.setPinned(GLOBAL, true);
        WindowView.beginPinnedPass();
        try {
            assertTrue(WindowView.shows(GLOBAL));
            assertFalse(WindowView.shows(OOC));
            assertEquals(Arrays.asList(GLOBAL), WindowFrame.visibleTabs(
                    WindowLayout.windowOf(GLOBAL)));
        } finally {
            WindowView.endPinnedPass();
        }
        assertTrue(WindowView.shows(OOC));
    }

    @Test
    public void aWindowPinnedWholePinsEveryTabItHolds() {
        Window window = WindowLayout.windowOf(GLOBAL);
        assertFalse(WindowLayout.isPinned(OOC));
        assertTrue(WindowLayout.setWindowPinned(window.getId(), true));
        assertFalse("pinned already",
                WindowLayout.setWindowPinned(window.getId(), true));
        assertTrue(WindowLayout.isPinned(GLOBAL));
        assertTrue(WindowLayout.isPinned(OOC));
        assertTrue(WindowLayout.pinned().isEmpty());
        assertEquals(Arrays.asList(window), WindowLayout.pinnedWindows());
        assertTrue(WindowLayout.setWindowPinned(window.getId(), false));
        assertFalse(WindowLayout.isPinned(GLOBAL));
        assertTrue(WindowLayout.pinnedWindows().isEmpty());
    }

    @Test
    public void lettingOneTabOfAPinnedWindowGoLeavesTheOthersPinned() {
        Window window = WindowLayout.windowOf(GLOBAL);
        WindowLayout.setWindowPinned(window.getId(), true);
        assertTrue(WindowLayout.setPinned(GLOBAL, false));
        assertFalse(window.isPinned());
        assertFalse(WindowLayout.isPinned(GLOBAL));
        assertTrue(WindowLayout.isPinned(OOC));
        assertFalse(WindowLayout.pinned().contains(GLOBAL.id()));
        assertTrue(WindowLayout.pinned().contains(OOC.id()));
        assertEquals(Arrays.asList(window), WindowLayout.pinnedWindows());
    }

    @Test
    public void pinningAWindowWholeTakesTheSinglePinsOfItsTabs() {
        Window window = WindowLayout.windowOf(GLOBAL);
        WindowLayout.setPinned(GLOBAL, true);
        assertTrue(WindowLayout.setWindowPinned(window.getId(), true));
        assertTrue(WindowLayout.pinned().isEmpty());
        assertTrue(WindowLayout.isPinned(GLOBAL));
        WindowLayout.setWindowPinned(window.getId(), false);
        assertFalse("nothing of the window stays pinned",
                WindowLayout.isPinned(GLOBAL));
    }

    @Test
    public void unpinningAWindowNotPinnedWholeLetsItsTabsGo() {
        Window window = WindowLayout.windowOf(GLOBAL);
        WindowLayout.setPinned(GLOBAL, true);
        assertTrue(WindowLayout.setWindowPinned(window.getId(), false));
        assertFalse(WindowLayout.isPinned(GLOBAL));
        assertFalse("nothing left to let go",
                WindowLayout.setWindowPinned(window.getId(), false));
        assertFalse(WindowLayout.setWindowPinned("nope", true));
    }

    @Test
    public void aPageOfTheWorldIsNeitherKeptShownNorPinned() {
        PageTab world = WindowPages.tab(WORLD);
        assertNotNull(world);
        assertFalse(WindowLayout.staysPut(world));
        assertFalse(WindowLayout.staysPut(null));
        assertTrue(WindowLayout.staysPut(GLOBAL));
        assertFalse(WindowLayout.setPinned(world, true));
        assertFalse(WindowLayout.isPinned(world));
        Window window = WindowLayout.showPage(world);
        assertNotNull(window);
        WindowLayout.setWindowPinned(window.getId(), true);
        assertFalse("not even in a window pinned whole",
                WindowLayout.isPinned(world));
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
    public void keptAndPinnedTabsRoundTripThroughTheLayoutFile() {
        WindowLayout.setKept(GLOBAL, true);
        WindowLayout.setPinned(OOC, true);
        Window consoles = WindowLayout.windowOf(
                ChatTab.of(ChatChannel.CLIENT_CONSOLE));
        WindowLayout.setWindowPinned(consoles.getId(), true);
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.toString(),
                described.contains("keep\t" + GLOBAL.id()));
        assertTrue(described.toString(),
                described.contains("pin\t" + OOC.id()));
        TwoWindowLayout.reset();
        assertFalse(WindowLayout.isKept(GLOBAL));
        WindowLayoutStore.load(described);
        assertTrue(WindowLayout.isKept(GLOBAL));
        assertFalse(WindowLayout.isKept(OOC));
        assertTrue(WindowLayout.isPinned(OOC));
        assertFalse(WindowLayout.isPinned(GLOBAL));
        assertTrue("the window's own pin comes back with it",
                WindowLayout.window(consoles.getId()).isPinned());
        assertEquals(described, WindowLayoutStore.describe());
    }

    @Test
    public void aKeepOrPinLineWithNoIdOrAnOverlongOneIsLeftOut() {
        StringBuilder overlong = new StringBuilder();
        for (int index = 0; index < 300; index++) {
            overlong.append('a');
        }
        WindowLayoutStore.load(Arrays.asList(
                "window w1 x=0.00 y=0.00 active=global tabs=global,ooc",
                "keep\t",
                "pin\t" + overlong,
                "keep\t" + overlong,
                "pin\t" + OOC.id()));
        assertTrue(WindowLayout.kept().isEmpty());
        assertEquals(1, WindowLayout.pinned().size());
        assertTrue(WindowLayout.isPinned(OOC));
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
