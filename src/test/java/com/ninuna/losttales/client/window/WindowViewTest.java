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
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The screen shows one view at a time: each category's view, or a custom
 * one. Any page may stand in any view: a page the game or a key opens goes
 * to its category's view, one opened by hand to the view on screen. A view
 * with no window opens with its defaults, and Reset View puts it back so;
 * custom views are made, named, keyed and deleted; a window pinned to the
 * GUI shows in every view, one pinned to the HUD stays while playing; a
 * locked window cannot be closed.
 */
public final class WindowViewTest {
    private static final String PAGE = "view_page";
    private static final String OTHER = "view_other";
    private static final String WORLD = "view_world";
    /** The pages the categories' views open with, by the ids their systems register them under. */
    private static final String MAP = "map";
    private static final String JOURNAL = "journal";
    private static final String FELLOWSHIPS = "fellowship";
    private static final String CHARACTERS = "characters";

    private static final ConversationPage GLOBAL = ConversationPage.of(ChatChannel.GLOBAL);
    private static final ConversationPage OOC = ConversationPage.of(ChatChannel.OOC);
    private static final ConversationPage CONSOLE =
            ConversationPage.of(ChatChannel.CLIENT_CONSOLE);

    @BeforeClass
    public static void registerPages() {
        register(PAGE, Items.book, PageCategory.QUEST_JOURNAL);
        register(OTHER, Items.compass, PageCategory.MAP);
        if (WindowPages.byId(WORLD) == null) {
            WindowPages.registerWorldPage(WORLD, "gui.test.view.world",
                    new ItemStack(Items.compass), PageCategory.MAP, EMPTY);
        }
        register(MAP, Items.map, PageCategory.MAP);
        register(JOURNAL, Items.writable_book, PageCategory.QUEST_JOURNAL);
        register(FELLOWSHIPS, Items.iron_helmet, PageCategory.FELLOWSHIPS);
        register(CHARACTERS, Items.name_tag, PageCategory.PROFILE);
        register(NewPage.PAGE_ID, Items.paper, PageCategory.NEW_PAGE);
    }

    private static void register(String id, net.minecraft.item.Item item,
                                 PageCategory category) {
        if (WindowPages.byId(id) == null) {
            WindowPages.register(id, "gui.test.view." + id,
                    new ItemStack(item), null, category, EMPTY);
        }
    }

    private static final WindowPages.Factory EMPTY = new WindowPages.Factory() {
        @Override
        public PageContent create() {
            return new EmptyPage();
        }
    };

    @Before
    public void reset() {
        Views.reset();
        TwoWindowLayout.reset();
        WindowView.clear();
    }

    @After
    public void cleanUp() {
        WindowView.clear();
        Views.reset();
        TwoWindowLayout.reset();
        WindowLayout.setChangeListener(null);
        ChatChannel.resetToBuiltIn();
    }

    private static View view(PageCategory category) {
        return Views.of(category);
    }

    private static OtherPage tab(String id) {
        return WindowPages.tab(id);
    }

    /* ---- One view at a time ---- */

    @Test
    public void withNoScreenOpenEveryTabCountsAsShown() {
        WindowLayout.showPage(tab(PAGE));
        assertFalse(WindowView.isOn(view(PageCategory.CHANNELS)));
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue(WindowView.shows(tab(PAGE)));
        assertFalse("no tab is no tab in any view", WindowView.shows(null));
    }

    /** The chat's key shows the Chat view's windows; the command key the Consoles view's. */
    @Test
    public void theChatAndTheConsolesAreViewsOfTheirOwn() {
        WindowLayout.showPage(tab(PAGE));
        WindowView.forChat();
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue(WindowView.shows(OOC));
        assertFalse(WindowView.shows(CONSOLE));
        assertFalse("the page waits in its own view", WindowView.shows(tab(PAGE)));
        assertTrue(WindowLayout.isOpen(tab(PAGE)));
        WindowView.forConsole();
        assertTrue(WindowView.shows(CONSOLE));
        assertTrue("its window shows whole: Operator stands beside it",
                WindowView.shows(ConversationPage.of(ChatChannel.OPERATOR)));
        assertFalse(WindowView.shows(GLOBAL));
    }

    /** With no screen open, a page goes to its category's view, and a page's key swaps to that view. */
    @Test
    public void aPageOpenedByTheGameGoesToItsCategorysView() {
        Window journal = WindowLayout.showPage(tab(PAGE));
        Window map = WindowLayout.showPage(tab(OTHER));
        Window world = WindowLayout.showPage(tab(WORLD));
        assertEquals(view(PageCategory.QUEST_JOURNAL), WindowLayout.viewOf(journal));
        assertEquals(view(PageCategory.MAP), WindowLayout.viewOf(map));
        assertEquals(view(PageCategory.MAP), WindowLayout.viewOf(world));
        WindowView.forPage(tab(PAGE));
        assertTrue(WindowView.isFor(tab(PAGE)));
        assertFalse(WindowView.isFor(tab(OTHER)));
        assertTrue(WindowView.shows(tab(PAGE)));
        assertFalse(WindowView.shows(tab(OTHER)));
        WindowView.forPage(tab(OTHER));
        assertTrue("one key for the whole category", WindowView.isFor(tab(WORLD)));
        assertTrue(WindowView.shows(tab(WORLD)));
        assertFalse(WindowView.shows(tab(PAGE)));
    }

    /**
     * On the screen, any page opens in the view shown, whatever its
     * category; one open in another view opens there as a copy of its
     * own, and each view keeps its own.
     */
    @Test
    public void anyPageOpensInTheViewOnScreen() {
        WindowView.forView(view(PageCategory.MAP));
        WindowPage global = WindowLayout.openByHand(GLOBAL, null);
        assertNotNull(global);
        assertTrue("a copy of its own: the Chat view keeps Global",
                global.isCopyOf(GLOBAL) && !global.equals(GLOBAL));
        assertEquals(view(PageCategory.MAP),
                WindowLayout.viewOf(WindowLayout.windowOf(global)));
        assertTrue(WindowView.shows(global));
        assertFalse(WindowView.shows(GLOBAL));
        WindowView.forChat();
        assertTrue(WindowView.shows(GLOBAL));
        assertFalse(WindowView.shows(global));
        assertEquals(view(PageCategory.CHANNELS), WindowView.handView(tab(PAGE)));
    }

    /** A view swaps for another: one view at a time, never two. */
    @Test
    public void aViewSwapsForAnother() {
        WindowLayout.showPage(tab(OTHER));
        WindowView.forChat();
        assertTrue(WindowView.isOn(Views.of(PageCategory.WHISPERS)));
        WindowView.forView(view(PageCategory.MAP));
        assertTrue(WindowView.shows(tab(OTHER)));
        assertFalse(WindowView.shows(GLOBAL));
        assertFalse(WindowView.isOn(view(PageCategory.CHANNELS)));
        WindowView.forView(null);
        assertTrue("no view is the Lost Tales Menu's", WindowView.isOn(Views.menu()));
    }

    /** A tab gone to by hand swaps the screen to the view of its window. */
    @Test
    public void goingToATabSwapsToItsView() {
        WindowLayout.showPage(tab(PAGE));
        WindowView.forChat();
        WindowView.show(tab(PAGE));
        assertTrue(WindowView.isOn(view(PageCategory.QUEST_JOURNAL)));
        assertTrue(WindowView.shows(tab(PAGE)));
    }

    /* ---- Defaults ---- */

    /**
     * Each category's view opens with its own windows, locked, each in
     * its part of the screen; a page another view holds opens as a copy.
     */
    @Test
    public void eachViewOpensWithItsDefaults() {
        assertTrue(WindowLayout.buildView(view(PageCategory.MAP)));
        Window map = WindowLayout.windowOf(tab(MAP));
        assertTrue(map.isLocked());
        assertEquals(Window.ScreenFill.FULL, map.getFill());
        assertFalse("built once", WindowLayout.buildView(view(PageCategory.MAP)));

        assertTrue(WindowLayout.buildView(view(PageCategory.QUEST_JOURNAL)));
        Window journal = WindowLayout.windowOf(tab(JOURNAL));
        assertEquals(Window.ScreenFill.LEFT, journal.getFill());
        Window questMap = windowIn(view(PageCategory.QUEST_JOURNAL), tab(MAP));
        assertNotNull(questMap);
        assertEquals(Window.ScreenFill.RIGHT, questMap.getFill());
        assertTrue(questMap.isLocked());
        assertTrue("the Map view keeps its own", map.contains(tab(MAP)));

        assertTrue(WindowLayout.buildView(view(PageCategory.FELLOWSHIPS)));
        assertEquals(Window.ScreenFill.CENTRE_HALF,
                WindowLayout.windowOf(tab(FELLOWSHIPS)).getFill());
        assertEquals(Window.ScreenFill.RIGHT_QUARTER,
                windowIn(view(PageCategory.FELLOWSHIPS), tab(MAP)).getFill());
        assertTrue(WindowLayout.buildView(view(PageCategory.PROFILE)));
        assertEquals(Window.ScreenFill.FULL,
                WindowLayout.windowOf(tab(CHARACTERS)).getFill());
    }

    /** The Chat view's defaults: Global and OOC in the bottom-left quarter; the consoles' the top-left. */
    @Test
    public void theChatsViewsOpenInTheirQuarters() {
        for (Window window : WindowLayout.windows()) {
            window.setLocked(false);
        }
        assertTrue(WindowLayout.closeWindow(WindowLayout.windowOf(GLOBAL).getId()));
        assertTrue(WindowLayout.closeWindow(WindowLayout.windowOf(CONSOLE).getId()));
        assertTrue(WindowLayout.buildView(view(PageCategory.CHANNELS)));
        Window chat = WindowLayout.windowOf(GLOBAL);
        assertEquals(Arrays.<WindowPage>asList(GLOBAL, OOC), chat.getTabs());
        assertEquals(Window.ScreenFill.BOTTOM_LEFT, chat.getFill());
        assertTrue(chat.isLocked());
        assertTrue(WindowLayout.buildView(view(PageCategory.CONSOLES)));
        Window consoles = WindowLayout.windowOf(CONSOLE);
        assertEquals(Window.ScreenFill.TOP_LEFT, consoles.getFill());
        assertTrue(consoles.isLocked());
    }

    /** A custom view opens with a New Page in an unlocked window at the default place. */
    @Test
    public void aCustomViewOpensWithANewPage() {
        View made = Views.make();
        assertNotNull(made);
        assertTrue(WindowLayout.buildView(made));
        Window window = WindowLayout.windowOf(tab(NewPage.PAGE_ID));
        assertEquals(made, WindowLayout.viewOf(window));
        assertFalse(window.isLocked());
        assertEquals(Window.ScreenFill.NONE, window.getFill());
    }

    /** A view left with no window opens with its defaults again; Reset View does so at once. */
    @Test
    public void anEmptiedViewOpensWithItsDefaultsAgain() {
        View mapView = view(PageCategory.MAP);
        WindowLayout.buildView(mapView);
        Window map = WindowLayout.windowOf(tab(MAP));
        WindowView.forView(mapView);
        WindowLayout.openByHand(tab(PAGE), null);
        assertEquals(2, windowsIn(mapView));
        WindowLayout.resetView(mapView);
        assertEquals("only what it opens with", 1, windowsIn(mapView));
        Window again = WindowLayout.windowOf(tab(MAP));
        again.setLocked(false);
        assertTrue(WindowLayout.closeWindow(again.getId()));
        assertFalse(WindowLayout.hasWindowIn(mapView));
        assertTrue(WindowLayout.buildView(mapView));
        assertTrue(WindowLayout.windowOf(tab(MAP)).isLocked());
        assertFalse(map == WindowLayout.windowOf(tab(MAP)));
    }

    /** Reset Window Layout puts a window back where its view's defaults have it, locked. */
    @Test
    public void resettingAWindowPutsItWhereItsViewHasIt() {
        WindowLayout.buildView(view(PageCategory.QUEST_JOURNAL));
        Window journal = WindowLayout.windowOf(tab(JOURNAL));
        journal.setLocked(false);
        WindowLayout.setFill(journal.getId(), Window.ScreenFill.NONE, false);
        WindowLayout.setPinnedToGui(journal.getId(), true);
        assertTrue(WindowLayout.resetWindow(journal.getId()));
        assertEquals(Window.ScreenFill.LEFT, journal.getFill());
        assertTrue(journal.isLocked());
        assertFalse(journal.isPinnedToGui());
    }

    /** A page's key opens its category's view with its defaults, the page forward there. */
    @Test
    public void aPagesKeyOpensItsViewWithItsDefaults() {
        Window held = WindowLayout.showView(tab(JOURNAL));
        assertNotNull(held);
        assertEquals(view(PageCategory.QUEST_JOURNAL), WindowLayout.viewOf(held));
        assertEquals(Window.ScreenFill.LEFT, held.getFill());
        assertNotNull("the map beside it", windowIn(
                view(PageCategory.QUEST_JOURNAL), tab(MAP)));
    }

    /* ---- Custom views ---- */

    /** Custom views: the Lost Tales Menu's first, Caps Lock its key; at most a few more. */
    @Test
    public void customViewsAreMadeUpToTheMost() {
        assertEquals(View.MENU_ID, Views.menu().id());
        assertEquals(Keyboard.KEY_CAPITAL, Views.menu().key());
        assertEquals(Views.menu(), Views.byKey(Keyboard.KEY_CAPITAL));
        assertFalse(Views.menu().isDeletable());
        View made = Views.make();
        assertEquals("v1", made.id());
        assertTrue(made.isDeletable());
        while (Views.canMake()) {
            assertNotNull(Views.make());
        }
        assertEquals(Views.MAX_CUSTOM, Views.custom().size());
        assertNull(Views.make());
        assertTrue(Views.delete(made));
        assertFalse(Views.delete(Views.menu()));
        assertEquals("the lowest number free", "v1", Views.make().id());
    }

    /** A view's key may be any key nothing else holds; a view's name is cleaned. */
    @Test
    public void aViewsKeyAndNameFollowTheRules() {
        View made = Views.make();
        assertNotNull(Views.keyRefusal(made, Keyboard.KEY_ESCAPE));
        assertNotNull("the menu's", Views.keyRefusal(made, Keyboard.KEY_CAPITAL));
        assertNull(Views.keyRefusal(made, Keyboard.KEY_G));
        assertTrue(Views.setKey(made, Keyboard.KEY_G));
        assertEquals(made, Views.byKey(Keyboard.KEY_G));
        assertFalse(Views.setKey(Views.menu(), Keyboard.KEY_G));
        assertTrue("no key at all", Views.setKey(made, 0));
        assertNull(Views.byKey(Keyboard.KEY_G));
        Views.rename(made, "  §cHunting§r Party of the Long Grey Road ");
        assertEquals("Hunting Party of the Lon", made.title());
        Views.rename(Views.of(PageCategory.MAP), "Atlas");
        assertEquals("a category's view keeps its name",
                PageCategory.MAP.title(), Views.of(PageCategory.MAP).title());
    }

    /** A view deleted takes its windows and their pages with it. */
    @Test
    public void aDeletedViewLetsItsWindowsGo() {
        View made = Views.make();
        WindowView.forView(made);
        WindowLayout.openByHand(tab(PAGE), null);
        assertEquals(made, WindowLayout.viewOf(WindowLayout.windowOf(tab(PAGE))));
        assertTrue(Views.delete(made));
        WindowLayout.forgetView(made);
        assertFalse(WindowLayout.isOpen(tab(PAGE)));
        assertFalse(WindowLayout.hasWindowIn(made));
    }

    /** A window keeps the view it stands in through the layout file. */
    @Test
    public void aWindowsViewRoundTripsThroughTheLayoutFile() {
        View made = Views.make();
        WindowView.forView(made);
        Window window = WindowLayout.showPage(tab(PAGE));
        assertEquals(made, WindowLayout.viewOf(window));
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.toString(), described.toString().contains(" view=v1 "));
        TwoWindowLayout.reset();
        Views.reset();
        WindowLayoutStore.load(described);
        assertEquals(Views.byId("v1"), WindowLayout.viewOf(
                WindowLayout.window(window.getId())));
    }

    /* ---- Pins and the padlock ---- */

    @Test
    public void aWindowPinnedToTheGuiShowsInEveryView() {
        WindowLayout.showPage(tab(PAGE));
        String chat = WindowLayout.windowOf(GLOBAL).getId();
        assertTrue(WindowLayout.setPinnedToGui(chat, true));
        assertFalse("pinned already", WindowLayout.setPinnedToGui(chat, true));
        WindowView.forPage(tab(PAGE));
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue("every page the window holds", WindowView.shows(OOC));
        assertFalse("another window's pages wait for their view",
                WindowView.shows(CONSOLE));
        assertTrue(WindowLayout.setPinnedToGui(chat, false));
        assertFalse(WindowView.shows(GLOBAL));
    }

    @Test
    public void aWindowPinnedToTheHudAloneStaysOutOfOtherViews() {
        WindowLayout.showPage(tab(PAGE));
        String chat = WindowLayout.windowOf(GLOBAL).getId();
        assertTrue(WindowLayout.setPinnedToHud(chat, true));
        WindowView.forPage(tab(PAGE));
        assertFalse(WindowView.shows(GLOBAL));
        WindowView.forChat();
        assertTrue("its own view still shows it", WindowView.shows(GLOBAL));
    }

    @Test
    public void whilePlayingOnlyTheWindowsPinnedToTheHudShow() {
        Window chat = WindowLayout.windowOf(GLOBAL);
        WindowLayout.setPinnedToHud(chat.getId(), true);
        WindowLayout.setPinnedToGui(WindowLayout.windowOf(CONSOLE).getId(), true);
        assertEquals(Arrays.asList(chat), WindowLayout.hudWindows());
        WindowView.beginPinnedPass();
        try {
            assertTrue(WindowView.shows(GLOBAL));
            assertTrue(WindowView.shows(OOC));
            assertFalse("pinned to the GUI only", WindowView.shows(CONSOLE));
        } finally {
            WindowView.endPinnedPass();
        }
        assertTrue(WindowView.shows(CONSOLE));
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
        OtherPage world = tab(WORLD);
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

    /** Closing an unlocked window closes every page of it; a locked one stays. */
    @Test
    public void aLockedWindowCannotBeClosed() {
        Window window = WindowLayout.windowOf(GLOBAL);
        window.setLocked(true);
        assertFalse(WindowLayout.closeWindow(window.getId()));
        assertTrue(WindowLayout.isOpen(GLOBAL));
        window.setLocked(false);
        assertTrue(WindowLayout.closeWindow(window.getId()));
        assertNull(WindowLayout.window(window.getId()));
        assertFalse(WindowLayout.isOpen(GLOBAL));
        assertFalse(WindowLayout.isOpen(OOC));
    }

    @Test
    public void theWindowsPinsRoundTripThroughTheLayoutFile() {
        Window chat = WindowLayout.windowOf(GLOBAL);
        Window consoles = WindowLayout.windowOf(CONSOLE);
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

    /** The window of {@code view} holding a copy of {@code page}; null for none. */
    private static Window windowIn(View view, WindowPage page) {
        for (Window window : WindowLayout.windows()) {
            if (WindowLayout.viewOf(window) != view) {
                continue;
            }
            for (WindowPage held : window.getTabs()) {
                if (held.isCopyOf(page)) {
                    return window;
                }
            }
        }
        return null;
    }

    private static int windowsIn(View view) {
        int count = 0;
        for (Window window : WindowLayout.windows()) {
            if (WindowLayout.viewOf(window) == view) {
                count++;
            }
        }
        return count;
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
